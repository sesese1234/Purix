package app.yomi.domain.recurrence

import app.yomi.model.Recurrence
import app.yomi.model.RecurrenceRule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.datetime.previousOrSame

/**
 * Answers "does this rule fire on this date?" in constant time for every
 * pattern, so a month view can be materialised without walking the calendar.
 */
object RecurrenceEngine {

    /** The single question the rest of the app asks. */
    fun occursOn(rule: RecurrenceRule, date: LocalDate): Boolean {
        // Manual overrides always win, in both directions.
        if (date in rule.exceptions) return false
        if (date in rule.additions) return true
        if (date < rule.startDate) return false
        rule.endDate?.let { if (date > it) return false }
        return matches(rule.recurrence, rule.startDate, date)
    }

    /** Every date in `[from, to]` on which the rule fires, in ascending order. */
    fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from > to) return emptyList()
        val result = mutableListOf<LocalDate>()
        var cursor = from
        while (cursor <= to) {
            if (occursOn(rule, cursor)) result += cursor
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    /** The next firing on or after [from], searched up to [limitDays] ahead. */
    fun nextOccurrence(rule: RecurrenceRule, from: LocalDate, limitDays: Int = 730): LocalDate? {
        var cursor = maxOf(from, rule.startDate)
        var steps = 0
        while (steps <= limitDays) {
            rule.endDate?.let { if (cursor > it && rule.additions.none { a -> a >= cursor } ) return null }
            if (occursOn(rule, cursor)) return cursor
            cursor = cursor.plus(1, DateTimeUnit.DAY)
            steps++
        }
        return null
    }

    private fun matches(recurrence: Recurrence, start: LocalDate, date: LocalDate): Boolean =
        when (recurrence) {
            is Recurrence.Never -> false
            is Recurrence.Once -> date == recurrence.date
            is Recurrence.Dates -> date in recurrence.dates

            is Recurrence.Daily -> {
                val interval = recurrence.interval.coerceAtLeast(1)
                start.daysUntil(date) % interval == 0
            }

            is Recurrence.Weekly -> {
                val interval = recurrence.interval.coerceAtLeast(1)
                if (date.dayOfWeek !in recurrence.days) {
                    false
                } else {
                    // Align on ISO weeks so "every other week" never drifts.
                    val startWeek = start.previousOrSame(DayOfWeek.MONDAY)
                    val dateWeek = date.previousOrSame(DayOfWeek.MONDAY)
                    val weeks = startWeek.daysUntil(dateWeek) / 7
                    weeks >= 0 && weeks % interval == 0
                }
            }

            is Recurrence.MonthlyByDay -> {
                val interval = recurrence.interval.coerceAtLeast(1)
                if (start.monthsUntil(date) % interval != 0) {
                    false
                } else {
                    val lastDay = lastDayOfMonth(date)
                    recurrence.daysOfMonth.any { requested ->
                        when {
                            requested == -1 -> date.day == lastDay
                            requested < 0 -> date.day == lastDay + requested + 1
                            // A "31st" rule still fires on the 30th of a 30-day month.
                            requested > lastDay -> date.day == lastDay
                            else -> date.day == requested
                        }
                    }
                }
            }

            is Recurrence.MonthlyByWeekday -> {
                val interval = recurrence.interval.coerceAtLeast(1)
                if (start.monthsUntil(date) % interval != 0) {
                    false
                } else if (date.dayOfWeek != recurrence.dayOfWeek) {
                    false
                } else if (recurrence.ordinal == -1) {
                    date.plus(7, DateTimeUnit.DAY).month != date.month
                } else {
                    val index = (date.day - 1) / 7 + 1
                    index == recurrence.ordinal
                }
            }

            is Recurrence.EveryNDays -> {
                val n = recurrence.n.coerceAtLeast(1)
                val delta = recurrence.anchor.daysUntil(date)
                ((delta % n) + n) % n == 0
            }
        }

    private fun lastDayOfMonth(date: LocalDate): Int {
        val firstOfThisMonth = LocalDate(date.year, date.month, 1)
        return firstOfThisMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }
}
