package app.yomi.domain

import app.yomi.domain.goals.GoalEngine
import app.yomi.domain.notifications.NotificationPlanner
import app.yomi.domain.notifications.isQuiet
import app.yomi.domain.scoring.StreakCalculator
import app.yomi.domain.scoring.WeekMath
import app.yomi.domain.scoring.WeekScoringEngine
import app.yomi.model.DayScore
import app.yomi.model.EntryStatus
import app.yomi.model.Goal
import app.yomi.model.GoalContribution
import app.yomi.model.GoalLink
import app.yomi.model.GoalPeriod
import app.yomi.model.GoalType
import app.yomi.model.NotificationKind
import app.yomi.model.NotificationSettings
import app.yomi.model.ReminderRule
import app.yomi.model.ScoringConfig
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalEngineTest {

    private val engine = GoalEngine()

    private val readingGoal = Goal(
        id = "g1",
        title = "Read",
        type = GoalType.Quantity,
        target = 100.0,
        period = GoalPeriod.Week,
        startDate = Monday.minusDays(30),
        linkedTaskIds = setOf("t-read")
    )

    private fun readEntry(id: String, pages: Double) = entry(
        id = id,
        taskId = "t-read",
        status = EntryStatus.Done,
        actualQuantity = pages,
        scoring = TaskScoring(points = 10, quantityTarget = 20.0)
    ).copy(goalLinks = listOf(GoalLink("g1", GoalContribution.PerQuantity)))

    @Test
    fun `quantity contributions accumulate over the period`() {
        val days = listOf(
            plan(date = Monday, entries = listOf(readEntry("a", 30.0))),
            plan(date = Tuesday, entries = listOf(readEntry("b", 25.0)))
        )
        val progress = engine.progress(readingGoal, days, emptyMap(), Tuesday, DayOfWeek.MONDAY)
        assertEquals(55.0, progress.current)
        assertEquals(2, progress.contributions.size)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `unfinished tasks contribute nothing`() {
        val days = listOf(
            plan(date = Monday, entries = listOf(readEntry("a", 30.0).copy(status = EntryStatus.Pending)))
        )
        val progress = engine.progress(readingGoal, days, emptyMap(), Monday, DayOfWeek.MONDAY)
        assertEquals(0.0, progress.current)
    }

    @Test
    fun `count goals fed by a category tally completions`() {
        val goal = readingGoal.copy(
            type = GoalType.Count,
            target = 4.0,
            linkedTaskIds = emptySet(),
            linkedCategoryIds = setOf("body")
        )
        val days = listOf(
            plan(date = Monday, entries = listOf(
                entry("a", categoryId = "body", status = EntryStatus.Done),
                entry("b", categoryId = "body", status = EntryStatus.Done),
                entry("c", categoryId = "mind", status = EntryStatus.Done)
            ))
        )
        val progress = engine.progress(goal, days, emptyMap(), Monday, DayOfWeek.MONDAY)
        assertEquals(2.0, progress.current)
    }

    @Test
    fun `minute goals use the planned duration`() {
        val goal = readingGoal.copy(type = GoalType.Minutes, target = 180.0, linkedTaskIds = setOf("t-gym"))
        val days = listOf(
            plan(date = Monday, entries = listOf(
                entry(
                    "a",
                    taskId = "t-gym",
                    status = EntryStatus.Done,
                    timing = TaskTiming.Fixed(time(17), time(18, 30))
                )
            ))
        )
        val progress = engine.progress(goal, days, emptyMap(), Monday, DayOfWeek.MONDAY)
        assertEquals(90.0, progress.current)
    }

    @Test
    fun `average score goals read the day scores`() {
        val goal = readingGoal.copy(type = GoalType.AverageScore, target = 85.0)
        val scores = mapOf(
            Monday to score(Monday, 90.0),
            Tuesday to score(Tuesday, 80.0)
        )
        val days = listOf(plan(date = Monday, entries = listOf(entry("a"))), plan(date = Tuesday, entries = listOf(entry("b"))))
        val progress = engine.progress(goal, days, scores, Tuesday, DayOfWeek.MONDAY)
        assertEquals(85.0, progress.current)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `pace tracking knows when you are behind`() {
        val days = listOf(plan(date = Monday, entries = listOf(readEntry("a", 5.0))))
        // Day 1 of 7, so 100/7 is expected and 5 pages is behind.
        val progress = engine.progress(readingGoal, days, emptyMap(), Monday, DayOfWeek.MONDAY)
        assertFalse(progress.isOnPace)
        assertEquals(95.0, progress.remaining)
    }

    @Test
    fun `the period never starts before the goal did`() {
        val goal = readingGoal.copy(startDate = Tuesday)
        val progress = engine.progress(goal, emptyList(), emptyMap(), Wednesday, DayOfWeek.MONDAY)
        assertEquals(Tuesday, progress.periodStart)
    }

    @Test
    fun `daily pace is null when no goal asks for one`() {
        assertNull(engine.dailyPaceMet(listOf(readingGoal), plan()))
    }

    @Test
    fun `daily pace is met once the minimum is reached`() {
        val goal = readingGoal.copy(minimumDailyProgress = 15.0)
        val met = plan(date = Monday, entries = listOf(readEntry("a", 20.0)))
        val missedIt = plan(date = Monday, entries = listOf(readEntry("a", 5.0)))
        assertEquals(true, engine.dailyPaceMet(listOf(goal), met))
        assertEquals(false, engine.dailyPaceMet(listOf(goal), missedIt))
    }

    private fun score(date: LocalDate, value: Double) = DayScore(
        date = date,
        score = value,
        baseScore = value,
        grade = ScoringConfig().gradeFor(value),
        components = emptyList(),
        adjustments = emptyList(),
        entryScores = emptyList(),
        tasksPlanned = 3,
        tasksDone = 3,
        tasksPartial = 0,
        tasksMissed = 0,
        tasksSkipped = 0,
        isPerfectDay = value >= 100.0,
        streakLength = 0,
        insights = emptyList()
    )
}

class WeekAndStreakTest {

    private val engine = WeekScoringEngine()

    private fun score(date: LocalDate, value: Double, planned: Int = 3) = DayScore(
        date = date,
        score = value,
        baseScore = value,
        grade = ScoringConfig().gradeFor(value),
        components = emptyList(),
        adjustments = emptyList(),
        entryScores = emptyList(),
        tasksPlanned = planned,
        tasksDone = planned,
        tasksPartial = 0,
        tasksMissed = 0,
        tasksSkipped = 0,
        isPerfectDay = value >= 100.0,
        streakLength = 0,
        insights = emptyList()
    )

    @Test
    fun `the weekly score averages the days that had a plan`() {
        val scores = listOf(
            score(Monday, 80.0),
            score(Tuesday, 90.0),
            score(Wednesday, 0.0, planned = 0)
        )
        val config = ScoringConfig(weeklyConsistencyBonus = 0.0)
        val week = engine.scoreWeek(Monday, Monday.plusDays(6), scores, config)
        assertEquals(85.0, week.score)
        assertEquals(2, week.daysPlanned)
    }

    @Test
    fun `dropping the worst day lifts the average`() {
        val scores = listOf(
            score(Monday, 90.0),
            score(Tuesday, 90.0),
            score(Wednesday, 90.0),
            score(Monday.plusDays(3), 30.0)
        )
        val config = ScoringConfig(weeklyDropWorstDay = true, weeklyConsistencyBonus = 0.0)
        val week = engine.scoreWeek(Monday, Monday.plusDays(6), scores, config)
        assertEquals(90.0, week.score)
    }

    @Test
    fun `a fully qualifying week earns the consistency bonus`() {
        val scores = listOf(score(Monday, 80.0), score(Tuesday, 90.0))
        val config = ScoringConfig(weeklyConsistencyBonus = 5.0, streakQualifyingScore = 70.0)
        val week = engine.scoreWeek(Monday, Monday.plusDays(6), scores, config)
        assertEquals(90.0, week.score)
        assertEquals(2, week.daysQualified)
    }

    @Test
    fun `best and worst days are reported`() {
        val scores = listOf(score(Monday, 60.0), score(Tuesday, 95.0))
        val week = engine.scoreWeek(Monday, Monday.plusDays(6), scores, ScoringConfig())
        assertEquals(Tuesday, week.bestDay)
        assertEquals(Monday, week.worstDay)
    }

    @Test
    fun `a streak counts backwards from the given day`() {
        val scores = mapOf(
            Monday to 80.0,
            Tuesday to 85.0,
            Wednesday to 40.0
        )
        assertEquals(2, StreakCalculator.currentStreak(scores, Tuesday, 70.0))
        assertEquals(0, StreakCalculator.currentStreak(scores, Wednesday, 70.0))
    }

    @Test
    fun `the inherited streak ignores today`() {
        val scores = mapOf(Monday to 90.0, Tuesday to 10.0)
        assertEquals(1, StreakCalculator.streakBefore(scores, Tuesday, 70.0))
    }

    @Test
    fun `a gap in the calendar breaks the longest run`() {
        val scores = mapOf(
            Monday to 90.0,
            Tuesday to 90.0,
            Monday.plusDays(5) to 90.0
        )
        assertEquals(2, StreakCalculator.longestRun(scores, 70.0))
    }

    @Test
    fun `week boundaries follow the configured first day`() {
        assertEquals(Monday, WeekMath.startOfWeek(Wednesday, DayOfWeek.MONDAY))
        assertEquals(Monday.minusDays(1), WeekMath.startOfWeek(Wednesday, DayOfWeek.SUNDAY))
        assertEquals(7, WeekMath.weekDates(Wednesday, DayOfWeek.SUNDAY).size)
    }

    @Test
    fun `a month grid is always whole weeks`() {
        val grid = WeekMath.monthGrid(LocalDate(2026, 3, 15), DayOfWeek.SUNDAY)
        assertEquals(0, grid.size % 7)
        assertTrue(grid.contains(LocalDate(2026, 3, 1)))
        assertTrue(grid.contains(LocalDate(2026, 3, 31)))
    }
}

class NotificationPlannerTest {

    private val planner = NotificationPlanner()
    private val settings = NotificationSettings(quietHoursEnabled = false)

    @Test
    fun `nothing is planned when notifications are off`() {
        val off = settings.copy(enabled = false)
        assertTrue(planner.planForDay(plan(entries = listOf(entry("a"))), off).isEmpty())
    }

    @Test
    fun `a task reminder fires ahead of its start`() {
        val p = plan(entries = listOf(
            entry("a", timing = TaskTiming.Fixed(time(9))).copy(
                reminders = listOf(ReminderRule("r1", minutesBefore = 15))
            )
        ))
        val reminder = planner.planForDay(p, settings)
            .first { it.kind == NotificationKind.TaskReminder }
        assertEquals(time(8, 45), reminder.fireAt.time)
        assertEquals("a", reminder.entryId)
    }

    @Test
    fun `tasks without their own rule use the default lead time`() {
        val p = plan(entries = listOf(entry("a", timing = TaskTiming.Fixed(time(9)))))
        val reminder = planner.planForDay(p, settings.copy(defaultLeadMinutes = 30))
            .first { it.kind == NotificationKind.TaskReminder }
        assertEquals(time(8, 30), reminder.fireAt.time)
    }

    @Test
    fun `an overdue alert follows the deadline`() {
        val p = plan(entries = listOf(entry("a", timing = TaskTiming.Deadline(time(14)))))
        val overdue = planner.planForDay(p, settings).first { it.kind == NotificationKind.TaskOverdue }
        assertEquals(time(14, 15), overdue.fireAt.time)
    }

    @Test
    fun `completed tasks are not announced`() {
        val p = plan(entries = listOf(
            entry("a", timing = TaskTiming.Fixed(time(9)), status = EntryStatus.Done)
        ))
        val kinds = planner.planForDay(p, settings).map { it.kind }
        assertFalse(kinds.contains(NotificationKind.TaskReminder))
        assertFalse(kinds.contains(NotificationKind.TaskOverdue))
    }

    @Test
    fun `quiet hours filter out everything inside the window`() {
        val quiet = NotificationSettings(
            quietHoursEnabled = true,
            quietStart = time(23),
            quietEnd = time(6, 30)
        )
        val p = plan(entries = listOf(entry("a", timing = TaskTiming.Fixed(time(6)))))
        val fired = planner.planForDay(p, quiet).filter { it.entryId == "a" }
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `quiet hours that wrap midnight are handled in both halves`() {
        val quiet = NotificationSettings(
            quietHoursEnabled = true,
            quietStart = time(23),
            quietEnd = time(6, 30)
        )
        assertTrue(quiet.isQuiet(time(23, 30)))
        assertTrue(quiet.isQuiet(time(2)))
        assertFalse(quiet.isQuiet(time(12)))
    }

    @Test
    fun `a streak at risk is announced before the review`() {
        val p = plan(entries = listOf(entry("a")))
        val notifications = planner.planForDay(
            p,
            settings,
            streakLength = 5,
            currentScore = 20.0,
            streakQualifyingScore = 70.0
        )
        val risk = notifications.first { it.kind == NotificationKind.StreakAtRisk }
        assertEquals(listOf("5", "1"), risk.args)
    }

    @Test
    fun `the weekly report lands on the chosen weekday`() {
        val onReportDay = planner.planForDay(
            plan(date = Sunday, entries = listOf(entry("a"))),
            settings.copy(weeklyReportDay = DayOfWeek.SUNDAY)
        )
        assertTrue(onReportDay.any { it.kind == NotificationKind.WeeklyReport })

        val otherDay = planner.planForDay(
            plan(date = Monday, entries = listOf(entry("a"))),
            settings.copy(weeklyReportDay = DayOfWeek.SUNDAY)
        )
        assertFalse(otherDay.any { it.kind == NotificationKind.WeeklyReport })
    }

    @Test
    fun `results come back in chronological order`() {
        val p = plan(entries = listOf(
            entry("late", timing = TaskTiming.Fixed(time(20))),
            entry("early", timing = TaskTiming.Fixed(time(8)))
        ))
        val fired = planner.planForDay(p, settings)
        assertEquals(fired.sortedBy { it.fireAt }, fired)
    }
}
