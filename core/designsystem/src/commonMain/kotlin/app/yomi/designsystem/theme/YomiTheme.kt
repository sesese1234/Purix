package app.yomi.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.i18n.Strings
import app.yomi.designsystem.i18n.stringsFor
import app.yomi.model.AppearanceSettings
import app.yomi.model.MotionLevel
import app.yomi.model.PaletteFlavor
import app.yomi.model.ShapeStyle
import app.yomi.model.ThemeMode
import app.yomi.model.UiDensity
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Spacing and sizing that reacts to the density preference, so "compact" is a
 * genuinely tighter layout rather than just smaller text.
 */
data class YomiSpacing(
    val hairline: androidx.compose.ui.unit.Dp = 2.dp,
    val tiny: androidx.compose.ui.unit.Dp = 4.dp,
    val small: androidx.compose.ui.unit.Dp = 8.dp,
    val medium: androidx.compose.ui.unit.Dp = 12.dp,
    val large: androidx.compose.ui.unit.Dp = 16.dp,
    val xlarge: androidx.compose.ui.unit.Dp = 24.dp,
    val xxlarge: androidx.compose.ui.unit.Dp = 32.dp,
    val screenPadding: androidx.compose.ui.unit.Dp = 20.dp,
    val cardPadding: androidx.compose.ui.unit.Dp = 18.dp,
    val rowHeight: androidx.compose.ui.unit.Dp = 68.dp
) {
    companion object {
        fun of(density: UiDensity): YomiSpacing = when (density) {
            UiDensity.Compact -> YomiSpacing(
                tiny = 3.dp, small = 6.dp, medium = 9.dp, large = 12.dp,
                xlarge = 18.dp, xxlarge = 24.dp,
                screenPadding = 14.dp, cardPadding = 13.dp, rowHeight = 56.dp
            )

            UiDensity.Cozy -> YomiSpacing()
            UiDensity.Comfortable -> YomiSpacing(
                small = 10.dp, medium = 16.dp, large = 20.dp,
                xlarge = 30.dp, xxlarge = 40.dp,
                screenPadding = 26.dp, cardPadding = 24.dp, rowHeight = 80.dp
            )
        }
    }
}

/** Animation budget, driven by the motion preference. */
data class YomiMotion(
    val enabled: Boolean = true,
    val fast: Int = 180,
    val medium: Int = 320,
    val slow: Int = 620,
    val springy: Boolean = true
) {
    companion object {
        fun of(level: MotionLevel): YomiMotion = when (level) {
            MotionLevel.None -> YomiMotion(enabled = false, fast = 0, medium = 0, slow = 0, springy = false)
            MotionLevel.Subtle -> YomiMotion(fast = 120, medium = 200, slow = 320, springy = false)
            MotionLevel.Playful -> YomiMotion()
        }
    }
}

/** Extra colour roles the app needs that Material does not define. */
data class YomiAccents(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val missed: Color,
    val skipped: Color,
    val scoreLow: Color,
    val scoreMid: Color,
    val scoreHigh: Color
) {
    companion object {
        fun of(dark: Boolean): YomiAccents = if (dark) {
            YomiAccents(
                success = Color(0xFF6BDCA8), onSuccess = Color(0xFF04321F),
                warning = Color(0xFFFFCB6B), onWarning = Color(0xFF3A2A00),
                missed = Color(0xFFFF8A8A), skipped = Color(0xFF9AA0B4),
                scoreLow = Color(0xFFFF8A8A), scoreMid = Color(0xFFFFCB6B), scoreHigh = Color(0xFF6BDCA8)
            )
        } else {
            YomiAccents(
                success = Color(0xFF1E9C6B), onSuccess = Color(0xFFFFFFFF),
                warning = Color(0xFFC77E00), onWarning = Color(0xFFFFFFFF),
                missed = Color(0xFFD64545), skipped = Color(0xFF6B7185),
                scoreLow = Color(0xFFD64545), scoreMid = Color(0xFFE0A100), scoreHigh = Color(0xFF1E9C6B)
            )
        }
    }
}

val LocalSpacing = staticCompositionLocalOf { YomiSpacing() }
val LocalMotion = staticCompositionLocalOf { YomiMotion() }
val LocalAccents = staticCompositionLocalOf { YomiAccents.of(dark = false) }
val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }

/** Convenient access to the app's own design tokens next to `MaterialTheme`. */
object YomiTheme {
    val spacing: YomiSpacing
        @Composable get() = LocalSpacing.current
    val motion: YomiMotion
        @Composable get() = LocalMotion.current
    val accents: YomiAccents
        @Composable get() = LocalAccents.current
    val strings: Strings
        @Composable get() = LocalStrings.current
    val appearance: AppearanceSettings
        @Composable get() = LocalAppearance.current
}

/**
 * The single theme wrapper for the whole app.
 *
 * The colour scheme is generated from the user's seed colour, the corner
 * radius, spacing, text size and motion all follow their own preferences, and
 * the layout direction flips with the chosen language — so Hebrew is a genuine
 * right-to-left experience rather than translated English.
 */
@Composable
fun YomiTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    language: app.yomi.model.AppLanguage = app.yomi.model.AppLanguage.System,
    systemIsHebrew: Boolean = false,
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dark = when (appearance.themeMode) {
        ThemeMode.System -> systemInDarkTheme
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val seed = Color(appearance.seedColorArgb.toInt())
    val scheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = appearance.amoledDark,
        style = appearance.paletteFlavor.toPaletteStyle(),
        contrastLevel = if (appearance.highContrast) HIGH_CONTRAST else NORMAL_CONTRAST
    )

    val strings = stringsFor(language, systemIsHebrew)
    val spacing = remember(appearance.density) { YomiSpacing.of(appearance.density) }
    val motion = remember(appearance.motion) { YomiMotion.of(appearance.motion) }
    val accents = remember(dark) { YomiAccents.of(dark) }
    val shapes = remember(appearance.shapeStyle) { yomiShapes(appearance.shapeStyle) }
    val typography = remember(appearance.fontScale) { yomiTypography(appearance.fontScale.toFloat()) }

    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, appearance.fontScale) {
        Density(baseDensity.density, baseDensity.fontScale)
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalSpacing provides spacing,
        LocalMotion provides motion,
        LocalAccents provides accents,
        LocalAppearance provides appearance,
        LocalDensity provides density,
        LocalLayoutDirection provides if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = shapes,
            typography = typography,
            content = content
        )
    }
}

private const val HIGH_CONTRAST = 0.6
private const val NORMAL_CONTRAST = 0.0

private fun PaletteFlavor.toPaletteStyle(): PaletteStyle = when (this) {
    PaletteFlavor.Expressive -> PaletteStyle.Expressive
    PaletteFlavor.Vibrant -> PaletteStyle.Vibrant
    PaletteFlavor.TonalSpot -> PaletteStyle.TonalSpot
    PaletteFlavor.Rainbow -> PaletteStyle.Rainbow
    PaletteFlavor.FruitSalad -> PaletteStyle.FruitSalad
    PaletteFlavor.Neutral -> PaletteStyle.Neutral
    PaletteFlavor.Monochrome -> PaletteStyle.Monochrome
    PaletteFlavor.Fidelity -> PaletteStyle.Fidelity
    PaletteFlavor.Content -> PaletteStyle.Content
}

/**
 * Yomi leans round. Even the "sharp" option keeps a couple of dp so nothing
 * ever feels like a spreadsheet.
 */
fun yomiShapes(style: ShapeStyle): Shapes {
    fun r(small: Int, medium: Int, large: Int, xl: Int): Shapes = Shapes(
        extraSmall = RoundedCornerShape(small.dp),
        small = RoundedCornerShape((small + 4).dp),
        medium = RoundedCornerShape(medium.dp),
        large = RoundedCornerShape(large.dp),
        extraLarge = RoundedCornerShape(xl.dp)
    )
    return when (style) {
        ShapeStyle.Soft -> r(10, 18, 26, 34)
        ShapeStyle.Rounded -> r(12, 22, 30, 40)
        ShapeStyle.Pill -> Shapes(
            extraSmall = RoundedCornerShape(50),
            small = RoundedCornerShape(50),
            medium = RoundedCornerShape(28.dp),
            large = RoundedCornerShape(36.dp),
            extraLarge = RoundedCornerShape(48.dp)
        )

        ShapeStyle.Sharp -> Shapes(
            extraSmall = CutCornerShape(0.dp).let { RoundedCornerShape(2.dp) },
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(12.dp)
        )
    }
}

/** The shape used by the big feature surfaces, one step rounder than `large`. */
val Shapes.hero: CornerBasedShape get() = extraLarge

/**
 * A slightly tightened Material scale: display sizes get shorter line heights
 * so the big score reads as one confident block rather than a paragraph.
 */
fun yomiTypography(scale: Float): Typography {
    val base = Typography()
    fun scaleUp(style: androidx.compose.ui.text.TextStyle) = style.copy(
        fontSize = style.fontSize * scale,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight
    )
    return Typography(
        displayLarge = scaleUp(base.displayLarge.copy(lineHeight = 60.sp)),
        displayMedium = scaleUp(base.displayMedium.copy(lineHeight = 50.sp)),
        displaySmall = scaleUp(base.displaySmall),
        headlineLarge = scaleUp(base.headlineLarge),
        headlineMedium = scaleUp(base.headlineMedium),
        headlineSmall = scaleUp(base.headlineSmall),
        titleLarge = scaleUp(base.titleLarge),
        titleMedium = scaleUp(base.titleMedium),
        titleSmall = scaleUp(base.titleSmall),
        bodyLarge = scaleUp(base.bodyLarge),
        bodyMedium = scaleUp(base.bodyMedium),
        bodySmall = scaleUp(base.bodySmall),
        labelLarge = scaleUp(base.labelLarge),
        labelMedium = scaleUp(base.labelMedium),
        labelSmall = scaleUp(base.labelSmall)
    )
}

private val androidx.compose.ui.unit.TextUnit.isSpecified: Boolean
    get() = this != androidx.compose.ui.unit.TextUnit.Unspecified
