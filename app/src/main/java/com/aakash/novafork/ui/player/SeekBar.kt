package com.aakash.novafork.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.model.GestureFeedback
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import kotlinx.coroutines.delay
import kotlin.math.abs

/** How long a committed seek may keep the thumb at its target while the player catches up. */
private const val PENDING_SEEK_MS = 2_000L

/**
 * Seek slider with the position on the left and the duration (or "−remaining", tap to switch)
 * on the right. Disabled for unseekable media; live streams (no duration) show "LIVE".
 */
@Composable
internal fun PlayerSeekBar(
    state: PlayerUiState,
    actions: PlayerActions,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val duration = state.durationMs
    val live = duration <= 0L
    val enabled = state.seekable && !live

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // After a commit the thumb stays at the target until playback gets there, instead of jumping back.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingSeekMs) {
        if (pendingSeekMs != null) {
            delay(PENDING_SEEK_MS)
            pendingSeekMs = null
        }
    }

    val dragging = dragFraction
    val pending = pendingSeekMs
    val shownMs = when {
        live -> state.positionMs
        dragging != null -> (dragging * duration).toLong()
        else -> (state.feedback as? GestureFeedback.Seek)?.targetMs
            ?: pending?.takeIf { abs(it - state.positionMs) > 1_000 }
            ?: state.positionMs
    }.coerceAtLeast(0L)
    val fraction = if (live) 0f else (shownMs.toFloat() / duration).coerceIn(0f, 1f)

    val labelStyle = (if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
        .merge(TextStyle(fontFeatureSettings = "tnum"))
    val labelWidth = if (large) 64.dp else 52.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 8.dp),
    ) {
        Text(
            text = Formats.clock(shownMs),
            style = labelStyle,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = labelWidth),
        )
        SeekSlider(
            fraction = fraction,
            enabled = enabled,
            large = large,
            onValueChange = { value ->
                dragFraction = value
                actions.scrub((value * duration).toLong(), commit = false)
            },
            onValueChangeFinished = {
                val final = dragFraction
                if (final != null) {
                    val target = (final * duration).toLong()
                    pendingSeekMs = target
                    actions.scrub(target, commit = true)
                }
                dragFraction = null
            },
            modifier = Modifier.weight(1f),
        )
        if (live) {
            LiveBadge(large = large, modifier = Modifier.widthIn(min = labelWidth))
        } else {
            Text(
                text = if (showRemaining) formatRemaining(shownMs, duration) else Formats.clock(duration),
                style = labelStyle,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = labelWidth)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClickLabel = "Switch between duration and time left", onClick = onToggleRemaining)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SeekSlider(
    fraction: Float,
    enabled: Boolean,
    large: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val active = dragged || pressed
    val thumbSize by animateDpAsState(if (active) 20.dp else 14.dp, label = "seekThumb")
    val trackHeight = if (large) 6.dp else 4.dp
    val activeColor = MaterialTheme.colorScheme.primary

    Slider(
        value = fraction,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        // Fixed-size slot: the thumb grows inside it so the track never changes length mid-drag.
        thumb = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (enabled) {
                    Box(
                        Modifier
                            .size(thumbSize)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = range.endInclusive - range.start
            val progress = if (span > 0f) ((sliderState.value - range.start) / span).coerceIn(0f, 1f) else 0f
            val filled = if (enabled) activeColor else Color.White.copy(alpha = 0.38f)
            val rest = Color.White.copy(alpha = if (enabled) 0.3f else 0.15f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight),
            ) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(color = rest, cornerRadius = radius)
                val filledWidth = size.width * progress
                if (filledWidth > 0f) {
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - filledWidth else 0f
                    drawRoundRect(
                        color = filled,
                        topLeft = Offset(left, 0f),
                        size = Size(filledWidth, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        },
    )
}

@Composable
private fun LiveBadge(large: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(Color(0xFFFF4D4D), CircleShape),
        )
        Text(
            text = "LIVE",
            style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}
