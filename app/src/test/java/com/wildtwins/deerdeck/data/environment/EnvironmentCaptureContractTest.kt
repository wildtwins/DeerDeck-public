package com.wildtwins.deerdeck.data.environment

import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentCaptureContractTest {
    @Test
    fun completeCaptureHasEveryContractField() {
        val result = EnvironmentCaptureContract.evaluate(completeEnvironment())

        assertTrue(result.isComplete)
        assertEquals(14, result.capturedFields)
        assertEquals(14, result.totalFields)
        assertEquals("Conditions 14/14", result.displayText())
    }

    @Test
    fun partialCaptureNamesEveryMissingField() {
        val result = EnvironmentCaptureContract.evaluate(
            completeEnvironment().copy(
                pressureInHg = null,
                visibilityMiles = null,
            ),
        )

        assertFalse(result.isComplete)
        assertEquals(listOf("pressure", "visibility"), result.missingFields)
        assertEquals("Conditions 12/14; missing pressure, visibility", result.displayText())
    }

    @Test
    fun incompleteSessionRetriesUnlessCaptureWasDisabled() {
        val incomplete = ObservationSessionEntity(
            startedAt = 1_700_000_000_000,
            latitude = 41.0,
            longitude = -93.0,
            weatherSummary = "Clear",
            temperatureF = 45.0,
            environmentStatus = "Partial",
        )

        assertTrue(EnvironmentCaptureContract.needsBackfill(incomplete))
        assertFalse(
            EnvironmentCaptureContract.needsBackfill(
                incomplete.copy(environmentStatus = "Disabled"),
            ),
        )
        assertFalse(
            EnvironmentCaptureContract.needsBackfill(
                incomplete.copy(
                    environmentAttemptCount = EnvironmentCaptureContract.MaxCaptureAttempts,
                ),
            ),
        )
    }

    @Test
    fun manualRetryRequiresOriginalGpsAndStopsAtDurableAttemptLimit() {
        val incomplete = ObservationSessionEntity(
            startedAt = 1_700_000_000_000,
            latitude = 41.0,
            longitude = -93.0,
            environmentStatus = "Partial",
            environmentAttemptCount = 2,
        )

        assertEquals(
            ManualEnvironmentRetryEligibility.Eligible,
            EnvironmentCaptureContract.manualRetryEligibility(incomplete),
        )
        assertEquals(
            ManualEnvironmentRetryEligibility.MissingOriginalGps,
            EnvironmentCaptureContract.manualRetryEligibility(
                incomplete.copy(latitude = null, longitude = null),
            ),
        )
        assertEquals(
            ManualEnvironmentRetryEligibility.AttemptLimitReached,
            EnvironmentCaptureContract.manualRetryEligibility(
                incomplete.copy(environmentAttemptCount = EnvironmentCaptureContract.MaxCaptureAttempts),
            ),
        )
    }

    private fun completeEnvironment() = SessionEnvironment(
        latitude = 41.0,
        longitude = -93.0,
        elevationFeet = 940.0,
        sunsetAt = 1_700_030_000_000,
        weatherSummary = "Clear",
        temperatureF = 45.0,
        windSummary = "5 mph NW",
        pressureInHg = 29.92,
        humidityPercent = 62.0,
        dewPointF = 33.0,
        visibilityMiles = 10.0,
        precipitationProbabilityPercent = 5.0,
        forecastSummary = "Clear",
        moonPhase = "Waxing crescent",
        moonIlluminationPercent = 38.0,
        capturedAt = 1_700_000_000_000,
        status = "Complete",
    )
}
