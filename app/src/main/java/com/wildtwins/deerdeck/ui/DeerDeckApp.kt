package com.wildtwins.deerdeck.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import com.wildtwins.deerdeck.ui.history.HistoryScreen
import com.wildtwins.deerdeck.ui.history.HistoryViewModel
import com.wildtwins.deerdeck.ui.observe.ObserveScreen
import com.wildtwins.deerdeck.ui.observe.ObserveViewModel
import com.wildtwins.deerdeck.ui.settings.SettingsScreen
import com.wildtwins.deerdeck.ui.settings.SettingsViewModel
import com.wildtwins.deerdeck.ui.species.SpeciesScreen
import com.wildtwins.deerdeck.ui.species.SpeciesViewModel
import com.wildtwins.deerdeck.ui.stats.StatsScreenV2
import com.wildtwins.deerdeck.ui.stats.StatsViewModel

private enum class AppTab {
    Observe,
    Stats,
    History,
    Profiles,
    Settings,
}

@Composable
fun DeerDeckApp(
    repository: DeerDeckRepository,
    settingsRepository: AppSettingsRepository,
) {
    val factory = remember(repository, settingsRepository) { DeerDeckViewModelFactory(repository, settingsRepository) }
    val observeViewModel: ObserveViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val speciesViewModel: SpeciesViewModel = viewModel(factory = factory)
    val statsViewModel: StatsViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val privacyNoticeAccepted by settingsRepository.privacyNoticeAccepted.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.Observe) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Observe,
                    onClick = { selectedTab = AppTab.Observe },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    label = { Text("Observe") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Stats,
                    onClick = { selectedTab = AppTab.Stats },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("Stats") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.History,
                    onClick = { selectedTab = AppTab.History },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Profiles,
                    onClick = { selectedTab = AppTab.Profiles },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("Profiles") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Settings,
                    onClick = { selectedTab = AppTab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            AppTab.Observe -> ObserveScreen(
                viewModel = observeViewModel,
                modifier = Modifier.padding(padding),
            )

            AppTab.Stats -> StatsScreenV2(
                viewModel = statsViewModel,
                modifier = Modifier.padding(padding),
            )

            AppTab.History -> HistoryScreen(
                viewModel = historyViewModel,
                modifier = Modifier.padding(padding),
            )

            AppTab.Profiles -> SpeciesScreen(
                viewModel = speciesViewModel,
                modifier = Modifier.padding(padding),
            )

            AppTab.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (!privacyNoticeAccepted) {
        PrivacyWelcomeDialog(
            onEnableFieldConditions = {
                settingsRepository.acceptPrivacyNotice(environmentCaptureEnabled = true)
            },
            onContinueOffline = {
                settingsRepository.acceptPrivacyNotice(environmentCaptureEnabled = false)
            },
        )
    }
}

@Composable
private fun PrivacyWelcomeDialog(
    onEnableFieldConditions: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to Withrow DeerDeck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Your observation history stays on this device unless you choose to export or back it up.")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optional field conditions", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When enabled, DeerDeck stores the session location and sends coordinates over HTTPS " +
                                "to the U.S. National Weather Service only to retrieve current conditions.",
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        "Camera, microphone, location, backups, and sharing remain under your control. Permissions " +
                            "are requested only when needed, and you can erase the local dataset in Settings.",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onEnableFieldConditions) {
                Text("Enable field conditions")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onContinueOffline) {
                Text("Use without location")
            }
        },
    )
}
