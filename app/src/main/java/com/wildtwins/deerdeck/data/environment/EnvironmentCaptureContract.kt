package com.wildtwins.deerdeck.data.environment

import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SessionSummary

data class EnvironmentCompleteness(
    val capturedFields: Int,
    val totalFields: Int,
    val missingFields: List<String>,
) {
    val isComplete: Boolean
        get() = missingFields.isEmpty()

    fun displayText(): String {
        val count = "Conditions $capturedFields/$totalFields"
        return if (isComplete) count else "$count; missing ${missingFields.joinToString(", ")}"
    }
}

enum class ManualEnvironmentRetryEligibility {
    Eligible,
    Complete,
    Disabled,
    MissingOriginalGps,
    AttemptLimitReached,
}

object EnvironmentCaptureContract {
    const val CurrentVersion = 1
    const val MaxCaptureAttempts = 6

    private const val DisabledStatus = "Disabled"
    private val Fields = listOf(
        "GPS coordinates",
        "elevation",
        "sunset",
        "weather",
        "temperature",
        "wind",
        "pressure",
        "humidity",
        "dew point",
        "visibility",
        "precipitation chance",
        "forecast",
        "moon phase",
        "moon illumination",
    )

    fun evaluate(environment: SessionEnvironment): EnvironmentCompleteness {
        return evaluate(
            latitude = environment.latitude,
            longitude = environment.longitude,
            elevationFeet = environment.elevationFeet,
            sunsetAt = environment.sunsetAt,
            weatherSummary = environment.weatherSummary,
            temperatureF = environment.temperatureF,
            windSummary = environment.windSummary,
            pressureInHg = environment.pressureInHg,
            humidityPercent = environment.humidityPercent,
            dewPointF = environment.dewPointF,
            visibilityMiles = environment.visibilityMiles,
            precipitationProbabilityPercent = environment.precipitationProbabilityPercent,
            forecastSummary = environment.forecastSummary,
            moonPhase = environment.moonPhase,
            moonIlluminationPercent = environment.moonIlluminationPercent,
        )
    }

    fun evaluate(session: ObservationSessionEntity): EnvironmentCompleteness {
        return evaluate(
            latitude = session.latitude,
            longitude = session.longitude,
            elevationFeet = session.elevationFeet,
            sunsetAt = session.sunsetAt,
            weatherSummary = session.weatherSummary,
            temperatureF = session.temperatureF,
            windSummary = session.windSummary,
            pressureInHg = session.pressureInHg,
            humidityPercent = session.humidityPercent,
            dewPointF = session.dewPointF,
            visibilityMiles = session.visibilityMiles,
            precipitationProbabilityPercent = session.precipitationProbabilityPercent,
            forecastSummary = session.forecastSummary,
            moonPhase = session.moonPhase,
            moonIlluminationPercent = session.moonIlluminationPercent,
        )
    }

    fun evaluate(session: SessionSummary): EnvironmentCompleteness {
        return evaluate(
            latitude = session.latitude,
            longitude = session.longitude,
            elevationFeet = session.elevationFeet,
            sunsetAt = session.sunsetAt,
            weatherSummary = session.weatherSummary,
            temperatureF = session.temperatureF,
            windSummary = session.windSummary,
            pressureInHg = session.pressureInHg,
            humidityPercent = session.humidityPercent,
            dewPointF = session.dewPointF,
            visibilityMiles = session.visibilityMiles,
            precipitationProbabilityPercent = session.precipitationProbabilityPercent,
            forecastSummary = session.forecastSummary,
            moonPhase = session.moonPhase,
            moonIlluminationPercent = session.moonIlluminationPercent,
        )
    }

    fun needsBackfill(session: ObservationSessionEntity): Boolean {
        return !isDisabled(session.environmentStatus) &&
            !evaluate(session).isComplete &&
            (session.environmentAttemptCount ?: 0) < MaxCaptureAttempts
    }

    fun manualRetryEligibility(session: ObservationSessionEntity): ManualEnvironmentRetryEligibility {
        return manualRetryEligibility(
            environmentStatus = session.environmentStatus,
            completeness = evaluate(session),
            latitude = session.latitude,
            longitude = session.longitude,
            attemptCount = session.environmentAttemptCount ?: 0,
        )
    }

    fun manualRetryEligibility(session: SessionSummary): ManualEnvironmentRetryEligibility {
        return manualRetryEligibility(
            environmentStatus = session.environmentStatus,
            completeness = evaluate(session),
            latitude = session.latitude,
            longitude = session.longitude,
            attemptCount = session.environmentAttemptCount ?: 0,
        )
    }

    fun statusFor(environment: SessionEnvironment): String {
        return if (evaluate(environment).isComplete) "Complete" else "Partial"
    }

    fun isDisabled(status: String?): Boolean {
        return status == DisabledStatus || status == "Location and weather off"
    }

    private fun manualRetryEligibility(
        environmentStatus: String?,
        completeness: EnvironmentCompleteness,
        latitude: Double?,
        longitude: Double?,
        attemptCount: Int,
    ): ManualEnvironmentRetryEligibility {
        return when {
            isDisabled(environmentStatus) -> ManualEnvironmentRetryEligibility.Disabled
            completeness.isComplete -> ManualEnvironmentRetryEligibility.Complete
            latitude == null || longitude == null -> ManualEnvironmentRetryEligibility.MissingOriginalGps
            attemptCount >= MaxCaptureAttempts -> ManualEnvironmentRetryEligibility.AttemptLimitReached
            else -> ManualEnvironmentRetryEligibility.Eligible
        }
    }

    private fun evaluate(
        latitude: Double?,
        longitude: Double?,
        elevationFeet: Double?,
        sunsetAt: Long?,
        weatherSummary: String?,
        temperatureF: Double?,
        windSummary: String?,
        pressureInHg: Double?,
        humidityPercent: Double?,
        dewPointF: Double?,
        visibilityMiles: Double?,
        precipitationProbabilityPercent: Double?,
        forecastSummary: String?,
        moonPhase: String?,
        moonIlluminationPercent: Double?,
    ): EnvironmentCompleteness {
        val present = listOf(
            latitude != null && longitude != null,
            elevationFeet != null,
            sunsetAt != null,
            !weatherSummary.isNullOrBlank(),
            temperatureF != null,
            !windSummary.isNullOrBlank(),
            pressureInHg != null,
            humidityPercent != null,
            dewPointF != null,
            visibilityMiles != null,
            precipitationProbabilityPercent != null,
            !forecastSummary.isNullOrBlank(),
            !moonPhase.isNullOrBlank(),
            moonIlluminationPercent != null,
        )
        val missing = Fields.filterIndexed { index, _ -> !present[index] }
        return EnvironmentCompleteness(
            capturedFields = present.count { it },
            totalFields = Fields.size,
            missingFields = missing,
        )
    }
}
