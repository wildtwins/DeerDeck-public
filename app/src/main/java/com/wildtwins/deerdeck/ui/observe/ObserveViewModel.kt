package com.wildtwins.deerdeck.ui.observe

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.environment.EnvironmentCaptureContract
import com.wildtwins.deerdeck.data.environment.SessionEnvironmentFetcher
import com.wildtwins.deerdeck.data.environment.WeatherBackfillScheduler
import com.wildtwins.deerdeck.data.backup.BackupWriteResult
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.RecentObservation
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import com.wildtwins.deerdeck.data.settings.BackupDestination
import com.wildtwins.deerdeck.data.settings.BackupStatus
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val InactivityLimitMillis = 3 * 60 * 60 * 1_000L

data class ObserveUiState(
    val activeSession: ObservationSessionEntity? = null,
    val activeProfile: ObservationProfile = ObservationProfile.WildlifeWatching,
    val species: List<SpeciesEntity> = emptyList(),
    val counts: List<SpeciesCount> = emptyList(),
    val recentObservations: List<RecentObservation> = emptyList(),
    val mediaCount: Int = 0,
    val hapticFeedbackEnabled: Boolean = true,
    val fieldModeEnabled: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val environmentCaptureEnabled: Boolean = true,
    val sessionTags: List<String> = emptyList(),
    val fieldLocations: List<String> = emptyList(),
    val selectedFieldLocation: String? = null,
    val backupDestination: BackupDestination = BackupDestination.PhoneOnly,
    val googleDriveFolderUri: String? = null,
    val googleDriveBackupIssue: String? = null,
    val backupInProgress: Boolean = false,
    val isStartingSession: Boolean = false,
    val lastMessage: String? = null,
)

private data class ObserveFieldSettings(
    val activeProfile: ObservationProfile,
    val hapticFeedbackEnabled: Boolean,
    val fieldModeEnabled: Boolean,
    val keepScreenAwake: Boolean,
    val environmentCaptureEnabled: Boolean,
    val sessionTags: List<String>,
    val fieldLocations: List<String>,
    val selectedFieldLocation: String?,
    val backupDestination: BackupDestination,
    val googleDriveFolderUri: String?,
    val googleDriveBackupIssue: String?,
)

private data class ObserveInteractionSettings(
    val activeProfile: ObservationProfile,
    val hapticFeedbackEnabled: Boolean,
    val fieldModeEnabled: Boolean,
    val keepScreenAwake: Boolean,
    val environmentCaptureEnabled: Boolean,
)

private data class ObserveLocationSettings(
    val sessionTags: List<String>,
    val fieldLocations: List<String>,
    val selectedFieldLocation: String?,
)

private data class ActiveSessionActivity(
    val recentObservations: List<RecentObservation>,
    val mediaCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveViewModel(
    private val repository: DeerDeckRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val lastMessage = MutableStateFlow<String?>(null)
    private val sessionStarting = MutableStateFlow(false)
    private val backupInProgress = MutableStateFlow(false)
    private var inactivityJob: Job? = null
    private var backupContext = WeakReference<Context>(null)

    private val baseUiState = combine(
        repository.activeSession,
        combine(
            repository.activeSession,
            settingsRepository.activeProfile,
        ) { session, profile ->
            session?.let { settingsRepository.profileFromStorage(it.profileKey) } ?: profile
        }.flatMapLatest { profile ->
            repository.activeSpecies(profile)
        },
        combine(
            repository.activeSession,
            settingsRepository.activeProfile,
        ) { session, profile ->
            session to (session?.let { settingsRepository.profileFromStorage(it.profileKey) } ?: profile)
        }.flatMapLatest { (session, profile) ->
                session?.let {
                    repository.countsForSession(
                        sessionId = it.id,
                        profile = profile,
                    )
                } ?: flowOf(emptyList())
            },
        combine(
            repository.activeSession.flatMapLatest { session ->
                session?.let { repository.recentObservationsForSession(it.id) } ?: flowOf(emptyList())
            },
            repository.activeSession.flatMapLatest { session ->
                session?.let { repository.photosForSession(it.id) } ?: flowOf(emptyList())
            },
        ) { recentObservations, media ->
            ActiveSessionActivity(
                recentObservations = recentObservations,
                mediaCount = media.size,
            )
        },
        combine(
            combine(
                settingsRepository.activeProfile,
                settingsRepository.hapticFeedbackEnabled,
                settingsRepository.fieldModeEnabled,
                settingsRepository.keepScreenAwake,
                settingsRepository.environmentCaptureEnabled,
            ) { activeProfile, hapticFeedbackEnabled, fieldModeEnabled, keepScreenAwake, environmentCaptureEnabled ->
                ObserveInteractionSettings(
                    activeProfile,
                    hapticFeedbackEnabled,
                    fieldModeEnabled,
                    keepScreenAwake,
                    environmentCaptureEnabled,
                )
            },
            combine(
                settingsRepository.sessionTags,
                settingsRepository.fieldLocations,
                settingsRepository.selectedFieldLocation,
            ) { sessionTags, fieldLocations, selectedFieldLocation ->
                ObserveLocationSettings(sessionTags, fieldLocations, selectedFieldLocation)
            },
            combine(
                settingsRepository.backupDestination,
                settingsRepository.googleDriveFolderUri,
                settingsRepository.backupStatus,
            ) { destination, folderUri, status ->
                ObserveBackupSettings(
                    destination = destination,
                    folderUri = folderUri,
                    driveIssue = status.googleDriveIssue(),
                )
            },
        ) { interaction, location, backup ->
            ObserveFieldSettings(
                activeProfile = interaction.activeProfile,
                hapticFeedbackEnabled = interaction.hapticFeedbackEnabled,
                fieldModeEnabled = interaction.fieldModeEnabled,
                keepScreenAwake = interaction.keepScreenAwake,
                environmentCaptureEnabled = interaction.environmentCaptureEnabled,
                sessionTags = location.sessionTags,
                fieldLocations = location.fieldLocations,
                selectedFieldLocation = location.selectedFieldLocation,
                backupDestination = backup.destination,
                googleDriveFolderUri = backup.folderUri,
                googleDriveBackupIssue = backup.driveIssue,
            )
        },
    ) { session, species, counts, sessionActivity, fieldSettings ->
        ObserveUiState(
            activeSession = session,
            activeProfile = session
                ?.let { settingsRepository.profileFromStorage(it.profileKey) }
                ?: fieldSettings.activeProfile,
            species = species,
            counts = counts,
            recentObservations = sessionActivity.recentObservations,
            mediaCount = sessionActivity.mediaCount,
            hapticFeedbackEnabled = fieldSettings.hapticFeedbackEnabled,
            fieldModeEnabled = fieldSettings.fieldModeEnabled,
            keepScreenAwake = fieldSettings.keepScreenAwake,
            environmentCaptureEnabled = fieldSettings.environmentCaptureEnabled,
            sessionTags = fieldSettings.sessionTags,
            fieldLocations = fieldSettings.fieldLocations,
            selectedFieldLocation = fieldSettings.selectedFieldLocation,
            backupDestination = fieldSettings.backupDestination,
            googleDriveFolderUri = fieldSettings.googleDriveFolderUri,
            googleDriveBackupIssue = fieldSettings.googleDriveBackupIssue,
        )
    }

    val uiState: StateFlow<ObserveUiState> = combine(
        baseUiState,
        lastMessage,
        sessionStarting,
        backupInProgress,
    ) { state, message, starting, backingUp ->
        state.copy(
            isStartingSession = starting,
            lastMessage = message,
            backupInProgress = backingUp,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ObserveUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.activeProfile.collect { profile ->
                repository.ensureDefaultSpecies(
                    profile = profile,
                    customized = settingsRepository.isProfileCustomized(profile),
                )
            }
        }
        viewModelScope.launch {
            repository.activeSession.collect { session ->
                scheduleInactiveSessionStop(session)
            }
        }
    }

    fun startSession(context: Context? = null) {
        if (sessionStarting.value) return
        context?.applicationContext?.let { backupContext = WeakReference(it) }
        viewModelScope.launch {
            sessionStarting.value = true
            try {
                val profile = settingsRepository.activeProfile.value
                repository.ensureDefaultSpecies(
                    profile = profile,
                    customized = settingsRepository.isProfileCustomized(profile),
                )
                val appContext = context?.applicationContext
                val captureEnvironment =
                    appContext != null && settingsRepository.environmentCaptureEnabled.value
                val locationContext = if (captureEnvironment) {
                    SessionEnvironmentFetcher.captureLocationContext(appContext)
                } else {
                    null
                }
                val region = if (locationContext?.latitude != null && locationContext.longitude != null) {
                    repository.refreshLocationAwareSpecies(
                        profile = profile,
                        latitude = locationContext.latitude,
                        longitude = locationContext.longitude,
                    )
                } else {
                    null
                }
                val sessionId = repository.startSession(
                    profile = profile,
                    fieldLocationName = settingsRepository.selectedFieldLocation.value,
                )
                if (locationContext != null) {
                    repository.updateSessionEnvironment(sessionId, locationContext)
                }
                lastMessage.value = region?.let { "Session started with $it profile" } ?: "Session started"

                if (appContext != null) {
                    if (captureEnvironment) {
                        WeatherBackfillScheduler.schedule(appContext, sessionId)
                    }
                    val environment = if (captureEnvironment) {
                        SessionEnvironmentFetcher.capture(appContext)
                    } else {
                        SessionEnvironmentFetcher.captureWithoutLocation()
                    }
                    repository.updateSessionEnvironment(sessionId, environment)
                    val completeness = EnvironmentCaptureContract.evaluate(environment)
                    if (completeness.isComplete) {
                        WeatherBackfillScheduler.cancel(appContext, sessionId)
                    }
                    lastMessage.value = when (environment.status) {
                        "Complete" -> region?.let { "Session conditions saved; $it profile loaded" }
                            ?: "Session conditions saved"
                        "Disabled" -> "Session started without location"
                        else -> "Session started: ${completeness.displayText()}; retry scheduled"
                    }
                }
            } finally {
                sessionStarting.value = false
            }
        }
    }

    fun endSession(context: Context? = null, notes: String? = null, tags: List<String> = emptyList()) {
        context?.applicationContext?.let { backupContext = WeakReference(it) }
        viewModelScope.launch {
            repository.endActiveSession(notes, tags)
            lastMessage.value = backupEndedSession(
                context?.applicationContext ?: backupContext.get(),
                "Session saved",
            )
        }
    }

    fun reconnectGoogleDrive(context: Context, folderUri: Uri) {
        if (backupInProgress.value) return
        val appContext = context.applicationContext
        backupContext = WeakReference(appContext)
        settingsRepository.setGoogleDriveFolderUri(folderUri.toString())
        viewModelScope.launch {
            backupInProgress.value = true
            try {
                val result = writeConfiguredBackup(appContext)
                settingsRepository.recordBackupSuccess(result)
                lastMessage.value = if (result.warnings.isEmpty()) {
                    "Google Drive reconnected; backup verified"
                } else {
                    "Backup saved, but one destination still needs attention"
                }
            } catch (error: Throwable) {
                settingsRepository.recordBackupFailure(error.readableMessage())
                lastMessage.value = "Google Drive backup failed: ${error.readableMessage()}"
            } finally {
                backupInProgress.value = false
            }
        }
    }

    fun setSessionTags(tags: List<String>) {
        settingsRepository.setSessionTags(tags)
    }

    fun resetSessionTags() {
        settingsRepository.resetSessionTags()
    }

    fun selectFieldLocation(name: String?) {
        settingsRepository.selectFieldLocation(name)
    }

    fun addFieldLocation(name: String) {
        settingsRepository.addFieldLocation(name)
        lastMessage.value = "Location saved"
    }

    fun removeFieldLocation(name: String) {
        settingsRepository.removeFieldLocation(name)
        lastMessage.value = "Location removed"
    }

    fun addQuickObservation(species: SpeciesEntity) {
        viewModelScope.launch {
            repository.addQuickObservation(species.id)
            lastMessage.value = "${species.name} +1"
        }
    }

    fun addDetailedObservation(
        species: SpeciesEntity,
        quantity: Int,
        notes: String,
        sex: String,
        ageClass: String,
        behavior: String,
        onSaved: (Long) -> Unit = {},
    ) {
        viewModelScope.launch {
            val observationId = repository.addObservation(
                speciesId = species.id,
                quantity = quantity,
                notes = notes,
                sex = sex,
                ageClass = ageClass,
                behavior = behavior,
            )
            lastMessage.value = "${species.name} +${quantity.coerceAtLeast(1)}"
            onSaved(observationId)
        }
    }

    fun undoLast() {
        viewModelScope.launch {
            repository.undoLastObservation()
            lastMessage.value = "Last observation removed"
        }
    }

    fun decrementSpeciesCount(speciesId: Long) {
        viewModelScope.launch {
            repository.decrementSpeciesCount(speciesId)
            lastMessage.value = "Count corrected"
        }
    }

    fun deleteObservation(observation: RecentObservation) {
        viewModelScope.launch {
            repository.deleteObservation(observation.id)
            lastMessage.value = "${observation.speciesName} removed"
        }
    }

    fun attachPhoto(uri: String, observationId: Long? = null) {
        viewModelScope.launch {
            repository.addPhoto(uri = uri, observationId = observationId)
            lastMessage.value = "Photo saved"
        }
    }

    fun attachMedia(uri: String, mediaType: String, observationId: Long? = null) {
        viewModelScope.launch {
            repository.addMedia(uri = uri, mediaType = mediaType, observationId = observationId)
            lastMessage.value = when {
                mediaType.equals("video", ignoreCase = true) -> "Video saved"
                mediaType.equals("audio", ignoreCase = true) -> "Audio note saved"
                else -> "Photo saved"
            }
        }
    }

    fun moveSpeciesToIndex(species: SpeciesEntity, targetIndex: Int) {
        viewModelScope.launch {
            val profile = settingsRepository.activeProfile.value
            if (!profile.isCustom) {
                lastMessage.value = "Built-in profiles follow GPS and cannot be reordered"
                return@launch
            }
            repository.moveSpeciesToIndex(species.id, targetIndex, profile)
            settingsRepository.setProfileCustomized(profile)
            lastMessage.value = "Wildlife order updated"
        }
    }

    fun clearMessage() {
        lastMessage.value = null
    }

    private fun scheduleInactiveSessionStop(session: ObservationSessionEntity?) {
        inactivityJob?.cancel()
        if (session == null) return

        inactivityJob = viewModelScope.launch {
            while (true) {
                val lastActivityAt = session.lastActivityAt ?: session.startedAt
                val remaining = lastActivityAt + InactivityLimitMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    if (repository.endInactiveActiveSessionIfNeeded()) {
                        lastMessage.value = backupEndedSession(
                            context = backupContext.get(),
                            savedMessage = "Session auto-ended after 3 hours inactive",
                        )
                    }
                    return@launch
                }
                delay(remaining.coerceIn(1_000L, 60_000L))
            }
        }
    }

    private suspend fun backupEndedSession(context: Context?, savedMessage: String): String {
        if (context == null || !settingsRepository.autoBackupEnabled.value) return savedMessage

        return try {
            val result = writeConfiguredBackup(context.applicationContext)
            settingsRepository.recordBackupSuccess(result)
            when {
                result.warnings.isEmpty() -> "$savedMessage and backed up"
                result.warnings.any { it.startsWith("Google Drive:") } ->
                    "$savedMessage and backed up to phone; Google Drive needs attention"
                else -> "$savedMessage and backed up; check Backup settings"
            }
        } catch (error: Throwable) {
            settingsRepository.recordBackupFailure(error.readableMessage())
            "$savedMessage; backup failed"
        }
    }

    private suspend fun writeConfiguredBackup(context: Context): BackupWriteResult {
        return repository.writeVerifiedBackup(
            context = context.applicationContext,
            themeMode = settingsRepository.themeMode.value,
            autoBackupEnabled = settingsRepository.autoBackupEnabled.value,
            mediaSaveLocation = settingsRepository.mediaSaveLocation.value,
            hapticFeedbackEnabled = settingsRepository.hapticFeedbackEnabled.value,
            environmentCaptureEnabled = settingsRepository.environmentCaptureEnabled.value,
            activeProfile = settingsRepository.activeProfile.value,
            customProfiles = settingsRepository.profiles.value.filter { it.isCustom },
            sessionTags = settingsRepository.sessionTags.value,
            fieldLocations = settingsRepository.fieldLocations.value,
            selectedFieldLocation = settingsRepository.selectedFieldLocation.value,
            sharedLocationPrecision = settingsRepository.sharedLocationPrecision.value,
            includeNamedFieldLocationsInShares = settingsRepository.includeNamedFieldLocationsInShares.value,
            fieldModeEnabled = settingsRepository.fieldModeEnabled.value,
            keepScreenAwake = settingsRepository.keepScreenAwake.value,
            backupDestination = settingsRepository.backupDestination.value,
            googleDriveFolderUri = settingsRepository.googleDriveFolderUri.value,
        )
    }

    private fun Throwable.readableMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "Backup failed."
    }
}

private data class ObserveBackupSettings(
    val destination: BackupDestination,
    val folderUri: String?,
    val driveIssue: String?,
)

internal fun BackupStatus.googleDriveIssue(): String? {
    val detail = warning ?: message
    return detail?.takeIf { text ->
        text.contains("Google Drive", ignoreCase = true) ||
            text.contains("Drive folder", ignoreCase = true)
    }
}
