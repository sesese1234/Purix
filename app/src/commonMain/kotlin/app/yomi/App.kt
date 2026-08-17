package app.yomi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.icons.YomiIcons
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.feature.goals.GoalsScreen
import app.yomi.feature.goals.GoalsViewModel
import app.yomi.feature.insights.InsightsScreen
import app.yomi.feature.insights.InsightsViewModel
import app.yomi.feature.planner.PlannerScreen
import app.yomi.feature.planner.PlannerViewModel
import app.yomi.feature.planner.TaskEditorDialog
import app.yomi.feature.settings.SettingsScreen
import app.yomi.feature.settings.SettingsViewModel
import app.yomi.feature.today.TodayScreen
import app.yomi.feature.today.TodayViewModel
import app.yomi.model.AppLanguage
import app.yomi.model.TaskDefinition
import app.yomi.ui.nav.Navigator
import app.yomi.ui.nav.TopLevelTab
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * The application shell: theme, navigation and the five screens.
 *
 * Layout adapts to the window — a rail on wide displays, a bottom bar on narrow
 * ones — and the whole tree re-themes instantly when a preference changes,
 * including flipping to right-to-left for Hebrew.
 */
@Composable
fun YomiApp(
    windowWidth: Dp,
    systemIsHebrew: Boolean = false,
    onExport: (String) -> Unit = {}
) {
    val todayViewModel: TodayViewModel = koinViewModel()
    val plannerViewModel: PlannerViewModel = koinViewModel()
    val goalsViewModel: GoalsViewModel = koinViewModel()
    val insightsViewModel: InsightsViewModel = koinViewModel()
    val settingsViewModel: SettingsViewModel = koinViewModel()

    val settings by settingsViewModel.settings.collectAsState()
    val navigator = remember { Navigator() }

    YomiTheme(
        appearance = settings.appearance,
        language = settings.general.language,
        systemIsHebrew = systemIsHebrew
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val wide = windowWidth >= WIDE_BREAKPOINT
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    YomiNavigationRail(navigator)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f)) {
                        TabContent(
                            navigator = navigator,
                            todayViewModel = todayViewModel,
                            plannerViewModel = plannerViewModel,
                            goalsViewModel = goalsViewModel,
                            insightsViewModel = insightsViewModel,
                            settingsViewModel = settingsViewModel,
                            onExport = onExport
                        )
                    }
                    if (!wide) {
                        YomiNavigationBar(navigator)
                    }
                }
            }
        }
    }

    // A light heartbeat keeps "now" honest: overdue tasks flip to missed and the
    // greeting changes without the user having to touch anything.
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            todayViewModel.refresh()
        }
    }
}

@Composable
private fun TabContent(
    navigator: Navigator,
    todayViewModel: TodayViewModel,
    plannerViewModel: PlannerViewModel,
    goalsViewModel: GoalsViewModel,
    insightsViewModel: InsightsViewModel,
    settingsViewModel: SettingsViewModel,
    onExport: (String) -> Unit
) {
    var editingTask by remember { mutableStateOf<TaskDefinition?>(null) }
    var taskEditorOpen by remember { mutableStateOf(false) }

    when (navigator.tab) {
        TopLevelTab.Today -> {
            val state by todayViewModel.state.collectAsState()
            TodayScreen(
                state = state,
                onToggleDone = todayViewModel::toggleDone,
                onToggleSubtask = todayViewModel::toggleSubtask,
                onSkip = todayViewModel::skip,
                onDefer = todayViewModel::defer,
                onDelete = todayViewModel::deleteEntry,
                onStart = todayViewModel::start,
                onQuickAdd = { todayViewModel.addQuickTask(it) },
                onShiftDay = todayViewModel::shiftDay,
                onGoToToday = todayViewModel::goToToday,
                onSetWake = todayViewModel::setWakeTime,
                onSetSleep = todayViewModel::setSleepTime,
                onSetMood = todayViewModel::setMood,
                onSetEnergy = todayViewModel::setEnergy,
                onFinishDay = todayViewModel::finalizeDay,
                onReopenDay = todayViewModel::reopenDay
            )
        }

        TopLevelTab.Planner -> {
            val state by plannerViewModel.state.collectAsState()
            PlannerScreen(
                state = state,
                onSelectTab = plannerViewModel::selectTab,
                onShiftMonth = plannerViewModel::shiftMonth,
                onSelectDate = plannerViewModel::selectDate,
                onGoToToday = plannerViewModel::goToToday,
                onCopy = { target, options ->
                    when (target) {
                        is app.yomi.feature.planner.CopyTarget.NextDays ->
                            plannerViewModel.copyToNextDays(target.count, options)

                        is app.yomi.feature.planner.CopyTarget.Weekdays ->
                            plannerViewModel.copyToWeekdays(target.days, target.weeksAhead, options)

                        is app.yomi.feature.planner.CopyTarget.Tomorrow ->
                            plannerViewModel.copyToDates(listOf(target.date), options)
                    }
                },
                onSaveTemplate = { plannerViewModel.saveAsTemplate(it) },
                onApplyTemplate = {
                    plannerViewModel.applyTemplate(it, app.yomi.domain.plan.CopyOptions())
                },
                onDeleteTemplate = plannerViewModel::deleteTemplate,
                onEditTask = { task ->
                    editingTask = task
                    taskEditorOpen = true
                },
                onDeleteTask = plannerViewModel::deleteTask,
                onToggleArchive = plannerViewModel::setTaskArchived,
                onToggleDone = plannerViewModel::toggleDone
            )

            if (taskEditorOpen) {
                TaskEditorDialog(
                    original = editingTask,
                    categories = state.catalog.activeCategories,
                    firstDayOfWeek = state.settings.general.firstDayOfWeek,
                    use24h = state.settings.general.use24HourClock,
                    today = state.selectedDate,
                    newId = plannerViewModel::newId,
                    onSave = plannerViewModel::upsertTask,
                    onDismiss = {
                        taskEditorOpen = false
                        editingTask = null
                    }
                )
            }
        }

        TopLevelTab.Goals -> {
            val state by goalsViewModel.state.collectAsState()
            GoalsScreen(
                state = state,
                onSave = goalsViewModel::upsert,
                onDelete = goalsViewModel::delete,
                onArchive = goalsViewModel::setArchived,
                newId = goalsViewModel::newId
            )
        }

        TopLevelTab.Insights -> {
            val state by insightsViewModel.state.collectAsState()
            InsightsScreen(state = state, onSelectRange = insightsViewModel::selectRange)
        }

        TopLevelTab.Settings -> {
            val state by settingsViewModel.state.collectAsState()
            SettingsScreen(
                state = state,
                onSelectSection = settingsViewModel::selectSection,
                viewModelActions = settingsActions(settingsViewModel, onExport)
            )
        }
    }
}

/** The bottom bar used on phone-width layouts. Public so it can be rendered in isolation. */
@Composable
fun YomiNavigationBar(navigator: Navigator) {
    val strings = LocalStrings.current
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = navigator.tab == tab,
                onClick = { navigator.selectTab(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label(strings)) },
                label = { Text(tab.label(strings)) },
                alwaysShowLabel = true
            )
        }
    }
}

/** The rail used on wide layouts. Public so it can be rendered in isolation. */
@Composable
fun YomiNavigationRail(navigator: Navigator) {
    val strings = LocalStrings.current
    NavigationRail(
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = YomiIcons.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = strings.appName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Spacer(Modifier.height(8.dp))
        TopLevelTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = navigator.tab == tab,
                onClick = { navigator.selectTab(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label(strings)) },
                label = { Text(tab.label(strings)) }
            )
        }
    }
}

private const val TICK_MILLIS = 60_000L
private val WIDE_BREAKPOINT = 840.dp
