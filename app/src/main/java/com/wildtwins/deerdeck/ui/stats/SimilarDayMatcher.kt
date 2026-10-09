package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SessionWildlifeCount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

data class SimilarDay(
    val sessionId: Long,
    val dateLabel: String,
    val similarityPercent: Int,
    val conditions: String,
    val sightings: String,
)

data class SimilarDaysContext(
    val targetLabel: String = "",
    val days: List<SimilarDay> = emptyList(),
)

object SimilarDayMatcher {
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun match(
        target: SessionSummary?,
        sessions: List<SessionSummary>,
        wildlifeCounts: List<SessionWildlifeCount>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SimilarDaysContext {
        if (target == null) return SimilarDaysContext()
        val countsBySession = wildlifeCounts.groupBy { it.sessionId }
        val matches = sessions.asSequence()
            .filter { it.id != target.id && it.endedAt != null }
            .mapNotNull { candidate ->
                val score = similarityScore(target, candidate, zoneId) ?: return@mapNotNull null
                val topWildlife = countsBySession[candidate.id]
                    .orEmpty()
                    .sortedWith(compareByDescending<SessionWildlifeCount> { it.total }.thenBy { it.wildlifeName })
                    .take(3)
                    .joinToString(", ") { "${it.wildlifeName} x${it.total}" }
                    .ifBlank { "No sightings" }
                SimilarDay(
                    sessionId = candidate.id,
                    dateLabel = Instant.ofEpochMilli(candidate.startedAt).atZone(zoneId).format(dateFormatter),
                    similarityPercent = (score * 100).toInt().coerceIn(0, 100),
                    conditions = candidate.conditionLabel(),
                    sightings = topWildlife,
                )
            }
            .sortedByDescending { it.similarityPercent }
            .take(3)
            .toList()

        val targetDate = Instant.ofEpochMilli(target.startedAt).atZone(zoneId).format(dateFormatter)
        return SimilarDaysContext(
            targetLabel = if (target.endedAt == null) "live session" else targetDate,
            days = matches,
        )
    }

    internal fun similarityScore(target: SessionSummary, candidate: SessionSummary, zoneId: ZoneId): Double? {
        var weightedScore = 0.0
        var totalWeight = 0.0
        var conditionCount = 0

        fun addMetric(weight: Double, score: Double) {
            weightedScore += weight * score.coerceIn(0.0, 1.0)
            totalWeight += weight
            conditionCount += 1
        }

        if (!target.weatherSummary.isNullOrBlank() && !candidate.weatherSummary.isNullOrBlank()) {
            addMetric(25.0, weatherSimilarity(target.weatherSummary, candidate.weatherSummary))
        }
        if (target.temperatureF != null && candidate.temperatureF != null) {
            addMetric(25.0, 1.0 - abs(target.temperatureF - candidate.temperatureF) / 40.0)
        }
        if (target.pressureInHg != null && candidate.pressureInHg != null) {
            addMetric(15.0, 1.0 - abs(target.pressureInHg - candidate.pressureInHg) / 1.0)
        }
        if (target.humidityPercent != null && candidate.humidityPercent != null) {
            addMetric(15.0, 1.0 - abs(target.humidityPercent - candidate.humidityPercent) / 50.0)
        }
        if (target.moonIlluminationPercent != null && candidate.moonIlluminationPercent != null) {
            addMetric(10.0, 1.0 - abs(target.moonIlluminationPercent - candidate.moonIlluminationPercent) / 100.0)
        }
        if (conditionCount < 2) return null

        val targetDay = Instant.ofEpochMilli(target.startedAt).atZone(zoneId).dayOfYear
        val candidateDay = Instant.ofEpochMilli(candidate.startedAt).atZone(zoneId).dayOfYear
        val rawDifference = abs(targetDay - candidateDay)
        val seasonalDifference = min(rawDifference, 365 - rawDifference)
        addMetric(10.0, 1.0 - seasonalDifference / 183.0)

        return weightedScore / totalWeight
    }

    private fun weatherSimilarity(first: String, second: String): Double {
        val firstCategory = weatherCategory(first)
        val secondCategory = weatherCategory(second)
        if (firstCategory == secondCategory) return 1.0
        val wet = setOf("rain", "snow", "storm")
        return if (firstCategory in wet && secondCategory in wet) 0.55 else 0.15
    }

    private fun weatherCategory(value: String): String {
        val normalized = value.lowercase(Locale.US)
        return when {
            "thunder" in normalized || "storm" in normalized -> "storm"
            "snow" in normalized || "sleet" in normalized -> "snow"
            "rain" in normalized || "shower" in normalized || "drizzle" in normalized -> "rain"
            "fog" in normalized || "mist" in normalized || "haze" in normalized -> "fog"
            "cloud" in normalized || "overcast" in normalized -> "cloudy"
            "clear" in normalized || "sunny" in normalized || "fair" in normalized -> "clear"
            else -> normalized.substringBefore(' ')
        }
    }

    private fun SessionSummary.conditionLabel(): String {
        return listOfNotNull(
            weatherSummary,
            temperatureF?.let { "${String.format(Locale.US, "%.0f", it)}F" },
            pressureInHg?.let { "${String.format(Locale.US, "%.2f", it)} inHg" },
            moonPhase,
        ).filter { it.isNotBlank() }.joinToString(" / ")
    }
}
