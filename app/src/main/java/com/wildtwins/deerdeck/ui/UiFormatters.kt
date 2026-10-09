package com.wildtwins.deerdeck.ui

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object UiFormatters {
    private val dayFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun dayTime(epochMillis: Long): String = "${dayFormat.format(Date(epochMillis))} at ${timeFormat.format(Date(epochMillis))}"

    fun time(epochMillis: Long): String = timeFormat.format(Date(epochMillis))

    fun photoTimestamp(epochMillis: Long): String = fileFormat.format(Date(epochMillis))

    fun duration(startedAt: Long, endedAt: Long?): String {
        val end = endedAt ?: System.currentTimeMillis()
        val minutes = TimeUnit.MILLISECONDS.toMinutes((end - startedAt).coerceAtLeast(0))
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
    }
}
