package app.yomi.domain.plan

import app.yomi.domain.util.IdGenerator
import app.yomi.model.DayCheckIn
import app.yomi.model.DayPlan
import app.yomi.model.DayTemplate
import app.yomi.model.EntryStatus
import app.yomi.model.PlanEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** How copied entries meet whatever is already on the target day. */
enum class CopyMode {
    /** Wipe the target day and put the copy there. */
    Replace,

    /** Keep the target day and add only what it does not already have. */
    Merge,

    /** Keep the target day and add everything, duplicates included. */
    Append
}

/** Every switch the copy dialog exposes. */
data class CopyOptions(
    val mode: CopyMode = CopyMode.Merge,
    /** Copy items that were typed straight onto the day. */
    val includeAdHoc: Boolean = true,
    /** Copy items that came from a recurring blueprint. */
    val includeRecurring: Boolean = true,
    /** Copy items the user skipped or missed on the source day. */
    val includeUnfinished: Boolean = true,
    /** Reset statuses, timers and sub-mission ticks on the copies. */
    val resetProgress: Boolean = true,
    /** Carry the wake/sleep targets across as well. */
    val includeCheckInTargets: Boolean = false,
    /** Carry the day note across. */
    val includeNote: Boolean = false,
    /** When set, only these categories are copied. */
    val categoryFilter: Set<String> = emptySet(),
    /** Never touch a day the user has already closed. */
    val skipFinalizedTargets: Boolean = true
)

/** What a copy did, per target date. */
data class CopyResult(
    val plans: Map<LocalDate, DayPlan>,
    val entriesCopied: Int,
    val datesChanged: List<LocalDate>,
    val datesSkipped: List<LocalDate>
)

/**
 * Moves a day's shape around the calendar: to tomorrow, to every Sunday of the
 * month, into a reusable template, or back out of one.
 */
class DayCopyEngine(private val ids: IdGenerator) {

    /** Copy [source] onto each of [targets]. */
    fun copy(
        source: DayPlan,
        targets: List<LocalDate>,
        existing: Map<LocalDate, DayPlan>,
        options: CopyOptions = CopyOptions()
    ): CopyResult {
        val payload = selectEntries(source.entries, options)
        val plans = mutableMapOf<LocalDate, DayPlan>()
        val changed = mutableListOf<LocalDate>()
        val skipped = mutableListOf<LocalDate>()
        var copied = 0

        for (target in targets.distinct().sorted()) {
            if (target == source.date) {
                skipped += target
                continue
            }
            val current = existing[target] ?: DayPlan(date = target)
            if (options.skipFinalizedTargets && current.finalized) {
                skipped += target
                continue
            }

            val incoming = payload.map { it.prepare(options) }
            val added = when (options.mode) {
                CopyMode.Replace, CopyMode.Append -> incoming
                CopyMode.Merge -> incoming.filterNot { candidate ->
                    current.entries.any { it.isSameThingAs(candidate) }
                }
            }
            val merged = when (options.mode) {
                CopyMode.Replace -> added
                else -> current.entries + added
            }

            copied += added.size

            plans[target] = current.copy(
                entries = DayMaterializer.sortEntries(merged),
                checkIn = if (options.includeCheckInTargets) {
                    current.checkIn.copy(
                        wakeTarget = source.checkIn.wakeTarget,
                        sleepTarget = source.checkIn.sleepTarget
                    )
                } else {
                    current.checkIn
                },
                note = if (options.includeNote) source.note else current.note
            )
            changed += target
        }

        return CopyResult(plans, copied, changed, skipped)
    }

    /** Copy onto every matching weekday inside `[from, to]`. */
    fun copyToWeekdays(
        source: DayPlan,
        from: LocalDate,
        to: LocalDate,
        weekdays: Set<DayOfWeek>,
        existing: Map<LocalDate, DayPlan>,
        options: CopyOptions = CopyOptions()
    ): CopyResult = copy(source, datesIn(from, to).filter { it.dayOfWeek in weekdays }, existing, options)

    /** Copy onto every day in `[from, to]`. */
    fun copyToRange(
        source: DayPlan,
        from: LocalDate,
        to: LocalDate,
        existing: Map<LocalDate, DayPlan>,
        options: CopyOptions = CopyOptions()
    ): CopyResult = copy(source, datesIn(from, to), existing, options)

    /** Copy onto the next [count] days, starting the day after the source. */
    fun copyToNextDays(
        source: DayPlan,
        count: Int,
        existing: Map<LocalDate, DayPlan>,
        options: CopyOptions = CopyOptions()
    ): CopyResult {
        val targets = List(count.coerceAtLeast(0)) { source.date.plus(it + 1, DateTimeUnit.DAY) }
        return copy(source, targets, existing, options)
    }

    /** Freeze a day into a reusable template. */
    fun toTemplate(
        source: DayPlan,
        name: String,
        emoji: String = "",
        description: String = "",
        createdAt: String = "",
        options: CopyOptions = CopyOptions(resetProgress = true)
    ): DayTemplate = DayTemplate(
        id = ids.newId(),
        name = name,
        emoji = emoji,
        description = description,
        entries = selectEntries(source.entries, options).map { it.prepare(options) },
        checkIn = DayCheckIn(
            wakeTarget = source.checkIn.wakeTarget,
            sleepTarget = source.checkIn.sleepTarget
        ),
        createdAt = createdAt
    )

    /** Stamp a template onto a date. */
    fun applyTemplate(
        template: DayTemplate,
        target: LocalDate,
        existing: DayPlan?,
        options: CopyOptions = CopyOptions()
    ): DayPlan {
        val source = DayPlan(
            date = target.plus(-1, DateTimeUnit.DAY),
            entries = template.entries,
            checkIn = template.checkIn
        )
        val result = copy(
            source = source,
            targets = listOf(target),
            existing = existing?.let { mapOf(target to it) } ?: emptyMap(),
            options = options
        )
        val plan = result.plans[target] ?: existing ?: DayPlan(date = target)
        return plan.copy(sourceTemplateId = template.id)
    }

    /** Duplicate a single entry onto other days — "same gym session on Wed and Fri". */
    fun duplicateEntry(
        entry: PlanEntry,
        targets: List<LocalDate>,
        existing: Map<LocalDate, DayPlan>,
        options: CopyOptions = CopyOptions(mode = CopyMode.Append)
    ): CopyResult {
        val anchor = targets.minOrNull() ?: return CopyResult(emptyMap(), 0, emptyList(), emptyList())
        val source = DayPlan(date = anchor.plus(-1, DateTimeUnit.DAY), entries = listOf(entry))
        return copy(source, targets, existing, options)
    }

    // ------------------------------------------------------------------ utils

    private fun selectEntries(entries: List<PlanEntry>, options: CopyOptions): List<PlanEntry> =
        entries.filter { entry ->
            val adHocOk = if (entry.taskId == null) options.includeAdHoc else options.includeRecurring
            val unfinishedOk = options.includeUnfinished ||
                (entry.status != EntryStatus.Missed && entry.status != EntryStatus.Skipped)
            val categoryOk = options.categoryFilter.isEmpty() ||
                (entry.categoryId != null && entry.categoryId in options.categoryFilter)
            adHocOk && unfinishedOk && categoryOk
        }

    private fun PlanEntry.prepare(options: CopyOptions): PlanEntry =
        if (options.resetProgress) resetForNewDay(ids.newId()) else copy(id = ids.newId())

    private fun datesIn(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from > to) return emptyList()
        val result = mutableListOf<LocalDate>()
        var cursor = from
        while (cursor <= to) {
            result += cursor
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return result
    }
}

/**
 * Two entries describe the same commitment when they come from the same
 * blueprint, or — for hand-written ones — share a title and a start time.
 */
internal fun PlanEntry.isSameThingAs(other: PlanEntry): Boolean = when {
    taskId != null && other.taskId != null -> taskId == other.taskId
    else -> title.trim().equals(other.title.trim(), ignoreCase = true) &&
        timing.startsAt == other.timing.startsAt
}
