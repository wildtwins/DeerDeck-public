package com.wildtwins.deerdeck.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.export.ExportPrivacyOptions
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import com.wildtwins.deerdeck.data.settings.BackupDestination
import com.wildtwins.deerdeck.data.settings.MediaSaveLocation
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import com.wildtwins.deerdeck.data.model.DatasetCounts
import com.wildtwins.deerdeck.data.settings.BackupStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BackupHealthLevel {
    Healthy,
    Due,
    NeedsAttention,
}

data class BackupHealth(
    val level: BackupHealthLevel = BackupHealthLevel.NeedsAttention,
    val title: String = "Backup needs attention",
    val detail: String = "No verified backup has run yet.",
)

data class RestoreUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val succeeded: Boolean = false,
)

data class DataEraseUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val succeeded: Boolean = false,
)

class SettingsViewModel(
    private val repository: DeerDeckRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val _backupInProgress = MutableStateFlow(false)
    private val _restoreState = MutableStateFlow(RestoreUiState())
    private val _eraseState = MutableStateFlow(DataEraseUiState())

    val themeMode = settingsRepository.themeMode
    val autoBackupEnabled = settingsRepository.autoBackupEnabled
    val backupDestination = settingsRepository.backupDestination
    val googleDriveFolderUri = settingsRepository.googleDriveFolderUri
    val mediaSaveLocation = settingsRepository.mediaSaveLocation
    val hapticFeedbackEnabled = settingsRepository.hapticFeedbackEnabled
    val fieldModeEnabled = settingsRepository.fieldModeEnabled
    val keepScreenAwake = settingsRepository.keepScreenAwake
    val environmentCaptureEnabled = settingsRepository.environmentCaptureEnabled
    val sharedLocationPrecision = settingsRepository.sharedLocationPrecision
    val includeNamedFieldLocationsInShares = settingsRepository.includeNamedFieldLocationsInShares
    val activeProfile = settingsRepository.activeProfile
    val sessionTags = settingsRepository.sessionTags
    val fieldLocations = settingsRepository.fieldLocations
    val selectedFieldLocation = settingsRepository.selectedFieldLocation
    val backupStatus = settingsRepository.backupStatus
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()
    val restoreState: StateFlow<RestoreUiState> = _restoreState.asStateFlow()
    val eraseState: StateFlow<DataEraseUiState> = _eraseState.asStateFlow()
    val backupHealth: StateFlow<BackupHealth> = combine(
        settingsRepository.backupStatus,
        repository.datasetCounts,
        settingsRepository.autoBackupEnabled,
        combine(
            settingsRepository.backupDestination,
            settingsRepository.googleDriveFolderUri,
        ) { destination, folderUri -> destination to folderUri },
    ) { status, counts, autoBackup, destinationState ->
        calculateBackupHealth(
            status = status,
            counts = counts,
            autoBackupEnabled = autoBackup,
            backupDestination = destinationState.first,
            driveFolderUri = destinationState.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupHealth(),
    )

    fun setThemeMode(mode: AppThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        settingsRepository.setAutoBackupEnabled(enabled)
    }

    fun setBackupDestination(destination: BackupDestination) {
        settingsRepository.setBackupDestination(destination)
    }

    fun setGoogleDriveFolderUri(uri: Uri?) {
        settingsRepository.setGoogleDriveFolderUri(uri?.toString())
    }

    fun setMediaSaveLocation(location: MediaSaveLocation) {
        settingsRepository.setMediaSaveLocation(location)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        settingsRepository.setHapticFeedbackEnabled(enabled)
    }

    fun setFieldModeEnabled(enabled: Boolean) {
        settingsRepository.setFieldModeEnabled(enabled)
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        settingsRepository.setKeepScreenAwake(enabled)
    }

    fun setEnvironmentCaptureEnabled(enabled: Boolean) {
        settingsRepository.setEnvironmentCaptureEnabled(enabled)
    }

    fun setSharedLocationPrecision(precision: SharedLocationPrecision) {
        settingsRepository.setSharedLocationPrecision(precision)
    }

    fun setIncludeNamedFieldLocationsInShares(enabled: Boolean) {
        settingsRepository.setIncludeNamedFieldLocationsInShares(enabled)
    }

    fun setActiveProfile(profile: ObservationProfile) {
        settingsRepository.setActiveProfile(profile)
    }

    fun setSessionTags(tags: List<String>) {
        settingsRepository.setSessionTags(tags)
    }

    fun resetSessionTags() {
        settingsRepository.resetSessionTags()
    }

    fun setFieldLocations(locations: List<String>) {
        settingsRepository.setFieldLocations(locations)
    }

    fun selectFieldLocation(name: String?) {
        settingsRepository.selectFieldLocation(name)
    }

    fun backupNow(context: Context) {
        if (_backupInProgress.value) return

        viewModelScope.launch {
            _backupInProgress.value = true
            try {
                val result = repository.writeVerifiedBackup(
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
                    includeNamedFieldLocationsInShares =
                        settingsRepository.includeNamedFieldLocationsInShares.value,
                    fieldModeEnabled = settingsRepository.fieldModeEnabled.value,
                    keepScreenAwake = settingsRepository.keepScreenAwake.value,
                    backupDestination = settingsRepository.backupDestination.value,
                    googleDriveFolderUri = settingsRepository.googleDriveFolderUri.value,
                )
                settingsRepository.recordBackupSuccess(result)
            } catch (error: Throwable) {
                settingsRepository.recordBackupFailure(error.readableMessage())
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    fun exportDataPackage(context: Context, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            try {
                val uri = repository.exportDataPackage(
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
                    includeNamedFieldLocationsInShares =
                        settingsRepository.includeNamedFieldLocationsInShares.value,
                    fieldModeEnabled = settingsRepository.fieldModeEnabled.value,
                    keepScreenAwake = settingsRepository.keepScreenAwake.value,
                )
                onReady(uri)
            } catch (error: Throwable) {
                settingsRepository.recordBackupFailure(error.readableMessage())
            }
        }
    }

    fun exportPrivateSharePackage(context: Context, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            try {
                val uri = repository.exportPrivateSharePackage(
                    context = context.applicationContext,
                    privacy = ExportPrivacyOptions(
                        locationPrecision = settingsRepository.sharedLocationPrecision.value,
                        includeNamedFieldLocations = settingsRepository.includeNamedFieldLocationsInShares.value,
                    ),
                )
                onReady(uri)
            } catch (error: Throwable) {
                settingsRepository.recordBackupFailure(error.readableMessage())
            }
        }
    }

    fun restoreDataPackage(context: Context, sourceUri: Uri) {
        if (_restoreState.value.inProgress) return
        viewModelScope.launch {
            _restoreState.value = RestoreUiState(inProgress = true)
            try {
                val result = repository.restoreDataPackage(
                    context = context.applicationContext,
                    sourceUri = sourceUri,
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
                    includeNamedFieldLocationsInShares =
                        settingsRepository.includeNamedFieldLocationsInShares.value,
                    fieldModeEnabled = settingsRepository.fieldModeEnabled.value,
                    keepScreenAwake = settingsRepository.keepScreenAwake.value,
                )
                settingsRepository.setThemeMode(AppThemeMode.fromStorage(result.settings.themeMode))
                result.settings.autoBackupEnabled?.let(settingsRepository::setAutoBackupEnabled)
                result.settings.mediaSaveLocation?.let { value ->
                    settingsRepository.setMediaSaveLocation(MediaSaveLocation.fromStorage(value))
                }
                result.settings.hapticFeedbackEnabled?.let(settingsRepository::setHapticFeedbackEnabled)
                result.settings.environmentCaptureEnabled?.let(settingsRepository::setEnvironmentCaptureEnabled)
                result.settings.customProfiles?.let { profiles ->
                    settingsRepository.restoreCustomProfiles(
                        profiles.map {
                            ObservationProfile.custom(
                                storageValue = it.storageValue,
                                label = it.label,
                            )
                        },
                    )
                } ?: result.settings.activeProfile
                    ?.takeIf { it.startsWith("custom") }
                    ?.let { legacyKey ->
                        settingsRepository.restoreCustomProfiles(
                            listOf(ObservationProfile.custom(legacyKey, "Custom")),
                        )
                    }
                result.settings.activeProfile?.let { value ->
                    settingsRepository.setActiveProfile(settingsRepository.profileFromStorage(value))
                }
                result.settings.sessionTags?.let(settingsRepository::setSessionTags)
                result.settings.fieldLocations?.let { locations ->
                    settingsRepository.setFieldLocations(locations)
                    settingsRepository.selectFieldLocation(result.settings.selectedFieldLocation)
                }
                result.settings.sharedLocationPrecision?.let { value ->
                    settingsRepository.setSharedLocationPrecision(SharedLocationPrecision.fromStorage(value))
                }
                result.settings.includeNamedFieldLocationsInShares?.let {
                    settingsRepository.setIncludeNamedFieldLocationsInShares(it)
                }
                result.settings.fieldModeEnabled?.let(settingsRepository::setFieldModeEnabled)
                result.settings.keepScreenAwake?.let(settingsRepository::setKeepScreenAwake)
                _restoreState.value = RestoreUiState(
                    message = "Restored ${result.counts.sessions} sessions and ${result.counts.observations} observations. " +
                        "Safety backup: ${result.safetyBackupPath}",
                    succeeded = true,
                )
            } catch (error: Throwable) {
                _restoreState.value = RestoreUiState(
                    message = error.readableMessage(),
                    succeeded = false,
                )
            }
        }
    }

    fun clearRestoreMessage() {
        _restoreState.value = RestoreUiState()
    }

    fun eraseLocalDataset(context: Context) {
        if (_eraseState.value.inProgress) return
        viewModelScope.launch {
            _eraseState.value = DataEraseUiState(inProgress = true)
            try {
                repository.eraseLocalDataset(context.applicationContext)
                settingsRepository.clearUserDatasetSettings()
                repository.ensureDefaultSpecies(
                    profile = settingsRepository.activeProfile.value,
                    customized = false,
                )
                _eraseState.value = DataEraseUiState(
                    message = "Observation history, custom wildlife, and DeerDeck-only media were erased.",
                    succeeded = true,
                )
            } catch (error: Throwable) {
                _eraseState.value = DataEraseUiState(
                    message = error.readableMessage(),
                    succeeded = false,
                )
            }
        }
    }

    fun clearEraseMessage() {
        _eraseState.value = DataEraseUiState()
    }

    private fun Throwable.readableMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "Data operation failed."
    }

    private fun calculateBackupHealth(
        status: BackupStatus,
        counts: DatasetCounts,
        autoBackupEnabled: Boolean,
        backupDestination: BackupDestination,
        driveFolderUri: String?,
        now: Long = System.currentTimeMillis(),
    ): BackupHealth {
        if (backupDestination.usesDrive && driveFolderUri.isNullOrBlank()) {
            return BackupHealth(
                level = BackupHealthLevel.NeedsAttention,
                title = "Backup destination incomplete",
                detail = "Choose a Google Drive folder or switch to phone backup.",
            )
        }
        if (status.lastSucceeded == false) {
            return BackupHealth(
                level = BackupHealthLevel.NeedsAttention,
                title = "Last backup failed",
                detail = status.message ?: "Run Backup now to protect the current dataset.",
            )
        }
        status.warning?.let { warning ->
            return BackupHealth(
                level = BackupHealthLevel.NeedsAttention,
                title = "One backup destination needs attention",
                detail = warning,
            )
        }
        val lastSuccess = status.lastSuccessAt ?: return BackupHealth()
        val dataChanged = status.wildlifeCount != counts.wildlife ||
            status.sessionCount != counts.sessions ||
            status.observationCount != counts.observations ||
            status.mediaCount != counts.media
        if (dataChanged) {
            return BackupHealth(
                level = BackupHealthLevel.Due,
                title = "Backup due",
                detail = "New field data has been added since the last verified backup.",
            )
        }
        val ageMillis = (now - lastSuccess).coerceAtLeast(0L)
        if (ageMillis > 7 * 24 * 60 * 60 * 1_000L) {
            return BackupHealth(
                level = BackupHealthLevel.NeedsAttention,
                title = "Backup is over a week old",
                detail = "Run a fresh verified backup before more field use.",
            )
        }
        if (!autoBackupEnabled || ageMillis > 3 * 24 * 60 * 60 * 1_000L) {
            return BackupHealth(
                level = BackupHealthLevel.Due,
                title = "Backup due soon",
                detail = if (autoBackupEnabled) {
                    "The last verified backup is more than three days old."
                } else {
                    "Auto backup is off; remember to run manual backups."
                },
            )
        }
        return BackupHealth(
            level = BackupHealthLevel.Healthy,
            title = "Backup healthy",
            detail = "Current data matches the last verified backup.",
        )
    }
}
