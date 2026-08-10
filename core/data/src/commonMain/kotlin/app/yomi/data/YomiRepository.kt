package app.yomi.data

import app.yomi.data.storage.CatalogStore
import app.yomi.data.storage.JournalStore
import app.yomi.data.storage.SettingsStore
import app.yomi.data.storage.YomiJson
import app.yomi.domain.goals.GoalEngine
import app.yomi.domain.plan.CopyOptions
import app.yomi.domain.plan.CopyResult
import app.yomi.domain.plan.DayCopyEngine
import app.yomi.domain.plan.DayMaterializer
import app.yomi.domain.plan.MissedTaskDetector
import app.yomi.domain.plan.resetForNewDay
import app.yomi.domain.scoring.ScoreBook
import app.yomi.domain.scoring.WeekMath
import app.yomi.domain.scoring.WeekScoringEngine
import app.yomi.domain.seed.DefaultContent
import app.yomi.domain.time.YomiClock
import app.yomi.domain.util.IdGenerator
import app.yomi.model.AppLanguage
import app.yomi.model.AppSettings
import app.yomi.model.Catalog
import app.yomi.model.Category
import app.yomi.model.DayCheckIn
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.DayTemplate
import app.yomi.model.EntryStatus
import app.yomi.model.Goal
import app.yomi.model.GoalProgress
import app.yomi.model.PlanEntry
import app.yomi.model.TaskDefinition
import app.yomi.model.WeekScore
import app.yomi.model.YomiBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * The single door between the user interface and everything underneath it.
 *
 * Screens never touch a file, an engine or a clock directly: they observe the
 * flows exposed here and call the intent-shaped methods below. That keeps every
 * feature module free of persistence concerns and makes the whole app testable
 * by swapping the clock and the file system.
 */
class YomiRepository(
    private val settingsStore: SettingsStore,
    private val catalogStore: CatalogStore,
    private val journalStore: JournalStore,
    private val clock: YomiClock,
    private val ids: IdGenerator
) {
    private val materializer = DayMaterializer(ids)
    private val copyEngine = DayCopyEngine(ids)
    private val missedDetector = MissedTaskDetector()
    private val scoreBook = ScoreBook()
    private val weekEngine = WeekScoringEngine()
    private val goalEngine = GoalEngine()

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val catalog: StateFlow<Catalog> = catalogStore.catalog
    val days: StateFlow<Map<LocalDate, DayPlan>> = journalStore.days

    private val scoreCache = MutableStateFlow<Map<LocalDate, DayScore>>(emptyMap())

    /**
     * Every day's score. Recomputed synchronously after each write rather than
     * on a background flow, so a caller that reads a score immediately after a
     * mutation is guaranteed to see the result of that mutation.
     */
    val scores: StateFlow<Map<LocalDate, DayScore>> = scoreCache.asStateFlow()

    private fun recomputeScores() {
        scoreCache.value = scoreBook.computeAll(
            days = days.value.values,
            config = settings.value.scoring,
            categories = catalog.value.categories,
            goals = catalog.value.goals
        )
    }

    // Every persistence call in this class goes through these three helpers, so
    // there is exactly one place where derived state can fall out of date.
    private suspend fun saveDays(plans: Collection<DayPlan>) {
        journalStore.putAll(plans)
        recomputeScores()
    }

    private suspend fun saveDay(plan: DayPlan) = saveDays(listOf(plan))

    private suspend fun editCatalog(transform: (Catalog) -> Catalog) {
        catalogStore.update(transform)
        recomputeScores()
    }

    private suspend fun editSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
        recomputeScores()
    }

    // ------------------------------------------------------------- lifecycle

    /** Loads everything from disk and seeds a starter library on a first run. */
    suspend fun initialize() {
        settingsStore.load()
        val firstRun = catalogStore.load()
        journalStore.load()
        if (firstRun) {
            val language = settings.value.general.language
            catalogStore.replace(DefaultContent.catalog(today(), language))
        }
        recomputeScores()
        ensureDay(today())
    }

    fun today(): LocalDate = clock.today(settings.value.general.dayStartHour)

    fun now(): LocalTime = clock.time()

    // ------------------------------------------------------------ day access

    /**
     * Makes sure a date exists in the journal, materialised from the current
     * blueprints and swept for anything that has quietly gone past due.
     */
    suspend fun ensureDay(date: LocalDate): DayPlan {
        val existing = journalStore.day(date)
        val materialized = materializer.materialize(date, catalog.value, settings.value.general, existing)
        val sweep = missedDetector.sweep(materialized, clock.now(), settings.value.general)
        val result = sweep.plan
        if (result != existing) saveDay(result)
        return result
    }

    /** Materialises a whole range at once, for the planner's month view. */
    suspend fun ensureRange(from: LocalDate, to: LocalDate) {
        if (from > to) return
        val updated = mutableListOf<DayPlan>()
        var cursor = from
        while (cursor <= to) {
            val existing = journalStore.day(cursor)
            val materialized = materializer.materialize(cursor, catalog.value, settings.value.general, existing)
            val swept = missedDetector.sweep(materialized, clock.now(), settings.value.general).plan
            // Empty future days are not written; the journal stays sparse.
            if (swept != existing && !swept.isEmpty) updated += swept
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        saveDays(updated)
    }

    fun observeDay(date: LocalDate): Flow<DayPlan> =
        days.map { it[date] ?: DayPlan(date = date) }

    fun day(date: LocalDate): DayPlan = days.value[date] ?: DayPlan(date = date)

    fun score(date: LocalDate): DayScore =
        scores.value[date] ?: DayScore.empty(date, settings.value.scoring)

    fun weekScore(anchor: LocalDate): WeekScore {
        val first = settings.value.general.firstDayOfWeek
        val start = WeekMath.startOfWeek(anchor, first)
        val end = WeekMath.endOfWeek(anchor, first)
        return weekEngine.scoreWeek(start, end, scores.value.values.toList(), settings.value.scoring)
    }

    fun currentStreak(): Int = scoreBook.currentStreak(scores.value, today(), settings.value.scoring)

    fun goalProgress(goal: Goal, anchor: LocalDate = today()): GoalProgress {
        val library = catalog.value
        return goalEngine.progress(
            goal = goal,
            days = days.value.values.toList(),
            scores = scores.value,
            today = anchor,
            firstDayOfWeek = settings.value.general.firstDayOfWeek,
            taskCategoryOf = { taskId -> library.task(taskId)?.categoryId }
        )
    }

    fun allGoalProgress(anchor: LocalDate = today()): Map<String, GoalProgress> =
        catalog.value.activeGoals.associate { it.id to goalProgress(it, anchor) }

    // -------------------------------------------------------- entry mutations

    private suspend fun mutateDay(date: LocalDate, transform: (DayPlan) -> DayPlan) {
        val current = journalStore.day(date) ?: ensureDay(date)
        if (current.finalized) return
        val updated = transform(current)
        if (updated != current) saveDay(updated)
    }

    private suspend fun mutateEntry(date: LocalDate, entryId: String, transform: (PlanEntry) -> PlanEntry) {
        mutateDay(date) { plan -> plan.withEntry(entryId, transform) }
    }

    /** The main gesture: tick a task off, or un-tick it. */
    suspend fun toggleDone(date: LocalDate, entryId: String) {
        val entry = day(date).entry(entryId) ?: return
        if (entry.status == EntryStatus.Done) {
            mutateEntry(date, entryId) {
                it.copy(status = EntryStatus.Pending, completedAt = null)
            }
        } else {
            complete(date, entryId)
        }
    }

    suspend fun complete(date: LocalDate, entryId: String) {
        val stamp = completionStamp(date)
        mutateEntry(date, entryId) { entry ->
            entry.copy(
                status = EntryStatus.Done,
                completedAt = stamp,
                startedAt = entry.startedAt ?: stamp,
                // Ticking the parent ticks everything under it.
                subtasks = entry.subtasks.map { it.copy(done = true) },
                skipReason = "",
                deferredTo = null
            )
        }
    }

    suspend fun setStatus(date: LocalDate, entryId: String, status: EntryStatus) {
        val stamp = completionStamp(date)
        mutateEntry(date, entryId) { entry ->
            entry.copy(
                status = status,
                completedAt = if (status == EntryStatus.Done) stamp else entry.completedAt,
                startedAt = if (status == EntryStatus.InProgress) (entry.startedAt ?: stamp) else entry.startedAt
            )
        }
    }

    suspend fun start(date: LocalDate, entryId: String) {
        mutateEntry(date, entryId) { entry ->
            if (entry.status != EntryStatus.Pending) entry
            else entry.copy(status = EntryStatus.InProgress, startedAt = completionStamp(date))
        }
    }

    suspend fun toggleSubtask(date: LocalDate, entryId: String, subtaskId: String) {
        val stamp = completionStamp(date)
        mutateEntry(date, entryId) { entry ->
            val subtasks = entry.subtasks.map {
                if (it.id == subtaskId) it.copy(done = !it.done) else it
            }
            val updated = entry.copy(subtasks = subtasks)
            // Finishing the last sub-mission finishes the task itself.
            val allDone = updated.allSubtasksDone()
            when {
                allDone && updated.status.isOpen ->
                    updated.copy(status = EntryStatus.Done, completedAt = stamp)

                !allDone && updated.status == EntryStatus.Done ->
                    updated.copy(status = EntryStatus.Partial)

                else -> updated
            }
        }
    }

    suspend fun setQuantity(date: LocalDate, entryId: String, quantity: Double?) {
        mutateEntry(date, entryId) { entry ->
            val target = entry.scoring.quantityTarget
            val reached = target != null && quantity != null && quantity >= target
            entry.copy(
                actualQuantity = quantity,
                status = when {
                    reached && entry.status.isOpen -> EntryStatus.Done
                    !reached && entry.status == EntryStatus.Done -> EntryStatus.Partial
                    else -> entry.status
                },
                completedAt = if (reached && entry.status.isOpen) completionStamp(date) else entry.completedAt
            )
        }
    }

    suspend fun skip(date: LocalDate, entryId: String, reason: String = "") {
        mutateEntry(date, entryId) {
            it.copy(status = EntryStatus.Skipped, skipReason = reason, completedAt = null)
        }
    }

    /** Moves an unfinished task to another date and records where it went. */
    suspend fun defer(date: LocalDate, entryId: String, to: LocalDate) {
        val entry = day(date).entry(entryId) ?: return
        mutateEntry(date, entryId) { it.copy(status = EntryStatus.Deferred, deferredTo = to) }
        val target = journalStore.day(to) ?: DayPlan(date = to)
        if (target.finalized) return
        val moved = entry.resetForNewDay(ids.newId())
        saveDay(
            target.copy(entries = DayMaterializer.sortEntries(target.entries + moved))
        )
    }

    suspend fun addEntry(date: LocalDate, entry: PlanEntry) {
        mutateDay(date) { plan ->
            plan.copy(entries = DayMaterializer.sortEntries(plan.entries + entry.copy(id = ids.newId())))
        }
    }

    suspend fun updateEntry(date: LocalDate, entry: PlanEntry) {
        mutateDay(date) { plan ->
            plan.copy(
                entries = DayMaterializer.sortEntries(
                    plan.entries.map { if (it.id == entry.id) entry else it }
                )
            )
        }
    }

    suspend fun deleteEntry(date: LocalDate, entryId: String) {
        mutateDay(date) { plan -> plan.copy(entries = plan.entries.filterNot { it.id == entryId }) }
    }

    suspend fun reorderEntries(date: LocalDate, orderedIds: List<String>) {
        mutateDay(date) { plan ->
            val positions = orderedIds.withIndex().associate { (index, id) -> id to index }
            plan.copy(
                entries = plan.entries
                    .sortedBy { positions[it.id] ?: Int.MAX_VALUE }
                    .mapIndexed { index, entry -> entry.copy(order = index) }
            )
        }
    }

    // ------------------------------------------------------------- the day itself

    suspend fun updateCheckIn(date: LocalDate, transform: (DayCheckIn) -> DayCheckIn) {
        mutateDay(date) { plan -> plan.copy(checkIn = transform(plan.checkIn)) }
    }

    suspend fun setDayNote(date: LocalDate, note: String) {
        mutateDay(date) { plan -> plan.copy(note = note) }
    }

    /**
     * Closes a day: open items are resolved, the score is frozen, and — if the
     * user asked for it — unfinished work is carried over to tomorrow.
     */
    suspend fun finalizeDay(date: LocalDate) {
        val plan = journalStore.day(date) ?: ensureDay(date)
        if (plan.finalized) return

        val carryOver = settings.value.general.carryOverUnfinished
        val unfinished = if (carryOver) plan.entries.filter { it.status.isOpen } else emptyList()

        val sweep = missedDetector.finalizeDay(plan)
        val frozen = scoreBook.computeAll(
            days = days.value.values.map { if (it.date == date) sweep.plan else it },
            config = settings.value.scoring,
            categories = catalog.value.categories,
            goals = catalog.value.goals
        )[date]

        saveDay(sweep.plan.copy(finalized = true, frozenScore = frozen))

        if (unfinished.isNotEmpty()) {
            val tomorrow = date.plus(1, DateTimeUnit.DAY)
            val target = journalStore.day(tomorrow) ?: DayPlan(date = tomorrow)
            if (!target.finalized) {
                val moved = unfinished.map { it.resetForNewDay(ids.newId()) }
                saveDay(
                    target.copy(entries = DayMaterializer.sortEntries(target.entries + moved))
                )
            }
        }
    }

    suspend fun reopenDay(date: LocalDate) {
        val plan = journalStore.day(date) ?: return
        if (!plan.finalized) return
        saveDay(plan.copy(finalized = false, frozenScore = null))
    }

    // ------------------------------------------------------------------ copying

    suspend fun copyDay(
        from: LocalDate,
        targets: List<LocalDate>,
        options: CopyOptions = CopyOptions()
    ): CopyResult {
        val source = journalStore.day(from) ?: return CopyResult(emptyMap(), 0, emptyList(), targets)
        val result = copyEngine.copy(source, targets, days.value, options)
        saveDays(result.plans.values)
        return result
    }

    suspend fun copyDayToRange(
        from: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        weekdays: Set<kotlinx.datetime.DayOfWeek>? = null,
        options: CopyOptions = CopyOptions()
    ): CopyResult {
        val source = journalStore.day(from) ?: return CopyResult(emptyMap(), 0, emptyList(), emptyList())
        val result = if (weekdays.isNullOrEmpty()) {
            copyEngine.copyToRange(source, rangeStart, rangeEnd, days.value, options)
        } else {
            copyEngine.copyToWeekdays(source, rangeStart, rangeEnd, weekdays, days.value, options)
        }
        saveDays(result.plans.values)
        return result
    }

    suspend fun saveDayAsTemplate(date: LocalDate, name: String, emoji: String = "🗂️"): DayTemplate? {
        val source = journalStore.day(date) ?: return null
        val template = copyEngine.toTemplate(source, name, emoji, createdAt = clock.now().toString())
        editCatalog { it.copy(templates = it.templates + template) }
        return template
    }

    suspend fun applyTemplate(
        templateId: String,
        date: LocalDate,
        options: CopyOptions = CopyOptions()
    ): Boolean {
        val template = catalog.value.template(templateId) ?: return false
        val plan = copyEngine.applyTemplate(template, date, journalStore.day(date), options)
        saveDay(plan)
        return true
    }

    suspend fun duplicateEntry(
        date: LocalDate,
        entryId: String,
        targets: List<LocalDate>
    ): CopyResult {
        val entry = day(date).entry(entryId)
            ?: return CopyResult(emptyMap(), 0, emptyList(), targets)
        val result = copyEngine.duplicateEntry(entry, targets, days.value)
        saveDays(result.plans.values)
        return result
    }

    // ---------------------------------------------------------------- catalog

    suspend fun upsertTask(task: TaskDefinition) {
        editCatalog { library ->
            val exists = library.tasks.any { it.id == task.id }
            if (exists) {
                library.copy(tasks = library.tasks.map { if (it.id == task.id) task else it })
            } else {
                library.copy(tasks = library.tasks + task)
            }
        }
        ensureDay(today())
    }

    suspend fun deleteTask(taskId: String) {
        editCatalog { it.copy(tasks = it.tasks.filterNot { task -> task.id == taskId }) }
        // Pending copies of a deleted blueprint disappear from open days;
        // finished ones stay, because they are history.
        val cleaned = days.value.values
            .filter { plan -> !plan.finalized && plan.entries.any { it.taskId == taskId && it.status.isOpen } }
            .map { plan ->
                plan.copy(entries = plan.entries.filterNot { it.taskId == taskId && it.status.isOpen })
            }
        saveDays(cleaned)
    }

    suspend fun setTaskArchived(taskId: String, archived: Boolean) {
        editCatalog { library ->
            library.copy(tasks = library.tasks.map { if (it.id == taskId) it.copy(archived = archived) else it })
        }
    }

    suspend fun upsertCategory(category: Category) {
        editCatalog { library ->
            val exists = library.categories.any { it.id == category.id }
            if (exists) {
                library.copy(categories = library.categories.map { if (it.id == category.id) category else it })
            } else {
                library.copy(categories = library.categories + category)
            }
        }
    }

    suspend fun deleteCategory(categoryId: String) {
        editCatalog { library ->
            library.copy(
                categories = library.categories.filterNot { it.id == categoryId },
                tasks = library.tasks.map {
                    if (it.categoryId == categoryId) it.copy(categoryId = null) else it
                }
            )
        }
    }

    suspend fun upsertGoal(goal: Goal) {
        editCatalog { library ->
            val exists = library.goals.any { it.id == goal.id }
            if (exists) {
                library.copy(goals = library.goals.map { if (it.id == goal.id) goal else it })
            } else {
                library.copy(goals = library.goals + goal)
            }
        }
    }

    suspend fun deleteGoal(goalId: String) {
        editCatalog { library ->
            library.copy(
                goals = library.goals.filterNot { it.id == goalId },
                tasks = library.tasks.map { task ->
                    task.copy(goalLinks = task.goalLinks.filterNot { it.goalId == goalId })
                }
            )
        }
    }

    suspend fun deleteTemplate(templateId: String) {
        editCatalog { it.copy(templates = it.templates.filterNot { t -> t.id == templateId }) }
    }

    fun newId(): String = ids.newId()

    // --------------------------------------------------------------- settings

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        editSettings(transform)
    }

    // ----------------------------------------------------------------- backup

    fun exportBackup(appVersion: String = "1.0.0"): String {
        val backup = YomiBackup(
            exportedAt = clock.now().toString(),
            appVersion = appVersion,
            settings = settings.value,
            catalog = catalog.value,
            days = days.value.values.sortedBy { it.date }
        )
        return YomiJson.encodeToString(YomiBackup.serializer(), backup)
    }

    suspend fun importBackup(text: String): Boolean {
        val backup = try {
            YomiJson.decodeFromString(YomiBackup.serializer(), text)
        } catch (e: Exception) {
            return false
        }
        settingsStore.replace(backup.settings)
        catalogStore.replace(backup.catalog)
        journalStore.replaceAll(backup.days)
        recomputeScores()
        return true
    }

    /** Wipes everything and starts again from the starter library. */
    suspend fun resetEverything(language: AppLanguage = settings.value.general.language) {
        journalStore.replaceAll(emptyList())
        catalogStore.replace(DefaultContent.catalog(today(), language))
        settingsStore.replace(AppSettings())
        recomputeScores()
        ensureDay(today())
    }

    /**
     * The clock time to stamp on a completion. Only "today" gets a real time —
     * back-filling an old day must not invent a punctuality record.
     */
    private fun completionStamp(date: LocalDate): LocalTime? =
        if (date == today()) clock.time() else null
}
