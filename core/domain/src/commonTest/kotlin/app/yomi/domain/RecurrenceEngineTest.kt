package app.yomi.domain

import app.yomi.domain.recurrence.RecurrenceEngine
import app.yomi.model.Recurrence
import app.yomi.model.RecurrenceRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceEngineTest {

    @Test
    fun `daily fires on every day from the start date`() {
        val rule = RecurrenceRule(Recurrence.Daily(), Monday)
        assertTrue(RecurrenceEngine.occursOn(rule, Monday))
        assertTrue(RecurrenceEngine.occursOn(rule, Tuesday))
        assertFalse(RecurrenceEngine.occursOn(rule, Monday.minusDays(1)))
    }

    @Test
    fun `daily with interval skips the days in between`() {
        val rule = RecurrenceRule(Recurrence.Daily(interval = 3), Monday)
        assertTrue(RecurrenceEngine.occursOn(rule, Monday))
        assertFalse(RecurrenceEngine.occursOn(rule, Monday.plusDays(1)))
        assertFalse(RecurrenceEngine.occursOn(rule, Monday.plusDays(2)))
        assertTrue(RecurrenceEngine.occursOn(rule, Monday.plusDays(3)))
    }

    @Test
    fun `weekly fires only on the selected weekdays`() {
        val rule = RecurrenceRule(
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            Monday
        )
        assertTrue(RecurrenceEngine.occursOn(rule, Monday))
        assertFalse(RecurrenceEngine.occursOn(rule, Tuesday))
        assertTrue(RecurrenceEngine.occursOn(rule, Wednesday))
    }

    @Test
    fun `every other week stays aligned to ISO weeks`() {
        val rule = RecurrenceRule(
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY), interval = 2),
            Monday
        )
        assertTrue(RecurrenceEngine.occursOn(rule, Monday))
        assertFalse(RecurrenceEngine.occursOn(rule, Monday.plusDays(7)))
        assertTrue(RecurrenceEngine.occursOn(rule, Monday.plusDays(14)))
        assertFalse(RecurrenceEngine.occursOn(rule, Monday.plusDays(21)))
    }

    @Test
    fun `monthly by day handles short months by clamping to the last day`() {
        val rule = RecurrenceRule(Recurrence.MonthlyByDay(setOf(31)), LocalDate(2026, 1, 31))
        assertTrue(RecurrenceEngine.occursOn(rule, LocalDate(2026, 1, 31)))
        // February 2026 has 28 days, so the 31st rule lands on the 28th.
        assertTrue(RecurrenceEngine.occursOn(rule, LocalDate(2026, 2, 28)))
        assertFalse(RecurrenceEngine.occursOn(rule, LocalDate(2026, 2, 27)))
        assertTrue(RecurrenceEngine.occursOn(rule, LocalDate(2026, 3, 31)))
    }

    @Test
    fun `monthly by day supports the last day shorthand`() {
        val rule = RecurrenceRule(Recurrence.MonthlyByDay(setOf(-1)), LocalDate(2026, 1, 1))
        assertTrue(RecurrenceEngine.occursOn(rule, LocalDate(2026, 4, 30)))
        assertFalse(RecurrenceEngine.occursOn(rule, LocalDate(2026, 4, 29)))
    }

    @Test
    fun `monthly by weekday finds the nth and the last occurrence`() {
        val secondTuesday = RecurrenceRule(
            Recurrence.MonthlyByWeekday(2, DayOfWeek.TUESDAY),
            LocalDate(2026, 1, 1)
        )
        assertTrue(RecurrenceEngine.occursOn(secondTuesday, LocalDate(2026, 3, 10)))
        assertFalse(RecurrenceEngine.occursOn(secondTuesday, LocalDate(2026, 3, 3)))

        val lastFriday = RecurrenceRule(
            Recurrence.MonthlyByWeekday(-1, DayOfWeek.FRIDAY),
            LocalDate(2026, 1, 1)
        )
        assertTrue(RecurrenceEngine.occursOn(lastFriday, LocalDate(2026, 3, 27)))
        assertFalse(RecurrenceEngine.occursOn(lastFriday, LocalDate(2026, 3, 20)))
    }

    @Test
    fun `every n days works before and after the anchor`() {
        val rule = RecurrenceRule(
            Recurrence.EveryNDays(4, Monday),
            Monday.minusDays(30)
        )
        assertTrue(RecurrenceEngine.occursOn(rule, Monday))
        assertTrue(RecurrenceEngine.occursOn(rule, Monday.plusDays(8)))
        assertTrue(RecurrenceEngine.occursOn(rule, Monday.minusDays(4)))
        assertFalse(RecurrenceEngine.occursOn(rule, Monday.plusDays(3)))
    }

    @Test
    fun `exceptions suppress and additions force an occurrence`() {
        val rule = RecurrenceRule(
            recurrence = Recurrence.Weekly(setOf(DayOfWeek.MONDAY)),
            startDate = Monday,
            exceptions = setOf(Monday),
            additions = setOf(Tuesday)
        )
        assertFalse(RecurrenceEngine.occursOn(rule, Monday))
        assertTrue(RecurrenceEngine.occursOn(rule, Tuesday))
    }

    @Test
    fun `an addition beats the end date`() {
        val rule = RecurrenceRule(
            recurrence = Recurrence.Daily(),
            startDate = Monday,
            endDate = Tuesday,
            additions = setOf(Sunday)
        )
        assertFalse(RecurrenceEngine.occursOn(rule, Wednesday))
        assertTrue(RecurrenceEngine.occursOn(rule, Sunday))
    }

    @Test
    fun `never occurs on nothing`() {
        val rule = RecurrenceRule(Recurrence.Never, Monday)
        assertFalse(RecurrenceEngine.occursOn(rule, Monday))
        assertTrue(RecurrenceEngine.occursOn(rule.withAddition(Monday), Monday))
    }

    @Test
    fun `occurrences between returns an ascending range`() {
        val rule = RecurrenceRule(Recurrence.Weekly(setOf(DayOfWeek.MONDAY)), Monday)
        val dates = RecurrenceEngine.occurrencesBetween(rule, Monday, Monday.plusDays(21))
        assertEquals(4, dates.size)
        assertEquals(Monday, dates.first())
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `next occurrence skips exceptions and gives up past the end date`() {
        val rule = RecurrenceRule(
            recurrence = Recurrence.Daily(),
            startDate = Monday,
            endDate = Monday.plusDays(2),
            exceptions = setOf(Monday, Tuesday)
        )
        assertEquals(Wednesday, RecurrenceEngine.nextOccurrence(rule, Monday))
        assertNull(RecurrenceEngine.nextOccurrence(rule, Monday.plusDays(3)))
    }

    @Test
    fun `once fires exactly once`() {
        val rule = RecurrenceRule(Recurrence.Once(Wednesday), Monday)
        assertFalse(RecurrenceEngine.occursOn(rule, Monday))
        assertTrue(RecurrenceEngine.occursOn(rule, Wednesday))
        assertFalse(RecurrenceEngine.occursOn(rule, Wednesday.plusDays(1)))
    }
}
