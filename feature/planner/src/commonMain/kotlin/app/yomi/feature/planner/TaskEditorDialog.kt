package app.yomi.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.components.SettingRow
import app.yomi.designsystem.components.SoftDivider
import app.yomi.designsystem.components.TimePickerDialog
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.components.YomiDialog
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.model.Category
import app.yomi.model.EnergyLevel
import app.yomi.model.Priority
import app.yomi.model.Recurrence
import app.yomi.model.RecurrenceRule
import app.yomi.model.SubtaskDefinition
import app.yomi.model.SubtaskRule
import app.yomi.model.TaskDefinition
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import app.yomi.model.weekdaysStartingFrom
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private enum class TimingKind { Fixed, Window, Deadline, Anytime }
private enum class RepeatKind { Daily, Weekly, Monthly, EveryN, Once, Never }

/**
 * The full blueprint editor.
 *
 * Everything the scoring engine can read is editable here — timing, repetition,
 * sub-missions, weight, partial credit, punctuality — because a scoring system
 * you cannot tune is a scoring system you end up arguing with.
 */
@Composable
fun TaskEditorDialog(
    original: TaskDefinition?,
    categories: List<Category>,
    firstDayOfWeek: DayOfWeek,
    use24h: Boolean,
    today: LocalDate,
    newId: () -> String,
    onSave: (TaskDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var emoji by remember { mutableStateOf(original?.emoji ?: "✨") }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var categoryId by remember { mutableStateOf(original?.categoryId) }
    var priority by remember { mutableStateOf(original?.priority ?: Priority.Normal) }
    var energy by remember { mutableStateOf(original?.energy ?: EnergyLevel.Medium) }

    val originalTiming = original?.timing ?: TaskTiming.Anytime
    var timingKind by remember {
        mutableStateOf(
            when (originalTiming) {
                is TaskTiming.Fixed -> TimingKind.Fixed
                is TaskTiming.Window -> TimingKind.Window
                is TaskTiming.Deadline -> TimingKind.Deadline
                TaskTiming.Anytime -> TimingKind.Anytime
            }
        )
    }
    var startTime by remember { mutableStateOf(originalTiming.startsAt ?: LocalTime(9, 0)) }
    var endTime by remember { mutableStateOf(originalTiming.dueAt ?: LocalTime(10, 0)) }
    var duration by remember { mutableStateOf((originalTiming.plannedMinutes ?: 30).toString()) }

    val originalRecurrence = original?.schedule?.recurrence ?: Recurrence.Daily()
    var repeatKind by remember {
        mutableStateOf(
            when (originalRecurrence) {
                is Recurrence.Daily -> RepeatKind.Daily
                is Recurrence.Weekly -> RepeatKind.Weekly
                is Recurrence.MonthlyByDay, is Recurrence.MonthlyByWeekday -> RepeatKind.Monthly
                is Recurrence.EveryNDays -> RepeatKind.EveryN
                is Recurrence.Once -> RepeatKind.Once
                else -> RepeatKind.Never
            }
        )
    }
    var weekdays by remember {
        mutableStateOf((originalRecurrence as? Recurrence.Weekly)?.days ?: emptySet())
    }
    var everyN by remember {
        mutableStateOf(((originalRecurrence as? Recurrence.EveryNDays)?.n ?: 2).toString())
    }

    val subtasks = remember {
        (original?.subtasks ?: emptyList()).toMutableStateList()
    }
    var newSubtask by remember { mutableStateOf("") }

    var points by remember { mutableStateOf((original?.scoring?.points ?: DEFAULT_POINTS).toFloat()) }
    var subtaskRule by remember { mutableStateOf(original?.scoring?.subtaskRule ?: SubtaskRule.Weighted) }
    var allowPartial by remember { mutableStateOf(original?.scoring?.allowPartial ?: true) }
    var punctualityTracked by remember { mutableStateOf(original?.scoring?.punctualityTracked ?: true) }
    var excluded by remember { mutableStateOf(original?.scoring?.excludedFromScore ?: false) }
    var quantityTarget by remember {
        mutableStateOf(original?.scoring?.quantityTarget?.let { Fmt.amount(it) }.orEmpty())
    }
    var quantityUnit by remember { mutableStateOf(original?.scoring?.quantityUnit.orEmpty()) }

    var startPicker by remember { mutableStateOf(false) }
    var endPicker by remember { mutableStateOf(false) }

    YomiDialog(
        title = if (original == null) strings.newTask else strings.editTask,
        confirmEnabled = title.isNotBlank(),
        onConfirm = {
            val timing = when (timingKind) {
                TimingKind.Fixed -> TaskTiming.Fixed(startTime, endTime)
                TimingKind.Window -> TaskTiming.Window(
                    earliest = startTime,
                    latest = endTime,
                    durationMinutes = duration.toIntOrNull()?.coerceIn(1, MAX_DURATION) ?: DEFAULT_DURATION
                )

                TimingKind.Deadline -> TaskTiming.Deadline(endTime)
                TimingKind.Anytime -> TaskTiming.Anytime
            }
            val recurrence = when (repeatKind) {
                RepeatKind.Daily -> Recurrence.Daily()
                RepeatKind.Weekly -> Recurrence.Weekly(
                    weekdays.ifEmpty { setOf(today.dayOfWeek) }
                )

                RepeatKind.Monthly -> Recurrence.MonthlyByDay(setOf(today.day))
                RepeatKind.EveryN -> Recurrence.EveryNDays(
                    n = everyN.toIntOrNull()?.coerceIn(1, MAX_INTERVAL) ?: 2,
                    anchor = original?.schedule?.startDate ?: today
                )

                RepeatKind.Once -> Recurrence.Once(today)
                RepeatKind.Never -> Recurrence.Never
            }

            onSave(
                TaskDefinition(
                    id = original?.id ?: newId(),
                    title = title.trim(),
                    emoji = emoji.ifBlank { "✨" },
                    notes = notes,
                    categoryId = categoryId,
                    priority = priority,
                    energy = energy,
                    timing = timing,
                    schedule = RecurrenceRule(
                        recurrence = recurrence,
                        startDate = original?.schedule?.startDate ?: today,
                        endDate = original?.schedule?.endDate,
                        exceptions = original?.schedule?.exceptions ?: emptySet(),
                        additions = original?.schedule?.additions ?: emptySet()
                    ),
                    subtasks = subtasks.toList(),
                    scoring = TaskScoring(
                        points = points.toInt(),
                        subtaskRule = subtaskRule,
                        requiredSubtaskCount = original?.scoring?.requiredSubtaskCount ?: 0,
                        graceMinutesOverride = original?.scoring?.graceMinutesOverride,
                        punctualityTracked = punctualityTracked,
                        allowPartial = allowPartial,
                        quantityTarget = quantityTarget.toDoubleOrNull(),
                        quantityUnit = quantityUnit,
                        excludedFromScore = excluded
                    ),
                    reminders = original?.reminders ?: emptyList(),
                    goalLinks = original?.goalLinks ?: emptyList(),
                    archived = original?.archived ?: false,
                    order = original?.order ?: 0
                )
            )
        },
        onDismiss = onDismiss
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedTextField(
                value = emoji,
                onValueChange = { if (it.length <= MAX_EMOJI_LENGTH) emoji = it },
                label = { Text(strings.taskEmoji) },
                singleLine = true,
                modifier = Modifier.width(EMOJI_FIELD_WIDTH.dp)
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.taskTitle) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(strings.taskNotes) },
            modifier = Modifier.fillMaxWidth()
        )

        EditorSection(strings.taskCategory) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                YomiChip(
                    label = strings.none,
                    selected = categoryId == null,
                    onClick = { categoryId = null }
                )
                categories.forEach { category ->
                    YomiChip(
                        label = category.name,
                        emoji = category.emoji,
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id }
                    )
                }
            }
        }

        EditorSection(strings.taskPriority) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                Priority.entries.forEach { value ->
                    YomiChip(
                        label = Fmt.priority(value, strings),
                        selected = priority == value,
                        onClick = { priority = value }
                    )
                }
            }
        }

        EditorSection(strings.taskEnergy) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                EnergyLevel.entries.forEach { value ->
                    YomiChip(
                        label = when (value) {
                            EnergyLevel.Light -> strings.energyLight
                            EnergyLevel.Medium -> strings.energyMedium
                            EnergyLevel.Deep -> strings.energyDeep
                        },
                        selected = energy == value,
                        onClick = { energy = value }
                    )
                }
            }
        }

        SoftDivider()

        EditorSection(strings.taskTiming) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                listOf(
                    TimingKind.Fixed to strings.timingFixed,
                    TimingKind.Window to strings.timingWindow,
                    TimingKind.Deadline to strings.timingDeadline,
                    TimingKind.Anytime to strings.timingAnytime
                ).forEach { (kind, label) ->
                    YomiChip(label = label, selected = timingKind == kind, onClick = { timingKind = kind })
                }
            }
            if (timingKind != TimingKind.Anytime) {
                Spacer(Modifier.height(spacing.small))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    if (timingKind != TimingKind.Deadline) {
                        OutlinedButton(onClick = { startPicker = true }, modifier = Modifier.weight(1f)) {
                            Text("${strings.fromTime} ${Fmt.time(startTime, use24h)}")
                        }
                    }
                    OutlinedButton(onClick = { endPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (timingKind == TimingKind.Deadline) {
                                "${strings.byTime} ${Fmt.time(endTime, use24h)}"
                            } else {
                                "${strings.toTime} ${Fmt.time(endTime, use24h)}"
                            }
                        )
                    }
                }
                if (timingKind == TimingKind.Window) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit) },
                        label = { Text(strings.durationMinutes) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        EditorSection(strings.taskRepeat) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                listOf(
                    RepeatKind.Daily to strings.repeatDaily,
                    RepeatKind.Weekly to strings.repeatWeekly,
                    RepeatKind.Monthly to strings.repeatMonthly,
                    RepeatKind.EveryN to strings.repeatEveryNDays,
                    RepeatKind.Once to strings.repeatOnce,
                    RepeatKind.Never to strings.repeatNever
                ).forEach { (kind, label) ->
                    YomiChip(label = label, selected = repeatKind == kind, onClick = { repeatKind = kind })
                }
            }
            if (repeatKind == RepeatKind.Weekly) {
                Spacer(Modifier.height(spacing.small))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                    weekdaysStartingFrom(firstDayOfWeek).forEach { day ->
                        YomiChip(
                            label = Fmt.weekdayShort(day, strings),
                            selected = day in weekdays,
                            onClick = {
                                weekdays = if (day in weekdays) weekdays - day else weekdays + day
                            }
                        )
                    }
                }
            }
            if (repeatKind == RepeatKind.EveryN) {
                OutlinedTextField(
                    value = everyN,
                    onValueChange = { everyN = it.filter(Char::isDigit) },
                    label = { Text(strings.interval) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SoftDivider()

        EditorSection(strings.taskSubtasks) {
            subtasks.forEachIndexed { index, subtask ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(spacing.small))
                    Text(subtask.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        subtasks[index] = subtask.copy(optional = !subtask.optional)
                    }) {
                        Text(if (subtask.optional) strings.optional else strings.all)
                    }
                    TextButton(onClick = { subtasks.removeAt(index) }) { Text("🗑") }
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    label = { Text(strings.addSubtask) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(spacing.small))
                TextButton(
                    enabled = newSubtask.isNotBlank(),
                    onClick = {
                        subtasks.add(SubtaskDefinition(id = newId(), title = newSubtask.trim()))
                        newSubtask = ""
                    }
                ) { Text(strings.add) }
            }
            if (subtasks.isNotEmpty()) {
                Spacer(Modifier.height(spacing.small))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
                    listOf(
                        SubtaskRule.Weighted to strings.ruleWeighted,
                        SubtaskRule.AllRequired to strings.ruleAllRequired,
                        SubtaskRule.NOfM to strings.ruleNOfM,
                        SubtaskRule.Informational to strings.ruleInformational
                    ).forEach { (rule, label) ->
                        YomiChip(label = label, selected = subtaskRule == rule, onClick = { subtaskRule = rule })
                    }
                }
            }
        }

        SoftDivider()

        EditorSection("${strings.taskScoring} · ${points.toInt()} ${strings.points}") {
            Slider(
                value = points,
                onValueChange = { points = it },
                valueRange = 1f..MAX_POINTS,
                modifier = Modifier.fillMaxWidth()
            )
            SettingRow(
                title = strings.allowPartial,
                trailing = { Switch(checked = allowPartial, onCheckedChange = { allowPartial = it }) },
                onClick = { allowPartial = !allowPartial }
            )
            SettingRow(
                title = strings.trackPunctuality,
                trailing = {
                    Switch(checked = punctualityTracked, onCheckedChange = { punctualityTracked = it })
                },
                onClick = { punctualityTracked = !punctualityTracked }
            )
            SettingRow(
                title = strings.excludeFromScore,
                trailing = { Switch(checked = excluded, onCheckedChange = { excluded = it }) },
                onClick = { excluded = !excluded }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                OutlinedTextField(
                    value = quantityTarget,
                    onValueChange = { quantityTarget = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(strings.quantityTarget) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = quantityUnit,
                    onValueChange = { quantityUnit = it },
                    label = { Text(strings.quantityUnit) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (startPicker) {
        TimePickerDialog(
            title = strings.fromTime,
            initial = startTime,
            use24h = use24h,
            onConfirm = { startTime = it },
            onDismiss = { startPicker = false }
        )
    }
    if (endPicker) {
        TimePickerDialog(
            title = strings.toTime,
            initial = endTime,
            use24h = use24h,
            onConfirm = { endTime = it },
            onDismiss = { endPicker = false }
        )
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(YomiTheme.spacing.tiny))
        content()
    }
}

private const val DEFAULT_POINTS = 10
private const val MAX_POINTS = 50f
private const val MAX_DURATION = 24 * 60
private const val DEFAULT_DURATION = 30
private const val MAX_INTERVAL = 365
private const val MAX_EMOJI_LENGTH = 4
private const val EMOJI_FIELD_WIDTH = 84
