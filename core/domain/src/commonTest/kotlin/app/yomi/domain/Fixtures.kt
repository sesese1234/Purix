package app.yomi.domain

import app.yomi.model.DayCheckIn
import app.yomi.model.DayPlan
import app.yomi.model.EntryStatus
import app.yomi.model.PlanEntry
import app.yomi.model.Priority
import app.yomi.model.Recurrence
import app.yomi.model.RecurrenceRule
import app.yomi.model.SubtaskState
import app.yomi.model.TaskDefinition
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** A fixed Monday used as the anchor for every date in the tests. */
val Monday: LocalDate = LocalDate(2026, 3, 2)
val Tuesday: LocalDate = LocalDate(2026, 3, 3)
val Wednesday: LocalDate = LocalDate(2026, 3, 4)
val Sunday: LocalDate = LocalDate(2026, 3, 8)

fun time(hour: Int, minute: Int = 0): LocalTime = LocalTime(hour, minute)

fun entry(
    id: String = "e1",
    title: String = "Task $id",
    taskId: String? = null,
    points: Int = 10,
    priority: Priority = Priority.Normal,
    timing: TaskTiming = TaskTiming.Anytime,
    status: EntryStatus = EntryStatus.Pending,
    completedAt: LocalTime? = null,
    subtasks: List<SubtaskState> = emptyList(),
    scoring: TaskScoring = TaskScoring(points = points),
    categoryId: String? = null,
    skipReason: String = "",
    actualQuantity: Double? = null
): PlanEntry = PlanEntry(
    id = id,
    taskId = taskId,
    title = title,
    priority = priority,
    timing = timing,
    status = status,
    completedAt = completedAt,
    subtasks = subtasks,
    scoring = scoring,
    categoryId = categoryId,
    skipReason = skipReason,
    actualQuantity = actualQuantity
)

fun subtask(id: String, done: Boolean = false, weight: Int = 1, optional: Boolean = false) =
    SubtaskState(id = id, title = id, weight = weight, optional = optional, done = done)

fun plan(
    date: LocalDate = Monday,
    entries: List<PlanEntry> = emptyList(),
    checkIn: DayCheckIn = DayCheckIn(),
    finalized: Boolean = false
): DayPlan = DayPlan(date = date, entries = entries, checkIn = checkIn, finalized = finalized)

fun task(
    id: String,
    recurrence: Recurrence = Recurrence.Daily(),
    start: LocalDate = Monday,
    timing: TaskTiming = TaskTiming.Anytime,
    archived: Boolean = false
): TaskDefinition = TaskDefinition(
    id = id,
    title = "Task $id",
    timing = timing,
    schedule = RecurrenceRule(recurrence, start),
    archived = archived
)

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.minusDays(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)
