package com.wildtwins.deerdeck.data.environment

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object SunCalculator {
    fun sunsetMillis(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zoneId: ZoneId,
    ): Long? {
        val dayOfYear = date.dayOfYear.toDouble()
        val longitudeHour = longitude / 15.0
        val approximateTime = dayOfYear + ((18.0 - longitudeHour) / 24.0)
        val meanAnomaly = (0.9856 * approximateTime) - 3.289
        val trueLongitude = normalizeDegrees(
            meanAnomaly +
                (1.916 * sinDegrees(meanAnomaly)) +
                (0.020 * sinDegrees(2 * meanAnomaly)) +
                282.634,
        )

        var rightAscension = atanDegrees(0.91764 * tanDegrees(trueLongitude))
        rightAscension = normalizeDegrees(rightAscension)
        val longitudeQuadrant = floor(trueLongitude / 90.0) * 90.0
        val ascensionQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + longitudeQuadrant - ascensionQuadrant) / 15.0

        val sinDeclination = 0.39782 * sinDegrees(trueLongitude)
        val cosDeclination = cos(asin(sinDeclination))
        val cosHourAngle = (cosDegrees(90.833) - (sinDeclination * sinDegrees(latitude))) /
            (cosDeclination * cosDegrees(latitude))

        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) return null

        val hourAngle = acos(cosHourAngle).toDegrees() / 15.0
        val localMeanTime = hourAngle + rightAscension - (0.06571 * approximateTime) - 6.622
        val universalTime = normalizeHours(localMeanTime - longitudeHour)
        val seconds = (universalTime * 3600.0).toLong()

        return date
            .atStartOfDay(ZoneOffset.UTC)
            .plusSeconds(seconds)
            .toInstant()
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun normalizeDegrees(value: Double): Double {
        val result = value % 360.0
        return if (result < 0) result + 360.0 else result
    }

    private fun normalizeHours(value: Double): Double {
        val result = value % 24.0
        return if (result < 0) result + 24.0 else result
    }

    private fun sinDegrees(value: Double): Double = sin(Math.toRadians(value))

    private fun cosDegrees(value: Double): Double = cos(Math.toRadians(value))

    private fun tanDegrees(value: Double): Double = tan(Math.toRadians(value))

    private fun atanDegrees(value: Double): Double = Math.toDegrees(kotlin.math.atan(value))

    private fun Double.toDegrees(): Double = Math.toDegrees(this)
}
