package app.yomi.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    @SerialName("system") System,
    @SerialName("light") Light,
    @SerialName("dark") Dark
}

/** Palette generation strategy, mapped onto MaterialKolor's palette styles. */
@Serializable
enum class PaletteFlavor {
    @SerialName("expressive") Expressive,
    @SerialName("vibrant") Vibrant,
    @SerialName("tonalSpot") TonalSpot,
    @SerialName("rainbow") Rainbow,
    @SerialName("fruitSalad") FruitSalad,
    @SerialName("neutral") Neutral,
    @SerialName("monochrome") Monochrome,
    @SerialName("fidelity") Fidelity,
    @SerialName("content") Content
}

/** Global corner-radius personality. */
@Serializable
enum class ShapeStyle {
    @SerialName("soft") Soft,
    @SerialName("rounded") Rounded,
    @SerialName("pill") Pill,
    @SerialName("sharp") Sharp
}

@Serializable
enum class UiDensity {
    @SerialName("compact") Compact,
    @SerialName("cozy") Cozy,
    @SerialName("comfortable") Comfortable
}

@Serializable
enum class MotionLevel {
    @SerialName("none") None,
    @SerialName("subtle") Subtle,
    @SerialName("playful") Playful
}

@Serializable
enum class AppLanguage {
    @SerialName("system") System,
    @SerialName("en") English,
    @SerialName("he") Hebrew
}

/** Which cards appear on the Today screen, and in what order. */
@Serializable
enum class TodayCard {
    @SerialName("score") Score,
    @SerialName("checkIn") CheckIn,
    @SerialName("progress") Progress,
    @SerialName("upNext") UpNext,
    @SerialName("goals") Goals,
    @SerialName("streak") Streak,
    @SerialName("timeline") Timeline
}

/** How the timeline groups its items. */
@Serializable
enum class TimelineGrouping {
    @SerialName("dayPart") DayPart,
    @SerialName("category") Category,
    @SerialName("priority") Priority,
    @SerialName("status") Status,
    @SerialName("flat") Flat
}

@Serializable
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val seedColorArgb: Long = 0xFF7C6BF2,
    val paletteFlavor: PaletteFlavor = PaletteFlavor.Vibrant,
    val shapeStyle: ShapeStyle = ShapeStyle.Rounded,
    val density: UiDensity = UiDensity.Cozy,
    val motion: MotionLevel = MotionLevel.Playful,
    /** 0.85..1.35 */
    val fontScale: Double = 1.0,
    /** Pure black backgrounds in dark mode. */
    val amoledDark: Boolean = false,
    /** Higher contrast text and outlines. */
    val highContrast: Boolean = false,
    /** Tint every card by its category colour. */
    val colorfulCards: Boolean = true,
    /** Show the big emoji on task rows. */
    val showEmojis: Boolean = true,
    val showScoreRing: Boolean = true
)

@Serializable
data class GeneralSettings(
    val language: AppLanguage = AppLanguage.System,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val use24HourClock: Boolean = true,
    /**
     * The hour at which a "day" rolls over. 4 means that anything logged at
     * 02:00 still belongs to the previous day — the classic night-owl fix.
     */
    val dayStartHour: Int = 4,
    val defaultWakeTime: LocalTime = LocalTime(7, 0),
    val defaultSleepTime: LocalTime = LocalTime(23, 30),
    val timelineGrouping: TimelineGrouping = TimelineGrouping.DayPart,
    val todayCards: List<TodayCard> = listOf(
        TodayCard.Score,
        TodayCard.Progress,
        TodayCard.CheckIn,
        TodayCard.UpNext,
        TodayCard.Timeline
    ),
    val hideCompletedInTimeline: Boolean = false,
    val confirmBeforeSkipping: Boolean = true,
    /** Ask for a reason when skipping, which can make the skip penalty-free. */
    val askForSkipReason: Boolean = true,
    /** Auto-mark overdue tasks as missed instead of leaving them pending. */
    val autoDetectMissed: Boolean = true,
    /** Minutes after the due time before an item is declared missed. */
    val missedGraceMinutes: Int = 30,
    /** Move unfinished items to tomorrow when the day is finalised. */
    val carryOverUnfinished: Boolean = false,
    /** Boundaries between day parts, in hours: early / morning / afternoon / evening. */
    val dayPartBoundaries: List<Int> = listOf(5, 12, 17, 21)
) {
    fun dayPartOf(time: LocalTime?): DayPart {
        if (time == null) return DayPart.Anytime
        val b = dayPartBoundaries.takeIf { it.size == 4 } ?: listOf(5, 12, 17, 21)
        val h = time.hour
        return when {
            h < b[0] -> DayPart.Night
            h < b[1] -> if (h < b[0] + 3) DayPart.EarlyMorning else DayPart.Morning
            h < b[2] -> DayPart.Afternoon
            h < b[3] -> DayPart.Evening
            else -> DayPart.Night
        }
    }
}

@Serializable
data class NotificationSettings(
    val enabled: Boolean = true,
    val taskReminders: Boolean = true,
    /** Default lead time for tasks without their own [ReminderRule]. */
    val defaultLeadMinutes: Int = 10,
    val overdueAlerts: Boolean = true,
    val dayStartSummary: Boolean = true,
    val dayStartTime: LocalTime = LocalTime(7, 15),
    val dayReview: Boolean = true,
    val dayReviewTime: LocalTime = LocalTime(21, 30),
    val weeklyReport: Boolean = true,
    val weeklyReportDay: DayOfWeek = DayOfWeek.SUNDAY,
    val weeklyReportTime: LocalTime = LocalTime(20, 0),
    val streakAtRisk: Boolean = true,
    val goalPaceAlerts: Boolean = true,
    /** No notifications inside this window. */
    val quietHoursEnabled: Boolean = true,
    val quietStart: LocalTime = LocalTime(23, 0),
    val quietEnd: LocalTime = LocalTime(6, 30)
)

/** The whole preferences document. Persisted as a single JSON file. */
@Serializable
data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val general: GeneralSettings = GeneralSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val scoring: ScoringConfig = ScoringConfig(),
    /** Bumped by migrations; lets old files be upgraded safely. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val onboardingCompleted: Boolean = false
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
