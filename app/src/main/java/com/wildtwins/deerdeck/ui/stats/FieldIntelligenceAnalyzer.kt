package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.WildlifeObservationFact
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class FieldBriefCandidate(
    val name: String,
    val color: Long,
    val patternScore: Int,
    val matchedSightings: Int,
    val sampleSessions: Int,
)

data class SmartFieldBrief(
    val contextLabel: String = "Current time and season",
    val confidenceLabel: String = "Waiting for history",
    val matchedSessions: Int = 0,
    val candidates: List<FieldBriefCandidate> = emptyList(),
)

data class WeeklyWindowRank(
    val label: String,
    val sessions: Int,
    val sightings: Int,
    val sightingsPerSession: Double,
)

data class WildlifePairing(
    val first: String,
    val second: String,
    val sessionsTogether: Int,
    val affinityPercent: Int,
)

data class LocationScorecard(
    val name: String,
    val sessions: Int,
    val effortMinutes: Long,
    val sightings: Int,
    val wildlifeTypes: Int,
    val sightingsPerHour: Double?,
    val bestTimeBlock: String?,
    val confidenceLabel: String,
)

data class ConditionResponse(
    val label: String,
    val sessions: Int,
    val sightings: Int,
    val sightingsPerSession: Double,
)

data class RecordHighlight(
    val label: String,
    val value: String,
    val detail: String,
)

data class DiscoveryEvent(
    val name: String,
    val color: Long,
    val firstSeenAt: Long,
)

data class DiscoverySummary(
    val totalDiscovered: Int = 0,
    val newLast30Days: Int = 0,
    val newThisYear: Int = 0,
    val averageDaysBetweenDiscoveries: Double? = null,
    val recentDiscoveries: List<DiscoveryEvent> = emptyList(),
)

data class ReturnWatchItem(
    val name: String,
    val color: Long,
    val daysSinceLastSeen: Long,
    val usualGapDays: Double,
    val overdueDays: Long,
)

data class FieldIntelligenceSummary(
    val fieldBrief: SmartFieldBrief = SmartFieldBrief(),
    val bestWindows: List<WeeklyWindowRank> = emptyList(),
    val pairings: List<WildlifePairing> = emptyList(),
    val locations: List<LocationScorecard> = emptyList(),
    val weatherResponse: List<ConditionResponse> = emptyList(),
    val pressureResponse: List<ConditionResponse> = emptyList(),
    val moonResponse: List<ConditionResponse> = emptyList(),
    val records: List<RecordHighlight> = emptyList(),
    val discoveries: DiscoverySummary = DiscoverySummary(),
    val returnWatch: List<ReturnWatchItem> = emptyList(),
    val shareText: String = "",
)

object FieldIntelligenceAnalyzer {
    fun analyze(
        sessions: List<SessionSummary>,
        observations: List<WildlifeObservationFact>,
        zoneId: ZoneId,
        nowAt: Long = System.currentTimeMillis(),
    ): FieldIntelligenceSummary {
        val completed = sessions.filter { session ->
            session.endedAt?.let { it > session.startedAt } == true && session.startedAt <= nowAt
        }
        val observationsBySession = observations.groupBy { it.sessionId }
        val currentDate = nowAt.toLocalDate(zoneId)
        val brief = buildFieldBrief(completed, observationsBySession, sessions, zoneId, nowAt)
        val windows = bestWeeklyWindows(completed, zoneId)
        val pairings = wildlifePairings(observationsBySession)
        val locations = locationScorecards(completed, observationsBySession, zoneId)
        val weather = conditionResponse(completed, WeatherOrder) { weatherCategory(it.weatherSummary) }
        val pressure = conditionResponse(completed, PressureOrder) { pressureCategory(it.pressureInHg) }
        val moon = conditionResponse(completed, MoonOrder) { moonCategory(it.moonIlluminationPercent) }
        val records = personalRecords(completed, observationsBySession, zoneId)
        val discoveries = discoveryTimeline(observations, zoneId, currentDate)
        val returnWatch = returnWatch(observations, zoneId, currentDate)

        return FieldIntelligenceSummary(
            fieldBrief = brief,
            bestWindows = windows,
            pairings = pairings,
            locations = locations,
            weatherResponse = weather,
            pressureResponse = pressure,
            moonResponse = moon,
            records = records,
            discoveries = discoveries,
            returnWatch = returnWatch,
            shareText = buildShareText(
                brief = brief,
                bestWindow = windows.firstOrNull(),
                bestLocation = locations.firstOrNull(),
                returnWatch = returnWatch,
                completedSessions = completed.size,
            ),
        )
    }

    private fun buildFieldBrief(
        completed: List<SessionSummary>,
        observationsBySession: Map<Long, List<WildlifeObservationFact>>,
        allSessions: List<SessionSummary>,
        zoneId: ZoneId,
        nowAt: Long,
    ): SmartFieldBrief {
        val active = allSessions.filter { it.endedAt == null }.maxByOrNull { it.startedAt }
        val now = Instant.ofEpochMilli(nowAt).atZone(zoneId)
        val scored = completed
            .map { session -> session to similarityScore(session, active, now.month, now.hour, zoneId) }
            .filter { (session, _) -> observationsBySession[session.id].orEmpty().isNotEmpty() }
            .sortedByDescending { it.second }
        val threshold = if (active == null) 4.0 else 5.0
        val strongMatches = scored.filter { it.second >= threshold }
        val matched = (strongMatches.takeIf { it.size >= 3 } ?: scored.take(10)).take(20)
        val scoreBySession = matched.associate { (session, score) -> session.id to score }
        val matchedRows = matched.flatMap { (session, _) -> observationsBySession[session.id].orEmpty() }
        val weightedByName = matchedRows
            .groupBy { it.normalizedName() }
            .mapValues { (_, rows) ->
                rows.sumOf { row -> row.quantity * (scoreBySession[row.sessionId] ?: 1.0) }
            }
        val maxWeighted = weightedByName.values.maxOrNull() ?: 0.0
        val candidates = matchedRows
            .groupBy { it.normalizedName() }
            .mapNotNull { (_, rows) ->
                val first = rows.firstOrNull() ?: return@mapNotNull null
                val weighted = weightedByName[first.normalizedName()] ?: 0.0
                FieldBriefCandidate(
                    name = first.wildlifeName,
                    color = first.color,
                    patternScore = if (maxWeighted > 0.0) (weighted * 100.0 / maxWeighted).roundToInt() else 0,
                    matchedSightings = rows.sumOf { it.quantity },
                    sampleSessions = rows.map { it.sessionId }.distinct().size,
                )
            }
            .sortedWith(
                compareByDescending<FieldBriefCandidate> { it.patternScore }
                    .thenByDescending { it.matchedSightings }
                    .thenBy { it.name },
            )
            .take(5)
        val contextParts = mutableListOf(now.month.displayName(), now.hour.timePeriod())
        active?.fieldLocationName?.takeIf { it.isNotBlank() }?.let(contextParts::add)
        weatherCategory(active?.weatherSummary)?.let(contextParts::add)
        active?.pressureInHg?.let { contextParts += String.format(Locale.US, "%.2f inHg", it) }
        active?.moonIlluminationPercent?.let { contextParts += "${it.roundToInt()}% moon" }
        return SmartFieldBrief(
            contextLabel = contextParts.joinToString(" / "),
            confidenceLabel = sampleConfidence(matched.size),
            matchedSessions = matched.size,
            candidates = candidates,
        )
    }

    private fun similarityScore(
        historical: SessionSummary,
        active: SessionSummary?,
        currentMonth: Month,
        currentHour: Int,
        zoneId: ZoneId,
    ): Double {
        val historicalTime = Instant.ofEpochMilli(historical.startedAt).atZone(zoneId)
        var score = 1.0
        val monthDistance = circularMonthDistance(currentMonth.value, historicalTime.monthValue)
        score += when (monthDistance) {
            0 -> 2.0
            1 -> 0.75
            else -> 0.0
        }
        if (historicalTime.hour.timePeriod() == currentHour.timePeriod()) score += 2.0
        if (active != null) {
            if (
                !active.fieldLocationName.isNullOrBlank() &&
                active.fieldLocationName.equals(historical.fieldLocationName, ignoreCase = true)
            ) {
                score += 3.0
            }
            if (
                weatherCategory(active.weatherSummary) != null &&
                weatherCategory(active.weatherSummary) == weatherCategory(historical.weatherSummary)
            ) {
                score += 1.5
            }
            score += proximityScore(active.temperatureF, historical.temperatureF, 5.0, 12.0, 1.0)
            score += proximityScore(active.pressureInHg, historical.pressureInHg, 0.12, 0.3, 1.0)
            score += proximityScore(
                active.moonIlluminationPercent,
                historical.moonIlluminationPercent,
                15.0,
                30.0,
                0.75,
            )
        }
        return score
    }

    private fun bestWeeklyWindows(
        sessions: List<SessionSummary>,
        zoneId: ZoneId,
    ): List<WeeklyWindowRank> {
        return sessions
            .groupBy { session ->
                val time = Instant.ofEpochMilli(session.startedAt).atZone(zoneId)
                time.dayOfWeek to time.hour.timePeriod()
            }
            .map { (window, rows) ->
                val sightings = rows.sumOf { it.totalObservations }
                WeeklyWindowRank(
                    label = "${window.first.shortName()} ${window.second}",
                    sessions = rows.size,
                    sightings = sightings,
                    sightingsPerSession = sightings.toDouble() / rows.size,
                )
            }
            .sortedWith(
                compareByDescending<WeeklyWindowRank> { it.sightingsPerSession }
                    .thenByDescending { it.sessions }
                    .thenBy { it.label },
            )
            .take(6)
    }

    private fun wildlifePairings(
        observationsBySession: Map<Long, List<WildlifeObservationFact>>,
    ): List<WildlifePairing> {
        val namesBySession = observationsBySession.mapValues { (_, rows) ->
            rows.groupBy { it.normalizedName() }.mapValues { it.value.first().wildlifeName }
        }
        val sessionFrequency = namesBySession.values
            .flatMap { it.keys }
            .groupingBy { it }
            .eachCount()
        val displayNames = namesBySession.values.flatMap { it.entries }.associate { it.key to it.value }
        val pairs = mutableMapOf<Pair<String, String>, Int>()
        namesBySession.values.forEach { names ->
            val keys = names.keys.sorted()
            for (firstIndex in 0 until keys.lastIndex) {
                for (secondIndex in firstIndex + 1..keys.lastIndex) {
                    val pair = keys[firstIndex] to keys[secondIndex]
                    pairs[pair] = (pairs[pair] ?: 0) + 1
                }
            }
        }
        return pairs.map { (pair, together) ->
            val smallerFrequency = minOf(sessionFrequency[pair.first] ?: 1, sessionFrequency[pair.second] ?: 1)
            WildlifePairing(
                first = displayNames[pair.first] ?: pair.first,
                second = displayNames[pair.second] ?: pair.second,
                sessionsTogether = together,
                affinityPercent = (together * 100.0 / smallerFrequency).roundToInt(),
            )
        }.sortedWith(
            compareByDescending<WildlifePairing> { it.sessionsTogether }
                .thenByDescending { it.affinityPercent }
                .thenBy { it.first },
        ).take(8)
    }

    private fun locationScorecards(
        sessions: List<SessionSummary>,
        observationsBySession: Map<Long, List<WildlifeObservationFact>>,
        zoneId: ZoneId,
    ): List<LocationScorecard> {
        return sessions
            .filter { !it.fieldLocationName.isNullOrBlank() }
            .groupBy { it.fieldLocationName!!.trim().lowercase() }
            .values
            .map { rows ->
                val sessionIds = rows.map { it.id }.toSet()
                val observations = sessionIds.flatMap { observationsBySession[it].orEmpty() }
                val totalMillis = rows.sumOf { it.durationMillis() ?: 0L }
                val hours = totalMillis / 3_600_000.0
                val bestTime = rows.groupBy { it.startedAt.timePeriod(zoneId) }
                    .maxWithOrNull(
                        compareBy<Map.Entry<String, List<SessionSummary>>> { entry ->
                            entry.value.sumOf { it.totalObservations }.toDouble() / entry.value.size
                        }.thenBy { it.key },
                    )
                    ?.key
                LocationScorecard(
                    name = rows.first().fieldLocationName!!.trim(),
                    sessions = rows.size,
                    effortMinutes = totalMillis / 60_000,
                    sightings = rows.sumOf { it.totalObservations },
                    wildlifeTypes = observations.map { it.normalizedName() }.distinct().size,
                    sightingsPerHour = hours.takeIf { it > 0.0 }
                        ?.let { rows.sumOf { row -> row.totalObservations } / it },
                    bestTimeBlock = bestTime,
                    confidenceLabel = sampleConfidence(rows.size),
                )
            }
            .sortedWith(
                compareByDescending<LocationScorecard> { it.sightingsPerHour ?: 0.0 }
                    .thenByDescending { it.sessions }
                    .thenBy { it.name },
            )
            .take(8)
    }

    private fun conditionResponse(
        sessions: List<SessionSummary>,
        order: List<String>,
        category: (SessionSummary) -> String?,
    ): List<ConditionResponse> {
        return sessions
            .mapNotNull { session -> category(session)?.let { it to session } }
            .groupBy({ it.first }, { it.second })
            .map { (label, rows) ->
                val sightings = rows.sumOf { it.totalObservations }
                ConditionResponse(
                    label = label,
                    sessions = rows.size,
                    sightings = sightings,
                    sightingsPerSession = sightings.toDouble() / rows.size,
                )
            }
            .sortedBy { order.indexOf(it.label).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    }

    private fun personalRecords(
        sessions: List<SessionSummary>,
        observationsBySession: Map<Long, List<WildlifeObservationFact>>,
        zoneId: ZoneId,
    ): List<RecordHighlight> {
        if (sessions.isEmpty()) return emptyList()
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val mostSightings = sessions.maxWithOrNull(compareBy<SessionSummary> { it.totalObservations }.thenBy { it.startedAt })
        val longest = sessions.maxByOrNull { it.durationMillis() ?: 0L }
        val diverse = sessions.maxWithOrNull(
            compareBy<SessionSummary> { session ->
                observationsBySession[session.id].orEmpty().map { it.normalizedName() }.distinct().size
            }.thenBy { it.totalObservations },
        )
        val mediaRich = sessions.maxWithOrNull(compareBy<SessionSummary> { it.photoCount }.thenBy { it.startedAt })
        val bestDay = sessions
            .groupBy { it.startedAt.toLocalDate(zoneId) }
            .maxWithOrNull(compareBy<Map.Entry<LocalDate, List<SessionSummary>>> { it.value.sumOf { row -> row.totalObservations } }.thenBy { it.key })

        return listOfNotNull(
            mostSightings?.let { RecordHighlight("Most sightings", "${it.totalObservations} seen", it.dateLabel(zoneId, formatter)) },
            longest?.let { RecordHighlight("Longest watch", formatMinutes((it.durationMillis() ?: 0L) / 60_000), it.dateLabel(zoneId, formatter)) },
            diverse?.let {
                val diversity = observationsBySession[it.id].orEmpty().map { row -> row.normalizedName() }.distinct().size
                RecordHighlight("Greatest diversity", "$diversity types", it.dateLabel(zoneId, formatter))
            },
            bestDay?.let { (date, rows) ->
                RecordHighlight("Best day", "${rows.sumOf { it.totalObservations }} seen", date.format(formatter))
            },
            mediaRich?.let { RecordHighlight("Most media", "${it.photoCount} files", it.dateLabel(zoneId, formatter)) },
        )
    }

    private fun discoveryTimeline(
        observations: List<WildlifeObservationFact>,
        zoneId: ZoneId,
        currentDate: LocalDate,
    ): DiscoverySummary {
        val discoveries = observations
            .groupBy { it.normalizedName() }
            .mapNotNull { (_, rows) ->
                rows.minByOrNull { it.observedAt }?.let { first ->
                    DiscoveryEvent(first.wildlifeName, first.color, first.observedAt)
                }
            }
            .sortedBy { it.firstSeenAt }
        val discoveryDates = discoveries.map { it.firstSeenAt.toLocalDate(zoneId) }
        val gaps = discoveryDates.zipWithNext { first, second ->
            ChronoUnit.DAYS.between(first, second).toDouble()
        }
        return DiscoverySummary(
            totalDiscovered = discoveries.size,
            newLast30Days = discoveries.count {
                ChronoUnit.DAYS.between(it.firstSeenAt.toLocalDate(zoneId), currentDate) in 0..29
            },
            newThisYear = discoveries.count { it.firstSeenAt.toLocalDate(zoneId).year == currentDate.year },
            averageDaysBetweenDiscoveries = gaps.takeIf { it.isNotEmpty() }?.average(),
            recentDiscoveries = discoveries.asReversed().take(8),
        )
    }

    private fun returnWatch(
        observations: List<WildlifeObservationFact>,
        zoneId: ZoneId,
        currentDate: LocalDate,
    ): List<ReturnWatchItem> {
        return observations
            .groupBy { it.normalizedName() }
            .mapNotNull { (_, rows) ->
                val dates = rows.map { it.observedAt.toLocalDate(zoneId) }.distinct().sorted()
                if (dates.size < 2) return@mapNotNull null
                val gaps = dates.zipWithNext { first, second ->
                    ChronoUnit.DAYS.between(first, second).toDouble()
                }.filter { it > 0.0 }.sorted()
                if (gaps.isEmpty()) return@mapNotNull null
                val usualGap = gaps.median()
                val daysSince = ChronoUnit.DAYS.between(dates.last(), currentDate).coerceAtLeast(0)
                val dueAfter = maxOf(7.0, usualGap * 1.25)
                if (daysSince < dueAfter) return@mapNotNull null
                val first = rows.first()
                ReturnWatchItem(
                    name = first.wildlifeName,
                    color = first.color,
                    daysSinceLastSeen = daysSince,
                    usualGapDays = usualGap,
                    overdueDays = (daysSince - usualGap.roundToInt()).coerceAtLeast(0),
                )
            }
            .sortedWith(
                compareByDescending<ReturnWatchItem> { it.overdueDays }
                    .thenByDescending { it.daysSinceLastSeen }
                    .thenBy { it.name },
            )
            .take(8)
    }

    private fun buildShareText(
        brief: SmartFieldBrief,
        bestWindow: WeeklyWindowRank?,
        bestLocation: LocationScorecard?,
        returnWatch: List<ReturnWatchItem>,
        completedSessions: Int,
    ): String {
        val lines = mutableListOf(
            "Withrow DeerDeck Field Brief",
            brief.contextLabel,
            "Pattern confidence: ${brief.confidenceLabel} (${brief.matchedSessions} matched sessions)",
        )
        brief.candidates.forEachIndexed { index, candidate ->
            lines += "${index + 1}. ${candidate.name}: ${candidate.patternScore} pattern score"
        }
        bestWindow?.let { lines += "Best weekly window: ${it.label} (${it.sightingsPerSession.oneDecimal()} seen/session)" }
        bestLocation?.let { lines += "Top field location: ${it.name} (${it.sightingsPerHour.oneDecimalOrDash()} seen/hour)" }
        if (returnWatch.isNotEmpty()) {
            lines += "Return watch: ${returnWatch.take(3).joinToString { it.name }}"
        }
        lines += "Built locally from $completedSessions completed sessions. Pattern guidance is not a guarantee."
        return lines.joinToString("\n")
    }

    private fun weatherCategory(summary: String?): String? {
        val value = summary?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            listOf("snow", "sleet", "ice", "freezing").any(value::contains) -> "Snow / ice"
            listOf("rain", "drizzle", "shower", "storm", "thunder").any(value::contains) -> "Rain / storm"
            listOf("fog", "mist", "haze", "smoke").any(value::contains) -> "Low visibility"
            listOf("cloud", "overcast").any(value::contains) -> "Cloudy"
            listOf("clear", "sunny", "fair").any(value::contains) -> "Clear"
            else -> "Other"
        }
    }

    private fun pressureCategory(pressure: Double?): String? = when {
        pressure == null -> null
        pressure < 29.7 -> "Low (<29.70)"
        pressure <= 30.2 -> "Normal (29.70-30.20)"
        else -> "High (>30.20)"
    }

    private fun moonCategory(illumination: Double?): String? = when {
        illumination == null -> null
        illumination <= 25.0 -> "Dark moon (0-25%)"
        illumination <= 65.0 -> "Mixed light (26-65%)"
        else -> "Bright moon (66-100%)"
    }

    private fun proximityScore(
        first: Double?,
        second: Double?,
        close: Double,
        near: Double,
        weight: Double,
    ): Double {
        if (first == null || second == null) return 0.0
        val difference = abs(first - second)
        return when {
            difference <= close -> weight
            difference <= near -> weight / 2.0
            else -> 0.0
        }
    }

    private fun circularMonthDistance(first: Int, second: Int): Int {
        val direct = abs(first - second)
        return minOf(direct, 12 - direct)
    }

    private fun sampleConfidence(samples: Int): String = when {
        samples >= 12 -> "Strong sample"
        samples >= 5 -> "Growing sample"
        samples > 0 -> "Early pattern"
        else -> "Waiting for history"
    }

    private fun Int.timePeriod(): String = when (this) {
        in 4..7 -> "Dawn"
        in 8..16 -> "Day"
        in 17..20 -> "Dusk"
        else -> "Night"
    }

    private fun Month.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    private fun SessionSummary.durationMillis(): Long? {
        val end = endedAt ?: return null
        return (end - startedAt).takeIf { it > 0 }
    }

    private fun SessionSummary.dateLabel(zoneId: ZoneId, formatter: DateTimeFormatter): String {
        return startedAt.toLocalDate(zoneId).format(formatter)
    }

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun Long.timePeriod(zoneId: ZoneId): String {
        return Instant.ofEpochMilli(this).atZone(zoneId).hour.timePeriod()
    }

    private fun WildlifeObservationFact.normalizedName(): String = wildlifeName.trim().lowercase()

    private fun List<Double>.median(): Double {
        val middle = size / 2
        return if (size % 2 == 0) (this[middle - 1] + this[middle]) / 2.0 else this[middle]
    }

    private fun formatMinutes(minutes: Long): String {
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
    }

    private fun Double?.oneDecimalOrDash(): String = this?.oneDecimal() ?: "-"

    private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

    private val WeatherOrder = listOf("Clear", "Cloudy", "Rain / storm", "Snow / ice", "Low visibility", "Other")
    private val PressureOrder = listOf("Low (<29.70)", "Normal (29.70-30.20)", "High (>30.20)")
    private val MoonOrder = listOf("Dark moon (0-25%)", "Mixed light (26-65%)", "Bright moon (66-100%)")
}
