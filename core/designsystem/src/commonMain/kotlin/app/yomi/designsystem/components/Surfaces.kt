package app.yomi.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.yomi.designsystem.theme.YomiTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * The app's standard container: generous corners, a whisper of tint, and an
 * optional accent colour that lets a card carry its category's identity.
 */
@Composable
fun YomiCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(YomiTheme.spacing.cardPadding),
    content: @Composable ColumnScopeAlias.() -> Unit
) {
    val tint = accent?.copy(alpha = if (YomiTheme.appearance.colorfulCards) 0.10f else 0f)
    val container = MaterialTheme.colorScheme.surfaceContainer
    val base = Modifier
        .clip(shape)
        .background(container)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Box(modifier = modifier.then(base)) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Alias so callers do not need to import Compose's ColumnScope explicitly. */
typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/** A titled section, optionally with an action on the trailing edge. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/** A compact number with a label — the building block of every summary row. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = YomiTheme.spacing.medium, horizontal = YomiTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (emoji != null) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(YomiTheme.spacing.hairline))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** A small rounded label, optionally selectable. */
@Composable
fun YomiChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    emoji: String? = null,
    accent: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val color = accent ?: MaterialTheme.colorScheme.primary
    val background = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (selected) color else Color.Transparent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(percent = 50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = YomiTheme.spacing.medium, vertical = YomiTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.tiny)
    ) {
        if (emoji != null && YomiTheme.appearance.showEmojis) Text(emoji, style = MaterialTheme.typography.labelLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** A round colour swatch used by the accent and category pickers. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(percent = 50)
            )
            .clickable(onClick = onClick)
    )
}

/**
 * A progress bar with a gentle wave in it. The wave flattens as the bar fills,
 * which gives "almost done" a visible personality without any extra text.
 */
@Composable
fun WavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val motion = YomiTheme.motion
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(if (motion.enabled) motion.medium else 0),
        label = "wavyProgress"
    )

    // The bar is drawn with raw canvas coordinates, so right-to-left layouts
    // have to be mirrored explicitly — unlike the box-based bars, which the
    // layout system flips for free.
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(modifier.fillMaxWidth().height(height)) {
        val strokeWidth = size.height * 0.7f
        val y = size.height / 2f

        val body: DrawScope.() -> Unit = {
            drawLine(
                color = trackColor,
                start = Offset(strokeWidth / 2f, y),
                end = Offset(size.width - strokeWidth / 2f, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            if (animated > 0f) {
                val end = (size.width - strokeWidth) * animated + strokeWidth / 2f
                // The closer to done, the calmer the wave.
                val amplitude = size.height * 0.22f * (1f - animated)
                if (amplitude < 0.4f) {
                    drawLine(
                        color = color,
                        start = Offset(strokeWidth / 2f, y),
                        end = Offset(end, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                } else {
                    val path = Path().apply {
                        moveTo(strokeWidth / 2f, y)
                        var x = strokeWidth / 2f
                        val step = 3f
                        while (x < end) {
                            val phase = (x / size.width) * WAVE_CYCLES * 2f * PI.toFloat()
                            lineTo(x, y + sin(phase) * amplitude)
                            x += step
                        }
                        lineTo(end, y)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.85f), color)
                        ),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (mirrored) scale(scaleX = -1f, scaleY = 1f) { body() } else body()
    }
}

/** A slim linear bar for compact rows. */
@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 6.dp
) {
    val motion = YomiTheme.motion
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(if (motion.enabled) motion.fast else 0),
        label = "thinProgress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(percent = 50))
                .background(color)
        )
    }
}

/** The friendly "there is nothing here yet" panel. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(YomiTheme.spacing.xlarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(YomiTheme.spacing.small))
            action()
        }
    }
}

/** A labelled value pair used throughout the settings screens. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = YomiTheme.spacing.small, horizontal = YomiTheme.spacing.tiny),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(YomiTheme.spacing.medium))
            trailing()
        }
    }
}

/** A soft divider that reads as a breath rather than a line. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

/** A rounded surface that hosts a whole screen section. */
@Composable
fun PanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeAlias.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(YomiTheme.spacing.cardPadding), content = content)
    }
}

private const val WAVE_CYCLES = 5f
