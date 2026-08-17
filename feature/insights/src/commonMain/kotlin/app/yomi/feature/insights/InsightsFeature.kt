package app.yomi.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.yomi.data.YomiRepository
import app.yomi.designsystem.components.BarChart
import app.yomi.designsystem.components.BarDatum
import app.yomi.designsystem.components.BreakdownRow
import app.yomi.designsystem.components.EmptyState
import app.yomi.designsystem.components.HeatCell
import app.yomi.designsystem.components.Heatmap
import app.yomi.designsystem.components.ScoreRing
import app.yomi.designsystem.components.SectionHeader
import app.yomi.designsystem.components.StatTile
import app.yomi.designsystem.components.TrendLine
import app.yomi.designsystem.components.YomiCard
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.i18n.insightText
import app.yomi.designsystem.icons.YomiIcons
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.domain.insights.InsightsReport
import app.yomi.domain.insights.StatisticsEngine
import app.yomi.domain.scoring.WeekMath
import app.yomi.model.AppSettings
import app.yomi.model.WeekScore
import app.yomi.ui.YomiViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The window the insights are measured over. */
enum class InsightRange(val days: Int) {
    Week(7), Month(30), Quarter(90)
}

data class InsightsUiState(
    val range: InsightRange = InsightRange.Week,
    val report: InsightsReport? = null,
    val weekScore: WeekScore? = null,
    val heatmap: List<HeatCell> = emptyList(),
    val heatmapColumns: Int = 7,
    val settings: AppSettings = AppSettings()
)

/** Reads the journal and produces every chart on the Insights screen. */
class InsightsViewModel(repository: YomiRepository) : YomiViewModel(repository) {

    private val statistics = StatisticsEngine()
    private val range = MutableStateFlow(InsightRange.Week)

    val state: StateFlow<InsightsUiState> = combine(
        range,
        repository.days,
        repository.scores,
        repository.settings,
        repository.catalog
    ) { window, days, scores, settings, catalog ->
        val today = repository.today()
        val start = today.minus(window.days - 1, DateTimeUnit.DAY)

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
            range = window,
            report = statistics.report(
                days = days.values.toList(),
                scores = scores,
                categories = catalog.categories,
                settings = settings.general,
                rangeStart = start,
                rangeEnd = today
            ),
            weekScore = repository.weekScore(today),
            heatmap = statistics.heatmap(heatDates, days, scores).map { cell ->
                HeatCell(
                    label = Fmt.shortDate(cell.date),
                    value = cell.score,
                    tooltip = Fmt.isoDate(cell.date)
                )
            },
            heatmapColumns = HEATMAP_COLUMNS,
            settings = settings
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), InsightsUiState())

    fun selectRange(value: InsightRange) {
        range.value = value
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT = 5_000L
        const val HEATMAP_DAYS = 90
        const val HEATMAP_COLUMNS = 7
    }
}

/** The statistics screen: the week's headline, then the details behind it. */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    modifier: Modifier = Modifier,
    onSelectRange: (InsightRange) -> Unit = {}
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing
    val report = state.report

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = spacing.screenPadding),
        contentPadding = PaddingValues(top = spacing.medium, bottom = spacing.xxlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.large)
    ) {
        item("rangePicker") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                listOf(
                    InsightRange.Week to strings.last7Days,
                    InsightRange.Month to strings.last30Days,
                    InsightRange.Quarter to strings.last90Days
                ).forEach { (value, label) ->
                    YomiChip(
                        label = label,
                        selected = state.range == value,
                        onClick = { onSelectRange(value) }
                    )
                }
            }
        }

        if (report == null || report.daysTracked == 0) {
            item("empty") {
                EmptyState(icon = YomiIcons.Insights, title = strings.insights, body = strings.notEnoughData)
            }
            return@LazyColumn
        }

        state.weekScore?.let { week ->
            item("week") {
                YomiCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(
                            score = week.score,
                            gradeLabel = week.grade.label,
                            gradeEmoji = week.grade.emoji,
                            caption = strings.weekScore,
                            size = WEEK_RING_SIZE.dpValue()
                        )
                        Spacer(Modifier.width(spacing.medium))
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacing.tiny)
                        ) {
                            week.insights.forEach { insight ->
                                Text(
                                    text = insightText(insight, strings),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${strings.perfectDays}: ${week.perfectDays} · " +
                                    "${strings.longestStreak}: ${week.longestStreak}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item("summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                StatTile(
                    value = Fmt.score(report.averageScore),
                    label = strings.averageScore,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = Fmt.percent(report.completionRate),
                    label = strings.completionRate,
                    modifier = Modifier.weight(1f),
                    accent = YomiTheme.accents.success
                )
                StatTile(
                    value = Fmt.percent(report.onTimeRate),
                    label = strings.punctualityRate,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = report.perfectDays.toString(),
                    label = strings.perfectDays,
                    modifier = Modifier.weight(1f),
                    icon = YomiIcons.Star
                )
            }
        }

        if (report.scoreTrend.size >= 2) {
            item("trend") {
                YomiCard {
                    SectionHeader(title = strings.scoreTrend)
                    Spacer(Modifier.height(spacing.medium))
                    TrendLine(values = report.scoreTrend.map { it.second })
                }
            }
        }

        if (report.byWeekday.isNotEmpty()) {
            item("weekday") {
                YomiCard {
                    SectionHeader(title = strings.byWeekday)
                    Spacer(Modifier.height(spacing.medium))
                    BarChart(
                        data = report.byWeekday.map { (day, score) ->
                            BarDatum(
                                label = Fmt.weekdayShort(day, strings),
                                value = score,
                                highlighted = score >= report.averageScore
                            )
                        },
                        maxValue = MAX_SCORE
                    )
                }
            }
        }

        if (report.byCategory.isNotEmpty()) {
            item("category") {
                YomiCard {
                    SectionHeader(title = strings.byCategory)
                    Spacer(Modifier.height(spacing.small))
                    report.byCategory.forEach { bucket ->
                        BreakdownRow(
                            label = bucket.label,
                            value = "${bucket.done}/${bucket.planned}",
                            ratio = bucket.completionRate.toFloat(),
                            color = bucket.colorArgb?.let { Color(it.toInt()) }
                                ?: MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (report.byDayPart.isNotEmpty()) {
            item("dayPart") {
                YomiCard {
                    SectionHeader(title = strings.byTimeOfDay)
                    Spacer(Modifier.height(spacing.medium))
                    BarChart(
                        data = report.byDayPart.map { (part, rate) ->
                            BarDatum(label = Fmt.dayPart(part, strings), value = rate)
                        },
                        maxValue = MAX_SCORE
                    )
                }
            }
        }

        item("routine") {
            YomiCard {
                SectionHeader(title = strings.checkIn)
                Spacer(Modifier.height(spacing.small))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    StatTile(
                        value = report.averageWakeMinutes?.let { minutesToClock(it) } ?: "—",
                        label = strings.averageWake,
                        modifier = Modifier.weight(1f),
                        icon = YomiIcons.Sun
                    )
                    StatTile(
                        value = report.wakeConsistencyMinutes?.let { "±$it′" } ?: "—",
                        label = strings.wakeConsistency,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = report.daysTracked.toString(),
                        label = strings.daysTracked,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item("heatmap") {
            YomiCard {
                SectionHeader(title = strings.heatmap)
                Spacer(Modifier.height(spacing.medium))
                Heatmap(
                    cells = state.heatmap,
                    columns = state.heatmapColumns,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun minutesToClock(minutes: Int): String {
    val hour = (minutes / 60).coerceIn(0, 23)
    val minute = minutes % 60
    return "${if (hour < 10) "0$hour" else "$hour"}:${if (minute < 10) "0$minute" else "$minute"}"
}

private fun Int.dpValue() = androidx.compose.ui.unit.Dp(this.toFloat())

private const val MAX_SCORE = 100.0
private const val WEEK_RING_SIZE = 150
