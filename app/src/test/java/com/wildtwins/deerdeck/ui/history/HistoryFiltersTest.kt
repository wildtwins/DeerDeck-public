package com.wildtwins.deerdeck.ui.history

import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SessionWildlifeCount
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFiltersTest {
    private val now = 1_800_000_000_000L

    @Test
    fun searchMatchesWildlifeTagsConditionsAndLocation() {
        val sessions = listOf(
            session(
                id = 1,
                startedAt = now - 2 * DayMillis,
                notes = "Quiet evening near the feeder",
                tags = "[\"Clear\",\"Windy\"]",
                fieldLocationName = "North stand",
                weatherSummary = "Partly cloudy",
            ),
            session(
                id = 2,
                startedAt = now - 10 * DayMillis,
                notes = "Fresh snow",
                tags = "[\"Snow\"]",
                fieldLocationName = "Back field",
                weatherSummary = "Overcast",
            ),
        )
        val wildlife = listOf(
            SessionWildlifeCount(sessionId = 1, wildlifeName = "Buck", total = 2),
            SessionWildlifeCount(sessionId = 2, wildlifeName = "Pheasant", total = 1),
        )

        assertEquals(
            listOf(1L),
            filterHistorySessions(sessions, wildlife, "buck north", HistoryRange.All, now, ZoneOffset.UTC)
                .map { it.id },
        )
        assertEquals(
            listOf(2L),
            filterHistorySessions(sessions, wildlife, "snow pheasant", HistoryRange.All, now, ZoneOffset.UTC)
                .map { it.id },
        )
    }

    @Test
    fun rangeExcludesOlderSessions() {
        val sessions = listOf(
            session(id = 1, startedAt = now - 29 * DayMillis),
            session(id = 2, startedAt = now - 31 * DayMillis),
            session(id = 3, startedAt = now - 366 * DayMillis),
        )

        assertEquals(
            listOf(1L),
            filterHistorySessions(sessions, emptyList(), "", HistoryRange.ThirtyDays, now, ZoneOffset.UTC)
                .map { it.id },
        )
        assertEquals(
            listOf(1L, 2L),
            filterHistorySessions(sessions, emptyList(), "", HistoryRange.OneYear, now, ZoneOffset.UTC)
                .map { it.id },
        )
    }

    private fun session(
        id: Long,
        startedAt: Long,
        notes: String? = null,
        tags: String? = null,
        fieldLocationName: String? = null,
        weatherSummary: String? = null,
    ) = SessionSummary(
        id = id,
        startedAt = startedAt,
        endedAt = startedAt + 60_000,
        profileKey = "wildlife_watching",
        title = null,
        notes = notes,
        tags = tags,
        fieldLocationName = fieldLocationName,
        latitude = null,
        longitude = null,
        locationAccuracyMeters = null,
        locationProvider = null,
        elevationFeet = null,
        sunsetAt = null,
        weatherSummary = weatherSummary,
        temperatureF = null,
        windSummary = null,
        pressureInHg = null,
        humidityPercent = null,
        dewPointF = null,
        visibilityMiles = null,
        precipitationProbabilityPercent = null,
        forecastSummary = null,
        moonPhase = null,
        moonIlluminationPercent = null,
        environmentStatus = null,
        totalObservations = 0,
        photoCount = 0,
    )

    private companion object {
        const val DayMillis = 24 * 60 * 60 * 1_000L
    }
}
