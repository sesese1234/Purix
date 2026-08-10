package app.yomi.feature.settings

import app.yomi.data.YomiRepository
import app.yomi.domain.scoring.ScoringContext
import app.yomi.domain.scoring.ScoringEngine
import app.yomi.model.AppSettings
import app.yomi.model.AppearanceSettings
import app.yomi.model.DayScore
import app.yomi.model.GeneralSettings
import app.yomi.model.NotificationSettings
import app.yomi.model.ScoringConfig
import app.yomi.ui.YomiViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which settings section is open. */
enum class SettingsSection { Appearance, General, Scoring, Notifications, Data }

data class SettingsUiState(
    val section: SettingsSection = SettingsSection.Appearance,
    val settings: AppSettings = AppSettings(),
    /** Today re-scored under the current rules, so tuning shows its effect live. */
    val previewScore: DayScore? = null,
    val taskCount: Int = 0,
    val goalCount: Int = 0,
    val dayCount: Int = 0,
    val message: String? = null
)

/**
 * Owns the preferences screen — including the scoring editor, which re-scores
 * today on every change so the user can see what a rule actually does before
 * committing to it.
 */
class SettingsViewModel(repository: YomiRepository) : YomiViewModel(repository) {

    private val scoringEngine = ScoringEngine()
    private val section = MutableStateFlow(SettingsSection.Appearance)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        section,
        repository.settings,
        repository.catalog,
        repository.days,
        message
    ) { current, settings, catalog, days, note ->
        val today = repository.today()
        val plan = days[today]
        SettingsUiState(
            section = current,
            settings = settings,
            previewScore = plan?.let {
                scoringEngine.scoreDay(
                    plan = it.copy(finalized = false, frozenScore = null),
                    config = settings.scoring,
                    context = ScoringContext(categories = catalog.categories.associateBy { c -> c.id })
                )
            },
            taskCount = catalog.tasks.size,
            goalCount = catalog.goals.size,
            dayCount = days.count { it.value.entries.isNotEmpty() },
            message = note
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), SettingsUiState())

    fun selectSection(value: SettingsSection) {
        section.value = value
    }

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) =
        act { repository.updateSettings { it.copy(appearance = transform(it.appearance)) } }

    fun updateGeneral(transform: (GeneralSettings) -> GeneralSettings) =
        act { repository.updateSettings { it.copy(general = transform(it.general)) } }

    fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) =
        act { repository.updateSettings { it.copy(notifications = transform(it.notifications)) } }

    fun updateScoring(transform: (ScoringConfig) -> ScoringConfig) =
        act { repository.updateSettings { it.copy(scoring = transform(it.scoring)) } }

    fun resetScoring() = act { repository.updateSettings { it.copy(scoring = ScoringConfig()) } }

    fun exportBackup(): String = repository.exportBackup()

    fun importBackup(text: String) = act {
        message.value = if (repository.importBackup(text)) IMPORT_OK else IMPORT_FAILED
    }

    fun resetEverything() = act {
        repository.resetEverything()
        message.value = RESET_DONE
    }

    fun clearMessage() {
        message.value = null
    }

    companion object {
        const val IMPORT_OK = "imported"
        const val IMPORT_FAILED = "importFailed"
        const val RESET_DONE = "reset"
        private const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}
