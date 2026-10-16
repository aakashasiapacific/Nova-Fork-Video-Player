package com.aakash.novafork.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    /** Rating star. */
    val Gold = Color(0xFFFFC24B)
    /** Watched check / success. */
    val Mint = Color(0xFF5FD49A)
    /** Video player chrome sits on black. */
    val PlayerScrim = Color(0xB3000000)
    val PlayerSurface = Color(0xE6111318)
}

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A5B),
    onPrimary = Color(0xFF2E1000),
    primaryContainer = Color(0xFF5C2611),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFB9C3FF),
    onSecondary = Color(0xFF1C2559),
    secondaryContainer = Color(0xFF2A3263),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFFFFC24B),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0B0D12),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1E222C),
    onSurfaceVariant = Color(0xFFB4B9C6),
    surfaceTint = Color(0xFFFF8A5B),
    inverseSurface = Color(0xFFE8EAF0),
    inverseOnSurface = Color(0xFF1A1D24),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3B414E),
    outlineVariant = Color(0xFF2A2F3A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2F3A),
    surfaceDim = Color(0xFF0B0D12),
    surfaceContainerLowest = Color(0xFF07080C),
    surfaceContainerLow = Color(0xFF111419),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1C2029),
    surfaceContainerHighest = Color(0xFF242934),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFB4441A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF4A55A0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF041463),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF8F8FB),
    onBackground = Color(0xFF15181E),
    surface = Color(0xFFF8F8FB),
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFE3E5EC),
    onSurfaceVariant = Color(0xFF454A55),
    surfaceTint = Color(0xFFB4441A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF757B87),
    outlineVariant = Color(0xFFC5C8D1),
    surfaceBright = Color(0xFFF8F8FB),
    surfaceDim = Color(0xFFD9DAE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F6),
    surfaceContainer = Color(0xFFECEDF2),
    surfaceContainerHigh = Color(0xFFE6E7EC),
    surfaceContainerHighest = Color(0xFFE0E1E7),
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

@Composable
fun NovaTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = NovaTypography,
        content = content,
    )
}

/** The player always uses the dark scheme. */
@Composable
fun NovaPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = NovaTypography, content = content)
}
