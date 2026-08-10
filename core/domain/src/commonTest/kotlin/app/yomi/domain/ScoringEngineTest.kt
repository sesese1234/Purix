package app.yomi.domain

import app.yomi.domain.scoring.ScoringContext
import app.yomi.domain.scoring.ScoringEngine
import app.yomi.model.AdjustmentKind
import app.yomi.model.Category
import app.yomi.model.DayCheckIn
import app.yomi.model.EntryStatus
import app.yomi.model.InsightKind
import app.yomi.model.PenaltyRules
import app.yomi.model.Priority
import app.yomi.model.PunctualityRules
import app.yomi.model.ScoreComponent
import app.yomi.model.ScoreWeights
import app.yomi.model.ScoringConfig
import app.yomi.model.SubtaskRule
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoringEngineTest {

    private val engine = ScoringEngine()

    /** Only completion counts, so the arithmetic in each test is obvious. */
    private val completionOnly = ScoringConfig(
        weights = ScoreWeights(completion = 100, punctuality = 0, subtasks = 0, routine = 0, consistency = 0),
        bonuses = ScoringConfig().bonuses.copy(
            perfectDayBonus = 0.0,
            streakBonusPerDay = 0.0,
            allSubtasksBonus = 0.0,
            earlyRiserBonus = 0.0,
            goalPaceBonus = 0.0
        ),
        penalties = PenaltyRules(missedPenalty = 0.0, skippedPenalty = 0.0, deferPenalty = 0.0)
    )

    @Test
    fun `an empty day scores the configured empty value`() {
        val score = engine.scoreDay(plan(entries = emptyList()), ScoringConfig())
        assertEquals(0.0, score.score)
        assertEquals(listOf(InsightKind.EmptyDay), score.insights.map { it.kind })
    }

    @Test
    fun `every task done scores one hundred`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", status = EntryStatus.Done)
            )),
            completionOnly
        )
        assertEquals(100.0, score.score)
        assertEquals(2, score.tasksDone)
    }

    @Test
    fun `half the weight done scores half`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", points = 10, status = EntryStatus.Done),
                entry("b", points = 10, status = EntryStatus.Pending)
            )),
            completionOnly
        )
        assertEquals(50.0, score.score)
    }

    @Test
    fun `priority multipliers make critical tasks worth more`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", points = 10, priority = Priority.Critical, status = EntryStatus.Done),
                entry("b", points = 10, priority = Priority.Low, status = EntryStatus.Pending)
            )),
            completionOnly
        )
        // 24 of 24 + 6 available -> 80%.
        assertEquals(80.0, score.score)
    }

    @Test
    fun `category multipliers are applied on top of priority`() {
        val config = completionOnly
        val context = ScoringContext(
            categories = mapOf("work" to Category("work", "Work", weightMultiplier = 3.0))
        )
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", points = 10, categoryId = "work", status = EntryStatus.Done),
                entry("b", points = 10, status = EntryStatus.Pending)
            )),
            config,
            context
        )
        assertEquals(75.0, score.score)
    }

    @Test
    fun `weighted subtasks give partial completion credit`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    status = EntryStatus.Pending,
                    subtasks = listOf(subtask("s1", done = true), subtask("s2"), subtask("s3", done = true)),
                    scoring = TaskScoring(points = 10, subtaskRule = SubtaskRule.Weighted)
                )
            )),
            completionOnly
        )
        assertEquals(66.67, score.score)
    }

    @Test
    fun `all-required subtasks are all or nothing`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    subtasks = listOf(subtask("s1", done = true), subtask("s2")),
                    scoring = TaskScoring(points = 10, subtaskRule = SubtaskRule.AllRequired)
                )
            )),
            completionOnly
        )
        assertEquals(0.0, score.score)
    }

    @Test
    fun `n of m subtasks reach full credit at the threshold`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    subtasks = listOf(subtask("s1", done = true), subtask("s2", done = true), subtask("s3")),
                    scoring = TaskScoring(points = 10, subtaskRule = SubtaskRule.NOfM, requiredSubtaskCount = 2)
                )
            )),
            completionOnly
        )
        assertEquals(100.0, score.score)
    }

    @Test
    fun `informational subtasks never affect the score`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    status = EntryStatus.Done,
                    subtasks = listOf(subtask("s1"), subtask("s2")),
                    scoring = TaskScoring(points = 10, subtaskRule = SubtaskRule.Informational)
                )
            )),
            completionOnly
        )
        assertEquals(100.0, score.score)
        assertTrue(score.components.first { it.component == ScoreComponent.Subtasks }.notApplicable)
    }

    @Test
    fun `quantity progress counts towards completion`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    status = EntryStatus.Partial,
                    actualQuantity = 5.0,
                    scoring = TaskScoring(points = 10, quantityTarget = 20.0)
                )
            )),
            completionOnly
        )
        assertEquals(25.0, score.score)
    }

    @Test
    fun `skipped tasks leave the denominator by default`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", status = EntryStatus.Skipped, skipReason = "sick")
            )),
            completionOnly
        )
        assertEquals(100.0, score.score)
    }

    @Test
    fun `skipped tasks can be configured to stay in the denominator`() {
        val config = completionOnly.copy(
            penalties = completionOnly.penalties.copy(skippedLeaveDenominator = false)
        )
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", status = EntryStatus.Skipped)
            )),
            config
        )
        assertEquals(50.0, score.score)
    }

    @Test
    fun `finishing on time keeps the punctuality component full`() {
        val config = ScoringConfig(
            weights = ScoreWeights(completion = 0, punctuality = 100, subtasks = 0, routine = 0, consistency = 0),
            bonuses = completionOnly.bonuses,
            penalties = completionOnly.penalties
        )
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    status = EntryStatus.Done,
                    timing = TaskTiming.Fixed(time(9), time(10)),
                    completedAt = time(10)
                )
            )),
            config
        )
        assertEquals(100.0, score.score)
    }

    @Test
    fun `being late costs punctuality points beyond the grace window`() {
        val config = ScoringConfig(
            weights = ScoreWeights(completion = 0, punctuality = 100, subtasks = 0, routine = 0, consistency = 0),
            punctuality = PunctualityRules(graceMinutes = 10, penaltyPerLateMinute = 1.0),
            bonuses = completionOnly.bonuses,
            penalties = completionOnly.penalties
        )
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    status = EntryStatus.Done,
                    timing = TaskTiming.Deadline(time(10)),
                    completedAt = time(10, 40)
                )
            )),
            config
        )
        // 40 late - 10 grace = 30 minutes * 1% = 30% off.
        assertEquals(70.0, score.score)
    }

    @Test
    fun `a per-task grace override wins over the global rule`() {
        val config = ScoringConfig(
            weights = ScoreWeights(completion = 0, punctuality = 100, subtasks = 0, routine = 0, consistency = 0),
            punctuality = PunctualityRules(graceMinutes = 0, penaltyPerLateMinute = 5.0),
            bonuses = completionOnly.bonuses,
            penalties = completionOnly.penalties
        )
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry(
                    "a",
                    status = EntryStatus.Done,
                    timing = TaskTiming.Deadline(time(10)),
                    completedAt = time(10, 25),
                    scoring = TaskScoring(points = 10, graceMinutesOverride = 30)
                )
            )),
            config
        )
        assertEquals(100.0, score.score)
    }

    @Test
    fun `missed tasks score zero punctuality and trigger a penalty`() {
        val config = ScoringConfig(
            penalties = PenaltyRules(missedPenalty = 3.0, maxTotalPenalty = 50.0),
            bonuses = completionOnly.bonuses
        )
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done, timing = TaskTiming.Deadline(time(9)), completedAt = time(9)),
                entry("b", status = EntryStatus.Missed, timing = TaskTiming.Deadline(time(10)))
            )),
            config
        )
        val penalty = score.adjustments.first { it.kind == AdjustmentKind.Penalty }
        assertEquals(3.0, penalty.points)
        assertEquals(1, score.tasksMissed)
    }

    @Test
    fun `the total penalty is capped`() {
        val config = ScoringConfig(
            penalties = PenaltyRules(missedPenalty = 10.0, maxTotalPenalty = 15.0),
            bonuses = completionOnly.bonuses
        )
        val entries = List(5) { entry("m$it", status = EntryStatus.Missed, timing = TaskTiming.Deadline(time(10))) }
        val score = engine.scoreDay(plan(entries = entries), config)
        val total = score.adjustments.filter { it.kind == AdjustmentKind.Penalty }.sumOf { it.points }
        assertEquals(15.0, total)
    }

    @Test
    fun `a skip with a reason is free when the rules say so`() {
        val config = ScoringConfig(
            penalties = PenaltyRules(skippedPenalty = 5.0, skipIsFreeWithReason = true),
            bonuses = completionOnly.bonuses
        )
        val withReason = engine.scoreDay(
            plan(entries = listOf(entry("a", status = EntryStatus.Skipped, skipReason = "family"))),
            config
        )
        val withoutReason = engine.scoreDay(
            plan(entries = listOf(entry("a", status = EntryStatus.Skipped))),
            config
        )
        assertTrue(withReason.adjustments.none { it.kind == AdjustmentKind.Penalty })
        assertTrue(withoutReason.adjustments.any { it.kind == AdjustmentKind.Penalty })
    }

    @Test
    fun `a perfect day earns its bonus and is flagged`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", status = EntryStatus.Done)
            )),
            ScoringConfig()
        )
        assertTrue(score.isPerfectDay)
        assertTrue(score.adjustments.any { it.kind == AdjustmentKind.Bonus })
        assertTrue(score.insights.any { it.kind == InsightKind.PerfectDay })
    }

    @Test
    fun `a day with an unfinished critical task is not perfect`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", priority = Priority.Critical, status = EntryStatus.Partial)
            )),
            ScoringConfig()
        )
        assertFalse(score.isPerfectDay)
    }

    @Test
    fun `waking up late costs routine points`() {
        val config = ScoringConfig(
            weights = ScoreWeights(completion = 0, punctuality = 0, subtasks = 0, routine = 100, consistency = 0),
            bonuses = completionOnly.bonuses,
            penalties = completionOnly.penalties,
            routine = ScoringConfig().routine.copy(
                trackSleep = false,
                trackMood = false,
                wakeGraceMinutes = 15,
                wakePenaltyPerMinute = 1.0
            )
        )
        val onTime = engine.scoreDay(
            plan(
                entries = listOf(entry("a", status = EntryStatus.Done)),
                checkIn = DayCheckIn(wakeTarget = time(7), wakeActual = time(7, 10))
            ),
            config
        )
        val late = engine.scoreDay(
            plan(
                entries = listOf(entry("a", status = EntryStatus.Done)),
                checkIn = DayCheckIn(wakeTarget = time(7), wakeActual = time(8, 0))
            ),
            config
        )
        assertEquals(100.0, onTime.score)
        // 60 late - 15 grace = 45 minutes * 1% = 55% of the routine component.
        assertEquals(55.0, late.score)
    }

    @Test
    fun `going to bed after midnight is measured as being late, not early`() {
        val config = ScoringConfig(
            weights = ScoreWeights(completion = 0, punctuality = 0, subtasks = 0, routine = 100, consistency = 0),
            bonuses = completionOnly.bonuses,
            penalties = completionOnly.penalties,
            routine = ScoringConfig().routine.copy(
                trackWakeUp = false,
                trackMood = false,
                wakeGraceMinutes = 0,
                wakePenaltyPerMinute = 1.0
            )
        )
        val score = engine.scoreDay(
            plan(
                entries = listOf(entry("a", status = EntryStatus.Done)),
                checkIn = DayCheckIn(sleepTarget = time(23, 30), sleepActual = time(0, 30))
            ),
            config
        )
        assertEquals(40.0, score.score)
    }

    @Test
    fun `components that a day cannot exercise redistribute their weight`() {
        // No timings anywhere, so punctuality is not applicable and completion
        // must absorb its share instead of the day being silently punished.
        val config = ScoringConfig(
            weights = ScoreWeights(completion = 50, punctuality = 50, subtasks = 0, routine = 0, consistency = 0),
            bonuses = completionOnly.bonuses,
            penalties = completionOnly.penalties
        )
        val score = engine.scoreDay(
            plan(entries = listOf(entry("a", status = EntryStatus.Done))),
            config
        )
        assertEquals(100.0, score.score)
        assertTrue(score.components.first { it.component == ScoreComponent.Punctuality }.notApplicable)
    }

    @Test
    fun `the streak bonus is capped`() {
        val config = ScoringConfig(
            bonuses = ScoringConfig().bonuses.copy(streakBonusPerDay = 1.0, streakBonusCap = 4.0)
        )
        val score = engine.scoreDay(
            plan(entries = listOf(entry("a", status = EntryStatus.Done))),
            config,
            ScoringContext(streakLength = 30)
        )
        val streakBonus = score.adjustments.first { it.label == "streak" }
        assertEquals(4.0, streakBonus.points)
    }

    @Test
    fun `the score never leaves the configured range`() {
        val config = ScoringConfig(
            bonuses = ScoringConfig().bonuses.copy(
                perfectDayBonus = 50.0,
                streakBonusPerDay = 10.0,
                streakBonusCap = 50.0,
                scoreCeiling = 100.0
            )
        )
        val high = engine.scoreDay(
            plan(entries = listOf(entry("a", status = EntryStatus.Done))),
            config,
            ScoringContext(streakLength = 40)
        )
        assertEquals(100.0, high.score)

        val low = engine.scoreDay(
            plan(entries = List(20) { entry("m$it", status = EntryStatus.Missed) }),
            ScoringConfig(penalties = PenaltyRules(missedPenalty = 50.0, maxTotalPenalty = 500.0))
        )
        assertEquals(0.0, low.score)
    }

    @Test
    fun `a frozen score is returned untouched for a finalised day`() {
        val original = engine.scoreDay(
            plan(entries = listOf(entry("a", status = EntryStatus.Done))),
            completionOnly
        )
        val frozen = plan(
            entries = listOf(entry("a", status = EntryStatus.Missed)),
            finalized = true
        ).copy(frozenScore = original)
        assertEquals(100.0, engine.scoreDay(frozen, completionOnly).score)
    }

    @Test
    fun `excluded tasks are invisible to the score`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", status = EntryStatus.Missed, scoring = TaskScoring(excludedFromScore = true))
            )),
            completionOnly
        )
        assertEquals(100.0, score.score)
        assertEquals(1, score.tasksPlanned)
    }

    @Test
    fun `entry scores add up to the weights they were given`() {
        val score = engine.scoreDay(
            plan(entries = listOf(
                entry("a", points = 10, status = EntryStatus.Done),
                entry("b", points = 30, status = EntryStatus.Pending)
            )),
            completionOnly
        )
        assertEquals(2, score.entryScores.size)
        assertEquals(10.0, score.entryScores.first { it.entryId == "a" }.earnedPoints)
        assertEquals(30.0, score.entryScores.first { it.entryId == "b" }.maxPoints)
    }

    @Test
    fun `grades follow the configured bands`() {
        val config = ScoringConfig()
        assertEquals("S", config.gradeFor(100.0).label)
        assertEquals("A", config.gradeFor(90.0).label)
        assertEquals("E", config.gradeFor(10.0).label)
    }
}
