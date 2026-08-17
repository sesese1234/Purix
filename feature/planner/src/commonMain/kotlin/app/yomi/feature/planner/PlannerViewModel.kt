package app.yomi.feature.planner

import app.yomi.data.YomiRepository
import app.yomi.domain.plan.CopyOptions
import app.yomi.domain.scoring.WeekMath
import app.yomi.model.AppSettings
import app.yomi.model.Catalog
import app.yomi.model.Category
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.DayTemplate
import app.yomi.model.EntryStatus
import app.yomi.model.TaskDefinition
import app.yomi.ui.YomiViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Which pane of the planner is showing. */
enum class PlannerTab { Calendar, Library, Templates }

/** One square in the month grid. */
data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val score: Double?,
    val plannedCount: Int,
    val doneCount: Int,
    val finalized: Boolean
)

data class PlannerUiState(
    val tab: PlannerTab = PlannerTab.Calendar,
    val month: LocalDate = LocalDate(2026, 1, 1),
    val selectedDate: LocalDate = LocalDate(2026, 1, 1),
    val cells: List<CalendarCell> = emptyList(),
    val weekdayHeaders: List<DayOfWeek> = emptyList(),
    val selectedPlan: DayPlan = DayPlan(date = LocalDate(2026, 1, 1)),
    val selectedScore: DayScore? = null,
    val monthAverage: Double = 0.0,
    val monthPerfectDays: Int = 0,
    val settings: AppSettings = AppSettings(),
    val catalog: Catalog = Catalog(),
    val categories: Map<String, Category> = emptyMap(),
    val lastCopySummary: String? = null
) {
    val tasks: List<TaskDefinition> get() = catalog.tasks.sortedBy { it.order }
    val templates: List<DayTemplate> get() = catalog.templates.sortedBy { it.order }
}

/**
 * Runs the planner: the month grid, the day preview beside it, and the copy and
 * template operations that make "the same day again" a two-tap job.
 */
class PlannerViewModel(repository: YomiRepository) : YomiViewModel(repository) {

    private val tab = MutableStateFlow(PlannerTab.Calendar)
    private val month = MutableStateFlow(repository.today())
    private val selected = MutableStateFlow(repository.today())
    private val copySummary = MutableStateFlow<String?>(null)

    val state: StateFlow<PlannerUiState> = combine(
        combine(tab, month, selected, copySummary) { t, m, s, c -> Quad(t, m, s, c) },
        repository.days,
        repository.scores,
        repository.settings,
        repository.catalog
    ) { nav, days, scores, settings, catalog ->
        val firstDay = settings.general.firstDayOfWeek
        val grid = WeekMath.monthGrid(nav.month, firstDay)
        val today = repository.today()

        val cells = grid.map { date ->
            val plan = days[date]
            CalendarCell(
                date = date,
                inMonth = date.month == nav.month.month && date.year == nav.month.year,
                isToday = date == today,
                isSelected = date == nav.selected,
                score = scores[date]?.takeIf { (plan?.entries?.size ?: 0) > 0 }?.score,
                plannedCount = plan?.entries?.size ?: 0,
                doneCount = plan?.entries?.count { it.status == EntryStatus.Done } ?: 0,
                finalized = plan?.finalized == true
            )
        }

        val monthScores = cells.filter { it.inMonth }.mapNotNull { it.score }

        PlannerUiState(
            tab = nav.tab,
            month = nav.month,
            selectedDate = nav.selected,
            cells = cells,
            weekdayHeaders = app.yomi.model.weekdaysStartingFrom(firstDay),
            selectedPlan = days[nav.selected] ?: DayPlan(date = nav.selected),
            selectedScore = scores[nav.selected],
            monthAverage = if (monthScores.isEmpty()) 0.0 else monthScores.average(),
            monthPerfectDays = cells.count { cell ->
                cell.inMonth && scores[cell.date]?.isPerfectDay == true
            },
            settings = settings,
            catalog = catalog,
            categories = catalog.categories.associateBy { it.id },
            lastCopySummary = nav.copySummary
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), PlannerUiState())

    init {
        act { ensureVisibleMonth(month.value) }
    }

    // --------------------------------------------------------------- browsing

    fun selectTab(value: PlannerTab) {
        tab.value = value
    }

    fun shiftMonth(delta: Int) {
        val target = month.value.plus(delta, DateTimeUnit.MONTH)
        month.value = target
        act { ensureVisibleMonth(target) }
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
        if (date.month != month.value.month || date.year != month.value.year) {
            month.value = date
        }
        act { repository.ensureDay(date) }
    }

    fun goToToday() = selectDate(repository.today())

    private suspend fun ensureVisibleMonth(anchor: LocalDate) {
        val first = WeekMath.startOfWeek(
            WeekMath.startOfMonth(anchor),
            repository.settings.value.general.firstDayOfWeek
        )
        val last = WeekMath.endOfWeek(
            WeekMath.endOfMonth(anchor),
            repository.settings.value.general.firstDayOfWeek
        )
        repository.ensureRange(first, last)
    }

    // ----------------------------------------------------------------- copying

    fun copyToDates(targets: List<LocalDate>, options: CopyOptions) = act {
        val result = repository.copyDay(selected.value, targets, options)
        copySummary.value = "${result.entriesCopied}|${result.datesChanged.size}"
    }

    fun copyToNextDays(count: Int, options: CopyOptions) {
        val targets = List(count) { selected.value.plus(it + 1, DateTimeUnit.DAY) }
        copyToDates(targets, options)
    }

    fun copyToWeekdays(
        weekdays: Set<DayOfWeek>,
        weeksAhead: Int,
        options: CopyOptions
    ) = act {
        val from = selected.value.plus(1, DateTimeUnit.DAY)
        val to = selected.value.plus(weeksAhead * DAYS_PER_WEEK, DateTimeUnit.DAY)
        val result = repository.copyDayToRange(selected.value, from, to, weekdays, options)
        copySummary.value = "${result.entriesCopied}|${result.datesChanged.size}"
    }

    fun clearCopySummary() {
        copySummary.value = null
    }

    // --------------------------------------------------------------- templates

    fun saveAsTemplate(name: String, emoji: String = "") = act {
        repository.saveDayAsTemplate(selected.value, name, emoji)
    }

    fun applyTemplate(templateId: String, options: CopyOptions) = act {
        repository.applyTemplate(templateId, selected.value, options)
    }

    fun deleteTemplate(templateId: String) = act { repository.deleteTemplate(templateId) }

    // ----------------------------------------------------------------- library

    fun upsertTask(task: TaskDefinition) = act { repository.upsertTask(task) }

    fun deleteTask(taskId: String) = act { repository.deleteTask(taskId) }

    fun setTaskArchived(taskId: String, archived: Boolean) =
        act { repository.setTaskArchived(taskId, archived) }

    fun upsertCategory(category: Category) = act { repository.upsertCategory(category) }

    fun deleteCategory(categoryId: String) = act { repository.deleteCategory(categoryId) }

    fun newId(): String = repository.newId()

    // ------------------------------------------------------------ day editing

    fun toggleDone(entryId: String) = act { repository.toggleDone(selected.value, entryId) }

    fun deleteEntry(entryId: String) = act { repository.deleteEntry(selected.value, entryId) }

    private data class Quad(
        val tab: PlannerTab,
        val month: LocalDate,
        val selected: LocalDate,
        val copySummary: String?
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
        const val DAYS_PER_WEEK = 7
    }
}
