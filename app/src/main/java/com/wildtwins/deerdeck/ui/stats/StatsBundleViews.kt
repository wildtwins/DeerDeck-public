package com.wildtwins.deerdeck.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wildtwins.deerdeck.data.environment.ManualEnvironmentRetryEligibility
import com.wildtwins.deerdeck.ui.UiFormatters
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class StatsSection(val label: String) {
    Overview("Overview"),
    FieldIq("Field IQ"),
    Wildlife("Wildlife"),
    Compare("Compare"),
    Calendar("Calendar"),
    Health("Health"),
}

@Composable
internal fun StatsSectionTabs(
    selected: StatsSection,
    onSelected: (StatsSection) -> Unit,
) {
    ScrollableTabRow(
        selectedTabIndex = StatsSection.entries.indexOf(selected),
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        StatsSection.entries.forEach { section ->
            Tab(
                selected = section == selected,
                onClick = { onSelected(section) },
                text = { Text(section.label) },
            )
        }
    }
}

@Composable
internal fun WildlifeInsightsView(insights: List<WildlifeInsight>) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = insights.firstOrNull { it.speciesId == selectedId }
    if (selected != null) {
        WildlifeDetail(insight = selected, onBack = { selectedId = null })
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            title = "Wildlife details",
            subtitle = "Select a wildlife type to inspect its history, timing, places, conditions, and linked media.",
        )
        if (insights.isEmpty()) {
            BundleEmpty("Record a sighting to unlock wildlife detail pages.")
        } else {
            insights.forEach { insight ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = insight.speciesId },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(insight.color)),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(insight.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${insight.total} seen / ${insight.sessionsSeen} sessions / ${insight.activeDays} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open ${insight.name} details")
                    }
                }
            }
        }
    }
}

@Composable
private fun WildlifeDetail(
    insight: WildlifeInsight,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("All wildlife")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(insight.color)),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = insight.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        BundleMetricRow(
            leftLabel = "Total seen",
            leftValue = insight.total.toString(),
            rightLabel = "Sessions",
            rightValue = insight.sessionsSeen.toString(),
        )
        BundleMetricRow(
            leftLabel = "Field-hour rate",
            leftValue = insight.sightingsPerFieldHour.oneDecimalOrDash(),
            rightLabel = "Linked media",
            rightValue = insight.linkedMedia.toString(),
        )
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Recorded history", fontWeight = FontWeight.SemiBold)
                BundleLabelValue("First", UiFormatters.dayTime(insight.firstSeenAt))
                BundleLabelValue("Latest", UiFormatters.dayTime(insight.lastSeenAt))
                BundleLabelValue("Active days", insight.activeDays.toString())
            }
        }
        BreakdownCard("Time of day", insight.timePeriods)
        BreakdownCard("Last 12 months", insight.monthlyTotals)
        BreakdownCard("Top field locations", insight.locations, emptyText = "Name a field location to compare places.")
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Typical recorded conditions", fontWeight = FontWeight.SemiBold)
                BundleLabelValue("Temperature", insight.averageTemperatureF?.let { "${it.oneDecimal()} F" } ?: "-")
                BundleLabelValue("Pressure", insight.averagePressureInHg?.let { "${it.twoDecimals()} inHg" } ?: "-")
                BundleLabelValue(
                    "Moon illumination",
                    insight.averageMoonIlluminationPercent?.let { "${it.oneDecimal()}%" } ?: "-",
                )
                Text(
                    text = "Based on ${insight.conditionSamples} sessions with recorded conditions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class ComparisonBaseline(val label: String) {
    Previous("Previous period"),
    YearAgo("Same dates last year"),
}

@Composable
internal fun PeriodComparisonView(comparisons: List<PeriodComparison>) {
    var presetName by rememberSaveable { mutableStateOf(ComparisonPreset.ThirtyDays.name) }
    var baselineName by rememberSaveable { mutableStateOf(ComparisonBaseline.Previous.name) }
    val preset = ComparisonPreset.valueOf(presetName)
    val baselineMode = ComparisonBaseline.valueOf(baselineName)
    val comparison = comparisons.firstOrNull { it.preset == preset }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(
            title = "Period comparison",
            subtitle = "Measure field activity against the immediately preceding period or the same dates last year.",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComparisonPreset.entries.forEach { option ->
                FilterChip(
                    selected = preset == option,
                    onClick = { presetName = option.name },
                    label = { Text(option.label) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComparisonBaseline.entries.forEach { option ->
                FilterChip(
                    selected = baselineMode == option,
                    onClick = { baselineName = option.name },
                    label = { Text(option.label) },
                )
            }
        }
        if (comparison == null) {
            BundleEmpty("Comparison data is loading.")
        } else {
            val baseline = if (baselineMode == ComparisonBaseline.Previous) comparison.previous else comparison.yearAgo
            val baselineLabel = if (baselineMode == ComparisonBaseline.Previous) {
                comparison.previousLabel
            } else {
                comparison.yearAgoLabel
            }
            Text(
                text = "${comparison.currentLabel} compared with $baselineLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ComparisonTable(comparison.current, baseline)
        }
    }
}

@Composable
private fun ComparisonTable(
    current: PeriodSnapshot,
    baseline: PeriodSnapshot,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("Metric", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.SemiBold)
                Text("Now", modifier = Modifier.weight(0.7f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                Text("Change", modifier = Modifier.weight(0.9f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
            }
            ComparisonRow("Sightings", current.sightings.toDouble(), baseline.sightings.toDouble(), current.sightings.toString())
            ComparisonRow("Sessions", current.sessions.toDouble(), baseline.sessions.toDouble(), current.sessions.toString())
            ComparisonRow("Active days", current.activeDays.toDouble(), baseline.activeDays.toDouble(), current.activeDays.toString())
            ComparisonRow("Wildlife types", current.wildlifeTypes.toDouble(), baseline.wildlifeTypes.toDouble(), current.wildlifeTypes.toString())
            ComparisonRow("Field time", current.effortMinutes.toDouble(), baseline.effortMinutes.toDouble(), formatBundleMinutes(current.effortMinutes))
            ComparisonRow("Media", current.media.toDouble(), baseline.media.toDouble(), current.media.toString())
            ComparisonRow(
                "Seen per hour",
                current.sightingsPerHour ?: 0.0,
                baseline.sightingsPerHour ?: 0.0,
                current.sightingsPerHour.oneDecimalOrDash(),
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    current: Double,
    baseline: Double,
    currentText: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
        Text(currentText, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
        Text(
            text = changeText(current, baseline),
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun ObservationCalendarView(days: Map<LocalDate, ObservationCalendarDay>) {
    var monthName by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateName by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthName)
    val todayMonth = YearMonth.now()
    val firstDate = month.atDay(1)
    val leadingDays = firstDate.dayOfWeek.value % 7
    val gridStart = firstDate.minusDays(leadingDays.toLong())
    val gridDates = (0L..41L).map(gridStart::plusDays)
    val maxSightings = (1..month.lengthOfMonth())
        .maxOfOrNull { day -> days[month.atDay(day)]?.sightings ?: 0 }
        ?.coerceAtLeast(1)
        ?: 1
    val selectedDay = selectedDateName?.let(LocalDate::parse)?.let(days::get)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(
            title = "Observation calendar",
            subtitle = "Dark days had more sightings. Outlined days contain a session with no sightings.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    monthName = month.minusMonths(1).toString()
                    selectedDateName = null
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = {
                    monthName = month.plusMonths(1).toString()
                    selectedDateName = null
                },
                enabled = month < todayMonth,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        gridDates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    CalendarDayCell(
                        date = date,
                        day = days[date],
                        inMonth = YearMonth.from(date) == month,
                        selected = selectedDateName == date.toString(),
                        maxSightings = maxSightings,
                        onClick = { selectedDateName = date.toString() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (selectedDateName != null) {
            CalendarDayDetail(
                date = LocalDate.parse(selectedDateName),
                day = selectedDay,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    day: ObservationCalendarDay?,
    inMonth: Boolean,
    selected: Boolean,
    maxSightings: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = day?.sightings ?: 0
    val intensity = if (activity > 0) 0.18f + 0.62f * (activity / maxSightings.toFloat()) else 0f
    val background = when {
        !inMonth -> Color.Transparent
        activity > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = intensity.coerceAtMost(0.8f))
        day?.sessions?.let { it > 0 } == true -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        inMonth && (day?.zeroSightingSessions ?: 0) > 0 -> MaterialTheme.colorScheme.tertiary
        else -> Color.Transparent
    }
    val textColor = when {
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        intensity > 0.48f -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .aspectRatio(0.88f)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(enabled = inMonth, onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), color = textColor, style = MaterialTheme.typography.labelMedium)
        Text(
            text = activity.takeIf { it > 0 }?.toString() ?: "",
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CalendarDayDetail(
    date: LocalDate,
    day: ObservationCalendarDay?,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                fontWeight = FontWeight.SemiBold,
            )
            if (day == null) {
                Text("No activity recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BundleLabelValue("Sightings", day.sightings.toString())
                BundleLabelValue("Sessions", day.sessions.toString())
                BundleLabelValue("Wildlife types", day.wildlifeTypes.toString())
                BundleLabelValue("Field time", formatBundleMinutes(day.effortMinutes))
                BundleLabelValue("Media", day.media.toString())
                if (day.zeroSightingSessions > 0) {
                    BundleLabelValue("Zero-sighting sessions", day.zeroSightingSessions.toString())
                }
            }
        }
    }
}

@Composable
internal fun DataHealthView(
    health: DataHealthSummary,
    retryState: ConditionRetryUiState,
    onRetryConditions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(
            title = "Data health",
            subtitle = "Audit condition completeness and safely retry records that already contain their original GPS coordinates.",
        )
        BundleMetricRow(
            leftLabel = "Conditions complete",
            leftValue = health.completenessPercent?.let { "$it%" } ?: "-",
            rightLabel = "Complete sessions",
            rightValue = "${health.completeSessions}/${health.eligibleSessions}",
        )
        LinearProgressIndicator(
            progress = { (health.completenessPercent ?: 0) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HealthSummaryRow("All sessions", health.totalSessions.toString())
                HealthSummaryRow("Open sessions", health.openSessions.toString())
                HealthSummaryRow("Condition capture disabled", health.disabledSessions.toString())
                HealthSummaryRow("Source provenance stored", health.provenanceSessions.toString())
                HealthSummaryRow("Missing original GPS", health.missingLocationSessions.toString())
                HealthSummaryRow("Retry limit reached", health.retryLimitSessions.toString())
            }
        }
        Button(
            onClick = onRetryConditions,
            enabled = health.retryableSessions > 0 && !retryState.inProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (retryState.inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Default.CloudSync, contentDescription = null)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                if (retryState.inProgress) {
                    "Retrying ${retryState.processed}/${retryState.total}"
                } else {
                    "Retry ${health.retryableSessions} eligible sessions"
                },
            )
        }
        if (retryState.inProgress && retryState.total > 0) {
            LinearProgressIndicator(
                progress = { retryState.processed / retryState.total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
        retryState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (health.retryLimitSessions > 0) {
            Text(
                text = "${health.retryLimitSessions} sessions reached the retry limit. Their stored values remain unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (health.missingLocationSessions > 0) {
            Text(
                text = "Sessions without original GPS are not retried because today\u2019s location would corrupt their history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BreakdownCard("Most common missing fields", health.commonMissingFields)
        Text(
            text = "Incomplete sessions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (health.issues.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("Every eligible completed session has a full condition record.")
            }
        } else {
            health.issues.take(25).forEach { issue ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(UiFormatters.dayTime(issue.startedAt), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Conditions ${issue.capturedFields}/${issue.totalFields} / ${issue.missingFields.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = when (issue.retryEligibility) {
                                    ManualEnvironmentRetryEligibility.Eligible ->
                                        "Retry available / ${issue.attemptCount} prior attempts"
                                    ManualEnvironmentRetryEligibility.AttemptLimitReached ->
                                        "Retry limit reached after ${issue.attemptCount} attempts"
                                    ManualEnvironmentRetryEligibility.MissingOriginalGps ->
                                        "Original GPS unavailable / retry blocked"
                                    ManualEnvironmentRetryEligibility.Disabled ->
                                        "Condition capture disabled"
                                    ManualEnvironmentRetryEligibility.Complete ->
                                        "Condition record complete"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (health.issues.size > 25) {
                Text(
                    text = "Showing the 25 newest of ${health.issues.size} incomplete sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HealthSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BundleMetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BundleMetricCard(leftLabel, leftValue, Modifier.weight(1f))
        BundleMetricCard(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun BundleMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    counts: List<NamedCount>,
    emptyText: String = "No data recorded yet.",
) {
    val maxCount = counts.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (counts.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                counts.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.label, style = MaterialTheme.typography.bodySmall)
                            Text(item.count.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = { item.count / maxCount.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleLabelValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.35f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BundleEmpty(text: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun changeText(current: Double, baseline: Double): String {
    if (baseline == 0.0) return if (current == 0.0) "No change" else "New"
    val percent = ((current - baseline) / abs(baseline) * 100.0).roundToInt()
    return when {
        percent > 0 -> "+$percent%"
        percent < 0 -> "$percent%"
        else -> "No change"
    }
}

private fun formatBundleMinutes(minutes: Long): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
}

private fun Double?.oneDecimalOrDash(): String = this?.oneDecimal() ?: "-"

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun Double.twoDecimals(): String = String.format(Locale.US, "%.2f", this)
