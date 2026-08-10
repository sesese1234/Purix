package app.yomi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.components.ColorSwatch
import app.yomi.designsystem.components.ConfirmDialog
import app.yomi.designsystem.components.SectionHeader
import app.yomi.designsystem.components.SettingRow
import app.yomi.designsystem.components.SoftDivider
import app.yomi.designsystem.components.StatTile
import app.yomi.designsystem.components.TimePickerDialog
import app.yomi.designsystem.components.YomiCard
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.model.AppLanguage
import app.yomi.model.MotionLevel
import app.yomi.model.PaletteFlavor
import app.yomi.model.Priority
import app.yomi.model.ShapeStyle
import app.yomi.model.ThemeMode
import app.yomi.model.TimelineGrouping
import app.yomi.model.UiDensity
import app.yomi.model.weekdaysStartingFrom
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

/** Every knob in the app, grouped into five sections. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onSelectSection: (SettingsSection) -> Unit = {},
    viewModelActions: SettingsActions = SettingsActions()
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing
    var resetConfirm by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(horizontal = spacing.screenPadding)) {
        Spacer(Modifier.height(spacing.medium))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            listOf(
                SettingsSection.Appearance to strings.appearance,
                SettingsSection.General to strings.general,
                SettingsSection.Scoring to strings.scoring,
                SettingsSection.Notifications to strings.notifications,
                SettingsSection.Data to strings.data
            ).forEach { (value, label) ->
                YomiChip(
                    label = label,
                    selected = state.section == value,
                    onClick = { onSelectSection(value) }
                )
            }
        }
        Spacer(Modifier.height(spacing.medium))

        LazyColumn(
            contentPadding = PaddingValues(bottom = spacing.xxlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            when (state.section) {
                SettingsSection.Appearance -> item("appearance") { AppearanceSection(state, viewModelActions) }
                SettingsSection.General -> item("general") { GeneralSection(state, viewModelActions) }
                SettingsSection.Scoring -> item("scoring") { ScoringSection(state, viewModelActions) }
                SettingsSection.Notifications -> item("notifications") {
                    NotificationSection(state, viewModelActions)
                }

                SettingsSection.Data -> item("data") {
                    DataSection(state, viewModelActions) { resetConfirm = true }
                }
            }
        }
    }

    if (resetConfirm) {
        ConfirmDialog(
            title = strings.resetEverything,
            message = strings.resetWarning,
            destructive = true,
            onConfirm = viewModelActions.onResetEverything,
            onDismiss = { resetConfirm = false }
        )
    }
}

/** The callbacks the settings screen needs, bundled so the signature stays sane. */
data class SettingsActions(
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onSeedColor: (Long) -> Unit = {},
    val onPalette: (PaletteFlavor) -> Unit = {},
    val onShape: (ShapeStyle) -> Unit = {},
    val onDensity: (UiDensity) -> Unit = {},
    val onMotion: (MotionLevel) -> Unit = {},
    val onFontScale: (Double) -> Unit = {},
    val onAmoled: (Boolean) -> Unit = {},
    val onHighContrast: (Boolean) -> Unit = {},
    val onColorfulCards: (Boolean) -> Unit = {},
    val onShowEmojis: (Boolean) -> Unit = {},
    val onShowScoreRing: (Boolean) -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onFirstDayOfWeek: (DayOfWeek) -> Unit = {},
    val onClock24h: (Boolean) -> Unit = {},
    val onDayStartHour: (Int) -> Unit = {},
    val onGrouping: (TimelineGrouping) -> Unit = {},
    val onHideCompleted: (Boolean) -> Unit = {},
    val onAutoDetectMissed: (Boolean) -> Unit = {},
    val onMissedGrace: (Int) -> Unit = {},
    val onCarryOver: (Boolean) -> Unit = {},
    val onAskSkipReason: (Boolean) -> Unit = {},
    val onWeight: (String, Int) -> Unit = { _, _ -> },
    val onPunctuality: (String, Double) -> Unit = { _, _ -> },
    val onPenalty: (String, Double) -> Unit = { _, _ -> },
    val onBonus: (String, Double) -> Unit = { _, _ -> },
    val onPriorityMultiplier: (Priority, Double) -> Unit = { _, _ -> },
    val onStreakQualifying: (Double) -> Unit = {},
    val onSkipFreeWithReason: (Boolean) -> Unit = {},
    val onWeeklyDropWorst: (Boolean) -> Unit = {},
    val onResetScoring: () -> Unit = {},
    val onNotificationsEnabled: (Boolean) -> Unit = {},
    val onTaskReminders: (Boolean) -> Unit = {},
    val onDefaultLead: (Int) -> Unit = {},
    val onOverdueAlerts: (Boolean) -> Unit = {},
    val onDayStartSummary: (Boolean) -> Unit = {},
    val onDayReview: (Boolean) -> Unit = {},
    val onWeeklyReport: (Boolean) -> Unit = {},
    val onStreakAtRisk: (Boolean) -> Unit = {},
    val onQuietHours: (Boolean) -> Unit = {},
    val onExport: () -> Unit = {},
    val onResetEverything: () -> Unit = {}
)

@Composable
private fun AppearanceSection(state: SettingsUiState, actions: SettingsActions) {
    val strings = LocalStrings.current
    val appearance = state.settings.appearance

    YomiCard {
        SectionHeader(title = strings.appearance)
        Spacer(Modifier.height(YomiTheme.spacing.medium))

        ChipRow(strings.themeMode, listOf(
            ThemeMode.System to strings.themeSystem,
            ThemeMode.Light to strings.themeLight,
            ThemeMode.Dark to strings.themeDark
        ), appearance.themeMode, actions.onThemeMode)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Text(strings.accentColor, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)) {
            SEED_COLORS.forEach { argb ->
                ColorSwatch(
                    color = Color(argb.toInt()),
                    selected = appearance.seedColorArgb == argb,
                    onClick = { actions.onSeedColor(argb) }
                )
            }
        }

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        ChipRow(strings.paletteStyle, PaletteFlavor.entries.map { it to it.name }, appearance.paletteFlavor, actions.onPalette)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        ChipRow(strings.shapeStyle, listOf(
            ShapeStyle.Soft to strings.shapeSoft,
            ShapeStyle.Rounded to strings.shapeRounded,
            ShapeStyle.Pill to strings.shapePill,
            ShapeStyle.Sharp to strings.shapeSharp
        ), appearance.shapeStyle, actions.onShape)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        ChipRow(strings.density, listOf(
            UiDensity.Compact to strings.densityCompact,
            UiDensity.Cozy to strings.densityCozy,
            UiDensity.Comfortable to strings.densityComfortable
        ), appearance.density, actions.onDensity)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        ChipRow(strings.motion, listOf(
            MotionLevel.None to strings.motionNone,
            MotionLevel.Subtle to strings.motionSubtle,
            MotionLevel.Playful to strings.motionPlayful
        ), appearance.motion, actions.onMotion)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        SliderRow(
            label = "${strings.fontScale}: ${(appearance.fontScale * 100).roundToInt()}%",
            value = appearance.fontScale.toFloat(),
            range = MIN_FONT_SCALE..MAX_FONT_SCALE,
            onChange = { actions.onFontScale(it.toDouble()) }
        )

        SoftDivider()
        SwitchRow(strings.amoled, appearance.amoledDark, actions.onAmoled)
        SwitchRow(strings.highContrast, appearance.highContrast, actions.onHighContrast)
        SwitchRow(strings.colorfulCards, appearance.colorfulCards, actions.onColorfulCards)
        SwitchRow(strings.showEmojis, appearance.showEmojis, actions.onShowEmojis)
        SwitchRow(strings.showScoreRing, appearance.showScoreRing, actions.onShowScoreRing)
    }
}

@Composable
private fun GeneralSection(state: SettingsUiState, actions: SettingsActions) {
    val strings = LocalStrings.current
    val general = state.settings.general

    YomiCard {
        SectionHeader(title = strings.general)
        Spacer(Modifier.height(YomiTheme.spacing.medium))

        ChipRow(strings.language, listOf(
            AppLanguage.System to strings.languageSystem,
            AppLanguage.English to strings.languageEnglish,
            AppLanguage.Hebrew to strings.languageHebrew
        ), general.language, actions.onLanguage)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Text(strings.firstDayOfWeek, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.tiny)) {
            weekdaysStartingFrom(DayOfWeek.SUNDAY).forEach { day ->
                YomiChip(
                    label = Fmt.weekdayShort(day, strings),
                    selected = general.firstDayOfWeek == day,
                    onClick = { actions.onFirstDayOfWeek(day) }
                )
            }
        }

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        ChipRow(strings.timelineGrouping, listOf(
            TimelineGrouping.DayPart to strings.groupDayPart,
            TimelineGrouping.Category to strings.groupCategory,
            TimelineGrouping.Priority to strings.groupPriority,
            TimelineGrouping.Status to strings.groupStatus,
            TimelineGrouping.Flat to strings.groupFlat
        ), general.timelineGrouping, actions.onGrouping)

        Spacer(Modifier.height(YomiTheme.spacing.medium))
        SliderRow(
            label = "${strings.dayStartHour} ${general.dayStartHour}:00",
            value = general.dayStartHour.toFloat(),
            range = 0f..MAX_DAY_START_HOUR,
            steps = MAX_DAY_START_HOUR.toInt() - 1,
            onChange = { actions.onDayStartHour(it.roundToInt()) }
        )
        SliderRow(
            label = "${strings.missedGrace}: ${general.missedGraceMinutes}′",
            value = general.missedGraceMinutes.toFloat(),
            range = 0f..MAX_GRACE,
            onChange = { actions.onMissedGrace(it.roundToInt()) }
        )

        SoftDivider()
        SwitchRow(strings.clock24h, general.use24HourClock, actions.onClock24h)
        SwitchRow(strings.hideCompleted, general.hideCompletedInTimeline, actions.onHideCompleted)
        SwitchRow(strings.autoDetectMissed, general.autoDetectMissed, actions.onAutoDetectMissed)
        SwitchRow(strings.carryOver, general.carryOverUnfinished, actions.onCarryOver)
        SwitchRow(strings.askSkipReason, general.askForSkipReason, actions.onAskSkipReason)
    }
}

@Composable
private fun ScoringSection(state: SettingsUiState, actions: SettingsActions) {
    val strings = LocalStrings.current
    val scoring = state.settings.scoring
    val weights = scoring.weights

    YomiCard {
        SectionHeader(title = strings.livePreview, subtitle = strings.previewNote)
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        val preview = state.previewScore
        Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)) {
            StatTile(
                value = preview?.let { Fmt.score(it.score) } ?: "—",
                label = strings.todayScore,
                modifier = Modifier.weight(1f),
                emoji = preview?.grade?.emoji
            )
            StatTile(
                value = preview?.let { Fmt.score(it.baseScore) } ?: "—",
                label = strings.baseScore,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = preview?.grade?.label ?: "—",
                label = strings.scoringGrades,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(YomiTheme.spacing.medium))

    YomiCard {
        SectionHeader(title = strings.scoringWeights)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        SliderRow("${strings.componentCompletion}: ${weights.completion}", weights.completion.toFloat(), 0f..MAX_WEIGHT) {
            actions.onWeight("completion", it.roundToInt())
        }
        SliderRow("${strings.componentPunctuality}: ${weights.punctuality}", weights.punctuality.toFloat(), 0f..MAX_WEIGHT) {
            actions.onWeight("punctuality", it.roundToInt())
        }
        SliderRow("${strings.componentSubtasks}: ${weights.subtasks}", weights.subtasks.toFloat(), 0f..MAX_WEIGHT) {
            actions.onWeight("subtasks", it.roundToInt())
        }
        SliderRow("${strings.componentRoutine}: ${weights.routine}", weights.routine.toFloat(), 0f..MAX_WEIGHT) {
            actions.onWeight("routine", it.roundToInt())
        }
        SliderRow("${strings.componentConsistency}: ${weights.consistency}", weights.consistency.toFloat(), 0f..MAX_WEIGHT) {
            actions.onWeight("consistency", it.roundToInt())
        }
    }

    Spacer(Modifier.height(YomiTheme.spacing.medium))

    YomiCard {
        SectionHeader(title = strings.scoringPunctuality)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        SliderRow("${strings.graceMinutes}: ${scoring.punctuality.graceMinutes}", scoring.punctuality.graceMinutes.toFloat(), 0f..MAX_GRACE) {
            actions.onPunctuality("grace", it.toDouble())
        }
        SliderRow("${strings.penaltyPerMinute}: ${Fmt.score(scoring.punctuality.penaltyPerLateMinute)}", scoring.punctuality.penaltyPerLateMinute.toFloat(), 0f..MAX_PER_MINUTE) {
            actions.onPunctuality("perMinute", it.toDouble())
        }
        SliderRow("${strings.untimedCredit}: ${Fmt.percent(scoring.punctuality.untimedCredit)}", scoring.punctuality.untimedCredit.toFloat(), 0f..1f) {
            actions.onPunctuality("untimed", it.toDouble())
        }
    }

    Spacer(Modifier.height(YomiTheme.spacing.medium))

    YomiCard {
        SectionHeader(title = strings.scoringPenalties)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        SliderRow("${strings.missedPenalty}: ${Fmt.score(scoring.penalties.missedPenalty)}", scoring.penalties.missedPenalty.toFloat(), 0f..MAX_PENALTY) {
            actions.onPenalty("missed", it.toDouble())
        }
        SliderRow("${strings.skippedPenalty}: ${Fmt.score(scoring.penalties.skippedPenalty)}", scoring.penalties.skippedPenalty.toFloat(), 0f..MAX_PENALTY) {
            actions.onPenalty("skipped", it.toDouble())
        }
        SliderRow("${strings.maxTotalPenalty}: ${Fmt.score(scoring.penalties.maxTotalPenalty)}", scoring.penalties.maxTotalPenalty.toFloat(), 0f..MAX_TOTAL_PENALTY) {
            actions.onPenalty("max", it.toDouble())
        }
        SwitchRow(strings.skipFreeWithReason, scoring.penalties.skipIsFreeWithReason, actions.onSkipFreeWithReason)
    }

    Spacer(Modifier.height(YomiTheme.spacing.medium))

    YomiCard {
        SectionHeader(title = strings.scoringBonuses)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        SliderRow("${strings.perfectDayBonus}: ${Fmt.score(scoring.bonuses.perfectDayBonus)}", scoring.bonuses.perfectDayBonus.toFloat(), 0f..MAX_BONUS) {
            actions.onBonus("perfect", it.toDouble())
        }
        SliderRow("${strings.streakBonusPerDay}: ${Fmt.score(scoring.bonuses.streakBonusPerDay)}", scoring.bonuses.streakBonusPerDay.toFloat(), 0f..MAX_BONUS) {
            actions.onBonus("streakPerDay", it.toDouble())
        }
        SliderRow("${strings.streakBonusCap}: ${Fmt.score(scoring.bonuses.streakBonusCap)}", scoring.bonuses.streakBonusCap.toFloat(), 0f..MAX_BONUS_CAP) {
            actions.onBonus("streakCap", it.toDouble())
        }
        SliderRow("${strings.earlyRiserBonus}: ${Fmt.score(scoring.bonuses.earlyRiserBonus)}", scoring.bonuses.earlyRiserBonus.toFloat(), 0f..MAX_BONUS) {
            actions.onBonus("earlyRiser", it.toDouble())
        }
        SliderRow("${strings.streakQualifying}: ${Fmt.score(scoring.streakQualifyingScore)}", scoring.streakQualifyingScore.toFloat(), 0f..MAX_SCORE) {
            actions.onStreakQualifying(it.toDouble())
        }
        SwitchRow(strings.weeklyDropWorst, scoring.weeklyDropWorstDay, actions.onWeeklyDropWorst)
    }

    Spacer(Modifier.height(YomiTheme.spacing.medium))

    YomiCard {
        SectionHeader(title = strings.priorityWeights)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        Priority.entries.forEach { priority ->
            val value = scoring.multiplierFor(priority)
            SliderRow("${Fmt.priority(priority, strings)}: ×${Fmt.score(value)}", value.toFloat(), 0f..MAX_MULTIPLIER) {
                actions.onPriorityMultiplier(priority, it.toDouble())
            }
        }
        Spacer(Modifier.height(YomiTheme.spacing.small))
        OutlinedButton(onClick = actions.onResetScoring) { Text(strings.reset) }
    }
}

@Composable
private fun NotificationSection(state: SettingsUiState, actions: SettingsActions) {
    val strings = LocalStrings.current
    val notifications = state.settings.notifications

    YomiCard {
        SectionHeader(title = strings.notifications)
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        SwitchRow(strings.enableNotifications, notifications.enabled, actions.onNotificationsEnabled)
        SoftDivider()
        SwitchRow(strings.settingsTaskReminders, notifications.taskReminders, actions.onTaskReminders)
        SliderRow(
            label = "${strings.defaultLead}: ${notifications.defaultLeadMinutes}′",
            value = notifications.defaultLeadMinutes.toFloat(),
            range = 0f..MAX_LEAD,
            onChange = { actions.onDefaultLead(it.roundToInt()) }
        )
        SwitchRow(strings.overdueAlerts, notifications.overdueAlerts, actions.onOverdueAlerts)
        SwitchRow(strings.dayStartSummary, notifications.dayStartSummary, actions.onDayStartSummary)
        SwitchRow(strings.dayReviewReminder, notifications.dayReview, actions.onDayReview)
        SwitchRow(strings.weeklyReport, notifications.weeklyReport, actions.onWeeklyReport)
        SwitchRow(strings.streakAtRisk, notifications.streakAtRisk, actions.onStreakAtRisk)
        SwitchRow(strings.quietHours, notifications.quietHoursEnabled, actions.onQuietHours)
        Text(
            text = "${strings.quietFrom} ${Fmt.time(notifications.quietStart, state.settings.general.use24HourClock)} " +
                "${strings.quietTo} ${Fmt.time(notifications.quietEnd, state.settings.general.use24HourClock)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DataSection(
    state: SettingsUiState,
    actions: SettingsActions,
    onRequestReset: () -> Unit
) {
    val strings = LocalStrings.current

    YomiCard {
        SectionHeader(title = strings.data)
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)) {
            StatTile(state.taskCount.toString(), strings.library, Modifier.weight(1f))
            StatTile(state.goalCount.toString(), strings.goals, Modifier.weight(1f))
            StatTile(state.dayCount.toString(), strings.daysTracked, Modifier.weight(1f))
        }
        Spacer(Modifier.height(YomiTheme.spacing.medium))
        Button(onClick = actions.onExport, modifier = Modifier.fillMaxWidth()) {
            Text(strings.exportBackup)
        }
        Spacer(Modifier.height(YomiTheme.spacing.small))
        OutlinedButton(onClick = onRequestReset, modifier = Modifier.fillMaxWidth()) {
            Text(strings.resetEverything, color = MaterialTheme.colorScheme.error)
        }
        state.message?.let { key ->
            Spacer(Modifier.height(YomiTheme.spacing.small))
            Text(
                text = when (key) {
                    SettingsViewModel.IMPORT_OK -> strings.imported
                    SettingsViewModel.IMPORT_FAILED -> strings.importFailed
                    else -> strings.exported
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(Modifier.height(YomiTheme.spacing.medium))

    YomiCard {
        SectionHeader(title = strings.about)
        Spacer(Modifier.height(YomiTheme.spacing.small))
        Text("${strings.appName} · ${strings.version} 1.0.0", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = strings.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun <T> ChipRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(YomiTheme.spacing.small))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.tiny)) {
        options.forEach { (value, label) ->
            YomiChip(label = label, selected = selected == value, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = label,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) }
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = YomiTheme.spacing.tiny)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
    }
}

private val SEED_COLORS = listOf(
    0xFF7C6BF2, 0xFF4CC9A7, 0xFFFF8FAB, 0xFFFFC857, 0xFF8ECAE6,
    0xFFB388FF, 0xFFFF7043, 0xFF26A69A, 0xFF9CCC65, 0xFFEC407A
)

private const val MIN_FONT_SCALE = 0.85f
private const val MAX_FONT_SCALE = 1.35f
private const val MAX_WEIGHT = 100f
private const val MAX_GRACE = 120f
private const val MAX_PER_MINUTE = 5f
private const val MAX_PENALTY = 20f
private const val MAX_TOTAL_PENALTY = 100f
private const val MAX_BONUS = 20f
private const val MAX_BONUS_CAP = 30f
private const val MAX_SCORE = 100f
private const val MAX_MULTIPLIER = 5f
private const val MAX_LEAD = 120f
private const val MAX_DAY_START_HOUR = 12f
