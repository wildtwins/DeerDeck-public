package com.wildtwins.deerdeck.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesViewModel(
    private val repository: DeerDeckRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)

    val profiles = settingsRepository.profiles
    val activeProfile: StateFlow<ObservationProfile> = settingsRepository.activeProfile
    val activeSession: StateFlow<ObservationSessionEntity?> = repository.activeSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
    val message = _message.asStateFlow()
    val species: StateFlow<List<SpeciesEntity>> = settingsRepository.activeProfile.flatMapLatest { profile ->
        repository.activeSpecies(profile)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
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
    }

    fun addSpecies(name: String, color: Long) {
        viewModelScope.launch {
            if (repository.currentSessionId() != null) {
                _message.value = "End the active session before editing profiles"
                return@launch
            }
            val profile = settingsRepository.activeProfile.value
            if (!profile.isCustom) {
                _message.value = "Built-in profiles are managed from GPS"
                return@launch
            }
            repository.addSpecies(profile, name, color)
            settingsRepository.setProfileCustomized(profile)
            _message.value = "$name added"
        }
    }

    fun archiveSpecies(speciesId: Long) {
        viewModelScope.launch {
            if (repository.currentSessionId() != null) {
                _message.value = "End the active session before editing profiles"
                return@launch
            }
            if (!settingsRepository.activeProfile.value.isCustom) {
                _message.value = "Built-in profiles cannot be edited"
                return@launch
            }
            repository.archiveSpecies(speciesId)
            settingsRepository.setProfileCustomized(settingsRepository.activeProfile.value)
        }
    }

    fun updateSpecies(speciesId: Long, name: String, color: Long) {
        viewModelScope.launch {
            if (repository.currentSessionId() != null) {
                _message.value = "End the active session before editing profiles"
                return@launch
            }
            val profile = settingsRepository.activeProfile.value
            val updated = repository.updateSpecies(profile, speciesId, name, color)
            _message.value = if (updated) "Wildlife updated" else "That name is already in this profile"
        }
    }

    fun selectProfile(profile: ObservationProfile) {
        viewModelScope.launch {
            if (
                repository.currentSessionId() != null &&
                profile.storageValue != settingsRepository.activeProfile.value.storageValue
            ) {
                _message.value = "End the active session before switching profiles"
                return@launch
            }
            settingsRepository.setActiveProfile(profile)
        }
    }

    fun addProfile(name: String) {
        viewModelScope.launch {
            if (repository.currentSessionId() != null) {
                _message.value = "End the active session before adding a profile"
                return@launch
            }
            val profile = settingsRepository.addCustomProfile(name)
            _message.value = if (profile == null) {
                "Choose a unique profile name"
            } else {
                "${profile.label} created"
            }
        }
    }

    fun renameProfile(profile: ObservationProfile, name: String) {
        viewModelScope.launch {
            if (repository.currentSessionId() != null) {
                _message.value = "End the active session before editing profiles"
                return@launch
            }
            _message.value = if (settingsRepository.renameCustomProfile(profile, name)) {
                "Profile renamed"
            } else {
                "Choose a unique profile name"
            }
        }
    }

    fun deleteProfile(profile: ObservationProfile) {
        viewModelScope.launch {
            if (repository.currentSessionId() != null) {
                _message.value = "End the active session before deleting a profile"
                return@launch
            }
            _message.value = if (settingsRepository.deleteCustomProfile(profile)) {
                "${profile.label} deleted"
            } else {
                "Built-in profiles cannot be deleted"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
