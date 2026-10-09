package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class PersonalActivityScore(
    val score: Int? = null,
    val label: String = "Start a session",
    val detail: String = "Current conditions are needed to calculate a personal activity score.",
    val confidencePercent: Int = 0,
    val matchedSessions: Int = 0,
)

object PersonalActivityScoreAnalyzer {
    fun analyze(
        target: SessionSummary?,
        sessions: List<SessionSummary>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PersonalActivityScore {
        if (target == null || target.endedAt != null) return PersonalActivityScore()

        val matches = sessions.asSequence()
            .filter { it.id != target.id && it.endedAt != null }
            .mapNotNull { candidate ->
                SimilarDayMatcher.similarityScore(target, candidate, zoneId)?.let { similarity ->
                    candidate to similarity
                }
            }
            .sortedByDescending { it.second }
            .take(5)
            .toList()

        if (matches.size < 3) {
            return PersonalActivityScore(
                label = "Building history",
                detail = "At least 3 comparable completed sessions are needed for a personal score.",
                matchedSessions = matches.size,
            )
        }

        val totalWeight = matches.sumOf { it.second }.coerceAtLeast(0.01)
        val expectedSightings = matches.sumOf { (session, similarity) ->
            session.totalObservations * similarity
        } / totalWeight
        val completedTotals = sessions
            .filter { it.id != target.id && it.endedAt != null }
            .map { it.totalObservations }
            .sorted()
        val strongSessionReference = completedTotals
            .getOrNull(((completedTotals.size - 1) * 0.8).roundToInt())
            ?.coerceAtLeast(1)
            ?: 1
        val score = ((expectedSightings / strongSessionReference) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        val averageSimilarity = matches.map { it.second }.average()
        val confidence = (averageSimilarity * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        return PersonalActivityScore(
            score = score,
            label = scoreLabel(score),
            detail = "${String.format(Locale.US, "%.1f", expectedSightings)} expected sightings from " +
                "${matches.size} similar sessions.",
            confidencePercent = confidence,
            matchedSessions = matches.size,
        )
    }

    private fun scoreLabel(score: Int): String = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Strong"
        score >= 50 -> "Typical"
        score >= 30 -> "Below usual"
        else -> "Low"
    }
}
