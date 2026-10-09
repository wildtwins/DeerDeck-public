package com.wildtwins.deerdeck.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.wildtwins.deerdeck.data.backup.BackupWriteResult
import com.wildtwins.deerdeck.data.backup.BackupCounts
import com.wildtwins.deerdeck.data.backup.BackupDataset
import com.wildtwins.deerdeck.data.backup.DeerDeckBackupJson
import com.wildtwins.deerdeck.data.backup.DeerDeckBackupStorage
import com.wildtwins.deerdeck.data.backup.DeerDeckRestorePackage
import com.wildtwins.deerdeck.data.backup.RestoreResult
import com.wildtwins.deerdeck.data.environment.SessionEnvironment
import com.wildtwins.deerdeck.data.environment.EnvironmentCaptureContract
import com.wildtwins.deerdeck.data.environment.EnvironmentRetryResult
import com.wildtwins.deerdeck.data.environment.ManualEnvironmentRetryEligibility
import com.wildtwins.deerdeck.data.environment.SessionEnvironmentFetcher
import com.wildtwins.deerdeck.data.export.CsvExport
import com.wildtwins.deerdeck.data.export.DataPackageExporter
import com.wildtwins.deerdeck.data.export.ExportPrivacyOptions
import com.wildtwins.deerdeck.data.export.ObservationCsvExporter
import com.wildtwins.deerdeck.data.db.DeerDeckDao
import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import com.wildtwins.deerdeck.data.settings.BackupDestination
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.MediaSaveLocation
import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeerDeckRepository(
    private val dao: DeerDeckDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val inactivityLimitMillis = 3 * 60 * 60 * 1_000L

    val activeSession = dao.observeActiveSession()
    val sessionSummaries = dao.observeSessionSummaries()
    val overallSpeciesCounts = dao.observeOverallSpeciesCounts()
    val datasetCounts = dao.observeDatasetCounts()
    val sessionWildlifeCounts = dao.observeSessionWildlifeCounts()
    val wildlifeObservationFacts = dao.observeWildlifeObservationFacts()
    val allMedia = dao.observeAllMedia()

    fun activeSpecies(profile: ObservationProfile) = dao.observeActiveSpecies(profile.storageValue)

    suspend fun ensureDefaultSpecies(
        profile: ObservationProfile = ObservationProfile.WildlifeWatching,
        @Suppress("UNUSED_PARAMETER")
        customized: Boolean = false,
    ) {
        if (profile.isCustom) return

        val activeSpecies = dao.getActiveSpecies(profile.storageValue)
        activeSpecies.filter { !it.isDefault }.forEach { species ->
            dao.updateSpecies(species.copy(isArchived = true))
        }
        if (
            activeSpecies.any { it.isDefault } &&
            activeSpecies.none { it.name in DefaultSpecies.removedNames }
        ) {
            return
        }
        syncProfileSpecies(profile, DefaultSpecies.forProfile(profile))
    }

    suspend fun refreshLocationAwareSpecies(
        profile: ObservationProfile,
        latitude: Double,
        longitude: Double,
    ): String? {
        if (profile.isCustom) return null
        val (regionLabel, defaults) = LocationAwareSpeciesDefaults.forProfile(profile, latitude, longitude)
        syncProfileSpecies(profile, defaults)
        return regionLabel
    }

    private suspend fun syncProfileSpecies(
        profile: ObservationProfile,
        defaults: List<SpeciesEntity>,
    ) {
        val defaultNames = defaults.map { it.name }.toSet()
        DefaultSpecies.removedNames.forEach { name ->
            dao.getSpeciesByName(profile.storageValue, name)?.let { species ->
                if (!species.isArchived) {
                    dao.updateSpecies(species.copy(isArchived = true))
                }
            }
        }
        dao.getActiveSpecies(profile.storageValue)
            .filter { it.name !in defaultNames }
            .forEach { species -> dao.updateSpecies(species.copy(isArchived = true)) }

        defaults.forEach { defaultSpecies ->
            val existing = dao.getSpeciesByName(profile.storageValue, defaultSpecies.name)
            if (existing == null) {
                dao.insertSpecies(defaultSpecies.copy(profileKey = profile.storageValue))
            } else {
                dao.updateSpecies(
                    existing.copy(
                        color = defaultSpecies.color,
                        emoji = defaultSpecies.emoji,
                        sortOrder = defaultSpecies.sortOrder,
                        isDefault = true,
                        isArchived = false,
                    ),
                )
            }
        }
    }

    suspend fun startSession(
        profile: ObservationProfile = ObservationProfile.WildlifeWatching,
        fieldLocationName: String? = null,
    ): Long {
        val active = dao.getActiveSession()
        if (active != null) return active.id

        val now = clock()
        return dao.insertSession(
            ObservationSessionEntity(
                startedAt = now,
                profileKey = profile.storageValue,
                lastActivityAt = now,
                title = profile.label,
                fieldLocationName = fieldLocationName.clean(),
            ),
        )
    }

    suspend fun endActiveSession(notes: String? = null, tags: List<String> = emptyList()) {
        val active = dao.getActiveSession() ?: return
        dao.updateSession(
            active.copy(
                endedAt = clock(),
                notes = notes?.trim()?.takeIf { it.isNotEmpty() } ?: active.notes,
                tags = SessionTagCodec.encode(tags) ?: active.tags,
            ),
        )
    }

    suspend fun appendActiveSessionNote(note: String) {
        val active = dao.getActiveSession() ?: return
        val cleanNote = note.clean() ?: return
        dao.updateSession(
            active.copy(
                notes = active.notes.withAppendedNote(cleanNote),
            ),
        )
        touchSession(active.id)
    }

    suspend fun addQuickObservation(speciesId: Long): Long {
        return addObservation(
            speciesId = speciesId,
            quantity = 1,
            notes = null,
            sex = null,
            ageClass = null,
            behavior = null,
        )
    }

    suspend fun addObservation(
        speciesId: Long,
        quantity: Int,
        notes: String?,
        sex: String?,
        ageClass: String?,
        behavior: String?,
    ): Long {
        val sessionId = startSession()
        val observationId = dao.insertObservation(
            ObservationEntity(
                sessionId = sessionId,
                speciesId = speciesId,
                observedAt = clock(),
                quantity = quantity.coerceAtLeast(1),
                notes = notes.clean(),
                sex = sex.clean(),
                ageClass = ageClass.clean(),
                behavior = behavior.clean(),
            ),
        )
        touchSession(sessionId)
        return observationId
    }

    suspend fun undoLastObservation() {
        val active = dao.getActiveSession() ?: return
        val last = dao.getLastObservationForSession(active.id) ?: return
        dao.deleteObservation(last.id)
        touchSession(active.id)
    }

    suspend fun decrementSpeciesCount(speciesId: Long) {
        val active = dao.getActiveSession() ?: return
        val observation = dao.getLastObservationForSessionAndSpecies(active.id, speciesId) ?: return
        if (observation.quantity > 1) {
            dao.updateObservationQuantity(observation.id, observation.quantity - 1)
        } else {
            dao.deleteObservation(observation.id)
        }
        touchSession(active.id)
    }

    suspend fun deleteObservation(observationId: Long) {
        dao.deleteObservation(observationId)
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSession(sessionId)
    }

    fun countsForSession(
        sessionId: Long,
        profile: ObservationProfile = ObservationProfile.WildlifeWatching,
    ) = dao.observeCountsForSession(sessionId, profile.storageValue)

    fun photosForSession(sessionId: Long) = dao.observePhotosForSession(sessionId)

    fun recentObservationsForSession(sessionId: Long) = dao.observeRecentObservations(sessionId)

    fun observationsForSession(sessionId: Long) = dao.observeRecentObservations(sessionId, limit = 100)

    fun sessionSummariesBetween(startAt: Long, endAt: Long) = dao.observeSessionSummariesBetween(startAt, endAt)

    fun speciesCountsBetween(startAt: Long, endAt: Long) = dao.observeSpeciesCountsBetween(startAt, endAt)

    fun dailySpeciesCountsBetween(startAt: Long, endAt: Long, dayMillis: Long) =
        dao.observeDailySpeciesCountsBetween(startAt, endAt, dayMillis)

    suspend fun addPhoto(
        uri: String,
        observationId: Long? = null,
        caption: String? = null,
    ): Long {
        return addMedia(
            uri = uri,
            mediaType = "photo",
            observationId = observationId,
            caption = caption,
        )
    }

    suspend fun addMedia(
        uri: String,
        mediaType: String,
        observationId: Long? = null,
        caption: String? = null,
    ): Long {
        val sessionId = observationId
            ?.let { id -> dao.getObservation(id)?.sessionId }
            ?: startSession()

        val mediaId = dao.insertPhoto(
            ObservationPhotoEntity(
                observationId = observationId,
                sessionId = sessionId,
                uri = uri,
                capturedAt = clock(),
                mediaType = mediaType.clean() ?: "photo",
                caption = caption.clean(),
            ),
        )
        touchSession(sessionId)
        return mediaId
    }

    suspend fun updateSessionEnvironment(sessionId: Long, environment: SessionEnvironment) {
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(
            session.copy(
                latitude = environment.latitude ?: session.latitude,
                longitude = environment.longitude ?: session.longitude,
                locationAccuracyMeters = environment.locationAccuracyMeters ?: session.locationAccuracyMeters,
                locationProvider = environment.locationProvider ?: session.locationProvider,
                elevationFeet = environment.elevationFeet ?: session.elevationFeet,
                sunsetAt = environment.sunsetAt ?: session.sunsetAt,
                weatherSummary = environment.weatherSummary ?: session.weatherSummary,
                temperatureF = environment.temperatureF ?: session.temperatureF,
                windSummary = environment.windSummary ?: session.windSummary,
                pressureInHg = environment.pressureInHg ?: session.pressureInHg,
                humidityPercent = environment.humidityPercent ?: session.humidityPercent,
                dewPointF = environment.dewPointF ?: session.dewPointF,
                visibilityMiles = environment.visibilityMiles ?: session.visibilityMiles,
                precipitationProbabilityPercent = environment.precipitationProbabilityPercent
                    ?: session.precipitationProbabilityPercent,
                forecastSummary = environment.forecastSummary ?: session.forecastSummary,
                moonPhase = environment.moonPhase ?: session.moonPhase,
                moonIlluminationPercent = environment.moonIlluminationPercent ?: session.moonIlluminationPercent,
                environmentCapturedAt = environment.capturedAt,
                environmentStatus = environment.status,
                environmentSchemaVersion = environment.schemaVersion,
                environmentAttemptCount = maxOf(
                    session.environmentAttemptCount ?: 0,
                    environment.attemptCount,
                ),
                locationCapturedAt = environment.locationCapturedAt ?: session.locationCapturedAt,
                weatherObservedAt = environment.weatherObservedAt ?: session.weatherObservedAt,
                elevationSource = environment.elevationSource ?: session.elevationSource,
                weatherSource = environment.weatherSource ?: session.weatherSource,
            ),
        )
    }

    suspend fun retrySessionEnvironment(
        context: Context,
        sessionId: Long,
    ): EnvironmentRetryResult? {
        val session = dao.getSession(sessionId) ?: return null
        if (
            EnvironmentCaptureContract.manualRetryEligibility(session) !=
            ManualEnvironmentRetryEligibility.Eligible
        ) {
            return null
        }

        val before = EnvironmentCaptureContract.evaluate(session)
        val environment = SessionEnvironmentFetcher.backfill(
            context = context.applicationContext,
            session = session,
        )
        updateSessionEnvironment(sessionId, environment)
        val updated = dao.getSession(sessionId) ?: return null
        val after = EnvironmentCaptureContract.evaluate(updated)
        return EnvironmentRetryResult(
            sessionId = sessionId,
            beforeCapturedFields = before.capturedFields,
            afterCapturedFields = after.capturedFields,
            isComplete = after.isComplete,
        )
    }

    suspend fun addSpecies(
        profile: ObservationProfile = ObservationProfile.WildlifeWatching,
        name: String,
        color: Long = 0xFF386641,
        emoji: String? = null,
    ): Long {
        if (!profile.isCustom) return -1
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return -1

        return dao.insertSpecies(
            SpeciesEntity(
                profileKey = profile.storageValue,
                name = cleanName,
                color = color,
                emoji = emoji.clean(),
                sortOrder = dao.getMaxSpeciesSortOrder(profile.storageValue) + 1,
                isDefault = false,
            ),
        )
    }

    suspend fun archiveSpecies(speciesId: Long) {
        val species = dao.getSpecies(speciesId) ?: return
        if (species.profileKey.startsWith("custom")) {
            dao.archiveSpecies(speciesId)
        }
    }

    suspend fun updateSpecies(
        profile: ObservationProfile,
        speciesId: Long,
        name: String,
        color: Long,
    ): Boolean {
        if (!profile.isCustom) return false
        val species = dao.getSpecies(speciesId) ?: return false
        if (species.profileKey != profile.storageValue) return false
        val cleanName = name.trim().replace(Regex("\\s+"), " ").take(40)
        if (cleanName.isBlank()) return false
        val conflicting = dao.getSpeciesByName(profile.storageValue, cleanName)
        if (conflicting != null && conflicting.id != speciesId) return false
        dao.updateSpecies(species.copy(name = cleanName, color = color, isDefault = false))
        return true
    }

    suspend fun moveSpecies(
        speciesId: Long,
        direction: Int,
        profile: ObservationProfile = ObservationProfile.WildlifeWatching,
    ) {
        if (!profile.isCustom) return
        if (direction == 0) return
        val species = dao.getActiveSpecies(profile.storageValue)
        val currentIndex = species.indexOfFirst { it.id == speciesId }
        if (currentIndex == -1) return

        moveSpeciesToIndex(speciesId, currentIndex + direction, profile)
    }

    suspend fun moveSpeciesToIndex(
        speciesId: Long,
        targetIndex: Int,
        profile: ObservationProfile = ObservationProfile.WildlifeWatching,
    ) {
        if (!profile.isCustom) return
        val species = dao.getActiveSpecies(profile.storageValue)
        val currentIndex = species.indexOfFirst { it.id == speciesId }
        if (currentIndex == -1 || species.isEmpty()) return

        val targetIndex = targetIndex.coerceIn(species.indices)
        if (targetIndex == currentIndex) return

        val reordered = species.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }

        reordered.forEachIndexed { index, item ->
            dao.updateSpeciesSortOrder(item.id, (index + 1) * 10)
        }
    }

    suspend fun endInactiveActiveSessionIfNeeded(): Boolean {
        val active = dao.getActiveSession() ?: return false
        val lastActivityAt = active.lastActivityAt ?: active.startedAt
        val autoEndAt = lastActivityAt + inactivityLimitMillis
        if (clock() < autoEndAt) return false

        dao.updateSession(
            active.copy(
                endedAt = autoEndAt,
                notes = active.notes.withAppendedNote("Auto-ended after 3 hours of inactivity."),
            ),
        )
        return true
    }

    suspend fun hasSpecies(profile: ObservationProfile = ObservationProfile.WildlifeWatching): Boolean =
        dao.getActiveSpecies(profile.storageValue).isNotEmpty()

    suspend fun currentSessionId(): Long? = activeSession.first()?.id

    suspend fun exportObservationsCsv(
        context: Context,
        privacy: ExportPrivacyOptions = ExportPrivacyOptions.Full,
    ): Uri {
        val rows = dao.getObservationExportRows()
        return ObservationCsvExporter.write(
            context = context,
            rows = rows,
            createdAt = clock(),
            privacy = privacy,
        )
    }

    suspend fun buildObservationsCsv(
        privacy: ExportPrivacyOptions = ExportPrivacyOptions.Full,
    ): CsvExport {
        val rows = dao.getObservationExportRows()
        return ObservationCsvExporter.build(
            rows = rows,
            createdAt = clock(),
            privacy = privacy,
        )
    }

    suspend fun exportPrivateSharePackage(
        context: Context,
        privacy: ExportPrivacyOptions,
    ): Uri {
        val createdAt = clock()
        val csvExport = ObservationCsvExporter.build(
            rows = dao.getObservationExportRows(),
            createdAt = createdAt,
            privacy = privacy,
        )
        val media = dao.getAllMediaForBackup()
        return withContext(Dispatchers.IO) {
            DataPackageExporter.writePrivateShare(
                context = context.applicationContext,
                csvExport = csvExport,
                media = media,
                createdAt = createdAt,
            )
        }
    }

    suspend fun exportDataPackage(
        context: Context,
        themeMode: AppThemeMode,
        autoBackupEnabled: Boolean = true,
        mediaSaveLocation: MediaSaveLocation = MediaSaveLocation.CameraRoll,
        hapticFeedbackEnabled: Boolean = true,
        environmentCaptureEnabled: Boolean = true,
        activeProfile: ObservationProfile = ObservationProfile.WildlifeWatching,
        customProfiles: List<ObservationProfile> = emptyList(),
        sessionTags: List<String> = emptyList(),
        fieldLocations: List<String> = emptyList(),
        selectedFieldLocation: String? = null,
        sharedLocationPrecision: SharedLocationPrecision = SharedLocationPrecision.Approximate,
        includeNamedFieldLocationsInShares: Boolean = false,
        fieldModeEnabled: Boolean = false,
        keepScreenAwake: Boolean = true,
    ): Uri {
        val createdAt = clock()
        val backupArchive = buildBackupArchive(
            context = context.applicationContext,
            themeMode = themeMode,
            autoBackupEnabled = autoBackupEnabled,
            mediaSaveLocation = mediaSaveLocation,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            environmentCaptureEnabled = environmentCaptureEnabled,
            activeProfile = activeProfile,
            customProfiles = customProfiles,
            sessionTags = sessionTags,
            fieldLocations = fieldLocations,
            selectedFieldLocation = selectedFieldLocation,
            sharedLocationPrecision = sharedLocationPrecision,
            includeNamedFieldLocationsInShares = includeNamedFieldLocationsInShares,
            fieldModeEnabled = fieldModeEnabled,
            keepScreenAwake = keepScreenAwake,
        )
        val csvExport = ObservationCsvExporter.build(
            rows = dao.getObservationExportRows(),
            createdAt = createdAt,
        )
        val media = dao.getAllMediaForBackup()
        return withContext(Dispatchers.IO) {
            DataPackageExporter.write(
                context = context.applicationContext,
                backupArchive = backupArchive,
                csvExport = csvExport,
                media = media,
                createdAt = createdAt,
            )
        }
    }

    suspend fun writeVerifiedBackup(
        context: Context,
        themeMode: AppThemeMode,
        autoBackupEnabled: Boolean = true,
        mediaSaveLocation: MediaSaveLocation = MediaSaveLocation.CameraRoll,
        hapticFeedbackEnabled: Boolean = true,
        environmentCaptureEnabled: Boolean = true,
        activeProfile: ObservationProfile = ObservationProfile.WildlifeWatching,
        customProfiles: List<ObservationProfile> = emptyList(),
        sessionTags: List<String> = emptyList(),
        fieldLocations: List<String> = emptyList(),
        selectedFieldLocation: String? = null,
        backupDestination: BackupDestination = BackupDestination.PhoneOnly,
        googleDriveFolderUri: String? = null,
        sharedLocationPrecision: SharedLocationPrecision = SharedLocationPrecision.Approximate,
        includeNamedFieldLocationsInShares: Boolean = false,
        fieldModeEnabled: Boolean = false,
        keepScreenAwake: Boolean = true,
    ): BackupWriteResult {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val archive = buildBackupArchive(
            context = context,
            themeMode = themeMode,
            autoBackupEnabled = autoBackupEnabled,
            mediaSaveLocation = mediaSaveLocation,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            environmentCaptureEnabled = environmentCaptureEnabled,
            activeProfile = activeProfile,
            customProfiles = customProfiles,
            sessionTags = sessionTags,
            fieldLocations = fieldLocations,
            selectedFieldLocation = selectedFieldLocation,
            sharedLocationPrecision = sharedLocationPrecision,
            includeNamedFieldLocationsInShares = includeNamedFieldLocationsInShares,
            fieldModeEnabled = fieldModeEnabled,
            keepScreenAwake = keepScreenAwake,
            appVersionName = packageInfo.versionName ?: "unknown",
            appVersionCode = versionCode,
        )

        return withContext(Dispatchers.IO) {
            DeerDeckBackupStorage.writeVerified(
                context = context.applicationContext,
                archive = archive,
                destination = backupDestination,
                googleDriveFolderUri = googleDriveFolderUri,
            )
        }
    }

    suspend fun buildBackupArchive(
        context: Context,
        themeMode: AppThemeMode,
        autoBackupEnabled: Boolean = true,
        mediaSaveLocation: MediaSaveLocation = MediaSaveLocation.CameraRoll,
        hapticFeedbackEnabled: Boolean = true,
        environmentCaptureEnabled: Boolean = true,
        activeProfile: ObservationProfile = ObservationProfile.WildlifeWatching,
        customProfiles: List<ObservationProfile> = emptyList(),
        sessionTags: List<String> = emptyList(),
        fieldLocations: List<String> = emptyList(),
        selectedFieldLocation: String? = null,
        appVersionName: String? = null,
        appVersionCode: Long? = null,
        sharedLocationPrecision: SharedLocationPrecision = SharedLocationPrecision.Approximate,
        includeNamedFieldLocationsInShares: Boolean = false,
        fieldModeEnabled: Boolean = false,
        keepScreenAwake: Boolean = true,
    ) = DeerDeckBackupJson.build(
        createdAt = clock(),
        appVersionName = appVersionName ?: context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?: "unknown",
        appVersionCode = appVersionCode ?: context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        },
        themeMode = themeMode,
        autoBackupEnabled = autoBackupEnabled,
        mediaSaveLocation = mediaSaveLocation.storageValue,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        environmentCaptureEnabled = environmentCaptureEnabled,
        activeProfile = activeProfile.storageValue,
        customProfiles = customProfiles,
        sessionTags = sessionTags,
        fieldLocations = fieldLocations,
        selectedFieldLocation = selectedFieldLocation,
        sharedLocationPrecision = sharedLocationPrecision,
        includeNamedFieldLocationsInShares = includeNamedFieldLocationsInShares,
        fieldModeEnabled = fieldModeEnabled,
        keepScreenAwake = keepScreenAwake,
        wildlife = dao.getAllSpeciesForBackup(),
        sessions = dao.getAllSessionsForBackup(),
        observations = dao.getAllObservationsForBackup(),
        media = dao.getAllMediaForBackup(),
    )

    suspend fun restoreDataPackage(
        context: Context,
        sourceUri: Uri,
        themeMode: AppThemeMode,
        autoBackupEnabled: Boolean = true,
        mediaSaveLocation: MediaSaveLocation = MediaSaveLocation.CameraRoll,
        hapticFeedbackEnabled: Boolean = true,
        environmentCaptureEnabled: Boolean = true,
        activeProfile: ObservationProfile,
        customProfiles: List<ObservationProfile> = emptyList(),
        sessionTags: List<String>,
        fieldLocations: List<String> = emptyList(),
        selectedFieldLocation: String? = null,
        sharedLocationPrecision: SharedLocationPrecision = SharedLocationPrecision.Approximate,
        includeNamedFieldLocationsInShares: Boolean = false,
        fieldModeEnabled: Boolean = false,
        keepScreenAwake: Boolean = true,
    ): RestoreResult {
        val appContext = context.applicationContext
        val prepared = withContext(Dispatchers.IO) {
            DeerDeckRestorePackage.prepare(appContext, sourceUri)
        }
        try {
            val safetyArchive = buildBackupArchive(
                context = appContext,
                themeMode = themeMode,
                autoBackupEnabled = autoBackupEnabled,
                mediaSaveLocation = mediaSaveLocation,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                environmentCaptureEnabled = environmentCaptureEnabled,
                activeProfile = activeProfile,
                customProfiles = customProfiles,
                sessionTags = sessionTags,
                fieldLocations = fieldLocations,
                selectedFieldLocation = selectedFieldLocation,
                sharedLocationPrecision = sharedLocationPrecision,
                includeNamedFieldLocationsInShares = includeNamedFieldLocationsInShares,
                fieldModeEnabled = fieldModeEnabled,
                keepScreenAwake = keepScreenAwake,
            )
            val safetyBackup = withContext(Dispatchers.IO) {
                DeerDeckBackupStorage.writeVerified(
                    context = appContext,
                    archive = safetyArchive,
                    destination = BackupDestination.PhoneOnly,
                )
            }
            val (restoredDataset, restoredMediaCount) = withContext(Dispatchers.IO) {
                prepared.withPersistedMedia(appContext)
            }
            dao.replaceDataset(restoredDataset)
            return RestoreResult(
                counts = BackupCounts(
                    wildlife = restoredDataset.wildlife.size,
                    sessions = restoredDataset.sessions.size,
                    observations = restoredDataset.observations.size,
                    media = restoredDataset.media.size,
                ),
                settings = restoredDataset.settings,
                safetyBackupPath = safetyBackup.displayPath,
                restoredMedia = restoredMediaCount,
                unavailableMedia = restoredDataset.media.size - restoredMediaCount,
            )
        } finally {
            withContext(Dispatchers.IO) { prepared.close() }
        }
    }

    suspend fun replaceDataset(dataset: BackupDataset) {
        dao.replaceDataset(dataset)
    }

    suspend fun eraseLocalDataset(context: Context) {
        dao.clearDataset()
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val externalMediaRoots = listOfNotNull(
                appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            )
            val mediaDirectories = externalMediaRoots.flatMap { root ->
                listOf(
                    File(root, "DeerDeck"),
                    File(root, "withrow-deerdeck-restored"),
                )
            }
            (mediaDirectories + listOf(
                File(appContext.filesDir, "DeerDeck"),
                File(appContext.cacheDir, "exports"),
            )).forEach { directory ->
                if (directory.exists() && !directory.deleteRecursively()) {
                    throw IllegalStateException("Could not remove ${directory.name} data.")
                }
            }
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun touchSession(sessionId: Long) {
        dao.updateSessionLastActivity(sessionId, clock())
    }

    private fun String?.withAppendedNote(note: String): String {
        val existing = this?.trim().orEmpty()
        return if (existing.isBlank()) note else "$existing\n$note"
    }
}
