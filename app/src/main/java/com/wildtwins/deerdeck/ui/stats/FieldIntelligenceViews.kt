package com.wildtwins.deerdeck.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wildtwins.deerdeck.ui.UiFormatters
import java.util.Locale

@Composable
internal fun FieldIntelligenceView(
    intelligence: FieldIntelligenceSummary,
    onShareBrief: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Field IQ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Ten local intelligence tools built from your own observation history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SmartFieldBriefCard(intelligence.fieldBrief, intelligence.shareText, onShareBrief)
        WeeklyWindowsCard(intelligence.bestWindows)
        PairingsCard(intelligence.pairings)
        LocationScorecardsCard(intelligence.locations)
        ConditionResponseCard(
            featureNumber = 5,
            title = "Weather response",
            subtitle = "Sightings per session by recorded sky condition.",
            icon = Icons.Default.Cloud,
            responses = intelligence.weatherResponse,
        )
        ConditionResponseCard(
            featureNumber = 6,
            title = "Pressure response",
            subtitle = "Activity across low, normal, and high barometric bands.",
            icon = Icons.Default.Speed,
            responses = intelligence.pressureResponse,
        )
        ConditionResponseCard(
            featureNumber = 7,
            title = "Moonlight response",
            subtitle = "Activity compared with recorded moon illumination.",
            icon = Icons.Default.NightsStay,
            responses = intelligence.moonResponse,
        )
        PersonalRecordsCard(intelligence.records)
        DiscoveryTimelineCard(intelligence.discoveries)
        ReturnWatchCard(intelligence.returnWatch)

        Text(
            text = "Field IQ finds patterns in your personal dataset. It reports sample sizes and does not claim a wildlife sighting is guaranteed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmartFieldBriefCard(
    brief: SmartFieldBrief,
    shareText: String,
    onShareBrief: (String) -> Unit,
) {
    IntelligenceCard(
        featureNumber = 1,
        title = "Smart field brief",
        subtitle = brief.contextLabel,
        icon = Icons.Default.AutoAwesome,
        action = {
            TextButton(
                onClick = { onShareBrief(shareText) },
                enabled = brief.candidates.isNotEmpty(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(5.dp))
                Text("Share")
            }
        },
    ) {
        Text(
            text = "${brief.confidenceLabel} / ${brief.matchedSessions} matched sessions",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (brief.candidates.isEmpty()) {
            IntelligenceEmpty("Complete a few sessions to build a personal field brief.")
        } else {
            brief.candidates.forEach { candidate ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WildlifeDot(candidate.color)
                        Spacer(Modifier.size(8.dp))
                        Text(candidate.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${candidate.patternScore}", color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { candidate.patternScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    Text(
                        text = "${candidate.matchedSightings} seen across ${candidate.sampleSessions} similar sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyWindowsCard(windows: List<WeeklyWindowRank>) {
    IntelligenceCard(
        featureNumber = 2,
        title = "Best weekly windows",
        subtitle = "Your strongest day and time combinations.",
        icon = Icons.Default.Schedule,
    ) {
        if (windows.isEmpty()) {
            IntelligenceEmpty("Completed sessions will reveal your strongest field windows.")
        } else {
            windows.forEachIndexed { index, window ->
                RankedRow(
                    rank = index + 1,
                    label = window.label,
                    value = "${window.sightingsPerSession.oneDecimal()} seen/session",
                    detail = "${window.sessions} sessions / ${window.sightings} sightings",
                )
            }
        }
    }
}

@Composable
private fun PairingsCard(pairings: List<WildlifePairing>) {
    IntelligenceCard(
        featureNumber = 3,
        title = "Wildlife pairings",
        subtitle = "Animals that repeatedly appear during the same sessions.",
        icon = Icons.Default.Hub,
    ) {
        if (pairings.isEmpty()) {
            IntelligenceEmpty("Pairings appear after multiple wildlife types share sessions.")
        } else {
            pairings.forEach { pairing ->
                MetricDetailRow(
                    label = "${pairing.first} + ${pairing.second}",
                    value = "${pairing.affinityPercent}% affinity",
                    detail = "Together in ${pairing.sessionsTogether} sessions",
                )
            }
        }
    }
}

@Composable
private fun LocationScorecardsCard(locations: List<LocationScorecard>) {
    IntelligenceCard(
        featureNumber = 4,
        title = "Location scorecards",
        subtitle = "Effort-normalized performance for named field locations.",
        icon = Icons.Default.LocationOn,
    ) {
        if (locations.isEmpty()) {
            IntelligenceEmpty("Save field-location names to compare your regular spots.")
        } else {
            locations.forEach { location ->
                MetricDetailRow(
                    label = location.name,
                    value = "${location.sightingsPerHour.oneDecimalOrDash()} seen/hour",
                    detail = buildString {
                        append("${location.sessions} sessions / ${location.wildlifeTypes} types")
                        location.bestTimeBlock?.let { append(" / best at $it") }
                        append(" / ${location.confidenceLabel.lowercase()}")
                    },
                )
            }
        }
    }
}

@Composable
private fun ConditionResponseCard(
    featureNumber: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    responses: List<ConditionResponse>,
) {
    IntelligenceCard(featureNumber, title, subtitle, icon) {
        if (responses.isEmpty()) {
            IntelligenceEmpty("More completed sessions with conditions are needed.")
        } else {
            val bestRate = responses.maxOf { it.sightingsPerSession }
            responses.forEach { response ->
                val best = response.sightingsPerSession == bestRate && bestRate > 0.0
                MetricDetailRow(
                    label = response.label,
                    value = "${response.sightingsPerSession.oneDecimal()} seen/session",
                    detail = "${response.sessions} sessions / ${response.sightings} sightings",
                    emphasized = best,
                )
            }
        }
    }
}

@Composable
private fun PersonalRecordsCard(records: List<RecordHighlight>) {
    IntelligenceCard(
        featureNumber = 8,
        title = "Personal records",
        subtitle = "The standout moments in your field history.",
        icon = Icons.Default.EmojiEvents,
    ) {
        if (records.isEmpty()) {
            IntelligenceEmpty("Your first completed session will start the record book.")
        } else {
            records.forEach { record ->
                MetricDetailRow(record.label, record.value, record.detail)
            }
        }
    }
}

@Composable
private fun DiscoveryTimelineCard(discoveries: DiscoverySummary) {
    IntelligenceCard(
        featureNumber = 9,
        title = "Discovery timeline",
        subtitle = "First-ever encounters across your observation history.",
        icon = Icons.Default.Explore,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IntelligenceMetric("All time", discoveries.totalDiscovered.toString(), Modifier.weight(1f))
            IntelligenceMetric("Last 30 days", discoveries.newLast30Days.toString(), Modifier.weight(1f))
            IntelligenceMetric("This year", discoveries.newThisYear.toString(), Modifier.weight(1f))
        }
        discoveries.averageDaysBetweenDiscoveries?.let { average ->
            Text(
                text = "A new wildlife type has appeared every ${average.oneDecimal()} days on average.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (discoveries.recentDiscoveries.isEmpty()) {
            IntelligenceEmpty("First sightings will build your discovery timeline.")
        } else {
            discoveries.recentDiscoveries.forEach { discovery ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WildlifeDot(discovery.color)
                    Spacer(Modifier.size(8.dp))
                    Text(discovery.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(
                        text = UiFormatters.dayTime(discovery.firstSeenAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReturnWatchCard(items: List<ReturnWatchItem>) {
    IntelligenceCard(
        featureNumber = 10,
        title = "Return watch",
        subtitle = "Wildlife running later than its usual observed return rhythm.",
        icon = Icons.Default.Replay,
    ) {
        if (items.isEmpty()) {
            IntelligenceEmpty("Nothing is currently past its established return rhythm.")
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    WildlifeDot(item.color)
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Usually every ${item.usualGapDays.oneDecimal()} days / last seen ${item.daysSinceLastSeen} days ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "+${item.overdueDays}d",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntelligenceCard(
    featureNumber: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$featureNumber. $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun RankedRow(
    rank: Int,
    label: String,
    value: String,
    detail: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(rank.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricDetailRow(
    label: String,
    value: String,
    detail: String,
    emphasized: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                text = value,
                color = if (emphasized) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.End,
            )
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntelligenceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WildlifeDot(color: Long) {
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

@Composable
private fun IntelligenceEmpty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun Double?.oneDecimalOrDash(): String = this?.oneDecimal() ?: "-"
