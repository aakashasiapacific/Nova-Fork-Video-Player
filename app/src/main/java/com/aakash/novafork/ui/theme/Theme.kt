package com.aakash.novafork.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.data.ThemeMode

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Colours that are the same in both themes. */
object NovaColors {
    /** Brand gradient (launcher icon): amber → coral pink. */
    val Amber = Color(0xFFFFB347)
    val Coral = Color(0xFFFF5E7E)
    /**
     * iOS 26/27 systemBlue in dark mode: the accent everywhere, Audio included, as Apple Music
     * itself turned blue in iOS 27.2 (owner).
     */
    val IosBlue = IosPalette.Blue
    /** Rating star. */
    val Gold = Color(0xFFFFC24B)
    /** Watched check / success. */
    val Mint = Color(0xFF5FD49A)
    /** Video player chrome sits on black. */
    val PlayerScrim = Color(0xB3000000)
    val PlayerSurface = Color(0xE6111318)
}

/**
 * iOS 26/27's dark-mode system colours (Apple brightened and shifted the palette in iOS 26; iOS 27
 * keeps it): for accents, icon tiles and states.
 */
object IosPalette {
    val Red = Color(0xFFFF4245)
    val Orange = Color(0xFFFF9230)
    val Yellow = Color(0xFFFFD600)
    val Green = Color(0xFF30D158)
    val Mint = Color(0xFF00DAC3)
    val Teal = Color(0xFF00D2E0)
    val Cyan = Color(0xFF3CD3FE)
    val Blue = Color(0xFF0091FF)
    val Indigo = Color(0xFF6D7CFF)
    val Purple = Color(0xFFDB34F2)
    val Pink = Color(0xFFFF375F)
    val Brown = Color(0xFFB78A66)
    val Gray = Color(0xFF8E8E93)
}

/**
 * The one theme (decided by the owner): the Apple TV app's dark look on pure black, with iOS
 * blue (iOS 26/27 dark-mode systemBlue, #0091FF) as the accent: links, switches, buttons, progress. The
 * hero keeps Apple's white Play pill and white/grey dots, and the tab bar its white selected tab;
 * greys are iOS's dark system greys.
 */
private val DarkScheme: ColorScheme = darkColorScheme(
    primary = NovaColors.IosBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF003A6B),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFFAEAEB2),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFFC24B),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceTint = Color(0xFF000000),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Color(0xFF0088FF),
    error = IosPalette.Red,
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF2C2C2E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
)

private fun ios(size: Float, line: Float, weight: FontWeight, tracking: Float): TextStyle = Base.bodyLarge.copy(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val Base = Typography()

/**
 * iOS's text styles (dark Settings / Apple TV / Apple Music) set in Inter: Large Title 34 bold,
 * Title 1 28, Title 2 22, Title 3 20, Headline 17 semibold, Body 17, Subheadline 15, Footnote 13,
 * Caption 12 and 11. Nothing is heavier than bold. Inter runs wider than SF Pro, so every size
 * is tracked a little tighter than Apple's table to keep the same line lengths.
 */
val NovaTypography = Typography(
    displayLarge = ios(40f, 46f, FontWeight.Bold, -0.8f),
    displayMedium = ios(36f, 42f, FontWeight.Bold, -0.7f),
    displaySmall = ios(34f, 41f, FontWeight.Bold, -0.6f),
    headlineLarge = ios(34f, 41f, FontWeight.Bold, -0.6f),
    headlineMedium = ios(28f, 34f, FontWeight.Bold, -0.5f),
    headlineSmall = ios(22f, 28f, FontWeight.Bold, -0.35f),
    titleLarge = ios(20f, 25f, FontWeight.SemiBold, -0.3f),
    titleMedium = ios(17f, 22f, FontWeight.SemiBold, -0.3f),
    titleSmall = ios(15f, 20f, FontWeight.SemiBold, -0.2f),
    bodyLarge = ios(17f, 22f, FontWeight.Normal, -0.3f),
    bodyMedium = ios(15f, 20f, FontWeight.Normal, -0.2f),
    bodySmall = ios(13f, 18f, FontWeight.Normal, -0.1f),
    labelLarge = ios(15f, 20f, FontWeight.SemiBold, -0.2f),
    labelMedium = ios(12f, 16f, FontWeight.Medium, 0f),
    labelSmall = ios(11f, 13f, FontWeight.Medium, 0.05f),
)

/**
 * Dark only, whatever [themeMode] says: the owner chose one AMOLED-black theme. The parameter
 * stays so older callers and saved settings keep working.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun NovaTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = NovaTypography) { IosTouch(content) }
}

/** The player always uses the dark scheme. */
@Composable
fun NovaPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = NovaTypography) { IosTouch(content) }
}

/**
 * The Audio tab's look. It had Apple Music's red; iOS 27.2's Music went blue, and so did ours
 * (owner), so it is now the app's own theme. Kept so the Audio screens keep one place to theme.
 */
@Composable
fun AudioTheme(content: @Composable () -> Unit) {
    content()
}
