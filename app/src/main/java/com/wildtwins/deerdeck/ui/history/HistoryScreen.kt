package com.wildtwins.deerdeck.ui.history

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.data.export.CsvExport
import com.wildtwins.deerdeck.data.environment.EnvironmentCaptureContract
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.RecentObservation
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.SessionTagCodec
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.ui.UiFormatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HistoryViewMode(val label: String) {
    Timeline("Timeline"),
    Gallery("Gallery"),
}

private val GalleryDateFormatter = DateTimeFormatter.ofPattern("M/d/yy")
    .withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCsvExport by remember { mutableStateOf<CsvExport?>(null) }
    var pendingDeleteSession by remember { mutableStateOf<SessionSummary?>(null) }
    var pendingDeleteObservation by remember { mutableStateOf<RecentObservation?>(null) }
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val export = pendingCsvExport
        pendingCsvExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(export.content.toByteArray(Charsets.UTF_8))
            } ?: error("No output stream")
        }.onSuccess {
            Toast.makeText(context, "CSV saved", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.sessions) {
        if (state.selectedSessionId == null && state.sessions.isNotEmpty()) {
            viewModel.selectSession(state.sessions.first().id)
        }
    }

    HistoryContent(
        state = state,
        onSelectSession = viewModel::selectSession,
        onDeleteSession = { pendingDeleteSession = it },
        onDeleteObservation = { pendingDeleteObservation = it },
        onOpenMedia = { media ->
            val mediaUri = Uri.parse(media.uri)
            val mimeType = when (media.mediaType.lowercase()) {
                "video" -> "video/*"
                "audio" -> "audio/*"
                else -> "image/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(mediaUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { Toast.makeText(context, "Media is unavailable", Toast.LENGTH_SHORT).show() }
        },
        onOpenMap = { session ->
            val latitude = session.latitude
            val longitude = session.longitude
            if (latitude != null && longitude != null) {
                val label = Uri.encode(session.fieldLocationName ?: "DeerDeck observation")
                val mapUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, mapUri)) }
                    .onFailure { Toast.makeText(context, "No map app is available", Toast.LENGTH_SHORT).show() }
            }
        },
        onExportCsv = {
            scope.launch {
                runCatching { viewModel.exportObservationsCsv(context) }
                    .onSuccess { uri ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Withrow DeerDeck observations")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export Withrow DeerDeck CSV"))
                    }
                    .onFailure {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
            }
        },
        onSaveCsv = {
            scope.launch {
                runCatching { viewModel.buildObservationsCsv() }
                    .onSuccess { export ->
                        pendingCsvExport = export
                        saveCsvLauncher.launch(export.fileName)
                    }
                    .onFailure {
                        Toast.makeText(context, "CSV save failed", Toast.LENGTH_SHORT).show()
                    }
            }
        },
        modifier = modifier,
    )

    pendingDeleteSession?.let { session ->
        ConfirmDeleteSessionDialog(
            session = session,
            onDismiss = { pendingDeleteSession = null },
            onConfirm = {
                viewModel.deleteSession(session.id)
                pendingDeleteSession = null
            },
        )
    }

    pendingDeleteObservation?.let { observation ->
        ConfirmDeleteObservationDialog(
            observation = observation,
            onDismiss = { pendingDeleteObservation = null },
            onConfirm = {
                viewModel.deleteObservation(observation.id)
                pendingDeleteObservation = null
            },
        )
    }
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (SessionSummary) -> Unit,
    onDeleteObservation: (RecentObservation) -> Unit,
    onOpenMedia: (ObservationPhotoEntity) -> Unit,
    onOpenMap: (SessionSummary) -> Unit,
    onExportCsv: () -> Unit,
    onSaveCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewMode by rememberSaveable { mutableStateOf(HistoryViewMode.Timeline) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var historyRange by rememberSaveable { mutableStateOf(HistoryRange.All) }
    val filteredSessions = remember(
        state.sessions,
        state.sessionWildlifeCounts,
        searchQuery,
        historyRange,
    ) {
        filterHistorySessions(
            sessions = state.sessions,
            wildlifeCounts = state.sessionWildlifeCounts,
            query = searchQuery,
            range = historyRange,
        )
    }
    val filteredSessionIds = remember(filteredSessions) { filteredSessions.mapTo(mutableSetOf()) { it.id } }
    LaunchedEffect(filteredSessions, state.selectedSessionId) {
        if (filteredSessions.isNotEmpty() && state.selectedSessionId !in filteredSessionIds) {
            onSelectSession(filteredSessions.first().id)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        TabRow(selectedTabIndex = viewMode.ordinal) {
            HistoryViewMode.entries.forEach { mode ->
                Tab(
                    selected = mode == viewMode,
                    onClick = { viewMode = mode },
                    text = { Text(mode.label) },
                )
            }
        }


        if (state.sessions.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search history") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryRange.entries) { range ->
                    FilterChip(
                        selected = historyRange == range,
                        onClick = { historyRange = range },
                        label = { Text(range.label) },
                    )
                }
            }
        }

        if (state.sessions.isEmpty()) {
            EmptyHistory()
        } else if (filteredSessions.isEmpty()) {
            EmptyFilteredHistory()
        } else {
            when (viewMode) {
                HistoryViewMode.Timeline -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onExportCsv,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Share CSV")
                        }
                        FilledTonalButton(
                            onClick = onSaveCsv,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Full CSV")
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filteredSessions, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                selected = session.id == state.selectedSessionId,
                                counts = if (session.id == state.selectedSessionId) state.selectedCounts else emptyList(),
                                photos = if (session.id == state.selectedSessionId) state.selectedPhotos else emptyList(),
                                observations = if (session.id == state.selectedSessionId) state.selectedObservations else emptyList(),
                                onClick = { onSelectSession(session.id) },
                                onDeleteSession = { onDeleteSession(session) },
                                onDeleteObservation = onDeleteObservation,
                                onOpenMap = { onOpenMap(session) },
                            )
                        }
                    }
                }

                HistoryViewMode.Gallery -> MediaGallery(
                    media = state.allMedia.filter { it.session.id in filteredSessionIds },
                    onOpenMedia = onOpenMedia,
                )
            }
        }
    }
}

@Composable
private fun EmptyFilteredHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No matching sessions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Try a wildlife name, tag, condition, location, or a wider time range.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionSummary,
    selected: Boolean,
    counts: List<SpeciesCount>,
    photos: List<ObservationPhotoEntity>,
    observations: List<RecentObservation>,
    onClick: () -> Unit,
    onDeleteSession: () -> Unit,
    onDeleteObservation: (RecentObservation) -> Unit,
    onOpenMap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = UiFormatters.dayTime(session.startedAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Duration ${UiFormatters.duration(session.startedAt, session.endedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val tags = SessionTagCodec.decode(session.tags)
                    if (tags.isNotEmpty()) {
                        Text(
                            text = "Tags: ${tags.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!session.notes.isNullOrBlank()) {
                        Text(
                            text = session.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val environmentText = session.environmentText()
                    if (environmentText.isNotBlank()) {
                        Text(
                            text = environmentText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = session.totalObservations.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" ${session.photoCount} media", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    IconButton(onClick = onDeleteSession) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete session from history",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (selected) {
                CountsBreakdown(counts)
                ObservationTimeline(
                    observations = observations,
                    onDeleteObservation = onDeleteObservation,
                )
                PhotoStrip(photos)
                if (session.latitude != null && session.longitude != null) {
                    OutlinedButton(
                        onClick = onOpenMap,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open session location in Maps")
                    }
                }
                FilledTonalButton(
                    onClick = onDeleteSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Delete session")
                }
            }
        }
    }
}

@Composable
private fun CountsBreakdown(counts: List<SpeciesCount>) {
    val visibleCounts = counts.filter { it.total > 0 }
    if (visibleCounts.isEmpty()) {
        Text(
            text = "No observations logged in this session yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleCounts.forEach { count ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(count.color)),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(count.speciesName)
                }
                Text(count.total.toString(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ObservationTimeline(
    observations: List<RecentObservation>,
    onDeleteObservation: (RecentObservation) -> Unit,
) {
    if (observations.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Timeline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        observations.forEach { observation ->
            ObservationTimelineRow(
                observation = observation,
                onDelete = { onDeleteObservation(observation) },
            )
        }
    }
}

@Composable
private fun ObservationTimelineRow(
    observation: RecentObservation,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(observation.color)),
            )
            Spacer(Modifier.size(8.dp))
            Column {
                Text(
                    text = if (observation.quantity > 1) {
                        "${observation.speciesName} x${observation.quantity}"
                    } else {
                        observation.speciesName
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val detail = observation.detailText()
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = UiFormatters.time(observation.observedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${observation.summary()}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun RecentObservation.summary(): String {
    return if (quantity > 1) {
        "$speciesName x$quantity"
    } else {
        speciesName
    }
}

private fun RecentObservation.detailText(): String {
    return listOfNotNull(sex, ageClass, behavior, notes)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
}

private fun SessionSummary.environmentText(): String {
    val temp = temperatureF?.let { "${String.format(Locale.US, "%.0f", it)}F" }
    val weather = listOfNotNull(weatherSummary, temp, windSummary)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val gpsLocation = if (latitude != null && longitude != null) {
        val coordinates = "${String.format(Locale.US, "%.4f", latitude)}, ${String.format(Locale.US, "%.4f", longitude)}"
        val accuracy = locationAccuracyMeters?.let { "±${String.format(Locale.US, "%.0f", it)}m" }
        val elevation = elevationFeet?.let { "Elev ${String.format(Locale.US, "%.0f", it)}ft" }
        listOfNotNull("GPS $coordinates", accuracy, elevation).joinToString(" ")
    } else {
        null
    }
    val location = listOfNotNull(
        fieldLocationName?.takeIf { it.isNotBlank() },
        gpsLocation,
    ).joinToString(" / ").takeIf { it.isNotBlank() }
    val atmosphere = listOfNotNull(
        pressureInHg?.let { "Pressure ${String.format(Locale.US, "%.2f", it)} inHg" },
        humidityPercent?.let { "Humidity ${String.format(Locale.US, "%.0f", it)}%" },
        dewPointF?.let { "Dew ${String.format(Locale.US, "%.0f", it)}F" },
        visibilityMiles?.let { "Visibility ${String.format(Locale.US, "%.1f", it)} mi" },
        precipitationProbabilityPercent?.let { "Precip ${String.format(Locale.US, "%.0f", it)}%" },
    ).joinToString(" ")
    val forecast = forecastSummary
        ?.takeIf { it.isNotBlank() && !it.equals(weatherSummary, ignoreCase = true) }
        ?.let { "Forecast $it" }
    val sunset = sunsetAt?.let { "Sunset ${UiFormatters.time(it)}" }
    val moon = moonPhase
        ?.takeIf { it.isNotBlank() }
        ?.let { phase ->
            val illumination = moonIlluminationPercent?.let { "${String.format(Locale.US, "%.0f", it)}%" }
            listOfNotNull("Moon $phase", illumination).joinToString(" ")
        }
    val completeness = takeUnless { EnvironmentCaptureContract.isDisabled(environmentStatus) }
        ?.let(EnvironmentCaptureContract::evaluate)
        ?.displayText()
    val provenance = listOfNotNull(elevationSource, weatherSource)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
        ?.let { "Sources $it" }
    val status = environmentStatus?.takeUnless {
        it == "Captured" || it == "Complete" || it == "Partial" || it == "Collecting"
    }
        ?.takeIf { weather.isBlank() || sunset == null }
    return listOfNotNull(
        location,
        weather.takeIf { it.isNotBlank() },
        atmosphere.takeIf { it.isNotBlank() },
        forecast,
        sunset,
        moon,
        completeness,
        provenance,
        status,
    )
        .joinToString(" / ")
}

@Composable
private fun PhotoStrip(photos: List<ObservationPhotoEntity>) {
    if (photos.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { photo ->
            MediaThumb(photo)
        }
    }
}

@Composable
private fun MediaThumb(photo: ObservationPhotoEntity) {
    MediaArtwork(
        media = photo,
        modifier = Modifier.size(96.dp),
    )
}

@Composable
private fun MediaGallery(
    media: List<HistoryMediaItem>,
    onOpenMedia: (ObservationPhotoEntity) -> Unit,
) {
    if (media.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No session media yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${media.size} media ${if (media.size == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(media, key = { it.media.id }) { item ->
                GalleryMediaTile(item = item, onOpenMedia = onOpenMedia)
            }
        }
    }
}

@Composable
private fun GalleryMediaTile(
    item: HistoryMediaItem,
    onOpenMedia: (ObservationPhotoEntity) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenMedia(item.media) },
    ) {
        MediaArtwork(
            media = item.media,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 5.dp, vertical = 3.dp),
        ) {
            val type = when (item.media.mediaType.lowercase()) {
                "video" -> "Video"
                "audio" -> "Audio"
                else -> "Photo"
            }
            Text(
                text = "$type ${GalleryDateFormatter.format(Instant.ofEpochMilli(item.media.capturedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaArtwork(
    media: ObservationPhotoEntity,
    modifier: Modifier = Modifier,
) {
    val isVideo = media.mediaType.equals("video", ignoreCase = true)
    val isAudio = media.mediaType.equals("audio", ignoreCase = true)
    val bitmap by rememberMediaBitmap(media)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = when {
                    isVideo -> "Observation video"
                    isAudio -> "Observation audio"
                    else -> "Observation photo"
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (isVideo || isAudio) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.Videocam,
                    contentDescription = if (isAudio) "Observation audio" else "Observation video",
                )
                Text(
                    text = if (isAudio) "Audio" else "Video",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
        }
        if (isVideo && bitmap != null) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun rememberMediaBitmap(media: ObservationPhotoEntity): State<ImageBitmap?> {
    val context = LocalContext.current
    val bitmapState = remember(media.uri, media.mediaType) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(media.uri, media.mediaType) {
        bitmapState.value = withContext(Dispatchers.IO) {
            runCatching {
                when (media.mediaType.lowercase()) {
                    "video" -> context.decodeVideoThumbnail(Uri.parse(media.uri))
                    "audio" -> null
                    else -> context.decodeImageThumbnail(Uri.parse(media.uri))
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmapState
}

private fun Context.decodeImageThumbnail(uri: Uri, targetSize: Int = 512): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > targetSize || bounds.outHeight / sampleSize > targetSize) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun Context.decodeVideoThumbnail(uri: Uri, targetSize: Int = 512): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(this, uri)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val largestSide = maxOf(frame.width, frame.height)
        if (largestSide <= targetSize) {
            frame
        } else {
            val scale = targetSize / largestSide.toFloat()
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { frame.recycle() }
        }
    } finally {
        retriever.release()
    }
}

@Composable
private fun EmptyHistory() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No sessions yet. Start one from Observe.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmDeleteSessionDialog(
    session: SessionSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete session?") },
        text = {
            Text(
                text = "${UiFormatters.dayTime(session.startedAt)} and all observations in that session will be removed.",
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ConfirmDeleteObservationDialog(
    observation: RecentObservation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete observation?") },
        text = {
            Text(
                text = "${observation.summary()} at ${UiFormatters.time(observation.observedAt)} will be removed from history.",
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
