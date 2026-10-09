package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SessionWildlifeCount
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarDayMatcherTest {
    @Test
    fun closestEnvironmentalMatchRanksFirstAndIncludesSightings() {
        val target = session(
            id = 1,
            startedAt = 1_725_000_000_000L,
            weather = "Clear",
            temperature = 70.0,
            pressure = 30.02,
            humidity = 48.0,
            moon = 40.0,
            endedAt = null,
        )
        val close = session(
            id = 2,
            startedAt = 1_693_464_000_000L,
            weather = "Fair",
            temperature = 72.0,
            pressure = 30.00,
            humidity = 50.0,
            moon = 44.0,
        )
        val distant = session(
            id = 3,
            startedAt = 1_693_550_400_000L,
            weather = "Thunderstorms",
            temperature = 42.0,
            pressure = 29.20,
            humidity = 92.0,
            moon = 90.0,
        )

        val result = SimilarDayMatcher.match(
            target = target,
            sessions = listOf(target, distant, close),
            wildlifeCounts = listOf(
                SessionWildlifeCount(sessionId = 2, wildlifeName = "Doe", total = 3),
                SessionWildlifeCount(sessionId = 2, wildlifeName = "Turkey", total = 1),
            ),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(2, result.days.first().sessionId)
        assertTrue(result.days.first().sightings.contains("Doe x3"))
        assertTrue(result.days.first().similarityPercent > result.days.last().similarityPercent)
    }

    @Test
    fun sessionsWithoutTwoSharedConditionsAreNotRanked() {
        val target = session(id = 1, startedAt = 1_725_000_000_000L, weather = "Clear")
        val sparse = session(id = 2, startedAt = 1_693_464_000_000L, weather = "Clear")

        val result = SimilarDayMatcher.match(target, listOf(target, sparse), emptyList(), ZoneOffset.UTC)

        assertTrue(result.days.isEmpty())
    }

    @Test
    fun fieldLocationStatsCombineSessionsCaseInsensitively() {
        val stats = listOf(
            session(id = 1, startedAt = 1_000, fieldLocationName = "North stand", totalObservations = 3),
            session(id = 2, startedAt = 2_000, fieldLocationName = "north STAND", totalObservations = 2),
            session(id = 3, startedAt = 3_000, fieldLocationName = "Back field", totalObservations = 1),
            session(id = 4, startedAt = 4_000, fieldLocationName = null, totalObservations = 20),
        ).toFieldLocationStats()

        assertEquals("North stand", stats.first().name)
        assertEquals(2, stats.first().sessions)
        assertEquals(5, stats.first().sightings)
    }

    private fun session(
        id: Long,
        startedAt: Long,
        weather: String? = null,
        temperature: Double? = null,
        pressure: Double? = null,
        humidity: Double? = null,
        moon: Double? = null,
        fieldLocationName: String? = null,
        totalObservations: Int = 0,
        endedAt: Long? = startedAt + 60_000,
    ) = SessionSummary(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        profileKey = "wildlife_watching",
        title = null,
        notes = null,
        tags = null,
        fieldLocationName = fieldLocationName,
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
        humidityPercent = humidity,
        dewPointF = null,
        visibilityMiles = null,
        precipitationProbabilityPercent = null,
        forecastSummary = null,
        moonPhase = null,
        moonIlluminationPercent = moon,
        environmentStatus = "Captured",
        totalObservations = totalObservations,
        photoCount = 0,
    )
}
