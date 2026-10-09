package com.wildtwins.deerdeck.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.environment.WeatherBackfillScheduler
import com.wildtwins.deerdeck.data.model.DailySpeciesCount
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.data.model.WildlifeObservationFact
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TrendDayCount = 30
private const val DayMillis = 86_400_000L

data class TrendDay(
    val label: String,
    val dayStartAt: Long,
)

data class SpeciesTrend(
    val speciesId: Long,
    val speciesName: String,
    val color: Long,
    val totals: List<Int>,
) {
    val total: Int = totals.sum()
}

data class FieldLocationStats(
    val name: String,
    val sessions: Int,
    val sightings: Int,
    val media: Int,
)

data class YearAgoStatsContext(
    val dateLabel: String = "",
    val sessions: List<SessionSummary> = emptyList(),
    val counts: List<SpeciesCount> = emptyList(),
) {
    val totalSightings: Int = sessions.sumOf { it.totalObservations }
    val topSpecies: SpeciesCount? = counts.firstOrNull()
    val firstSession: SessionSummary? = sessions.firstOrNull()
}

data class StatsUiState(
    val activeSession: ObservationSessionEntity? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val speciesCounts: List<SpeciesCount> = emptyList(),
    val trendDays: List<TrendDay> = emptyList(),
    val speciesTrends: List<SpeciesTrend> = emptyList(),
    val yearAgoContext: YearAgoStatsContext = YearAgoStatsContext(),
    val similarDays: SimilarDaysContext = SimilarDaysContext(),
    val fieldLocationStats: List<FieldLocationStats> = emptyList(),
    val bestConditions: BestConditionsSummary = BestConditionsSummary(),
    val personalActivityScore: PersonalActivityScore = PersonalActivityScore(),
    val extendedStats: ExtendedStatsSummary = ExtendedStatsSummary(),
    val statsBundle: StatsBundleSummary = StatsBundleSummary(),
    val conditionRetry: ConditionRetryUiState = ConditionRetryUiState(),
) {
    val totalSightings: Int = sessions.sumOf { it.totalObservations }
    val totalMedia: Int = sessions.sumOf { it.photoCount }
    val completedSessions: Int = sessions.count { it.endedAt != null }
    val topSpecies: SpeciesCount? = speciesCounts.firstOrNull()
}

data class ConditionRetryUiState(
    val inProgress: Boolean = false,
    val total: Int = 0,
    val processed: Int = 0,
    val improved: Int = 0,
    val madeComplete: Int = 0,
    val stillIncomplete: Int = 0,
    val failures: Int = 0,
    val message: String? = null,
)

class StatsViewModel(
    private val repository: DeerDeckRepository,
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val trendRange = DateRange.lastThirtyDays(zoneId)
    private val yearAgoRange = DateRange.oneYearAgoToday(zoneId)
    private val conditionRetryState = MutableStateFlow(ConditionRetryUiState())
    private val historicalStats = combine(
        repository.sessionSummaries,
        repository.overallSpeciesCounts,
        repository.sessionWildlifeCounts,
        repository.wildlifeObservationFacts,
        repository.allMedia,
    ) { sessions, speciesCounts, sessionWildlifeCounts, observations, media ->
        HistoricalStats(sessions, speciesCounts, sessionWildlifeCounts, observations, media)
    }

    private val baseUiState = combine(
        repository.activeSession,
        historicalStats,
        repository.dailySpeciesCountsBetween(trendRange.startAt, trendRange.endAt, DayMillis),
        combine(
            repository.sessionSummariesBetween(yearAgoRange.startAt, yearAgoRange.endAt),
            repository.speciesCountsBetween(yearAgoRange.startAt, yearAgoRange.endAt),
        ) { yearAgoSessions, yearAgoCounts ->
            YearAgoStatsContext(
                dateLabel = yearAgoRange.label,
                sessions = yearAgoSessions,
                counts = yearAgoCounts,
            )
        },
    ) { activeSession, historical, dailyCounts, yearAgoContext ->
        val targetSession = activeSession
            ?.let { active -> historical.sessions.firstOrNull { it.id == active.id } }
            ?: historical.sessions.firstOrNull()
        StatsUiState(
            activeSession = activeSession,
            sessions = historical.sessions,
            speciesCounts = historical.speciesCounts,
            trendDays = trendRange.days,
            speciesTrends = dailyCounts.toTopFiveTrends(),
            yearAgoContext = yearAgoContext,
            similarDays = SimilarDayMatcher.match(
                target = targetSession,
                sessions = historical.sessions,
                wildlifeCounts = historical.sessionWildlifeCounts,
                zoneId = zoneId,
            ),
            fieldLocationStats = historical.sessions.toFieldLocationStats(),
            bestConditions = BestConditionsAnalyzer.analyze(
                sessions = historical.sessions,
                zoneId = zoneId,
            ),
            personalActivityScore = PersonalActivityScoreAnalyzer.analyze(
                target = activeSession?.let { active -> historical.sessions.firstOrNull { it.id == active.id } },
                sessions = historical.sessions,
                zoneId = zoneId,
            ),
            extendedStats = ExtendedStatsAnalyzer.analyze(
                sessions = historical.sessions,
                wildlifeCounts = historical.speciesCounts,
                zoneId = zoneId,
            ),
            statsBundle = StatsBundleAnalyzer.analyze(
                sessions = historical.sessions,
                observations = historical.observations,
                media = historical.media,
                zoneId = zoneId,
            ),
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<StatsUiState> = combine(
        baseUiState,
        conditionRetryState,
    ) { state, retry ->
        state.copy(conditionRetry = retry)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun retryIncompleteConditions(context: Context) {
        if (conditionRetryState.value.inProgress) return
        val retryable = uiState.value.statsBundle.dataHealth.issues.filter { it.retryable }
        if (retryable.isEmpty()) {
            conditionRetryState.value = ConditionRetryUiState(
                message = "No sessions are currently eligible for condition repair.",
            )
            return
        }

        viewModelScope.launch {
            val appContext = context.applicationContext
            var improved = 0
            var madeComplete = 0
            var stillIncomplete = 0
            var failures = 0
            conditionRetryState.value = ConditionRetryUiState(
                inProgress = true,
                total = retryable.size,
            )

            retryable.forEachIndexed { index, issue ->
                WeatherBackfillScheduler.cancel(appContext, issue.sessionId)
                try {
                    val result = repository.retrySessionEnvironment(appContext, issue.sessionId)
                    when {
                        result == null -> failures++
                        result.isComplete -> {
                            if (result.improved) improved++
                            madeComplete++
                        }
                        else -> {
                            if (result.improved) improved++
                            stillIncomplete++
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failures++
                }
                conditionRetryState.value = ConditionRetryUiState(
                    inProgress = true,
                    total = retryable.size,
                    processed = index + 1,
                    improved = improved,
                    madeComplete = madeComplete,
                    stillIncomplete = stillIncomplete,
                    failures = failures,
                )
            }

            val message = buildRetryMessage(
                improved = improved,
                madeComplete = madeComplete,
                stillIncomplete = stillIncomplete,
                failures = failures,
            )
            conditionRetryState.value = ConditionRetryUiState(
                total = retryable.size,
                processed = retryable.size,
                improved = improved,
                madeComplete = madeComplete,
                stillIncomplete = stillIncomplete,
                failures = failures,
                message = message,
            )
        }
    }

    private fun buildRetryMessage(
        improved: Int,
        madeComplete: Int,
        stillIncomplete: Int,
        failures: Int,
    ): String {
        if (improved == 0 && failures == 0) {
            return "Retry finished. No additional historical condition values were available."
        }
        val parts = buildList {
            if (improved > 0) add("updated $improved")
            if (madeComplete > 0) add("completed $madeComplete")
            if (stillIncomplete > 0) add("$stillIncomplete still incomplete")
            if (failures > 0) add("$failures failed")
        }
        return "Retry finished: ${parts.joinToString()}."
    }

    private fun List<DailySpeciesCount>.toTopFiveTrends(): List<SpeciesTrend> {
        val bySpecies = groupBy { it.speciesId }
        val topSpecies = bySpecies.values
            .mapNotNull { rows ->
                val first = rows.firstOrNull() ?: return@mapNotNull null
                val total = rows.sumOf { it.total }
                if (total <= 0) return@mapNotNull null
                first to total
            }
            .sortedWith(
                compareByDescending<Pair<DailySpeciesCount, Int>> { it.second }
                    .thenBy { it.first.speciesName },
            )
            .take(5)

        return topSpecies.map { (species, _) ->
            val totalsByDay = bySpecies[species.speciesId]
                .orEmpty()
                .associate { it.dayIndex to it.total }
            SpeciesTrend(
                speciesId = species.speciesId,
                speciesName = species.speciesName,
                color = species.color,
                totals = List(TrendDayCount) { day -> totalsByDay[day] ?: 0 },
            )
        }
    }

    private data class DateRange(
        val startAt: Long,
        val endAt: Long,
        val label: String,
        val days: List<TrendDay> = emptyList(),
    ) {
        companion object {
            fun lastThirtyDays(zoneId: ZoneId): DateRange {
                val today = LocalDate.now(zoneId)
                val startDate = today.minusDays((TrendDayCount - 1).toLong())
                val formatter = DateTimeFormatter.ofPattern("M/d")
                val days = (0 until TrendDayCount).map { offset ->
                    val date = startDate.plusDays(offset.toLong())
                    TrendDay(
                        label = date.format(formatter),
                        dayStartAt = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    )
                }
                return DateRange(
                    startAt = days.first().dayStartAt,
                    endAt = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    label = "Last 30 days",
                    days = days,
                )
            }

            fun oneYearAgoToday(zoneId: ZoneId): DateRange {
                val date = LocalDate.now(zoneId).minusYears(1)
                return DateRange(
                    startAt = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    endAt = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    label = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                )
            }
        }
    }

    private data class HistoricalStats(
        val sessions: List<SessionSummary>,
        val speciesCounts: List<SpeciesCount>,
        val sessionWildlifeCounts: List<com.wildtwins.deerdeck.data.model.SessionWildlifeCount>,
        val observations: List<WildlifeObservationFact>,
        val media: List<ObservationPhotoEntity>,
    )
}

internal fun List<SessionSummary>.toFieldLocationStats(): List<FieldLocationStats> {
    return filter { !it.fieldLocationName.isNullOrBlank() }
        .groupBy { it.fieldLocationName!!.trim().lowercase() }
        .values
        .map { sessions ->
            FieldLocationStats(
                name = sessions.first().fieldLocationName!!.trim(),
                sessions = sessions.size,
                sightings = sessions.sumOf { it.totalObservations },
                media = sessions.sumOf { it.photoCount },
            )
        }
        .sortedWith(
            compareByDescending<FieldLocationStats> { it.sightings }
                .thenByDescending { it.sessions }
                .thenBy { it.name },
        )
}
