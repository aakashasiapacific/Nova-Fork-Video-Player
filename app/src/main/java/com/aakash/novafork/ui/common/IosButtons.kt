package com.aakash.novafork.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.TextButton as M3TextButton

/*
 * Material's buttons with iOS's press instead of Android's ripple (owner): the button fades
 * while held and comes back as it is let go. Same parameters as Material's, so call sites read
 * the same; the ripple itself is off app-wide (IosTouch).
 */

@Composable
fun IosIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    M3IconButton(onClick, modifier.iosPressFade(source, ICON_PRESSED_ALPHA), enabled, colors, source, content)
}

@Composable
fun IosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    M3TextButton(onClick, modifier.iosPressFade(source, TEXT_PRESSED_ALPHA), enabled, shape, colors, elevation, border, contentPadding, source, content)
}

@Composable
fun IosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    M3Button(onClick, modifier.iosPressFade(source, FILLED_PRESSED_ALPHA), enabled, shape, colors, elevation, border, contentPadding, source, content)
}

@Composable
fun IosFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    M3FilledTonalButton(onClick, modifier.iosPressFade(source, FILLED_PRESSED_ALPHA), enabled, shape, colors, elevation, border, contentPadding, source, content)
}

/** Fades to [pressedAlpha] at once while [source] is pressed, and back over a moment on release. */
@Composable
fun Modifier.iosPressFade(source: InteractionSource, pressedAlpha: Float): Modifier {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(source) {
        var pressed = 0
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed++
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
            }
            launch { if (pressed > 0) alpha.animateTo(pressedAlpha, snap()) else alpha.animateTo(1f, tween(RELEASE_MS)) }
        }
    }
    return graphicsLayer { this.alpha = alpha.value }
}

private const val ICON_PRESSED_ALPHA = 0.35f
private const val TEXT_PRESSED_ALPHA = 0.35f
private const val FILLED_PRESSED_ALPHA = 0.7f
private const val RELEASE_MS = 220
