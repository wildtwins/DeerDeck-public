package com.wildtwins.deerdeck.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.wildtwins.deerdeck.data.model.ObservationExportRow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ObservationCsvExporter {
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())

    fun write(
        context: Context,
        rows: List<ObservationExportRow>,
        createdAt: Long,
        privacy: ExportPrivacyOptions = ExportPrivacyOptions.Full,
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val export = build(rows, createdAt, privacy)
        val file = File(exportDir, export.fileName)
        file.writeText(export.content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun build(
        rows: List<ObservationExportRow>,
        createdAt: Long,
        privacy: ExportPrivacyOptions = ExportPrivacyOptions.Full,
    ): CsvExport {
        return CsvExport(
            fileName = "deerdeck-observations-${fileTimestamp.format(Instant.ofEpochMilli(createdAt))}.csv",
            content = toCsv(rows, privacy),
        )
    }

    fun toCsv(
        rows: List<ObservationExportRow>,
        privacy: ExportPrivacyOptions = ExportPrivacyOptions.Full,
    ): String {
        val header = listOf(
            "session_id",
            "session_started_at",
            "session_ended_at",
            "session_notes",
            "session_tags",
            "session_field_location",
            "session_latitude",
            "session_longitude",
            "session_location_accuracy_meters",
            "session_location_provider",
            "session_elevation_feet",
            "session_sunset_at",
            "session_weather",
            "session_temperature_f",
            "session_wind",
            "session_pressure_inhg",
            "session_humidity_percent",
            "session_dew_point_f",
            "session_visibility_miles",
            "session_precipitation_probability_percent",
            "session_forecast",
            "session_moon_phase",
            "session_moon_illumination_percent",
            "session_environment_status",
            "session_environment_schema_version",
            "session_environment_attempt_count",
            "session_location_captured_at",
            "session_weather_observed_at",
            "session_elevation_source",
            "session_weather_source",
            "wildlife",
            "observed_at",
            "quantity",
            "sex",
            "age_class",
            "behavior",
            "notes",
        )

        return buildString {
            appendLine(header.joinToString(","))
            rows.forEach { row ->
                appendLine(
                    listOf(
                        row.sessionId.toString(),
                        iso(row.sessionStartedAt),
                        row.sessionEndedAt?.let(::iso).orEmpty(),
                        row.sessionNotes.orEmpty(),
                        row.sessionTags.orEmpty(),
                        row.sessionFieldLocationName
                            ?.takeIf { privacy.includeNamedFieldLocations }
                            .orEmpty(),
                        row.sessionLatitude
                            ?.forSharedLocation(privacy.locationPrecision)
                            ?.formatDecimal(if (privacy.locationPrecision.isApproximate) 2 else 5)
                            .orEmpty(),
                        row.sessionLongitude
                            ?.forSharedLocation(privacy.locationPrecision)
                            ?.formatDecimal(if (privacy.locationPrecision.isApproximate) 2 else 5)
                            .orEmpty(),
                        row.sessionLocationAccuracyMeters
                            ?.takeIf { privacy.locationPrecision.isExact }
                            ?.formatDecimal(1)
                            .orEmpty(),
                        row.sessionLocationProvider
                            ?.takeIf { privacy.locationPrecision.isExact }
                            .orEmpty(),
                        row.sessionElevationFeet
                            ?.takeIf { privacy.locationPrecision.isExact }
                            ?.formatDecimal(0)
                            .orEmpty(),
                        row.sessionSunsetAt?.let(::iso).orEmpty(),
                        row.sessionWeatherSummary.orEmpty(),
                        row.sessionTemperatureF?.formatDecimal(1).orEmpty(),
                        row.sessionWindSummary.orEmpty(),
                        row.sessionPressureInHg?.formatDecimal(2).orEmpty(),
                        row.sessionHumidityPercent?.formatDecimal(0).orEmpty(),
                        row.sessionDewPointF?.formatDecimal(1).orEmpty(),
                        row.sessionVisibilityMiles?.formatDecimal(1).orEmpty(),
                        row.sessionPrecipitationProbabilityPercent?.formatDecimal(0).orEmpty(),
                        row.sessionForecastSummary.orEmpty(),
                        row.sessionMoonPhase.orEmpty(),
                        row.sessionMoonIlluminationPercent?.formatDecimal(0).orEmpty(),
                        row.sessionEnvironmentStatus.orEmpty(),
                        row.sessionEnvironmentSchemaVersion?.toString().orEmpty(),
                        row.sessionEnvironmentAttemptCount?.toString().orEmpty(),
                        row.sessionLocationCapturedAt?.let(::iso).orEmpty(),
                        row.sessionWeatherObservedAt?.let(::iso).orEmpty(),
                        row.sessionElevationSource.orEmpty(),
                        row.sessionWeatherSource
                            ?.takeIf { privacy.locationPrecision.isExact }
                            .orEmpty(),
                        row.speciesName,
                        iso(row.observedAt),
                        row.quantity.toString(),
                        row.sex.orEmpty(),
                        row.ageClass.orEmpty(),
                        row.behavior.orEmpty(),
                        row.notes.orEmpty(),
                    ).joinToString(",") { cell -> cell.csvEscape() },
                )
            }
        }
    }

    private val com.wildtwins.deerdeck.data.settings.SharedLocationPrecision.isExact: Boolean
        get() = this == com.wildtwins.deerdeck.data.settings.SharedLocationPrecision.Exact

    private val com.wildtwins.deerdeck.data.settings.SharedLocationPrecision.isApproximate: Boolean
        get() = this == com.wildtwins.deerdeck.data.settings.SharedLocationPrecision.Approximate

    private fun iso(timestamp: Long): String = Instant.ofEpochMilli(timestamp).toString()

    private fun Double.formatDecimal(decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", this)
    }

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        return if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
