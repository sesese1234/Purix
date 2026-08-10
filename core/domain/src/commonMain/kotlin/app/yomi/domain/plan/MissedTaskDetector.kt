package app.yomi.domain.plan

import app.yomi.model.DayPlan
import app.yomi.model.EntryStatus
import app.yomi.model.GeneralSettings
import app.yomi.model.PlanEntry
import app.yomi.model.minutesOfDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/** Result of a sweep, so the UI can tell the user exactly what changed. */
data class MissedSweep(
    val plan: DayPlan,
    /** Entries that went from open to [EntryStatus.Missed]. */
    val newlyMissed: List<PlanEntry> = emptyList(),
    /** Entries that were half-done when their window closed. */
    val newlyPartial: List<PlanEntry> = emptyList()
) {
    val changed: Boolean get() = newlyMissed.isNotEmpty() || newlyPartial.isNotEmpty()
}

/**
 * Notices when a task's window has closed without it being done.
 *
 * A task is declared missed once `due + grace` has passed. For past days every
 * open task is missed by definition. Tasks with no clock window (`Anytime`) are
 * only missed once the whole day is over — you cannot be late for something
 * that had no time attached to it. Work that was genuinely started is recorded
 * as partial instead of being thrown away.
 */
class MissedTaskDetector {

    fun sweep(plan: DayPlan, now: LocalDateTime, settings: GeneralSettings): MissedSweep {
        if (plan.finalized || !settings.autoDetectMissed) return MissedSweep(plan)

        val today = logicalToday(now, settings.dayStartHour)
        if (plan.date > today) return MissedSweep(plan)

        val newlyMissed = mutableListOf<PlanEntry>()
        val newlyPartial = mutableListOf<PlanEntry>()

        val updated = plan.entries.map { entry ->
            if (!entry.status.isOpen) return@map entry
            val closed = plan.date < today || isOverdue(entry, now, settings)
            if (!closed) return@map entry

            val resolved = entry.resolveClosedWindow()
            when (resolved.status) {
                EntryStatus.Partial -> newlyPartial += resolved
                else -> newlyMissed += resolved
            }
            resolved
        }

        return MissedSweep(plan.copy(entries = updated), newlyMissed, newlyPartial)
    }

    /** Entries that are past due right now but not yet flagged — used for alerts. */
    fun overdueNow(plan: DayPlan, now: LocalDateTime, settings: GeneralSettings): List<PlanEntry> =
        plan.entries.filter { it.status.isOpen && isOverdue(it, now, settings) }

    /**
     * Closes a day: everything still open is resolved and the plan is marked
     * finalised so nothing can silently change afterwards.
     */
    fun finalizeDay(plan: DayPlan): MissedSweep {
        val newlyMissed = mutableListOf<PlanEntry>()
        val newlyPartial = mutableListOf<PlanEntry>()
        val updated = plan.entries.map { entry ->
            if (!entry.status.isOpen) return@map entry
            val resolved = entry.resolveClosedWindow()
            when (resolved.status) {
                EntryStatus.Partial -> newlyPartial += resolved
                else -> newlyMissed += resolved
            }
            resolved
        }
        return MissedSweep(plan.copy(entries = updated), newlyMissed, newlyPartial)
    }

    private fun isOverdue(entry: PlanEntry, now: LocalDateTime, settings: GeneralSettings): Boolean {
        val due = entry.timing.dueAt ?: return false
        val grace = entry.scoring.graceMinutesOverride ?: settings.missedGraceMinutes
        return now.time.minutesOfDay() > due.minutesOfDay() + grace
    }

    private fun PlanEntry.resolveClosedWindow(): PlanEntry {
        val progressed = (subtaskRatio() ?: 0.0) > 0.0 ||
            (quantityRatio() ?: 0.0) > 0.0 ||
            status == EntryStatus.InProgress
        return if (progressed && scoring.allowPartial) {
            copy(status = EntryStatus.Partial)
        } else {
            copy(status = EntryStatus.Missed)
        }
    }

    private fun logicalToday(now: LocalDateTime, dayStartHour: Int): LocalDate =
        if (now.hour < dayStartHour) now.date.minus(1, DateTimeUnit.DAY) else now.date
}
