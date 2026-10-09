package com.wildtwins.deerdeck.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.export.CsvExport
import com.wildtwins.deerdeck.data.export.ExportPrivacyOptions
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.RecentObservation
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.data.model.SessionWildlifeCount
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.AppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSessionId: Long? = null,
    val selectedCounts: List<SpeciesCount> = emptyList(),
    val selectedPhotos: List<ObservationPhotoEntity> = emptyList(),
    val selectedObservations: List<RecentObservation> = emptyList(),
    val allMedia: List<HistoryMediaItem> = emptyList(),
    val sessionWildlifeCounts: List<SessionWildlifeCount> = emptyList(),
)

data class HistoryMediaItem(
    val media: ObservationPhotoEntity,
    val session: SessionSummary,
)

private data class HistoryBaseState(
    val sessions: List<SessionSummary>,
    val selectedSessionId: Long?,
    val allMedia: List<HistoryMediaItem>,
    val sessionWildlifeCounts: List<SessionWildlifeCount>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: DeerDeckRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {
    private val selectedSessionId = MutableStateFlow<Long?>(null)

    private val baseState = combine(
        repository.sessionSummaries,
        selectedSessionId,
        repository.allMedia,
        repository.sessionWildlifeCounts,
    ) { sessions, selectedId, media, wildlifeCounts ->
        HistoryBaseState(
            sessions = sessions,
            selectedSessionId = selectedId,
            allMedia = buildHistoryMediaItems(sessions, media),
            sessionWildlifeCounts = wildlifeCounts,
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        baseState,
        combine(repository.sessionSummaries, selectedSessionId) { sessions, selectedId ->
            val fallbackId = selectedId ?: sessions.firstOrNull()?.id
            sessions.firstOrNull { it.id == fallbackId }
        }.flatMapLatest { session ->
            session?.let {
                repository.countsForSession(
                    sessionId = it.id,
                    profile = ObservationProfile.fromStorage(it.profileKey),
                )
            } ?: flowOf(emptyList())
        },
        selectedSessionId.flatMapLatest { id ->
            id?.let { repository.photosForSession(it) } ?: flowOf(emptyList())
        },
        selectedSessionId.flatMapLatest { id ->
            id?.let { repository.observationsForSession(it) } ?: flowOf(emptyList())
        },
    ) { base, counts, photos, observations ->
        val sessions = base.sessions
        val selectedId = base.selectedSessionId
        val fallbackId = selectedId ?: sessions.firstOrNull()?.id
        val showFallback = fallbackId != null && (fallbackId == selectedId || selectedId == null)
        HistoryUiState(
            sessions = sessions,
            selectedSessionId = fallbackId,
            selectedCounts = if (showFallback) counts else emptyList(),
            selectedPhotos = if (showFallback) photos else emptyList(),
            selectedObservations = if (showFallback) observations else emptyList(),
            allMedia = base.allMedia,
            sessionWildlifeCounts = base.sessionWildlifeCounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun selectSession(sessionId: Long) {
        selectedSessionId.value = sessionId
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (selectedSessionId.value == sessionId) {
                selectedSessionId.value = null
            }
        }
    }

    fun deleteObservation(observationId: Long) {
        viewModelScope.launch {
            repository.deleteObservation(observationId)
        }
    }

    suspend fun exportObservationsCsv(context: Context): Uri {
        return repository.exportObservationsCsv(
            context = context.applicationContext,
            privacy = ExportPrivacyOptions(
                locationPrecision = settingsRepository.sharedLocationPrecision.value,
                includeNamedFieldLocations = settingsRepository.includeNamedFieldLocationsInShares.value,
            ),
        )
    }

    suspend fun buildObservationsCsv(): CsvExport {
        return repository.buildObservationsCsv()
    }
}

internal fun buildHistoryMediaItems(
    sessions: List<SessionSummary>,
    media: List<ObservationPhotoEntity>,
): List<HistoryMediaItem> {
    val sessionsById = sessions.associateBy { it.id }
    return media.mapNotNull { item ->
        sessionsById[item.sessionId]?.let { session -> HistoryMediaItem(item, session) }
    }.sortedWith(
        compareByDescending<HistoryMediaItem> { it.media.capturedAt }
            .thenByDescending { it.media.id },
    )
}
