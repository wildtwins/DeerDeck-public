package com.wildtwins.deerdeck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import com.wildtwins.deerdeck.ui.history.HistoryViewModel
import com.wildtwins.deerdeck.ui.observe.ObserveViewModel
import com.wildtwins.deerdeck.ui.settings.SettingsViewModel
import com.wildtwins.deerdeck.ui.species.SpeciesViewModel
import com.wildtwins.deerdeck.ui.stats.StatsViewModel

class DeerDeckViewModelFactory(
    private val repository: DeerDeckRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(StatsViewModel::class.java) -> StatsViewModel(repository) as T
            modelClass.isAssignableFrom(ObserveViewModel::class.java) -> ObserveViewModel(repository, settingsRepository) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository, settingsRepository) as T
            modelClass.isAssignableFrom(SpeciesViewModel::class.java) -> SpeciesViewModel(repository, settingsRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository, settingsRepository) as T
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
