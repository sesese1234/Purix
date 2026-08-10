package app.yomi.designsystem.i18n

import app.yomi.model.InsightKind
import app.yomi.model.ScoreInsight

/**
 * Turns the domain's machine-readable observations into a sentence.
 *
 * The engines deliberately emit [ScoreInsight] rather than text, so the same
 * verdict reads naturally in every language and nothing user-facing has to live
 * inside the scoring rules.
 */
fun insightText(insight: ScoreInsight, strings: Strings): String {
    val a = insight.args
    fun arg(index: Int): String = a.getOrElse(index) { "" }

    return when (insight.kind) {
        InsightKind.EmptyDay -> strings.insightEmptyDay
        InsightKind.PerfectDay -> strings.insightPerfectDay
        InsightKind.AllTasksDone -> strings.insightAllDone
        InsightKind.MissedTasks -> strings.insightMissed.format(arg(0))
        InsightKind.LateStart -> strings.insightLateStart.format(arg(0))
        InsightKind.EarlyRiser -> strings.insightEarlyRiser
        InsightKind.PunctualityStrong -> strings.insightPunctualityStrong.format(arg(0))
        InsightKind.PunctualityWeak -> strings.insightPunctualityWeak.format(arg(0))
        InsightKind.SubtasksIncomplete -> strings.insightSubtasks.format(arg(0))
        InsightKind.StreakAlive -> strings.insightStreakAlive.format(arg(0))
        InsightKind.StreakBroken -> strings.noStreak
        InsightKind.StreakAtRisk -> strings.insightStreakAtRisk.format(arg(0))
        InsightKind.BestCategory -> strings.bestDay
        InsightKind.WorstCategory -> strings.worstDay
        InsightKind.ImprovedOverYesterday -> strings.insightImproved.format(arg(0))
        InsightKind.DeclinedFromYesterday -> strings.insightDeclined.format(arg(0))
        InsightKind.WeeklyConsistent -> strings.insightWeeklyConsistent.format(arg(0))
        InsightKind.WeeklyBestDay -> strings.insightWeeklyBest.format(arg(0), arg(1))
        InsightKind.WeeklyWorstDay -> strings.insightWeeklyWorst.format(arg(0), arg(1))
        InsightKind.WeeklyGoalPace -> strings.onPace
        InsightKind.NoCheckIn -> strings.insightNoCheckIn
    }
}

/** Localised label for a bonus or penalty produced by the scoring engine. */
fun adjustmentLabel(key: String, strings: Strings): String = when (key) {
    "perfectDay" -> strings.bonusPerfectDay
    "streak" -> strings.bonusStreak
    "allSubtasks" -> strings.bonusAllSubtasks
    "earlyRiser" -> strings.bonusEarlyRiser
    "goalPace" -> strings.bonusGoalPace
    "missed" -> strings.penaltyMissed
    "skipped" -> strings.penaltySkipped
    "deferred" -> strings.penaltyDeferred
    "weeklyConsistency" -> strings.weeklyConsistency
    else -> key
}
