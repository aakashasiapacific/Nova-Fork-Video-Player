package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * SF Symbols' "replace" effect: when [icon] changes (play to pause, bookmark to filled), the old
 * glyph shrinks away as the new one grows in its place, instead of a jump cut.
 */
@Composable
fun SymbolSwap(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    AnimatedContent(
        targetState = icon,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.6f, stiffness = 600f), initialScale = 0.4f) + fadeIn(tween(140))) togetherWith
                (scaleOut(tween(140), targetScale = 0.4f) + fadeOut(tween(110)))
        },
        label = "symbolSwap",
    ) { shown ->
        NovaIcon(shown, contentDescription = contentDescription, tint = tint, modifier = modifier)
    }
}

/**
 * SF Symbols' "bounce": each time [key] changes after the first composition (a heart filled, a
 * bookmark set), the content pops a little bigger and springs back.
 */
fun Modifier.popOnChange(key: Any?): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != last) {
            last = key
            scale.animateTo(PopScale, spring(stiffness = 1_400f))
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

private const val PopScale = 1.28f
