package app.yomi.domain.scoring

import app.yomi.model.AdjustmentKind
import app.yomi.model.DayScore
import app.yomi.model.InsightKind
import app.yomi.model.ScoreAdjustment
import app.yomi.model.ScoreInsight
import app.yomi.model.ScoringConfig
import app.yomi.model.WeekScore
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.previousOrSame

/** Rolls a set of day scores up into the weekly headline number. */
class WeekScoringEngine {

    fun scoreWeek(
        startDate: LocalDate,
        endDate: LocalDate,
        dayScores: List<DayScore>,
        config: ScoringConfig
    ): WeekScore {
        val inRange = dayScores.filter { it.date in startDate..endDate }.sortedBy { it.date }

        val counted = if (config.weeklyIgnoreEmptyDays) {
            inRange.filter { it.tasksPlanned > 0 }
        } else {
            inRange
        }

        val considered = if (config.weeklyDropWorstDay && counted.size > MIN_DAYS_TO_DROP) {
            val worst = counted.minByOrNull { it.score }
            counted.filterNot { it === worst }
        } else {
            counted
        }

        val base = if (considered.isEmpty()) 0.0 else considered.sumOf { it.score } / considered.size

        val adjustments = mutableListOf<ScoreAdjustment>()
        val qualified = counted.count { it.score >= config.streakQualifyingScore }
        val allQualified = counted.isNotEmpty() && qualified == counted.size
        if (allQualified && config.weeklyConsistencyBonus > 0.0) {
            adjustments += ScoreAdjustment(
                AdjustmentKind.Bonus,
                WEEKLY_CONSISTENCY,
                config.weeklyConsistencyBonus
            )
        }

        val bonus = adjustments.filter { it.kind == AdjustmentKind.Bonus }.sumOf { it.points }
        val penalty = adjustments.filter { it.kind == AdjustmentKind.Penalty }.sumOf { it.points }
        val finalScore = (base + bonus - penalty).coerceIn(0.0, config.bonuses.scoreCeiling)

        val best = counted.maxByOrNull { it.score }
        val worst = counted.minByOrNull { it.score }

        return WeekScore(
            startDate = startDate,
            endDate = endDate,
            score = finalScore.round2(),
            grade = config.gradeFor(finalScore),
            dayScores = inRange,
            adjustments = adjustments,
            daysPlanned = counted.size,
            daysQualified = qualified,
            perfectDays = counted.count { it.isPerfectDay },
            tasksDone = inRange.sumOf { it.tasksDone },
            tasksPlanned = inRange.sumOf { it.tasksPlanned },
            bestDay = best?.date,
            worstDay = if (counted.size > 1) worst?.date else null,
            longestStreak = StreakCalculator.longestRun(
                inRange.associate { it.date to it.score },
                config.streakQualifyingScore
            ),
            insights = buildList {
                if (allQualified && counted.isNotEmpty()) {
                    add(ScoreInsight(InsightKind.WeeklyConsistent, listOf(counted.size.toString())))
                }
                best?.let { add(ScoreInsight(InsightKind.WeeklyBestDay, listOf(it.date.toString(), it.score.round1().toString()))) }
                if (counted.size > 1) {
                    worst?.let { add(ScoreInsight(InsightKind.WeeklyWorstDay, listOf(it.date.toString(), it.score.round1().toString()))) }
                }
            }
        )
    }

    private companion object {
        const val MIN_DAYS_TO_DROP = 3
        const val WEEKLY_CONSISTENCY = "weeklyConsistency"
    }
}

/** Week boundaries that respect the user's chosen first day of the week. */
object WeekMath {
    fun startOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.previousOrSame(firstDayOfWeek)

    fun endOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        startOfWeek(date, firstDayOfWeek).plus(6, DateTimeUnit.DAY)

    fun weekDates(date: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val start = startOfWeek(date, firstDayOfWeek)
        return List(7) { start.plus(it, DateTimeUnit.DAY) }
    }

    fun startOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    fun endOfMonth(date: LocalDate): LocalDate =
        startOfMonth(date).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    /** Every date shown by a month grid, padded to whole weeks. */
    fun monthGrid(date: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val first = startOfWeek(startOfMonth(date), firstDayOfWeek)
        val last = endOfWeek(endOfMonth(date), firstDayOfWeek)
        val days = first.daysUntilInclusive(last)
        return List(days) { first.plus(it, DateTimeUnit.DAY) }
    }

    private fun LocalDate.daysUntilInclusive(other: LocalDate): Int {
        var count = 0
        var cursor = this
        while (cursor <= other) {
            count++
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return count
    }
}

/** Consecutive qualifying days, forwards and backwards. */
object StreakCalculator {

    /** Length of the run of qualifying days ending on [endingAt] (inclusive). */
    fun currentStreak(
        scoresByDate: Map<LocalDate, Double>,
        endingAt: LocalDate,
        qualifyingScore: Double
    ): Int {
        var streak = 0
        var cursor = endingAt
        while (true) {
            val score = scoresByDate[cursor] ?: break
            if (score < qualifyingScore) break
            streak++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    /**
     * The streak a day *inherits* — i.e. the run of qualifying days that ended
     * yesterday. This is what the scoring engine uses so that today's bonus
     * does not depend on today's own score.
     */
    fun streakBefore(
        scoresByDate: Map<LocalDate, Double>,
        date: LocalDate,
        qualifyingScore: Double
    ): Int = currentStreak(scoresByDate, date.minus(1, DateTimeUnit.DAY), qualifyingScore)

    /** The longest run of qualifying days anywhere in the supplied map. */
    fun longestRun(scoresByDate: Map<LocalDate, Double>, qualifyingScore: Double): Int {
        if (scoresByDate.isEmpty()) return 0
        val dates = scoresByDate.keys.sorted()
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in dates) {
            val qualifies = (scoresByDate[date] ?: 0.0) >= qualifyingScore
            val contiguous = previous?.let { it.plus(1, DateTimeUnit.DAY) == date } ?: false
            run = when {
                !qualifies -> 0
                contiguous -> run + 1
                else -> 1
            }
            if (run > longest) longest = run
            previous = date
        }
        return longest
    }
}
