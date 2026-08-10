package app.yomi.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.theme.YomiTheme
import kotlin.math.min

/**
 * The headline of the app: one number, one grade, one arc.
 *
 * The arc's colour travels from the "low" accent through "mid" to "high" as the
 * score climbs, so the ring reads correctly at a glance even before the number
 * is parsed. A faint second arc shows how much of the day is still winnable.
 */
@Composable
fun ScoreRing(
    score: Double,
    maxScore: Double = 100.0,
    gradeLabel: String = "",
    gradeEmoji: String = "",
    caption: String = "",
    potential: Double? = null,
    size: Dp = 190.dp,
    strokeWidth: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val accents = YomiTheme.accents
    val motion = YomiTheme.motion
    val ratio = (score / maxScore).coerceIn(0.0, 1.0).toFloat()
    val potentialRatio = potential?.let { (it / maxScore).coerceIn(0.0, 1.0).toFloat() }

    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = if (motion.enabled) motion.slow else 0),
        label = "scoreRing"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = scoreColor(ratio, accents.scoreLow, accents.scoreMid, accents.scoreHigh)
    val potentialColor = arcColor.copy(alpha = 0.22f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val diameter = min(this.size.width, this.size.height) - stroke
            val topLeft = Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            potentialRatio?.let {
                drawArc(
                    color = potentialColor,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_SWEEP * it,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to arcColor.copy(alpha = 0.75f),
                        0.5f to arcColor,
                        1f to arcColor.copy(alpha = 0.75f),
                        center = Offset(this.size.width / 2f, this.size.height / 2f)
                    ),
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_SWEEP * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            // A soft inner halo keeps the centre from looking punched out.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(arcColor.copy(alpha = 0.12f), Color.Transparent),
                    center = center,
                    radius = diameter / 2f - inset
                ),
                radius = diameter / 2f - inset
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = Fmt.score(score),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (gradeLabel.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(gradeEmoji, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = " $gradeLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = arcColor
                    )
                }
            }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Red below a third, amber in the middle, green at the top — smoothly. */
fun scoreColor(ratio: Float, low: Color, mid: Color, high: Color): Color = when {
    ratio <= 0f -> low
    ratio < MID_POINT -> lerp(low, mid, ratio / MID_POINT)
    else -> lerp(mid, high, ((ratio - MID_POINT) / (1f - MID_POINT)).coerceIn(0f, 1f))
}

private const val START_ANGLE = 130f
private const val FULL_SWEEP = 280f
private const val MID_POINT = 0.55f
