package app.yomi

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.yomi.data.YomiRepository
import app.yomi.data.storage.defaultDataDirectory
import app.yomi.data.storage.platformFileSystem
import app.yomi.designsystem.i18n.stringsFor
import app.yomi.di.yomiModules
import app.yomi.notification.NotificationService
import app.yomi.notification.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale

/**
 * Desktop entry point.
 *
 * The graph is built and the journal loaded *before* the first frame, so the
 * window never flashes an empty day on its way to the real one.
 */
fun main() = application {
    val koin = remember {
        startKoin {
            modules(yomiModules(defaultDataDirectory(), platformFileSystem()))
        }.koin
    }

    val repository = remember { koin.get<YomiRepository>() }
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Blocking here is deliberate: reading a few JSON files is fast, and it
    // guarantees the first composition already sees the user's real data.
    remember { runBlocking { repository.initialize() } }

    val systemIsHebrew = remember { Locale.getDefault().language in HEBREW_LANGUAGE_TAGS }

    val notifications = remember {
        NotificationService(
            repository = repository,
            notifier = koin.get<Notifier>(),
            copy = notificationCopy(
                stringsFor(repository.settings.value.general.language, systemIsHebrew)
            )
        )
    }

    LaunchedEffect(Unit) { notifications.start(appScope) }

    val windowState = rememberWindowState(size = DpSize(WINDOW_WIDTH, WINDOW_HEIGHT))

    Window(
        onCloseRequest = {
            notifications.stop()
            stopKoin()
            exitApplication()
        },
        state = windowState,
        title = "Yomi"
    ) {
        YomiApp(
            windowWidth = windowState.size.width,
            systemIsHebrew = systemIsHebrew,
            onExport = ::copyToClipboard
        )
    }
}

/** Backups are copied to the clipboard: no file dialog, no permissions dance. */
private fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }.onFailure { println("[Yomi] clipboard unavailable: ${it.message}") }
}

private val WINDOW_WIDTH = 1180.dp
private val WINDOW_HEIGHT = 860.dp
// The JDK still reports Hebrew as the legacy "iw" on some platforms.
private val HEBREW_LANGUAGE_TAGS = setOf("he", "iw")
