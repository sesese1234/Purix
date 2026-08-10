package app.yomi.domain.notifications

import app.yomi.model.DayPlan
import app.yomi.model.EntryStatus
import app.yomi.model.Goal
import app.yomi.model.GoalProgress
import app.yomi.model.NotificationKind
import app.yomi.model.NotificationSettings
import app.yomi.model.ScheduledNotification
import app.yomi.model.localTimeOfMinutes
import app.yomi.model.minutesOfDay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime

/**
 * Decides *what* should be announced and *when*, without knowing anything about
 * how a platform actually shows a notification.
 *
 * The planner is pure: give it a day and it returns the full list of moments,
 * which makes the whole notification surface testable to the minute.
 */
class NotificationPlanner {

    fun planForDay(
        plan: DayPlan,
        settings: NotificationSettings,
        goals: List<Goal> = emptyList(),
        goalProgress: Map<String, GoalProgress> = emptyMap(),
        streakLength: Int = 0,
        streakQualifyingScore: Double = 70.0,
        currentScore: Double = 0.0,
        weeklyReportDay: DayOfWeek? = null
    ): List<ScheduledNotification> {
        if (!settings.enabled) return emptyList()
        val date = plan.date
        val out = mutableListOf<ScheduledNotification>()

        if (settings.dayStartSummary) {
            out += ScheduledNotification(
                id = "dayStart-$date",
                kind = NotificationKind.DayStart,
                fireAt = date.atTime(settings.dayStartTime),
                date = date,
                args = listOf(plan.entries.size.toString())
            )
        }

        if (settings.taskReminders) {
            for (entry in plan.entries) {
                if (!entry.status.isOpen) continue
                val anchor = entry.timing.startsAt ?: entry.timing.dueAt ?: continue
                val leads = entry.reminders.filter { it.enabled }.map { it.minutesBefore }
                    .ifEmpty { listOf(settings.defaultLeadMinutes) }
                for (lead in leads.distinct()) {
                    val fireMinutes = anchor.minutesOfDay() - lead
                    if (fireMinutes < 0) continue
                    out += ScheduledNotification(
                        id = "reminder-${entry.id}-$lead",
                        kind = if (lead <= 0) NotificationKind.TaskStart else NotificationKind.TaskReminder,
                        fireAt = date.atTime(localTimeOfMinutes(fireMinutes)),
                        date = date,
                        entryId = entry.id,
                        args = listOf(entry.title, lead.toString(), entry.emoji)
                    )
                }
            }
        }

        if (settings.overdueAlerts) {
            for (entry in plan.entries) {
                if (!entry.status.isOpen) continue
                val due = entry.timing.dueAt ?: continue
                val fireMinutes = due.minutesOfDay() + OVERDUE_DELAY_MINUTES
                if (fireMinutes >= MINUTES_IN_DAY) continue
                out += ScheduledNotification(
                    id = "overdue-${entry.id}",
                    kind = NotificationKind.TaskOverdue,
                    fireAt = date.atTime(localTimeOfMinutes(fireMinutes)),
                    date = date,
                    entryId = entry.id,
                    args = listOf(entry.title, entry.emoji)
                )
            }
        }

        if (settings.dayReview && plan.entries.isNotEmpty()) {
            val open = plan.entries.count { it.status.isOpen }
            out += ScheduledNotification(
                id = "review-$date",
                kind = NotificationKind.DayReview,
                fireAt = date.atTime(settings.dayReviewTime),
                date = date,
                args = listOf(open.toString())
            )
        }

        if (settings.streakAtRisk && streakLength > 0 && currentScore < streakQualifyingScore) {
            val unfinished = plan.entries.count { it.status == EntryStatus.Pending }
            if (unfinished > 0) {
                out += ScheduledNotification(
                    id = "streak-$date",
                    kind = NotificationKind.StreakAtRisk,
                    fireAt = date.atTime(streakWarningTime(settings)),
                    date = date,
                    args = listOf(streakLength.toString(), unfinished.toString())
                )
            }
        }

        if (settings.goalPaceAlerts) {
            for (goal in goals.filter { !it.archived }) {
                val progress = goalProgress[goal.id] ?: continue
                if (progress.isComplete || progress.isOnPace) continue
                out += ScheduledNotification(
                    id = "goal-${goal.id}-$date",
                    kind = NotificationKind.GoalPace,
                    fireAt = date.atTime(settings.dayReviewTime),
                    date = date,
                    goalId = goal.id,
                    args = listOf(goal.title, progress.remaining.toInt().toString(), goal.emoji)
                )
            }
        }

        if (settings.weeklyReport && (weeklyReportDay ?: settings.weeklyReportDay) == date.dayOfWeek) {
            out += ScheduledNotification(
                id = "weekly-$date",
                kind = NotificationKind.WeeklyReport,
                fireAt = date.atTime(settings.weeklyReportTime),
                date = date
            )
        }

        return out
            .filterNot { settings.isQuiet(it.fireAt.time) }
            .sortedBy { it.fireAt }
    }

    /** Everything from [plan] that should fire strictly after [after]. */
    fun upcoming(
        notifications: List<ScheduledNotification>,
        after: LocalDateTime
    ): List<ScheduledNotification> = notifications.filter { it.fireAt > after }

    private fun streakWarningTime(settings: NotificationSettings): LocalTime {
        val minutes = (settings.dayReviewTime.minutesOfDay() - STREAK_WARNING_LEAD)
            .coerceAtLeast(0)
        return localTimeOfMinutes(minutes)
    }

    private companion object {
        const val OVERDUE_DELAY_MINUTES = 15
        const val STREAK_WARNING_LEAD = 60
        const val MINUTES_IN_DAY = 24 * 60
    }
}

/** Quiet hours may wrap past midnight, so the check has two shapes. */
fun NotificationSettings.isQuiet(time: LocalTime): Boolean {
    if (!quietHoursEnabled) return false
    val now = time.minutesOfDay()
    val start = quietStart.minutesOfDay()
    val end = quietEnd.minutesOfDay()
    return if (start <= end) now in start..end else now >= start || now <= end
}

/** Convenience for callers that only have a date. */
fun LocalDate.at(time: LocalTime): LocalDateTime = atTime(time)
