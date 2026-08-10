package app.yomi.notification

/** A notification as the platform layer receives it: already localised. */
data class NotificationPayload(
    val id: String,
    val title: String,
    val body: String
)

/**
 * Shows a native notification.
 *
 * The interface is deliberately tiny: everything about *what* to show and
 * *when* is decided by the domain's notification planner, so a platform only
 * has to know how to put a message on screen.
 */
interface Notifier {
    fun notify(payload: NotificationPayload)

    /** False when the platform is unavailable (headless CI, denied permission). */
    val isAvailable: Boolean
}

/** Used in tests and wherever notifications are switched off. */
class RecordingNotifier : Notifier {
    private val sent = mutableListOf<NotificationPayload>()

    override val isAvailable: Boolean = true

    override fun notify(payload: NotificationPayload) {
        sent += payload
    }

    fun history(): List<NotificationPayload> = sent.toList()

    fun clear() = sent.clear()
}

/** The platform's real implementation. */
expect fun createPlatformNotifier(appName: String = "Yomi"): Notifier
