package app.yomi.feature.today

import app.yomi.data.YomiRepository
import app.yomi.domain.scoring.ScoreBook
import app.yomi.domain.scoring.ScoringContext
import app.yomi.domain.scoring.ScoringEngine
import app.yomi.model.AppSettings
import app.yomi.model.Category
import app.yomi.model.DayPart
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.EntryStatus
import app.yomi.model.Goal
import app.yomi.model.GoalProgress
import app.yomi.model.PlanEntry
import app.yomi.model.Priority
import app.yomi.model.TaskTiming
import app.yomi.model.TimelineGrouping
import app.yomi.ui.YomiViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** One block of the timeline, already titled and sorted. */
data class TimelineGroup(
    val key: String,
    val title: String,
    val emoji: String,
    val entries: List<PlanEntry>,
    /** Set when the group is a time-of-day bucket, so the UI can localise it. */
    val dayPart: DayPart? = null,
    val priority: Priority? = null,
    val status: EntryStatus? = null
)

/** A goal as the Today screen shows it. */
data class GoalCardState(
    val goal: Goal,
    val progress: GoalProgress,
    val todayContribution: Double
)

data class TodayUiState(
    val date: LocalDate = LocalDate(2026, 1, 1),
    val isToday: Boolean = true,
    val plan: DayPlan = DayPlan(date = LocalDate(2026, 1, 1)),
    val score: DayScore? = null,
    /** The score this day would end on if everything still open were finished. */
    val potentialScore: Double = 0.0,
    val streak: Int = 0,
    val settings: AppSettings = AppSettings(),
    val categories: Map<String, Category> = emptyMap(),
    val goals: List<GoalCardState> = emptyList(),
    val groups: List<TimelineGroup> = emptyList(),
    val upNext: PlanEntry? = null,
    val now: LocalTime = LocalTime(0, 0)
) {
    val entries: List<PlanEntry> get() = plan.entries
    val doneCount: Int get() = entries.count { it.status == EntryStatus.Done }
    val openCount: Int get() = entries.count { it.status.isOpen }
    val missedCount: Int get() = entries.count { it.status == EntryStatus.Missed }
    val progress: Float
        get() = if (entries.isEmpty()) 0f else doneCount.toFloat() / entries.size
}

/**
 * Drives the Today screen: it turns the repository's flows into one ready-made
 * state object and exposes the handful of intents the screen can trigger.
 */
class TodayViewModel(repository: YomiRepository) : YomiViewModel(repository) {

    private val scoringEngine = ScoringEngine()
    private val scoreBook = ScoreBook()

    private val selectedDate = MutableStateFlow(repository.today())

    val state: StateFlow<TodayUiState> = combine(
        selectedDate,
        repository.days,
        repository.scores,
        repository.settings,
        repository.catalog
    ) { date, days, scores, settings, catalog ->
        val plan = days[date] ?: DayPlan(date = date)
        val categories = catalog.categories.associateBy { it.id }
        val goalStates = catalog.activeGoals.map { goal ->
            GoalCardState(
                goal = goal,
                progress = repository.goalProgress(goal, date),
                todayContribution = 0.0
            )
        }

        TodayUiState(
            date = date,
            isToday = date == repository.today(),
            plan = plan,
            score = scores[date],
            potentialScore = potentialScore(plan, settings, categories, scores, date),
            streak = scoreBook.currentStreak(scores, repository.today(), settings.scoring),
            settings = settings,
            categories = categories,
            goals = goalStates,
            groups = buildGroups(plan, settings, categories),
            upNext = upNext(plan, repository.now()),
            now = repository.now()
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), TodayUiState())

    init {
        act { repository.ensureDay(selectedDate.value) }
    }

    // ------------------------------------------------------------------ dates

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        act { repository.ensureDay(date) }
    }

    fun goToToday() = selectDate(repository.today())

    fun shiftDay(days: Int) = selectDate(selectedDate.value.plus(days, DateTimeUnit.DAY))

    /** Re-checks the clock: called by the app's minute ticker. */
    fun refresh() = act { repository.ensureDay(selectedDate.value) }

    // --------------------------------------------------------------- intents

    fun toggleDone(entryId: String) = act { repository.toggleDone(selectedDate.value, entryId) }

    fun toggleSubtask(entryId: String, subtaskId: String) =
        act { repository.toggleSubtask(selectedDate.value, entryId, subtaskId) }

    fun start(entryId: String) = act { repository.start(selectedDate.value, entryId) }

    fun skip(entryId: String, reason: String) = act { repository.skip(selectedDate.value, entryId, reason) }

    fun defer(entryId: String, to: LocalDate) = act { repository.defer(selectedDate.value, entryId, to) }

    fun deleteEntry(entryId: String) = act { repository.deleteEntry(selectedDate.value, entryId) }

    fun setQuantity(entryId: String, value: Double?) =
        act { repository.setQuantity(selectedDate.value, entryId, value) }

    fun updateEntry(entry: PlanEntry) = act { repository.updateEntry(selectedDate.value, entry) }

    fun addQuickTask(title: String, timing: TaskTiming = TaskTiming.Anytime, priority: Priority = Priority.Normal) {
        if (title.isBlank()) return
        act {
            repository.addEntry(
                selectedDate.value,
                PlanEntry(
                    id = repository.newId(),
                    title = title.trim(),
                    emoji = "✨",
                    timing = timing,
                    priority = priority
                )
            )
        }
    }

    fun setWakeTime(time: LocalTime?) =
        act { repository.updateCheckIn(selectedDate.value) { it.copy(wakeActual = time) } }

    fun setWakeTarget(time: LocalTime) =
        act { repository.updateCheckIn(selectedDate.value) { it.copy(wakeTarget = time) } }

    fun setSleepTime(time: LocalTime?) =
        act { repository.updateCheckIn(selectedDate.value) { it.copy(sleepActual = time) } }

    fun setSleepTarget(time: LocalTime) =
        act { repository.updateCheckIn(selectedDate.value) { it.copy(sleepTarget = time) } }

    fun setMood(value: Int?) = act { repository.updateCheckIn(selectedDate.value) { it.copy(mood = value) } }

    fun setEnergy(value: Int?) = act { repository.updateCheckIn(selectedDate.value) { it.copy(energy = value) } }

    fun setJournal(text: String) =
        act { repository.updateCheckIn(selectedDate.value) { it.copy(journal = text) } }

    fun setDayNote(text: String) = act { repository.setDayNote(selectedDate.value, text) }

    fun finalizeDay() = act { repository.finalizeDay(selectedDate.value) }

    fun reopenDay() = act { repository.reopenDay(selectedDate.value) }

    // -------------------------------------------------------------- helpers

    /**
     * What the day could still become: every open task treated as finished on
     * time. This is the number the "reach for the maximum" loop is built on.
     */
    private fun potentialScore(
        plan: DayPlan,
        settings: AppSettings,
        categories: Map<String, Category>,
        scores: Map<LocalDate, DayScore>,
        date: LocalDate
    ): Double {
        if (plan.entries.isEmpty()) return 0.0
        val optimistic = plan.copy(
            finalized = false,
            frozenScore = null,
            entries = plan.entries.map { entry ->
                if (entry.status.isOpen) {
                    entry.copy(
                        status = EntryStatus.Done,
                        completedAt = entry.timing.dueAt ?: entry.completedAt,
                        subtasks = entry.subtasks.map { it.copy(done = true) }
                    )
                } else {
                    entry
                }
            },
            checkIn = plan.checkIn
        )
        val streak = scoreBook.currentStreak(scores, date, settings.scoring)
        return scoringEngine.scoreDay(
            plan = optimistic,
            config = settings.scoring,
            context = ScoringContext(categories = categories, streakLength = streak)
        ).score
    }

    private fun upNext(plan: DayPlan, now: LocalTime): PlanEntry? {
        val minutes = now.hour * 60 + now.minute
        return plan.entries
            .filter { it.status.isOpen }
            .filter { entry ->
                val start = entry.timing.startsAt ?: entry.timing.dueAt
                start == null || (start.hour * 60 + start.minute) >= minutes
            }
            .minByOrNull { entry ->
                val start = entry.timing.startsAt ?: entry.timing.dueAt
                start?.let { it.hour * 60 + it.minute } ?: Int.MAX_VALUE
            }
    }

    private fun buildGroups(
        plan: DayPlan,
        settings: AppSettings,
        categories: Map<String, Category>
    ): List<TimelineGroup> {
        val visible = plan.entries.filterNot {
            settings.general.hideCompletedInTimeline && it.status == EntryStatus.Done
        }
        if (visible.isEmpty()) return emptyList()

        return when (settings.general.timelineGrouping) {
            TimelineGrouping.Flat -> listOf(TimelineGroup("all", "", "", visible))

            TimelineGrouping.DayPart -> {
                val order = listOf(
                    DayPart.EarlyMorning, DayPart.Morning, DayPart.Afternoon,
                    DayPart.Evening, DayPart.Night, DayPart.Anytime
                )
                visible.groupBy { settings.general.dayPartOf(it.timing.startsAt ?: it.timing.dueAt) }
                    .toList()
                    .sortedBy { order.indexOf(it.first) }
                    .map { (part, entries) ->
                        TimelineGroup(part.name, "", "", entries, dayPart = part)
                    }
            }

            TimelineGrouping.Category -> visible
                .groupBy { it.categoryId }
                .toList()
                .sortedBy { categories[it.first]?.order ?: Int.MAX_VALUE }
                .map { (categoryId, entries) ->
                    val category = categories[categoryId]
                    TimelineGroup(
                        key = categoryId ?: "none",
                        title = category?.name ?: "",
                        emoji = category?.emoji ?: "•",
                        entries = entries
                    )
                }

            TimelineGrouping.Priority -> visible
                .groupBy { it.priority }
                .toList()
                .sortedByDescending { it.first.sortKey }
                .map { (priority, entries) ->
                    TimelineGroup(priority.name, "", "", entries, priority = priority)
                }

            TimelineGrouping.Status -> visible
                .groupBy { it.status }
                .toList()
                .sortedBy { it.first.ordinal }
                .map { (status, entries) ->
                    TimelineGroup(status.name, "", "", entries, status = status)
                }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}
