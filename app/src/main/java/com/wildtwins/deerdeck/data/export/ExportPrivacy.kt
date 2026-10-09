package com.wildtwins.deerdeck.data.export

import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import kotlin.math.pow
import kotlin.math.round

data class ExportPrivacyOptions(
    val locationPrecision: SharedLocationPrecision = SharedLocationPrecision.Exact,
    val includeNamedFieldLocations: Boolean = true,
) {
    companion object {
        val Full = ExportPrivacyOptions()
    }
}

internal fun Double.forSharedLocation(precision: SharedLocationPrecision): Double? {
    return when (precision) {
        SharedLocationPrecision.Exact -> this
        SharedLocationPrecision.Approximate -> roundToDecimals(2)
        SharedLocationPrecision.Hidden -> null
    }
}

private fun Double.roundToDecimals(decimals: Int): Double {
    val scale = 10.0.pow(decimals)
    return round(this * scale) / scale
}
