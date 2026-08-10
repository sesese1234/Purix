package app.yomi.domain.scoring

import app.yomi.model.AdjustmentKind
import app.yomi.model.Category
import app.yomi.model.ComponentScore
import app.yomi.model.DayCheckIn
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.EntryScore
import app.yomi.model.EntryStatus
import app.yomi.model.InsightKind
import app.yomi.model.PlanEntry
import app.yomi.model.Priority
import app.yomi.model.RoutineRules
import app.yomi.model.ScoreAdjustment
import app.yomi.model.ScoreComponent
import app.yomi.model.ScoreInsight
import app.yomi.model.ScoringConfig
import app.yomi.model.SubtaskRule
import app.yomi.model.minutesOfDay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Extra facts the engine cannot derive from a single day on its own. */
data class ScoringContext(
    val categories: Map<String, Category> = emptyMap(),
    /** Length of the streak this day is part of, used for the streak bonus. */
    val streakLength: Int = 0,
    /** Yesterday's final score, used only to produce an insight. */
    val previousDayScore: Double? = null,
    /** Whether every goal with a daily minimum got its share today. */
    val goalPaceMet: Boolean? = null
)

/**
 * Turns a day into a number between 0 and 100.
 *
 * The score is the weighted sum of five components — completion, punctuality,
 * sub-missions, morning/evening routine and reliability — after which bonuses
 * are added and penalties subtracted. Every threshold, weight and penalty comes
 * from [ScoringConfig]: the engine itself has no opinions of its own.
 *
 * Components that a day cannot exercise (no timed tasks, no check-in, ...) are
 * marked *not applicable* and their weight is redistributed over the rest, so a
 * day of untimed tasks is not silently punished for lacking a clock.
 */
class ScoringEngine {

    fun scoreDay(
        plan: DayPlan,
        config: ScoringConfig,
        context: ScoringContext = ScoringContext()
    ): DayScore {
        plan.frozenScore?.let { if (plan.finalized) return it }

        val entries = plan.scoredEntries
        if (entries.isEmpty() && plan.checkIn.isEmpty) {
            return DayScore.empty(plan.date, config)
        }

        val weighted = entries.map { WeightedEntry(it, weightOf(it, config, context)) }

        val completion = completionComponent(weighted, config)
        val punctuality = punctualityComponent(weighted, config)
        val subtasks = subtaskComponent(weighted)
        val routine = routineComponent(plan.checkIn, config.routine)
        val consistency = consistencyComponent(weighted, config, context.streakLength)

        val raw = listOf(
            ScoreComponent.Completion to completion,
            ScoreComponent.Punctuality to punctuality,
            ScoreComponent.Subtasks to subtasks,
            ScoreComponent.Routine to routine,
            ScoreComponent.Consistency to consistency
        )

        val components = buildComponents(raw, config)
        val baseScore = components.sumOf { it.points }

        val counts = Counts(weighted)
        val adjustments = buildAdjustments(plan, weighted, counts, config, context)

        val bonusTotal = adjustments.filter { it.kind == AdjustmentKind.Bonus }.sumOf { it.points }
        val penaltyTotal = adjustments.filter { it.kind == AdjustmentKind.Penalty }.sumOf { it.points }

        val finalScore = (baseScore + bonusTotal - penaltyTotal)
            .coerceIn(0.0, config.bonuses.scoreCeiling)

        return DayScore(
            date = plan.date,
            score = finalScore.round2(),
            baseScore = baseScore.round2(),
            grade = config.gradeFor(finalScore),
            components = components,
            adjustments = adjustments,
            entryScores = weighted.map { it.toEntryScore(config) },
            tasksPlanned = counts.planned,
            tasksDone = counts.done,
            tasksPartial = counts.partial,
            tasksMissed = counts.missed,
            tasksSkipped = counts.skipped,
            isPerfectDay = isPerfectDay(weighted, counts, config),
            streakLength = context.streakLength,
            insights = buildInsights(plan, weighted, counts, components, config, context, finalScore)
        )
    }

    // ---------------------------------------------------------------- weights

    private fun weightOf(entry: PlanEntry, config: ScoringConfig, context: ScoringContext): Double {
        val category = entry.categoryId?.let { context.categories[it] }
        val base = entry.scoring.weight.toDouble()
        return (base * config.multiplierFor(entry.priority) * (category?.weightMultiplier ?: 1.0))
            .coerceAtLeast(0.0)
    }

    // ------------------------------------------------------------- components

    private fun completionComponent(entries: List<WeightedEntry>, config: ScoringConfig): Ratio {
        val counted = entries.filter { it.countsTowardsCompletion(config) }
        val denominator = counted.sumOf { it.weight }
        if (counted.isEmpty() || denominator <= 0.0) return Ratio.notApplicable()
        val numerator = counted.sumOf { it.weight * it.completionRatio() }
        return Ratio.of(numerator / denominator)
    }

    private fun punctualityComponent(entries: List<WeightedEntry>, config: ScoringConfig): Ratio {
        val tracked = entries.filter { it.tracksPunctuality() }
        if (tracked.isEmpty()) return Ratio.notApplicable()
        val denominator = tracked.sumOf { it.weight }
        if (denominator <= 0.0) return Ratio.notApplicable()
        val numerator = tracked.sumOf { it.weight * it.punctualityRatio(config) }
        return Ratio.of(numerator / denominator)
    }

    private fun subtaskComponent(entries: List<WeightedEntry>): Ratio {
        val withSubtasks = entries.filter {
            it.entry.subtasks.isNotEmpty() &&
                it.entry.scoring.subtaskRule != SubtaskRule.Informational &&
                it.entry.status != EntryStatus.Skipped &&
                it.entry.status != EntryStatus.Deferred
        }
        if (withSubtasks.isEmpty()) return Ratio.notApplicable()
        val denominator = withSubtasks.sumOf { it.weight }
        if (denominator <= 0.0) return Ratio.notApplicable()
        val numerator = withSubtasks.sumOf { weighted ->
            weighted.weight * (weighted.entry.subtaskRatio() ?: 0.0)
        }
        return Ratio.of(numerator / denominator)
    }

    private fun routineComponent(checkIn: DayCheckIn, rules: RoutineRules): Ratio {
        data class Part(val share: Double, val ratio: Double)

        val parts = buildList {
            if (rules.trackWakeUp && checkIn.hasWakeData) {
                add(Part(rules.wakeShare, wakeRatio(checkIn, rules)))
            }
            if (rules.trackSleep && checkIn.sleepTarget != null && checkIn.sleepActual != null) {
                add(Part(rules.sleepShare, sleepRatio(checkIn, rules)))
            }
            if (rules.trackMood) {
                val answered = checkIn.mood != null || checkIn.energy != null || checkIn.journal.isNotBlank()
                if (answered) add(Part(rules.checkInShare, 1.0))
            }
        }
        if (parts.isEmpty()) return Ratio.notApplicable()
        val shareSum = parts.sumOf { it.share }
        if (shareSum <= 0.0) return Ratio.notApplicable()
        return Ratio.of(parts.sumOf { it.share * it.ratio } / shareSum)
    }

    private fun wakeRatio(checkIn: DayCheckIn, rules: RoutineRules): Double {
        val delta = checkIn.wakeDeltaMinutes ?: return 1.0
        if (delta <= rules.wakeGraceMinutes) return 1.0
        val over = delta - rules.wakeGraceMinutes
        return (1.0 - over * rules.wakePenaltyPerMinute / 100.0).coerceIn(0.0, 1.0)
    }

    private fun sleepRatio(checkIn: DayCheckIn, rules: RoutineRules): Double {
        val target = checkIn.sleepTarget ?: return 1.0
        val actual = checkIn.sleepActual ?: return 1.0
        // Bedtimes cross midnight: 00:30 against a 23:30 target is one hour late,
        // not twenty-three hours early.
        var delta = actual.minutesOfDay() - target.minutesOfDay()
        if (delta < -MINUTES_IN_HALF_DAY) delta += MINUTES_IN_DAY
        if (delta > MINUTES_IN_HALF_DAY) delta -= MINUTES_IN_DAY
        if (delta <= rules.wakeGraceMinutes) return 1.0
        val over = delta - rules.wakeGraceMinutes
        return (1.0 - over * rules.wakePenaltyPerMinute / 100.0).coerceIn(0.0, 1.0)
    }

    /**
     * Reliability: how much of what was planned was actually carried out rather
     * than skipped, missed or pushed away — blended with the current streak so
     * consistency over time is worth something today.
     */
    private fun consistencyComponent(
        entries: List<WeightedEntry>,
        config: ScoringConfig,
        streakLength: Int
    ): Ratio {
        if (entries.isEmpty()) return Ratio.notApplicable()
        val total = entries.sumOf { it.weight }
        val reliability = if (total <= 0.0) {
            1.0
        } else {
            entries.filter { it.entry.status.isSuccessful }.sumOf { it.weight } / total
        }
        val streakFactor = (streakLength.toDouble() / STREAK_SATURATION_DAYS).coerceIn(0.0, 1.0)
        val blended = RELIABILITY_SHARE * reliability + (1 - RELIABILITY_SHARE) * streakFactor
        // A config with no streak bonus at all should not drag the component down.
        return Ratio.of(if (config.bonuses.streakBonusCap <= 0.0) reliability else blended)
    }

    private fun buildComponents(
        raw: List<Pair<ScoreComponent, Ratio>>,
        config: ScoringConfig
    ): List<ComponentScore> {
        val weights = config.weights.normalized()
        val applicableWeight = raw.filter { !it.second.notApplicable }
            .sumOf { weights[it.first] ?: 0.0 }

        return raw.map { (component, ratio) ->
            val declared = weights[component] ?: 0.0
            val effective = when {
                ratio.notApplicable -> 0.0
                applicableWeight <= 0.0 -> 0.0
                else -> declared / applicableWeight
            }
            ComponentScore(
                component = component,
                ratio = ratio.value,
                weight = effective,
                points = (effective * ratio.value * 100.0).round2(),
                maxPoints = (effective * 100.0).round2(),
                notApplicable = ratio.notApplicable
            )
        }
    }

    // ------------------------------------------------------------ adjustments

    private fun buildAdjustments(
        plan: DayPlan,
        entries: List<WeightedEntry>,
        counts: Counts,
        config: ScoringConfig,
        context: ScoringContext
    ): List<ScoreAdjustment> {
        val result = mutableListOf<ScoreAdjustment>()
        val bonuses = config.bonuses
        val penalties = config.penalties

        if (isPerfectDay(entries, counts, config) && bonuses.perfectDayBonus > 0.0) {
            result += ScoreAdjustment(AdjustmentKind.Bonus, BONUS_PERFECT_DAY, bonuses.perfectDayBonus)
        }

        if (context.streakLength > 0 && bonuses.streakBonusPerDay > 0.0) {
            val amount = (context.streakLength * bonuses.streakBonusPerDay)
                .coerceAtMost(bonuses.streakBonusCap)
            if (amount > 0.0) {
                result += ScoreAdjustment(AdjustmentKind.Bonus, BONUS_STREAK, amount.round2())
            }
        }

        val subtaskEntries = entries.filter {
            it.entry.subtasks.isNotEmpty() &&
                it.entry.scoring.subtaskRule != SubtaskRule.Informational
        }
        if (subtaskEntries.isNotEmpty() &&
            subtaskEntries.all { it.entry.allSubtasksDone() } &&
            bonuses.allSubtasksBonus > 0.0
        ) {
            result += ScoreAdjustment(AdjustmentKind.Bonus, BONUS_ALL_SUBTASKS, bonuses.allSubtasksBonus)
        }

        val wakeDelta = plan.checkIn.wakeDeltaMinutes
        if (wakeDelta != null && wakeDelta <= 0 && bonuses.earlyRiserBonus > 0.0) {
            result += ScoreAdjustment(AdjustmentKind.Bonus, BONUS_EARLY_RISER, bonuses.earlyRiserBonus)
        }

        if (context.goalPaceMet == true && bonuses.goalPaceBonus > 0.0) {
            result += ScoreAdjustment(AdjustmentKind.Bonus, BONUS_GOAL_PACE, bonuses.goalPaceBonus)
        }

        var penaltyBudget = penalties.maxTotalPenalty.coerceAtLeast(0.0)

        fun addPenalty(label: String, amount: Double) {
            if (amount <= 0.0 || penaltyBudget <= 0.0) return
            val applied = amount.coerceAtMost(penaltyBudget)
            penaltyBudget -= applied
            result += ScoreAdjustment(AdjustmentKind.Penalty, label, applied.round2())
        }

        val missedCost = entries.filter { it.entry.status == EntryStatus.Missed }
            .sumOf { (it.entry.scoring.missedPenaltyOverride?.toDouble() ?: penalties.missedPenalty) }
        addPenalty(PENALTY_MISSED, missedCost)

        val paidSkips = entries.filter { weighted ->
            weighted.entry.status == EntryStatus.Skipped &&
                !(penalties.skipIsFreeWithReason && weighted.entry.skipReason.isNotBlank())
        }
        addPenalty(PENALTY_SKIPPED, paidSkips.size * penalties.skippedPenalty)

        val deferred = entries.count { it.entry.status == EntryStatus.Deferred }
        addPenalty(PENALTY_DEFERRED, deferred * penalties.deferPenalty)

        return result
    }

    private fun isPerfectDay(entries: List<WeightedEntry>, counts: Counts, config: ScoringConfig): Boolean {
        if (counts.planned == 0) return false
        if (counts.missed > 0 || counts.skipped > 0 || counts.deferred > 0) return false
        if (counts.done < counts.planned) return false
        if (config.perfectRequiresCriticalTasks) {
            val criticals = entries.filter { it.entry.priority == Priority.Critical }
            if (criticals.any { it.entry.status != EntryStatus.Done }) return false
        }
        return true
    }

    // --------------------------------------------------------------- insights

    private fun buildInsights(
        plan: DayPlan,
        entries: List<WeightedEntry>,
        counts: Counts,
        components: List<ComponentScore>,
        config: ScoringConfig,
        context: ScoringContext,
        finalScore: Double
    ): List<ScoreInsight> {
        val insights = mutableListOf<ScoreInsight>()

        if (counts.planned == 0) {
            insights += ScoreInsight(InsightKind.EmptyDay)
            return insights
        }

        if (isPerfectDay(entries, counts, config)) {
            insights += ScoreInsight(InsightKind.PerfectDay)
        } else if (counts.done == counts.planned) {
            insights += ScoreInsight(InsightKind.AllTasksDone)
        }

        if (counts.missed > 0) {
            insights += ScoreInsight(InsightKind.MissedTasks, listOf(counts.missed.toString()))
        }

        val punctuality = components.first { it.component == ScoreComponent.Punctuality }
        if (!punctuality.notApplicable) {
            if (punctuality.ratio >= PUNCTUALITY_STRONG) {
                insights += ScoreInsight(InsightKind.PunctualityStrong, listOf(punctuality.ratio.percent()))
            } else if (punctuality.ratio < PUNCTUALITY_WEAK) {
                insights += ScoreInsight(InsightKind.PunctualityWeak, listOf(punctuality.ratio.percent()))
            }
        }

        val wakeDelta = plan.checkIn.wakeDeltaMinutes
        when {
            wakeDelta == null -> insights += ScoreInsight(InsightKind.NoCheckIn)
            wakeDelta <= 0 -> insights += ScoreInsight(InsightKind.EarlyRiser, listOf(abs(wakeDelta).toString()))
            wakeDelta > config.routine.wakeGraceMinutes ->
                insights += ScoreInsight(InsightKind.LateStart, listOf(wakeDelta.toString()))
        }

        val subtasks = components.first { it.component == ScoreComponent.Subtasks }
        if (!subtasks.notApplicable && subtasks.ratio < SUBTASK_WEAK) {
            insights += ScoreInsight(InsightKind.SubtasksIncomplete, listOf(subtasks.ratio.percent()))
        }

        if (context.streakLength >= STREAK_INSIGHT_MIN) {
            insights += ScoreInsight(InsightKind.StreakAlive, listOf(context.streakLength.toString()))
        }
        if (finalScore < config.streakQualifyingScore && context.streakLength > 0) {
            insights += ScoreInsight(InsightKind.StreakAtRisk, listOf(context.streakLength.toString()))
        }

        context.previousDayScore?.let { previous ->
            val delta = finalScore - previous
            if (delta >= SIGNIFICANT_DELTA) {
                insights += ScoreInsight(InsightKind.ImprovedOverYesterday, listOf(delta.round1().toString()))
            } else if (delta <= -SIGNIFICANT_DELTA) {
                insights += ScoreInsight(InsightKind.DeclinedFromYesterday, listOf(abs(delta).round1().toString()))
            }
        }

        return insights
    }

    private companion object {
        const val MINUTES_IN_DAY = 24 * 60
        const val MINUTES_IN_HALF_DAY = 12 * 60
        const val STREAK_SATURATION_DAYS = 7.0
        const val RELIABILITY_SHARE = 0.75
        const val PUNCTUALITY_STRONG = 0.9
        const val PUNCTUALITY_WEAK = 0.6
        const val SUBTASK_WEAK = 0.7
        const val STREAK_INSIGHT_MIN = 2
        const val SIGNIFICANT_DELTA = 8.0

        const val BONUS_PERFECT_DAY = "perfectDay"
        const val BONUS_STREAK = "streak"
        const val BONUS_ALL_SUBTASKS = "allSubtasks"
        const val BONUS_EARLY_RISER = "earlyRiser"
        const val BONUS_GOAL_PACE = "goalPace"
        const val PENALTY_MISSED = "missed"
        const val PENALTY_SKIPPED = "skipped"
        const val PENALTY_DEFERRED = "deferred"
    }
}

/** An entry paired with the weight the configuration gives it. */
private data class WeightedEntry(val entry: PlanEntry, val weight: Double) {

    fun completionRatio(): Double = when (entry.status) {
        EntryStatus.Done -> 1.0
        EntryStatus.Skipped, EntryStatus.Missed, EntryStatus.Deferred -> 0.0
        EntryStatus.Partial -> if (entry.scoring.allowPartial) partialRatio() else 0.0
        EntryStatus.Pending, EntryStatus.InProgress ->
            if (entry.scoring.allowPartial) partialRatio() else 0.0
    }

    private fun partialRatio(): Double {
        val fromSubtasks = entry.subtaskRatio()
        val fromQuantity = entry.quantityRatio()
        return when {
            fromSubtasks != null && fromQuantity != null -> maxOf(fromSubtasks, fromQuantity)
            fromSubtasks != null -> fromSubtasks
            fromQuantity != null -> fromQuantity
            // Nothing measurable: a bare "partial" is worth half.
            entry.status == EntryStatus.Partial -> 0.5
            else -> 0.0
        }
    }

    fun countsTowardsCompletion(config: ScoringConfig): Boolean = when (entry.status) {
        EntryStatus.Skipped, EntryStatus.Deferred -> !config.penalties.skippedLeaveDenominator
        else -> true
    }

    fun tracksPunctuality(): Boolean =
        entry.scoring.punctualityTracked &&
            entry.timing.dueAt != null &&
            (entry.status.isSuccessful || entry.status == EntryStatus.Missed)

    fun minutesLate(): Int? {
        val due = entry.timing.dueAt ?: return null
        val completed = entry.completedAt ?: return null
        return completed.minutesOfDay() - due.minutesOfDay()
    }

    fun punctualityRatio(config: ScoringConfig): Double {
        if (entry.status == EntryStatus.Missed) return 0.0
        val rules = config.punctuality
        val late = minutesLate() ?: return rules.untimedCredit.coerceIn(0.0, 1.0)
        val grace = entry.scoring.graceMinutesOverride ?: rules.graceMinutes
        if (late <= grace) {
            val startedEarly = entry.timing.startsAt?.let { start ->
                entry.completedAt!!.minutesOfDay() <= start.minutesOfDay()
            } ?: false
            return if (startedEarly) 1.0 + rules.earlyCompletionBonusPercent / 100.0 else 1.0
        }
        val over = (late - grace).toDouble()
        val penalty = (over * rules.penaltyPerLateMinute).coerceAtMost(rules.maxPenaltyPercent)
        return (1.0 - penalty / 100.0).coerceIn(0.0, 1.0)
    }

    fun toEntryScore(config: ScoringConfig): EntryScore {
        val completion = completionRatio()
        return EntryScore(
            entryId = entry.id,
            title = entry.title,
            emoji = entry.emoji,
            status = entry.status,
            weight = weight,
            completionRatio = completion,
            punctualityRatio = if (tracksPunctuality()) punctualityRatio(config).coerceAtMost(1.0) else null,
            minutesLate = minutesLate(),
            earnedPoints = (weight * completion).round2(),
            maxPoints = weight.round2()
        )
    }
}

private class Counts(entries: List<WeightedEntry>) {
    val planned = entries.size
    val done = entries.count { it.entry.status == EntryStatus.Done }
    val partial = entries.count { it.entry.status == EntryStatus.Partial }
    val missed = entries.count { it.entry.status == EntryStatus.Missed }
    val skipped = entries.count { it.entry.status == EntryStatus.Skipped }
    val deferred = entries.count { it.entry.status == EntryStatus.Deferred }
}

/** A component result plus the "this day cannot exercise me" flag. */
private class Ratio private constructor(val value: Double, val notApplicable: Boolean) {
    companion object {
        fun of(value: Double) = Ratio(value.coerceIn(0.0, 1.0), false)
        fun notApplicable() = Ratio(0.0, true)
    }
}

internal fun Double.round2(): Double = (this * 100.0).roundToInt() / 100.0
internal fun Double.round1(): Double = (this * 10.0).roundToInt() / 10.0
private fun Double.percent(): String = (this * 100).roundToInt().toString()
