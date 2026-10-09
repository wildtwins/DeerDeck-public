package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class BestConditionsAnalyzerTest {
    @Test
    fun ranksByAverageSightingsInsteadOfRawTotal() {
        val sessions = listOf(
            session(1, hour = 18, sightings = 5),
            session(2, hour = 18, sightings = 3),
            session(3, hour = 10, sightings = 3),
            session(4, hour = 10, sightings = 3),
            session(5, hour = 10, sightings = 3),
        )

        val result = BestConditionsAnalyzer.analyze(sessions, ZoneOffset.UTC)
        val time = result.insights.first { it.category == "Time of day" }

        assertEquals("Evening", time.value)
        assertEquals(4.0, time.averageSightings, 0.001)
        assertEquals(2, time.sessions)
    }

    @Test
    fun prefersRepeatedConditionsOverOneOffOutlier() {
        val sessions = listOf(
            session(1, temperature = 60.0, sightings = 4),
            session(2, temperature = 65.0, sightings = 2),
            session(3, temperature = 75.0, sightings = 20),
        )

        val result = BestConditionsAnalyzer.analyze(sessions, ZoneOffset.UTC)
        val temperature = result.insights.first { it.category == "Temperature" }

        assertEquals("50-69F", temperature.value)
        assertEquals(2, temperature.sessions)
    }

    @Test
    fun categorizesWeatherAndMoonConditions() {
        val sessions = listOf(
            session(1, wind = "Calm", pressure = 30.25, moon = "Waxing crescent", sightings = 2),
            session(2, wind = "2 mph N", pressure = 30.31, moon = "Waxing crescent", sightings = 4),
        )

        val insights = BestConditionsAnalyzer.analyze(sessions, ZoneOffset.UTC)
            .insights
            .associateBy { it.category }

        assertEquals("Calm", insights.getValue("Wind").value)
        assertEquals("30.20 inHg+", insights.getValue("Pressure").value)
        assertEquals("Waxing crescent", insights.getValue("Moon").value)
    }

    private fun session(
        id: Long,
        hour: Int = 18,
        temperature: Double? = null,
        wind: String? = null,
        pressure: Double? = null,
        moon: String? = null,
        sightings: Int,
    ): SessionSummary {
        val startedAt = LocalDateTime.of(2026, 7, 16, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return SessionSummary(
            id = id,
            startedAt = startedAt,
            endedAt = startedAt + 60_000,
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
            weatherSummary = null,
            temperatureF = temperature,
            windSummary = wind,
            pressureInHg = pressure,
            humidityPercent = null,
            dewPointF = null,
            visibilityMiles = null,
            precipitationProbabilityPercent = null,
            forecastSummary = null,
            moonPhase = moon,
            moonIlluminationPercent = null,
            environmentStatus = "Captured",
            totalObservations = sightings,
            photoCount = 0,
        )
    }
}
