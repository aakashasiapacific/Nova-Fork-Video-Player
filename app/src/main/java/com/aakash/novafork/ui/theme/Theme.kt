package com.aakash.novafork.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.data.ThemeMode

/*
 * Nova Fork's look is the Apple TV app on iOS 26 / iPadOS 26 (docs/APPLE-TV-SPEC.md), set in
 * Inter. Colours are the iOS system colours: a pure black canvas with #1C1C1E grouped cells in
 * dark mode, and #F2F2F7 / #FFFFFF grouped colours in light mode. See docs/DESIGN.md.
 */

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** The iOS semantic colours for one appearance. Read through [NovaColors] in composables. */
@Immutable
data class NovaPalette(
    val dark: Boolean,
    /** Page canvas (systemBackground / systemGroupedBackground). */
    val background: Color,
    /** Inset-grouped rows, About cards, search field in dark mode (secondarySystemGroupedBackground). */
    val cell: Color,
    /** A row while it is pressed. */
    val cellPressed: Color,
    /** Controls raised on a cell (tertiarySystemGroupedBackground). */
    val raised: Color,
    /** Opaque 0.5dp hairlines. */
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    /** Translucent fill for segmented-control tracks and similar (tertiarySystemFill). */
    val fill: Color,
    /** The search field background. */
    val searchField: Color,
    /** Toggle track when off. */
    val toggleOff: Color,
    /** Selected segment of a segmented control. */
    val segmentThumb: Color,
    /** Accent for text buttons, links and checkmarks (systemBlue). */
    val tint: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val purple: Color,
    val pink: Color,
    val teal: Color,
    val indigo: Color,
    val gray: Color,
    /** Dims the page behind sheets and alerts. */
    val scrim: Color,
)

val DarkPalette = NovaPalette(
    dark = true,
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    cellPressed = Color(0xFF3A3A3C),
    raised = Color(0xFF2C2C2E),
    separator = Color(0xFF38383A),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x2EEBEBF5),
    fill = Color(0x3D767680),
    searchField = Color(0xFF1C1C1E),
    toggleOff = Color(0xFF39393D),
    segmentThumb = Color(0xFF636366),
    tint = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    purple = Color(0xFFBF5AF2),
    pink = Color(0xFFFF375F),
    teal = Color(0xFF40C8E0),
    indigo = Color(0xFF5E5CE6),
    gray = Color(0xFF8E8E93),
    scrim = Color(0x73000000),
)

val LightPalette = NovaPalette(
    dark = false,
    background = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    cellPressed = Color(0xFFD1D1D6),
    raised = Color(0xFFF2F2F7),
    separator = Color(0xFFC6C6C8),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    fill = Color(0x1F767680),
    searchField = Color(0xFFE3E3E8),
    toggleOff = Color(0xFFE9E9EA),
    segmentThumb = Color(0xFFFFFFFF),
    tint = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    purple = Color(0xFFAF52DE),
    pink = Color(0xFFFF2D55),
    teal = Color(0xFF30B0C7),
    indigo = Color(0xFF5856D6),
    gray = Color(0xFF8E8E93),
    scrim = Color(0x33000000),
)

val LocalNovaPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Colour tokens. The camelCase members follow the current appearance (dark or light) and can only
 * be read in a composable; read them before entering draw lambdas. The PascalCase members are
 * fixed colours for artwork, media chrome and legacy screens.
 */
object NovaColors {
    // Fixed colours.
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    /** Text and glyphs over artwork and video: always white, whatever the appearance. */
    val OnMedia = Color(0xFFFFFFFF)
    /** Secondary text over artwork and video (#EBEBF5 at 60%). */
    val OnMediaSecondary = Color(0x99EBEBF5)
    /** Thin progress capsules over artwork: white fill on white 20%. */
    val ProgressTrack = Color(0x33FFFFFF)
    /** Page dots: active white, inactive white 35%. */
    val DotInactive = Color(0x59FFFFFF)
    /** Brand gradient (launcher icon): amber to coral pink. */
    val Amber = Color(0xFFFFB347)
    val Coral = Color(0xFFFF5E7E)
    /** Rating star (systemYellow). */
    val Gold = Color(0xFFFFD60A)
    /** Success / "Working" (systemGreen, dark). */
    val Mint = Color(0xFF30D158)
    /** Gradient over video behind the player chrome. */
    val PlayerScrim = Color(0x99000000)
    /** Opaque-enough panel colour for player cards when glass cannot blur (#1C1C1E at 88%). */
    val PlayerSurface = Color(0xE01C1C1E)

    // Appearance-dependent colours.
    val background: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.background
    val cell: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.cell
    val cellPressed: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.cellPressed
    val raised: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.raised
    val separator: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.separator
    val label: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.label
    val secondaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.secondaryLabel
    val tertiaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.tertiaryLabel
    val quaternaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.quaternaryLabel
    val fill: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.fill
    val searchField: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.searchField
    val toggleOff: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.toggleOff
    val tint: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.tint
    val green: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.green
    val red: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.red
    val orange: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.orange
    val yellow: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.yellow
    val purple: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.purple
    val pink: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.pink
    val teal: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.teal
    val indigo: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.indigo
    val gray: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.gray
    val scrim: Color
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.scrim
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalNovaPalette.current.dark
}

private fun ios(size: Float, line: Float, weight: FontWeight, tracking: Float): TextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

/**
 * The iOS Dynamic Type scale (default size) in Inter. Tracking follows SF Pro's so Inter sets as
 * tightly as the original. Colours are not part of the styles: Text takes LocalContentColor
 * (label) unless a colour is passed.
 */
object NovaType {
    val largeTitle = ios(34f, 41f, FontWeight.Bold, -0.4f)
    val title1 = ios(28f, 34f, FontWeight.Bold, -0.4f)
    val title2 = ios(22f, 28f, FontWeight.Bold, -0.3f)
    val title3 = ios(20f, 25f, FontWeight.SemiBold, -0.25f)
    val headline = ios(17f, 22f, FontWeight.SemiBold, -0.4f)
    val body = ios(17f, 22f, FontWeight.Normal, -0.4f)
    val callout = ios(16f, 21f, FontWeight.Normal, -0.3f)
    val subhead = ios(15f, 20f, FontWeight.Normal, -0.2f)
    val subheadSemibold = ios(15f, 20f, FontWeight.SemiBold, -0.2f)
    val footnote = ios(13f, 18f, FontWeight.Normal, -0.08f)
    val footnoteSemibold = ios(13f, 18f, FontWeight.SemiBold, -0.08f)
    val caption1 = ios(12f, 16f, FontWeight.Normal, 0f)
    val caption2 = ios(11f, 13f, FontWeight.Normal, 0.06f)
    val caption2Semibold = ios(11f, 13f, FontWeight.SemiBold, 0.06f)

    /** "EPISODE 3", "SEASON 1": Caption 1 semibold, +0.6 tracking. Uppercase the text yourself (or use SmallCapsText). */
    val smallCaps = ios(12f, 16f, FontWeight.SemiBold, 0.6f)
    /** Phone tab bar labels (Caption 2 semibold). */
    val tabLabel = ios(11f, 13f, FontWeight.SemiBold, 0.06f)
    /** iPad hero and details titles. */
    val heroTitle = ios(48f, 54f, FontWeight.Bold, -0.9f)
    /** Player times: Caption 1 with tabular digits. */
    val monoCaption = ios(12f, 16f, FontWeight.Medium, 0f).copy(fontFeatureSettings = "tnum")
    /** Inline numbers that must not jump while they change (counters, durations). */
    val monoSubhead = ios(15f, 20f, FontWeight.Normal, -0.2f).copy(fontFeatureSettings = "tnum")
}

/**
 * Material typography mapped onto the iOS scale, for plain Text that still reads
 * MaterialTheme.typography: display = hero/large titles, headline = titles, title = headline
 * and subhead weights, body = body/subhead/footnote, label = callout/captions.
 */
val NovaTypography = Typography(
    displayLarge = NovaType.heroTitle,
    displayMedium = ios(40f, 46f, FontWeight.Bold, -0.8f),
    displaySmall = NovaType.largeTitle,
    headlineLarge = NovaType.largeTitle,
    headlineMedium = NovaType.title1,
    headlineSmall = NovaType.title2,
    titleLarge = NovaType.title3,
    titleMedium = NovaType.headline,
    titleSmall = NovaType.subheadSemibold,
    bodyLarge = NovaType.body,
    bodyMedium = NovaType.subhead,
    bodySmall = NovaType.footnote,
    labelLarge = ios(16f, 21f, FontWeight.SemiBold, -0.3f),
    labelMedium = ios(12f, 16f, FontWeight.Medium, 0f),
    labelSmall = ios(11f, 13f, FontWeight.Medium, 0.06f),
)

private fun schemeFor(p: NovaPalette): ColorScheme = if (p.dark) {
    darkColorScheme(
        primary = p.label,
        onPrimary = Color.Black,
        primaryContainer = p.raised,
        onPrimaryContainer = p.label,
        secondary = p.tint,
        onSecondary = Color.White,
        secondaryContainer = p.raised,
        onSecondaryContainer = p.label,
        tertiary = p.yellow,
        onTertiary = Color.Black,
        tertiaryContainer = p.raised,
        onTertiaryContainer = p.label,
        background = p.background,
        onBackground = p.label,
        surface = p.background,
        onSurface = p.label,
        surfaceVariant = p.cell,
        onSurfaceVariant = p.secondaryLabel,
        surfaceTint = Color.Transparent,
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        error = p.red,
        onError = Color.White,
        errorContainer = Color(0xFF3A1311),
        onErrorContainer = p.red,
        outline = p.separator,
        outlineVariant = p.separator,
        scrim = Color.Black,
        surfaceBright = p.raised,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.cell,
        surfaceContainer = p.cell,
        surfaceContainerHigh = p.raised,
        surfaceContainerHighest = p.cellPressed,
    )
} else {
    lightColorScheme(
        primary = p.label,
        onPrimary = Color.White,
        primaryContainer = p.cell,
        onPrimaryContainer = p.label,
        secondary = p.tint,
        onSecondary = Color.White,
        secondaryContainer = p.cell,
        onSecondaryContainer = p.label,
        tertiary = p.orange,
        onTertiary = Color.White,
        tertiaryContainer = p.cell,
        onTertiaryContainer = p.label,
        background = p.background,
        onBackground = p.label,
        surface = p.background,
        onSurface = p.label,
        surfaceVariant = p.cell,
        onSurfaceVariant = p.secondaryLabel,
        surfaceTint = Color.Transparent,
        inverseSurface = Color.Black,
        inverseOnSurface = Color.White,
        error = p.red,
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E3),
        onErrorContainer = p.red,
        outline = p.separator,
        outlineVariant = p.separator,
        scrim = Color.Black,
        surfaceBright = p.cell,
        surfaceDim = p.background,
        surfaceContainerLowest = p.cell,
        surfaceContainerLow = p.cell,
        surfaceContainer = p.cell,
        surfaceContainerHigh = p.raised,
        surfaceContainerHighest = p.cellPressed,
    )
}

private val DarkScheme: ColorScheme = schemeFor(DarkPalette)
private val LightScheme: ColorScheme = schemeFor(LightPalette)

@Composable
private fun NovaThemeFor(palette: NovaPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNovaPalette provides palette) {
        MaterialTheme(
            colorScheme = if (palette.dark) DarkScheme else LightScheme,
            typography = NovaTypography,
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.label, content = content)
        }
    }
}

/** App theme. DARK is the Apple TV look; LIGHT uses the iOS light grouped colours. */
@Composable
fun NovaTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    NovaThemeFor(if (dark) DarkPalette else LightPalette, content)
}

/** The player is always dark. */
@Composable
fun NovaPlayerTheme(content: @Composable () -> Unit) {
    NovaThemeFor(DarkPalette, content)
}
