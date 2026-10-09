package com.wildtwins.deerdeck.ui.history

import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMediaGalleryTest {
    @Test
    fun joinsMediaToSessionsSortsNewestAndIgnoresOrphans() {
        val sessions = listOf(session(10), session(20))
        val media = listOf(
            media(id = 1, sessionId = 10, capturedAt = 1_000),
            media(id = 2, sessionId = 20, capturedAt = 3_000, type = "video"),
            media(id = 3, sessionId = 999, capturedAt = 4_000),
        )

        val result = buildHistoryMediaItems(sessions, media)

        assertEquals(listOf(2L, 1L), result.map { it.media.id })
        assertEquals(listOf(20L, 10L), result.map { it.session.id })
    }

    private fun media(
        id: Long,
        sessionId: Long,
        capturedAt: Long,
        type: String = "photo",
    ) = ObservationPhotoEntity(
        id = id,
        sessionId = sessionId,
        uri = "content://deerdeck/$id",
        capturedAt = capturedAt,
        mediaType = type,
    )

    private fun session(id: Long) = SessionSummary(
        id = id,
        startedAt = id * 1_000,
        endedAt = id * 1_000 + 500,
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
}
