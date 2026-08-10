package app.yomi.designsystem.format

import app.yomi.designsystem.i18n.Strings
import app.yomi.model.DayPart
import app.yomi.model.EntryStatus
import app.yomi.model.GoalPeriod
import app.yomi.model.GoalType
import app.yomi.model.Priority
import app.yomi.model.TaskTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatting helpers shared by every screen; no platform locale involved. */
object Fmt {

    fun time(time: LocalTime?, use24h: Boolean): String {
        if (time == null) return "—"
        return if (use24h) {
            "${time.hour.pad2()}:${time.minute.pad2()}"
        } else {
            val suffix = if (time.hour < 12) "AM" else "PM"
            val hour = when (val h = time.hour % 12) {
                0 -> 12
                else -> h
            }
            "$hour:${time.minute.pad2()} $suffix"
        }
    }

    fun timeRange(timing: TaskTiming, use24h: Boolean, strings: Strings): String = when (timing) {
        is TaskTiming.Fixed ->
            if (timing.end == null) time(timing.start, use24h)
            else "${time(timing.start, use24h)} – ${time(timing.end, use24h)}"

        is TaskTiming.Window ->
            "${time(timing.earliest, use24h)} – ${time(timing.latest, use24h)} · ${timing.durationMinutes}′"

        is TaskTiming.Deadline -> "${strings.byTime} ${time(timing.by, use24h)}"
        TaskTiming.Anytime -> strings.partAnytime
    }

    fun date(date: LocalDate, strings: Strings): String {
        val month = strings.monthNames.getOrElse(date.month.number - 1) { "" }
        return if (strings.isRtl) "${date.day} ב$month" else "$month ${date.day}"
    }

    fun dateWithWeekday(date: LocalDate, strings: Strings): String =
        "${weekday(date.dayOfWeek, strings)}, ${date(date, strings)}"

    fun shortDate(date: LocalDate): String = "${date.day.pad2()}/${date.month.number.pad2()}"

    fun isoDate(date: LocalDate): String =
        "${date.year}-${date.month.number.pad2()}-${date.day.pad2()}"

    fun weekday(day: DayOfWeek, strings: Strings): String = when (day) {
        DayOfWeek.MONDAY -> strings.monday
        DayOfWeek.TUESDAY -> strings.tuesday
        DayOfWeek.WEDNESDAY -> strings.wednesday
        DayOfWeek.THURSDAY -> strings.thursday
        DayOfWeek.FRIDAY -> strings.friday
        DayOfWeek.SATURDAY -> strings.saturday
        else -> strings.sunday
    }

    fun weekdayShort(day: DayOfWeek, strings: Strings): String = when (day) {
        DayOfWeek.MONDAY -> strings.mondayShort
        DayOfWeek.TUESDAY -> strings.tuesdayShort
        DayOfWeek.WEDNESDAY -> strings.wednesdayShort
        DayOfWeek.THURSDAY -> strings.thursdayShort
        DayOfWeek.FRIDAY -> strings.fridayShort
        DayOfWeek.SATURDAY -> strings.saturdayShort
        else -> strings.sundayShort
    }

    fun dayPart(part: DayPart, strings: Strings): String = when (part) {
        DayPart.EarlyMorning -> strings.partEarlyMorning
        DayPart.Morning -> strings.partMorning
        DayPart.Afternoon -> strings.partAfternoon
        DayPart.Evening -> strings.partEvening
        DayPart.Night -> strings.partNight
        DayPart.Anytime -> strings.partAnytime
    }

    fun dayPartEmoji(part: DayPart): String = when (part) {
        DayPart.EarlyMorning -> "🌄"
        DayPart.Morning -> "☀️"
        DayPart.Afternoon -> "🌤️"
        DayPart.Evening -> "🌆"
        DayPart.Night -> "🌙"
        DayPart.Anytime -> "🕊️"
    }

    fun status(status: EntryStatus, strings: Strings): String = when (status) {
        EntryStatus.Pending -> strings.statusPending
        EntryStatus.InProgress -> strings.statusInProgress
        EntryStatus.Done -> strings.statusDone
        EntryStatus.Partial -> strings.statusPartial
        EntryStatus.Skipped -> strings.statusSkipped
        EntryStatus.Missed -> strings.statusMissed
        EntryStatus.Deferred -> strings.statusDeferred
    }

    fun priority(priority: Priority, strings: Strings): String = when (priority) {
        Priority.Low -> strings.priorityLow
        Priority.Normal -> strings.priorityNormal
        Priority.High -> strings.priorityHigh
        Priority.Critical -> strings.priorityCritical
    }

    fun goalType(type: GoalType, strings: Strings): String = when (type) {
        GoalType.Count -> strings.goalTypeCount
        GoalType.Minutes -> strings.goalTypeMinutes
        GoalType.Quantity -> strings.goalTypeQuantity
        GoalType.AverageScore -> strings.goalTypeAverageScore
        GoalType.Streak -> strings.goalTypeStreak
    }

    fun goalPeriod(period: GoalPeriod, strings: Strings): String = when (period) {
        GoalPeriod.Day -> strings.periodDay
        GoalPeriod.Week -> strings.periodWeek
        GoalPeriod.Month -> strings.periodMonth
        GoalPeriod.Total -> strings.periodTotal
    }

    /** Scores are shown without a decimal unless the fraction actually matters. */
    fun score(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (abs(rounded - rounded.roundToInt()) < 0.05) {
            rounded.roundToInt().toString()
        } else {
            val whole = rounded.toInt()
            val tenth = ((abs(rounded) * 10).roundToInt() % 10)
            "$whole.$tenth"
        }
    }

    fun percent(ratio: Double): String = "${(ratio * 100).roundToInt()}%"

    fun signed(value: Double): String {
        val text = score(abs(value))
        return if (value < 0) "−$text" else "+$text"
    }

    fun amount(value: Double, unit: String = ""): String {
        val body = if (abs(value - value.roundToInt()) < 0.05) {
            value.roundToInt().toString()
        } else {
            score(value)
        }
        return if (unit.isBlank()) body else "$body $unit"
    }

    fun duration(minutes: Int, strings: Strings): String {
        if (minutes < 60) return "$minutes′"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "${hours}h" else "${hours}h ${rest}′"
    }

    private fun Int.pad2(): String = if (this < 10) "0$this" else toString()
}
