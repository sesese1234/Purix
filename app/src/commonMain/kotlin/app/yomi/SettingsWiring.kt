package app.yomi

import app.yomi.designsystem.i18n.Strings
import app.yomi.designsystem.i18n.format
import app.yomi.feature.settings.SettingsActions
import app.yomi.feature.settings.SettingsViewModel
import app.yomi.model.NotificationKind
import app.yomi.notification.NotificationCopy
import app.yomi.notification.NotificationPayload

/**
 * Connects the settings screen's callback bundle to the view model.
 *
 * Keeping the mapping here means the settings UI stays a pure function of state
 * plus lambdas, which is what makes it renderable in isolation.
 */
fun settingsActions(
    viewModel: SettingsViewModel,
    onExport: (String) -> Unit
): SettingsActions = SettingsActions(
    onThemeMode = { mode -> viewModel.updateAppearance { it.copy(themeMode = mode) } },
    onSeedColor = { argb -> viewModel.updateAppearance { it.copy(seedColorArgb = argb) } },
    onPalette = { flavor -> viewModel.updateAppearance { it.copy(paletteFlavor = flavor) } },
    onShape = { style -> viewModel.updateAppearance { it.copy(shapeStyle = style) } },
    onDensity = { density -> viewModel.updateAppearance { it.copy(density = density) } },
    onMotion = { motion -> viewModel.updateAppearance { it.copy(motion = motion) } },
    onFontScale = { scale -> viewModel.updateAppearance { it.copy(fontScale = scale) } },
    onAmoled = { value -> viewModel.updateAppearance { it.copy(amoledDark = value) } },
    onHighContrast = { value -> viewModel.updateAppearance { it.copy(highContrast = value) } },
    onColorfulCards = { value -> viewModel.updateAppearance { it.copy(colorfulCards = value) } },
    onShowEmojis = { value -> viewModel.updateAppearance { it.copy(showEmojis = value) } },
    onShowScoreRing = { value -> viewModel.updateAppearance { it.copy(showScoreRing = value) } },

    onLanguage = { language -> viewModel.updateGeneral { it.copy(language = language) } },
    onFirstDayOfWeek = { day -> viewModel.updateGeneral { it.copy(firstDayOfWeek = day) } },
    onClock24h = { value -> viewModel.updateGeneral { it.copy(use24HourClock = value) } },
    onDayStartHour = { hour -> viewModel.updateGeneral { it.copy(dayStartHour = hour.coerceIn(0, 12)) } },
    onGrouping = { grouping -> viewModel.updateGeneral { it.copy(timelineGrouping = grouping) } },
    onHideCompleted = { value -> viewModel.updateGeneral { it.copy(hideCompletedInTimeline = value) } },
    onAutoDetectMissed = { value -> viewModel.updateGeneral { it.copy(autoDetectMissed = value) } },
    onMissedGrace = { minutes -> viewModel.updateGeneral { it.copy(missedGraceMinutes = minutes) } },
    onCarryOver = { value -> viewModel.updateGeneral { it.copy(carryOverUnfinished = value) } },
    onAskSkipReason = { value -> viewModel.updateGeneral { it.copy(askForSkipReason = value) } },

    onWeight = { key, value ->
        viewModel.updateScoring { config ->
            val weights = config.weights
            config.copy(
                weights = when (key) {
                    "completion" -> weights.copy(completion = value)
                    "punctuality" -> weights.copy(punctuality = value)
                    "subtasks" -> weights.copy(subtasks = value)
                    "routine" -> weights.copy(routine = value)
                    else -> weights.copy(consistency = value)
                }
            )
        }
    },
    onPunctuality = { key, value ->
        viewModel.updateScoring { config ->
            val rules = config.punctuality
            config.copy(
                punctuality = when (key) {
                    "grace" -> rules.copy(graceMinutes = value.toInt())
                    "perMinute" -> rules.copy(penaltyPerLateMinute = value)
                    else -> rules.copy(untimedCredit = value.coerceIn(0.0, 1.0))
                }
            )
        }
    },
    onPenalty = { key, value ->
        viewModel.updateScoring { config ->
            val rules = config.penalties
            config.copy(
                penalties = when (key) {
                    "missed" -> rules.copy(missedPenalty = value)
                    "skipped" -> rules.copy(skippedPenalty = value)
                    else -> rules.copy(maxTotalPenalty = value)
                }
            )
        }
    },
    onBonus = { key, value ->
        viewModel.updateScoring { config ->
            val rules = config.bonuses
            config.copy(
                bonuses = when (key) {
                    "perfect" -> rules.copy(perfectDayBonus = value)
                    "streakPerDay" -> rules.copy(streakBonusPerDay = value)
                    "streakCap" -> rules.copy(streakBonusCap = value)
                    else -> rules.copy(earlyRiserBonus = value)
                }
            )
        }
    },
    onPriorityMultiplier = { priority, value ->
        viewModel.updateScoring { config ->
            config.copy(priorityMultipliers = config.priorityMultipliers + (priority to value))
        }
    },
    onStreakQualifying = { value -> viewModel.updateScoring { it.copy(streakQualifyingScore = value) } },
    onSkipFreeWithReason = { value ->
        viewModel.updateScoring { it.copy(penalties = it.penalties.copy(skipIsFreeWithReason = value)) }
    },
    onWeeklyDropWorst = { value -> viewModel.updateScoring { it.copy(weeklyDropWorstDay = value) } },
    onResetScoring = viewModel::resetScoring,

    onNotificationsEnabled = { value -> viewModel.updateNotifications { it.copy(enabled = value) } },
    onTaskReminders = { value -> viewModel.updateNotifications { it.copy(taskReminders = value) } },
    onDefaultLead = { minutes -> viewModel.updateNotifications { it.copy(defaultLeadMinutes = minutes) } },
    onOverdueAlerts = { value -> viewModel.updateNotifications { it.copy(overdueAlerts = value) } },
    onDayStartSummary = { value -> viewModel.updateNotifications { it.copy(dayStartSummary = value) } },
    onDayReview = { value -> viewModel.updateNotifications { it.copy(dayReview = value) } },
    onWeeklyReport = { value -> viewModel.updateNotifications { it.copy(weeklyReport = value) } },
    onStreakAtRisk = { value -> viewModel.updateNotifications { it.copy(streakAtRisk = value) } },
    onQuietHours = { value -> viewModel.updateNotifications { it.copy(quietHoursEnabled = value) } },

    onExport = { onExport(viewModel.exportBackup()) },
    onResetEverything = viewModel::resetEverything
)

/** Maps every notification kind onto its localised title and body. */
fun notificationCopy(strings: Strings): NotificationCopy = NotificationCopy { notification ->
    fun arg(index: Int) = notification.args.getOrElse(index) { "" }
    val pair: Pair<String, String> = when (notification.kind) {
        NotificationKind.DayStart ->
            strings.notifyDayStartTitle to strings.notifyDayStartBody.format(arg(0))

        NotificationKind.TaskReminder ->
            strings.notifyReminderTitle.format(arg(0), arg(1)) to strings.upNext

        NotificationKind.TaskStart ->
            strings.notifyStartTitle.format(arg(0)) to strings.upNext

        NotificationKind.TaskOverdue ->
            strings.notifyOverdueTitle.format(arg(0)) to strings.notifyOverdueBody

        NotificationKind.DayReview ->
            strings.notifyReviewTitle to strings.notifyReviewBody.format(arg(0))

        NotificationKind.WeeklyReport -> strings.notifyWeeklyTitle to strings.weekScore

        NotificationKind.StreakAtRisk ->
            strings.notifyStreakTitle to strings.notifyStreakBody.format(arg(0), arg(1))

        NotificationKind.GoalPace ->
            strings.notifyGoalTitle.format(arg(0)) to strings.notifyGoalBody.format(arg(1))
    }
    NotificationPayload(id = notification.id, title = pair.first, body = pair.second)
}
