package com.wildtwins.deerdeck.data.environment

data class SessionEnvironment(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Double? = null,
    val locationProvider: String? = null,
    val elevationFeet: Double? = null,
    val sunsetAt: Long? = null,
    val weatherSummary: String? = null,
    val temperatureF: Double? = null,
    val windSummary: String? = null,
    val pressureInHg: Double? = null,
    val humidityPercent: Double? = null,
    val dewPointF: Double? = null,
    val visibilityMiles: Double? = null,
    val precipitationProbabilityPercent: Double? = null,
    val forecastSummary: String? = null,
    val moonPhase: String? = null,
    val moonIlluminationPercent: Double? = null,
    val schemaVersion: Int = EnvironmentCaptureContract.CurrentVersion,
    val attemptCount: Int = 1,
    val locationCapturedAt: Long? = null,
    val weatherObservedAt: Long? = null,
    val elevationSource: String? = null,
    val weatherSource: String? = null,
    val capturedAt: Long,
    val status: String,
)

data class EnvironmentRetryResult(
    val sessionId: Long,
    val beforeCapturedFields: Int,
    val afterCapturedFields: Int,
    val isComplete: Boolean,
) {
    val improved: Boolean
        get() = afterCapturedFields > beforeCapturedFields
}
