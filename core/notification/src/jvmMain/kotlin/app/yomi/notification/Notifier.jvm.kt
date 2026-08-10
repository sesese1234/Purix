package app.yomi.notification

import com.kdroid.composenotification.builder.Notification
import java.awt.GraphicsEnvironment

/**
 * Desktop notifications through kdroidFilter's native notification library,
 * which speaks the right protocol on Windows, macOS and Linux.
 *
 * Any failure — a headless session, a missing notification daemon — degrades to
 * a log line rather than taking a screen down with it.
 */
private class DesktopNotifier(private val appName: String) : Notifier {

    override val isAvailable: Boolean = !GraphicsEnvironment.isHeadless()

    override fun notify(payload: NotificationPayload) {
        if (!isAvailable) return
        runCatching {
            Notification(title = payload.title, message = payload.body)
        }.onFailure {
            println("[Yomi] notification failed for '${payload.title}': ${it.message}")
        }
    }
}

actual fun createPlatformNotifier(appName: String): Notifier = DesktopNotifier(appName)
