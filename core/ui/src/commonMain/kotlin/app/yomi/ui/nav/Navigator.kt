package app.yomi.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.yomi.designsystem.i18n.Strings
import app.yomi.designsystem.icons.YomiIcons
import kotlinx.datetime.LocalDate

/** The five places you can be in Yomi. */
enum class TopLevelTab {
    Today, Planner, Goals, Insights, Settings;

    fun label(strings: Strings): String = when (this) {
        Today -> strings.navToday
        Planner -> strings.navPlanner
        Goals -> strings.navGoals
        Insights -> strings.navInsights
        Settings -> strings.navSettings
    }

    val icon: ImageVector
        get() = when (this) {
            Today -> YomiIcons.Today
            Planner -> YomiIcons.Planner
            Goals -> YomiIcons.Goals
            Insights -> YomiIcons.Insights
            Settings -> YomiIcons.Settings
        }
}

/** A screen pushed on top of a tab. */
sealed interface Detail {
    data class Day(val date: LocalDate) : Detail
    data object TaskLibrary : Detail
    data object Categories : Detail
    data object Templates : Detail
    data class TaskEditor(val taskId: String?) : Detail
    data class GoalEditor(val goalId: String?) : Detail
    data object ScoringRules : Detail
}

/**
 * Navigation without a framework.
 *
 * Yomi has five tabs and a shallow stack of details on top of them, so a small
 * observable back stack is both sufficient and far easier to reason about — and
 * to test — than a route-matching graph.
 */
@Stable
class Navigator(initialTab: TopLevelTab = TopLevelTab.Today) {

    var tab: TopLevelTab by mutableStateOf(initialTab)
        private set

    private val stack = mutableStateListOf<Detail>()

    val current: Detail? get() = stack.lastOrNull()

    val canGoBack: Boolean get() = stack.isNotEmpty()

    /** Selecting the tab you are already on pops back to its root. */
    fun selectTab(target: TopLevelTab) {
        if (tab == target) {
            stack.clear()
        } else {
            tab = target
            stack.clear()
        }
    }

    fun push(detail: Detail) {
        if (stack.lastOrNull() == detail) return
        stack.add(detail)
    }

    /** Replaces the top of the stack, for detail-to-detail moves. */
    fun replace(detail: Detail) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(detail)
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popToRoot() = stack.clear()

    /** Deep link used by the planner and the notifications: open a specific day. */
    fun openDay(date: LocalDate) {
        tab = TopLevelTab.Planner
        stack.clear()
        stack.add(Detail.Day(date))
    }
}
