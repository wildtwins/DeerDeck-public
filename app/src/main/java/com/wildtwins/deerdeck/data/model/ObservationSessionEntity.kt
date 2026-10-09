package com.wildtwins.deerdeck.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "observation_sessions")
data class ObservationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val profileKey: String = "wildlife_watching",
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val fieldLocationName: String? = null,
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
    val environmentCapturedAt: Long? = null,
    val environmentStatus: String? = null,
    val environmentSchemaVersion: Int? = null,
    val environmentAttemptCount: Int? = null,
    val locationCapturedAt: Long? = null,
    val weatherObservedAt: Long? = null,
    val elevationSource: String? = null,
    val weatherSource: String? = null,
    val lastActivityAt: Long? = null,
)
