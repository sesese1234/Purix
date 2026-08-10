package app.yomi.feature.today

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.components.ConfirmDialog
import app.yomi.designsystem.components.EmptyState
import app.yomi.designsystem.components.ScoreRing
import app.yomi.designsystem.components.SectionHeader
import app.yomi.designsystem.components.StatTile
import app.yomi.designsystem.components.TaskRow
import app.yomi.designsystem.components.TextPromptDialog
import app.yomi.designsystem.components.ThinProgress
import app.yomi.designsystem.components.TimePickerDialog
import app.yomi.designsystem.components.WavyProgress
import app.yomi.designsystem.components.YomiCard
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.i18n.format
import app.yomi.designsystem.i18n.insightText
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.model.DayPart
import app.yomi.model.EntryStatus
import app.yomi.model.PlanEntry
import app.yomi.model.TodayCard
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The screen the app opens on: what is left today, how the day is scoring, and
 * one tap between the user and the next thing on the list.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    onToggleDone: (String) -> Unit = {},
    onToggleSubtask: (String, String) -> Unit = { _, _ -> },
    onOpenEntry: (PlanEntry) -> Unit = {},
    onSkip: (String, String) -> Unit = { _, _ -> },
    onDefer: (String, LocalDate) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onStart: (String) -> Unit = {},
    onQuickAdd: (String) -> Unit = {},
    onShiftDay: (Int) -> Unit = {},
    onGoToToday: () -> Unit = {},
    onSetWake: (kotlinx.datetime.LocalTime?) -> Unit = {},
    onSetSleep: (kotlinx.datetime.LocalTime?) -> Unit = {},
    onSetMood: (Int?) -> Unit = {},
    onSetEnergy: (Int?) -> Unit = {},
    onFinishDay: () -> Unit = {},
    onReopenDay: () -> Unit = {},
    onOpenScoreDetail: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    var quickAddVisible by remember { mutableStateOf(false) }
    var skipTarget by remember { mutableStateOf<PlanEntry?>(null) }
    var finishConfirm by remember { mutableStateOf(false) }
    var wakePicker by remember { mutableStateOf(false) }
    var sleepPicker by remember { mutableStateOf(false) }
    var expandedEntry by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!state.plan.finalized) {
                ExtendedFloatingActionButton(
                    onClick = { quickAddVisible = true },
                    text = { Text(strings.addTask) },
                    icon = { Text("＋", style = MaterialTheme.typography.titleLarge) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screenPadding,
                end = spacing.screenPadding,
                top = spacing.medium,
                bottom = spacing.xxlarge * 2
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            item("header") {
                DayHeader(
                    state = state,
                    onShiftDay = onShiftDay,
                    onGoToToday = onGoToToday
                )
            }

            state.settings.general.todayCards.forEach { card ->
                when (card) {
                    TodayCard.Score -> if (state.settings.appearance.showScoreRing) {
                        item("score") { ScoreCard(state, onOpenScoreDetail) }
                    }

                    TodayCard.Progress -> item("progress") { ProgressCard(state) }
                    TodayCard.CheckIn -> item("checkIn") {
                        CheckInCard(
                            state = state,
                            onPickWake = { wakePicker = true },
                            onPickSleep = { sleepPicker = true },
                            onSetMood = onSetMood,
                            onSetEnergy = onSetEnergy
                        )
                    }

                    TodayCard.UpNext -> item("upNext") { UpNextCard(state, onToggleDone, onStart) }
                    TodayCard.Goals -> if (state.goals.isNotEmpty()) {
                        item("goals") { GoalStrip(state) }
                    }

                    TodayCard.Streak -> item("streak") { StreakCard(state) }
                    TodayCard.Timeline -> Unit
                }
            }

            if (state.entries.isEmpty()) {
                item("empty") {
                    EmptyState(
                        emoji = "🌱",
                        title = strings.emptyDayTitle,
                        body = strings.emptyDayBody,
                        action = {
                            Button(onClick = { quickAddVisible = true }) { Text(strings.addTask) }
                        }
                    )
                }
            } else {
                item("timelineHeader") {
                    SectionHeader(
                        title = strings.timeline,
                        subtitle = strings.tasksDoneOf.format(
                            state.doneCount.toString(),
                            state.entries.size.toString()
                        )
                    )
                }

                state.groups.forEach { group ->
                    item("group-${group.key}") {
                        GroupHeader(group)
                    }
                    items(group.entries, key = { it.id }) { entry ->
                        TaskRow(
                            entry = entry,
                            use24h = state.settings.general.use24HourClock,
                            accent = state.categories[entry.categoryId]?.let { Color(it.colorArgb.toInt()) },
                            expanded = expandedEntry == entry.id,
                            onToggleDone = { onToggleDone(entry.id) },
                            onClick = {
                                expandedEntry = if (expandedEntry == entry.id) null else entry.id
                            },
                            onToggleSubtask = { onToggleSubtask(entry.id, it) },
                            trailing = {
                                EntryActions(
                                    entry = entry,
                                    finalized = state.plan.finalized,
                                    onOpen = { onOpenEntry(entry) },
                                    onSkip = { skipTarget = entry },
                                    onDefer = { onDefer(entry.id, state.date.plus(1, DateTimeUnit.DAY)) },
                                    onDelete = { onDelete(entry.id) }
                                )
                            }
                        )
                    }
                }
            }

            item("finish") {
                Spacer(Modifier.height(YomiTheme.spacing.small))
                FinishDayRow(
                    finalized = state.plan.finalized,
                    onFinish = { finishConfirm = true },
                    onReopen = onReopenDay
                )
            }
        }
    }

    if (quickAddVisible) {
        TextPromptDialog(
            title = strings.quickAdd,
            label = strings.taskTitle,
            confirmLabel = strings.add,
            onConfirm = onQuickAdd,
            onDismiss = { quickAddVisible = false }
        )
    }

    skipTarget?.let { entry ->
        TextPromptDialog(
            title = strings.skipReason,
            label = strings.journalNote,
            supporting = strings.skipReasonHint,
            confirmLabel = strings.skipTask,
            allowEmpty = true,
            onConfirm = { reason -> onSkip(entry.id, reason) },
            onDismiss = { skipTarget = null }
        )
    }

    if (finishConfirm) {
        ConfirmDialog(
            title = strings.finishDay,
            message = strings.tasksDoneOf.format(
                state.doneCount.toString(),
                state.entries.size.toString()
            ),
            confirmLabel = strings.finishDay,
            onConfirm = onFinishDay,
            onDismiss = { finishConfirm = false }
        )
    }

    if (wakePicker) {
        TimePickerDialog(
            title = strings.wokeUpAt,
            initial = state.plan.checkIn.wakeActual ?: state.plan.checkIn.wakeTarget,
            use24h = state.settings.general.use24HourClock,
            onConfirm = onSetWake,
            onDismiss = { wakePicker = false }
        )
    }

    if (sleepPicker) {
        TimePickerDialog(
            title = strings.wentToBedAt,
            initial = state.plan.checkIn.sleepActual ?: state.plan.checkIn.sleepTarget,
            use24h = state.settings.general.use24HourClock,
            onConfirm = onSetSleep,
            onDismiss = { sleepPicker = false }
        )
    }
}

@Composable
private fun DayHeader(
    state: TodayUiState,
    onShiftDay: (Int) -> Unit,
    onGoToToday: () -> Unit
) {
    val strings = LocalStrings.current
    val greeting = when {
        !state.isToday -> Fmt.dateWithWeekday(state.date, strings)
        state.now.hour < MORNING_END -> strings.goodMorning
        state.now.hour < AFTERNOON_END -> strings.goodAfternoon
        state.now.hour < EVENING_END -> strings.goodEvening
        else -> strings.goodNight
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = Fmt.dateWithWeekday(state.date, strings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val backGlyph = if (strings.isRtl) "›" else "‹"
        val forwardGlyph = if (strings.isRtl) "‹" else "›"
        TextButton(onClick = { onShiftDay(-1) }) { Text(backGlyph) }
        if (!state.isToday) {
            TextButton(onClick = onGoToToday) { Text(strings.today) }
        }
        TextButton(onClick = { onShiftDay(1) }) { Text(forwardGlyph) }
    }
}

@Composable
private fun ScoreCard(state: TodayUiState, onOpenDetail: () -> Unit) {
    val strings = LocalStrings.current
    val score = state.score
    YomiCard(onClick = onOpenDetail) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(
                score = score?.score ?: 0.0,
                maxScore = state.settings.scoring.bonuses.scoreCeiling,
                gradeLabel = score?.grade?.label.orEmpty(),
                gradeEmoji = score?.grade?.emoji.orEmpty(),
                caption = strings.todayScore,
                potential = state.potentialScore
            )
            Spacer(Modifier.width(YomiTheme.spacing.medium))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)
            ) {
                Text(
                    text = "${strings.projectedScore}: ${Fmt.score(state.potentialScore)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                score?.insights?.take(MAX_INSIGHTS)?.forEach { insight ->
                    Text(
                        text = insightText(insight, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (score?.isPerfectDay == true) {
                    Text(
                        text = "🌟 ${strings.perfectDay}",
                        style = MaterialTheme.typography.titleSmall,
                        color = YomiTheme.accents.success,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: TodayUiState) {
    val strings = LocalStrings.current
    YomiCard {
        SectionHeader(
            title = strings.dayProgress,
            subtitle = strings.tasksDoneOf.format(
                state.doneCount.toString(),
                state.entries.size.toString()
            )
        )
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        WavyProgress(progress = state.progress)
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)) {
            StatTile(
                value = state.doneCount.toString(),
                label = strings.statusDone,
                modifier = Modifier.weight(1f),
                accent = YomiTheme.accents.success
            )
            StatTile(
                value = state.openCount.toString(),
                label = strings.statusPending,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = state.missedCount.toString(),
                label = strings.statusMissed,
                modifier = Modifier.weight(1f),
                accent = YomiTheme.accents.missed
            )
            StatTile(
                value = state.streak.toString(),
                label = strings.bonusStreak,
                modifier = Modifier.weight(1f),
                emoji = "🔥"
            )
        }
    }
}

@Composable
private fun CheckInCard(
    state: TodayUiState,
    onPickWake: () -> Unit,
    onPickSleep: () -> Unit,
    onSetMood: (Int?) -> Unit,
    onSetEnergy: (Int?) -> Unit
) {
    val strings = LocalStrings.current
    val checkIn = state.plan.checkIn
    val use24h = state.settings.general.use24HourClock

    YomiCard {
        SectionHeader(
            title = strings.checkIn,
            subtitle = if (checkIn.wakeActual == null) strings.notCheckedIn else null
        )
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)) {
            OutlinedButton(onClick = onPickWake, modifier = Modifier.weight(1f)) {
                Text("☀️ ${Fmt.time(checkIn.wakeActual, use24h)}")
            }
            OutlinedButton(onClick = onPickSleep, modifier = Modifier.weight(1f)) {
                Text("🌙 ${Fmt.time(checkIn.sleepActual, use24h)}")
            }
        }
        checkIn.wakeDeltaMinutes?.let { delta ->
            Spacer(Modifier.height(YomiTheme.spacing.small))
            Text(
                text = if (delta <= 0) {
                    strings.insightEarlyRiser
                } else {
                    strings.insightLateStart.format(delta.toString())
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (delta <= 0) YomiTheme.accents.success else YomiTheme.accents.warning
            )
        }

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Text(strings.mood, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(YomiTheme.spacing.tiny))
        Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.tiny)) {
            MOOD_EMOJI.forEachIndexed { index, emoji ->
                val value = index + 1
                YomiChip(
                    label = emoji,
                    selected = checkIn.mood == value,
                    onClick = { onSetMood(if (checkIn.mood == value) null else value) }
                )
            }
        }
        Spacer(Modifier.height(YomiTheme.spacing.small))
        Text(strings.energy, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(YomiTheme.spacing.tiny))
        Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.tiny)) {
            (1..ENERGY_LEVELS).forEach { value ->
                YomiChip(
                    label = value.toString(),
                    selected = checkIn.energy == value,
                    onClick = { onSetEnergy(if (checkIn.energy == value) null else value) }
                )
            }
        }
    }
}

@Composable
private fun UpNextCard(
    state: TodayUiState,
    onToggleDone: (String) -> Unit,
    onStart: (String) -> Unit
) {
    val strings = LocalStrings.current
    val entry = state.upNext
    YomiCard {
        SectionHeader(title = strings.upNext)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        if (entry == null) {
            Text(
                text = strings.nothingNext,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(YomiTheme.spacing.medium))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = Fmt.timeRange(entry.timing, state.settings.general.use24HourClock, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.status == EntryStatus.Pending) {
                    TextButton(onClick = { onStart(entry.id) }) { Text(strings.startTask) }
                }
                Button(onClick = { onToggleDone(entry.id) }) { Text(strings.done) }
            }
        }
    }
}

@Composable
private fun StreakCard(state: TodayUiState) {
    val strings = LocalStrings.current
    YomiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(YomiTheme.spacing.medium))
            Text(
                text = if (state.streak > 0) {
                    strings.streakDays.format(state.streak.toString())
                } else {
                    strings.noStreak
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GoalStrip(state: TodayUiState) {
    val strings = LocalStrings.current
    YomiCard {
        SectionHeader(title = strings.goals)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        state.goals.take(MAX_GOAL_CARDS).forEach { card ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = YomiTheme.spacing.tiny),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(card.goal.emoji)
                Spacer(Modifier.width(YomiTheme.spacing.small))
                Column(Modifier.weight(1f)) {
                    Text(card.goal.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(YomiTheme.spacing.hairline))
                    ThinProgress(
                        progress = card.progress.ratio.toFloat(),
                        color = Color(card.goal.colorArgb.toInt())
                    )
                }
                Spacer(Modifier.width(YomiTheme.spacing.small))
                Text(
                    text = "${Fmt.amount(card.progress.current)}/${Fmt.amount(card.progress.target)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (card.progress.isOnPace) {
                        YomiTheme.accents.success
                    } else {
                        YomiTheme.accents.warning
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TimelineGroup) {
    val strings = LocalStrings.current
    val title = when {
        group.dayPart != null -> Fmt.dayPart(group.dayPart, strings)
        group.priority != null -> Fmt.priority(group.priority, strings)
        group.status != null -> Fmt.status(group.status, strings)
        else -> group.title
    }
    val emoji = when {
        group.dayPart != null -> Fmt.dayPartEmoji(group.dayPart)
        else -> group.emoji
    }
    if (title.isBlank() && emoji.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = YomiTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji.isNotBlank()) {
            Text(emoji, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(YomiTheme.spacing.tiny))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(YomiTheme.spacing.small))
        Text(
            text = "${group.entries.count { it.status == EntryStatus.Done }}/${group.entries.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EntryActions(
    entry: PlanEntry,
    finalized: Boolean,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    onDefer: () -> Unit,
    onDelete: () -> Unit
) {
    if (finalized) return
    val strings = LocalStrings.current
    Row {
        TextButton(onClick = onOpen) { Text("⋯") }
        if (entry.status.isOpen) {
            TextButton(onClick = onSkip) { Text("–") }
            TextButton(onClick = onDefer) { Text("»") }
        }
        if (entry.taskId == null) {
            TextButton(onClick = onDelete) { Text("🗑") }
        }
    }
}

@Composable
private fun FinishDayRow(
    finalized: Boolean,
    onFinish: () -> Unit,
    onReopen: () -> Unit
) {
    val strings = LocalStrings.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (finalized) {
            OutlinedButton(onClick = onReopen) { Text("${strings.dayFinished} · ${strings.reopenDay}") }
        } else {
            Button(onClick = onFinish) { Text(strings.finishDay) }
        }
    }
}

private val MOOD_EMOJI = listOf("😞", "🙁", "😐", "🙂", "😄")
private const val ENERGY_LEVELS = 5
private const val MORNING_END = 12
private const val AFTERNOON_END = 17
private const val EVENING_END = 22
private const val MAX_INSIGHTS = 3
private const val MAX_GOAL_CARDS = 4
