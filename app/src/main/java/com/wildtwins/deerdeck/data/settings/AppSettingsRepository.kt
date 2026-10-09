package com.wildtwins.deerdeck.data.settings

import android.content.Context
import com.wildtwins.deerdeck.data.backup.BackupWriteResult
import com.wildtwins.deerdeck.data.SessionTagCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val DefaultSessionTags = listOf(
    "Clear",
    "Cloudy",
    "Windy",
    "Rain",
    "Snow",
    "Cold",
    "Feeder empty",
    "Neighbor activity",
)

enum class AppThemeMode(val storageValue: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: System
        }
    }
}

enum class MediaSaveLocation(val storageValue: String, val label: String) {
    CameraRoll("camera_roll", "Camera roll"),
    DeerDeckOnly("deerdeck_only", "DeerDeck only");

    companion object {
        fun fromStorage(value: String?): MediaSaveLocation {
            return entries.firstOrNull { it.storageValue == value } ?: CameraRoll
        }
    }
}

enum class SharedLocationPrecision(val storageValue: String, val label: String) {
    Exact("exact", "Exact"),
    Approximate("approximate", "Approximate"),
    Hidden("hidden", "Hidden");

    companion object {
        fun fromStorage(value: String?): SharedLocationPrecision {
            return entries.firstOrNull { it.storageValue == value } ?: Approximate
        }
    }
}

enum class BackupDestination(val storageValue: String, val label: String) {
    PhoneOnly("phone_only", "Phone only"),
    GoogleDrive("google_drive", "Google Drive"),
    Both("both", "Both");

    val usesPhone: Boolean get() = this != GoogleDrive
    val usesDrive: Boolean get() = this != PhoneOnly

    companion object {
        fun fromStorage(value: String?): BackupDestination {
            return entries.firstOrNull { it.storageValue == value } ?: PhoneOnly
        }
    }
}

enum class ObservationProfileKind {
    Wildlife,
    Birds,
    Custom,
}

data class ObservationProfile(
    val storageValue: String,
    val label: String,
    val kind: ObservationProfileKind,
) {
    val isBuiltIn: Boolean get() = kind != ObservationProfileKind.Custom
    val isCustom: Boolean get() = kind == ObservationProfileKind.Custom

    companion object {
        val WildlifeWatching = ObservationProfile("wildlife_watching", "Wildlife", ObservationProfileKind.Wildlife)
        val BirdWatching = ObservationProfile("bird_watching", "Birds", ObservationProfileKind.Birds)
        val BuiltIns = listOf(WildlifeWatching, BirdWatching)
        internal val LegacyCustom = ObservationProfile("custom", "Custom", ObservationProfileKind.Custom)

        fun custom(storageValue: String, label: String): ObservationProfile {
            require(storageValue.startsWith("custom"))
            return ObservationProfile(storageValue, label, ObservationProfileKind.Custom)
        }

        fun fromStorage(
            value: String?,
            profiles: List<ObservationProfile> = BuiltIns,
        ): ObservationProfile {
            return profiles.firstOrNull { it.storageValue == value }
                ?: BuiltIns.firstOrNull { it.storageValue == value }
                ?: value?.takeIf { it.startsWith("custom") }?.let { custom(it, "Custom") }
                ?: WildlifeWatching
        }
    }
}

data class BackupStatus(
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastSucceeded: Boolean? = null,
    val message: String? = null,
    val warning: String? = null,
    val path: String? = null,
    val fileName: String? = null,
    val fileChecksumSha256: String? = null,
    val payloadChecksumSha256: String? = null,
    val wildlifeCount: Int = 0,
    val sessionCount: Int = 0,
    val observationCount: Int = 0,
    val mediaCount: Int = 0,
)

class AppSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(AppThemeMode.fromStorage(preferences.getString(ThemeModeKey, null)))
    private val _autoBackupEnabled = MutableStateFlow(preferences.getBoolean(AutoBackupEnabledKey, true))
    private val _backupDestination = MutableStateFlow(
        BackupDestination.fromStorage(preferences.getString(BackupDestinationKey, null)),
    )
    private val _googleDriveFolderUri = MutableStateFlow(preferences.getString(GoogleDriveFolderUriKey, null))
    private val _mediaSaveLocation = MutableStateFlow(
        MediaSaveLocation.fromStorage(preferences.getString(MediaSaveLocationKey, null)),
    )
    private val _hapticFeedbackEnabled = MutableStateFlow(preferences.getBoolean(HapticFeedbackEnabledKey, true))
    private val _fieldModeEnabled = MutableStateFlow(preferences.getBoolean(FieldModeEnabledKey, false))
    private val _keepScreenAwake = MutableStateFlow(preferences.getBoolean(KeepScreenAwakeKey, true))
    private val _environmentCaptureEnabled = MutableStateFlow(
        preferences.getBoolean(EnvironmentCaptureEnabledKey, true),
    )
    private val _privacyNoticeAccepted = MutableStateFlow(
        preferences.getInt(PrivacyNoticeVersionKey, 0) >= CurrentPrivacyNoticeVersion,
    )
    private val _sharedLocationPrecision = MutableStateFlow(
        SharedLocationPrecision.fromStorage(preferences.getString(SharedLocationPrecisionKey, null)),
    )
    private val _includeNamedFieldLocationsInShares = MutableStateFlow(
        preferences.getBoolean(IncludeNamedFieldLocationsInSharesKey, false),
    )
    private val initialCustomProfiles = readCustomProfiles()
    private val _profiles = MutableStateFlow(ObservationProfile.BuiltIns + initialCustomProfiles)
    private val _activeProfile = MutableStateFlow(
        ObservationProfile.fromStorage(preferences.getString(ActiveProfileKey, null), _profiles.value),
    )
    private val _sessionTags = MutableStateFlow(readSessionTags())
    private val _fieldLocations = MutableStateFlow(readFieldLocations())
    private val _selectedFieldLocation = MutableStateFlow(
        preferences.getString(SelectedFieldLocationKey, null)
            ?.takeIf { selected -> _fieldLocations.value.any { it.equals(selected, ignoreCase = true) } },
    )
    private val _backupStatus = MutableStateFlow(readBackupStatus())

    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()
    val backupDestination: StateFlow<BackupDestination> = _backupDestination.asStateFlow()
    val googleDriveFolderUri: StateFlow<String?> = _googleDriveFolderUri.asStateFlow()
    val mediaSaveLocation: StateFlow<MediaSaveLocation> = _mediaSaveLocation.asStateFlow()
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()
    val fieldModeEnabled: StateFlow<Boolean> = _fieldModeEnabled.asStateFlow()
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()
    val environmentCaptureEnabled: StateFlow<Boolean> = _environmentCaptureEnabled.asStateFlow()
    val privacyNoticeAccepted: StateFlow<Boolean> = _privacyNoticeAccepted.asStateFlow()
    val sharedLocationPrecision: StateFlow<SharedLocationPrecision> = _sharedLocationPrecision.asStateFlow()
    val includeNamedFieldLocationsInShares: StateFlow<Boolean> =
        _includeNamedFieldLocationsInShares.asStateFlow()
    val profiles: StateFlow<List<ObservationProfile>> = _profiles.asStateFlow()
    val activeProfile: StateFlow<ObservationProfile> = _activeProfile.asStateFlow()
    val sessionTags: StateFlow<List<String>> = _sessionTags.asStateFlow()
    val fieldLocations: StateFlow<List<String>> = _fieldLocations.asStateFlow()
    val selectedFieldLocation: StateFlow<String?> = _selectedFieldLocation.asStateFlow()
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(ThemeModeKey, mode.storageValue).apply()
        _themeMode.value = mode
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(AutoBackupEnabledKey, enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    fun setBackupDestination(destination: BackupDestination) {
        preferences.edit().putString(BackupDestinationKey, destination.storageValue).apply()
        _backupDestination.value = destination
    }

    fun setGoogleDriveFolderUri(uri: String?) {
        preferences.edit().apply {
            if (uri.isNullOrBlank()) {
                remove(GoogleDriveFolderUriKey)
            } else {
                putString(GoogleDriveFolderUriKey, uri)
            }
        }.apply()
        _googleDriveFolderUri.value = uri?.takeIf { it.isNotBlank() }
    }

    fun setMediaSaveLocation(location: MediaSaveLocation) {
        preferences.edit().putString(MediaSaveLocationKey, location.storageValue).apply()
        _mediaSaveLocation.value = location
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(HapticFeedbackEnabledKey, enabled).apply()
        _hapticFeedbackEnabled.value = enabled
    }

    fun setFieldModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(FieldModeEnabledKey, enabled).apply()
        _fieldModeEnabled.value = enabled
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        preferences.edit().putBoolean(KeepScreenAwakeKey, enabled).apply()
        _keepScreenAwake.value = enabled
    }

    fun setEnvironmentCaptureEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(EnvironmentCaptureEnabledKey, enabled).apply()
        _environmentCaptureEnabled.value = enabled
    }

    fun acceptPrivacyNotice(environmentCaptureEnabled: Boolean) {
        preferences.edit()
            .putInt(PrivacyNoticeVersionKey, CurrentPrivacyNoticeVersion)
            .putBoolean(EnvironmentCaptureEnabledKey, environmentCaptureEnabled)
            .apply()
        _privacyNoticeAccepted.value = true
        _environmentCaptureEnabled.value = environmentCaptureEnabled
    }

    fun setSharedLocationPrecision(precision: SharedLocationPrecision) {
        preferences.edit().putString(SharedLocationPrecisionKey, precision.storageValue).apply()
        _sharedLocationPrecision.value = precision
    }

    fun setIncludeNamedFieldLocationsInShares(enabled: Boolean) {
        preferences.edit().putBoolean(IncludeNamedFieldLocationsInSharesKey, enabled).apply()
        _includeNamedFieldLocationsInShares.value = enabled
    }

    fun setActiveProfile(profile: ObservationProfile) {
        if (_profiles.value.none { it.storageValue == profile.storageValue }) return
        preferences.edit().putString(ActiveProfileKey, profile.storageValue).apply()
        _activeProfile.value = profile
    }

    fun profileFromStorage(value: String?): ObservationProfile {
        return ObservationProfile.fromStorage(value, _profiles.value)
    }

    fun addCustomProfile(name: String): ObservationProfile? {
        val cleanName = name.cleanProfileName() ?: return null
        if (_profiles.value.any { it.label.equals(cleanName, ignoreCase = true) }) return null

        val profile = ObservationProfile.custom(
            storageValue = "custom_${UUID.randomUUID()}",
            label = cleanName,
        )
        setCustomProfiles(_profiles.value.filter { it.isCustom } + profile)
        setActiveProfile(profile)
        return profile
    }

    fun renameCustomProfile(profile: ObservationProfile, name: String): Boolean {
        if (!profile.isCustom) return false
        val cleanName = name.cleanProfileName() ?: return false
        if (_profiles.value.any {
                it.storageValue != profile.storageValue && it.label.equals(cleanName, ignoreCase = true)
            }
        ) {
            return false
        }

        val renamed = profile.copy(label = cleanName)
        setCustomProfiles(
            _profiles.value.filter { it.isCustom }.map {
                if (it.storageValue == profile.storageValue) renamed else it
            },
        )
        if (_activeProfile.value.storageValue == profile.storageValue) {
            setActiveProfile(renamed)
        }
        return true
    }

    fun deleteCustomProfile(profile: ObservationProfile): Boolean {
        if (!profile.isCustom || _profiles.value.none { it.storageValue == profile.storageValue }) return false
        setCustomProfiles(
            _profiles.value.filter { it.isCustom && it.storageValue != profile.storageValue },
        )
        preferences.edit().remove(profileCustomizedKey(profile)).apply()
        if (_activeProfile.value.storageValue == profile.storageValue) {
            setActiveProfile(ObservationProfile.WildlifeWatching)
        }
        return true
    }

    fun restoreCustomProfiles(profiles: List<ObservationProfile>) {
        val clean = profiles
            .filter { it.isCustom && it.storageValue.startsWith("custom") }
            .mapNotNull { profile ->
                profile.label.cleanProfileName()?.let { label -> profile.copy(label = label) }
            }
            .distinctBy { it.storageValue }
        setCustomProfiles(clean)
    }

    fun setSessionTags(tags: List<String>) {
        val cleanTags = tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(24)
        val stored = SessionTagCodec.encode(cleanTags) ?: "[]"
        preferences.edit().putString(SessionTagsKey, stored).apply()
        _sessionTags.value = cleanTags
    }

    fun resetSessionTags() {
        preferences.edit().remove(SessionTagsKey).apply()
        _sessionTags.value = DefaultSessionTags
    }

    fun setFieldLocations(locations: List<String>) {
        val cleanLocations = locations.cleanFieldLocations()
        preferences.edit().putString(FieldLocationsKey, SessionTagCodec.encode(cleanLocations) ?: "[]").apply()
        _fieldLocations.value = cleanLocations
        val selected = _selectedFieldLocation.value
        if (selected != null && cleanLocations.none { it.equals(selected, ignoreCase = true) }) {
            selectFieldLocation(null)
        }
    }

    fun addFieldLocation(name: String) {
        val cleanName = name.trim().take(40).takeIf { it.isNotBlank() } ?: return
        val existing = _fieldLocations.value.firstOrNull { it.equals(cleanName, ignoreCase = true) }
        if (existing != null) {
            selectFieldLocation(existing)
            return
        }
        setFieldLocations(_fieldLocations.value + cleanName)
        selectFieldLocation(cleanName)
    }

    fun removeFieldLocation(name: String) {
        setFieldLocations(_fieldLocations.value.filterNot { it.equals(name, ignoreCase = true) })
    }

    fun selectFieldLocation(name: String?) {
        val selected = name?.let { candidate ->
            _fieldLocations.value.firstOrNull { it.equals(candidate, ignoreCase = true) }
        }
        preferences.edit().apply {
            if (selected == null) remove(SelectedFieldLocationKey) else putString(SelectedFieldLocationKey, selected)
        }.apply()
        _selectedFieldLocation.value = selected
    }

    fun isProfileCustomized(profile: ObservationProfile): Boolean {
        return preferences.getBoolean(profileCustomizedKey(profile), false)
    }

    fun setProfileCustomized(profile: ObservationProfile, customized: Boolean = true) {
        preferences.edit().putBoolean(profileCustomizedKey(profile), customized).apply()
    }

    fun clearUserDatasetSettings() {
        val editor = preferences.edit()
            .remove(SessionTagsKey)
            .remove(FieldLocationsKey)
            .remove(SelectedFieldLocationKey)
            .remove(BackupLastAttemptAtKey)
            .remove(BackupLastSuccessAtKey)
            .remove(BackupLastSucceededKey)
            .remove(BackupMessageKey)
            .remove(BackupWarningKey)
            .remove(BackupPathKey)
            .remove(BackupFileNameKey)
            .remove(BackupFileChecksumKey)
            .remove(BackupPayloadChecksumKey)
            .remove(BackupWildlifeCountKey)
            .remove(BackupSessionCountKey)
            .remove(BackupObservationCountKey)
            .remove(BackupMediaCountKey)
        _profiles.value.forEach { profile ->
            editor.remove(profileCustomizedKey(profile))
        }
        editor.remove(CustomProfilesKey)
            .putString(ActiveProfileKey, ObservationProfile.WildlifeWatching.storageValue)
        editor.apply()

        _profiles.value = ObservationProfile.BuiltIns
        _activeProfile.value = ObservationProfile.WildlifeWatching
        _sessionTags.value = DefaultSessionTags
        _fieldLocations.value = emptyList()
        _selectedFieldLocation.value = null
        _backupStatus.value = BackupStatus()
    }

    fun recordBackupSuccess(result: BackupWriteResult) {
        val warning = result.warnings.takeIf { it.isNotEmpty() }?.joinToString(separator = " ")
        val status = BackupStatus(
            lastAttemptAt = result.createdAt,
            lastSuccessAt = result.createdAt,
            lastSucceeded = true,
            message = if (warning == null) "Backup verified" else "Backup verified to an available destination",
            warning = warning,
            path = result.displayPath,
            fileName = result.fileName,
            fileChecksumSha256 = result.fileChecksumSha256,
            payloadChecksumSha256 = result.payloadChecksumSha256,
            wildlifeCount = result.counts.wildlife,
            sessionCount = result.counts.sessions,
            observationCount = result.counts.observations,
            mediaCount = result.counts.media,
        )
        val editor = preferences.edit()
            .putLong(BackupLastAttemptAtKey, result.createdAt)
            .putLong(BackupLastSuccessAtKey, result.createdAt)
            .putBoolean(BackupLastSucceededKey, true)
            .putString(BackupMessageKey, status.message)
            .putString(BackupPathKey, result.displayPath)
            .putString(BackupFileNameKey, result.fileName)
            .putString(BackupFileChecksumKey, result.fileChecksumSha256)
            .putString(BackupPayloadChecksumKey, result.payloadChecksumSha256)
            .putInt(BackupWildlifeCountKey, result.counts.wildlife)
            .putInt(BackupSessionCountKey, result.counts.sessions)
            .putInt(BackupObservationCountKey, result.counts.observations)
            .putInt(BackupMediaCountKey, result.counts.media)
        if (warning == null) {
            editor.remove(BackupWarningKey)
        } else {
            editor.putString(BackupWarningKey, warning)
        }
        editor.apply()
        _backupStatus.value = status
    }

    fun recordBackupFailure(message: String, attemptedAt: Long = System.currentTimeMillis()) {
        val status = _backupStatus.value.copy(
            lastAttemptAt = attemptedAt,
            lastSucceeded = false,
            message = message,
            warning = null,
        )
        preferences.edit()
            .putLong(BackupLastAttemptAtKey, attemptedAt)
            .putBoolean(BackupLastSucceededKey, false)
            .putString(BackupMessageKey, message)
            .remove(BackupWarningKey)
            .apply()
        _backupStatus.value = status
    }

    private fun readBackupStatus(): BackupStatus {
        return BackupStatus(
            lastAttemptAt = preferences.readLongOrNull(BackupLastAttemptAtKey),
            lastSuccessAt = preferences.readLongOrNull(BackupLastSuccessAtKey),
            lastSucceeded = if (preferences.contains(BackupLastSucceededKey)) {
                preferences.getBoolean(BackupLastSucceededKey, false)
            } else {
                null
            },
            message = preferences.getString(BackupMessageKey, null),
            warning = preferences.getString(BackupWarningKey, null),
            path = preferences.getString(BackupPathKey, null),
            fileName = preferences.getString(BackupFileNameKey, null),
            fileChecksumSha256 = preferences.getString(BackupFileChecksumKey, null),
            payloadChecksumSha256 = preferences.getString(BackupPayloadChecksumKey, null),
            wildlifeCount = preferences.getInt(BackupWildlifeCountKey, 0),
            sessionCount = preferences.getInt(BackupSessionCountKey, 0),
            observationCount = preferences.getInt(BackupObservationCountKey, 0),
            mediaCount = preferences.getInt(BackupMediaCountKey, 0),
        )
    }

    private fun readSessionTags(): List<String> {
        if (!preferences.contains(SessionTagsKey)) return DefaultSessionTags
        return SessionTagCodec.decode(preferences.getString(SessionTagsKey, null))
    }

    private fun readFieldLocations(): List<String> {
        return SessionTagCodec.decode(preferences.getString(FieldLocationsKey, null)).cleanFieldLocations()
    }

    private fun readCustomProfiles(): List<ObservationProfile> {
        val stored = preferences.getString(CustomProfilesKey, null)
        if (stored == null) {
            val shouldMigrateLegacyCustom =
                preferences.getString(ActiveProfileKey, null) == ObservationProfile.LegacyCustom.storageValue ||
                    preferences.getBoolean(profileCustomizedKey(ObservationProfile.LegacyCustom), false)
            return if (shouldMigrateLegacyCustom) listOf(ObservationProfile.LegacyCustom) else emptyList()
        }

        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val key = item.optString("key").takeIf { it.startsWith("custom") } ?: continue
                    val label = item.optString("label").cleanProfileName() ?: continue
                    add(ObservationProfile.custom(key, label))
                }
            }.distinctBy { it.storageValue }
        }.getOrDefault(emptyList())
    }

    private fun setCustomProfiles(customProfiles: List<ObservationProfile>) {
        val array = JSONArray()
        customProfiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("key", profile.storageValue)
                    .put("label", profile.label),
            )
        }
        preferences.edit().putString(CustomProfilesKey, array.toString()).apply()
        _profiles.value = ObservationProfile.BuiltIns + customProfiles
        if (_profiles.value.none { it.storageValue == _activeProfile.value.storageValue }) {
            preferences.edit()
                .putString(ActiveProfileKey, ObservationProfile.WildlifeWatching.storageValue)
                .apply()
            _activeProfile.value = ObservationProfile.WildlifeWatching
        }
    }

    private fun List<String>.cleanFieldLocations(): List<String> {
        return map { it.trim().take(40) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(12)
    }

    private companion object {
        const val PrefsName = "withrow_deerdeck_settings"
        const val ThemeModeKey = "theme_mode"
        const val AutoBackupEnabledKey = "auto_backup_enabled"
        const val BackupDestinationKey = "backup_destination"
        const val GoogleDriveFolderUriKey = "google_drive_folder_uri"
        const val MediaSaveLocationKey = "media_save_location"
        const val HapticFeedbackEnabledKey = "haptic_feedback_enabled"
        const val FieldModeEnabledKey = "field_mode_enabled"
        const val KeepScreenAwakeKey = "keep_screen_awake"
        const val EnvironmentCaptureEnabledKey = "environment_capture_enabled"
        const val PrivacyNoticeVersionKey = "privacy_notice_version"
        const val SharedLocationPrecisionKey = "shared_location_precision"
        const val IncludeNamedFieldLocationsInSharesKey = "include_named_field_locations_in_shares"
        const val ActiveProfileKey = "active_profile"
        const val CustomProfilesKey = "custom_profiles"
        const val SessionTagsKey = "session_tags"
        const val FieldLocationsKey = "field_locations"
        const val SelectedFieldLocationKey = "selected_field_location"
        const val ProfileCustomizedPrefix = "profile_customized_"
        const val BackupLastAttemptAtKey = "backup_last_attempt_at"
        const val BackupLastSuccessAtKey = "backup_last_success_at"
        const val BackupLastSucceededKey = "backup_last_succeeded"
        const val BackupMessageKey = "backup_message"
        const val BackupWarningKey = "backup_warning"
        const val BackupPathKey = "backup_path"
        const val BackupFileNameKey = "backup_file_name"
        const val BackupFileChecksumKey = "backup_file_checksum"
        const val BackupPayloadChecksumKey = "backup_payload_checksum"
        const val BackupWildlifeCountKey = "backup_wildlife_count"
        const val BackupSessionCountKey = "backup_session_count"
        const val BackupObservationCountKey = "backup_observation_count"
        const val BackupMediaCountKey = "backup_media_count"
        const val CurrentPrivacyNoticeVersion = 1

        fun profileCustomizedKey(profile: ObservationProfile): String {
            return "$ProfileCustomizedPrefix${profile.storageValue}"
        }
    }
}

private fun String.cleanProfileName(): String? {
    return trim().replace(Regex("\\s+"), " ").take(40).takeIf { it.isNotBlank() }
}

private fun android.content.SharedPreferences.readLongOrNull(key: String): Long? {
    return if (contains(key)) getLong(key, 0L) else null
}
