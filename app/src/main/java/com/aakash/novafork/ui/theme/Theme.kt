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
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Colours that are the same in both themes. */
object NovaColors {
    /** Brand gradient (launcher icon): amber → coral pink. */
    val Amber = Color(0xFFFFB347)
    val Coral = Color(0xFFFF5E7E)
    /** iOS systemBlue in dark mode: the app's accent. */
    val IosBlue = Color(0xFF0A84FF)
    /** Rating star. */
    val Gold = Color(0xFFFFC24B)
    /** Watched check / success. */
    val Mint = Color(0xFF5FD49A)
    /** Video player chrome sits on black. */
    val PlayerScrim = Color(0xB3000000)
    val PlayerSurface = Color(0xE6111318)
}

/**
 * The one theme (decided by the owner): the Apple TV app's dark look on pure black, with iOS
 * blue (dark-mode systemBlue, #0A84FF) as the accent: links, switches, buttons, progress. The
 * hero keeps Apple's white Play pill and white/grey dots, and the tab bar its white selected tab;
 * greys are iOS's dark system greys.
 */
private val DarkScheme: ColorScheme = darkColorScheme(
    primary = NovaColors.IosBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0B3A6E),
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
    inversePrimary = Color(0xFF007AFF),
    error = Color(0xFFFF6961),
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

private fun TextStyle.inter(weight: FontWeight? = null, tracking: Float? = null): TextStyle = copy(
    fontFamily = Inter,
    fontWeight = weight ?: fontWeight,
    letterSpacing = tracking?.sp ?: letterSpacing,
)

private val Base = Typography()

val NovaTypography = Typography(
    displayLarge = Base.displayLarge.inter(FontWeight.ExtraBold, -1f),
    displayMedium = Base.displayMedium.inter(FontWeight.ExtraBold, -0.8f),
    displaySmall = Base.displaySmall.inter(FontWeight.Bold, -0.5f),
    headlineLarge = Base.headlineLarge.inter(FontWeight.Bold, -0.4f),
    headlineMedium = Base.headlineMedium.inter(FontWeight.Bold, -0.3f),
    headlineSmall = Base.headlineSmall.inter(FontWeight.SemiBold, -0.2f),
    titleLarge = Base.titleLarge.inter(FontWeight.SemiBold, -0.1f),
    titleMedium = Base.titleMedium.inter(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.inter(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.inter(),
    bodyMedium = Base.bodyMedium.inter(),
    bodySmall = Base.bodySmall.inter(),
    labelLarge = Base.labelLarge.inter(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.inter(FontWeight.Medium),
    labelSmall = Base.labelSmall.inter(FontWeight.Medium),
)

/**
 * Dark only, whatever [themeMode] says: the owner chose one AMOLED-black theme. The parameter
 * stays so older callers and saved settings keep working.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun NovaTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = NovaTypography,
        content = content,
    )
}

/** The player always uses the dark scheme. */
@Composable
fun NovaPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = NovaTypography, content = content)
}
