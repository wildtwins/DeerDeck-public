package com.wildtwins.deerdeck.ui.observe

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.data.SessionInactivityScheduler
import com.wildtwins.deerdeck.data.backup.GoogleDriveDocumentProvider
import com.wildtwins.deerdeck.data.environment.EnvironmentCaptureContract
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.ui.UiFormatters
import com.wildtwins.deerdeck.ui.audio.AudioCaptureActivity
import com.wildtwins.deerdeck.ui.camera.CameraCaptureActivity
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ObserveScreen(
    viewModel: ObserveViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    var drivePromptDismissed by remember(state.googleDriveFolderUri) { mutableStateOf(false) }
    val storedDriveUri = state.googleDriveFolderUri?.let { stored ->
        runCatching { Uri.parse(stored) }.getOrNull()
    }
    val driveReady = storedDriveUri != null &&
        GoogleDriveDocumentProvider.isGoogleDriveTreeUri(context, storedDriveUri) &&
        GoogleDriveDocumentProvider.hasPersistedReadWriteAccess(context, storedDriveUri)
    val driveReconnectNeeded = state.backupDestination.usesDrive &&
        (!driveReady || state.googleDriveBackupIssue != null)
    val driveFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!GoogleDriveDocumentProvider.isGoogleDriveTreeUri(context, uri)) {
            Toast.makeText(
                context,
                "Choose a folder under Google Drive, not this phone.",
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
            viewModel.reconnectGoogleDrive(context, uri)
        }.onFailure {
            Toast.makeText(
                context,
                "DeerDeck could not keep access to that Drive folder. Try another folder.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    DisposableEffect(state.activeSession != null, state.keepScreenAwake) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.activeSession != null && state.keepScreenAwake
        onDispose { view.keepScreenOn = previous }
    }

    if (driveReconnectNeeded && !drivePromptDismissed) {
        AlertDialog(
            onDismissRequest = { drivePromptDismissed = true },
            title = { Text("Reconnect Google Drive") },
            text = {
                Text(
                    "DeerDeck's access to the backup folder expired. Your phone copy is safe, " +
                        "but Drive backups are paused. Choose the Drive folder again and DeerDeck " +
                        "will immediately verify a fresh backup.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        driveFolderLauncher.launch(GoogleDriveDocumentProvider.initialPickerUri(context))
                    },
                    enabled = !state.backupInProgress,
                ) {
                    Text("Reconnect Drive")
                }
            },
            dismissButton = {
                TextButton(onClick = { drivePromptDismissed = true }) {
                    Text("Later")
                }
            },
        )
    }

    ObserveContent(
        state = state,
        onStartSession = { context -> viewModel.startSession(context) },
        onEndSession = { notes, tags -> viewModel.endSession(context, notes, tags) },
        onQuickCount = viewModel::addQuickObservation,
        onDetailedCount = viewModel::addDetailedObservation,
        onUndo = viewModel::undoLast,
        onDecrementCount = viewModel::decrementSpeciesCount,
        onMoveSpecies = viewModel::moveSpeciesToIndex,
        onMediaCaptured = viewModel::attachMedia,
        onSessionTagsChanged = viewModel::setSessionTags,
        onResetSessionTags = viewModel::resetSessionTags,
        onFieldLocationSelected = viewModel::selectFieldLocation,
        onFieldLocationAdded = viewModel::addFieldLocation,
        onFieldLocationRemoved = viewModel::removeFieldLocation,
        onMessageShown = viewModel::clearMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ObserveContent(
    state: ObserveUiState,
    onStartSession: (Context) -> Unit,
    onEndSession: (String, List<String>) -> Unit,
    onQuickCount: (SpeciesEntity) -> Unit,
    onDetailedCount: (SpeciesEntity, Int, String, String, String, String, (Long) -> Unit) -> Unit,
    onUndo: () -> Unit,
    onDecrementCount: (Long) -> Unit,
    onMoveSpecies: (SpeciesEntity, Int) -> Unit,
    onMediaCaptured: (String, String, Long?) -> Unit,
    onSessionTagsChanged: (List<String>) -> Unit,
    onResetSessionTags: () -> Unit,
    onFieldLocationSelected: (String?) -> Unit,
    onFieldLocationAdded: (String) -> Unit,
    onFieldLocationRemoved: (String) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    var detailsSpecies by remember { mutableStateOf<SpeciesEntity?>(null) }
    var showEndSessionDialog by remember { mutableStateOf(false) }
    var pendingMediaObservationId by remember { mutableStateOf<Long?>(null) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onStartSession(context.applicationContext)
    }
    fun hapticTick(kind: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        if (state.hapticFeedbackEnabled) {
            view.performHapticFeedback(
                if (state.fieldModeEnabled && kind == HapticFeedbackConstants.KEYBOARD_TAP) {
                    HapticFeedbackConstants.LONG_PRESS
                } else {
                    kind
                },
            )
        }
    }
    fun startSessionWithEnvironment() {
        hapticTick()
        if (!state.environmentCaptureEnabled) {
            onStartSession(context.applicationContext)
            return
        }
        val hasLocationPermission = locationPermissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (hasLocationPermission) {
            onStartSession(context.applicationContext)
        } else {
            locationPermissionLauncher.launch(locationPermissions)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = result.data?.getStringArrayListExtra(CameraCaptureActivity.EXTRA_MEDIA_URIS)
            val types = result.data?.getStringArrayListExtra(CameraCaptureActivity.EXTRA_MEDIA_TYPES)
            if (!uris.isNullOrEmpty()) {
                uris.forEachIndexed { index, uri ->
                    val mediaType = types?.getOrNull(index) ?: CameraCaptureActivity.MODE_PHOTO
                    if (uri.isNotBlank()) {
                        onMediaCaptured(uri, mediaType, pendingMediaObservationId)
                    }
                }
            } else {
                val uri = result.data?.getStringExtra(CameraCaptureActivity.EXTRA_MEDIA_URI)
                    ?: result.data?.getStringExtra(CameraCaptureActivity.EXTRA_PHOTO_URI)
                val mediaType = result.data?.getStringExtra(CameraCaptureActivity.EXTRA_MEDIA_TYPE)
                    ?: CameraCaptureActivity.MODE_PHOTO
                if (!uri.isNullOrBlank()) {
                    onMediaCaptured(uri, mediaType, pendingMediaObservationId)
                }
            }
        }
        pendingMediaObservationId = null
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getStringExtra(AudioCaptureActivity.EXTRA_MEDIA_URI)
            val mediaType = result.data?.getStringExtra(AudioCaptureActivity.EXTRA_MEDIA_TYPE)
                ?: AudioCaptureActivity.MEDIA_TYPE_AUDIO
            if (!uri.isNullOrBlank()) {
                onMediaCaptured(uri, mediaType, null)
            }
        }
    }

    fun launchAudioRecording() {
        hapticTick()
        audioLauncher.launch(Intent(context, AudioCaptureActivity::class.java))
    }

    LaunchedEffect(state.lastMessage) {
        val message = state.lastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    LaunchedEffect(localMessage) {
        val message = localMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        localMessage = null
    }

    LaunchedEffect(state.activeSession?.id, state.activeSession?.lastActivityAt) {
        val session = state.activeSession
        if (session == null) {
            SessionInactivityScheduler.cancel(context.applicationContext)
        } else {
            SessionInactivityScheduler.schedule(
                context = context.applicationContext,
                lastActivityAt = session.lastActivityAt ?: session.startedAt,
            )
        }
    }

    if (detailsSpecies != null) {
        ObservationDetailSheet(
            species = detailsSpecies!!,
            onDismiss = { detailsSpecies = null },
            onSave = { species, quantity, notes, sex, ageClass, behavior ->
                hapticTick()
                onDetailedCount(species, quantity, notes, sex, ageClass, behavior) {}
                detailsSpecies = null
            },
            onSaveAndPhoto = { species, quantity, notes, sex, ageClass, behavior ->
                hapticTick()
                onDetailedCount(species, quantity, notes, sex, ageClass, behavior) { observationId ->
                    pendingMediaObservationId = observationId
                    cameraLauncher.launch(
                        Intent(context, CameraCaptureActivity::class.java)
                            .putExtra(CameraCaptureActivity.EXTRA_CAPTURE_MODE, CameraCaptureActivity.MODE_PHOTO),
                    )
                }
                detailsSpecies = null
            },
        )
    }

    if (showEndSessionDialog) {
        state.activeSession?.let { session ->
            EndSessionDialog(
                session = session,
                counts = state.counts,
                mediaCount = state.mediaCount,
                availableTags = state.sessionTags,
                onDismiss = { showEndSessionDialog = false },
                onDecrementCount = { speciesId ->
                    hapticTick()
                    onDecrementCount(speciesId)
                },
                onTagsChanged = onSessionTagsChanged,
                onResetTags = onResetSessionTags,
                onSave = { notes, tags ->
                    hapticTick()
                    onEndSession(notes, tags)
                    showEndSessionDialog = false
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(
                active = state.activeSession != null,
                starting = state.isStartingSession,
                profileLabel = state.activeProfile.label,
                startedAt = state.activeSession?.startedAt,
                sunsetAt = state.activeSession?.sunsetAt,
                weatherSummary = state.activeSession?.weatherSummary,
                temperatureF = state.activeSession?.temperatureF,
                windSummary = state.activeSession?.windSummary,
                moonPhase = state.activeSession?.moonPhase,
                moonIlluminationPercent = state.activeSession?.moonIlluminationPercent,
                fieldLocationName = state.activeSession?.fieldLocationName,
                latitude = state.activeSession?.latitude,
                longitude = state.activeSession?.longitude,
                locationAccuracyMeters = state.activeSession?.locationAccuracyMeters,
                elevationFeet = state.activeSession?.elevationFeet,
                pressureInHg = state.activeSession?.pressureInHg,
                humidityPercent = state.activeSession?.humidityPercent,
                dewPointF = state.activeSession?.dewPointF,
                visibilityMiles = state.activeSession?.visibilityMiles,
                precipitationProbabilityPercent = state.activeSession?.precipitationProbabilityPercent,
                forecastSummary = state.activeSession?.forecastSummary,
                environmentStatus = state.activeSession?.environmentStatus,
                environmentCompleteness = state.activeSession
                    ?.takeUnless { EnvironmentCaptureContract.isDisabled(it.environmentStatus) }
                    ?.let(EnvironmentCaptureContract::evaluate)
                    ?.displayText(),
                totalObservations = state.counts.sumOf { it.total },
                speciesSeen = state.counts.count { it.total > 0 },
                onStartSession = ::startSessionWithEnvironment,
                onEndSession = {
                    hapticTick()
                    showEndSessionDialog = true
                },
                onUndo = {
                    hapticTick()
                    onUndo()
                },
                canUndo = state.recentObservations.isNotEmpty(),
                fieldLocations = state.fieldLocations,
                selectedFieldLocation = state.selectedFieldLocation,
                onFieldLocationSelected = onFieldLocationSelected,
                onFieldLocationAdded = onFieldLocationAdded,
                onFieldLocationRemoved = onFieldLocationRemoved,
                onPhoto = {
                    hapticTick()
                    pendingMediaObservationId = null
                    cameraLauncher.launch(
                        Intent(context, CameraCaptureActivity::class.java)
                            .putExtra(CameraCaptureActivity.EXTRA_CAPTURE_MODE, CameraCaptureActivity.MODE_PHOTO),
                    )
                },
                onVideo = {
                    hapticTick()
                    pendingMediaObservationId = null
                    cameraLauncher.launch(
                        Intent(context, CameraCaptureActivity::class.java)
                            .putExtra(CameraCaptureActivity.EXTRA_CAPTURE_MODE, CameraCaptureActivity.MODE_VIDEO),
                    )
                },
                onAudio = ::launchAudioRecording,
            )

            val countsBySpecies = state.counts.associateBy { it.speciesId }
            val gridState = rememberLazyGridState()
            var draggedSpeciesId by remember { mutableStateOf<Long?>(null) }
            var dragStartIndex by remember { mutableIntStateOf(-1) }
            var dropTargetIndex by remember { mutableIntStateOf(-1) }
            var dragTranslation by remember { mutableStateOf(Offset.Zero) }
            var dragItemStartOffset by remember { mutableStateOf(Offset.Zero) }
            var dragItemSize by remember { mutableStateOf(IntSize.Zero) }

            fun resetDrag() {
                draggedSpeciesId = null
                dragStartIndex = -1
                dropTargetIndex = -1
                dragTranslation = Offset.Zero
                dragItemStartOffset = Offset.Zero
                dragItemSize = IntSize.Zero
            }

            fun nearestVisibleItemIndex(offset: Offset): Int? {
                return gridState.layoutInfo.visibleItemsInfo
                    .filter { item -> item.index in state.species.indices }
                    .minByOrNull { item ->
                        val centerX = item.offset.x + (item.size.width / 2f)
                        val centerY = item.offset.y + (item.size.height / 2f)
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        (dx * dx) + (dy * dy)
                    }
                    ?.index
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (state.fieldModeEnabled) 124.dp else 96.dp),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(state.species, state.activeProfile.storageValue) {
                        if (!state.activeProfile.isCustom) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    offset.x >= item.offset.x &&
                                        offset.x <= item.offset.x + item.size.width &&
                                        offset.y >= item.offset.y &&
                                        offset.y <= item.offset.y + item.size.height
                                }
                                val species = item?.index?.let { index -> state.species.getOrNull(index) }
                                if (item == null || species == null) {
                                    resetDrag()
                                } else {
                                    draggedSpeciesId = species.id
                                    dragStartIndex = item.index
                                    dropTargetIndex = item.index
                                    dragTranslation = Offset.Zero
                                    dragItemStartOffset = Offset(
                                        x = item.offset.x.toFloat(),
                                        y = item.offset.y.toFloat(),
                                    )
                                    dragItemSize = item.size
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggedSpeciesId != null) {
                                    dragTranslation += dragAmount
                                    val draggedCenter = dragItemStartOffset +
                                        dragTranslation +
                                        Offset(
                                            x = dragItemSize.width / 2f,
                                            y = dragItemSize.height / 2f,
                                        )
                                    dropTargetIndex = nearestVisibleItemIndex(draggedCenter) ?: dropTargetIndex
                                }
                            },
                            onDragEnd = {
                                val species = draggedSpeciesId?.let { id ->
                                    state.species.firstOrNull { it.id == id }
                                }
                                if (
                                    species != null &&
                                    dropTargetIndex in state.species.indices &&
                                    dropTargetIndex != dragStartIndex
                                ) {
                                    hapticTick(HapticFeedbackConstants.LONG_PRESS)
                                    onMoveSpecies(species, dropTargetIndex)
                                }
                                resetDrag()
                            },
                            onDragCancel = {
                                resetDrag()
                            },
                        )
                    },
                contentPadding = PaddingValues(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.species, key = { _, species -> species.id }) { index, species ->
                    val total = countsBySpecies[species.id]?.total ?: 0
                    val isDragging = species.id == draggedSpeciesId
                    SpeciesCountCard(
                        species = species,
                        total = total,
                        enabled = state.activeSession != null,
                        isDragging = isDragging,
                        dragTranslation = if (isDragging) dragTranslation else Offset.Zero,
                        fieldModeEnabled = state.fieldModeEnabled,
                        onQuickCount = {
                            hapticTick()
                            onQuickCount(species)
                        },
                        onDetails = {
                            hapticTick()
                            detailsSpecies = species
                        },
                    )
                }
            }

            if (state.species.isEmpty()) {
                EmptySpeciesMessage()
            }
        }
    }
}

@Composable
private fun Header(
    active: Boolean,
    starting: Boolean,
    profileLabel: String,
    startedAt: Long?,
    sunsetAt: Long?,
    weatherSummary: String?,
    temperatureF: Double?,
    windSummary: String?,
    moonPhase: String?,
    moonIlluminationPercent: Double?,
    fieldLocationName: String?,
    latitude: Double?,
    longitude: Double?,
    locationAccuracyMeters: Double?,
    elevationFeet: Double?,
    pressureInHg: Double?,
    humidityPercent: Double?,
    dewPointF: Double?,
    visibilityMiles: Double?,
    precipitationProbabilityPercent: Double?,
    forecastSummary: String?,
    environmentStatus: String?,
    environmentCompleteness: String?,
    totalObservations: Int,
    speciesSeen: Int,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    fieldLocations: List<String>,
    selectedFieldLocation: String?,
    onFieldLocationSelected: (String?) -> Unit,
    onFieldLocationAdded: (String) -> Unit,
    onFieldLocationRemoved: (String) -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
) {
    var now by remember(active, startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, startedAt) {
        if (active && startedAt != null) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                HeaderActions(
                    active = active,
                    starting = starting,
                    onStartSession = onStartSession,
                    onEndSession = onEndSession,
                    onPhoto = onPhoto,
                    onVideo = onVideo,
                    onAudio = onAudio,
                    onUndo = onUndo,
                    canUndo = canUndo,
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (active) Color(0xFF2E7D32) else Color(0xFF8A817C)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Withrow DeerDeck",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = when {
                        active && startedAt != null ->
                            "$profileLabel | Watching since ${UiFormatters.time(startedAt)}"
                        starting -> "$profileLabel | Getting regional defaults"
                        else -> "$profileLabel | Ready when the woods are"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (active) {
                    val environmentText = activeEnvironmentText(
                        sunsetAt = sunsetAt,
                        weatherSummary = weatherSummary,
                        temperatureF = temperatureF,
                        windSummary = windSummary,
                        moonPhase = moonPhase,
                        moonIlluminationPercent = moonIlluminationPercent,
                        fieldLocationName = fieldLocationName,
                        latitude = latitude,
                        longitude = longitude,
                        locationAccuracyMeters = locationAccuracyMeters,
                        elevationFeet = elevationFeet,
                        pressureInHg = pressureInHg,
                        humidityPercent = humidityPercent,
                        dewPointF = dewPointF,
                        visibilityMiles = visibilityMiles,
                        precipitationProbabilityPercent = precipitationProbabilityPercent,
                        forecastSummary = forecastSummary,
                        environmentStatus = environmentStatus,
                        environmentCompleteness = environmentCompleteness,
                    )
                    if (environmentText.isNotBlank()) {
                        Text(
                            text = environmentText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!active) {
                FieldLocationPicker(
                    locations = fieldLocations,
                    selectedLocation = selectedFieldLocation,
                    onSelected = onFieldLocationSelected,
                    onAdded = onFieldLocationAdded,
                    onRemoved = onFieldLocationRemoved,
                )
            }

            if (active) {
                SessionReliabilityStrip(
                    gpsReady = latitude != null && longitude != null,
                    weatherReady = !weatherSummary.isNullOrBlank() ||
                        temperatureF != null ||
                        !windSummary.isNullOrBlank(),
                    moonReady = !moonPhase.isNullOrBlank(),
                )
            }

            if (active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SessionStat(
                        label = "Sightings",
                        value = totalObservations.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    SessionStat(
                        label = "Types",
                        value = speciesSeen.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    SessionStat(
                        label = "Duration",
                        value = startedAt?.let { UiFormatters.duration(it, now) } ?: "0m",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

        }
    }
}

@Composable
private fun FieldLocationPicker(
    locations: List<String>,
    selectedLocation: String?,
    onSelected: (String?) -> Unit,
    onAdded: (String) -> Unit,
    onRemoved: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    var newLocationName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selectedLocation ?: "GPS location",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("GPS location only") },
                    onClick = {
                        onSelected(null)
                        menuExpanded = false
                    },
                )
                locations.forEach { location ->
                    DropdownMenuItem(
                        text = { Text(location) },
                        onClick = {
                            onSelected(location)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = { showAddDialog = true },
            enabled = locations.size < 12,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add field location")
        }
        if (selectedLocation != null) {
            IconButton(onClick = { pendingRemoval = selectedLocation }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove field location")
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add field location") },
            text = {
                OutlinedTextField(
                    value = newLocationName,
                    onValueChange = { newLocationName = it.take(40) },
                    label = { Text("Name") },
                    placeholder = { Text("North stand") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAdded(newLocationName)
                        newLocationName = ""
                        showAddDialog = false
                    },
                    enabled = newLocationName.isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    pendingRemoval?.let { location ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove $location?") },
            text = { Text("Past sessions keep this location name.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoved(location)
                        pendingRemoval = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HeaderActions(
    active: Boolean,
    starting: Boolean,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = if (active) onEndSession else onStartSession,
            enabled = active || !starting,
            modifier = Modifier.size(42.dp),
        ) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (active) Icons.Default.Stop else Icons.Default.Visibility,
                    contentDescription = if (active) "End session" else "Start session",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(
            onClick = onPhoto,
            enabled = active,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Take photo",
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onVideo,
            enabled = active,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Record video",
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onAudio,
            enabled = active,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Record session audio",
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = "Undo last observation",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SessionStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionReliabilityStrip(
    gpsReady: Boolean,
    weatherReady: Boolean,
    moonReady: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReliabilityStatus(Icons.Default.LocationOn, "GPS", gpsReady, Modifier.weight(1f))
        ReliabilityStatus(Icons.Default.Cloud, "Weather", weatherReady, Modifier.weight(1f))
        ReliabilityStatus(Icons.Default.DarkMode, "Moon", moonReady, Modifier.weight(1f))
        ReliabilityStatus(Icons.Default.Save, "Saved", true, Modifier.weight(1f))
    }
}

@Composable
private fun ReliabilityStatus(
    icon: ImageVector,
    label: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$label ${if (ready) "ready" else "pending"}",
            modifier = Modifier.size(15.dp),
            tint = color,
        )
        Spacer(Modifier.width(3.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Text(
                text = if (ready) "Ready" else "Waiting",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

private fun activeEnvironmentText(
    sunsetAt: Long?,
    weatherSummary: String?,
    temperatureF: Double?,
    windSummary: String?,
    moonPhase: String?,
    moonIlluminationPercent: Double?,
    fieldLocationName: String?,
    latitude: Double?,
    longitude: Double?,
    locationAccuracyMeters: Double?,
    elevationFeet: Double?,
    pressureInHg: Double?,
    humidityPercent: Double?,
    dewPointF: Double?,
    visibilityMiles: Double?,
    precipitationProbabilityPercent: Double?,
    forecastSummary: String?,
    environmentStatus: String?,
    environmentCompleteness: String? = null,
): String {
    val weather = weatherText(weatherSummary, temperatureF, windSummary)
    val location = fieldLocationName
        ?.takeIf { it.isNotBlank() }
        ?.let { "Location $it" }
        ?: locationText(latitude, longitude, locationAccuracyMeters, elevationFeet)
    val atmosphere = atmosphereText(
        pressureInHg = pressureInHg,
        humidityPercent = humidityPercent,
        dewPointF = dewPointF,
        visibilityMiles = visibilityMiles,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
    )
    val sunset = sunsetAt?.let { "Sunset ${UiFormatters.time(it)}" }
    val moon = moonText(moonPhase, moonIlluminationPercent)
    val forecast = forecastSummary
        ?.takeIf { it.isNotBlank() && !it.equals(weatherSummary, ignoreCase = true) }
        ?.let { "Forecast $it" }
    val status = environmentStatus?.takeUnless {
        it == "Captured" || it == "Complete" || it == "Partial" || it == "Collecting"
    }
        ?.takeIf { weather.isBlank() || sunset == null }
    val parts = listOfNotNull(
        location,
        sunset,
        weather,
        atmosphere,
        forecast,
        moon,
        environmentCompleteness,
        status,
    ).filter { it.isNotBlank() }
    if (parts.isNotEmpty()) return parts.joinToString(" / ")
    return environmentStatus?.takeUnless { it == "Captured" }.orEmpty()
}

private fun weatherText(
    weatherSummary: String?,
    temperatureF: Double?,
    windSummary: String?,
): String {
    val temp = temperatureF?.let { "${String.format(Locale.US, "%.0f", it)}F" }
    return listOfNotNull(weatherSummary, temp, windSummary)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun moonText(
    moonPhase: String?,
    moonIlluminationPercent: Double?,
): String {
    val phase = moonPhase?.takeIf { it.isNotBlank() } ?: return ""
    val illumination = moonIlluminationPercent?.let { "${String.format(Locale.US, "%.0f", it)}%" }
    return listOfNotNull("Moon $phase", illumination)
        .joinToString(" ")
}

private fun locationText(
    latitude: Double?,
    longitude: Double?,
    locationAccuracyMeters: Double?,
    elevationFeet: Double?,
): String {
    if (latitude == null || longitude == null) return ""
    val coordinates = "${String.format(Locale.US, "%.4f", latitude)}, ${String.format(Locale.US, "%.4f", longitude)}"
    val accuracy = locationAccuracyMeters?.let { "±${String.format(Locale.US, "%.0f", it)}m" }
    val elevation = elevationFeet?.let { "Elev ${String.format(Locale.US, "%.0f", it)}ft" }
    return listOfNotNull("GPS $coordinates", accuracy, elevation).joinToString(" ")
}

private fun atmosphereText(
    pressureInHg: Double?,
    humidityPercent: Double?,
    dewPointF: Double?,
    visibilityMiles: Double?,
    precipitationProbabilityPercent: Double?,
): String {
    return listOfNotNull(
        pressureInHg?.let { "Pressure ${String.format(Locale.US, "%.2f", it)} inHg" },
        humidityPercent?.let { "Humidity ${String.format(Locale.US, "%.0f", it)}%" },
        dewPointF?.let { "Dew ${String.format(Locale.US, "%.0f", it)}F" },
        visibilityMiles?.let { "Visibility ${String.format(Locale.US, "%.1f", it)} mi" },
        precipitationProbabilityPercent?.let { "Precip ${String.format(Locale.US, "%.0f", it)}%" },
    ).joinToString(" ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeciesCountCard(
    species: SpeciesEntity,
    total: Int,
    enabled: Boolean,
    isDragging: Boolean,
    dragTranslation: Offset,
    fieldModeEnabled: Boolean,
    onQuickCount: () -> Unit,
    onDetails: () -> Unit,
) {
    val speciesColor = Color(species.color)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragTranslation.x
                translationY = dragTranslation.y
                scaleX = if (isDragging) 1.04f else 1f
                scaleY = if (isDragging) 1.04f else 1f
                shadowElevation = if (isDragging) 12.dp.toPx() else 0f
            }
            .alpha(if (enabled) 1f else 0.78f)
            .combinedClickable(
                enabled = enabled,
                onClick = onQuickCount,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (fieldModeEnabled) {
                speciesColor.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (fieldModeEnabled) BorderStroke(2.dp, speciesColor) else null,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (fieldModeEnabled) 14.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(speciesColor),
                )
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Move ${species.name}",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = total.toString(),
                style = if (fieldModeEnabled) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = species.name,
                    style = if (fieldModeEnabled) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = onDetails,
                    enabled = enabled,
                ) {
                    Text(
                        text = "Details",
                        style = if (fieldModeEnabled) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ObservationDetailSheet(
    species: SpeciesEntity,
    onDismiss: () -> Unit,
    onSave: (SpeciesEntity, Int, String, String, String, String) -> Unit,
    onSaveAndPhoto: (SpeciesEntity, Int, String, String, String, String) -> Unit,
) {
    var quantity by remember(species.id) { mutableIntStateOf(1) }
    var notes by remember(species.id) { mutableStateOf("") }
    var sex by remember(species.id) { mutableStateOf("") }
    var ageClass by remember(species.id) { mutableStateOf("") }
    var behavior by remember(species.id) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${species.name} details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Quantity", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { quantity = (quantity - 1).coerceAtLeast(1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                }
                Text(
                    text = quantity.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { quantity += 1 }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase quantity")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Doe", "Buck", "Unknown").forEach { value ->
                    FilterChip(
                        selected = sex == value,
                        onClick = { sex = value },
                        label = { Text(value) },
                    )
                }
                listOf("Adult", "Young").forEach { value ->
                    FilterChip(
                        selected = ageClass == value,
                        onClick = { ageClass = value },
                        label = { Text(value) },
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Feeding", "Moving", "Bedding", "Alert", "Chasing").forEach { value ->
                    FilterChip(
                        selected = behavior == value,
                        onClick = { behavior = value },
                        label = { Text(value) },
                    )
                }
            }

            OutlinedTextField(
                value = behavior,
                onValueChange = { behavior = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Behavior") },
                singleLine = true,
                placeholder = { Text("Feeding, moving, bedding...") },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 2,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onSave(species, quantity, notes, sex, ageClass, behavior)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
                Button(
                    onClick = {
                        onSaveAndPhoto(species, quantity, notes, sex, ageClass, behavior)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save + Photo")
                }
            }
        }
    }
}

@Composable
private fun EmptySpeciesMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Wildlife buttons are loading.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndSessionDialog(
    session: ObservationSessionEntity,
    counts: List<SpeciesCount>,
    mediaCount: Int,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onDecrementCount: (Long) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    onResetTags: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var notes by remember { mutableStateOf("") }
    var newTag by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!transcript.isNullOrBlank()) {
                notes = notes.withAppendedNote(transcript)
                voiceError = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review session") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionReviewSummary(
                    session = session,
                    counts = counts,
                    mediaCount = mediaCount,
                    onDecrementCount = onDecrementCount,
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableTags.forEach { value ->
                        FilterChip(
                            selected = value in selectedTags,
                            onClick = {
                                selectedTags = if (value in selectedTags) {
                                    selectedTags - value
                                } else {
                                    selectedTags + value
                                }
                            },
                            label = { Text(value) },
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
                        label = { Text("Add tag") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val cleanTag = newTag.trim()
                            if (cleanTag.isNotBlank()) {
                                onTagsChanged(availableTags + cleanTag)
                                selectedTags = selectedTags + cleanTag
                                newTag = ""
                            }
                        },
                        enabled = newTag.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Session notes")
                            }
                            try {
                                voiceLauncher.launch(voiceIntent)
                            } catch (error: ActivityNotFoundException) {
                                voiceError = "Voice input unavailable on this phone."
                            }
                        },
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Speak note")
                    }
                    TextButton(
                        onClick = {
                            selectedTags = emptySet()
                            onResetTags()
                        },
                    ) {
                        Text("Reset tags")
                    }
                }

                voiceError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Session notes") },
                    minLines = 3,
                    placeholder = { Text("Weather, feeder status, anything unusual...") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(notes, selectedTags.toList()) }) {
                Text("Save session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep observing")
            }
        },
    )
}

@Composable
private fun SessionReviewSummary(
    session: ObservationSessionEntity,
    counts: List<SpeciesCount>,
    mediaCount: Int,
    onDecrementCount: (Long) -> Unit,
) {
    val visibleCounts = counts.filter { it.total > 0 }
    val conditions = activeEnvironmentText(
        sunsetAt = session.sunsetAt,
        weatherSummary = session.weatherSummary,
        temperatureF = session.temperatureF,
        windSummary = session.windSummary,
        moonPhase = session.moonPhase,
        moonIlluminationPercent = session.moonIlluminationPercent,
        fieldLocationName = session.fieldLocationName,
        latitude = session.latitude,
        longitude = session.longitude,
        locationAccuracyMeters = session.locationAccuracyMeters,
        elevationFeet = session.elevationFeet,
        pressureInHg = session.pressureInHg,
        humidityPercent = session.humidityPercent,
        dewPointF = session.dewPointF,
        visibilityMiles = session.visibilityMiles,
        precipitationProbabilityPercent = session.precipitationProbabilityPercent,
        forecastSummary = session.forecastSummary,
        environmentStatus = session.environmentStatus,
        environmentCompleteness = session
            .takeUnless { EnvironmentCaptureContract.isDisabled(it.environmentStatus) }
            ?.let(EnvironmentCaptureContract::evaluate)
            ?.displayText(),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ReviewMetric("Seen", visibleCounts.sumOf { it.total }.toString(), Modifier.weight(1f))
            ReviewMetric("Types", visibleCounts.size.toString(), Modifier.weight(1f))
            ReviewMetric("Media", mediaCount.toString(), Modifier.weight(1f))
            ReviewMetric(
                "Time",
                UiFormatters.duration(session.startedAt, System.currentTimeMillis()),
                Modifier.weight(1f),
            )
        }
        if (conditions.isNotBlank()) {
            Text(
                text = conditions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visibleCounts.isNotEmpty()) {
            Text("Wildlife counts", style = MaterialTheme.typography.labelLarge)
            visibleCounts.forEach { count ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = count.speciesName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = count.total.toString(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = { onDecrementCount(count.speciesId) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Remove one ${count.speciesName}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun String.withAppendedNote(note: String): String {
    return if (isBlank()) note else "$this; $note"
}
