package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.WildlifeObservationFact
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldIntelligenceAnalyzerTest {
    @Test
    fun buildsAllTenFieldIntelligenceFeatures() {
        val sessions = listOf(
            session(1, 2026, 9, 3, 18, 60, 4, "North stand", "Cloudy", 29.90, 45.0, 2),
            session(2, 2026, 9, 4, 18, 120, 3, "North stand", "Cloudy", 30.00, 50.0),
            session(3, 2026, 8, 28, 18, 60, 2, "North stand", "Clear", 29.60, 10.0),
            session(4, 2026, 7, 1, 6, 90, 4, "South field", "Rain showers", 30.30, 80.0, 1),
            session(5, 2025, 9, 5, 18, 60, 4, "North stand", "Cloudy", 29.95, 40.0),
            session(6, 2026, 8, 1, 18, 60, 1, "North stand", "Snow", 29.50, 20.0),
            session(7, 2026, 8, 25, 18, 45, 1, "North stand", "Clear", 29.60, 15.0),
            session(8, 2026, 9, 1, 18, 45, 1, "North stand", "Cloudy", 29.95, 45.0),
            session(
                id = 99,
                year = 2026,
                month = 9,
                day = 20,
                hour = 18,
                durationMinutes = null,
                sightings = 0,
                location = "North stand",
                weather = "Cloudy",
                pressure = 29.96,
                moon = 48.0,
            ),
        )
        val observations = listOf(
            observation(1, 1, "Doe", 1, 2026, 9, 3, 18, 3),
            observation(2, 2, "Buck", 1, 2026, 9, 3, 18, 1),
            observation(3, 1, "Doe", 2, 2026, 9, 4, 18, 2),
            observation(4, 2, "Buck", 2, 2026, 9, 4, 18, 1),
            observation(5, 1, "Doe", 3, 2026, 8, 28, 18, 1),
            observation(6, 3, "Fox", 3, 2026, 8, 28, 18, 1),
            observation(7, 4, "Turkey", 4, 2026, 7, 1, 6, 4),
            observation(8, 1, "Doe", 5, 2025, 9, 5, 18, 2),
            observation(9, 2, "Buck", 5, 2025, 9, 5, 18, 2),
            observation(10, 1, "Doe", 6, 2026, 8, 1, 18, 1),
            observation(11, 2, "Buck", 7, 2026, 8, 25, 18, 1),
            observation(12, 2, "Buck", 8, 2026, 9, 1, 18, 1),
        )
        val nowAt = epoch(2026, 9, 20, 18)

        val result = FieldIntelligenceAnalyzer.analyze(
            sessions = sessions,
            observations = observations,
            zoneId = ZoneOffset.UTC,
            nowAt = nowAt,
        )

        assertEquals("Doe", result.fieldBrief.candidates.first().name)
        assertTrue(result.fieldBrief.matchedSessions >= 3)
        assertFalse(result.bestWindows.isEmpty())
        assertTrue(result.pairings.any { it.first == "Buck" && it.second == "Doe" })
        assertEquals(2, result.locations.size)
        assertEquals(4, result.weatherResponse.size)
        assertEquals(3, result.pressureResponse.size)
        assertEquals(3, result.moonResponse.size)
        assertEquals(5, result.records.size)
        assertEquals(4, result.discoveries.totalDiscovered)
        assertEquals(1, result.discoveries.newLast30Days)
        assertTrue(result.returnWatch.any { it.name == "Buck" })
        assertTrue(result.shareText.contains("Withrow DeerDeck Field Brief"))
        assertTrue(result.shareText.contains("Pattern guidance is not a guarantee"))
    }

    private fun session(
        id: Long,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        durationMinutes: Long?,
        sightings: Int,
        location: String,
        weather: String,
        pressure: Double,
        moon: Double,
        photoCount: Int = 0,
    ): SessionSummary {
        val startedAt = epoch(year, month, day, hour)
        return SessionSummary(
            id = id,
            startedAt = startedAt,
            endedAt = durationMinutes?.let { startedAt + it * 60_000 },
            profileKey = "wildlife_watching",
            title = null,
            notes = null,
            tags = null,
            fieldLocationName = location,
            latitude = 44.98,
            longitude = -93.27,
            locationAccuracyMeters = 8.0,
            locationProvider = "gps",
            elevationFeet = 845.0,
            sunsetAt = startedAt + 3_600_000,
            weatherSummary = weather,
            temperatureF = 62.0,
            windSummary = "5 mph NW",
            pressureInHg = pressure,
            humidityPercent = 55.0,
            dewPointF = 48.0,
            visibilityMiles = 10.0,
            precipitationProbabilityPercent = 10.0,
            forecastSummary = weather,
            moonPhase = "Waxing",
            moonIlluminationPercent = moon,
            environmentStatus = "Complete",
            totalObservations = sightings,
            photoCount = photoCount,
        )
    }

    private fun observation(
        id: Long,
        wildlifeId: Long,
        name: String,
        sessionId: Long,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        quantity: Int,
    ) = WildlifeObservationFact(
        observationId = id,
        speciesId = wildlifeId,
        wildlifeName = name,
        color = 0xFF0046BE,
        sessionId = sessionId,
        observedAt = epoch(year, month, day, hour),
        quantity = quantity,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int): Long {
        return LocalDateTime.of(year, month, day, hour, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
}
