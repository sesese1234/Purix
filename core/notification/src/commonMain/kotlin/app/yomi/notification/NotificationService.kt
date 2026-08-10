package app.yomi.notification

import app.yomi.data.YomiRepository
import app.yomi.domain.notifications.NotificationPlanner
import app.yomi.model.NotificationKind
import app.yomi.model.ScheduledNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/** Renders a scheduled notification into the words the user will read. */
fun interface NotificationCopy {
    fun render(notification: ScheduledNotification): NotificationPayload
}

/**
 * The runtime half of notifications: a one-minute heartbeat that re-plans the
 * day, fires anything now due, and keeps the journal's missed-task detection
 * up to date while the app is open.
 *
 * Notifications already fired are remembered by their dedupe key, so a replan
 * — triggered by any edit — never repeats an alert.
 */
class NotificationService(
    private val repository: YomiRepository,
    private val notifier: Notifier,
    private val copy: NotificationCopy,
    private val planner: NotificationPlanner = NotificationPlanner()
) {
    private val fired = mutableSetOf<String>()
    private var job: Job? = null

    /** Starts the heartbeat. Safe to call twice; the second call is ignored. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            // On a cold start, everything already in the past is considered handled.
            markPastAsHandled()
            while (isActive) {
                tick()
                delay(TICK_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** One heartbeat: refresh the day, then fire whatever has come due. */
    suspend fun tick() {
        val today = repository.today()
        repository.ensureDay(today)

        val settings = repository.settings.value
        if (!settings.notifications.enabled || !notifier.isAvailable) return

        val now = nowFromRepository()
        val plan = repository.day(today)
        val due = planner.planForDay(
            plan = plan,
            settings = settings.notifications,
            goals = repository.catalog.value.activeGoals,
            goalProgress = repository.allGoalProgress(today),
            streakLength = repository.currentStreak(),
            streakQualifyingScore = settings.scoring.streakQualifyingScore,
            currentScore = repository.score(today).score
        ).filter { it.fireAt <= now && it.dedupeKey !in fired }

        for (notification in due) {
            fired += notification.dedupeKey
            notifier.notify(copy.render(notification))
        }
    }

    /** Everything earlier today is treated as already delivered. */
    private fun markPastAsHandled() {
        val today = repository.today()
        val now = nowFromRepository()
        val settings = repository.settings.value
        planner.planForDay(
            plan = repository.day(today),
            settings = settings.notifications
        ).filter { it.fireAt <= now }.forEach { fired += it.dedupeKey }
    }

    private fun nowFromRepository(): LocalDateTime =
        LocalDateTime(repository.today(), repository.now())

    /** Test seam: send one notification straight away. */
    fun sendTest(payload: NotificationPayload) = notifier.notify(payload)

    private companion object {
        const val TICK_MILLIS = 60_000L
    }
}
