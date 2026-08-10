package app.yomi.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How much a task matters. Priorities feed the scoring engine through
 * [ScoringConfig.priorityMultipliers] so a user can decide, for example, that
 * a `Critical` task is worth three ordinary ones.
 */
@Serializable
enum class Priority {
    @SerialName("low") Low,
    @SerialName("normal") Normal,
    @SerialName("high") High,
    @SerialName("critical") Critical;

    val sortKey: Int get() = ordinal
}

/** Self-reported effort needed. Used for planning hints and insights, never for scoring. */
@Serializable
enum class EnergyLevel {
    @SerialName("light") Light,
    @SerialName("medium") Medium,
    @SerialName("deep") Deep
}

/**
 * Coarse buckets used to group a day's timeline. Boundaries are configurable
 * through [app.yomi.model.GeneralSettings.dayPartBoundaries].
 */
@Serializable
enum class DayPart {
    @SerialName("earlyMorning") EarlyMorning,
    @SerialName("morning") Morning,
    @SerialName("afternoon") Afternoon,
    @SerialName("evening") Evening,
    @SerialName("night") Night,
    @SerialName("anytime") Anytime
}

/** Lifecycle of a single planned item on a given day. */
@Serializable
enum class EntryStatus {
    /** Not acted on yet and still inside its time window. */
    @SerialName("pending") Pending,

    /** Explicitly started; used for timing accuracy and "in focus" UI. */
    @SerialName("inProgress") InProgress,

    /** Fully completed. */
    @SerialName("done") Done,

    /** Completed only in part — some sub-missions are still open. */
    @SerialName("partial") Partial,

    /** Consciously skipped by the user (may be penalty-free, see [PenaltyRules]). */
    @SerialName("skipped") Skipped,

    /** Detected by the app as not done after its window closed. */
    @SerialName("missed") Missed,

    /** Moved to another date during the end-of-day review. */
    @SerialName("deferred") Deferred;

    val isResolved: Boolean
        get() = this == Done || this == Partial || this == Skipped || this == Missed || this == Deferred

    val isSuccessful: Boolean
        get() = this == Done || this == Partial

    val isOpen: Boolean
        get() = this == Pending || this == InProgress
}

/** How sub-missions roll up into the parent task's completion ratio. */
@Serializable
enum class SubtaskRule {
    /** Every sub-mission must be checked for the parent to count as done. */
    @SerialName("allRequired") AllRequired,

    /** At least [TaskScoring.requiredSubtaskCount] sub-missions must be checked. */
    @SerialName("nOfM") NOfM,

    /** Completion ratio equals the checked weight divided by the total weight. */
    @SerialName("weighted") Weighted,

    /** Sub-missions are informational only and never influence the score. */
    @SerialName("informational") Informational
}

/** Where a linked task sends progress when it is completed. */
@Serializable
enum class GoalContribution {
    /** +1 per completion. */
    @SerialName("perCompletion") PerCompletion,

    /** Adds the entry's measured quantity (pages, km, ...). */
    @SerialName("perQuantity") PerQuantity,

    /** Adds the entry's planned duration in minutes. */
    @SerialName("perMinute") PerMinute
}

@Serializable
enum class GoalType {
    /** A number of completions, e.g. "work out 4 times a week". */
    @SerialName("count") Count,

    /** Accumulated minutes, e.g. "3 hours of study a week". */
    @SerialName("minutes") Minutes,

    /** Accumulated custom units, e.g. "300 pages a month". */
    @SerialName("quantity") Quantity,

    /** Average day score across the period, e.g. "average 85 this week". */
    @SerialName("averageScore") AverageScore,

    /** Consecutive qualifying days, e.g. "a 30 day streak". */
    @SerialName("streak") Streak
}

@Serializable
enum class GoalPeriod {
    @SerialName("day") Day,
    @SerialName("week") Week,
    @SerialName("month") Month,

    /** Runs from the goal's start date until its deadline without resetting. */
    @SerialName("total") Total
}

/** Minutes since midnight, the canonical way this app talks about clock times. */
fun LocalTime.minutesOfDay(): Int = hour * 60 + minute

fun localTimeOfMinutes(minutes: Int): LocalTime {
    val clamped = minutes.coerceIn(0, 24 * 60 - 1)
    return LocalTime(clamped / 60, clamped % 60)
}

/** ISO order, Monday first. Rotated for display by [GeneralSettings.firstDayOfWeek]. */
val AllWeekdays: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

/** The week as the user sees it, starting from [first]. */
fun weekdaysStartingFrom(first: DayOfWeek): List<DayOfWeek> {
    val start = AllWeekdays.indexOf(first).coerceAtLeast(0)
    return List(7) { AllWeekdays[(start + it) % 7] }
}
