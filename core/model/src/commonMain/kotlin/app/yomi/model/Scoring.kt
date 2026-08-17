package app.yomi.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Relative importance of the five components a day score is built from.
 * The numbers are free-form; the engine normalises them, so a user can type
 * "70 / 30 / 0 / 0 / 0" and get a pure completion-and-punctuality score.
 */
@Serializable
data class ScoreWeights(
    val completion: Int = 50,
    val punctuality: Int = 20,
    val subtasks: Int = 10,
    val routine: Int = 12,
    val consistency: Int = 8
) {
    val total: Int get() = completion + punctuality + subtasks + routine + consistency

    /** Weights as fractions of 1.0. Falls back to pure completion if all are zero. */
    fun normalized(): Map<ScoreComponent, Double> {
        val sum = total
        if (sum <= 0) return mapOf(ScoreComponent.Completion to 1.0)
        return mapOf(
            ScoreComponent.Completion to completion / sum.toDouble(),
            ScoreComponent.Punctuality to punctuality / sum.toDouble(),
            ScoreComponent.Subtasks to subtasks / sum.toDouble(),
            ScoreComponent.Routine to routine / sum.toDouble(),
            ScoreComponent.Consistency to consistency / sum.toDouble()
        )
    }
}

@Serializable
enum class ScoreComponent { Completion, Punctuality, Subtasks, Routine, Consistency }

/** Lateness rules shared by tasks and by the wake-up check-in. */
@Serializable
data class PunctualityRules(
    /** Free minutes before lateness starts to cost anything. */
    val graceMinutes: Int = 10,
    /** Points of the punctuality component lost per late minute, in percent. */
    val penaltyPerLateMinute: Double = 1.5,
    /** Cap on the penalty for a single item, in percent. */
    val maxPenaltyPercent: Double = 100.0,
    /** Bonus for finishing before the window even opens, in percent. */
    val earlyCompletionBonusPercent: Double = 5.0,
    /** Tasks completed with no recorded time count as this fraction on time. */
    val untimedCredit: Double = 0.75
)

/** What it costs to drop the ball. */
@Serializable
data class PenaltyRules(
    /** Percentage points removed from the final score per missed task. */
    val missedPenalty: Double = 2.0,
    /** Same, per consciously skipped task. */
    val skippedPenalty: Double = 0.5,
    /** Skips are free when the user gives a reason. */
    val skipIsFreeWithReason: Boolean = true,
    /** Deferring to another day costs less than dropping it entirely. */
    val deferPenalty: Double = 0.75,
    /** Total penalty cannot take away more than this many points. */
    val maxTotalPenalty: Double = 25.0,
    /** Skipped tasks are removed from the completion denominator. */
    val skippedLeaveDenominator: Boolean = true
)

/** Ways to earn points above and beyond the plan. */
@Serializable
data class BonusRules(
    /** Awarded when every scored task is done and nothing was missed. */
    val perfectDayBonus: Double = 5.0,
    /** Awarded per day of the current streak... */
    val streakBonusPerDay: Double = 0.5,
    /** ...but never more than this in total. */
    val streakBonusCap: Double = 5.0,
    /** Awarded when every sub-mission of every task is checked. */
    val allSubtasksBonus: Double = 2.0,
    /** Awarded when the user got up at or before their wake target. */
    val earlyRiserBonus: Double = 2.0,
    /** Awarded when the day's goal contributions all hit their daily minimum. */
    val goalPaceBonus: Double = 2.0,
    /** The score is clamped here; raise it above 100 to allow overachievement. */
    val scoreCeiling: Double = 100.0
)

/** A named score range, e.g. "S" from 95 up. Fully user editable. */
@Serializable
data class GradeBand(
    val minScore: Double,
    val label: String,
    val emoji: String
)

/** How the wake-up check-in feeds the routine component. */
@Serializable
data class RoutineRules(
    val trackWakeUp: Boolean = true,
    val trackSleep: Boolean = true,
    val trackMood: Boolean = true,
    /** Minutes of lie-in tolerated before the routine component starts to drop. */
    val wakeGraceMinutes: Int = 15,
    val wakePenaltyPerMinute: Double = 1.0,
    /** Weight of wake-up inside the routine component (rest goes to sleep + mood). */
    val wakeShare: Double = 0.6,
    val sleepShare: Double = 0.25,
    val checkInShare: Double = 0.15
)

/**
 * The complete, user-editable rulebook for turning a day into a number.
 * Every knob the scoring engine reads lives here — nothing is hard-coded.
 */
@Serializable
data class ScoringConfig(
    val weights: ScoreWeights = ScoreWeights(),
    val punctuality: PunctualityRules = PunctualityRules(),
    val penalties: PenaltyRules = PenaltyRules(),
    val bonuses: BonusRules = BonusRules(),
    val routine: RoutineRules = RoutineRules(),
    val priorityMultipliers: Map<Priority, Double> = defaultPriorityMultipliers,
    val gradeBands: List<GradeBand> = defaultGradeBands,
    /** A day with no scored tasks reports this score instead of zero. */
    val emptyDayScore: Double = 0.0,
    /** Critical tasks must be done for a day to count as perfect. */
    val perfectRequiresCriticalTasks: Boolean = true,
    /** Days at or above this score count towards streaks. */
    val streakQualifyingScore: Double = 70.0,
    /** Weekly score = average of day scores, optionally dropping the worst day. */
    val weeklyDropWorstDay: Boolean = false,
    /** Days with no plan at all are ignored in the weekly average. */
    val weeklyIgnoreEmptyDays: Boolean = true,
    /** Bonus added to the weekly score when every day qualifies. */
    val weeklyConsistencyBonus: Double = 5.0
) {
    fun multiplierFor(priority: Priority): Double =
        priorityMultipliers[priority] ?: defaultPriorityMultipliers.getValue(priority)

    fun gradeFor(score: Double): GradeBand =
        gradeBands.sortedByDescending { it.minScore }.firstOrNull { score >= it.minScore }
            ?: gradeBands.minByOrNull { it.minScore }
            ?: GradeBand(0.0, "—", "")

    companion object {
        val defaultPriorityMultipliers: Map<Priority, Double> = mapOf(
            Priority.Low to 0.6,
            Priority.Normal to 1.0,
            Priority.High to 1.6,
            Priority.Critical to 2.4
        )

        val defaultGradeBands: List<GradeBand> = listOf(
            GradeBand(96.0, "S", ""),
            GradeBand(88.0, "A", ""),
            GradeBand(78.0, "B", ""),
            GradeBand(66.0, "C", ""),
            GradeBand(50.0, "D", ""),
            GradeBand(0.0, "E", "")
        )
    }
}

/** One line of the score breakdown, ready to be rendered. */
@Serializable
data class ComponentScore(
    val component: ScoreComponent,
    /** 0.0..1.0 achievement inside this component. */
    val ratio: Double,
    /** Share of the final score this component could contribute. */
    val weight: Double,
    /** Points actually contributed. */
    val points: Double,
    /** Points available. */
    val maxPoints: Double,
    /** True when nothing in the day exercised this component. */
    val notApplicable: Boolean = false
)

/** Why points were added or removed, in the user's language. */
@Serializable
data class ScoreAdjustment(
    val kind: AdjustmentKind,
    val label: String,
    val points: Double
)

@Serializable
enum class AdjustmentKind { Bonus, Penalty }

/** What a single entry contributed, used for the "where did my points go" view. */
@Serializable
data class EntryScore(
    val entryId: String,
    val title: String,
    val emoji: String,
    val status: EntryStatus,
    val weight: Double,
    val completionRatio: Double,
    val punctualityRatio: Double?,
    val minutesLate: Int?,
    val earnedPoints: Double,
    val maxPoints: Double
)

/** The final verdict on a day. */
@Serializable
data class DayScore(
    val date: LocalDate,
    /** Final, clamped score. */
    val score: Double,
    /** Score before bonuses and penalties. */
    val baseScore: Double,
    val grade: GradeBand,
    val components: List<ComponentScore>,
    val adjustments: List<ScoreAdjustment>,
    val entryScores: List<EntryScore>,
    val tasksPlanned: Int,
    val tasksDone: Int,
    val tasksPartial: Int,
    val tasksMissed: Int,
    val tasksSkipped: Int,
    val isPerfectDay: Boolean,
    val streakLength: Int,
    val insights: List<ScoreInsight>
) {
    val completionPercent: Double
        get() = if (tasksPlanned == 0) 0.0 else (tasksDone.toDouble() / tasksPlanned) * 100.0

    companion object {
        fun empty(date: LocalDate, config: ScoringConfig): DayScore = DayScore(
            date = date,
            score = config.emptyDayScore,
            baseScore = config.emptyDayScore,
            grade = config.gradeFor(config.emptyDayScore),
            components = emptyList(),
            adjustments = emptyList(),
            entryScores = emptyList(),
            tasksPlanned = 0,
            tasksDone = 0,
            tasksPartial = 0,
            tasksMissed = 0,
            tasksSkipped = 0,
            isPerfectDay = false,
            streakLength = 0,
            insights = listOf(ScoreInsight(InsightKind.EmptyDay, emptyList()))
        )
    }
}

/**
 * A machine-readable observation about a day or a week. The UI turns these into
 * localised sentences, which keeps the domain free of user-facing strings.
 */
@Serializable
data class ScoreInsight(
    val kind: InsightKind,
    /** Values substituted into the localised template, in order. */
    val args: List<String> = emptyList()
)

@Serializable
enum class InsightKind {
    EmptyDay,
    PerfectDay,
    AllTasksDone,
    MissedTasks,
    LateStart,
    EarlyRiser,
    PunctualityStrong,
    PunctualityWeak,
    SubtasksIncomplete,
    StreakAlive,
    StreakBroken,
    StreakAtRisk,
    BestCategory,
    WorstCategory,
    ImprovedOverYesterday,
    DeclinedFromYesterday,
    WeeklyConsistent,
    WeeklyBestDay,
    WeeklyWorstDay,
    WeeklyGoalPace,
    NoCheckIn
}

/** Weekly roll-up, mirroring [DayScore] one level up. */
@Serializable
data class WeekScore(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val score: Double,
    val grade: GradeBand,
    val dayScores: List<DayScore>,
    val adjustments: List<ScoreAdjustment>,
    val daysPlanned: Int,
    val daysQualified: Int,
    val perfectDays: Int,
    val tasksDone: Int,
    val tasksPlanned: Int,
    val bestDay: LocalDate?,
    val worstDay: LocalDate?,
    val longestStreak: Int,
    val insights: List<ScoreInsight>
) {
    val averageDayScore: Double
        get() = if (dayScores.isEmpty()) 0.0 else dayScores.sumOf { it.score } / dayScores.size
}
