package app.yomi.domain.plan

import app.yomi.domain.recurrence.RecurrenceEngine
import app.yomi.domain.util.IdGenerator
import app.yomi.model.Catalog
import app.yomi.model.DayCheckIn
import app.yomi.model.DayPlan
import app.yomi.model.GeneralSettings
import app.yomi.model.PlanEntry
import app.yomi.model.TaskDefinition
import app.yomi.model.TaskTiming
import app.yomi.model.toState
import kotlinx.datetime.LocalDate

/**
 * Turns the user's library of recurring blueprints into the concrete list of
 * things to do on a given date.
 *
 * The contract that keeps history honest: entries already stored for a date are
 * never overwritten. New blueprint occurrences are *added*, blueprints that no
 * longer fire are removed only while they are still untouched, and everything
 * the user has interacted with stays exactly as it was.
 */
class DayMaterializer(private val ids: IdGenerator) {

    fun materialize(
        date: LocalDate,
        catalog: Catalog,
        settings: GeneralSettings,
        existing: DayPlan? = null
    ): DayPlan {
        val stored = existing ?: DayPlan(date = date)
        if (stored.finalized) return stored

        val dueTasks = catalog.tasks.filter { task ->
            !task.archived && RecurrenceEngine.occursOn(task.schedule, date)
        }

        val byTaskId = stored.entries.filter { it.taskId != null }.associateBy { it.taskId!! }
        val kept = mutableListOf<PlanEntry>()

        // 1. Ad-hoc entries always survive: the user typed them in by hand.
        kept += stored.entries.filter { it.taskId == null }

        // 2. Entries backed by a blueprint that still fires today are preserved
        //    as-is, so status, timings and sub-mission ticks are never lost.
        val dueIds = dueTasks.map { it.id }.toSet()
        kept += stored.entries.filter { it.taskId != null && it.taskId in dueIds }

        // 3. Entries whose blueprint no longer fires are dropped only when the
        //    user never touched them; a completed one stays as a record.
        kept += stored.entries.filter { entry ->
            entry.taskId != null && entry.taskId !in dueIds &&
                (entry.status.isResolved || entry.status == app.yomi.model.EntryStatus.InProgress)
        }

        // 4. Blueprints that fire today but have no entry yet get one.
        val newEntries = dueTasks
            .filter { it.id !in byTaskId }
            .map { it.toEntry(ids.newId()) }

        val all = (kept + newEntries).distinctBy { it.id }
        val ordered = sortEntries(all)

        val checkIn = stored.checkIn.withDefaults(settings)

        return stored.copy(date = date, entries = ordered, checkIn = checkIn)
    }

    /** A fresh, empty day with only the wake/sleep targets pre-filled. */
    fun emptyDay(date: LocalDate, settings: GeneralSettings): DayPlan =
        DayPlan(date = date, checkIn = DayCheckIn().withDefaults(settings))

    companion object {
        /**
         * Timeline order: scheduled items by clock time, then unscheduled ones,
         * with the user's manual ordering used as the tie-breaker.
         */
        fun sortEntries(entries: List<PlanEntry>): List<PlanEntry> =
            entries.sortedWith(
                compareBy<PlanEntry> { it.timing.startsAt?.let { t -> t.hour * 60 + t.minute } ?: Int.MAX_VALUE }
                    .thenBy { it.timing.dueAt?.let { t -> t.hour * 60 + t.minute } ?: Int.MAX_VALUE }
                    .thenBy { it.order }
                    .thenBy { it.title }
            ).mapIndexed { index, entry -> entry.copy(order = index) }
    }
}

/** Snapshot a blueprint into a concrete entry for a day. */
fun TaskDefinition.toEntry(entryId: String): PlanEntry = PlanEntry(
    id = entryId,
    taskId = id,
    title = title,
    emoji = emoji,
    notes = notes,
    categoryId = categoryId,
    priority = priority,
    energy = energy,
    timing = timing,
    scoring = scoring,
    subtasks = subtasks.map { it.toState() },
    reminders = reminders,
    goalLinks = goalLinks,
    order = order
)

/** Copy an entry for another day, resetting everything that is day-specific. */
fun PlanEntry.resetForNewDay(newId: String): PlanEntry = copy(
    id = newId,
    status = app.yomi.model.EntryStatus.Pending,
    startedAt = null,
    completedAt = null,
    skipReason = "",
    deferredTo = null,
    actualQuantity = null,
    subtasks = subtasks.map { it.copy(done = false) }
)

private fun DayCheckIn.withDefaults(settings: GeneralSettings): DayCheckIn = copy(
    wakeTarget = wakeTarget ?: settings.defaultWakeTime,
    sleepTarget = sleepTarget ?: settings.defaultSleepTime
)

/** Human-facing helper: does this entry still have a clock window today? */
fun PlanEntry.isScheduled(): Boolean = timing != TaskTiming.Anytime
