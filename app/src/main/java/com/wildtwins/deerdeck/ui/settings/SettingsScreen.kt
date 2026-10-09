package com.wildtwins.deerdeck.ui.settings

import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.data.backup.GoogleDriveDocumentProvider
import com.wildtwins.deerdeck.data.settings.BackupStatus
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import com.wildtwins.deerdeck.data.settings.BackupDestination
import com.wildtwins.deerdeck.data.settings.MediaSaveLocation
import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import com.wildtwins.deerdeck.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backupDestination by viewModel.backupDestination.collectAsStateWithLifecycle()
    val googleDriveFolderUri by viewModel.googleDriveFolderUri.collectAsStateWithLifecycle()
    val mediaSaveLocation by viewModel.mediaSaveLocation.collectAsStateWithLifecycle()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsStateWithLifecycle()
    val fieldModeEnabled by viewModel.fieldModeEnabled.collectAsStateWithLifecycle()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val environmentCaptureEnabled by viewModel.environmentCaptureEnabled.collectAsStateWithLifecycle()
    val sharedLocationPrecision by viewModel.sharedLocationPrecision.collectAsStateWithLifecycle()
    val includeNamedFieldLocationsInShares by
        viewModel.includeNamedFieldLocationsInShares.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val backupInProgress by viewModel.backupInProgress.collectAsStateWithLifecycle()
    val backupHealth by viewModel.backupHealth.collectAsStateWithLifecycle()
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()
    val eraseState by viewModel.eraseState.collectAsStateWithLifecycle()
    val sessionTags by viewModel.sessionTags.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var eraseConfirmation by remember { mutableStateOf("") }
    val validGoogleDriveFolderUri = googleDriveFolderUri?.takeIf { storedUri ->
        val uri = runCatching { Uri.parse(storedUri) }.getOrNull()
        val isGoogleDrive = uri?.let { GoogleDriveDocumentProvider.isGoogleDriveTreeUri(context, it) } == true
        val hasWriteAccess = uri != null && GoogleDriveDocumentProvider.hasPersistedReadWriteAccess(context, uri)
        isGoogleDrive && hasWriteAccess
    }
    val driveFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        if (!GoogleDriveDocumentProvider.isGoogleDriveTreeUri(context, uri)) {
            Toast.makeText(
                context,
                "That folder is on this phone. Open the top-left menu, choose Google Drive, then select a folder.",
                Toast.LENGTH_LONG,
            ).show()
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onSuccess {
            viewModel.setGoogleDriveFolderUri(uri)
            Toast.makeText(context, "Google Drive backup folder selected.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "DeerDeck could not keep access to that Drive folder.", Toast.LENGTH_LONG).show()
        }
    }
    val sharePackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val restorePackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
    }

    fun hapticTick() {
        if (hapticFeedbackEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun launchDriveFolderPicker() {
        driveFolderLauncher.launch(GoogleDriveDocumentProvider.initialPickerUri(context))
    }

    LaunchedEffect(googleDriveFolderUri, validGoogleDriveFolderUri) {
        if (googleDriveFolderUri != null && validGoogleDriveFolderUri == null) {
            viewModel.setGoogleDriveFolderUri(null)
            Toast.makeText(
                context,
                "Google Drive folder access expired. Please choose the Drive folder again.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    SettingsContent(
        themeMode = themeMode,
        autoBackupEnabled = autoBackupEnabled,
        backupDestination = backupDestination,
        googleDriveFolderUri = validGoogleDriveFolderUri,
        mediaSaveLocation = mediaSaveLocation,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        fieldModeEnabled = fieldModeEnabled,
        keepScreenAwake = keepScreenAwake,
        environmentCaptureEnabled = environmentCaptureEnabled,
        sharedLocationPrecision = sharedLocationPrecision,
        includeNamedFieldLocationsInShares = includeNamedFieldLocationsInShares,
        backupStatus = backupStatus,
        backupInProgress = backupInProgress,
        backupHealth = backupHealth,
        restoreInProgress = restoreState.inProgress,
        sessionTags = sessionTags,
        onThemeModeSelected = { mode ->
            hapticTick()
            viewModel.setThemeMode(mode)
        },
        onAutoBackupChanged = { enabled ->
            hapticTick()
            viewModel.setAutoBackupEnabled(enabled)
        },
        onBackupDestinationSelected = { destination ->
            hapticTick()
            viewModel.setBackupDestination(destination)
            if (destination.usesDrive && validGoogleDriveFolderUri == null) {
                launchDriveFolderPicker()
            }
        },
        onChooseDriveFolder = {
            hapticTick()
            launchDriveFolderPicker()
        },
        onMediaSaveLocationSelected = { location ->
            hapticTick()
            viewModel.setMediaSaveLocation(location)
        },
        onHapticFeedbackChanged = { enabled ->
            hapticTick()
            viewModel.setHapticFeedbackEnabled(enabled)
        },
        onFieldModeChanged = { enabled ->
            hapticTick()
            viewModel.setFieldModeEnabled(enabled)
        },
        onKeepScreenAwakeChanged = { enabled ->
            hapticTick()
            viewModel.setKeepScreenAwake(enabled)
        },
        onEnvironmentCaptureChanged = { enabled ->
            hapticTick()
            viewModel.setEnvironmentCaptureEnabled(enabled)
        },
        onSharedLocationPrecisionSelected = { precision ->
            hapticTick()
            viewModel.setSharedLocationPrecision(precision)
        },
        onIncludeNamedFieldLocationsChanged = { enabled ->
            hapticTick()
            viewModel.setIncludeNamedFieldLocationsInShares(enabled)
        },
        onBackupNow = {
            hapticTick()
            viewModel.backupNow(context)
        },
        onExportPackage = {
            hapticTick()
            viewModel.exportDataPackage(context) { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                sharePackageLauncher.launch(Intent.createChooser(shareIntent, "Export DeerDeck data package"))
            }
        },
        onExportPrivatePackage = {
            hapticTick()
            viewModel.exportPrivateSharePackage(context) { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                sharePackageLauncher.launch(Intent.createChooser(shareIntent, "Share private DeerDeck package"))
            }
        },
        onRestorePackage = {
            hapticTick()
            restorePackageLauncher.launch(
                arrayOf("application/zip", "application/json", "application/octet-stream"),
            )
        },
        onSessionTagsChanged = viewModel::setSessionTags,
        onResetSessionTags = viewModel::resetSessionTags,
        onShowPrivacyPolicy = { showPrivacyPolicy = true },
        onEraseLocalDataset = { showEraseDialog = true },
        eraseInProgress = eraseState.inProgress,
        modifier = modifier,
    )

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore DeerDeck data?") },
            text = {
                Text(
                    "The selected package will replace the app's current sessions and wildlife setup. " +
                        "DeerDeck will create and verify a phone backup before changing anything.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreDataPackage(context, uri)
                        pendingRestoreUri = null
                    },
                ) { Text("Back up and restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            },
        )
    }

    restoreState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearRestoreMessage,
            title = { Text(if (restoreState.succeeded) "Restore complete" else "Restore stopped") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::clearRestoreMessage) { Text("OK") }
            },
        )
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Privacy policy") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Withrow DeerDeck ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                    Text(PrivacyPolicyText)
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyPolicy = false }) { Text("Done") }
            },
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = {
                showEraseDialog = false
                eraseConfirmation = ""
            },
            title = { Text("Erase local DeerDeck data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This permanently removes sessions, observations, custom wildlife, field locations, " +
                            "and DeerDeck-only media from this app. Camera roll media and backup files remain separate.",
                    )
                    Text("Type DELETE to confirm.", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = eraseConfirmation,
                        onValueChange = { eraseConfirmation = it.take(10) },
                        singleLine = true,
                        label = { Text("Confirmation") },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = eraseConfirmation == "DELETE",
                    onClick = {
                        showEraseDialog = false
                        eraseConfirmation = ""
                        viewModel.eraseLocalDataset(context)
                    },
                ) { Text("Erase data") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showEraseDialog = false
                        eraseConfirmation = ""
                    },
                ) { Text("Cancel") }
            },
        )
    }

    eraseState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearEraseMessage,
            title = { Text(if (eraseState.succeeded) "Local data erased" else "Erase stopped") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::clearEraseMessage) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    themeMode: AppThemeMode,
    autoBackupEnabled: Boolean,
    backupDestination: BackupDestination,
    googleDriveFolderUri: String?,
    mediaSaveLocation: MediaSaveLocation,
    hapticFeedbackEnabled: Boolean,
    fieldModeEnabled: Boolean,
    keepScreenAwake: Boolean,
    environmentCaptureEnabled: Boolean,
    sharedLocationPrecision: SharedLocationPrecision,
    includeNamedFieldLocationsInShares: Boolean,
    backupStatus: BackupStatus,
    backupInProgress: Boolean,
    backupHealth: BackupHealth,
    restoreInProgress: Boolean,
    sessionTags: List<String>,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
    onBackupDestinationSelected: (BackupDestination) -> Unit,
    onChooseDriveFolder: () -> Unit,
    onMediaSaveLocationSelected: (MediaSaveLocation) -> Unit,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    onFieldModeChanged: (Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onEnvironmentCaptureChanged: (Boolean) -> Unit,
    onSharedLocationPrecisionSelected: (SharedLocationPrecision) -> Unit,
    onIncludeNamedFieldLocationsChanged: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onExportPackage: () -> Unit,
    onExportPrivatePackage: () -> Unit,
    onRestorePackage: () -> Unit,
    onSessionTagsChanged: (List<String>) -> Unit,
    onResetSessionTags: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    onEraseLocalDataset: () -> Unit,
    eraseInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    SettingsHelpIcon(
                        title = "Appearance",
                        detail = "Choose whether DeerDeck follows the phone theme or always uses its light or dark palette.",
                    )
                }

                SettingLabel(
                    title = "Theme",
                    detail = "System changes with your phone. Light and Dark keep the selected appearance until you change it.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(
                        mode = AppThemeMode.System,
                        selected = themeMode == AppThemeMode.System,
                        onClick = { onThemeModeSelected(AppThemeMode.System) },
                    )
                    ThemeChip(
                        mode = AppThemeMode.Light,
                        selected = themeMode == AppThemeMode.Light,
                        onClick = { onThemeModeSelected(AppThemeMode.Light) },
                    )
                    ThemeChip(
                        mode = AppThemeMode.Dark,
                        selected = themeMode == AppThemeMode.Dark,
                        onClick = { onThemeModeSelected(AppThemeMode.Dark) },
                    )
                }
            }
        }

        DataSafetyCard(
            autoBackupEnabled = autoBackupEnabled,
            backupDestination = backupDestination,
            googleDriveFolderUri = googleDriveFolderUri,
            backupStatus = backupStatus,
            backupInProgress = backupInProgress,
            backupHealth = backupHealth,
            restoreInProgress = restoreInProgress,
            onAutoBackupChanged = onAutoBackupChanged,
            onBackupDestinationSelected = onBackupDestinationSelected,
            onChooseDriveFolder = onChooseDriveFolder,
            onBackupNow = onBackupNow,
            onExportPackage = onExportPackage,
            onRestorePackage = onRestorePackage,
        )

        PrivacyCard(
            environmentCaptureEnabled = environmentCaptureEnabled,
            locationPrecision = sharedLocationPrecision,
            includeNamedFieldLocations = includeNamedFieldLocationsInShares,
            onEnvironmentCaptureChanged = onEnvironmentCaptureChanged,
            onLocationPrecisionSelected = onSharedLocationPrecisionSelected,
            onIncludeNamedFieldLocationsChanged = onIncludeNamedFieldLocationsChanged,
            onExportPrivatePackage = onExportPrivatePackage,
            onShowPrivacyPolicy = onShowPrivacyPolicy,
            onEraseLocalDataset = onEraseLocalDataset,
            eraseInProgress = eraseInProgress,
        )

        FieldUseCard(
            mediaSaveLocation = mediaSaveLocation,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            fieldModeEnabled = fieldModeEnabled,
            keepScreenAwake = keepScreenAwake,
            onMediaSaveLocationSelected = onMediaSaveLocationSelected,
            onHapticFeedbackChanged = onHapticFeedbackChanged,
            onFieldModeChanged = onFieldModeChanged,
            onKeepScreenAwakeChanged = onKeepScreenAwakeChanged,
        )

        SessionTagsCard(
            tags = sessionTags,
            onTagsChanged = onSessionTagsChanged,
            onReset = onResetSessionTags,
        )

    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataSafetyCard(
    autoBackupEnabled: Boolean,
    backupDestination: BackupDestination,
    googleDriveFolderUri: String?,
    backupStatus: BackupStatus,
    backupInProgress: Boolean,
    backupHealth: BackupHealth,
    restoreInProgress: Boolean,
    onAutoBackupChanged: (Boolean) -> Unit,
    onBackupDestinationSelected: (BackupDestination) -> Unit,
    onChooseDriveFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onExportPackage: () -> Unit,
    onRestorePackage: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Data Safety",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SettingsHelpIcon(
                    title = "Data Safety",
                    detail = "Backups preserve the observation database and references to session media. Full archives also package app-managed media for transfer or recovery.",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val healthColor = when (backupHealth.level) {
                    BackupHealthLevel.Healthy -> MaterialTheme.colorScheme.primary
                    BackupHealthLevel.Due -> MaterialTheme.colorScheme.tertiary
                    BackupHealthLevel.NeedsAttention -> MaterialTheme.colorScheme.error
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(healthColor, androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = backupHealth.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = backupHealth.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingLabel(
                        title = "Auto backup after each session",
                        detail = "When enabled, DeerDeck writes a verified backup after a session ends. A failed backup does not delete or modify the local database.",
                    )
                    Text(
                        text = backupDestinationHelp(backupDestination, googleDriveFolderUri),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupChanged,
                )
            }

            SettingLabel(
                title = "Backup destination",
                detail = "App storage keeps backups on this phone. Google Drive uses Android's document picker, where you must choose a folder provided by the Google Drive app.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupDestination.entries.forEach { destination ->
                    FilterChip(
                        selected = backupDestination == destination,
                        onClick = { onBackupDestinationSelected(destination) },
                        label = { Text(destination.label) },
                    )
                }
            }

            if (backupDestination.usesDrive) {
                OutlinedButton(
                    onClick = onChooseDriveFolder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (googleDriveFolderUri == null) "Choose folder in Google Drive" else "Change folder in Google Drive")
                }
            }

            Text(
                text = backupStatusText(backupStatus),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            backupStatus.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (backupStatus.lastSucceeded == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            backupStatus.warning?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            backupStatus.path?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (backupStatus.lastSuccessAt != null) {
                Text(
                    text = "${backupStatus.sessionCount} sessions, ${backupStatus.observationCount} observations, " +
                        "${backupStatus.mediaCount} media refs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBackupNow,
                    enabled = !backupInProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    if (backupInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (backupInProgress) "Backing up" else "Backup now")
                }
                OutlinedButton(
                    onClick = onExportPackage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Full archive")
                }
            }
            OutlinedButton(
                onClick = onRestorePackage,
                enabled = !restoreInProgress && !backupInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (restoreInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (restoreInProgress) "Restoring" else "Restore ZIP or backup")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionTagsCard(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    onReset: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Session tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SettingsHelpIcon(
                    title = "Session tags",
                    detail = "Tags are reusable labels offered when a session ends. Customize them for conditions or events you want to filter and compare later.",
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onTagsChanged(tags - tag) },
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New tag") },
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        val clean = newTag.trim()
                        if (clean.isNotBlank()) {
                            onTagsChanged(tags + clean)
                            newTag = ""
                        }
                    },
                    enabled = newTag.isNotBlank(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            }
            OutlinedButton(onClick = onReset) {
                Text("Reset defaults")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyCard(
    environmentCaptureEnabled: Boolean,
    locationPrecision: SharedLocationPrecision,
    includeNamedFieldLocations: Boolean,
    onEnvironmentCaptureChanged: (Boolean) -> Unit,
    onLocationPrecisionSelected: (SharedLocationPrecision) -> Unit,
    onIncludeNamedFieldLocationsChanged: (Boolean) -> Unit,
    onExportPrivatePackage: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    onEraseLocalDataset: () -> Unit,
    eraseInProgress: Boolean,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Privacy & Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SettingsHelpIcon(
                    title = "Privacy & Data",
                    detail = "These controls decide what DeerDeck collects and what location detail appears in shareable exports. Full backups retain exact data for recovery.",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingLabel(
                        title = "Location and live weather",
                        detail = "At session start, DeerDeck can save GPS, elevation, sunset, moon data, and nearby National Weather Service conditions. Missing fields are retried without inventing values.",
                    )
                    Text(
                        text = "Stores session GPS and uses the U.S. National Weather Service for conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = environmentCaptureEnabled,
                    onCheckedChange = onEnvironmentCaptureChanged,
                )
            }
            SettingLabel(
                title = "Location in shared CSV and private ZIP",
                detail = "Exact includes full coordinates. Approximate rounds coordinates. Hidden removes coordinates and nearby weather-station identifiers from shareable exports.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedLocationPrecision.entries.forEach { precision ->
                    FilterChip(
                        selected = locationPrecision == precision,
                        onClick = { onLocationPrecisionSelected(precision) },
                        label = { Text(precision.label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingLabel(
                        title = "Include named field locations",
                        detail = "Controls whether names such as North Stand appear in shareable exports. It does not remove those names from your local data or full recovery backups.",
                    )
                    Text(
                        text = "Full backups and full archives always retain exact data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = includeNamedFieldLocations,
                    onCheckedChange = onIncludeNamedFieldLocationsChanged,
                )
            }
            OutlinedButton(
                onClick = onExportPrivatePackage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share private ZIP")
            }
            OutlinedButton(
                onClick = onShowPrivacyPolicy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Privacy policy & app info")
            }
            OutlinedButton(
                onClick = onEraseLocalDataset,
                enabled = !eraseInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (eraseInProgress) "Erasing local data" else "Erase local data")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldUseCard(
    mediaSaveLocation: MediaSaveLocation,
    hapticFeedbackEnabled: Boolean,
    fieldModeEnabled: Boolean,
    keepScreenAwake: Boolean,
    onMediaSaveLocationSelected: (MediaSaveLocation) -> Unit,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    onFieldModeChanged: (Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Vibration, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Field Use",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SettingsHelpIcon(
                    title = "Field Use",
                    detail = "Tune media storage, touch feedback, control sizing, and screen behavior for how you use the app outdoors.",
                )
            }

            SettingLabel(
                title = "Media location",
                detail = "Camera Roll makes media visible with your other phone photos and videos. DeerDeck Only keeps app-managed media private to the app and includes it in full archives.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaSaveLocation.entries.forEach { location ->
                    FilterChip(
                        selected = mediaSaveLocation == location,
                        onClick = { onMediaSaveLocationSelected(location) },
                        label = { Text(location.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingLabel(
                        title = "Haptic feedback",
                        detail = "Adds a subtle vibration confirmation to important field actions such as logging a sighting.",
                    )
                    Text(
                        text = "Subtle taps for core field actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = onHapticFeedbackChanged,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingLabel(
                        title = "Field Mode",
                        detail = "Uses larger, higher-contrast two-column wildlife controls for faster use outdoors or while wearing gloves.",
                    )
                    Text(
                        text = "Larger two-column wildlife controls with stronger contrast and feedback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = fieldModeEnabled,
                    onCheckedChange = onFieldModeChanged,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingLabel(
                        title = "Keep screen awake during sessions",
                        detail = "Prevents automatic display sleep only while a session is active. This can use more battery during long observations.",
                    )
                    Text(
                        text = "Prevents the display from sleeping while an observation is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = keepScreenAwake,
                    onCheckedChange = onKeepScreenAwakeChanged,
                )
            }
        }
    }
}

@Composable
private fun SettingLabel(
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        SettingsHelpIcon(title = title, detail = detail)
    }
}

@Composable
private fun SettingsHelpIcon(
    title: String,
    detail: String,
) {
    var showHelp by remember(title) { mutableStateOf(false) }
    IconButton(
        onClick = { showHelp = true },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "About $title",
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun ThemeChip(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = if (mode == AppThemeMode.Dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
            )
        },
        label = { Text(mode.label) },
    )
}

private fun backupDestinationHelp(
    destination: BackupDestination,
    googleDriveFolderUri: String?,
): String {
    return when (destination) {
        BackupDestination.PhoneOnly -> "Writes a verified JSON backup to Downloads/Withrow DeerDeck."
        BackupDestination.GoogleDrive -> if (googleDriveFolderUri == null) {
            "Choose a Drive folder. If Android opens on this phone, use the top-left menu to select Google Drive."
        } else {
            "Writes a verified JSON backup to the selected Google Drive folder."
        }
        BackupDestination.Both -> if (googleDriveFolderUri == null) {
            "Phone backup is ready. Use the folder picker's top-left menu to choose Google Drive too."
        } else {
            "Writes verified backups to the phone and selected Google Drive folder."
        }
    }
}

private fun backupStatusText(status: BackupStatus): String {
    return when (status.lastSucceeded) {
        true -> "Last verified backup: ${formatBackupTime(status.lastSuccessAt)}"
        false -> "Last backup failed: ${formatBackupTime(status.lastAttemptAt)}"
        null -> "No backup has run yet."
    }
}

private fun formatBackupTime(millis: Long?): String {
    if (millis == null) return "unknown time"

    return DateTimeFormatter
        .ofPattern("MMM d, h:mm a")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

private val PrivacyPolicyText = """
    Effective July 25, 2026

    Withrow DeerDeck is a local-first wildlife observation app. It does not require an account, show ads, use analytics, sell data, or send your observation history to the developer.

    Sessions, wildlife counts, notes, tags, field-location names, precise coordinates, and media references are stored in the app's private local database. Photos and videos are stored either in the app or in your camera roll according to your setting. Audio notes are stored in app-managed storage.

    Camera and microphone permissions are used only when you choose to capture media. Optional speech-to-text is handled by the recognition service configured on your device and may be subject to that provider's privacy terms.

    If Location and live weather is enabled, DeerDeck requests foreground location when you start a session. The app selects a broad regional Wildlife or Birds list locally on the device. Coordinates are sent over HTTPS to the U.S. National Weather Service for weather and forecasts and, when needed, to the USGS Elevation Point Query Service for elevation; profile selection does not make another network request. Exact coordinates are also stored in that local session. You can turn this feature off and use the app without location.

    Backups and exports happen only when enabled or initiated by you. Google Drive backup uses Android's document picker and the folder you select; DeerDeck never receives your Google credentials. Files you share, camera-roll media, and backups are controlled by the destination apps and services you choose.

    Settings > Privacy & Data > Erase local data removes the observation database, custom wildlife, saved field-location names, and app-managed media. Camera-roll media and previously exported backup files remain under your control and must be removed from those locations separately.

    For privacy or support questions, contact https://github.com/wildtwins.
""".trimIndent()
