package app.yomi.domain.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The single source of "now" for the whole application.
 *
 * Everything that needs the current time takes one of these instead of reading
 * the system clock directly, which is what makes the scoring, streak and
 * missed-task logic deterministically testable.
 */
interface YomiClock {
    fun now(): LocalDateTime

    /**
     * The date the user considers "today", honouring the configurable day
     * boundary: with `dayStartHour = 4`, 02:30 still belongs to yesterday.
     */
    fun today(dayStartHour: Int = 0): LocalDate {
        val n = now()
        return if (n.hour < dayStartHour) n.date.minus(1, DateTimeUnit.DAY) else n.date
    }

    fun time(): LocalTime = now().time
}

/** Reads the real system clock in the device's own time zone. */
class SystemClock(
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : YomiClock {
    @OptIn(ExperimentalTime::class)
    override fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(timeZone)
}

/** A clock you can move by hand. Used by every test in the domain module. */
class MutableClock(initial: LocalDateTime) : YomiClock {
    var current: LocalDateTime = initial

    override fun now(): LocalDateTime = current

    fun set(date: LocalDate, time: LocalTime) {
        current = date.atTime(time)
    }

    fun advanceDays(days: Int) {
        current = current.date.plus(days, DateTimeUnit.DAY).atTime(current.time)
    }

    fun setTime(hour: Int, minute: Int) {
        current = current.date.atTime(LocalTime(hour, minute))
    }
}
