package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** What the HUD shows: a big glyph over a short line ("Added to Watchlist"). */
internal data class HudMessage(@DrawableRes val icon: Int, val text: String, val id: Long)

/** The app's one HUD; [show] replaces whatever it is showing. */
internal class HudState {
    var message by mutableStateOf<HudMessage?>(null)
        private set
    private var next = 0L

    fun show(@DrawableRes icon: Int, text: String) {
        message = HudMessage(icon, text, next++)
    }
}

/** The HUD of the screen it is shown on; null where there is none (previews, tests). */
internal val LocalHud = staticCompositionLocalOf<HudState?> { null }

/**
 * iOS's confirmation HUD: a dark rounded square in the middle of the screen with a big glyph and
 * a line under it, popping in, staying a moment and fading away. Taps go through to the page.
 */
@Composable
internal fun IosHudHost(state: HudState, modifier: Modifier = Modifier) {
    val message = state.message
    var shown by remember { mutableStateOf<HudMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            visible = true
            delay(HUD_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 600f), initialScale = 0.8f) + fadeIn(tween(120)),
        exit = scaleOut(tween(260), targetScale = 0.9f) + fadeOut(tween(260)),
    ) {
        val current = shown ?: return@AnimatedVisibility
        Column(
            Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HudColor)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NovaIcon(current.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text(
                current.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private val HudColor = Color(0xE62C2C2E)
private const val HUD_MS = 1_300L
