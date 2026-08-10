package app.yomi.data.storage

import app.yomi.model.AppSettings
import app.yomi.model.Catalog
import app.yomi.model.DayPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.builtins.ListSerializer

/** Preferences, held in memory and mirrored to a single file. */
class SettingsStore(
    private val documents: DocumentStore,
    private val paths: YomiPaths
) {
    private val state = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = state.asStateFlow()
    private val writeLock = Mutex()

    fun load() {
        state.value = documents.read(paths.settings, AppSettings.serializer()) ?: AppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        writeLock.withLock {
            val updated = transform(state.value)
            if (updated == state.value) return@withLock
            state.value = updated
            documents.write(paths.settings, AppSettings.serializer(), updated)
        }
    }

    suspend fun replace(settings: AppSettings) = update { settings }
}

/** The library of blueprints, categories, goals and templates. */
class CatalogStore(
    private val documents: DocumentStore,
    private val paths: YomiPaths
) {
    private val state = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = state.asStateFlow()
    private val writeLock = Mutex()

    /** True when there was no catalog on disk, which means a first run. */
    fun load(): Boolean {
        val stored = documents.read(paths.catalog, Catalog.serializer())
        state.value = stored ?: Catalog()
        return stored == null
    }

    suspend fun update(transform: (Catalog) -> Catalog) {
        writeLock.withLock {
            val updated = transform(state.value)
            if (updated == state.value) return@withLock
            state.value = updated
            documents.write(paths.catalog, Catalog.serializer(), updated)
        }
    }

    suspend fun replace(catalog: Catalog) = update { catalog }
}

/**
 * The day-by-day journal, sharded one file per month.
 *
 * Everything is kept in memory — years of planning amount to a couple of
 * megabytes — while writes only touch the month that actually changed.
 */
class JournalStore(
    private val documents: DocumentStore,
    private val paths: YomiPaths
) {
    private val state = MutableStateFlow<Map<LocalDate, DayPlan>>(emptyMap())
    val days: StateFlow<Map<LocalDate, DayPlan>> = state.asStateFlow()
    private val writeLock = Mutex()

    private val shardSerializer = ListSerializer(DayPlan.serializer())

    fun load() {
        val loaded = mutableMapOf<LocalDate, DayPlan>()
        for (file in documents.listFiles(paths.journalDir)) {
            val plans = documents.read(file, shardSerializer) ?: continue
            for (plan in plans) loaded[plan.date] = plan
        }
        state.value = loaded
    }

    fun day(date: LocalDate): DayPlan? = state.value[date]

    suspend fun put(plan: DayPlan) = putAll(listOf(plan))

    suspend fun putAll(plans: Collection<DayPlan>) {
        if (plans.isEmpty()) return
        writeLock.withLock {
            val updated = state.value.toMutableMap()
            for (plan in plans) updated[plan.date] = plan
            state.value = updated
            plans.map { it.date.year to it.date.month.number }
                .distinct()
                .forEach { (year, month) -> persistShard(year, month, updated) }
        }
    }

    suspend fun remove(date: LocalDate) {
        writeLock.withLock {
            if (!state.value.containsKey(date)) return@withLock
            val updated = state.value.toMutableMap()
            updated.remove(date)
            state.value = updated
            persistShard(date.year, date.month.number, updated)
        }
    }

    suspend fun replaceAll(plans: Collection<DayPlan>) {
        writeLock.withLock {
            for (file in documents.listFiles(paths.journalDir)) documents.delete(file)
            val updated = plans.associateBy { it.date }
            state.value = updated
            updated.keys.map { it.year to it.month.number }
                .distinct()
                .forEach { (year, month) -> persistShard(year, month, updated) }
        }
    }

    private fun persistShard(year: Int, month: Int, all: Map<LocalDate, DayPlan>) {
        val shard = all.values
            .filter { it.date.year == year && it.date.month.number == month }
            .sortedBy { it.date }
        val path = paths.journalShard(year, month)
        // An empty month leaves no file behind, so backups stay tidy.
        if (shard.isEmpty()) documents.delete(path) else documents.write(path, shardSerializer, shard)
    }
}
