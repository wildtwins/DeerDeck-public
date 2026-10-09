package com.wildtwins.deerdeck.ui.stats

import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.WildlifeObservationFact
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsBundleAnalyzerTest {
    @Test
    fun buildsWildlifeComparisonCalendarAndHealthViewsFromOneDataset() {
        val sessions = listOf(
            session(1, 2026, 8, 20, 18, 60, 2, "North stand", complete = true, photoCount = 2),
            session(2, 2026, 8, 10, 6, 120, 3, "South field", partial = true),
            session(3, 2026, 7, 15, 12, 30, 0, null),
        )
        val observations = listOf(
            observation(10, 1, "Doe", 1, 2026, 8, 20, 18, 2),
            observation(11, 3, "Doe", 2, 2026, 8, 10, 6, 1),
            observation(12, 2, "Buck", 2, 2026, 8, 10, 6, 2),
        )
        val media = listOf(
            ObservationPhotoEntity(
                id = 1,
                observationId = 10,
                sessionId = 1,
                uri = "content://photo/1",
                capturedAt = epoch(2026, 8, 20, 18),
            ),
            ObservationPhotoEntity(
                id = 2,
                sessionId = 1,
                uri = "content://photo/2",
                capturedAt = epoch(2026, 8, 20, 18),
            ),
        )

        val result = StatsBundleAnalyzer.analyze(
            sessions = sessions,
            observations = observations,
            media = media,
            zoneId = ZoneOffset.UTC,
            today = LocalDate.of(2026, 8, 22),
        )

        val doe = result.wildlifeInsights.first { it.name == "Doe" }
        assertEquals(3, doe.total)
        assertEquals(2, doe.sessionsSeen)
        assertEquals(2, doe.activeDays)
        assertEquals(1, doe.linkedMedia)
        assertEquals(3.0 / 3.5, doe.sightingsPerFieldHour!!, 0.001)
        assertEquals(listOf(1, 0, 2, 0), doe.timePeriods.map { it.count })
        assertEquals(listOf("North stand", "South field"), doe.locations.map { it.label })
        assertEquals(2, doe.conditionSamples)

        val thirtyDays = result.periodComparisons.first { it.preset == ComparisonPreset.ThirtyDays }
        assertEquals(2, thirtyDays.current.sessions)
        assertEquals(5, thirtyDays.current.sightings)
        assertEquals(3, thirtyDays.current.effortMinutes / 60)
        assertEquals(1, thirtyDays.previous.sessions)
        assertEquals(0, thirtyDays.previous.sightings)

        val augustTenth = result.calendarDays.getValue(LocalDate.of(2026, 8, 10))
        assertEquals(3, augustTenth.sightings)
        assertEquals(2, augustTenth.wildlifeTypes)
        assertEquals(120, augustTenth.effortMinutes)
        val julyFifteenth = result.calendarDays.getValue(LocalDate.of(2026, 7, 15))
        assertEquals(1, julyFifteenth.zeroSightingSessions)

        assertEquals(3, result.dataHealth.eligibleSessions)
        assertEquals(1, result.dataHealth.completeSessions)
        assertEquals(1, result.dataHealth.retryableSessions)
        assertEquals(1, result.dataHealth.missingLocationSessions)
        assertTrue(result.dataHealth.issues.first { it.sessionId == 2L }.retryable)
        assertFalse(result.dataHealth.issues.first { it.sessionId == 3L }.retryable)
    }

    @Test
    fun dataHealthSeparatesAttemptLimitedSessionsFromRetryableSessions() {
        val result = StatsBundleAnalyzer.analyze(
            sessions = listOf(
                session(
                    id = 4,
                    year = 2026,
                    month = 8,
                    day = 5,
                    hour = 8,
                    durationMinutes = 45,
                    sightings = 1,
                    location = "West trail",
                    partial = true,
                    retryLimitReached = true,
                ),
            ),
            observations = emptyList(),
            media = emptyList(),
            zoneId = ZoneOffset.UTC,
            today = LocalDate.of(2026, 8, 22),
        )

        assertEquals(0, result.dataHealth.retryableSessions)
        assertEquals(1, result.dataHealth.retryLimitSessions)
        assertEquals(
            com.wildtwins.deerdeck.data.environment.ManualEnvironmentRetryEligibility.AttemptLimitReached,
            result.dataHealth.issues.first { it.sessionId == 4L }.retryEligibility,
        )
    }

    private fun session(
        id: Long,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        durationMinutes: Long,
        sightings: Int,
        location: String?,
        complete: Boolean = false,
        partial: Boolean = false,
        photoCount: Int = 0,
        retryLimitReached: Boolean = false,
    ): SessionSummary {
        val startedAt = epoch(year, month, day, hour)
        val hasGps = complete || partial
        return SessionSummary(
            id = id,
            startedAt = startedAt,
            endedAt = startedAt + durationMinutes * 60_000,
            profileKey = "wildlife_watching",
            title = null,
            notes = null,
            tags = null,
            fieldLocationName = location,
            latitude = if (hasGps) 44.98 else null,
            longitude = if (hasGps) -93.27 else null,
            locationAccuracyMeters = if (hasGps) 8.0 else null,
            locationProvider = if (hasGps) "gps" else null,
            elevationFeet = if (complete) 845.0 else null,
            sunsetAt = if (complete) startedAt + 3_600_000 else null,
            weatherSummary = if (hasGps) "Clear" else null,
            temperatureF = if (hasGps) 70.0 else null,
            windSummary = if (complete) "5 mph NW" else null,
            pressureInHg = if (hasGps) 29.92 else null,
            humidityPercent = if (complete) 55.0 else null,
            dewPointF = if (complete) 54.0 else null,
            visibilityMiles = if (complete) 10.0 else null,
            precipitationProbabilityPercent = if (complete) 5.0 else null,
            forecastSummary = if (complete) "Clear" else null,
            moonPhase = if (complete) "Waxing crescent" else null,
            moonIlluminationPercent = if (hasGps) 38.0 else null,
            environmentStatus = if (complete) "Complete" else "Partial",
            environmentAttemptCount = when {
                retryLimitReached -> 6
                hasGps -> 1
                else -> 0
            },
            weatherSource = if (hasGps) "weather.gov" else null,
            totalObservations = sightings,
            photoCount = photoCount,
        )
    }

    private fun observation(
        id: Long,
        speciesId: Long,
        name: String,
        sessionId: Long,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        quantity: Int,
    ) = WildlifeObservationFact(
        observationId = id,
        speciesId = speciesId,
        wildlifeName = name,
        color = 0xFF0072B2,
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
