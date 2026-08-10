package app.yomi

import app.yomi.feature.goals.GoalsScreen
import app.yomi.feature.insights.InsightsScreen
import app.yomi.feature.planner.PlannerScreen
import app.yomi.feature.planner.PlannerTab
import app.yomi.feature.settings.SettingsScreen
import app.yomi.feature.settings.SettingsSection
import app.yomi.feature.today.TodayScreen
import app.yomi.model.AppLanguage
import app.yomi.model.AppearanceSettings
import app.yomi.model.MotionLevel
import app.yomi.model.ThemeMode
import app.yomi.model.UiDensity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every screen off-screen and asserts that it produced a real image.
 *
 * A composable that throws — a bad index, a missing state, an illegal
 * constraint — fails here instead of in front of the user, and the PNGs are
 * kept so the visual result can be reviewed alongside the diff.
 */
class ScreenshotTest {

    // Animations are pinned off so the captures are byte-stable.
    private val still = AppearanceSettings(motion = MotionLevel.None)

    @Test
    fun `today renders in light and dark`() {
        val light = ScreenshotHarness.render(
            name = "today-light",
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) { TodayScreen(state = SampleData.todayState) }

        val dark = ScreenshotHarness.render(
            name = "today-dark",
            appearance = still.copy(themeMode = ThemeMode.Dark),
            dark = true
        ) { TodayScreen(state = SampleData.todayState) }

        assertRendered(light, dark)
    }

    @Test
    fun `today renders right-to-left in hebrew`() {
        val file = ScreenshotHarness.render(
            name = "today-hebrew-rtl",
            appearance = still.copy(themeMode = ThemeMode.Dark, seedColorArgb = 0xFFFF8FAB),
            language = AppLanguage.Hebrew,
            dark = true
        ) { TodayScreen(state = SampleData.todayState) }

        assertRendered(file)
    }

    @Test
    fun `planner renders its calendar, library and templates`() {
        val calendar = ScreenshotHarness.render(
            name = "planner-calendar",
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) { PlannerScreen(state = SampleData.plannerState) }

        val library = ScreenshotHarness.render(
            name = "planner-library",
            appearance = still.copy(themeMode = ThemeMode.Dark),
            dark = true
        ) { PlannerScreen(state = SampleData.plannerState.copy(tab = PlannerTab.Library)) }

        val templates = ScreenshotHarness.render(
            name = "planner-templates",
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) { PlannerScreen(state = SampleData.plannerState.copy(tab = PlannerTab.Templates)) }

        assertRendered(calendar, library, templates)
    }

    @Test
    fun `goals renders its cards`() {
        val file = ScreenshotHarness.render(
            name = "goals",
            appearance = still.copy(themeMode = ThemeMode.Dark, seedColorArgb = 0xFF4CC9A7),
            dark = true
        ) { GoalsScreen(state = SampleData.goalsState) }

        assertRendered(file)
    }

    @Test
    fun `insights renders every chart`() {
        val file = ScreenshotHarness.render(
            name = "insights",
            height = 1500,
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) { InsightsScreen(state = SampleData.insightsState) }

        assertRendered(file)
    }

    @Test
    fun `settings renders each section`() {
        val appearance = ScreenshotHarness.render(
            name = "settings-appearance",
            height = 1200,
            appearance = still.copy(themeMode = ThemeMode.Dark),
            dark = true
        ) { SettingsScreen(state = SampleData.settingsState) }

        val scoring = ScreenshotHarness.render(
            name = "settings-scoring",
            height = 1600,
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) {
            SettingsScreen(state = SampleData.settingsState.copy(section = SettingsSection.Scoring))
        }

        val notifications = ScreenshotHarness.render(
            name = "settings-notifications",
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) {
            SettingsScreen(
                state = SampleData.settingsState.copy(section = SettingsSection.Notifications)
            )
        }

        assertRendered(appearance, scoring, notifications)
    }

    @Test
    fun `the compact and comfortable densities both lay out`() {
        val compact = ScreenshotHarness.render(
            name = "today-compact",
            width = 600,
            height = 900,
            appearance = still.copy(density = UiDensity.Compact, themeMode = ThemeMode.Light)
        ) { TodayScreen(state = SampleData.todayState) }

        val comfortable = ScreenshotHarness.render(
            name = "today-comfortable",
            appearance = still.copy(density = UiDensity.Comfortable, themeMode = ThemeMode.Dark),
            dark = true
        ) { TodayScreen(state = SampleData.todayState) }

        assertRendered(compact, comfortable)
    }

    @Test
    fun `each palette flavour produces a coherent theme`() {
        val files = listOf(
            app.yomi.model.PaletteFlavor.Expressive,
            app.yomi.model.PaletteFlavor.Vibrant,
            app.yomi.model.PaletteFlavor.TonalSpot,
            app.yomi.model.PaletteFlavor.FruitSalad
        ).map { flavour ->
            ScreenshotHarness.render(
                name = "palette-${flavour.name.lowercase()}",
                height = 620,
                appearance = still.copy(themeMode = ThemeMode.Light, paletteFlavor = flavour)
            ) { TodayScreen(state = SampleData.todayState) }
        }
        assertRendered(*files.toTypedArray())
    }

    @Test
    fun `an empty first-run day renders without falling over`() {
        val empty = SampleData.todayState.copy(
            plan = SampleData.todayState.plan.copy(entries = emptyList()),
            groups = emptyList(),
            upNext = null,
            score = null
        )
        val file = ScreenshotHarness.render(
            name = "today-empty",
            appearance = still.copy(themeMode = ThemeMode.Light)
        ) { TodayScreen(state = empty) }

        assertRendered(file)
    }

    private fun assertRendered(vararg files: java.io.File) {
        files.forEach { file ->
            assertTrue(file.exists(), "${file.name} was not written")
            assertTrue(file.length() > MIN_PNG_BYTES, "${file.name} looks empty (${file.length()} bytes)")
        }
    }

    private companion object {
        const val MIN_PNG_BYTES = 2_000L
    }
}
