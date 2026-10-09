package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class BestConditionInsight(
    val category: String,
    val value: String,
    val averageSightings: Double,
    val sessions: Int,
    val totalSightings: Int,
)

data class BestConditionsSummary(
    val insights: List<BestConditionInsight> = emptyList(),
)

object BestConditionsAnalyzer {
    private val WindSpeedPattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:to\\s*\\d+(?:\\.\\d+)?\\s*)?mph", RegexOption.IGNORE_CASE)

    fun analyze(
        sessions: List<SessionSummary>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BestConditionsSummary {
        val completedSessions = sessions.filter { it.endedAt != null }
        if (completedSessions.isEmpty()) return BestConditionsSummary()

        val insights = listOfNotNull(
            completedSessions.bestInsight("Time of day") { session ->
                val hour = Instant.ofEpochMilli(session.startedAt).atZone(zoneId).hour
                when (hour) {
                    in 5..8 -> "Dawn"
                    in 9..11 -> "Morning"
                    in 12..16 -> "Afternoon"
                    in 17..20 -> "Evening"
                    else -> "Night"
                }
            },
            completedSessions.bestInsight("Month") { session ->
                Instant.ofEpochMilli(session.startedAt)
                    .atZone(zoneId)
                    .month
                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
            },
            completedSessions.bestInsight("Temperature") { session ->
                session.temperatureF?.let { temperature ->
                    when {
                        temperature < 32.0 -> "Below 32F"
                        temperature < 50.0 -> "32-49F"
                        temperature < 70.0 -> "50-69F"
                        else -> "70F+"
                    }
                }
            },
            completedSessions.bestInsight("Wind") { session ->
                session.windSummary.windSpeedMph()?.let { speed ->
                    when {
                        speed <= 3.0 -> "Calm"
                        speed <= 9.0 -> "Light"
                        speed <= 17.0 -> "Breezy"
                        else -> "Windy"
                    }
                }
            },
            completedSessions.bestInsight("Pressure") { session ->
                session.pressureInHg?.let { pressure ->
                    when {
                        pressure < 29.80 -> "Below 29.80 inHg"
                        pressure < 30.20 -> "29.80-30.19 inHg"
                        else -> "30.20 inHg+"
                    }
                }
            },
            completedSessions.bestInsight("Moon") { session ->
                session.moonPhase?.trim()?.takeIf { it.isNotBlank() }
            },
        )
        return BestConditionsSummary(insights)
    }

    private fun List<SessionSummary>.bestInsight(
        category: String,
        classifier: (SessionSummary) -> String?,
    ): BestConditionInsight? {
        val buckets = mapNotNull { session ->
            classifier(session)?.let { label -> label to session }
        }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (label, sessions) ->
                BestConditionInsight(
                    category = category,
                    value = label,
                    averageSightings = sessions.sumOf { it.totalObservations }.toDouble() / sessions.size,
                    sessions = sessions.size,
                    totalSightings = sessions.sumOf { it.totalObservations },
                )
            }
        if (buckets.isEmpty()) return null

        val repeatedBuckets = buckets.filter { it.sessions >= 2 }
        return (repeatedBuckets.ifEmpty { buckets }).maxWithOrNull(
            compareBy<BestConditionInsight> { it.averageSightings }
                .thenBy { it.sessions }
                .thenByDescending { it.value },
        )
    }

    private fun String?.windSpeedMph(): Double? {
        val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (value.contains("calm", ignoreCase = true)) return 0.0
        return WindSpeedPattern.find(value)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
