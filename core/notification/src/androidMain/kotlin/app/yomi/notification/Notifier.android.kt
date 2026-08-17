package app.yomi.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android notifications through the framework APIs directly.
 *
 * No extra dependency and no support library: a single channel is created on
 * first use, and a missing runtime permission simply means nothing is shown
 * rather than a crash on a background thread.
 */
object AndroidNotifications {
    private var appContext: Context? = null
    private var smallIconRes: Int = 0

    /**
     * [smallIcon] is the app's own status-bar vector; without it the system
     * falls back to a generic glyph that looks nothing like the app.
     */
    fun initialize(context: Context, smallIcon: Int = 0) {
        appContext = context.applicationContext
        smallIconRes = smallIcon
    }

    internal fun smallIcon(): Int =
        if (smallIconRes != 0) smallIconRes else android.R.drawable.ic_popup_reminder

    internal fun context(): Context? = appContext

    const val CHANNEL_ID: String = "yomi.reminders"
}

private class AndroidNotifier(private val appName: String) : Notifier {

    override val isAvailable: Boolean
        get() = AndroidNotifications.context() != null

    override fun notify(payload: NotificationPayload) {
        val context = AndroidNotifications.context() ?: return
        if (!hasPermission(context)) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val notification = Notification.Builder(context, AndroidNotifications.CHANNEL_ID)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(Notification.BigTextStyle().bigText(payload.body))
            .setSmallIcon(AndroidNotifications.smallIcon())
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(payload.id.hashCode(), notification) }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(AndroidNotifications.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                AndroidNotifications.CHANNEL_ID,
                appName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun hasPermission(context: Context): Boolean {
        // POST_NOTIFICATIONS only became a runtime permission in Android 13.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}

actual fun createPlatformNotifier(appName: String): Notifier = AndroidNotifier(appName)
