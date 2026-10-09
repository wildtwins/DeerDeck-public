package com.wildtwins.deerdeck.ui.history

import com.wildtwins.deerdeck.data.SessionTagCodec
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SessionWildlifeCount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class HistoryRange(val label: String, val days: Long?) {
    All("All", null),
    ThirtyDays("30 days", 30),
    OneYear("1 year", 365),
}

private val SearchDateFormatter = DateTimeFormatter
    .ofPattern("EEEE MMMM d yyyy M/d/yy", Locale.US)

internal fun filterHistorySessions(
    sessions: List<SessionSummary>,
    wildlifeCounts: List<SessionWildlifeCount>,
    query: String,
    range: HistoryRange,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<SessionSummary> {
    val cutoff = range.days?.let { days -> now - days * 24 * 60 * 60 * 1_000L }
    val normalizedQuery = query.trim().lowercase(Locale.US)
    val wildlifeBySession = wildlifeCounts
        .groupBy { it.sessionId }
        .mapValues { (_, counts) -> counts.joinToString(" ") { it.wildlifeName } }

    return sessions.filter { session ->
        val inRange = cutoff == null || session.startedAt >= cutoff
        if (!inRange) return@filter false
        if (normalizedQuery.isBlank()) return@filter true

        val searchableText = buildList {
            add(session.title)
            add(session.notes)
            add(session.fieldLocationName)
            add(session.weatherSummary)
            add(session.windSummary)
            add(session.forecastSummary)
            add(session.moonPhase)
            add(session.environmentStatus)
            add(session.profileKey.replace('_', ' '))
            add(wildlifeBySession[session.id])
            addAll(SessionTagCodec.decode(session.tags))
            add(SearchDateFormatter.format(Instant.ofEpochMilli(session.startedAt).atZone(zoneId)))
        }.filterNotNull().joinToString(" ").lowercase(Locale.US)

        normalizedQuery.split(Regex("\\s+"))
            .all { term -> searchableText.contains(term) }
    }
}
