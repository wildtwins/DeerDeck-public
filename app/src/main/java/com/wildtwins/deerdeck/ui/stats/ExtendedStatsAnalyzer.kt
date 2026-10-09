package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.environment.EnvironmentCaptureContract
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SpeciesCount
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

data class FieldEffortStats(
    val totalMinutes: Long = 0,
    val averageMinutes: Double? = null,
    val longestMinutes: Long = 0,
    val sightingsPerHour: Double? = null,
    val sightingsPerSession: Double? = null,
)

data class ObservationRhythmStats(
    val activeDays: Int = 0,
    val recentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val peakPeriod: String? = null,
    val peakPeriodSightings: Int = 0,
)

data class DatasetQualityStats(
    val completeConditionSessions: Int = 0,
    val eligibleConditionSessions: Int = 0,
    val averageConditionCompletenessPercent: Int? = null,
    val gpsCoveragePercent: Int? = null,
    val weatherCoveragePercent: Int? = null,
    val mediaSessionPercent: Int? = null,
)

data class WildlifeDiversityStats(
    val recordedWildlife: Int = 0,
    val effectiveDiversity: Double? = null,
    val evennessPercent: Int? = null,
    val topWildlifeSharePercent: Int? = null,
    val oneTimeRecords: Int = 0,
)

data class ExtendedStatsSummary(
    val effort: FieldEffortStats = FieldEffortStats(),
    val rhythm: ObservationRhythmStats = ObservationRhythmStats(),
    val quality: DatasetQualityStats = DatasetQualityStats(),
    val diversity: WildlifeDiversityStats = WildlifeDiversityStats(),
)

object ExtendedStatsAnalyzer {
    fun analyze(
        sessions: List<SessionSummary>,
        wildlifeCounts: List<SpeciesCount>,
        zoneId: ZoneId,
    ): ExtendedStatsSummary {
        return ExtendedStatsSummary(
            effort = effort(sessions),
            rhythm = rhythm(sessions, zoneId),
            quality = quality(sessions),
            diversity = diversity(wildlifeCounts),
        )
    }

    private fun effort(sessions: List<SessionSummary>): FieldEffortStats {
        val completed = sessions.filter { session ->
            session.endedAt?.let { it > session.startedAt } == true
        }
        val durations = completed.map { it.endedAt!! - it.startedAt }
        val totalMillis = durations.sum()
        val totalHours = totalMillis / 3_600_000.0
        return FieldEffortStats(
            totalMinutes = totalMillis / 60_000,
            averageMinutes = durations.takeIf { it.isNotEmpty() }?.average()?.div(60_000.0),
            longestMinutes = (durations.maxOrNull() ?: 0) / 60_000,
            sightingsPerHour = if (totalHours > 0.0) {
                completed.sumOf { it.totalObservations } / totalHours
            } else {
                null
            },
            sightingsPerSession = completed.takeIf { it.isNotEmpty() }
                ?.let { rows -> rows.sumOf { it.totalObservations }.toDouble() / rows.size },
        )
    }

    private fun rhythm(sessions: List<SessionSummary>, zoneId: ZoneId): ObservationRhythmStats {
        val dates = sessions
            .map { session -> session.startedAt.toLocalDate(zoneId) }
            .distinct()
            .sorted()
        val recentStreak = dates.consecutiveStreakEndingAtLatest()
        val longestStreak = dates.longestConsecutiveStreak()
        val peak = sessions
            .groupBy { session -> session.startedAt.timePeriod(zoneId) }
            .mapValues { (_, rows) -> rows.sumOf { it.totalObservations } }
            .maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key },
            )
            ?.takeIf { it.value > 0 }
        return ObservationRhythmStats(
            activeDays = dates.size,
            recentStreakDays = recentStreak,
            longestStreakDays = longestStreak,
            peakPeriod = peak?.key,
            peakPeriodSightings = peak?.value ?: 0,
        )
    }

    private fun quality(sessions: List<SessionSummary>): DatasetQualityStats {
        if (sessions.isEmpty()) return DatasetQualityStats()
        val eligible = sessions.filterNot {
            EnvironmentCaptureContract.isDisabled(it.environmentStatus)
        }
        val completeness = eligible.map(EnvironmentCaptureContract::evaluate)
        return DatasetQualityStats(
            completeConditionSessions = completeness.count { it.isComplete },
            eligibleConditionSessions = eligible.size,
            averageConditionCompletenessPercent = completeness
                .takeIf { it.isNotEmpty() }
                ?.map { it.capturedFields * 100.0 / it.totalFields }
                ?.average()
                ?.roundToInt(),
            gpsCoveragePercent = sessions.percentWith {
                it.latitude != null && it.longitude != null
            },
            weatherCoveragePercent = sessions.percentWith {
                !it.weatherSummary.isNullOrBlank() && it.temperatureF != null
            },
            mediaSessionPercent = sessions.percentWith { it.photoCount > 0 },
        )
    }

    private fun diversity(wildlifeCounts: List<SpeciesCount>): WildlifeDiversityStats {
        val counts = wildlifeCounts.filter { it.total > 0 }
        val total = counts.sumOf { it.total }
        if (counts.isEmpty() || total <= 0) return WildlifeDiversityStats()
        val shannon = -counts.sumOf { count ->
            val share = count.total.toDouble() / total
            share * ln(share)
        }
        val effectiveDiversity = exp(shannon)
        val evenness = if (counts.size == 1) {
            100
        } else {
            (shannon / ln(counts.size.toDouble()) * 100.0).roundToInt()
        }
        return WildlifeDiversityStats(
            recordedWildlife = counts.size,
            effectiveDiversity = effectiveDiversity,
            evennessPercent = evenness,
            topWildlifeSharePercent = (counts.maxOf { it.total } * 100.0 / total).roundToInt(),
            oneTimeRecords = counts.count { it.total == 1 },
        )
    }

    private fun List<LocalDate>.consecutiveStreakEndingAtLatest(): Int {
        if (isEmpty()) return 0
        var streak = 1
        for (index in lastIndex downTo 1) {
            if (this[index - 1] == this[index].minusDays(1)) {
                streak += 1
            } else {
                break
            }
        }
        return streak
    }

    private fun List<LocalDate>.longestConsecutiveStreak(): Int {
        if (isEmpty()) return 0
        var longest = 1
        var current = 1
        for (index in 1..lastIndex) {
            if (this[index] == this[index - 1].plusDays(1)) {
                current += 1
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
        return java.time.Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun Long.timePeriod(zoneId: ZoneId): String {
        return when (java.time.Instant.ofEpochMilli(this).atZone(zoneId).hour) {
            in 4..7 -> "Dawn"
            in 8..16 -> "Day"
            in 17..20 -> "Dusk"
            else -> "Night"
        }
    }

    private fun List<SessionSummary>.percentWith(predicate: (SessionSummary) -> Boolean): Int {
        return (count(predicate) * 100.0 / size).roundToInt()
    }
}
