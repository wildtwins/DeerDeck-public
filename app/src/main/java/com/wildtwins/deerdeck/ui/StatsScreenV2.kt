package com.wildtwins.deerdeck.ui.stats

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.ui.UiFormatters
import java.util.Locale

private val ChartPalette = listOf(
    0xFF0072B2,
    0xFFD55E00,
    0xFF009E73,
    0xFFCC79A7,
    0xFFE69F00,
    0xFF56B4E9,
    0xFF6A3D9A,
    0xFF8B1E3F,
)

private fun chartColor(index: Int): Color = Color(ChartPalette[index % ChartPalette.size])

@Composable
fun StatsScreenV2(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    StatsContentV2(
        state = state,
        onRetryConditions = {
            viewModel.retryIncompleteConditions(context)
        },
        onShareFieldBrief = { brief ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Withrow DeerDeck Field Brief")
                putExtra(Intent.EXTRA_TEXT, brief)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share field brief"))
        },
        modifier = modifier,
    )
}

@Composable
private fun StatsContentV2(
    state: StatsUiState,
    onRetryConditions: () -> Unit,
    onShareFieldBrief: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(StatsSection.Overview.name) }
    val selectedSection = StatsSection.valueOf(selectedSectionName)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.activeSession != null) {
                        "Live session running since ${UiFormatters.time(state.activeSession.startedAt)}"
                    } else {
                        "Your deck activity at a glance."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            StatsSectionTabs(
                selected = selectedSection,
                onSelected = { selectedSectionName = it.name },
            )
        }

        when (selectedSection) {
            StatsSection.Overview -> {
                item { PersonalActivityScoreCard(state.personalActivityScore) }
                item { YearAgoStatsCard(state.yearAgoContext) }
                item { SimilarDaysCard(state.similarDays) }
                item { BestConditionsCard(state.bestConditions) }
                item { FieldEffortCard(state.extendedStats.effort) }
                item { ObservationRhythmCard(state.extendedStats.rhythm) }
                item { WildlifeDiversityCard(state.extendedStats.diversity) }
                item { DatasetQualityCard(state.extendedStats.quality) }
                item {
                    ThirtyDayTrendCard(
                        trends = state.speciesTrends,
                        days = state.trendDays,
                    )
                }
                item { WildlifeShareCard(state.speciesCounts) }
                item { FieldLocationActivityCard(state.fieldLocationStats) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatCard("Sightings", state.totalSightings.toString(), Modifier.weight(1f))
                        StatCard("Sessions", state.completedSessions.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatCard("Media", state.totalMedia.toString(), Modifier.weight(1f))
                        StatCard("Top", state.topSpecies?.speciesName ?: "-", Modifier.weight(1f))
                    }
                }
                item { TopWildlifeCard(state.speciesCounts) }
                if (state.sessions.isEmpty()) item { EmptyStats() }
            }

            StatsSection.FieldIq -> item {
                FieldIntelligenceView(
                    intelligence = state.statsBundle.fieldIntelligence,
                    onShareBrief = onShareFieldBrief,
                )
            }

            StatsSection.Wildlife -> item {
                WildlifeInsightsView(state.statsBundle.wildlifeInsights)
            }

            StatsSection.Compare -> item {
                PeriodComparisonView(state.statsBundle.periodComparisons)
            }

            StatsSection.Calendar -> item {
                ObservationCalendarView(state.statsBundle.calendarDays)
            }

            StatsSection.Health -> item {
                DataHealthView(
                    health = state.statsBundle.dataHealth,
                    retryState = state.conditionRetry,
                    onRetryConditions = onRetryConditions,
                )
            }
        }
    }
}

@Composable
private fun FieldEffortCard(stats: FieldEffortStats) {
    StatsDetailCard(
        title = "Field effort",
        icon = Icons.Default.AccessTime,
        metrics = listOf(
            "Total observation time" to formatMinutes(stats.totalMinutes),
            "Average session" to (stats.averageMinutes?.let { formatMinutes(it) } ?: "-"),
            "Longest session" to formatMinutes(stats.longestMinutes),
            "Sightings per hour" to stats.sightingsPerHour.oneDecimal(),
            "Sightings per session" to stats.sightingsPerSession.oneDecimal(),
        ),
        note = "Rates use completed sessions with a recorded duration.",
    )
}

@Composable
private fun ObservationRhythmCard(stats: ObservationRhythmStats) {
    val peak = stats.peakPeriod?.let { period ->
        "$period / ${stats.peakPeriodSightings} seen"
    } ?: "-"
    StatsDetailCard(
        title = "Observation rhythm",
        icon = Icons.Default.DateRange,
        metrics = listOf(
            "Active days" to stats.activeDays.toString(),
            "Recent daily streak" to dayCount(stats.recentStreakDays),
            "Longest daily streak" to dayCount(stats.longestStreakDays),
            "Peak time block" to peak,
        ),
        note = "Time blocks are Dawn 4-8, Day 8-17, Dusk 17-21, and Night 21-4.",
    )
}

@Composable
private fun WildlifeDiversityCard(stats: WildlifeDiversityStats) {
    StatsDetailCard(
        title = "Wildlife diversity",
        icon = Icons.Default.Insights,
        metrics = listOf(
            "Wildlife recorded" to stats.recordedWildlife.toString(),
            "Effective diversity" to stats.effectiveDiversity.oneDecimal(),
            "Observation evenness" to stats.evennessPercent.percent(),
            "Top wildlife share" to stats.topWildlifeSharePercent.percent(),
            "One-time records" to stats.oneTimeRecords.toString(),
        ),
        note = "Effective diversity converts the diversity index into an intuitive equivalent number of equally common wildlife.",
    )
}

@Composable
private fun DatasetQualityCard(stats: DatasetQualityStats) {
    val completed = if (stats.eligibleConditionSessions > 0) {
        "${stats.completeConditionSessions}/${stats.eligibleConditionSessions}"
    } else {
        "-"
    }
    StatsDetailCard(
        title = "Dataset quality",
        icon = Icons.Default.CheckCircle,
        metrics = listOf(
            "Complete condition sets" to completed,
            "Average condition capture" to stats.averageConditionCompletenessPercent.percent(),
            "GPS coverage" to stats.gpsCoveragePercent.percent(),
            "Weather coverage" to stats.weatherCoveragePercent.percent(),
            "Sessions with media" to stats.mediaSessionPercent.percent(),
        ),
        note = "Coverage exposes missing data instead of treating unavailable fields as zero.",
    )
}

@Composable
private fun StatsDetailCard(
    title: String,
    icon: ImageVector,
    metrics: List<Pair<String, String>>,
    note: String,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            metrics.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours > 0 && remainder > 0 -> "${hours}h ${remainder}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun formatMinutes(minutes: Double): String = formatMinutes(minutes.toLong())

private fun Double?.oneDecimal(): String {
    return this?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
}

private fun Int?.percent(): String = this?.let { "$it%" } ?: "-"

private fun dayCount(days: Int): String = "$days " + if (days == 1) "day" else "days"

@Composable
private fun PersonalActivityScoreCard(score: PersonalActivityScore) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Personal activity score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (score.score == null) {
                Text(
                    text = score.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "${score.score}/100",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = score.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((score.score / 100f).coerceIn(0.02f, 1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = score.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (score.score != null) {
                Text(
                    text = "Match confidence ${score.confidencePercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SimilarDaysCard(context: SimilarDaysContext) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Similar days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (context.days.isEmpty()) {
                Text(
                    text = "More sessions with weather data are needed for a reliable match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Closest historical conditions to ${context.targetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                context.days.forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(day.dateLabel, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${day.similarityPercent}% match",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = day.conditions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = day.sightings,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BestConditionsCard(summary: BestConditionsSummary) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Best conditions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (summary.insights.isEmpty()) {
                Text(
                    text = "No completed session data yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Highest average sightings per completed session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.insights.forEach { insight ->
                    BestConditionRow(insight)
                }
            }
        }
    }
}

@Composable
private fun BestConditionRow(insight: BestConditionInsight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = insight.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${String.format(Locale.US, "%.1f", insight.averageSightings)} avg",
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
            Text(
                text = "${insight.totalSightings} seen / ${insight.sessions} " +
                    if (insight.sessions == 1) "session" else "sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun YearAgoStatsCard(context: YearAgoStatsContext) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "One year ago today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (context.sessions.isEmpty()) {
                Text(
                    text = "No session from ${context.dateLabel}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                val topSpecies = context.topSpecies?.let { "${it.speciesName} x${it.total}" } ?: "No wildlife"
                Text(
                    text = "${context.totalSightings} sightings / Top: $topSpecies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val conditions = context.firstSession.yearAgoConditionText()
                if (conditions.isNotBlank()) {
                    Text(
                        text = conditions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThirtyDayTrendCard(
    trends: List<SpeciesTrend>,
    days: List<TrendDay>,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "30-day wildlife trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (trends.isEmpty() || days.isEmpty()) {
                Text(
                    text = "No sightings in the last 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TrendChart(trends = trends, days = days)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trends.forEachIndexed { index, trend ->
                        SpeciesTrendLegendRow(trend, chartColor(index))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendChart(
    trends: List<SpeciesTrend>,
    days: List<TrendDay>,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = trends
        .maxOfOrNull { trend -> trend.totals.maxOrNull() ?: 0 }
        ?.coerceAtLeast(1)
        ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val left = 10.dp.toPx()
            val right = 10.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = 14.dp.toPx()
            val width = size.width - left - right
            val height = size.height - top - bottom
            val dayCount = days.size.coerceAtLeast(2)

            repeat(4) { index ->
                val y = top + height * (index / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(left + width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            trends.forEachIndexed { index, trend ->
                val lineColor = chartColor(index)
                val path = Path()
                trend.totals.forEachIndexed { index, total ->
                    val x = left + width * (index / (dayCount - 1f))
                    val y = top + height - (height * (total / maxValue.toFloat()))
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = days.first().label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last 30 days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = days.last().label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeciesTrendLegendRow(
    trend: SpeciesTrend,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.size(8.dp))
            Text(trend.speciesName)
        }
        Text(trend.total.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WildlifeShareCard(counts: List<SpeciesCount>) {
    val visibleCounts = counts
        .filter { it.total > 0 }
        .take(6)
    val maxCount = visibleCounts.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Wildlife share",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (visibleCounts.isEmpty()) {
                Text(
                    text = "No sightings yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                visibleCounts.forEachIndexed { index, count ->
                    WildlifeShareRow(
                        count = count,
                        color = chartColor(index),
                        fraction = count.total / maxCount.toFloat(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLocationActivityCard(locations: List<FieldLocationStats>) {
    val visibleLocations = locations.take(6)
    val maxActivity = visibleLocations.maxOfOrNull { maxOf(it.sightings, it.sessions) }
        ?.coerceAtLeast(1)
        ?: 1

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Activity by location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (visibleLocations.isEmpty()) {
                Text(
                    text = "No named locations yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                visibleLocations.forEachIndexed { index, location ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(location.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${location.sightings} seen / ${location.sessions} sessions / ${location.media} media",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        (maxOf(location.sightings, location.sessions) / maxActivity.toFloat())
                                            .coerceIn(0.04f, 1f),
                                    )
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(chartColor(index)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WildlifeShareRow(
    count: SpeciesCount,
    color: Color,
    fraction: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(count.speciesName, style = MaterialTheme.typography.bodyMedium)
            Text(count.total.toString(), fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun TopWildlifeCard(counts: List<SpeciesCount>) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Top Wildlife",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (counts.isEmpty()) {
                Text(
                    text = "No sightings yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                counts.take(5).forEach { count ->
                    SpeciesTotalRow(count)
                }
            }
        }
    }
}

@Composable
private fun SpeciesTotalRow(count: SpeciesCount) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(count.color)),
            )
            Spacer(Modifier.size(8.dp))
            Text(count.speciesName)
        }
        Text(count.total.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyStats() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = "Start a session to build your stats.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SessionSummary?.yearAgoConditionText(): String {
    if (this == null) return ""
    val temp = temperatureF?.let { "${String.format(Locale.US, "%.0f", it)}F" }
    val weather = listOfNotNull(weatherSummary, temp, windSummary)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val gpsLocation = if (latitude != null && longitude != null) {
        val coordinates = "${String.format(Locale.US, "%.4f", latitude)}, ${String.format(Locale.US, "%.4f", longitude)}"
        val accuracy = locationAccuracyMeters?.let { "±${String.format(Locale.US, "%.0f", it)}m" }
        val elevation = elevationFeet?.let { "Elev ${String.format(Locale.US, "%.0f", it)}ft" }
        listOfNotNull("GPS $coordinates", accuracy, elevation).joinToString(" ")
    } else {
        null
    }
    val location = listOfNotNull(
        fieldLocationName?.takeIf { it.isNotBlank() },
        gpsLocation,
    ).joinToString(" / ").takeIf { it.isNotBlank() }
    val atmosphere = listOfNotNull(
        pressureInHg?.let { "Pressure ${String.format(Locale.US, "%.2f", it)} inHg" },
        humidityPercent?.let { "Humidity ${String.format(Locale.US, "%.0f", it)}%" },
        dewPointF?.let { "Dew ${String.format(Locale.US, "%.0f", it)}F" },
        visibilityMiles?.let { "Visibility ${String.format(Locale.US, "%.1f", it)} mi" },
        precipitationProbabilityPercent?.let { "Precip ${String.format(Locale.US, "%.0f", it)}%" },
    ).joinToString(" ")
    val forecast = forecastSummary
        ?.takeIf { it.isNotBlank() && !it.equals(weatherSummary, ignoreCase = true) }
        ?.let { "Forecast $it" }
    val sunset = sunsetAt?.let { "Sunset ${UiFormatters.time(it)}" }
    val moon = moonPhase
        ?.takeIf { it.isNotBlank() }
        ?.let { phase ->
            val illumination = moonIlluminationPercent?.let { "${String.format(Locale.US, "%.0f", it)}%" }
            listOfNotNull("Moon $phase", illumination).joinToString(" ")
        }
    val notes = notes?.takeIf { it.isNotBlank() }
    return listOfNotNull(location, weather.takeIf { it.isNotBlank() }, atmosphere.takeIf { it.isNotBlank() }, forecast, sunset, moon, notes)
        .joinToString(" / ")
}
