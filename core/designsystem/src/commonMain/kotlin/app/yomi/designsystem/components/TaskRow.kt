package app.yomi.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.i18n.format
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.model.EntryStatus
import app.yomi.model.PlanEntry
import app.yomi.model.Priority

/**
 * A single task on the timeline.
 *
 * Everything the user needs to judge the item at a glance lives on one line:
 * its emoji, its title, its window, how late it was, and how far through its
 * sub-missions it is. Tapping the badge completes it; tapping the row opens it.
 */
@Composable
fun TaskRow(
    entry: PlanEntry,
    use24h: Boolean,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    expanded: Boolean = false,
    onToggleDone: () -> Unit = {},
    onClick: () -> Unit = {},
    onToggleSubtask: (String) -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    val strings = LocalStrings.current
    val accents = YomiTheme.accents
    val spacing = YomiTheme.spacing
    val showEmoji = YomiTheme.appearance.showEmojis

    val statusColor = when (entry.status) {
        EntryStatus.Done -> accents.success
        EntryStatus.Partial -> accents.warning
        EntryStatus.Missed -> accents.missed
        EntryStatus.Skipped, EntryStatus.Deferred -> accents.skipped
        EntryStatus.InProgress -> MaterialTheme.colorScheme.primary
        EntryStatus.Pending -> MaterialTheme.colorScheme.outline
    }
    val faded = entry.status == EntryStatus.Skipped || entry.status == EntryStatus.Deferred

    YomiCard(
        modifier = modifier.fillMaxWidth(),
        accent = accent,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                status = entry.status,
                color = statusColor,
                emoji = if (showEmoji) entry.emoji else null,
                onClick = onToggleDone
            )
            Spacer(Modifier.width(spacing.medium))

            Column(Modifier.weight(1f).alpha(if (faded) 0.6f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (entry.priority >= Priority.High) FontWeight.SemiBold else FontWeight.Normal,
                        textDecoration = if (entry.status == EntryStatus.Done) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.priority == Priority.Critical) {
                        Spacer(Modifier.width(spacing.tiny))
                        Text("‼️", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    if (entry.timing.isScheduled) {
                        Text(
                            text = Fmt.timeRange(entry.timing, use24h, strings),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LatenessLabel(entry, statusColor)
                    if (entry.hasSubtasks) {
                        Text(
                            text = "${entry.doneSubtasks}/${entry.subtasks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entry.scoring.quantityTarget?.let { target ->
                        Text(
                            text = "${Fmt.amount(entry.actualQuantity ?: 0.0)}/${Fmt.amount(target, entry.scoring.quantityUnit)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.status == EntryStatus.Skipped && entry.skipReason.isNotBlank()) {
                        Text(
                            text = entry.skipReason,
                            style = MaterialTheme.typography.labelMedium,
                            color = accents.skipped,
                            maxLines = 1
                        )
                    }
                }

                if (entry.hasSubtasks) {
                    Spacer(Modifier.height(spacing.tiny))
                    ThinProgress(
                        progress = (entry.subtaskRatio() ?: 0.0).toFloat(),
                        color = statusColor,
                        height = 4.dp
                    )
                }
            }

            trailing?.invoke()
        }

        AnimatedVisibility(visible = expanded && entry.hasSubtasks) {
            Column(Modifier.padding(top = spacing.small)) {
                entry.subtasks.forEach { subtask ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onToggleSubtask(subtask.id) }
                            .padding(vertical = spacing.tiny, horizontal = spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (subtask.done) accents.success
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (subtask.done) {
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.onSuccess
                                )
                            }
                        }
                        Spacer(Modifier.width(spacing.small))
                        Text(
                            text = subtask.title,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
                            color = if (subtask.done) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (subtask.optional) {
                            Spacer(Modifier.width(spacing.tiny))
                            Text(
                                "(${strings.optional})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The round tick target on the leading edge of every task row. */
@Composable
private fun StatusBadge(
    status: EntryStatus,
    color: Color,
    emoji: String?,
    onClick: () -> Unit
) {
    val motion = YomiTheme.motion
    val fill by animateFloatAsState(
        targetValue = if (status == EntryStatus.Done) 1f else 0f,
        animationSpec = tween(if (motion.enabled) motion.fast else 0),
        label = "statusBadge"
    )
    val background = color.copy(alpha = 0.16f + 0.5f * fill)

    Box(
        modifier = Modifier
            .size(BADGE_SIZE.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val glyph = when (status) {
            EntryStatus.Done -> "✓"
            EntryStatus.Partial -> "◐"
            EntryStatus.Missed -> "✕"
            EntryStatus.Skipped -> "–"
            EntryStatus.Deferred -> "»"
            EntryStatus.InProgress -> "▶"
            EntryStatus.Pending -> emoji ?: ""
        }
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (status == EntryStatus.Pending) {
                MaterialTheme.colorScheme.onSurface
            } else {
                color
            }
        )
    }
}

@Composable
private fun LatenessLabel(entry: PlanEntry, statusColor: Color) {
    val strings = LocalStrings.current
    val due = entry.timing.dueAt ?: return
    val completed = entry.completedAt ?: return
    if (!entry.status.isSuccessful) return
    val late = (completed.hour * 60 + completed.minute) - (due.hour * 60 + due.minute)
    if (late <= 0) {
        Text(
            text = strings.onTime,
            style = MaterialTheme.typography.labelMedium,
            color = YomiTheme.accents.success
        )
    } else {
        Text(
            text = strings.minutesLate.format(late.toString()),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor
        )
    }
}

private const val BADGE_SIZE = 42
