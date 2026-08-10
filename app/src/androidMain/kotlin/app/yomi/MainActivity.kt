package app.yomi

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.i18n.stringsFor
import app.yomi.notification.NotificationService
import app.yomi.notification.Notifier
import org.koin.mp.KoinPlatform

/**
 * The single Activity. Everything above it is the same Compose tree the desktop
 * build runs, so the two platforms cannot drift apart.
 */
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askForNotificationPermission()

        val application = applicationContext as YomiApplication
        val systemIsHebrew = resources.configuration.locales.get(0)?.language in HEBREW_LANGUAGE_TAGS

        val notifications = NotificationService(
            repository = application.repository,
            notifier = KoinPlatform.getKoin().get<Notifier>(),
            copy = notificationCopy(
                stringsFor(application.repository.settings.value.general.language, systemIsHebrew)
            )
        )

        setContent {
            val configuration = LocalConfiguration.current
            val widthDp = remember(configuration.screenWidthDp) { configuration.screenWidthDp.dp }

            LaunchedEffect(Unit) { notifications.start(application.applicationScope) }

            YomiApp(
                windowWidth = widthDp,
                systemIsHebrew = systemIsHebrew,
                onExport = ::copyToClipboard
            )
        }
    }

    /** Backups go to the clipboard, which needs no storage permission at all. */
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Yomi backup", text))
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        // The JDK still reports Hebrew as the legacy "iw" on some platforms.
        val HEBREW_LANGUAGE_TAGS = setOf("he", "iw")
    }
}
