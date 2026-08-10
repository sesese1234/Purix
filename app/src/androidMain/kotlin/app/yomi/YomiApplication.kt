package app.yomi

import android.app.Application
import app.yomi.data.YomiRepository
import app.yomi.data.storage.AndroidStorage
import app.yomi.data.storage.defaultDataDirectory
import app.yomi.data.storage.platformFileSystem
import app.yomi.di.yomiModules
import app.yomi.notification.AndroidNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

/**
 * Builds the object graph before the first Activity exists.
 *
 * The platform holders are filled in first — Android has no ambient data
 * directory or notification context — and the journal is loaded synchronously
 * so the first frame already shows the user's real day.
 */
class YomiApplication : Application() {

    lateinit var repository: YomiRepository
        private set

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AndroidStorage.initialize(this)
        AndroidNotifications.initialize(this)

        val koin = startKoin {
            modules(yomiModules(defaultDataDirectory(), platformFileSystem()))
        }.koin

        repository = koin.get()
        // A handful of small JSON files; reading them here keeps the first
        // composition free of an empty-state flash.
        runBlocking { repository.initialize() }
    }
}
