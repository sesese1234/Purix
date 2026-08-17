package app.yomi.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.theme.YomiTheme

/** One column of [BarChart]. */
data class BarDatum(
    val label: String,
    val value: Double,
    val color: Color? = null,
    val highlighted: Boolean = false
)

/**
 * A rounded bar chart sized to its content. Bars carry their own colour when a
 * category supplies one, so the chart matches the rest of the screen.
 */
@Composable
fun BarChart(
    data: List<BarDatum>,
    modifier: Modifier = Modifier,
    maxValue: Double? = null,
    height: Dp = 140.dp,
    showValues: Boolean = true
) {
    if (data.isEmpty()) return
    val ceiling = (maxValue ?: data.maxOf { it.value }).coerceAtLeast(1.0)
    val defaultColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { datum ->
            val fraction = (datum.value / ceiling).coerceIn(0.0, 1.0).toFloat()
                .coerceIn(BAR_MIN_FRACTION, 1f)
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // The empty space above the bar is a weighted spacer, which keeps
                // the value label glued to the top of its own column.
                if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
                if (showValues) {
                    Text(
                        text = if (datum.value <= 0.0) "" else datum.value.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(fraction)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    (datum.color ?: defaultColor),
                                    (datum.color ?: defaultColor).copy(alpha = 0.55f)
                                )
                            )
                        )
                )
                Text(
                    text = datum.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (datum.highlighted) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A smoothed score trend with a translucent fill underneath. Values are
 * expected in `0..maxValue`; gaps in the data simply shorten the line.
 */
@Composable
fun TrendLine(
    values: List<Double>,
    modifier: Modifier = Modifier,
    maxValue: Double = 100.0,
    height: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val h = size.height
        val w = size.width
        // Quarter grid lines give the eye something to measure against.
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas

        val step = w / (values.size - 1)
        fun pointAt(index: Int): Offset {
            val value = values[index].coerceIn(0.0, maxValue)
            val y = h - (value / maxValue).toFloat() * h
            return Offset(index * step, y.coerceIn(0f, h))
        }

        val line = Path()
        val area = Path()
        line.moveTo(pointAt(0).x, pointAt(0).y)
        area.moveTo(0f, h)
        area.lineTo(pointAt(0).x, pointAt(0).y)

        for (i in 1 until values.size) {
            val previous = pointAt(i - 1)
            val current = pointAt(i)
            val midX = (previous.x + current.x) / 2f
            line.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            area.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
        }
        area.lineTo(w, h)
        area.close()

        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent))
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

/** One square of the year heatmap. */
data class HeatCell(val label: String, val value: Double?, val tooltip: String = "")

/**
 * A calendar heatmap. Cells with no data stay neutral, so a gap reads as "no
 * plan" rather than "a bad day".
 */
@Composable
fun Heatmap(
    cells: List<HeatCell>,
    columns: Int,
    modifier: Modifier = Modifier,
    cellSize: Dp = 14.dp,
    maxValue: Double = 100.0,
    onCellClick: ((Int) -> Unit)? = null
) {
    val accents = YomiTheme.accents
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val rows = if (columns <= 0) 0 else (cells.size + columns - 1) / columns

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    val cell = cells.getOrNull(index)
                    val ratio = cell?.value?.let { (it / maxValue).coerceIn(0.0, 1.0).toFloat() }
                    val color = if (ratio == null) {
                        empty
                    } else {
                        scoreColor(ratio, accents.scoreLow, accents.scoreMid, accents.scoreHigh)
                            .copy(alpha = 0.35f + 0.65f * ratio)
                    }
                    Box(
                        Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (cell == null) Color.Transparent else color)
                    )
                }
            }
        }
    }
}

/** A horizontal breakdown row: label, bar, value. */
@Composable
fun BreakdownRow(
    label: String,
    value: String,
    ratio: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = YomiTheme.spacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)
    ) {
        Box(
            Modifier
                .size(DOT_SIZE)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(LABEL_WEIGHT),
            maxLines = 1
        )
        ThinProgress(progress = ratio, color = color, modifier = Modifier.weight(BAR_WEIGHT))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val DOT_SIZE = 10.dp

private const val BAR_MIN_FRACTION = 0.02f
private const val LABEL_WEIGHT = 1.1f
private const val BAR_WEIGHT = 1.6f
