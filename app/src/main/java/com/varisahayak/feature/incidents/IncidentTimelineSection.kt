package com.varisahayak.feature.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentEventKind
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.LifecycleStage
import com.varisahayak.domain.model.TimelineEvent
import com.varisahayak.domain.model.TimelineSeverity
import com.varisahayak.domain.usecase.MetricStage
import com.varisahayak.domain.usecase.ResponseInterval
import com.varisahayak.domain.usecase.ResponseMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The incident lifecycle, for a command or admin user.
 *
 * Reads the existing `incident_events` audit trail and nothing else. The point of the
 * screen is that somebody arriving at an incident cold can answer, in about five seconds:
 * what happened, how it was prioritised, who was chosen and why, when they accepted, when
 * they arrived, and how long the whole response took.
 *
 * The rail, the events and the metrics are three views of the same trail, so they cannot
 * disagree with each other — and none of them can disagree with the state machine, because
 * the rail is derived from [IncidentStatus] rather than from anything stored here.
 */
@Composable
fun IncidentTimelineSection(
    incident: Incident,
    events: List<TimelineEvent>,
    metrics: ResponseMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        LifecycleRail(status = incident.status)
        ResponseMetricsCard(metrics = metrics)
        LifecycleLog(events = events)
    }
}

// --- progress rail -------------------------------------------------------------------------

/**
 * The happy path, with the incident's position on it.
 *
 * Derived from [IncidentStatus] via [LifecycleStage], never from the event list: if the two
 * ever disagreed, the state machine is right and this must not imply otherwise. Exception
 * states are drawn as a branch rather than as a stage, because escalation and cancellation
 * are not progress.
 */
@Composable
private fun LifecycleRail(status: IncidentStatus) {
    val colors = VariTheme.colors
    val reached = LifecycleStage.reachedIndex(status)
    val isException = LifecycleStage.isException(status)

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LifecycleStage.entries.forEachIndexed { index, stage ->
                val done = index <= reached
                val tint = when {
                    isException && index == reached -> colors.critical
                    done -> colors.success
                    else -> colors.textMuted
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (done) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                    Text(
                        // Words as well as colour. A rail a user in sunlight can only read
                        // by hue is one they cannot read.
                        text = stage.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (done) colors.textPrimary else colors.textMuted,
                        fontWeight = if (index == reached) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                if (index < LifecycleStage.entries.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(2.dp)
                            .background(if (index < reached) colors.success else colors.textMuted),
                    )
                }
            }
        }

        if (isException) {
            Text(
                text = "Off the standard path — ${status.wireName.replace('_', ' ')}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.critical,
            )
        }
    }
}

// --- metrics -------------------------------------------------------------------------------

@Composable
private fun ResponseMetricsCard(metrics: ResponseMetrics) {
    val colors = VariTheme.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Response metrics",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                metrics.elapsedMillis?.let {
                    Text(
                        text = if (metrics.isResolved) "Closed in ${duration(it)}" else "Elapsed ${duration(it)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (metrics.isResolved) colors.success else colors.info,
                    )
                }
            }

            metrics.intervals.forEach { interval ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = interval.stage.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = interval.display(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            interval.millis != null -> colors.textPrimary
                            interval.inProgress -> colors.info
                            else -> colors.textMuted
                        },
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun MetricStage.label(): String = when (this) {
    MetricStage.TRIAGE -> "Triage time"
    MetricStage.ASSIGNMENT -> "Assignment time"
    MetricStage.ACCEPTANCE -> "Acceptance time"
    MetricStage.ARRIVAL -> "Arrival time"
    MetricStage.RESOLUTION -> "Resolution time"
}

/**
 * A measured interval, or an honest statement that it has not happened.
 *
 * "In progress" and "—" are deliberately different: the first means the clock is running,
 * the second means the stage never occurred and never will on this incident. Collapsing
 * them would make an incident resolved without a responder look like one still waiting.
 */
private fun ResponseInterval.display(): String = when {
    millis != null -> duration(millis)
    inProgress -> "In progress"
    else -> "—"
}

// --- event log -----------------------------------------------------------------------------

/**
 * The chronological trail.
 *
 * Newest first, because the question a command user asks on opening an incident is "what
 * just happened". The full history is one tap away rather than a scroll away — an incident
 * with forty events would otherwise bury its own summary.
 */
@Composable
private fun LifecycleLog(events: List<TimelineEvent>) {
    val colors = VariTheme.colors
    var expanded by remember { mutableStateOf(false) }

    val newestFirst = remember(events) { events.sortedByDescending { it.occurredAtEpochMillis } }
    val visible = if (expanded) newestFirst else newestFirst.take(COLLAPSED_EVENT_COUNT)

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(
            text = "Lifecycle",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )

        if (events.isEmpty()) {
            Text(
                text = "No lifecycle events recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            return@Column
        }

        visible.forEachIndexed { index, event ->
            TimelineRow(
                event = event,
                isLast = index == visible.lastIndex,
            )
        }

        if (newestFirst.size > COLLAPSED_EVENT_COUNT) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) {
                        "Show less"
                    } else {
                        "Show all ${newestFirst.size} events"
                    },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent, isLast: Boolean) {
    val colors = VariTheme.colors
    val tint = event.type.severity.tint()

    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        // The rail: a dot per event, joined by a line except at the end.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(colors.textMuted.copy(alpha = 0.4f)),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.type.title(event.rawType),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = clockTime(event.occurredAtEpochMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }

            event.detail()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            // A pending event is one this device wrote and has not pushed. Worth saying:
            // a command user must not read a local-only row as confirmed by the server.
            if (!event.synced) {
                Text(
                    text = "Pending sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                )
            }
        }
    }
}

/**
 * The line under an event title.
 *
 * Built from the structured columns the audit trail already has — actor, from, to, note —
 * rather than from anything new. Null when there is nothing worth a second line.
 */
private fun TimelineEvent.detail(): String? {
    val transition = when {
        fromValue != null && toValue != null -> "$fromValue → $toValue"
        toValue != null -> toValue
        else -> null
    }

    return listOfNotNull(
        actorName?.let { "by $it" },
        transition,
        note,
    ).joinToString(" · ").ifBlank { null }
}

private fun IncidentEventKind.title(rawType: String): String = when (this) {
    IncidentEventKind.INCIDENT_REPORTED -> "Incident reported"
    IncidentEventKind.INCIDENT_CREATED_OFFLINE -> "Created offline"
    IncidentEventKind.INCIDENT_SYNCED -> "Synced to server"
    IncidentEventKind.PRIORITY_ASSIGNED -> "Priority assigned"
    IncidentEventKind.PRIORITY_UPDATED -> "Priority updated"
    IncidentEventKind.AI_TRIAGE_COMPLETED -> "AI triage completed"
    IncidentEventKind.MANUAL_PRIORITY_OVERRIDE -> "Priority overridden"
    IncidentEventKind.MATCHING_STARTED -> "Responder matching started"
    IncidentEventKind.RESPONDER_MATCHED -> "Responder matched"
    IncidentEventKind.ASSIGNMENT_CREATED -> "Assignment created"
    IncidentEventKind.ASSIGNMENT_SENT -> "Assignment sent"
    IncidentEventKind.ASSIGNMENT_ACCEPTED -> "Assignment accepted"
    IncidentEventKind.ASSIGNMENT_REJECTED -> "Assignment rejected"
    IncidentEventKind.ASSIGNMENT_FAILED -> "No responder matched"
    IncidentEventKind.REASSIGNMENT_REQUIRED -> "Reassignment required"
    IncidentEventKind.RESPONDER_EN_ROUTE -> "Responder en route"
    IncidentEventKind.RESPONDER_ARRIVED -> "Responder arrived"
    IncidentEventKind.INCIDENT_IN_PROGRESS -> "Response in progress"
    IncidentEventKind.INCIDENT_ESCALATED -> "Incident escalated"
    IncidentEventKind.INCIDENT_RESOLVED -> "Incident resolved"
    IncidentEventKind.INCIDENT_CANCELLED -> "Incident cancelled"
    IncidentEventKind.SOS_TRIGGERED -> "SOS triggered"
    IncidentEventKind.ADMIN_NOTE_ADDED -> "Note added"
    IncidentEventKind.QR_RESOLVED -> "Location scanned"
    IncidentEventKind.STATUS_CHANGED -> "Status changed"
    // Shown rather than hidden. An audit trail that silently drops rows it does not
    // recognise is not an audit trail.
    IncidentEventKind.UNKNOWN -> rawType.replace('_', ' ').lowercase()
        .replaceFirstChar(Char::uppercase)
}

@Composable
private fun TimelineSeverity.tint(): Color {
    val colors = VariTheme.colors
    return when (this) {
        TimelineSeverity.CRITICAL -> colors.critical
        TimelineSeverity.WARNING -> colors.warning
        TimelineSeverity.ACTIVE -> colors.info
        TimelineSeverity.SUCCESS -> colors.success
        TimelineSeverity.INFO -> colors.info
        TimelineSeverity.MUTED -> colors.textMuted
    }
}

// --- formatting ----------------------------------------------------------------------------

private const val COLLAPSED_EVENT_COUNT = 6

private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())

private fun clockTime(epochMillis: Long): String =
    TIME_FORMAT.format(Date(epochMillis))

/** Human duration. Seconds below a minute, because triage is measured in seconds. */
private fun duration(millis: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    if (seconds < 60) return "${seconds}s"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    if (minutes < 60) return "${minutes}m"

    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val remainder = minutes - TimeUnit.HOURS.toMinutes(hours)
    return if (remainder == 0L) "${hours}h" else "${hours}h ${remainder}m"
}

private val RoundedCorner = RoundedCornerShape(8.dp)
