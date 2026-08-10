package app.yomi.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.yomi.data.YomiRepository
import app.yomi.designsystem.components.ConfirmDialog
import app.yomi.designsystem.components.EmptyState
import app.yomi.designsystem.components.SectionHeader
import app.yomi.designsystem.components.ThinProgress
import app.yomi.designsystem.components.YomiCard
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.components.YomiDialog
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.i18n.format
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.model.Category
import app.yomi.model.Goal
import app.yomi.model.GoalPeriod
import app.yomi.model.GoalProgress
import app.yomi.model.GoalType
import app.yomi.model.TaskDefinition
import app.yomi.ui.YomiViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class GoalRow(val goal: Goal, val progress: GoalProgress)

data class GoalsUiState(
    val goals: List<GoalRow> = emptyList(),
    val archived: List<Goal> = emptyList(),
    val tasks: List<TaskDefinition> = emptyList(),
    val categories: List<Category> = emptyList(),
    val today: LocalDate = LocalDate(2026, 1, 1)
)

/** Goals are read-only derivations of the journal, so this model is mostly a view. */
class GoalsViewModel(repository: YomiRepository) : YomiViewModel(repository) {

    val state: StateFlow<GoalsUiState> = combine(
        repository.catalog,
        repository.days,
        repository.scores
    ) { catalog, _, _ ->
        GoalsUiState(
            goals = catalog.activeGoals
                .sortedBy { it.order }
                .map { GoalRow(it, repository.goalProgress(it)) },
            archived = catalog.goals.filter { it.archived },
            tasks = catalog.activeTasks,
            categories = catalog.activeCategories,
            today = repository.today()
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), GoalsUiState())

    fun upsert(goal: Goal) = act { repository.upsertGoal(goal) }

    fun delete(goalId: String) = act { repository.deleteGoal(goalId) }

    fun setArchived(goal: Goal, archived: Boolean) = act {
        repository.upsertGoal(goal.copy(archived = archived))
    }

    fun newId(): String = repository.newId()

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

/** The goals board: one card per goal, each showing pace as well as progress. */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    modifier: Modifier = Modifier,
    onSave: (Goal) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onArchive: (Goal, Boolean) -> Unit = { _, _ -> },
    newId: () -> String = { "" }
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing
    var editing by remember { mutableStateOf<Goal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = spacing.screenPadding),
        contentPadding = PaddingValues(vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title = strings.goals, modifier = Modifier.weight(1f))
                Button(onClick = { creating = true }) { Text(strings.newGoal) }
            }
        }

        if (state.goals.isEmpty()) {
            item("empty") {
                EmptyState(emoji = "🎯", title = strings.noGoals, body = strings.noGoalsBody)
            }
        }

        items(state.goals, key = { it.goal.id }) { row ->
            GoalCard(
                row = row,
                onEdit = { editing = row.goal },
                onArchive = { onArchive(row.goal, true) },
                onDelete = { deleteTarget = row.goal }
            )
        }

        if (state.archived.isNotEmpty()) {
            item("archivedHeader") { SectionHeader(title = strings.archive) }
            items(state.archived, key = { it.id }) { goal ->
                YomiCard(shape = MaterialTheme.shapes.medium) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.emoji)
                        Spacer(Modifier.width(spacing.small))
                        Text(goal.title, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onArchive(goal, false) }) { Text(strings.restore) }
                        TextButton(onClick = { deleteTarget = goal }) { Text("🗑") }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        GoalEditorDialog(
            original = editing,
            tasks = state.tasks,
            categories = state.categories,
            today = state.today,
            newId = newId,
            onSave = onSave,
            onDismiss = { creating = false; editing = null }
        )
    }

    deleteTarget?.let { goal ->
        ConfirmDialog(
            title = strings.delete,
            message = goal.title,
            destructive = true,
            onConfirm = { onDelete(goal.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun GoalCard(
    row: GoalRow,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing
    val accent = Color(row.goal.colorArgb.toInt())
    val progress = row.progress

    YomiCard(accent = accent, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.goal.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(spacing.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${Fmt.goalType(row.goal.type, strings)} · ${Fmt.goalPeriod(row.goal.period, strings)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${Fmt.amount(progress.current)} / ${Fmt.amount(progress.target, row.goal.unit)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }

        Spacer(Modifier.height(spacing.medium))
        ThinProgress(progress = progress.ratio.toFloat(), color = accent, height = 8.dp())

        Spacer(Modifier.height(spacing.small))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    progress.isComplete -> "✅ ${strings.goalComplete}"
                    progress.isOnPace -> "🟢 ${strings.onPace}"
                    else -> "🟠 ${strings.behindPace}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    progress.isComplete -> YomiTheme.accents.success
                    progress.isOnPace -> YomiTheme.accents.success
                    else -> YomiTheme.accents.warning
                }
            )
            Spacer(Modifier.weight(1f))
            val paceIsMeaningful = row.goal.type != GoalType.AverageScore &&
                row.goal.type != GoalType.Streak
            progress.requiredPerRemainingDay
                ?.takeIf { !progress.isComplete && paceIsMeaningful }
                ?.let { perDay ->
                Text(
                    text = strings.perDayNeeded.format(Fmt.amount(perDay)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row {
            TextButton(onClick = onEdit) { Text(strings.edit) }
            TextButton(onClick = onArchive) { Text(strings.archive) }
            TextButton(onClick = onDelete) { Text(strings.delete) }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    original: Goal?,
    tasks: List<TaskDefinition>,
    categories: List<Category>,
    today: LocalDate,
    newId: () -> String,
    onSave: (Goal) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var emoji by remember { mutableStateOf(original?.emoji ?: "🎯") }
    var type by remember { mutableStateOf(original?.type ?: GoalType.Count) }
    var period by remember { mutableStateOf(original?.period ?: GoalPeriod.Week) }
    var target by remember { mutableStateOf(Fmt.amount(original?.target ?: DEFAULT_TARGET)) }
    var unit by remember { mutableStateOf(original?.unit.orEmpty()) }
    var dailyMinimum by remember {
        mutableStateOf(original?.minimumDailyProgress?.let { Fmt.amount(it) }.orEmpty())
    }
    var linkedTasks by remember { mutableStateOf(original?.linkedTaskIds ?: emptySet()) }
    var linkedCategories by remember { mutableStateOf(original?.linkedCategoryIds ?: emptySet()) }

    YomiDialog(
        title = if (original == null) strings.newGoal else strings.editGoal,
        confirmEnabled = title.isNotBlank(),
        onConfirm = {
            onSave(
                Goal(
                    id = original?.id ?: newId(),
                    title = title.trim(),
                    emoji = emoji.ifBlank { "🎯" },
                    description = original?.description.orEmpty(),
                    type = type,
                    target = target.toDoubleOrNull() ?: DEFAULT_TARGET,
                    unit = unit,
                    period = period,
                    startDate = original?.startDate ?: today,
                    deadline = original?.deadline,
                    colorArgb = original?.colorArgb ?: DEFAULT_GOAL_COLOR,
                    linkedTaskIds = linkedTasks,
                    linkedCategoryIds = linkedCategories,
                    minimumDailyProgress = dailyMinimum.toDoubleOrNull(),
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
                onValueChange = { if (it.length <= MAX_EMOJI) emoji = it },
                label = { Text(strings.taskEmoji) },
                singleLine = true,
                modifier = Modifier.width(EMOJI_WIDTH.dp())
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.goalTitle) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Text(strings.goalType, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
            GoalType.entries.forEach { value ->
                YomiChip(
                    label = Fmt.goalType(value, strings),
                    selected = type == value,
                    onClick = { type = value }
                )
            }
        }

        Text(strings.goalPeriod, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
            GoalPeriod.entries.forEach { value ->
                YomiChip(
                    label = Fmt.goalPeriod(value, strings),
                    selected = period == value,
                    onClick = { period = value }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(strings.goalTarget) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text(strings.quantityUnit) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = dailyMinimum,
            onValueChange = { dailyMinimum = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(strings.goalDailyMinimum) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(strings.goalLinkedTasks, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
            tasks.forEach { task ->
                YomiChip(
                    label = task.title,
                    emoji = task.emoji,
                    selected = task.id in linkedTasks,
                    onClick = {
                        linkedTasks = if (task.id in linkedTasks) {
                            linkedTasks - task.id
                        } else {
                            linkedTasks + task.id
                        }
                    }
                )
            }
        }

        Text(strings.goalLinkedCategories, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
            categories.forEach { category ->
                YomiChip(
                    label = category.name,
                    emoji = category.emoji,
                    selected = category.id in linkedCategories,
                    onClick = {
                        linkedCategories = if (category.id in linkedCategories) {
                            linkedCategories - category.id
                        } else {
                            linkedCategories + category.id
                        }
                    }
                )
            }
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

private const val DEFAULT_TARGET = 5.0
private const val DEFAULT_GOAL_COLOR = 0xFF4CC9A7
private const val MAX_EMOJI = 4
private const val EMOJI_WIDTH = 84
