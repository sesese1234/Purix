package app.yomi.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationKind {
    @SerialName("taskReminder") TaskReminder,
    @SerialName("taskStart") TaskStart,
    @SerialName("taskOverdue") TaskOverdue,
    @SerialName("dayStart") DayStart,
    @SerialName("dayReview") DayReview,
    @SerialName("weeklyReport") WeeklyReport,
    @SerialName("streakAtRisk") StreakAtRisk,
    @SerialName("goalPace") GoalPace
}

/**
 * A notification the planner decided should fire. The title and body are
 * localised keys resolved by the UI layer, so the domain stays language-free.
 */
@Serializable
data class ScheduledNotification(
    val id: String,
    val kind: NotificationKind,
    val fireAt: LocalDateTime,
    val date: LocalDate,
    val entryId: String? = null,
    val goalId: String? = null,
    /** Substitutions for the localised template (task title, minutes, ...). */
    val args: List<String> = emptyList()
) {
    /** Stable key used to avoid firing the same notification twice. */
    val dedupeKey: String get() = "${kind.name}:${date}:${entryId ?: goalId ?: "-"}:$fireAt"
}
