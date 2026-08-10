package app.yomi.domain.goals

import app.yomi.domain.scoring.WeekMath
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.EntryStatus
import app.yomi.model.Goal
import app.yomi.model.GoalContribution
import app.yomi.model.GoalContributionEntry
import app.yomi.model.GoalPeriod
import app.yomi.model.GoalProgress
import app.yomi.model.GoalType
import app.yomi.model.PlanEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Works out how far along every goal is, purely from the day journal — a goal
 * never has to be updated by hand, it simply reads the tasks wired into it.
 */
class GoalEngine {

    /** The window a goal is currently being measured over. */
    fun periodBounds(
        goal: Goal,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek
    ): Pair<LocalDate, LocalDate> = when (goal.period) {
        GoalPeriod.Day -> today to today
        GoalPeriod.Week -> WeekMath.startOfWeek(today, firstDayOfWeek) to
            WeekMath.endOfWeek(today, firstDayOfWeek)

        GoalPeriod.Month -> WeekMath.startOfMonth(today) to WeekMath.endOfMonth(today)
        GoalPeriod.Total -> goal.startDate to (goal.deadline ?: today)
    }.let { (start, end) ->
        // Never measure before the goal existed or after its deadline.
        val clampedStart = maxOf(start, goal.startDate)
        val clampedEnd = goal.deadline?.let { minOf(end, it) } ?: end
        clampedStart to maxOf(clampedStart, clampedEnd)
    }

    fun progress(
        goal: Goal,
        days: List<DayPlan>,
        scores: Map<LocalDate, DayScore>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        taskCategoryOf: (String) -> String? = { null }
    ): GoalProgress {
        val (start, end) = periodBounds(goal, today, firstDayOfWeek)
        val window = days.filter { it.date in start..end }

        val contributions = mutableListOf<GoalContributionEntry>()
        var current = when (goal.type) {
            GoalType.AverageScore -> {
                val relevant = window.mapNotNull { scores[it.date] }.filter { it.tasksPlanned > 0 }
                if (relevant.isEmpty()) 0.0 else relevant.sumOf { it.score } / relevant.size
            }

            GoalType.Streak -> qualifyingRun(window, scores, end).toDouble()

            else -> {
                for (day in window) {
                    for (entry in day.entries) {
                        if (!entry.feeds(goal, taskCategoryOf)) continue
                        val amount = entry.amountFor(goal)
                        if (amount <= 0.0) continue
                        record(contributions, day.date, entry, amount)
                    }
                }
                contributions.sumOf { it.amount }
            }
        }
        if (current.isNaN()) current = 0.0

        val daysTotal = (start.daysUntil(end) + 1).coerceAtLeast(1)
        val daysElapsed = (start.daysUntil(minOf(today, end)) + 1).coerceIn(0, daysTotal)
        val expected = if (goal.type == GoalType.AverageScore) {
            goal.target
        } else {
            goal.target * (daysElapsed.toDouble() / daysTotal)
        }

        return GoalProgress(
            goalId = goal.id,
            periodStart = start,
            periodEnd = end,
            current = current,
            target = goal.target,
            expectedByNow = expected,
            daysElapsed = daysElapsed,
            daysTotal = daysTotal,
            contributions = contributions.sortedByDescending { it.date }
        )
    }

    /** Everything a single day contributed to a single goal. */
    fun dailyContribution(
        goal: Goal,
        plan: DayPlan,
        taskCategoryOf: (String) -> String? = { null }
    ): Double = plan.entries
        .filter { it.feeds(goal, taskCategoryOf) }
        .sumOf { it.amountFor(goal) }

    /**
     * True when every goal carrying a daily minimum received it today. Feeds the
     * "kept the pace" bonus in the scoring engine.
     */
    fun dailyPaceMet(
        goals: List<Goal>,
        plan: DayPlan,
        taskCategoryOf: (String) -> String? = { null }
    ): Boolean? {
        val paced = goals.filter { !it.archived && (it.minimumDailyProgress ?: 0.0) > 0.0 }
        if (paced.isEmpty()) return null
        return paced.all { goal ->
            dailyContribution(goal, plan, taskCategoryOf) + 1e-9 >= (goal.minimumDailyProgress ?: 0.0)
        }
    }

    private fun record(
        into: MutableList<GoalContributionEntry>,
        date: LocalDate,
        entry: PlanEntry,
        amount: Double
    ) {
        into += GoalContributionEntry(date, entry.id, entry.title, amount)
    }

    private fun qualifyingRun(
        window: List<DayPlan>,
        scores: Map<LocalDate, DayScore>,
        end: LocalDate
    ): Int {
        var run = 0
        var cursor = end
        val known = window.map { it.date }.toSet()
        while (cursor in known) {
            val score = scores[cursor] ?: break
            if (score.tasksPlanned == 0 || score.tasksDone < score.tasksPlanned) break
            run++
            cursor = cursor.plus(-1, DateTimeUnit.DAY)
        }
        return run
    }
}

/** Is this entry wired into the goal, directly or through its category? */
internal fun PlanEntry.feeds(goal: Goal, taskCategoryOf: (String) -> String?): Boolean {
    if (status != EntryStatus.Done && status != EntryStatus.Partial) return false
    val byTask = taskId != null && taskId in goal.linkedTaskIds
    val entryCategory = categoryId ?: taskId?.let(taskCategoryOf)
    val byCategory = entryCategory != null && entryCategory in goal.linkedCategoryIds
    return byTask || byCategory
}

/** How much this completion is worth to the goal. */
internal fun PlanEntry.amountFor(goal: Goal): Double {
    val link = goalLinks.firstOrNull { it.goalId == goal.id }
    val mode = link?.contribution ?: when (goal.type) {
        GoalType.Minutes -> GoalContribution.PerMinute
        GoalType.Quantity -> GoalContribution.PerQuantity
        else -> GoalContribution.PerCompletion
    }
    val factor = link?.factor ?: 1.0
    val partialScale = if (status == EntryStatus.Partial) (completionRatio().coerceIn(0.0, 1.0)) else 1.0

    val base = when (mode) {
        GoalContribution.PerCompletion -> 1.0
        GoalContribution.PerMinute -> (timing.plannedMinutes ?: 0).toDouble()
        GoalContribution.PerQuantity -> actualQuantity ?: scoring.quantityTarget ?: 0.0
    }
    // A measured quantity already reflects reality; do not scale it down twice.
    val scale = if (mode == GoalContribution.PerQuantity && actualQuantity != null) 1.0 else partialScale
    return base * factor * scale
}
