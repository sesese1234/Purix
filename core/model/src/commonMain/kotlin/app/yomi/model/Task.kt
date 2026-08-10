package app.yomi.model

import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * When a task is supposed to happen. The variant chosen decides how the
 * punctuality component of the score is computed and when the missed-task
 * detector gives up on an entry.
 */
@Serializable
sealed interface TaskTiming {

    /** Anchored to the clock: "07:00–07:30". */
    @Serializable
    @SerialName("fixed")
    data class Fixed(
        val start: LocalTime,
        val end: LocalTime? = null
    ) : TaskTiming

    /** Flexible inside a window: "somewhere between 16:00 and 20:00, takes 45m". */
    @Serializable
    @SerialName("window")
    data class Window(
        val earliest: LocalTime,
        val latest: LocalTime,
        val durationMinutes: Int = 30
    ) : TaskTiming

    /** Only an end bound matters: "before 22:00". */
    @Serializable
    @SerialName("deadline")
    data class Deadline(val by: LocalTime) : TaskTiming

    /** No clock constraint at all. Never affects punctuality. */
    @Serializable
    @SerialName("anytime")
    data object Anytime : TaskTiming

    /** The moment the entry becomes actionable, if any. */
    val startsAt: LocalTime?
        get() = when (this) {
            is Fixed -> start
            is Window -> earliest
            is Deadline -> null
            Anytime -> null
        }

    /** The moment after which the entry counts as late. */
    val dueAt: LocalTime?
        get() = when (this) {
            is Fixed -> end ?: start
            is Window -> latest
            is Deadline -> by
            Anytime -> null
        }

    /** Planned length in minutes, when the timing implies one. */
    val plannedMinutes: Int?
        get() = when (this) {
            is Fixed -> end?.let { (it.minutesOfDay() - start.minutesOfDay()).coerceAtLeast(0) }
            is Window -> durationMinutes
            is Deadline -> null
            Anytime -> null
        }

    val isScheduled: Boolean get() = this !is Anytime
}

/** A sub-mission of a task, as defined on the blueprint. */
@Serializable
data class SubtaskDefinition(
    val id: String,
    val title: String,
    /** Relative importance inside the parent when [SubtaskRule.Weighted] is used. */
    val weight: Int = 1,
    /** Optional sub-missions never block the parent from being "done". */
    val optional: Boolean = false
) {
    val effectiveWeight: Int get() = if (optional) 0 else weight.coerceAtLeast(0)
}

/** The runtime state of a sub-mission on a specific day. */
@Serializable
data class SubtaskState(
    val id: String,
    val title: String,
    val weight: Int = 1,
    val optional: Boolean = false,
    val done: Boolean = false
) {
    val effectiveWeight: Int get() = if (optional) 0 else weight.coerceAtLeast(0)
}

fun SubtaskDefinition.toState(): SubtaskState =
    SubtaskState(id = id, title = title, weight = weight, optional = optional, done = false)

/** Everything about how a single task earns (or loses) points. */
@Serializable
data class TaskScoring(
    /** Base worth of the task relative to its siblings. */
    val points: Int = 10,
    val subtaskRule: SubtaskRule = SubtaskRule.Weighted,
    /** Only meaningful for [SubtaskRule.NOfM]. */
    val requiredSubtaskCount: Int = 0,
    /** Overrides [PunctualityRules.graceMinutes] for this task only. */
    val graceMinutesOverride: Int? = null,
    /** When false the task is excluded from the punctuality component. */
    val punctualityTracked: Boolean = true,
    /** When false a half-finished task scores zero instead of a fraction. */
    val allowPartial: Boolean = true,
    /** Overrides [PenaltyRules.missedPenalty] for this task only. */
    val missedPenaltyOverride: Int? = null,
    /** Measurable tasks: "read 20 pages". */
    val quantityTarget: Double? = null,
    val quantityUnit: String = "",
    /** Excluded from the day score entirely — useful for reference items. */
    val excludedFromScore: Boolean = false
) {
    val weight: Int get() = points.coerceIn(0, 1_000)
}

/** How far ahead of a task a reminder fires. */
@Serializable
data class ReminderRule(
    val id: String,
    /** Minutes before [TaskTiming.startsAt] (or [TaskTiming.dueAt] when there is no start). */
    val minutesBefore: Int = 10,
    val enabled: Boolean = true
)

/**
 * The blueprint of a recurring task. Day plans hold *snapshots* of these, so
 * editing a definition never rewrites history.
 */
@Serializable
data class TaskDefinition(
    val id: String,
    val title: String,
    val emoji: String = "✨",
    val notes: String = "",
    val categoryId: String? = null,
    val priority: Priority = Priority.Normal,
    val energy: EnergyLevel = EnergyLevel.Medium,
    val timing: TaskTiming = TaskTiming.Anytime,
    val schedule: RecurrenceRule,
    val subtasks: List<SubtaskDefinition> = emptyList(),
    val scoring: TaskScoring = TaskScoring(),
    val reminders: List<ReminderRule> = emptyList(),
    val goalLinks: List<GoalLink> = emptyList(),
    val archived: Boolean = false,
    val createdAt: String = "",
    val order: Int = 0
)

/** Connects a task to a goal and says what completing it is worth over there. */
@Serializable
data class GoalLink(
    val goalId: String,
    val contribution: GoalContribution = GoalContribution.PerCompletion,
    /** Multiplier applied on top of the contribution unit. */
    val factor: Double = 1.0
)
