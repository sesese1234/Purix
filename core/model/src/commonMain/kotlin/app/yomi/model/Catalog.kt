package app.yomi.model

import kotlinx.serialization.Serializable

/**
 * The user's library of reusable definitions: everything except the day-by-day
 * journal. Persisted as one document so a backup is trivially consistent.
 */
@Serializable
data class Catalog(
    val tasks: List<TaskDefinition> = emptyList(),
    val categories: List<Category> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val templates: List<DayTemplate> = emptyList(),
    val schemaVersion: Int = AppSettings.CURRENT_SCHEMA_VERSION
) {
    val activeTasks: List<TaskDefinition> get() = tasks.filter { !it.archived }
    val activeGoals: List<Goal> get() = goals.filter { !it.archived }
    val activeCategories: List<Category> get() = categories.filter { !it.archived }

    fun task(id: String): TaskDefinition? = tasks.firstOrNull { it.id == id }
    fun category(id: String?): Category? = id?.let { c -> categories.firstOrNull { it.id == c } }
    fun goal(id: String): Goal? = goals.firstOrNull { it.id == id }
    fun template(id: String): DayTemplate? = templates.firstOrNull { it.id == id }
}

/** A complete, portable backup of the app. */
@Serializable
data class YomiBackup(
    val exportedAt: String,
    val appVersion: String,
    val settings: AppSettings,
    val catalog: Catalog,
    val days: List<DayPlan>
)
