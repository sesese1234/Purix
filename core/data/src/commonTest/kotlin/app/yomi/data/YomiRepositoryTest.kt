package app.yomi.data

import app.yomi.data.storage.CatalogStore
import app.yomi.data.storage.DocumentStore
import app.yomi.data.storage.JournalStore
import app.yomi.data.storage.SettingsStore
import app.yomi.data.storage.YomiPaths
import app.yomi.domain.plan.CopyMode
import app.yomi.domain.plan.CopyOptions
import app.yomi.domain.time.MutableClock
import app.yomi.domain.util.SequentialIdGenerator
import app.yomi.model.AppLanguage
import app.yomi.model.Catalog
import app.yomi.model.EntryStatus
import app.yomi.model.PlanEntry
import app.yomi.model.Recurrence
import app.yomi.model.RecurrenceRule
import app.yomi.model.SubtaskDefinition
import app.yomi.model.TaskDefinition
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class YomiRepositoryTest {

    private val today = LocalDate(2026, 3, 2)

    private class Harness(startAt: LocalDateTime) {
        val fileSystem = FakeFileSystem()
        val paths = YomiPaths("/yomi".toPath())
        val documents = DocumentStore(fileSystem)
        val clock = MutableClock(startAt)
        val repository: YomiRepository

        init {
            documents.ensureRoot(paths.root)
            repository = YomiRepository(
                settingsStore = SettingsStore(documents, paths),
                catalogStore = CatalogStore(documents, paths),
                journalStore = JournalStore(documents, paths),
                clock = clock,
                ids = SequentialIdGenerator("e")
            )
        }

        /** A second repository over the same files, to prove data really persists. */
        fun reopen(): YomiRepository = YomiRepository(
            settingsStore = SettingsStore(documents, paths),
            catalogStore = CatalogStore(documents, paths),
            journalStore = JournalStore(documents, paths),
            clock = clock,
            ids = SequentialIdGenerator("r")
        )
    }

    private fun harness(hour: Int = 9) =
        Harness(LocalDateTime(2026, 3, 2, hour, 0))

    private fun simpleTask(id: String, timing: TaskTiming = TaskTiming.Anytime) = TaskDefinition(
        id = id,
        title = "Task $id",
        timing = timing,
        schedule = RecurrenceRule(Recurrence.Daily(), LocalDate(2026, 1, 1))
    )

    @Test
    fun `a first run seeds the starter library`() = runTest {
        val h = harness()
        h.repository.initialize()
        assertTrue(h.repository.catalog.value.tasks.isNotEmpty())
        assertTrue(h.repository.catalog.value.goals.isNotEmpty())
        assertTrue(h.repository.day(today).entries.isNotEmpty())
    }

    @Test
    fun `data survives a restart`() = runTest {
        val h = harness()
        h.repository.initialize()
        val entryId = h.repository.day(today).entries.first().id
        h.repository.complete(today, entryId)

        val reopened = h.reopen()
        reopened.initialize()
        val restored = reopened.day(today).entries.firstOrNull { it.id == entryId }
        assertNotNull(restored)
        assertEquals(EntryStatus.Done, restored.status)
    }

    @Test
    fun `a corrupt file falls back to defaults instead of crashing`() = runTest {
        val h = harness()
        h.fileSystem.createDirectories(h.paths.root)
        h.fileSystem.write(h.paths.settings) { writeUtf8("{ this is not json") }
        h.repository.initialize()
        assertEquals(4, h.repository.settings.value.general.dayStartHour)
    }

    @Test
    fun `completing a task stamps the current time`() = runTest {
        val h = harness(hour = 14)
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)
        val entryId = h.repository.day(today).entries.first().id

        h.repository.complete(today, entryId)
        val entry = h.repository.day(today).entry(entryId)!!
        assertEquals(EntryStatus.Done, entry.status)
        assertEquals(LocalTime(14, 0), entry.completedAt)
    }

    @Test
    fun `back-filling an old day does not invent a completion time`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        val yesterday = today.plus(-1, DateTimeUnit.DAY)
        h.repository.ensureDay(yesterday)
        val entryId = h.repository.day(yesterday).entries.first().id

        h.repository.complete(yesterday, entryId)
        assertNull(h.repository.day(yesterday).entry(entryId)!!.completedAt)
    }

    @Test
    fun `ticking the last sub-mission finishes the task`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(
            Catalog(tasks = listOf(
                simpleTask("t1").copy(
                    subtasks = listOf(SubtaskDefinition("s1", "one"), SubtaskDefinition("s2", "two"))
                )
            ))
        )
        h.repository.ensureDay(today)
        val entryId = h.repository.day(today).entries.first().id

        h.repository.toggleSubtask(today, entryId, "s1")
        assertEquals(EntryStatus.Pending, h.repository.day(today).entry(entryId)!!.status)

        h.repository.toggleSubtask(today, entryId, "s2")
        assertEquals(EntryStatus.Done, h.repository.day(today).entry(entryId)!!.status)

        h.repository.toggleSubtask(today, entryId, "s2")
        assertEquals(EntryStatus.Partial, h.repository.day(today).entry(entryId)!!.status)
    }

    @Test
    fun `completing a task ticks every sub-mission under it`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(
            Catalog(tasks = listOf(
                simpleTask("t1").copy(subtasks = listOf(SubtaskDefinition("s1", "one")))
            ))
        )
        h.repository.ensureDay(today)
        val entryId = h.repository.day(today).entries.first().id
        h.repository.complete(today, entryId)
        assertTrue(h.repository.day(today).entry(entryId)!!.subtasks.all { it.done })
    }

    @Test
    fun `reaching the quantity target completes a measurable task`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(
            Catalog(tasks = listOf(
                simpleTask("t1").copy(scoring = TaskScoring(points = 10, quantityTarget = 20.0))
            ))
        )
        h.repository.ensureDay(today)
        val entryId = h.repository.day(today).entries.first().id

        h.repository.setQuantity(today, entryId, 10.0)
        assertEquals(EntryStatus.Pending, h.repository.day(today).entry(entryId)!!.status)

        h.repository.setQuantity(today, entryId, 25.0)
        assertEquals(EntryStatus.Done, h.repository.day(today).entry(entryId)!!.status)
    }

    @Test
    fun `deferring moves the task to the target day`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)
        val entryId = h.repository.day(today).entries.first().id
        val tomorrow = today.plus(1, DateTimeUnit.DAY)

        h.repository.defer(today, entryId, tomorrow)

        assertEquals(EntryStatus.Deferred, h.repository.day(today).entry(entryId)!!.status)
        assertEquals(tomorrow, h.repository.day(today).entry(entryId)!!.deferredTo)
        assertTrue(h.repository.day(tomorrow).entries.any { it.title == "Task t1" })
    }

    @Test
    fun `finalising a day freezes its score and closes it`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"), simpleTask("t2"))))
        h.repository.ensureDay(today)
        h.repository.complete(today, h.repository.day(today).entries.first().id)

        h.repository.finalizeDay(today)
        val plan = h.repository.day(today)
        assertTrue(plan.finalized)
        assertNotNull(plan.frozenScore)
        assertEquals(EntryStatus.Missed, plan.entries.last().status)

        // A closed day rejects further edits.
        h.repository.complete(today, plan.entries.last().id)
        assertEquals(EntryStatus.Missed, h.repository.day(today).entries.last().status)
    }

    @Test
    fun `carry over moves unfinished work to tomorrow`() = runTest {
        val h = harness()
        h.repository.updateSettings { it.copy(general = it.general.copy(carryOverUnfinished = true)) }
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)

        h.repository.finalizeDay(today)
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        assertEquals(1, h.repository.day(tomorrow).entries.count { it.title == "Task t1" })
    }

    @Test
    fun `reopening a finalised day lets edits through again`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)
        h.repository.finalizeDay(today)
        h.repository.reopenDay(today)

        val entryId = h.repository.day(today).entries.first().id
        h.repository.complete(today, entryId)
        assertEquals(EntryStatus.Done, h.repository.day(today).entry(entryId)!!.status)
    }

    @Test
    fun `copying a day lands on every target`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)
        h.repository.addEntry(today, PlanEntry(id = "ignored", title = "Extra"))

        val targets = listOf(today.plus(1, DateTimeUnit.DAY), today.plus(2, DateTimeUnit.DAY))
        val result = h.repository.copyDay(today, targets, CopyOptions(mode = CopyMode.Replace))

        assertEquals(2, result.datesChanged.size)
        targets.forEach { assertEquals(2, h.repository.day(it).entries.size) }
    }

    @Test
    fun `a template can be saved and applied elsewhere`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)

        val template = h.repository.saveDayAsTemplate(today, "Weekday")
        assertNotNull(template)
        assertEquals(1, h.repository.catalog.value.templates.size)

        val target = today.plus(10, DateTimeUnit.DAY)
        assertTrue(h.repository.applyTemplate(template.id, target))
        assertEquals(template.id, h.repository.day(target).sourceTemplateId)
        assertTrue(h.repository.day(target).entries.isNotEmpty())
    }

    @Test
    fun `deleting a blueprint removes only untouched copies`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        val yesterday = today.plus(-1, DateTimeUnit.DAY)
        h.repository.ensureDay(yesterday)
        h.repository.ensureDay(today)
        h.repository.complete(yesterday, h.repository.day(yesterday).entries.first().id)

        h.repository.deleteTask("t1")

        assertEquals(1, h.repository.day(yesterday).entries.size)
        assertTrue(h.repository.day(today).entries.none { it.taskId == "t1" })
    }

    @Test
    fun `deleting a category detaches it from its tasks`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(
            Catalog(
                tasks = listOf(simpleTask("t1").copy(categoryId = "c1")),
                categories = listOf(app.yomi.model.Category("c1", "Work"))
            )
        )
        h.repository.deleteCategory("c1")
        assertNull(h.repository.catalog.value.task("t1")!!.categoryId)
        assertTrue(h.repository.catalog.value.categories.isEmpty())
    }

    @Test
    fun `scores follow the journal as it changes`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"), simpleTask("t2"))))
        h.repository.ensureDay(today)
        assertEquals(0.0, h.repository.score(today).score)

        h.repository.day(today).entries.forEach { h.repository.complete(today, it.id) }
        assertTrue(h.repository.score(today).score > 90.0)
        assertTrue(h.repository.score(today).isPerfectDay)
    }

    @Test
    fun `the missed detector runs when a day is opened`() = runTest {
        val h = harness(hour = 9)
        h.repository.catalogStoreSeed(
            Catalog(tasks = listOf(simpleTask("t1", TaskTiming.Deadline(LocalTime(10, 0)))))
        )
        h.repository.ensureDay(today)
        assertEquals(EntryStatus.Pending, h.repository.day(today).entries.first().status)

        h.clock.setTime(12, 0)
        h.repository.ensureDay(today)
        assertEquals(EntryStatus.Missed, h.repository.day(today).entries.first().status)
    }

    @Test
    fun `a backup round-trips through export and import`() = runTest {
        val h = harness()
        h.repository.initialize()
        h.repository.complete(today, h.repository.day(today).entries.first().id)
        val backup = h.repository.exportBackup()

        h.repository.resetEverything(AppLanguage.English)
        assertTrue(h.repository.days.value.values.all { plan -> plan.entries.none { it.status == EntryStatus.Done } })

        assertTrue(h.repository.importBackup(backup))
        assertTrue(h.repository.day(today).entries.any { it.status == EntryStatus.Done })
    }

    @Test
    fun `importing rubbish is refused without touching the data`() = runTest {
        val h = harness()
        h.repository.initialize()
        val before = h.repository.catalog.value.tasks.size
        assertFalse(h.repository.importBackup("not a backup"))
        assertEquals(before, h.repository.catalog.value.tasks.size)
    }

    @Test
    fun `the journal is written one file per month`() = runTest {
        val h = harness()
        h.repository.catalogStoreSeed(Catalog(tasks = listOf(simpleTask("t1"))))
        h.repository.ensureDay(today)
        h.repository.ensureDay(LocalDate(2026, 4, 15))

        val shards = h.fileSystem.list(h.paths.journalDir).map { it.name }.sorted()
        assertEquals(listOf("2026-03.json", "2026-04.json"), shards)
    }

    @Test
    fun `the night owl boundary keeps 2am on the previous day`() = runTest {
        val h = Harness(LocalDateTime(2026, 3, 3, 2, 0))
        h.repository.initialize()
        h.repository.updateSettings { it.copy(general = it.general.copy(dayStartHour = 4)) }
        assertEquals(LocalDate(2026, 3, 2), h.repository.today())
    }
}

/** Test-only shortcut for putting a specific library in place. */
private suspend fun YomiRepository.catalogStoreSeed(catalog: Catalog) {
    catalog.categories.forEach { upsertCategory(it) }
    catalog.tasks.forEach { upsertTask(it) }
    catalog.goals.forEach { upsertGoal(it) }
}
