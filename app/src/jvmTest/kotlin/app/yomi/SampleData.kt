package app.yomi

import app.yomi.designsystem.components.HeatCell
import app.yomi.domain.goals.GoalEngine
import app.yomi.domain.insights.StatisticsEngine
import app.yomi.domain.plan.DayMaterializer
import app.yomi.domain.scoring.ScoreBook
import app.yomi.domain.scoring.WeekMath
import app.yomi.domain.scoring.WeekScoringEngine
import app.yomi.domain.seed.DefaultContent
import app.yomi.domain.util.SequentialIdGenerator
import app.yomi.feature.goals.GoalRow
import app.yomi.feature.goals.GoalsUiState
import app.yomi.feature.insights.InsightRange
import app.yomi.feature.insights.InsightsUiState
import app.yomi.feature.planner.CalendarCell
import app.yomi.feature.planner.PlannerTab
import app.yomi.feature.planner.PlannerUiState
import app.yomi.feature.settings.SettingsSection
import app.yomi.feature.settings.SettingsUiState
import app.yomi.feature.today.GoalCardState
import app.yomi.feature.today.TimelineGroup
import app.yomi.feature.today.TodayUiState
import app.yomi.model.AppLanguage
import app.yomi.model.AppSettings
import app.yomi.model.Catalog
import app.yomi.model.DayCheckIn
import app.yomi.model.DayPart
import app.yomi.model.DayPlan
import app.yomi.model.EntryStatus
import app.yomi.model.PlanEntry
import app.yomi.model.SubtaskState
import app.yomi.designsystem.format.Fmt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.random.Random

/**
 * A believable month of history, generated deterministically.
 *
 * The screenshots are only useful if they show the app in the state a real user
 * would recognise: partly finished days, a couple of misses, goals mid-flight.
 */
object SampleData {

    val today: LocalDate = LocalDate(2026, 3, 11)
    private val ids = SequentialIdGenerator("s")
    private val random = Random(7)

    val settings = AppSettings()

    val catalog: Catalog = DefaultContent.catalog(today.minus(60, DateTimeUnit.DAY), AppLanguage.English)

    /** Sixty days of journal, ending with a realistic, half-finished today. */
    val days: Map<LocalDate, DayPlan> = buildMap {
        val materializer = DayMaterializer(ids)
        for (offset in HISTORY_DAYS downTo 0) {
            val date = today.minus(offset, DateTimeUnit.DAY)
            var plan = materializer.materialize(date, catalog, settings.general)
            plan = if (offset == 0) decorateToday(plan) else decoratePast(plan)
            put(date, plan)
        }
    }

    val scores = ScoreBook().computeAll(
        days = days.values,
        config = settings.scoring,
        categories = catalog.categories,
        goals = catalog.goals
    )

    private fun decoratePast(plan: DayPlan): DayPlan {
        val quality = random.nextDouble()
        val entries = plan.entries.map { entry ->
            val roll = random.nextDouble()
            when {
                roll < quality * DONE_BIAS -> entry.completed(entry.timing.dueAt ?: LocalTime(12, 0))
                roll < quality * DONE_BIAS + PARTIAL_BAND -> entry.copy(
                    status = EntryStatus.Partial,
                    subtasks = entry.subtasks.mapIndexed { index, sub -> sub.copy(done = index == 0) }
                )

                roll < quality * DONE_BIAS + PARTIAL_BAND + SKIP_BAND ->
                    entry.copy(status = EntryStatus.Skipped, skipReason = "busy")

                else -> entry.copy(status = EntryStatus.Missed)
            }
        }
        val wakeDrift = random.nextInt(-15, 45)
        return plan.copy(
            entries = entries,
            checkIn = DayCheckIn(
                wakeTarget = LocalTime(7, 0),
                wakeActual = LocalTime(7, 0).shifted(wakeDrift),
                sleepTarget = LocalTime(23, 30),
                sleepActual = LocalTime(23, 30).shifted(random.nextInt(-20, 60)),
                mood = random.nextInt(2, 6),
                energy = random.nextInt(2, 6)
            ),
            finalized = true
        )
    }

    private fun decorateToday(plan: DayPlan): DayPlan {
        val entries = plan.entries.mapIndexed { index, entry ->
            when (index) {
                0 -> entry.completed(LocalTime(7, 18))
                1 -> entry.completed(LocalTime(8, 40))
                2 -> entry.copy(
                    status = EntryStatus.InProgress,
                    startedAt = LocalTime(9, 5),
                    subtasks = entry.subtasks.mapIndexed { i, sub -> sub.copy(done = i == 0) }
                )

                3 -> entry.copy(actualQuantity = QUANTITY_SO_FAR)
                else -> entry
            }
        }
        return plan.copy(
            entries = entries,
            checkIn = DayCheckIn(
                wakeTarget = LocalTime(7, 0),
                wakeActual = LocalTime(6, 52),
                sleepTarget = LocalTime(23, 30),
                mood = 4,
                energy = 4
            ),
            note = "Deep work block is the one that matters today."
        )
    }

    private fun PlanEntry.completed(at: LocalTime) = copy(
        status = EntryStatus.Done,
        completedAt = at,
        subtasks = subtasks.map { it.copy(done = true) }
    )

    private fun LocalTime.shifted(minutes: Int): LocalTime {
        val total = (hour * 60 + minute + minutes).coerceIn(0, 24 * 60 - 1)
        return LocalTime(total / 60, total % 60)
    }

    // ------------------------------------------------------------ ui states

    val todayState: TodayUiState by lazy {
        val plan = days.getValue(today)
        val categories = catalog.categories.associateBy { it.id }
        val goalEngine = GoalEngine()
        TodayUiState(
            date = today,
            isToday = true,
            plan = plan,
            score = scores[today],
            potentialScore = POTENTIAL_SCORE,
            streak = 4,
            settings = settings,
            categories = categories,
            goals = catalog.activeGoals.map { goal ->
                GoalCardState(
                    goal = goal,
                    progress = goalEngine.progress(
                        goal = goal,
                        days = days.values.toList(),
                        scores = scores,
                        today = today,
                        firstDayOfWeek = settings.general.firstDayOfWeek
                    ),
                    todayContribution = 0.0
                )
            },
            groups = groupByDayPart(plan),
            upNext = plan.entries.firstOrNull { it.status == EntryStatus.Pending },
            now = LocalTime(10, 25)
        )
    }

    private fun groupByDayPart(plan: DayPlan): List<TimelineGroup> {
        val order = listOf(
            DayPart.EarlyMorning, DayPart.Morning, DayPart.Afternoon,
            DayPart.Evening, DayPart.Night, DayPart.Anytime
        )
        return plan.entries
            .groupBy { settings.general.dayPartOf(it.timing.startsAt ?: it.timing.dueAt) }
            .toList()
            .sortedBy { order.indexOf(it.first) }
            .map { (part, entries) -> TimelineGroup(part.name, "", "", entries, dayPart = part) }
    }

    val plannerState: PlannerUiState by lazy {
        val grid = WeekMath.monthGrid(today, settings.general.firstDayOfWeek)
        PlannerUiState(
            tab = PlannerTab.Calendar,
            month = today,
            selectedDate = today,
            cells = grid.map { date ->
                val plan = days[date]
                CalendarCell(
                    date = date,
                    inMonth = date.month == today.month,
                    isToday = date == today,
                    isSelected = date == today,
                    score = scores[date]?.takeIf { (plan?.entries?.size ?: 0) > 0 }?.score,
                    plannedCount = plan?.entries?.size ?: 0,
                    doneCount = plan?.entries?.count { it.status == EntryStatus.Done } ?: 0,
                    finalized = plan?.finalized == true
                )
            },
            weekdayHeaders = app.yomi.model.weekdaysStartingFrom(settings.general.firstDayOfWeek),
            selectedPlan = days.getValue(today),
            selectedScore = scores[today],
            monthAverage = scores.values.map { it.score }.average(),
            monthPerfectDays = scores.values.count { it.isPerfectDay },
            settings = settings,
            catalog = catalog,
            categories = catalog.categories.associateBy { it.id }
        )
    }

    val goalsState: GoalsUiState by lazy {
        val engine = GoalEngine()
        GoalsUiState(
            goals = catalog.activeGoals.map { goal ->
                GoalRow(
                    goal = goal,
                    progress = engine.progress(
                        goal = goal,
                        days = days.values.toList(),
                        scores = scores,
                        today = today,
                        firstDayOfWeek = settings.general.firstDayOfWeek
                    )
                )
            },
            archived = emptyList(),
            tasks = catalog.activeTasks,
            categories = catalog.activeCategories,
            today = today
        )
    }

    val insightsState: InsightsUiState by lazy {
        val statistics = StatisticsEngine()
        val heatStart = WeekMath.startOfWeek(
            today.minus(HEATMAP_DAYS, DateTimeUnit.DAY),
            settings.general.firstDayOfWeek
        )
        val heatDates = buildList {
            var cursor = heatStart
            while (cursor <= today) {
                add(cursor)
                cursor = cursor.plus(1, DateTimeUnit.DAY)
            }
        }
        InsightsUiState(
            range = InsightRange.Month,
            report = statistics.report(
                days = days.values.toList(),
                scores = scores,
                categories = catalog.categories,
                settings = settings.general,
                rangeStart = today.minus(29, DateTimeUnit.DAY),
                rangeEnd = today
            ),
            weekScore = WeekScoringEngine().scoreWeek(
                WeekMath.startOfWeek(today, settings.general.firstDayOfWeek),
                WeekMath.endOfWeek(today, settings.general.firstDayOfWeek),
                scores.values.toList(),
                settings.scoring
            ),
            heatmap = statistics.heatmap(heatDates, days, scores).map {
                HeatCell(Fmt.shortDate(it.date), it.score, Fmt.isoDate(it.date))
            },
            heatmapColumns = 7,
            settings = settings
        )
    }

    val settingsState: SettingsUiState by lazy {
        SettingsUiState(
            section = SettingsSection.Appearance,
            settings = settings,
            previewScore = scores[today],
            taskCount = catalog.tasks.size,
            goalCount = catalog.goals.size,
            dayCount = days.count { it.value.entries.isNotEmpty() }
        )
    }

    private const val HISTORY_DAYS = 60
    private const val HEATMAP_DAYS = 90
    private const val DONE_BIAS = 1.05
    private const val PARTIAL_BAND = 0.10
    private const val SKIP_BAND = 0.06
    private const val QUANTITY_SO_FAR = 12.0
    private const val POTENTIAL_SCORE = 97.5
}

private fun SubtaskState.touched(): Boolean = done
