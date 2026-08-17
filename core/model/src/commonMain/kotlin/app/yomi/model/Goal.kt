package app.yomi.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A target the user is working towards. Goals are fed automatically by the
 * tasks linked to them (see [GoalLink]) and never need manual bookkeeping.
 */
@Serializable
data class Goal(
    val id: String,
    val title: String,
    val emoji: String = "",
    val description: String = "",
    val type: GoalType = GoalType.Count,
    val target: Double = 5.0,
    val unit: String = "",
    val period: GoalPeriod = GoalPeriod.Week,
    val startDate: LocalDate,
    val deadline: LocalDate? = null,
    val colorArgb: Long = 0xFF4CC9A7,
    /** Tasks that feed this goal directly. */
    val linkedTaskIds: Set<String> = emptySet(),
    /** Every task in these categories feeds the goal as well. */
    val linkedCategoryIds: Set<String> = emptySet(),
    /** Warns the user when the day's contribution falls under this. */
    val minimumDailyProgress: Double? = null,
    val archived: Boolean = false,
    val order: Int = 0
) {
    val isMeasuredInMinutes: Boolean get() = type == GoalType.Minutes
}

/** Computed state of a goal for a specific period. */
@Serializable
data class GoalProgress(
    val goalId: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val current: Double,
    val target: Double,
    /** How much should have been done by now to stay on pace. */
    val expectedByNow: Double,
    val daysElapsed: Int,
    val daysTotal: Int,
    val contributions: List<GoalContributionEntry> = emptyList()
) {
    val ratio: Double get() = if (target <= 0.0) 0.0 else (current / target).coerceIn(0.0, 1.0)
    val isComplete: Boolean get() = target > 0.0 && current >= target
    val isOnPace: Boolean get() = current + 1e-9 >= expectedByNow
    val remaining: Double get() = (target - current).coerceAtLeast(0.0)

    /** Daily amount needed to still land the goal, or null when there is no time left. */
    val requiredPerRemainingDay: Double?
        get() {
            val left = daysTotal - daysElapsed
            if (left <= 0) return null
            return remaining / left
        }
}

/** One task completion's contribution to a goal, kept for the detail view. */
@Serializable
data class GoalContributionEntry(
    val date: LocalDate,
    val entryId: String,
    val title: String,
    val amount: Double
)
