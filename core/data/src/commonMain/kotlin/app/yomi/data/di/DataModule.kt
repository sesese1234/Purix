package app.yomi.data.di

import app.yomi.data.YomiRepository
import app.yomi.data.storage.CatalogStore
import app.yomi.data.storage.DocumentStore
import app.yomi.data.storage.JournalStore
import app.yomi.data.storage.SettingsStore
import app.yomi.data.storage.YomiPaths
import app.yomi.data.storage.defaultDataDirectory
import app.yomi.data.storage.platformFileSystem
import app.yomi.domain.time.SystemClock
import app.yomi.domain.time.YomiClock
import app.yomi.domain.util.IdGenerator
import app.yomi.domain.util.RandomIdGenerator
import okio.FileSystem
import okio.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wires the storage layer. Every binding is overridable, which is how the tests
 * swap in a fake file system and a clock they control.
 */
fun dataModule(
    dataDirectory: Path = defaultDataDirectory(),
    fileSystem: FileSystem = platformFileSystem()
): Module = module {
    single { YomiPaths(dataDirectory) }
    single { fileSystem }
    single {
        DocumentStore(
            fileSystem = get(),
            onError = { context, error -> println("[Yomi] storage problem at $context: ${error.message}") }
        ).also { it.ensureRoot(get<YomiPaths>().root) }
    }
    single { SettingsStore(get(), get()) }
    single { CatalogStore(get(), get()) }
    single { JournalStore(get(), get()) }
    single<YomiClock> { SystemClock() }
    single<IdGenerator> { RandomIdGenerator() }
    single {
        YomiRepository(
            settingsStore = get(),
            catalogStore = get(),
            journalStore = get(),
            clock = get(),
            ids = get()
        )
    }
}
