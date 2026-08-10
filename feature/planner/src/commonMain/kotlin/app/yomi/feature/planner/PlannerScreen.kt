package app.yomi.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.components.ConfirmDialog
import app.yomi.designsystem.components.EmptyState
import app.yomi.designsystem.components.SectionHeader
import app.yomi.designsystem.components.StatTile
import app.yomi.designsystem.components.TaskRow
import app.yomi.designsystem.components.TextPromptDialog
import app.yomi.designsystem.components.YomiCard
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.components.scoreColor
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.i18n.format
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.domain.plan.CopyOptions
import app.yomi.model.TaskDefinition
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * The month view, the day preview beside it, and the two bulk operations that
 * make a repeating life cheap to describe: copy a day, and save it as a
 * template you can stamp anywhere.
 */
@Composable
fun PlannerScreen(
    state: PlannerUiState,
    modifier: Modifier = Modifier,
    onSelectTab: (PlannerTab) -> Unit = {},
    onShiftMonth: (Int) -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onGoToToday: () -> Unit = {},
    onCopy: (CopyTarget, CopyOptions) -> Unit = { _, _ -> },
    onSaveTemplate: (String) -> Unit = {},
    onApplyTemplate: (String) -> Unit = {},
    onDeleteTemplate: (String) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onEditTask: (TaskDefinition?) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
    onToggleArchive: (String, Boolean) -> Unit = { _, _ -> },
    onToggleDone: (String) -> Unit = {}
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing
    var copyVisible by remember { mutableStateOf(false) }
    var templateNameVisible by remember { mutableStateOf(false) }
    var deleteTaskTarget by remember { mutableStateOf<TaskDefinition?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = spacing.screenPadding)) {
        Spacer(Modifier.height(spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            PlannerTab.entries.forEach { tab ->
                YomiChip(
                    label = when (tab) {
                        PlannerTab.Calendar -> strings.month
                        PlannerTab.Library -> strings.library
                        PlannerTab.Templates -> strings.templates
                    },
                    selected = state.tab == tab,
                    onClick = { onSelectTab(tab) }
                )
            }
        }
        Spacer(Modifier.height(spacing.medium))

        when (state.tab) {
            PlannerTab.Calendar -> CalendarPane(
                state = state,
                onShiftMonth = onShiftMonth,
                onSelectDate = onSelectDate,
                onGoToToday = onGoToToday,
                onOpenCopy = { copyVisible = true },
                onSaveTemplate = { templateNameVisible = true },
                onOpenDay = onOpenDay,
                onToggleDone = onToggleDone
            )

            PlannerTab.Library -> LibraryPane(
                state = state,
                onEditTask = onEditTask,
                onDeleteTask = { deleteTaskTarget = it },
                onToggleArchive = onToggleArchive
            )

            PlannerTab.Templates -> TemplatePane(
                state = state,
                onApply = onApplyTemplate,
                onDelete = onDeleteTemplate,
                onSaveCurrent = { templateNameVisible = true }
            )
        }
    }

    if (copyVisible) {
        CopyDayDialog(
            sourceDate = state.selectedDate,
            firstDayOfWeek = state.settings.general.firstDayOfWeek,
            onConfirm = onCopy,
            onDismiss = { copyVisible = false }
        )
    }

    if (templateNameVisible) {
        TextPromptDialog(
            title = strings.saveAsTemplate,
            label = strings.templateName,
            onConfirm = onSaveTemplate,
            onDismiss = { templateNameVisible = false }
        )
    }

    deleteTaskTarget?.let { task ->
        ConfirmDialog(
            title = strings.delete,
            message = task.title,
            destructive = true,
            onConfirm = { onDeleteTask(task.id) },
            onDismiss = { deleteTaskTarget = null }
        )
    }
}

@Composable
private fun CalendarPane(
    state: PlannerUiState,
    onShiftMonth: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onGoToToday: () -> Unit,
    onOpenCopy: () -> Unit,
    onSaveTemplate: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onToggleDone: (String) -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    LazyColumn(
        contentPadding = PaddingValues(bottom = spacing.xxlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.large)
    ) {
        item("monthHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${strings.monthNames.getOrElse(state.month.month.number - 1) { "" }} ${state.month.year}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${strings.averageScore}: ${Fmt.score(state.monthAverage)} · " +
                            "${strings.perfectDays}: ${state.monthPerfectDays}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onShiftMonth(-1) }) { Text("‹") }
                TextButton(onClick = onGoToToday) { Text(strings.today) }
                TextButton(onClick = { onShiftMonth(1) }) { Text("›") }
            }
        }

        item("grid") {
            YomiCard {
                Row(Modifier.fillMaxWidth()) {
                    state.weekdayHeaders.forEach { day ->
                        Text(
                            text = Fmt.weekdayShort(day, strings),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(spacing.tiny))
                state.cells.chunked(DAYS_PER_WEEK).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { cell ->
                            CalendarDay(
                                cell = cell,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectDate(cell.date) }
                            )
                        }
                    }
                }
            }
        }

        item("dayActions") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(onClick = onOpenCopy, modifier = Modifier.weight(1f)) { Text(strings.copyDay) }
                OutlinedButton(onClick = onSaveTemplate, modifier = Modifier.weight(1f)) {
                    Text(strings.saveAsTemplate)
                }
            }
        }

        item("daySummary") {
            YomiCard(onClick = { onOpenDay(state.selectedDate) }) {
                SectionHeader(
                    title = Fmt.dateWithWeekday(state.selectedDate, strings),
                    subtitle = strings.tasksDoneOf.format(
                        state.selectedPlan.entries.count { it.status == app.yomi.model.EntryStatus.Done }.toString(),
                        state.selectedPlan.entries.size.toString()
                    )
                )
                state.selectedScore?.let { score ->
                    Spacer(Modifier.height(spacing.medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        StatTile(
                            value = Fmt.score(score.score),
                            label = strings.todayScore,
                            modifier = Modifier.weight(1f),
                            emoji = score.grade.emoji
                        )
                        StatTile(
                            value = score.tasksDone.toString(),
                            label = strings.statusDone,
                            modifier = Modifier.weight(1f),
                            accent = YomiTheme.accents.success
                        )
                        StatTile(
                            value = score.tasksMissed.toString(),
                            label = strings.statusMissed,
                            modifier = Modifier.weight(1f),
                            accent = YomiTheme.accents.missed
                        )
                    }
                }
            }
        }

        if (state.selectedPlan.entries.isEmpty()) {
            item("emptyDay") {
                EmptyState(emoji = "🗓️", title = strings.emptyDayTitle, body = strings.emptyDayBody)
            }
        } else {
            items(state.selectedPlan.entries, key = { it.id }) { entry ->
                TaskRow(
                    entry = entry,
                    use24h = state.settings.general.use24HourClock,
                    accent = state.categories[entry.categoryId]?.let { Color(it.colorArgb.toInt()) },
                    onToggleDone = { onToggleDone(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun CalendarDay(
    cell: CalendarCell,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accents = YomiTheme.accents
    val ratio = cell.score?.let { (it / MAX_SCORE).coerceIn(0.0, 1.0).toFloat() }
    val fill = when {
        ratio != null -> scoreColor(ratio, accents.scoreLow, accents.scoreMid, accents.scoreHigh)
            .copy(alpha = 0.28f + 0.4f * ratio)

        cell.plannedCount > 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .height(CELL_HEIGHT.dp)
            .clip(RoundedCornerShape(CELL_CORNER.dp))
            .background(fill)
            .border(
                width = if (cell.isSelected) 2.dp else if (cell.isToday) 1.dp else 0.dp,
                color = when {
                    cell.isSelected -> MaterialTheme.colorScheme.primary
                    cell.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(CELL_CORNER.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.date.day.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (cell.inMonth) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }
            )
            if (cell.plannedCount > 0) {
                Text(
                    text = "${cell.doneCount}/${cell.plannedCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LibraryPane(
    state: PlannerUiState,
    onEditTask: (TaskDefinition?) -> Unit,
    onDeleteTask: (TaskDefinition) -> Unit,
    onToggleArchive: (String, Boolean) -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    LazyColumn(
        contentPadding = PaddingValues(bottom = spacing.xxlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title = strings.library, modifier = Modifier.weight(1f))
                Button(onClick = { onEditTask(null) }) { Text(strings.newTask) }
            }
        }

        if (state.tasks.isEmpty()) {
            item("empty") {
                EmptyState(emoji = "📋", title = strings.library, body = strings.emptyDayBody)
            }
        }

        items(state.tasks, key = { it.id }) { task ->
            YomiCard(
                accent = state.categories[task.categoryId]?.let { Color(it.colorArgb.toInt()) },
                onClick = { onEditTask(task) },
                shape = MaterialTheme.shapes.medium
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(spacing.medium))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = buildString {
                                append(Fmt.timeRange(task.timing, state.settings.general.use24HourClock, strings))
                                append(" · ")
                                append(recurrenceLabel(task, strings))
                                append(" · ")
                                append("${task.scoring.points} ${strings.points}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onToggleArchive(task.id, !task.archived) }) {
                        Text(if (task.archived) strings.restore else strings.archive)
                    }
                    TextButton(onClick = { onDeleteTask(task) }) { Text("🗑") }
                }
            }
        }
    }
}

@Composable
private fun TemplatePane(
    state: PlannerUiState,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCurrent: () -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    LazyColumn(
        contentPadding = PaddingValues(bottom = spacing.xxlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = strings.templates,
                    subtitle = Fmt.dateWithWeekday(state.selectedDate, strings),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSaveCurrent) { Text(strings.saveAsTemplate) }
            }
        }

        if (state.templates.isEmpty()) {
            item("empty") {
                EmptyState(emoji = "🗂️", title = strings.noTemplates, body = strings.emptyDayBody)
            }
        }

        items(state.templates, key = { it.id }) { template ->
            YomiCard(shape = MaterialTheme.shapes.medium) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(template.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(spacing.medium))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${template.entries.size} · ${strings.timeline}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { onApply(template.id) }) { Text(strings.apply) }
                    TextButton(onClick = { onDelete(template.id) }) { Text("🗑") }
                }
            }
        }
    }
}

private fun recurrenceLabel(task: TaskDefinition, strings: app.yomi.designsystem.i18n.Strings): String =
    when (val recurrence = task.schedule.recurrence) {
        is app.yomi.model.Recurrence.Daily -> strings.repeatDaily
        is app.yomi.model.Recurrence.Weekly ->
            recurrence.days.joinToString(" ") { Fmt.weekdayShort(it, strings) }

        is app.yomi.model.Recurrence.MonthlyByDay -> strings.repeatMonthly
        is app.yomi.model.Recurrence.MonthlyByWeekday -> strings.repeatMonthly
        is app.yomi.model.Recurrence.EveryNDays -> strings.everyNDays.format(recurrence.n.toString())
        is app.yomi.model.Recurrence.Once -> strings.repeatOnce
        is app.yomi.model.Recurrence.Dates -> strings.copyPickDates
        app.yomi.model.Recurrence.Never -> strings.repeatNever
    }

private const val DAYS_PER_WEEK = 7
private const val CELL_CORNER = 10
private const val CELL_HEIGHT = 62
private const val MAX_SCORE = 100.0
