package com.aakash.novafork.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * iOS's touch feedback instead of Android's ripple (owner: no "fake iOS"): whatever is pressed
 * lights up evenly at once and fades back when let go. Material's own buttons can't take a
 * custom indication, so their ripple is kept but made faint.
 */
@Composable
fun IosTouch(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides IosHighlight,
        LocalRippleConfiguration provides FaintRipple,
        content = content,
    )
}

private val FaintRipple = RippleConfiguration(
    color = Color.White,
    rippleAlpha = RippleAlpha(draggedAlpha = 0.06f, focusedAlpha = 0.06f, hoveredAlpha = 0.03f, pressedAlpha = 0.08f),
)

/** The pressed highlight: a soft white wash over the pressed thing, no ripple. */
private object IosHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlightNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class HighlightNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                }
                launch {
                    if (pressed > 0) strength.animateTo(1f, snap()) else strength.animateTo(0f, tween(220))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val value = strength.value
        if (value > 0f) drawRect(Color.White.copy(alpha = HIGHLIGHT_ALPHA * value))
    }

    private companion object {
        const val HIGHLIGHT_ALPHA = 0.12f
    }
}
