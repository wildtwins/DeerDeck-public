package com.wildtwins.deerdeck.data.environment

import kotlin.math.PI
import kotlin.math.cos

object MoonPhaseCalculator {
    private const val SynodicMonthDays = 29.530588853
    private const val KnownNewMoonJulianDay = 2451550.1
    private const val UnixEpochJulianDay = 2440587.5
    private const val MillisPerDay = 86_400_000.0

    fun phase(timestampMillis: Long): MoonPhaseSnapshot {
        val julianDay = timestampMillis / MillisPerDay + UnixEpochJulianDay
        val age = positiveModulo(julianDay - KnownNewMoonJulianDay, SynodicMonthDays)
        val fraction = age / SynodicMonthDays
        val illumination = ((1.0 - cos(2.0 * PI * fraction)) / 2.0) * 100.0

        return MoonPhaseSnapshot(
            phase = phaseName(age),
            illuminationPercent = illumination,
        )
    }

    private fun phaseName(age: Double): String {
        return when {
            age < 1.84566 -> "New moon"
            age < 5.53699 -> "Waxing crescent"
            age < 9.22831 -> "First quarter"
            age < 12.91963 -> "Waxing gibbous"
            age < 16.61096 -> "Full moon"
            age < 20.30228 -> "Waning gibbous"
            age < 23.99361 -> "Last quarter"
            age < 27.68493 -> "Waning crescent"
            else -> "New moon"
        }
    }

    private fun positiveModulo(value: Double, mod: Double): Double {
        val remainder = value % mod
        return if (remainder < 0) remainder + mod else remainder
    }
}

data class MoonPhaseSnapshot(
    val phase: String,
    val illuminationPercent: Double,
)
