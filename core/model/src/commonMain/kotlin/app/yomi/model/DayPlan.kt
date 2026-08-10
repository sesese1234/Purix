package app.yomi.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * One planned item on one day. Carries a full snapshot of the task blueprint so
 * that editing (or deleting) the definition later cannot retroactively change a
 * score that has already been recorded.
 */
@Serializable
data class PlanEntry(
    val id: String,
    /** Source blueprint, or null for an item added straight onto the day. */
    val taskId: String? = null,
    val title: String,
    val emoji: String = "✨",
    val notes: String = "",
    val categoryId: String? = null,
    val priority: Priority = Priority.Normal,
    val energy: EnergyLevel = EnergyLevel.Medium,
    val timing: TaskTiming = TaskTiming.Anytime,
    val scoring: TaskScoring = TaskScoring(),
    val subtasks: List<SubtaskState> = emptyList(),
    val reminders: List<ReminderRule> = emptyList(),
    val goalLinks: List<GoalLink> = emptyList(),
    val status: EntryStatus = EntryStatus.Pending,
    val startedAt: LocalTime? = null,
    val completedAt: LocalTime? = null,
    val skipReason: String = "",
    val deferredTo: LocalDate? = null,
    /** Measured result for tasks with a [TaskScoring.quantityTarget]. */
    val actualQuantity: Double? = null,
    val order: Int = 0
) {
    val doneSubtasks: Int get() = subtasks.count { it.done }
    val requiredSubtasks: Int get() = subtasks.count { !it.optional }

    val hasSubtasks: Boolean get() = subtasks.isNotEmpty()

    /**
     * The fraction of this entry that has been carried out, in `0.0..1.0`,
     * independent of punctuality. Sub-mission rules are applied here.
     */
    fun completionRatio(): Double = when (status) {
        EntryStatus.Done -> 1.0
        EntryStatus.Skipped, EntryStatus.Missed, EntryStatus.Deferred -> 0.0
        EntryStatus.Pending, EntryStatus.InProgress -> subtaskRatioOrZero()
        EntryStatus.Partial -> if (scoring.allowPartial) subtaskRatioOrZero().coerceAtLeast(quantityRatio() ?: 0.0) else 0.0
    }

    private fun subtaskRatioOrZero(): Double {
        if (status.isOpen && subtasks.none { it.done }) return 0.0
        return subtaskRatio() ?: 0.0
    }

    /** `null` when sub-missions do not drive the ratio at all. */
    fun subtaskRatio(): Double? {
        if (subtasks.isEmpty()) return null
        return when (scoring.subtaskRule) {
            SubtaskRule.Informational -> null
            SubtaskRule.AllRequired -> {
                val required = subtasks.filter { !it.optional }
                if (required.isEmpty()) null
                else if (required.all { it.done }) 1.0 else 0.0
            }

            SubtaskRule.NOfM -> {
                val need = scoring.requiredSubtaskCount.coerceIn(1, subtasks.size)
                (subtasks.count { it.done }.toDouble() / need).coerceIn(0.0, 1.0)
            }

            SubtaskRule.Weighted -> {
                val total = subtasks.sumOf { it.effectiveWeight }
                if (total <= 0) null
                else subtasks.filter { it.done }.sumOf { it.effectiveWeight }.toDouble() / total
            }
        }
    }

    /** Progress towards [TaskScoring.quantityTarget], or `null` when not measurable. */
    fun quantityRatio(): Double? {
        val target = scoring.quantityTarget ?: return null
        if (target <= 0.0) return null
        val actual = actualQuantity ?: return 0.0
        return (actual / target).coerceIn(0.0, 1.0)
    }

    /** True when every non-optional sub-mission is checked. */
    fun allSubtasksDone(): Boolean =
        subtasks.isNotEmpty() && subtasks.filter { !it.optional }.all { it.done }

    fun countsForScore(): Boolean = !scoring.excludedFromScore
}

/**
 * The daily check-in: when the user actually got up, when they went to bed and
 * how the day felt. Wake-up punctuality is a first-class part of the score.
 */
@Serializable
data class DayCheckIn(
    val wakeTarget: LocalTime? = null,
    val wakeActual: LocalTime? = null,
    val sleepTarget: LocalTime? = null,
    val sleepActual: LocalTime? = null,
    /** 1..5, or null when not answered. */
    val mood: Int? = null,
    /** 1..5, or null when not answered. */
    val energy: Int? = null,
    val journal: String = ""
) {
    val hasWakeData: Boolean get() = wakeTarget != null && wakeActual != null

    /** Minutes late (positive) or early (negative) relative to the wake target. */
    val wakeDeltaMinutes: Int?
        get() {
            val target = wakeTarget ?: return null
            val actual = wakeActual ?: return null
            return actual.minutesOfDay() - target.minutesOfDay()
        }

    val isEmpty: Boolean
        get() = wakeActual == null && sleepActual == null && mood == null &&
            energy == null && journal.isBlank()
}

/** Everything that happened, or is planned to happen, on a single date. */
@Serializable
data class DayPlan(
    val date: LocalDate,
    val entries: List<PlanEntry> = emptyList(),
    val checkIn: DayCheckIn = DayCheckIn(),
    val note: String = "",
    /**
     * Set once the day has been reviewed and closed. A finalised day is no
     * longer touched by the missed-task detector.
     */
    val finalized: Boolean = false,
    /** Score frozen at finalisation, so history is stable even if rules change. */
    val frozenScore: DayScore? = null,
    /** Name of the template this day was generated from, for provenance. */
    val sourceTemplateId: String? = null
) {
    val isEmpty: Boolean get() = entries.isEmpty() && checkIn.isEmpty && note.isBlank()

    fun entry(id: String): PlanEntry? = entries.firstOrNull { it.id == id }

    fun withEntry(id: String, transform: (PlanEntry) -> PlanEntry): DayPlan =
        copy(entries = entries.map { if (it.id == id) transform(it) else it })

    val scoredEntries: List<PlanEntry> get() = entries.filter { it.countsForScore() }
}

/** A saved day layout that can be applied to any date. */
@Serializable
data class DayTemplate(
    val id: String,
    val name: String,
    val emoji: String = "🗂️",
    val description: String = "",
    val entries: List<PlanEntry> = emptyList(),
    val checkIn: DayCheckIn = DayCheckIn(),
    val createdAt: String = "",
    val order: Int = 0
)

/** A colour-coded bucket used for grouping, filtering and weighting. */
@Serializable
data class Category(
    val id: String,
    val name: String,
    val emoji: String = "🏷️",
    /** ARGB, stored as a signed long so it round-trips through JSON cleanly. */
    val colorArgb: Long = 0xFF7C6BF2,
    /** Multiplies the weight of every task in this category. */
    val weightMultiplier: Double = 1.0,
    val order: Int = 0,
    val archived: Boolean = false
)
