package app.yomi

import app.yomi.data.YomiRepository
import app.yomi.di.viewModelModule
import app.yomi.data.di.dataModule
import app.yomi.feature.goals.GoalsViewModel
import app.yomi.feature.insights.InsightsViewModel
import app.yomi.feature.planner.PlannerViewModel
import app.yomi.feature.settings.SettingsViewModel
import app.yomi.feature.today.TodayViewModel
import app.yomi.notification.NotificationService
import app.yomi.notification.RecordingNotifier
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the object graph actually assembles.
 *
 * Dependency wiring is the classic thing that compiles perfectly and then fails
 * on the first launch, so the graph is built here — repository, every screen
 * model, and the notification service — against a fake file system.
 */
class WiringTest {

    private val fileSystem = FakeFileSystem()

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the whole graph resolves and the app initialises`(): Unit = runBlocking {
        val koin = startKoin {
            modules(dataModule("/yomi-test".toPath(), fileSystem), viewModelModule)
        }.koin

        val repository = koin.get<YomiRepository>()
        repository.initialize()

        assertTrue(repository.catalog.value.tasks.isNotEmpty(), "first run should seed a starter library")
        assertTrue(repository.days.value.isNotEmpty(), "today should have been materialised")

        // Every screen model must be constructible from the graph.
        assertNotNull(koin.get<TodayViewModel>())
        assertNotNull(koin.get<PlannerViewModel>())
        assertNotNull(koin.get<GoalsViewModel>())
        assertNotNull(koin.get<InsightsViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
    }

    @Test
    fun `the notification service fires its planned alerts exactly once`(): Unit = runBlocking {
        val koin = startKoin {
            modules(dataModule("/yomi-notify".toPath(), fileSystem), viewModelModule)
        }.koin

        val repository = koin.get<YomiRepository>()
        repository.initialize()
        // The day starts at 07:15 in the defaults; make sure everything earlier
        // is in the past by moving the review and quiet hours out of the way.
        repository.updateSettings { settings ->
            settings.copy(
                notifications = settings.notifications.copy(
                    quietHoursEnabled = false,
                    dayStartTime = kotlinx.datetime.LocalTime(0, 1)
                )
            )
        }

        val notifier = RecordingNotifier()
        val service = NotificationService(
            repository = repository,
            notifier = notifier,
            copy = notificationCopy(app.yomi.designsystem.i18n.EnglishStrings)
        )

        service.tick()
        val afterFirst = notifier.history().size
        service.tick()

        assertEquals(afterFirst, notifier.history().size, "a second tick must not repeat alerts")
        assertTrue(notifier.history().all { it.title.isNotBlank() }, "every alert needs a title")
    }

    @Test
    fun `a backup survives a full export and import cycle`(): Unit = runBlocking {
        val koin = startKoin {
            modules(dataModule("/yomi-backup".toPath(), fileSystem), viewModelModule)
        }.koin

        val repository = koin.get<YomiRepository>()
        repository.initialize()
        val today = repository.today()
        val entryId = repository.day(today).entries.first().id
        repository.complete(today, entryId)

        val backup = repository.exportBackup()
        assertTrue(backup.contains("\"exportedAt\""), "the backup should carry its metadata")

        repository.resetEverything()
        assertTrue(repository.importBackup(backup))
        assertEquals(
            app.yomi.model.EntryStatus.Done,
            repository.day(today).entries.first { it.id == entryId }.status
        )
    }
}
