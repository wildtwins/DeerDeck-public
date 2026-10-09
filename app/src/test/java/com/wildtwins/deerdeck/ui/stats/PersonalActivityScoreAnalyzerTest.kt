package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalActivityScoreAnalyzerTest {
    @Test
    fun scoresLiveConditionsFromComparableCompletedSessions() {
        val target = session(id = 1, endedAt = null, temperature = 70.0, pressure = 30.0, sightings = 0)
        val sessions = listOf(
            target,
            session(id = 2, temperature = 69.0, pressure = 30.02, sightings = 8),
            session(id = 3, temperature = 72.0, pressure = 29.98, sightings = 7),
            session(id = 4, temperature = 68.0, pressure = 30.05, sightings = 6),
            session(id = 5, temperature = 42.0, pressure = 29.20, sightings = 1, weather = "Storm"),
        )

        val result = PersonalActivityScoreAnalyzer.analyze(target, sessions, ZoneOffset.UTC)

        assertNotNull(result.score)
        assertTrue(result.score!! >= 70)
        assertEquals(4, result.matchedSessions)
        assertTrue(result.confidencePercent > 50)
    }

    @Test
    fun requiresThreeComparableSessions() {
        val target = session(id = 1, endedAt = null)
        val result = PersonalActivityScoreAnalyzer.analyze(
            target,
            listOf(target, session(id = 2), session(id = 3)),
            ZoneOffset.UTC,
        )

        assertEquals(null, result.score)
        assertEquals("Building history", result.label)
    }

    private fun session(
        id: Long,
        endedAt: Long? = id * 1_000 + 500,
        temperature: Double = 70.0,
        pressure: Double = 30.0,
        sightings: Int = 3,
        weather: String = "Clear",
    ) = SessionSummary(
        id = id,
        startedAt = id * 1_000,
        endedAt = endedAt,
        profileKey = "wildlife_watching",
        title = null,
        notes = null,
        tags = null,
        fieldLocationName = null,
        latitude = null,
        longitude = null,
        locationAccuracyMeters = null,
        locationProvider = null,
        elevationFeet = null,
        sunsetAt = null,
        weatherSummary = weather,
        temperatureF = temperature,
        windSummary = null,
        pressureInHg = pressure,
        humidityPercent = 50.0,
        dewPointF = null,
        visibilityMiles = null,
        precipitationProbabilityPercent = null,
        forecastSummary = null,
        moonPhase = null,
        moonIlluminationPercent = 40.0,
        environmentStatus = "Captured",
        totalObservations = sightings,
        photoCount = 0,
    )
}
