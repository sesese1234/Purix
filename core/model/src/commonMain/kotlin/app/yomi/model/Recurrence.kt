package app.yomi.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The repetition pattern of a task definition.
 *
 * Every variant is intentionally *stateless*: given a date, the engine can
 * answer "does this occur?" without walking the calendar, which keeps
 * materialisation cheap for arbitrary dates in the past or the future.
 */
@Serializable
sealed interface Recurrence {

    /** A one-off item pinned to a single date. */
    @Serializable
    @SerialName("once")
    data class Once(val date: LocalDate) : Recurrence

    /** Every day, or every [interval] days counted from the rule's start date. */
    @Serializable
    @SerialName("daily")
    data class Daily(val interval: Int = 1) : Recurrence

    /** Selected weekdays, optionally only on every [interval]-th week. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(
        val days: Set<DayOfWeek>,
        val interval: Int = 1
    ) : Recurrence

    /** Selected days of the month; `-1` means "last day of the month". */
    @Serializable
    @SerialName("monthlyByDay")
    data class MonthlyByDay(
        val daysOfMonth: Set<Int>,
        val interval: Int = 1
    ) : Recurrence

    /** e.g. "the 2nd Tuesday"; [ordinal] `-1` means the last one in the month. */
    @Serializable
    @SerialName("monthlyByWeekday")
    data class MonthlyByWeekday(
        val ordinal: Int,
        val dayOfWeek: DayOfWeek,
        val interval: Int = 1
    ) : Recurrence

    /** Every N days counted from [anchor], independent of the rule start date. */
    @Serializable
    @SerialName("everyNDays")
    data class EveryNDays(
        val n: Int,
        val anchor: LocalDate
    ) : Recurrence

    /** An explicit list of dates. */
    @Serializable
    @SerialName("dates")
    data class Dates(val dates: Set<LocalDate>) : Recurrence

    /** Never occurs on its own; used for tasks that are only added by hand. */
    @Serializable
    @SerialName("never")
    data object Never : Recurrence
}

/**
 * A [Recurrence] plus the calendar bounds and the manual overrides a user
 * accumulates over time (a holiday they skipped, an extra session they added).
 */
@Serializable
data class RecurrenceRule(
    val recurrence: Recurrence = Recurrence.Daily(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** Dates on which the pattern is suppressed. */
    val exceptions: Set<LocalDate> = emptySet(),
    /** Dates added on top of the pattern. */
    val additions: Set<LocalDate> = emptySet()
) {
    fun withException(date: LocalDate): RecurrenceRule =
        copy(exceptions = exceptions + date, additions = additions - date)

    fun withAddition(date: LocalDate): RecurrenceRule =
        copy(additions = additions + date, exceptions = exceptions - date)
}
