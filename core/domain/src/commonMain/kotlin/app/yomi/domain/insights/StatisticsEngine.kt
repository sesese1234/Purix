package app.yomi.domain.insights

import app.yomi.domain.scoring.round1
import app.yomi.model.Category
import app.yomi.model.DayPart
import app.yomi.model.DayPlan
import app.yomi.model.DayScore
import app.yomi.model.EntryStatus
import app.yomi.model.GeneralSettings
import app.yomi.model.Priority
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** One cell of the year heatmap. */
data class HeatmapCell(
    val date: LocalDate,
    val score: Double?,
    val hasPlan: Boolean
)

/** Completion statistics for one grouping key. */
data class BucketStats(
    val key: String,
    val label: String,
    val emoji: String,
    val colorArgb: Long?,
    val planned: Int,
    val done: Int,
    val missed: Int,
    val skipped: Int,
    val averageScoreContribution: Double
) {
    val completionRate: Double get() = if (planned == 0) 0.0 else done.toDouble() / planned
}

/** Everything the Insights screen shows, computed in one pass. */
data class InsightsReport(
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val averageScore: Double,
    val bestScore: Double,
    val worstScore: Double,
    val daysTracked: Int,
    val perfectDays: Int,
    val tasksDone: Int,
    val tasksPlanned: Int,
    val tasksMissed: Int,
    val completionRate: Double,
    val onTimeRate: Double,
    val byCategory: List<BucketStats>,
    val byPriority: List<BucketStats>,
    val byWeekday: List<Pair<DayOfWeek, Double>>,
    val byDayPart: List<Pair<DayPart, Double>>,
    val scoreTrend: List<Pair<LocalDate, Double>>,
    val averageWakeMinutes: Int?,
    val wakeConsistencyMinutes: Int?
)

/**
 * Aggregates the journal into the numbers the Insights screen renders. Kept
 * separate from scoring so that changing a chart never risks changing a score.
 */
class StatisticsEngine {

    fun report(
        days: List<DayPlan>,
        scores: Map<LocalDate, DayScore>,
        categories: List<Category>,
        settings: GeneralSettings,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): InsightsReport {
        val window = days.filter { it.date in rangeStart..rangeEnd }.sortedBy { it.date }
        val tracked = window.filter { it.entries.isNotEmpty() }
        val trackedScores = tracked.mapNotNull { scores[it.date] }

        val allEntries = window.flatMap { it.entries }
        val done = allEntries.count { it.status == EntryStatus.Done }
        val missed = allEntries.count { it.status == EntryStatus.Missed }

        val timed = allEntries.filter { it.timing.dueAt != null && it.status.isSuccessful }
        val onTime = timed.count { entry ->
            val due = entry.timing.dueAt ?: return@count false
            val completed = entry.completedAt ?: return@count false
            completed <= due
        }

        val categoryById = categories.associateBy { it.id }

        val byCategory = allEntries
            .groupBy { it.categoryId }
            .map { (categoryId, entries) ->
                val category = categoryId?.let { categoryById[it] }
                BucketStats(
                    key = categoryId ?: UNCATEGORIZED,
                    label = category?.name ?: UNCATEGORIZED,
                    emoji = category?.emoji ?: "•",
                    colorArgb = category?.colorArgb,
                    planned = entries.size,
                    done = entries.count { it.status == EntryStatus.Done },
                    missed = entries.count { it.status == EntryStatus.Missed },
                    skipped = entries.count { it.status == EntryStatus.Skipped },
                    averageScoreContribution = entries.sumOf { it.completionRatio() } / entries.size
                )
            }
            .sortedByDescending { it.planned }

        val byPriority = Priority.entries.mapNotNull { priority ->
            val entries = allEntries.filter { it.priority == priority }
            if (entries.isEmpty()) return@mapNotNull null
            BucketStats(
                key = priority.name,
                label = priority.name,
                emoji = "",
                colorArgb = null,
                planned = entries.size,
                done = entries.count { it.status == EntryStatus.Done },
                missed = entries.count { it.status == EntryStatus.Missed },
                skipped = entries.count { it.status == EntryStatus.Skipped },
                averageScoreContribution = entries.sumOf { it.completionRatio() } / entries.size
            )
        }

        val byWeekday = DayOfWeek.entries.mapNotNull { weekday ->
            val relevant = trackedScores.filter { it.date.dayOfWeek == weekday }
            if (relevant.isEmpty()) null
            else weekday to (relevant.sumOf { it.score } / relevant.size).round1()
        }

        val byDayPart = DayPart.entries.mapNotNull { part ->
            val entries = allEntries.filter { settings.dayPartOf(it.timing.startsAt) == part }
            if (entries.isEmpty()) return@mapNotNull null
            val rate = entries.count { it.status == EntryStatus.Done }.toDouble() / entries.size
            part to (rate * 100).round1()
        }

        val wakeMinutes = window.mapNotNull { plan ->
            plan.checkIn.wakeActual?.let { it.hour * 60 + it.minute }
        }
        val averageWake = if (wakeMinutes.isEmpty()) null else wakeMinutes.average().toInt()
        val wakeSpread = if (wakeMinutes.size < 2 || averageWake == null) {
            null
        } else {
            val variance = wakeMinutes.sumOf { val d = (it - averageWake).toDouble(); d * d } / wakeMinutes.size
            kotlin.math.sqrt(variance).toInt()
        }

        return InsightsReport(
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            averageScore = if (trackedScores.isEmpty()) 0.0
            else (trackedScores.sumOf { it.score } / trackedScores.size).round1(),
            bestScore = trackedScores.maxOfOrNull { it.score } ?: 0.0,
            worstScore = trackedScores.minOfOrNull { it.score } ?: 0.0,
            daysTracked = tracked.size,
            perfectDays = trackedScores.count { it.isPerfectDay },
            tasksDone = done,
            tasksPlanned = allEntries.size,
            tasksMissed = missed,
            completionRate = if (allEntries.isEmpty()) 0.0 else done.toDouble() / allEntries.size,
            onTimeRate = if (timed.isEmpty()) 0.0 else onTime.toDouble() / timed.size,
            byCategory = byCategory,
            byPriority = byPriority,
            byWeekday = byWeekday,
            byDayPart = byDayPart,
            scoreTrend = window.mapNotNull { plan -> scores[plan.date]?.let { plan.date to it.score } },
            averageWakeMinutes = averageWake,
            wakeConsistencyMinutes = wakeSpread
        )
    }

    fun heatmap(
        dates: List<LocalDate>,
        days: Map<LocalDate, DayPlan>,
        scores: Map<LocalDate, DayScore>
    ): List<HeatmapCell> = dates.map { date ->
        val plan = days[date]
        HeatmapCell(
            date = date,
            score = scores[date]?.takeIf { plan != null && plan.entries.isNotEmpty() }?.score,
            hasPlan = plan != null && plan.entries.isNotEmpty()
        )
    }

    private companion object {
        const val UNCATEGORIZED = "uncategorized"
    }
}
