package app.yomi.domain

import app.yomi.domain.plan.CopyMode
import app.yomi.domain.plan.CopyOptions
import app.yomi.domain.plan.DayCopyEngine
import app.yomi.domain.plan.DayMaterializer
import app.yomi.domain.plan.MissedTaskDetector
import app.yomi.domain.util.SequentialIdGenerator
import app.yomi.model.Catalog
import app.yomi.model.DayPlan
import app.yomi.model.EntryStatus
import app.yomi.model.GeneralSettings
import app.yomi.model.Recurrence
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayMaterializerTest {

    private val settings = GeneralSettings()

    private fun materializer() = DayMaterializer(SequentialIdGenerator("e"))

    @Test
    fun `due blueprints become entries`() {
        val catalog = Catalog(tasks = listOf(task("t1"), task("t2")))
        val day = materializer().materialize(Monday, catalog, settings)
        assertEquals(2, day.entries.size)
        assertContentEquals(listOf("t1", "t2"), day.entries.mapNotNull { it.taskId }.sorted())
    }

    @Test
    fun `archived and non-matching blueprints are ignored`() {
        val catalog = Catalog(
            tasks = listOf(
                task("archived", archived = true),
                task("weekly", Recurrence.Weekly(setOf(DayOfWeek.SATURDAY)))
            )
        )
        val day = materializer().materialize(Monday, catalog, settings)
        assertTrue(day.entries.isEmpty())
    }

    @Test
    fun `existing progress survives re-materialisation`() {
        val catalog = Catalog(tasks = listOf(task("t1")))
        val first = materializer().materialize(Monday, catalog, settings)
        val touched = first.withEntry(first.entries.first().id) {
            it.copy(status = EntryStatus.Done, completedAt = time(9))
        }
        val second = materializer().materialize(Monday, catalog, settings, touched)
        assertEquals(1, second.entries.size)
        assertEquals(EntryStatus.Done, second.entries.first().status)
        assertEquals(time(9), second.entries.first().completedAt)
    }

    @Test
    fun `ad-hoc entries are never removed`() {
        val catalog = Catalog(tasks = emptyList())
        val existing = plan(entries = listOf(entry("adhoc", taskId = null)))
        val day = materializer().materialize(Monday, catalog, settings, existing)
        assertEquals(1, day.entries.size)
        assertNull(day.entries.first().taskId)
    }

    @Test
    fun `an untouched entry disappears when its blueprint stops firing`() {
        val existing = plan(entries = listOf(entry("e1", taskId = "gone")))
        val day = materializer().materialize(Monday, Catalog(), settings, existing)
        assertTrue(day.entries.isEmpty())
    }

    @Test
    fun `a completed entry stays even when its blueprint stops firing`() {
        val existing = plan(
            entries = listOf(entry("e1", taskId = "gone", status = EntryStatus.Done))
        )
        val day = materializer().materialize(Monday, Catalog(), settings, existing)
        assertEquals(1, day.entries.size)
    }

    @Test
    fun `a finalised day is never rewritten`() {
        val catalog = Catalog(tasks = listOf(task("t1")))
        val existing = plan(entries = emptyList(), finalized = true)
        val day = materializer().materialize(Monday, catalog, settings, existing)
        assertTrue(day.entries.isEmpty())
    }

    @Test
    fun `entries are ordered by clock time with untimed ones last`() {
        val catalog = Catalog(
            tasks = listOf(
                task("late", timing = TaskTiming.Fixed(time(18))),
                task("anytime"),
                task("early", timing = TaskTiming.Fixed(time(7)))
            )
        )
        val day = materializer().materialize(Monday, catalog, settings)
        assertContentEquals(listOf("early", "late", "anytime"), day.entries.mapNotNull { it.taskId })
        assertContentEquals(listOf(0, 1, 2), day.entries.map { it.order })
    }

    @Test
    fun `wake and sleep targets are pre-filled from the settings`() {
        val day = materializer().materialize(Monday, Catalog(), settings)
        assertEquals(settings.defaultWakeTime, day.checkIn.wakeTarget)
        assertEquals(settings.defaultSleepTime, day.checkIn.sleepTarget)
    }
}

class MissedTaskDetectorTest {

    private val detector = MissedTaskDetector()
    private val settings = GeneralSettings(missedGraceMinutes = 30)

    private fun now(hour: Int, minute: Int = 0) = LocalDateTime(2026, 3, 2, hour, minute)

    @Test
    fun `a task inside its window stays pending`() {
        val sweep = detector.sweep(
            plan(entries = listOf(entry("a", timing = TaskTiming.Deadline(time(12))))),
            now(11),
            settings
        )
        assertFalse(sweep.changed)
        assertEquals(EntryStatus.Pending, sweep.plan.entries.first().status)
    }

    @Test
    fun `the grace window delays the verdict`() {
        val entries = listOf(entry("a", timing = TaskTiming.Deadline(time(12))))
        assertFalse(detector.sweep(plan(entries = entries), now(12, 20), settings).changed)
        assertTrue(detector.sweep(plan(entries = entries), now(12, 45), settings).changed)
    }

    @Test
    fun `an untimed task is not missed until the day itself is over`() {
        val entries = listOf(entry("a", timing = TaskTiming.Anytime))
        assertFalse(detector.sweep(plan(entries = entries), now(23, 59), settings).changed)

        val yesterday = plan(date = Monday, entries = entries)
        val sweep = detector.sweep(yesterday, LocalDateTime(2026, 3, 3, 9, 0), settings)
        assertEquals(EntryStatus.Missed, sweep.plan.entries.first().status)
    }

    @Test
    fun `started work is recorded as partial rather than missed`() {
        val entries = listOf(
            entry(
                "a",
                timing = TaskTiming.Deadline(time(12)),
                subtasks = listOf(subtask("s1", done = true), subtask("s2"))
            )
        )
        val sweep = detector.sweep(plan(entries = entries), now(14), settings)
        assertEquals(EntryStatus.Partial, sweep.plan.entries.first().status)
        assertEquals(1, sweep.newlyPartial.size)
        assertTrue(sweep.newlyMissed.isEmpty())
    }

    @Test
    fun `a future day is left alone`() {
        val future = plan(date = Wednesday, entries = listOf(entry("a", timing = TaskTiming.Deadline(time(1)))))
        assertFalse(detector.sweep(future, now(23), settings).changed)
    }

    @Test
    fun `auto detection can be switched off`() {
        val off = settings.copy(autoDetectMissed = false)
        val entries = listOf(entry("a", timing = TaskTiming.Deadline(time(1))))
        assertFalse(detector.sweep(plan(entries = entries), now(23), off).changed)
    }

    @Test
    fun `the day boundary hour keeps late-night work on the previous day`() {
        // 02:00 with a 4am boundary is still "Monday", so Monday is not past.
        val nightOwl = settings.copy(dayStartHour = 4)
        val sweep = detector.sweep(
            plan(date = Monday, entries = listOf(entry("a", timing = TaskTiming.Anytime))),
            LocalDateTime(2026, 3, 3, 2, 0),
            nightOwl
        )
        assertFalse(sweep.changed)
    }

    @Test
    fun `finalising a day resolves everything still open`() {
        val sweep = detector.finalizeDay(
            plan(entries = listOf(
                entry("a", status = EntryStatus.Done),
                entry("b", status = EntryStatus.Pending),
                entry("c", status = EntryStatus.InProgress, scoring = TaskScoring(allowPartial = true))
            ))
        )
        val byId = sweep.plan.entries.associateBy { it.id }
        assertEquals(EntryStatus.Done, byId.getValue("a").status)
        assertEquals(EntryStatus.Missed, byId.getValue("b").status)
        assertEquals(EntryStatus.Partial, byId.getValue("c").status)
    }

    @Test
    fun `overdue now lists what is late without changing anything`() {
        val p = plan(entries = listOf(
            entry("a", timing = TaskTiming.Deadline(time(9))),
            entry("b", timing = TaskTiming.Deadline(time(20)))
        ))
        val overdue = detector.overdueNow(p, now(15), settings)
        assertEquals(listOf("a"), overdue.map { it.id })
    }
}

class DayCopyEngineTest {

    private fun engine() = DayCopyEngine(SequentialIdGenerator("c"))

    private val source = plan(
        date = Monday,
        entries = listOf(
            entry("a", taskId = "t1", status = EntryStatus.Done, completedAt = time(9)),
            entry("b", taskId = null, status = EntryStatus.Missed),
            entry("c", taskId = "t2", categoryId = "work")
        )
    )

    @Test
    fun `copying resets progress by default`() {
        val result = engine().copy(source, listOf(Tuesday), emptyMap())
        val copied = result.plans.getValue(Tuesday)
        assertEquals(3, copied.entries.size)
        assertTrue(copied.entries.all { it.status == EntryStatus.Pending })
        assertTrue(copied.entries.all { it.completedAt == null })
    }

    @Test
    fun `copied entries get fresh identifiers`() {
        val copied = engine().copy(source, listOf(Tuesday), emptyMap()).plans.getValue(Tuesday)
        assertTrue(copied.entries.none { it.id in setOf("a", "b", "c") })
    }

    @Test
    fun `merge does not duplicate the same blueprint`() {
        val existing = mapOf(
            Tuesday to DayPlan(date = Tuesday, entries = listOf(entry("x", taskId = "t1")))
        )
        val merged = engine().copy(source, listOf(Tuesday), existing).plans.getValue(Tuesday)
        assertEquals(3, merged.entries.size)
        assertEquals(1, merged.entries.count { it.taskId == "t1" })
    }

    @Test
    fun `replace wipes the target day`() {
        val existing = mapOf(
            Tuesday to DayPlan(date = Tuesday, entries = listOf(entry("x"), entry("y")))
        )
        val options = CopyOptions(mode = CopyMode.Replace)
        val replaced = engine().copy(source, listOf(Tuesday), existing, options).plans.getValue(Tuesday)
        assertEquals(3, replaced.entries.size)
        assertTrue(replaced.entries.none { it.id in setOf("x", "y") })
    }

    @Test
    fun `append keeps duplicates on purpose`() {
        val existing = mapOf(
            Tuesday to DayPlan(date = Tuesday, entries = listOf(entry("x", taskId = "t1")))
        )
        val options = CopyOptions(mode = CopyMode.Append)
        val appended = engine().copy(source, listOf(Tuesday), existing, options).plans.getValue(Tuesday)
        assertEquals(4, appended.entries.size)
    }

    @Test
    fun `filters decide what travels`() {
        val onlyAdHoc = engine().copy(
            source,
            listOf(Tuesday),
            emptyMap(),
            CopyOptions(includeRecurring = false)
        ).plans.getValue(Tuesday)
        assertEquals(1, onlyAdHoc.entries.size)

        val noUnfinished = engine().copy(
            source,
            listOf(Tuesday),
            emptyMap(),
            CopyOptions(includeUnfinished = false)
        ).plans.getValue(Tuesday)
        assertEquals(2, noUnfinished.entries.size)

        val onlyWork = engine().copy(
            source,
            listOf(Tuesday),
            emptyMap(),
            CopyOptions(categoryFilter = setOf("work"))
        ).plans.getValue(Tuesday)
        assertEquals(1, onlyWork.entries.size)
    }

    @Test
    fun `finalised targets are skipped`() {
        val existing = mapOf(Tuesday to DayPlan(date = Tuesday, finalized = true))
        val result = engine().copy(source, listOf(Tuesday), existing)
        assertTrue(result.plans.isEmpty())
        assertEquals(listOf(Tuesday), result.datesSkipped)
    }

    @Test
    fun `copying onto the source date is a no-op`() {
        val result = engine().copy(source, listOf(Monday), emptyMap())
        assertTrue(result.plans.isEmpty())
    }

    @Test
    fun `copy to weekdays hits only the chosen days`() {
        val result = engine().copyToWeekdays(
            source,
            Monday,
            Monday.plusDays(13),
            setOf(DayOfWeek.WEDNESDAY),
            emptyMap()
        )
        assertEquals(2, result.datesChanged.size)
        assertTrue(result.datesChanged.all { it.dayOfWeek == DayOfWeek.WEDNESDAY })
    }

    @Test
    fun `copy to the next n days covers a contiguous run`() {
        val result = engine().copyToNextDays(source, 5, emptyMap())
        assertEquals(5, result.datesChanged.size)
        assertEquals(Tuesday, result.datesChanged.first())
        assertEquals(Monday.plusDays(5), result.datesChanged.last())
    }

    @Test
    fun `a day becomes a template and comes back out again`() {
        val template = engine().toTemplate(source, "Weekday")
        assertEquals(3, template.entries.size)
        assertTrue(template.entries.all { it.status == EntryStatus.Pending })

        val applied = engine().applyTemplate(template, Wednesday, existing = null)
        assertEquals(3, applied.entries.size)
        assertEquals(template.id, applied.sourceTemplateId)
    }

    @Test
    fun `a single entry can be duplicated across days`() {
        val result = engine().duplicateEntry(
            source.entries.first(),
            listOf(Tuesday, Wednesday),
            emptyMap()
        )
        assertEquals(2, result.datesChanged.size)
        assertEquals(1, result.plans.getValue(Wednesday).entries.size)
    }

    @Test
    fun `check-in targets and notes travel only when asked`() {
        val withNote = source.copy(note = "leg day")
        val plain = engine().copy(withNote, listOf(Tuesday), emptyMap()).plans.getValue(Tuesday)
        assertEquals("", plain.note)

        val carried = engine().copy(
            withNote,
            listOf(Tuesday),
            emptyMap(),
            CopyOptions(includeNote = true)
        ).plans.getValue(Tuesday)
        assertEquals("leg day", carried.note)
    }

    @Test
    fun `progress can be preserved when explicitly requested`() {
        val copied = engine().copy(
            source,
            listOf(Tuesday),
            emptyMap(),
            CopyOptions(resetProgress = false)
        ).plans.getValue(Tuesday)
        assertNotNull(copied.entries.firstOrNull { it.status == EntryStatus.Done })
    }
}
