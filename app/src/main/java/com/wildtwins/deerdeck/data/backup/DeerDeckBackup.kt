package com.wildtwins.deerdeck.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import com.wildtwins.deerdeck.data.settings.BackupDestination
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class BackupCounts(
    val wildlife: Int,
    val sessions: Int,
    val observations: Int,
    val media: Int,
)

data class BackupArchive(
    val fileName: String,
    val createdAt: Long,
    val content: String,
    val payloadChecksumSha256: String,
    val fileChecksumSha256: String,
    val counts: BackupCounts,
)

data class BackupSettings(
    val themeMode: String,
    val autoBackupEnabled: Boolean? = null,
    val mediaSaveLocation: String? = null,
    val hapticFeedbackEnabled: Boolean? = null,
    val environmentCaptureEnabled: Boolean? = null,
    val activeProfile: String? = null,
    val customProfiles: List<BackupProfile>? = null,
    val sessionTags: List<String>? = null,
    val fieldLocations: List<String>? = null,
    val selectedFieldLocation: String? = null,
    val sharedLocationPrecision: String? = null,
    val includeNamedFieldLocationsInShares: Boolean? = null,
    val fieldModeEnabled: Boolean? = null,
    val keepScreenAwake: Boolean? = null,
)

data class BackupProfile(
    val storageValue: String,
    val label: String,
)

data class BackupDataset(
    val settings: BackupSettings,
    val wildlife: List<SpeciesEntity>,
    val sessions: List<ObservationSessionEntity>,
    val observations: List<ObservationEntity>,
    val media: List<ObservationPhotoEntity>,
)

data class BackupWriteResult(
    val createdAt: Long,
    val fileName: String,
    val displayPath: String,
    val fileChecksumSha256: String,
    val payloadChecksumSha256: String,
    val counts: BackupCounts,
    val warnings: List<String> = emptyList(),
)

object DeerDeckBackupJson {
    private const val FormatName = "withrow-deerdeck-backup"
    private const val FormatVersion = 1
    private const val DatabaseSchemaVersion = 11
    private val FileTimestampFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss-SSS", Locale.US)
        .withZone(ZoneOffset.UTC)

    fun build(
        createdAt: Long,
        appVersionName: String,
        appVersionCode: Long,
        themeMode: AppThemeMode,
        autoBackupEnabled: Boolean = true,
        mediaSaveLocation: String? = null,
        hapticFeedbackEnabled: Boolean = true,
        environmentCaptureEnabled: Boolean = true,
        activeProfile: String? = null,
        customProfiles: List<ObservationProfile> = emptyList(),
        sessionTags: List<String> = emptyList(),
        fieldLocations: List<String> = emptyList(),
        selectedFieldLocation: String? = null,
        sharedLocationPrecision: SharedLocationPrecision = SharedLocationPrecision.Approximate,
        includeNamedFieldLocationsInShares: Boolean = false,
        fieldModeEnabled: Boolean = false,
        keepScreenAwake: Boolean = true,
        wildlife: List<SpeciesEntity>,
        sessions: List<ObservationSessionEntity>,
        observations: List<ObservationEntity>,
        media: List<ObservationPhotoEntity>,
    ): BackupArchive {
        val counts = BackupCounts(
            wildlife = wildlife.size,
            sessions = sessions.size,
            observations = observations.size,
            media = media.size,
        )
        val payload = JSONObject()
            .put("app", appJson(appVersionName, appVersionCode))
            .put(
                "settings",
                JSONObject()
                    .put("themeMode", themeMode.storageValue)
                    .put("autoBackupEnabled", autoBackupEnabled)
                    .putNullable("mediaSaveLocation", mediaSaveLocation)
                    .put("hapticFeedbackEnabled", hapticFeedbackEnabled)
                    .put("environmentCaptureEnabled", environmentCaptureEnabled)
                    .putNullable("activeProfile", activeProfile)
                    .put(
                        "customProfiles",
                        JSONArray().apply {
                            customProfiles.filter { it.isCustom }.forEach { profile ->
                                put(
                                    JSONObject()
                                        .put("key", profile.storageValue)
                                        .put("label", profile.label),
                                )
                            }
                        },
                    )
                    .put("sessionTags", JSONArray(sessionTags))
                    .put("fieldLocations", JSONArray(fieldLocations))
                    .putNullable("selectedFieldLocation", selectedFieldLocation)
                    .put("sharedLocationPrecision", sharedLocationPrecision.storageValue)
                    .put("includeNamedFieldLocationsInShares", includeNamedFieldLocationsInShares)
                    .put("fieldModeEnabled", fieldModeEnabled)
                    .put("keepScreenAwake", keepScreenAwake),
            )
            .put("wildlife", wildlifeJson(wildlife))
            .put("sessions", sessionsJson(sessions))
            .put("observations", observationsJson(observations))
            .put("media", mediaJson(media))

        val payloadText = payload.toString()
        val payloadChecksum = payloadText.sha256()
        val root = JSONObject()
            .put("format", FormatName)
            .put("formatVersion", FormatVersion)
            .put("createdAt", createdAt)
            .put("createdAtIso", Instant.ofEpochMilli(createdAt).toString())
            .put("payloadChecksumSha256", payloadChecksum)
            .put("counts", counts.toJson())
            .put("payload", payload)

        val content = root.toString(2)
        val fileChecksum = content.sha256()
        return BackupArchive(
            fileName = "withrow-deerdeck-backup-${FileTimestampFormatter.format(Instant.ofEpochMilli(createdAt))}.json",
            createdAt = createdAt,
            content = content,
            payloadChecksumSha256 = payloadChecksum,
            fileChecksumSha256 = fileChecksum,
            counts = counts,
        )
    }

    fun verifyReadBack(
        content: String,
        expectedFileChecksum: String,
        expectedCounts: BackupCounts,
    ) {
        if (content.sha256() != expectedFileChecksum) {
            throw IOException("Backup verification failed: file checksum mismatch.")
        }

        val dataset = parse(content)
        val root = JSONObject(content)
        val counts = root.getJSONObject("counts")
        if (
            counts.optInt("wildlife") != expectedCounts.wildlife ||
            counts.optInt("sessions") != expectedCounts.sessions ||
            counts.optInt("observations") != expectedCounts.observations ||
            counts.optInt("media") != expectedCounts.media
        ) {
            throw IOException("Backup verification failed: backup counts did not match the database.")
        }
        dataset.validateRelationships()
    }

    fun parse(content: String): BackupDataset {
        val root = runCatching { JSONObject(content) }
            .getOrElse { throw IOException("Restore failed: the backup is not valid JSON.", it) }
        if (root.optString("format") != FormatName) {
            throw IOException("Restore failed: this is not a Withrow DeerDeck backup.")
        }
        if (root.optInt("formatVersion") != FormatVersion) {
            throw IOException("Restore failed: unsupported backup version.")
        }

        val payload = root.optJSONObject("payload")
            ?: throw IOException("Restore failed: backup payload is missing.")
        val expectedPayloadChecksum = root.optString("payloadChecksumSha256")
        if (expectedPayloadChecksum.isBlank() || payload.toString().sha256() != expectedPayloadChecksum) {
            throw IOException("Restore failed: backup payload checksum mismatch.")
        }

        val settingsJson = payload.optJSONObject("settings") ?: JSONObject()
        val dataset = BackupDataset(
            settings = BackupSettings(
                themeMode = settingsJson.optString("themeMode", AppThemeMode.System.storageValue),
                autoBackupEnabled = settingsJson.optionalBoolean("autoBackupEnabled"),
                mediaSaveLocation = settingsJson.optNullableString("mediaSaveLocation"),
                hapticFeedbackEnabled = settingsJson.optionalBoolean("hapticFeedbackEnabled"),
                environmentCaptureEnabled = settingsJson.optionalBoolean("environmentCaptureEnabled"),
                activeProfile = settingsJson.optNullableString("activeProfile"),
                customProfiles = settingsJson.optJSONArray("customProfiles")?.objects { item ->
                    BackupProfile(
                        storageValue = item.getString("key"),
                        label = item.getString("label"),
                    )
                },
                sessionTags = settingsJson.optJSONArray("sessionTags")?.strings(),
                fieldLocations = settingsJson.optJSONArray("fieldLocations")?.strings(),
                selectedFieldLocation = settingsJson.optNullableString("selectedFieldLocation"),
                sharedLocationPrecision = settingsJson.optNullableString("sharedLocationPrecision"),
                includeNamedFieldLocationsInShares = settingsJson.optionalBoolean(
                    "includeNamedFieldLocationsInShares",
                ),
                fieldModeEnabled = settingsJson.optionalBoolean("fieldModeEnabled"),
                keepScreenAwake = settingsJson.optionalBoolean("keepScreenAwake"),
            ),
            wildlife = payload.requiredArray("wildlife").objects { item ->
                SpeciesEntity(
                    id = item.getLong("id"),
                    profileKey = item.optString("profileKey", "wildlife_watching"),
                    name = item.getString("name"),
                    color = item.getLong("color"),
                    emoji = item.optNullableString("emoji"),
                    sortOrder = item.getInt("sortOrder"),
                    isDefault = item.optBoolean("isDefault", false),
                    isArchived = item.optBoolean("isArchived", false),
                )
            },
            sessions = payload.requiredArray("sessions").objects { item ->
                ObservationSessionEntity(
                    id = item.getLong("id"),
                    startedAt = item.getLong("startedAt"),
                    endedAt = item.optNullableLong("endedAt"),
                    profileKey = item.optString("profileKey", "wildlife_watching"),
                    title = item.optNullableString("title"),
                    notes = item.optNullableString("notes"),
                    tags = item.optNullableString("tags"),
                    fieldLocationName = item.optNullableString("fieldLocationName"),
                    latitude = item.optNullableDouble("latitude"),
                    longitude = item.optNullableDouble("longitude"),
                    locationAccuracyMeters = item.optNullableDouble("locationAccuracyMeters"),
                    locationProvider = item.optNullableString("locationProvider"),
                    elevationFeet = item.optNullableDouble("elevationFeet"),
                    sunsetAt = item.optNullableLong("sunsetAt"),
                    weatherSummary = item.optNullableString("weatherSummary"),
                    temperatureF = item.optNullableDouble("temperatureF"),
                    windSummary = item.optNullableString("windSummary"),
                    pressureInHg = item.optNullableDouble("pressureInHg"),
                    humidityPercent = item.optNullableDouble("humidityPercent"),
                    dewPointF = item.optNullableDouble("dewPointF"),
                    visibilityMiles = item.optNullableDouble("visibilityMiles"),
                    precipitationProbabilityPercent = item.optNullableDouble("precipitationProbabilityPercent"),
                    forecastSummary = item.optNullableString("forecastSummary"),
                    moonPhase = item.optNullableString("moonPhase"),
                    moonIlluminationPercent = item.optNullableDouble("moonIlluminationPercent"),
                    environmentCapturedAt = item.optNullableLong("environmentCapturedAt"),
                    environmentStatus = item.optNullableString("environmentStatus"),
                    environmentSchemaVersion = item.optNullableInt("environmentSchemaVersion"),
                    environmentAttemptCount = item.optNullableInt("environmentAttemptCount"),
                    locationCapturedAt = item.optNullableLong("locationCapturedAt"),
                    weatherObservedAt = item.optNullableLong("weatherObservedAt"),
                    elevationSource = item.optNullableString("elevationSource"),
                    weatherSource = item.optNullableString("weatherSource"),
                    lastActivityAt = item.optNullableLong("lastActivityAt"),
                )
            },
            observations = payload.requiredArray("observations").objects { item ->
                ObservationEntity(
                    id = item.getLong("id"),
                    sessionId = item.getLong("sessionId"),
                    speciesId = item.getLong("wildlifeId"),
                    observedAt = item.getLong("observedAt"),
                    quantity = item.optInt("quantity", 1),
                    notes = item.optNullableString("notes"),
                    sex = item.optNullableString("sex"),
                    ageClass = item.optNullableString("ageClass"),
                    behavior = item.optNullableString("behavior"),
                )
            },
            media = payload.requiredArray("media").objects { item ->
                ObservationPhotoEntity(
                    id = item.getLong("id"),
                    observationId = item.optNullableLong("observationId"),
                    sessionId = item.getLong("sessionId"),
                    uri = item.getString("uri"),
                    capturedAt = item.getLong("capturedAt"),
                    mediaType = item.optString("mediaType", "photo"),
                    caption = item.optNullableString("caption"),
                )
            },
        )

        val expectedCounts = root.optJSONObject("counts")
            ?: throw IOException("Restore failed: backup counts are missing.")
        if (
            expectedCounts.optInt("wildlife") != dataset.wildlife.size ||
            expectedCounts.optInt("sessions") != dataset.sessions.size ||
            expectedCounts.optInt("observations") != dataset.observations.size ||
            expectedCounts.optInt("media") != dataset.media.size
        ) {
            throw IOException("Restore failed: backup counts do not match its payload.")
        }
        dataset.validateRelationships()
        return dataset
    }

    private fun appJson(appVersionName: String, appVersionCode: Long): JSONObject {
        return JSONObject()
            .put("versionName", appVersionName)
            .put("versionCode", appVersionCode)
            .put("databaseSchemaVersion", DatabaseSchemaVersion)
    }

    private fun wildlifeJson(wildlife: List<SpeciesEntity>): JSONArray {
        return JSONArray().apply {
            wildlife.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("profileKey", item.profileKey)
                        .put("name", item.name)
                        .put("color", item.color)
                        .putNullable("emoji", item.emoji)
                        .put("sortOrder", item.sortOrder)
                        .put("isDefault", item.isDefault)
                        .put("isArchived", item.isArchived),
                )
            }
        }
    }

    private fun sessionsJson(sessions: List<ObservationSessionEntity>): JSONArray {
        return JSONArray().apply {
            sessions.forEach { session ->
                put(
                    JSONObject()
                        .put("id", session.id)
                        .put("startedAt", session.startedAt)
                        .putNullable("endedAt", session.endedAt)
                        .put("profileKey", session.profileKey)
                        .putNullable("title", session.title)
                        .putNullable("notes", session.notes)
                        .putNullable("tags", session.tags)
                        .putNullable("fieldLocationName", session.fieldLocationName)
                        .putNullable("latitude", session.latitude)
                        .putNullable("longitude", session.longitude)
                        .putNullable("locationAccuracyMeters", session.locationAccuracyMeters)
                        .putNullable("locationProvider", session.locationProvider)
                        .putNullable("elevationFeet", session.elevationFeet)
                        .putNullable("sunsetAt", session.sunsetAt)
                        .putNullable("weatherSummary", session.weatherSummary)
                        .putNullable("temperatureF", session.temperatureF)
                        .putNullable("windSummary", session.windSummary)
                        .putNullable("pressureInHg", session.pressureInHg)
                        .putNullable("humidityPercent", session.humidityPercent)
                        .putNullable("dewPointF", session.dewPointF)
                        .putNullable("visibilityMiles", session.visibilityMiles)
                        .putNullable("precipitationProbabilityPercent", session.precipitationProbabilityPercent)
                        .putNullable("forecastSummary", session.forecastSummary)
                        .putNullable("moonPhase", session.moonPhase)
                        .putNullable("moonIlluminationPercent", session.moonIlluminationPercent)
                        .putNullable("environmentCapturedAt", session.environmentCapturedAt)
                        .putNullable("environmentStatus", session.environmentStatus)
                        .putNullable("environmentSchemaVersion", session.environmentSchemaVersion)
                        .putNullable("environmentAttemptCount", session.environmentAttemptCount)
                        .putNullable("locationCapturedAt", session.locationCapturedAt)
                        .putNullable("weatherObservedAt", session.weatherObservedAt)
                        .putNullable("elevationSource", session.elevationSource)
                        .putNullable("weatherSource", session.weatherSource)
                        .putNullable("lastActivityAt", session.lastActivityAt),
                )
            }
        }
    }

    private fun observationsJson(observations: List<ObservationEntity>): JSONArray {
        return JSONArray().apply {
            observations.forEach { observation ->
                put(
                    JSONObject()
                        .put("id", observation.id)
                        .put("sessionId", observation.sessionId)
                        .put("wildlifeId", observation.speciesId)
                        .put("observedAt", observation.observedAt)
                        .put("quantity", observation.quantity)
                        .putNullable("notes", observation.notes)
                        .putNullable("sex", observation.sex)
                        .putNullable("ageClass", observation.ageClass)
                        .putNullable("behavior", observation.behavior),
                )
            }
        }
    }

    private fun mediaJson(media: List<ObservationPhotoEntity>): JSONArray {
        return JSONArray().apply {
            media.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .putNullable("observationId", item.observationId)
                        .put("sessionId", item.sessionId)
                        .put("uri", item.uri)
                        .put("capturedAt", item.capturedAt)
                        .put("mediaType", item.mediaType)
                        .putNullable("caption", item.caption),
                )
            }
        }
    }

    private fun BackupCounts.toJson(): JSONObject {
        return JSONObject()
            .put("wildlife", wildlife)
            .put("sessions", sessions)
            .put("observations", observations)
            .put("media", media)
    }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
        return put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.requiredArray(name: String): JSONArray {
        return optJSONArray(name) ?: throw IOException("Restore failed: $name data is missing.")
    }

    private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> {
        return List(length()) { index -> transform(getJSONObject(index)) }
    }

    private fun JSONArray.strings(): List<String> {
        return List(length()) { index -> getString(index) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (!has(name) || isNull(name)) null else getString(name)
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        return if (!has(name) || isNull(name)) null else getLong(name)
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (!has(name) || isNull(name)) null else getInt(name)
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        return if (!has(name) || isNull(name)) null else getDouble(name)
    }

    private fun JSONObject.optionalBoolean(name: String): Boolean? {
        return if (!has(name) || isNull(name)) null else getBoolean(name)
    }

    private fun BackupDataset.validateRelationships() {
        if (wildlife.any { it.id <= 0 } || sessions.any { it.id <= 0 } || observations.any { it.id <= 0 } || media.any { it.id <= 0 }) {
            throw IOException("Restore failed: backup contains invalid record IDs.")
        }
        if (wildlife.map { it.id }.toSet().size != wildlife.size || sessions.map { it.id }.toSet().size != sessions.size ||
            observations.map { it.id }.toSet().size != observations.size || media.map { it.id }.toSet().size != media.size
        ) {
            throw IOException("Restore failed: backup contains duplicate record IDs.")
        }
        val wildlifeIds = wildlife.mapTo(mutableSetOf()) { it.id }
        val sessionIds = sessions.mapTo(mutableSetOf()) { it.id }
        val observationIds = observations.mapTo(mutableSetOf()) { it.id }
        if (observations.any { it.sessionId !in sessionIds || it.speciesId !in wildlifeIds }) {
            throw IOException("Restore failed: an observation references missing data.")
        }
        if (media.any { it.sessionId !in sessionIds || (it.observationId != null && it.observationId !in observationIds) }) {
            throw IOException("Restore failed: a media record references missing data.")
        }
    }
}

object DeerDeckBackupStorage {
    private const val DirectoryName = "Withrow DeerDeck"
    private const val MimeType = "application/json"

    fun writeVerified(
        context: Context,
        archive: BackupArchive,
        destination: BackupDestination = BackupDestination.PhoneOnly,
        googleDriveFolderUri: String? = null,
    ): BackupWriteResult {
        val appContext = context.applicationContext
        val writtenBackups = mutableListOf<WrittenBackup>()
        val warnings = mutableListOf<String>()

        fun writeAndVerify(label: String, writer: () -> WrittenBackup) {
            var written: WrittenBackup? = null
            try {
                val item = writer()
                written = item
                DeerDeckBackupJson.verifyReadBack(
                    content = item.readText(appContext),
                    expectedFileChecksum = archive.fileChecksumSha256,
                    expectedCounts = archive.counts,
                )
                writtenBackups += item
            } catch (error: Exception) {
                written?.let { item -> runCatching { item.delete(appContext) } }
                val detail = error.message?.takeIf { it.isNotBlank() } ?: "Backup failed."
                warnings += "$label: $detail"
            }
        }

        if (destination.usesPhone) {
            writeAndVerify("Phone backup") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    writeWithMediaStore(appContext, archive)
                } else {
                    writeLegacyFile(archive)
                }
            }
        }

        if (destination.usesDrive) {
            writeAndVerify("Google Drive") {
                val storedUri = googleDriveFolderUri?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Choose a Google Drive backup folder in Settings first.")
                val uri = Uri.parse(storedUri)
                if (!GoogleDriveDocumentProvider.isGoogleDriveTreeUri(appContext, uri)) {
                    throw IOException(
                        "The selected folder is not in Google Drive. Choose Drive from the folder picker's top-left menu.",
                    )
                }
                writeWithDocumentTree(appContext, uri, archive)
            }
        }

        if (writtenBackups.isEmpty()) {
            throw IOException(
                warnings.joinToString(separator = " ").ifBlank { "No backup destination is enabled." },
            )
        }

        return BackupWriteResult(
            createdAt = archive.createdAt,
            fileName = archive.fileName,
            displayPath = writtenBackups.joinToString(" + ") { it.displayPath },
            fileChecksumSha256 = archive.fileChecksumSha256,
            payloadChecksumSha256 = archive.payloadChecksumSha256,
            counts = archive.counts,
            warnings = warnings,
        )
    }

    private fun writeWithDocumentTree(
        context: Context,
        folderUri: Uri,
        archive: BackupArchive,
    ): WrittenBackup {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IOException("Could not open the selected Google Drive backup folder.")
        if (!folder.canWrite()) {
            throw IOException(
                "Folder access expired or is not writable. Choose the Google Drive folder again in Settings.",
            )
        }
        val file = folder.createFile(MimeType, archive.fileName)
            ?: throw IOException("Could not create backup file in the selected Google Drive folder.")
        try {
            context.contentResolver.openOutputStream(file.uri)?.use { stream ->
                stream.write(archive.content.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not write the Google Drive backup file.")
        } catch (error: Throwable) {
            runCatching { file.delete() }
            throw error
        }

        return WrittenBackup(
            uri = file.uri,
            file = null,
            displayPath = "${folder.name ?: "Google Drive"}/${archive.fileName}",
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(context: Context, archive: BackupArchive): WrittenBackup {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, archive.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DirectoryName/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create backup file in Downloads.")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(archive.content.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not open backup file for writing.")

            val completeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, completeValues, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }

        return WrittenBackup(
            uri = uri,
            file = null,
            displayPath = "${Environment.DIRECTORY_DOWNLOADS}/$DirectoryName/${archive.fileName}",
        )
    }

    private fun writeLegacyFile(archive: BackupArchive): WrittenBackup {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DirectoryName,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create backup folder: ${directory.absolutePath}")
        }

        val file = File(directory, archive.fileName)
        file.writeText(archive.content, Charsets.UTF_8)
        return WrittenBackup(
            uri = null,
            file = file,
            displayPath = file.absolutePath,
        )
    }

    private data class WrittenBackup(
        val uri: Uri?,
        val file: File?,
        val displayPath: String,
    ) {
        fun readText(context: Context): String {
            return when {
                uri != null -> context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IOException("Could not read backup file for verification.")
                file != null -> file.readText(Charsets.UTF_8)
                else -> throw IOException("Backup verification failed: no file handle.")
            }
        }

        fun delete(context: Context) {
            when {
                uri != null -> context.contentResolver.delete(uri, null, null)
                file != null -> file.delete()
            }
        }
    }
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
