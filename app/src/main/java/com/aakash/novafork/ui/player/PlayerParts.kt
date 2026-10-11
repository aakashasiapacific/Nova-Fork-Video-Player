package com.aakash.novafork.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.model.TrackUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Option groups of the phone track sheet and the tablet side panel, in panel order. */
internal enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, ASPECT }

/** Physical third of the screen a tap hit (not mirrored for RTL: right always seeks forward). */
internal enum class TapZone { LEFT, CENTER, RIGHT }

/** The "+20s" bubble of a double-tap seek series; [totalMs] grows while taps keep coming on that side. */
@Immutable
internal data class SeekBubble(val forward: Boolean, val totalMs: Long)

internal val SpeedPresets: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** libVLC's "Disable" pseudo track. */
internal const val DISABLED_TRACK_ID = -1

private const val MINUS = "−"

/** "1.0×", "1.25×", "0.5×" — ASCII digits in every locale. */
internal fun formatRate(rate: Float): String {
    val hundredths = (rate * 100).roundToInt().coerceAtLeast(0)
    val fraction = hundredths % 100
    val decimals = if (fraction % 10 == 0) (fraction / 10).toString() else fraction.toString().padStart(2, '0')
    return "${hundredths / 100}.$decimals×"
}

internal fun isRate(rate: Float, preset: Float): Boolean = abs(rate - preset) < 0.01f

/** "+0:30", "−1:05". */
internal fun formatSignedClock(deltaMs: Long): String =
    (if (deltaMs < 0) MINUS else "+") + Formats.clock(abs(deltaMs))

/** "−0:42:10" for the remaining-time label. */
internal fun formatRemaining(positionMs: Long, durationMs: Long): String =
    MINUS + Formats.clock((durationMs - positionMs).coerceAtLeast(0))

/** "+20s" / "−10s" in the double-tap bubble. */
internal fun formatSeekSeconds(forward: Boolean, totalMs: Long): String =
    (if (forward) "+" else MINUS) + "${totalMs / 1000}s"

/** "0 ms", "+300 ms", "−1200 ms". */
internal fun formatDelay(delayMs: Long): String = when {
    delayMs > 0 -> "+$delayMs ms"
    delayMs < 0 -> "$MINUS${abs(delayMs)} ms"
    else -> "0 ms"
}

internal fun trackLabel(track: TrackUi): String = if (track.id == DISABLED_TRACK_ID) "Off" else track.name

internal fun subtitlesOn(tracks: List<TrackUi>): Boolean = tracks.any { it.selected && it.id != DISABLED_TRACK_ID }

/** White icon button for chrome drawn over video; tint follows LocalContentColor (white in the player). */
@Composable
internal fun PlayerIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier.size(buttonSize), enabled = enabled) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

/** Big round play/pause; a spinner rings it while buffering so the centre never shows two things. */
@Composable
internal fun PlayPauseButton(
    isPlaying: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size + 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(size * 0.6f),
                tint = Color.White,
            )
        }
        if (buffering) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize(), color = Color.White, strokeWidth = 3.dp)
        }
    }
}

@Composable
internal fun SeekStepButton(
    forward: Boolean,
    stepMs: Long,
    onClick: () -> Unit,
    enabled: Boolean,
    buttonSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val seconds = stepMs / 1000
    IconButton(onClick = onClick, modifier = modifier.size(buttonSize), enabled = enabled) {
        SeekStepIcon(
            forward = forward,
            seconds = seconds,
            size = iconSize,
            contentDescription = if (forward) "Forward $seconds seconds" else "Back $seconds seconds",
        )
    }
}

/**
 * ic_replay_10 / ic_forward_10 for the default step. Other steps draw the number inside ic_replay
 * (mirrored for forward), which is how the Material "replay N" glyphs are built.
 */
@Composable
internal fun SeekStepIcon(forward: Boolean, seconds: Long, size: Dp, contentDescription: String?) {
    if (seconds == 10L) {
        Icon(
            painter = painterResource(if (forward) R.drawable.ic_forward_10 else R.drawable.ic_replay_10),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
    } else {
        // Sized in dp, not sp: the digits must fit inside the arrow whatever the font scale.
        val digitSize = with(LocalDensity.current) { (size * 0.3f).toSp() }
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_replay),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (forward) scaleX = -1f },
            )
            Text(
                text = seconds.toString(),
                fontSize = digitSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = size * 0.05f),
            )
        }
    }
}

/** Gradients behind the top and bottom bars so white chrome reads over bright video. */
@Composable
internal fun BoxScope.ControlScrims(top: Dp, bottom: Dp) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(top)
            .background(Brush.verticalGradient(listOf(NovaColors.PlayerScrim, Color.Transparent))),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(bottom)
            .background(Brush.verticalGradient(listOf(Color.Transparent, NovaColors.PlayerScrim))),
    )
}

/**
 * The last non-null [value], so content can stay on screen while its exit animation runs after
 * the state that produced it has gone.
 */
@Composable
internal fun <T : Any> rememberLatestNonNull(value: T?): T? {
    val holder = remember { LatestHolder<T>() }
    if (value != null) holder.value = value
    return value ?: holder.value
}

private class LatestHolder<T : Any> {
    var value: T? = null
}
