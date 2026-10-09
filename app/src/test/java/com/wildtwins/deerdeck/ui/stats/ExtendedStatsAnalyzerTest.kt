package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SpeciesCount
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedStatsAnalyzerTest {
    @Test
    fun calculatesEffortRhythmQualityAndDiversity() {
        val sessions = listOf(
            session(
                id = 1,
                day = 1,
                hour = 18,
                durationMinutes = 60,
                sightings = 3,
                photoCount = 1,
                completeConditions = true,
            ),
            session(
                id = 2,
                day = 2,
                hour = 18,
                durationMinutes = 120,
                sightings = 9,
                partialConditions = true,
            ),
            session(
                id = 3,
                day = 4,
                hour = 10,
                durationMinutes = 30,
                sightings = 0,
                captureDisabled = true,
            ),
        )
        val wildlife = listOf(
            SpeciesCount(1, "Doe", 0, 6),
            SpeciesCount(2, "Buck", 0, 3),
            SpeciesCount(3, "Fox", 0, 1),
        )

        val result = ExtendedStatsAnalyzer.analyze(sessions, wildlife, ZoneOffset.UTC)

        assertEquals(210L, result.effort.totalMinutes)
        assertEquals(70.0, result.effort.averageMinutes!!, 0.001)
        assertEquals(120L, result.effort.longestMinutes)
        assertEquals(12.0 / 3.5, result.effort.sightingsPerHour!!, 0.001)
        assertEquals(4.0, result.effort.sightingsPerSession!!, 0.001)

        assertEquals(3, result.rhythm.activeDays)
        assertEquals(1, result.rhythm.recentStreakDays)
        assertEquals(2, result.rhythm.longestStreakDays)
        assertEquals("Dusk", result.rhythm.peakPeriod)
        assertEquals(12, result.rhythm.peakPeriodSightings)

        assertEquals(1, result.quality.completeConditionSessions)
        assertEquals(2, result.quality.eligibleConditionSessions)
        assertEquals(61, result.quality.averageConditionCompletenessPercent)
        assertEquals(67, result.quality.gpsCoveragePercent)
        assertEquals(67, result.quality.weatherCoveragePercent)
        assertEquals(33, result.quality.mediaSessionPercent)

        assertEquals(3, result.diversity.recordedWildlife)
        assertEquals(2.5, result.diversity.effectiveDiversity!!, 0.1)
        assertEquals(82, result.diversity.evennessPercent)
        assertEquals(60, result.diversity.topWildlifeSharePercent)
        assertEquals(1, result.diversity.oneTimeRecords)
    }

    private fun session(
        id: Long,
        day: Int,
        hour: Int,
        durationMinutes: Long,
        sightings: Int,
        photoCount: Int = 0,
        completeConditions: Boolean = false,
        partialConditions: Boolean = false,
        captureDisabled: Boolean = false,
    ): SessionSummary {
        val startedAt = LocalDateTime.of(2026, 7, day, hour, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        return SessionSummary(
            id = id,
            startedAt = startedAt,
            endedAt = startedAt + durationMinutes * 60_000,
            profileKey = "wildlife_watching",
            title = null,
            notes = null,
            tags = null,
            fieldLocationName = null,
            latitude = if (completeConditions || partialConditions) 44.98 else null,
            longitude = if (completeConditions || partialConditions) -93.27 else null,
            locationAccuracyMeters = if (completeConditions) 8.0 else null,
            locationProvider = if (completeConditions) "gps" else null,
            elevationFeet = if (completeConditions) 845.0 else null,
            sunsetAt = if (completeConditions) startedAt + 3_600_000 else null,
            weatherSummary = if (completeConditions || partialConditions) "Clear" else null,
            temperatureF = if (completeConditions || partialConditions) 72.0 else null,
            windSummary = if (completeConditions) "5 mph NW" else null,
            pressureInHg = if (completeConditions) 29.92 else null,
            humidityPercent = if (completeConditions) 55.0 else null,
            dewPointF = if (completeConditions) 54.0 else null,
            visibilityMiles = if (completeConditions) 10.0 else null,
            precipitationProbabilityPercent = if (completeConditions) 5.0 else null,
            forecastSummary = if (completeConditions) "Clear" else null,
            moonPhase = if (completeConditions) "Waxing crescent" else null,
            moonIlluminationPercent = if (completeConditions) 38.0 else null,
            environmentStatus = if (captureDisabled) "Disabled" else if (completeConditions) "Complete" else "Partial",
            totalObservations = sightings,
            photoCount = photoCount,
        )
    }
}
