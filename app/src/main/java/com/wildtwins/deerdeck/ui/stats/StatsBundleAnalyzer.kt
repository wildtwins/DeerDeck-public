package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.environment.EnvironmentCaptureContract
import com.wildtwins.deerdeck.data.environment.ManualEnvironmentRetryEligibility
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.WildlifeObservationFact
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class NamedCount(
    val label: String,
    val count: Int,
)

data class WildlifeInsight(
    val speciesId: Long,
    val name: String,
    val color: Long,
    val total: Int,
    val sessionsSeen: Int,
    val activeDays: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val sightingsPerFieldHour: Double?,
    val linkedMedia: Int,
    val timePeriods: List<NamedCount>,
    val monthlyTotals: List<NamedCount>,
    val locations: List<NamedCount>,
    val averageTemperatureF: Double?,
    val averagePressureInHg: Double?,
    val averageMoonIlluminationPercent: Double?,
    val conditionSamples: Int,
)

enum class ComparisonPreset(val label: String) {
    SevenDays("7 days"),
    ThirtyDays("30 days"),
    NinetyDays("90 days"),
    YearToDate("Year to date"),
}

data class PeriodSnapshot(
    val sessions: Int = 0,
    val sightings: Int = 0,
    val activeDays: Int = 0,
    val wildlifeTypes: Int = 0,
    val effortMinutes: Long = 0,
    val media: Int = 0,
) {
    val sightingsPerHour: Double?
        get() = if (effortMinutes > 0) sightings / (effortMinutes / 60.0) else null
}

data class PeriodComparison(
    val preset: ComparisonPreset,
    val currentLabel: String,
    val previousLabel: String,
    val yearAgoLabel: String,
    val current: PeriodSnapshot,
    val previous: PeriodSnapshot,
    val yearAgo: PeriodSnapshot,
)

data class ObservationCalendarDay(
    val date: LocalDate,
    val sessions: Int,
    val sightings: Int,
    val wildlifeTypes: Int,
    val effortMinutes: Long,
    val media: Int,
    val zeroSightingSessions: Int,
)

data class SessionHealthIssue(
    val sessionId: Long,
    val startedAt: Long,
    val capturedFields: Int,
    val totalFields: Int,
    val missingFields: List<String>,
    val attemptCount: Int,
    val retryEligibility: ManualEnvironmentRetryEligibility,
) {
    val retryable: Boolean
        get() = retryEligibility == ManualEnvironmentRetryEligibility.Eligible
}

data class DataHealthSummary(
    val totalSessions: Int = 0,
    val eligibleSessions: Int = 0,
    val completeSessions: Int = 0,
    val disabledSessions: Int = 0,
    val openSessions: Int = 0,
    val provenanceSessions: Int = 0,
    val retryableSessions: Int = 0,
    val retryLimitSessions: Int = 0,
    val missingLocationSessions: Int = 0,
    val commonMissingFields: List<NamedCount> = emptyList(),
    val issues: List<SessionHealthIssue> = emptyList(),
) {
    val completenessPercent: Int?
        get() = eligibleSessions.takeIf { it > 0 }
            ?.let { (completeSessions * 100.0 / it).roundToInt() }
}

data class StatsBundleSummary(
    val wildlifeInsights: List<WildlifeInsight> = emptyList(),
    val periodComparisons: List<PeriodComparison> = emptyList(),
    val calendarDays: Map<LocalDate, ObservationCalendarDay> = emptyMap(),
    val dataHealth: DataHealthSummary = DataHealthSummary(),
    val fieldIntelligence: FieldIntelligenceSummary = FieldIntelligenceSummary(),
)

object StatsBundleAnalyzer {
    fun analyze(
        sessions: List<SessionSummary>,
        observations: List<WildlifeObservationFact>,
        media: List<ObservationPhotoEntity>,
        zoneId: ZoneId,
        nowAt: Long = System.currentTimeMillis(),
        today: LocalDate = Instant.ofEpochMilli(nowAt).atZone(zoneId).toLocalDate(),
    ): StatsBundleSummary {
        return StatsBundleSummary(
            wildlifeInsights = wildlifeInsights(sessions, observations, media, zoneId, today),
            periodComparisons = ComparisonPreset.entries.map { preset ->
                periodComparison(preset, sessions, observations, zoneId, today)
            },
            calendarDays = calendarDays(sessions, observations, media, zoneId),
            dataHealth = dataHealth(sessions),
            fieldIntelligence = FieldIntelligenceAnalyzer.analyze(
                sessions = sessions,
                observations = observations,
                zoneId = zoneId,
                nowAt = nowAt,
            ),
        )
    }

    private fun wildlifeInsights(
        sessions: List<SessionSummary>,
        observations: List<WildlifeObservationFact>,
        media: List<ObservationPhotoEntity>,
        zoneId: ZoneId,
        today: LocalDate,
    ): List<WildlifeInsight> {
        val sessionsById = sessions.associateBy { it.id }
        val totalFieldHours = sessions
            .mapNotNull { session -> session.durationMillis() }
            .sum() / 3_600_000.0
        val mediaByObservation = media
            .mapNotNull { item -> item.observationId }
            .groupingBy { it }
            .eachCount()
        val currentMonth = YearMonth.from(today)
        val firstMonth = currentMonth.minusMonths(11)
        val monthFormatter = DateTimeFormatter.ofPattern("MMM yy")

        return observations
            .groupBy { it.wildlifeName.trim().lowercase() }
            .values
            .mapNotNull { rows ->
                val first = rows.firstOrNull() ?: return@mapNotNull null
                val total = rows.sumOf { it.quantity }
                val sessionIds = rows.map { it.sessionId }.distinct()
                val conditionSessions = sessionIds.mapNotNull(sessionsById::get)
                val monthCounts = rows
                    .groupingBy { YearMonth.from(it.observedAt.toLocalDate(zoneId)) }
                    .fold(0) { totalForMonth, row -> totalForMonth + row.quantity }
                val locationCounts = rows
                    .mapNotNull { row ->
                        val location = sessionsById[row.sessionId]
                            ?.fieldLocationName
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                        location?.let { it to row.quantity }
                    }
                    .groupBy({ it.first.lowercase() }, { it })
                    .values
                    .map { items -> NamedCount(items.first().first, items.sumOf { it.second }) }
                    .sortedWith(compareByDescending<NamedCount> { it.count }.thenBy { it.label })
                val periodOrder = listOf("Dawn", "Day", "Dusk", "Night")
                val periodCounts = rows
                    .groupingBy { it.observedAt.timePeriod(zoneId) }
                    .fold(0) { periodTotal, row -> periodTotal + row.quantity }

                WildlifeInsight(
                    speciesId = first.speciesId,
                    name = first.wildlifeName,
                    color = first.color,
                    total = total,
                    sessionsSeen = sessionIds.size,
                    activeDays = rows.map { it.observedAt.toLocalDate(zoneId) }.distinct().size,
                    firstSeenAt = rows.minOf { it.observedAt },
                    lastSeenAt = rows.maxOf { it.observedAt },
                    sightingsPerFieldHour = totalFieldHours.takeIf { it > 0.0 }
                        ?.let { total / it },
                    linkedMedia = rows.sumOf { mediaByObservation[it.observationId] ?: 0 },
                    timePeriods = periodOrder.map { NamedCount(it, periodCounts[it] ?: 0) },
                    monthlyTotals = (0L..11L).map { offset ->
                        val month = firstMonth.plusMonths(offset)
                        NamedCount(month.format(monthFormatter), monthCounts[month] ?: 0)
                    },
                    locations = locationCounts.take(8),
                    averageTemperatureF = conditionSessions.mapNotNull { it.temperatureF }.averageOrNull(),
                    averagePressureInHg = conditionSessions.mapNotNull { it.pressureInHg }.averageOrNull(),
                    averageMoonIlluminationPercent = conditionSessions
                        .mapNotNull { it.moonIlluminationPercent }
                        .averageOrNull(),
                    conditionSamples = conditionSessions.count { session ->
                        session.temperatureF != null ||
                            session.pressureInHg != null ||
                            session.moonIlluminationPercent != null
                    },
                )
            }
            .sortedWith(compareByDescending<WildlifeInsight> { it.total }.thenBy { it.name })
    }

    private fun periodComparison(
        preset: ComparisonPreset,
        sessions: List<SessionSummary>,
        observations: List<WildlifeObservationFact>,
        zoneId: ZoneId,
        today: LocalDate,
    ): PeriodComparison {
        val current = when (preset) {
            ComparisonPreset.SevenDays -> DateWindow.endingToday(today, 7)
            ComparisonPreset.ThirtyDays -> DateWindow.endingToday(today, 30)
            ComparisonPreset.NinetyDays -> DateWindow.endingToday(today, 90)
            ComparisonPreset.YearToDate -> DateWindow(today.withDayOfYear(1), today.plusDays(1))
        }
        val previous = if (preset == ComparisonPreset.YearToDate) {
            DateWindow(today.minusYears(1).withDayOfYear(1), today.minusYears(1).plusDays(1))
        } else {
            DateWindow(
                start = current.start.minusDays(current.lengthDays),
                endExclusive = current.start,
            )
        }
        val yearAgo = DateWindow(current.start.minusYears(1), current.endExclusive.minusYears(1))
        return PeriodComparison(
            preset = preset,
            currentLabel = current.label(),
            previousLabel = previous.label(),
            yearAgoLabel = yearAgo.label(),
            current = snapshot(current, sessions, observations, zoneId),
            previous = snapshot(previous, sessions, observations, zoneId),
            yearAgo = snapshot(yearAgo, sessions, observations, zoneId),
        )
    }

    private fun snapshot(
        window: DateWindow,
        sessions: List<SessionSummary>,
        observations: List<WildlifeObservationFact>,
        zoneId: ZoneId,
    ): PeriodSnapshot {
        val periodSessions = sessions.filter { window.contains(it.startedAt.toLocalDate(zoneId)) }
        val periodObservations = observations.filter { window.contains(it.observedAt.toLocalDate(zoneId)) }
        val activeDays = (periodSessions.map { it.startedAt.toLocalDate(zoneId) } +
            periodObservations.map { it.observedAt.toLocalDate(zoneId) })
            .distinct()
            .size
        return PeriodSnapshot(
            sessions = periodSessions.size,
            sightings = periodObservations.sumOf { it.quantity },
            activeDays = activeDays,
            wildlifeTypes = periodObservations.map { it.wildlifeName.trim().lowercase() }.distinct().size,
            effortMinutes = periodSessions.mapNotNull { it.durationMillis() }.sum() / 60_000,
            media = periodSessions.sumOf { it.photoCount },
        )
    }

    private fun calendarDays(
        sessions: List<SessionSummary>,
        observations: List<WildlifeObservationFact>,
        media: List<ObservationPhotoEntity>,
        zoneId: ZoneId,
    ): Map<LocalDate, ObservationCalendarDay> {
        val sessionsByDate = sessions.groupBy { it.startedAt.toLocalDate(zoneId) }
        val observationsByDate = observations.groupBy { it.observedAt.toLocalDate(zoneId) }
        val sessionDates = sessions.associate { it.id to it.startedAt.toLocalDate(zoneId) }
        val mediaByDate = media.mapNotNull { item -> sessionDates[item.sessionId] }.groupingBy { it }.eachCount()
        val allDates = sessionsByDate.keys + observationsByDate.keys + mediaByDate.keys
        return allDates.associateWith { date ->
            val daySessions = sessionsByDate[date].orEmpty()
            val dayObservations = observationsByDate[date].orEmpty()
            ObservationCalendarDay(
                date = date,
                sessions = daySessions.size,
                sightings = dayObservations.sumOf { it.quantity },
                wildlifeTypes = dayObservations.map { it.wildlifeName.trim().lowercase() }.distinct().size,
                effortMinutes = daySessions.mapNotNull { it.durationMillis() }.sum() / 60_000,
                media = mediaByDate[date] ?: 0,
                zeroSightingSessions = daySessions.count { it.totalObservations == 0 },
            )
        }
    }

    private fun dataHealth(sessions: List<SessionSummary>): DataHealthSummary {
        val completed = sessions.filter { it.endedAt != null }
        val disabled = completed.filter { EnvironmentCaptureContract.isDisabled(it.environmentStatus) }
        val eligible = completed - disabled.toSet()
        val evaluated = eligible.map { it to EnvironmentCaptureContract.evaluate(it) }
        val issues = evaluated
            .filterNot { (_, completeness) -> completeness.isComplete }
            .map { (session, completeness) ->
                SessionHealthIssue(
                    sessionId = session.id,
                    startedAt = session.startedAt,
                    capturedFields = completeness.capturedFields,
                    totalFields = completeness.totalFields,
                    missingFields = completeness.missingFields,
                    attemptCount = session.environmentAttemptCount ?: 0,
                    retryEligibility = EnvironmentCaptureContract.manualRetryEligibility(session),
                )
            }
            .sortedByDescending { it.startedAt }
        val missingFields = issues
            .flatMap { it.missingFields }
            .groupingBy { it }
            .eachCount()
            .map { (field, count) -> NamedCount(field, count) }
            .sortedWith(compareByDescending<NamedCount> { it.count }.thenBy { it.label })
        return DataHealthSummary(
            totalSessions = sessions.size,
            eligibleSessions = eligible.size,
            completeSessions = evaluated.count { (_, completeness) -> completeness.isComplete },
            disabledSessions = disabled.size,
            openSessions = sessions.count { it.endedAt == null },
            provenanceSessions = completed.count { session ->
                !session.weatherSource.isNullOrBlank() || !session.elevationSource.isNullOrBlank()
            },
            retryableSessions = issues.count { it.retryable },
            retryLimitSessions = issues.count {
                it.retryEligibility == ManualEnvironmentRetryEligibility.AttemptLimitReached
            },
            missingLocationSessions = issues.count { "GPS coordinates" in it.missingFields },
            commonMissingFields = missingFields.take(6),
            issues = issues,
        )
    }

    private data class DateWindow(
        val start: LocalDate,
        val endExclusive: LocalDate,
    ) {
        val lengthDays: Long = java.time.temporal.ChronoUnit.DAYS.between(start, endExclusive)

        fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

        fun label(): String {
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            val end = endExclusive.minusDays(1)
            return if (start.year == end.year) {
                "${start.format(formatter)}-${end.format(formatter)}, ${end.year}"
            } else {
                "${start.format(formatter)}, ${start.year}-${end.format(formatter)}, ${end.year}"
            }
        }

        companion object {
            fun endingToday(today: LocalDate, days: Long): DateWindow {
                return DateWindow(today.minusDays(days - 1), today.plusDays(1))
            }
        }
    }

    private fun SessionSummary.durationMillis(): Long? {
        val end = endedAt ?: return null
        return (end - startedAt).takeIf { it > 0 }
    }

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun Long.timePeriod(zoneId: ZoneId): String {
        return when (Instant.ofEpochMilli(this).atZone(zoneId).hour) {
            in 4..7 -> "Dawn"
            in 8..16 -> "Day"
            in 17..20 -> "Dusk"
            else -> "Night"
        }
    }

    private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
}
