package app.yomi.domain.scoring

import app.yomi.model.Category
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.Goal
import app.yomi.model.ScoringConfig
import app.yomi.domain.goals.GoalEngine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Scores a whole journal in one pass.
 *
 * Day scores are not independent: the streak bonus a day receives depends on
 * the days before it. Walking the calendar in order — and feeding each day the
 * streak that *ended yesterday* — is what keeps the numbers stable and free of
 * circular reasoning.
 */
class ScoreBook(
    private val scoringEngine: ScoringEngine = ScoringEngine(),
    private val goalEngine: GoalEngine = GoalEngine()
) {

    fun computeAll(
        days: Collection<DayPlan>,
        config: ScoringConfig,
        categories: List<Category> = emptyList(),
        goals: List<Goal> = emptyList()
    ): Map<LocalDate, DayScore> {
        val ordered = days.sortedBy { it.date }
        val categoryMap = categories.associateBy { it.id }
        val result = LinkedHashMap<LocalDate, DayScore>(ordered.size)
        val scoreValues = HashMap<LocalDate, Double>(ordered.size)

        for (plan in ordered) {
            val streak = StreakCalculator.streakBefore(
                scoreValues,
                plan.date,
                config.streakQualifyingScore
            )
            val previous = scoreValues[plan.date.previousDay()]
            val score = scoringEngine.scoreDay(
                plan = plan,
                config = config,
                context = ScoringContext(
                    categories = categoryMap,
                    streakLength = streak,
                    previousDayScore = previous,
                    goalPaceMet = goalEngine.dailyPaceMet(goals, plan)
                )
            )
            result[plan.date] = score
            scoreValues[plan.date] = score.score
        }
        return result
    }

    /** The run of qualifying days ending today, for the headline streak counter. */
    fun currentStreak(
        scores: Map<LocalDate, DayScore>,
        today: LocalDate,
        config: ScoringConfig
    ): Int {
        val values = scores.mapValues { it.value.score }
        val todayQualifies = (values[today] ?: 0.0) >= config.streakQualifyingScore
        return if (todayQualifies) {
            StreakCalculator.currentStreak(values, today, config.streakQualifyingScore)
        } else {
            StreakCalculator.streakBefore(values, today, config.streakQualifyingScore)
        }
    }
}

private fun LocalDate.previousDay(): LocalDate = minus(1, DateTimeUnit.DAY)
