package com.wildtwins.deerdeck.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.ObservationProfile

private val wildlifeColors = listOf(
    0xFF0046BE,
    0xFF2B7DE9,
    0xFF174EA6,
    0xFF386641,
    0xFF405A7A,
    0xFF8AAFE8,
    0xFFD55E00,
    0xFF009E73,
    0xFFCC79A7,
    0xFFE69F00,
    0xFF56B4E9,
    0xFF6A3D9A,
    0xFF8B1E3F,
    0xFF5A5A5A,
    0xFF7A5195,
    0xFFEF5675,
)

@Composable
fun SpeciesScreen(
    viewModel: SpeciesViewModel,
    modifier: Modifier = Modifier,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val species by viewModel.species.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        SpeciesContent(
            profiles = profiles,
            activeProfile = activeProfile,
            hasActiveSession = activeSession != null,
            species = species,
            onSelectProfile = viewModel::selectProfile,
            onAddProfile = viewModel::addProfile,
            onRenameProfile = viewModel::renameProfile,
            onDeleteProfile = viewModel::deleteProfile,
            onAddSpecies = viewModel::addSpecies,
            onUpdateSpecies = viewModel::updateSpecies,
            onArchiveSpecies = viewModel::archiveSpecies,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun SpeciesContent(
    profiles: List<ObservationProfile>,
    activeProfile: ObservationProfile,
    hasActiveSession: Boolean,
    species: List<SpeciesEntity>,
    onSelectProfile: (ObservationProfile) -> Unit,
    onAddProfile: (String) -> Unit,
    onRenameProfile: (ObservationProfile, String) -> Unit,
    onDeleteProfile: (ObservationProfile) -> Unit,
    onAddSpecies: (String, Long) -> Unit,
    onUpdateSpecies: (Long, String, Long) -> Unit,
    onArchiveSpecies: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addProfileOpen by remember { mutableStateOf(false) }
    var renameProfileOpen by remember { mutableStateOf(false) }
    var deleteProfileOpen by remember { mutableStateOf(false) }
    var addWildlifeOpen by remember { mutableStateOf(false) }
    var editingWildlife by remember { mutableStateOf<SpeciesEntity?>(null) }
    var showProfileHelp by remember { mutableStateOf(false) }

    if (addProfileOpen) {
        ProfileNameDialog(
            title = "New profile",
            confirmLabel = "Create",
            initialName = "",
            onDismiss = { addProfileOpen = false },
            onSave = {
                onAddProfile(it)
                addProfileOpen = false
            },
        )
    }
    if (renameProfileOpen) {
        ProfileNameDialog(
            title = "Rename profile",
            confirmLabel = "Save",
            initialName = activeProfile.label,
            onDismiss = { renameProfileOpen = false },
            onSave = {
                onRenameProfile(activeProfile, it)
                renameProfileOpen = false
            },
        )
    }
    if (deleteProfileOpen) {
        AlertDialog(
            onDismissRequest = { deleteProfileOpen = false },
            title = { Text("Delete ${activeProfile.label}?") },
            text = { Text("Past sessions stay in History. This profile and its active wildlife list will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(activeProfile)
                        deleteProfileOpen = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfileOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (addWildlifeOpen) {
        WildlifeDialog(
            title = "Add wildlife",
            initialName = "",
            initialColor = wildlifeColors.first(),
            onDismiss = { addWildlifeOpen = false },
            onSave = { name, color ->
                onAddSpecies(name, color)
                addWildlifeOpen = false
            },
        )
    }
    editingWildlife?.let { item ->
        WildlifeDialog(
            title = "Edit wildlife",
            initialName = item.name,
            initialColor = item.color,
            onDismiss = { editingWildlife = null },
            onSave = { name, color ->
                onUpdateSpecies(item.id, name, color)
                editingWildlife = null
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Profiles",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (hasActiveSession) {
                        "Active session profile is locked"
                    } else {
                        "Choose which wildlife buttons appear in Observe"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                IconButton(onClick = { showProfileHelp = !showProfileHelp }) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = if (showProfileHelp) {
                            "Hide profile help"
                        } else {
                            "How profiles work"
                        },
                    )
                }
                IconButton(
                    onClick = { addProfileOpen = true },
                    enabled = !hasActiveSession,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add profile")
                }
            }
        }

        if (showProfileHelp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "How profiles work",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Wildlife and Bird Watching are locked starting points. When a session begins, " +
                        "GPS helps load animals commonly found in your region.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Use + to create unlimited custom profiles with your own names, colors, and wildlife. " +
                        "Profile changes affect future sessions; past History remains unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "A profile cannot be switched or edited while an observation session is running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.storageValue }) { profile ->
                FilterChip(
                    selected = activeProfile.storageValue == profile.storageValue,
                    onClick = { onSelectProfile(profile) },
                    leadingIcon = if (profile.isBuiltIn) {
                        {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    label = {
                        Text(
                            profile.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeProfile.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (activeProfile.isBuiltIn) {
                        "GPS regional list | locked"
                    } else {
                        "Custom profile | editable"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activeProfile.isBuiltIn) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                IconButton(
                    onClick = { renameProfileOpen = true },
                    enabled = !hasActiveSession,
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename profile")
                }
                IconButton(
                    onClick = { deleteProfileOpen = true },
                    enabled = !hasActiveSession,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete profile")
                }
                IconButton(
                    onClick = { addWildlifeOpen = true },
                    enabled = !hasActiveSession,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add wildlife")
                }
            }
        }

        if (species.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (activeProfile.isCustom) {
                        "No wildlife in this profile"
                    } else {
                        "Regional wildlife will load when location is available"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(species, key = { it.id }) { item ->
                    SpeciesRow(
                        species = item,
                        editable = activeProfile.isCustom && !hasActiveSession,
                        onEdit = { editingWildlife = item },
                        onArchive = { onArchiveSpecies(item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SpeciesRow(
    species: SpeciesEntity,
    editable: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(species.color)),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = species.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editable) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${species.name}")
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${species.name}")
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Profile name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WildlifeDialog(
    title: String,
    initialName: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedColor by remember(initialColor) { mutableLongStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    wildlifeColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(if (selectedColor == color) 34.dp else 28.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selectedColor = color },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedColor) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
