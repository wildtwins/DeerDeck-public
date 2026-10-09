package com.wildtwins.deerdeck.data.export

import com.wildtwins.deerdeck.data.model.ObservationExportRow
import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationCsvExporterTest {
    @Test
    fun toCsvEscapesNotesAndKeepsFullLocationByDefault() {
        val csv = ObservationCsvExporter.toCsv(listOf(sampleRow()))

        assertTrue(csv.contains("\"near feeder, looked \"\"calm\"\"\""))
        assertTrue(csv.contains("North stand"))
        assertTrue(csv.contains("41.12345"))
        assertTrue(csv.contains("12.0,gps,955"))
    }

    @Test
    fun privateCsvRoundsCoordinatesAndRedactsSensitiveLocationDetails() {
        val csv = ObservationCsvExporter.toCsv(
            rows = listOf(sampleRow()),
            privacy = ExportPrivacyOptions(
                locationPrecision = SharedLocationPrecision.Approximate,
                includeNamedFieldLocations = false,
            ),
        )

        assertTrue(csv.contains("41.12,-93.12"))
        assertFalse(csv.contains("North stand"))
        assertFalse(csv.contains("41.12345"))
        assertFalse(csv.contains(",gps,"))
        assertFalse(csv.contains(",955,"))
        assertFalse(csv.contains("NWS KDSM"))
    }

    private fun sampleRow() = ObservationExportRow(
        sessionId = 7,
        sessionStartedAt = 1_700_000_000_000,
        sessionEndedAt = null,
        sessionNotes = "Cold morning",
        sessionTags = "[\"Cold\",\"Cloudy\"]",
        sessionFieldLocationName = "North stand",
        sessionLatitude = 41.12345,
        sessionLongitude = -93.12345,
        sessionLocationAccuracyMeters = 12.0,
        sessionLocationProvider = "gps",
        sessionElevationFeet = 955.0,
        sessionSunsetAt = 1_700_030_000_000,
        sessionWeatherSummary = "Cloudy",
        sessionTemperatureF = 42.0,
        sessionWindSummary = "8 mph NW",
        sessionPressureInHg = 29.92,
        sessionHumidityPercent = 68.0,
        sessionDewPointF = 32.1,
        sessionVisibilityMiles = 10.0,
        sessionPrecipitationProbabilityPercent = 20.0,
        sessionForecastSummary = "Cloudy then clearing",
        sessionMoonPhase = "Waxing crescent",
        sessionMoonIlluminationPercent = 38.0,
        sessionEnvironmentStatus = "Complete",
        sessionEnvironmentSchemaVersion = 1,
        sessionEnvironmentAttemptCount = 1,
        sessionLocationCapturedAt = 1_700_000_000_000,
        sessionWeatherObservedAt = 1_700_000_000_000,
        sessionElevationSource = "USGS 3DEP",
        sessionWeatherSource = "NWS KDSM",
        speciesName = "Doe",
        observedAt = 1_700_000_060_000,
        quantity = 2,
        sex = "Doe",
        ageClass = "Adult",
        behavior = "Browsing",
        notes = "near feeder, looked \"calm\"",
    )
}
