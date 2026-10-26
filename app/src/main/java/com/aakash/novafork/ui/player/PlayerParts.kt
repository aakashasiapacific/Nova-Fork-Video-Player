package com.aakash.novafork.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.SymbolSwap
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.model.TrackUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Option groups of the phone track sheet and the tablet side panel, in panel order. */
internal enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, ASPECT }

/** Lists that open over the player (phone bottom sheet, tablet side panel), apart from the track options. */
internal enum class PlayerSheet(val title: String) {
    QUEUE("Queue"),
    CAST("Cast to a device"),
}

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

/** "0 ms", "+300 ms", "−1200 ms". */
internal fun formatDelay(delayMs: Long): String = when {
    delayMs > 0 -> "+$delayMs ms"
    delayMs < 0 -> "$MINUS${abs(delayMs)} ms"
    else -> "0 ms"
}

internal fun trackLabel(track: TrackUi): String = if (track.id == DISABLED_TRACK_ID) "Off" else track.name

internal fun subtitlesOn(tracks: List<TrackUi>): Boolean = tracks.any { it.selected && it.id != DISABLED_TRACK_ID }

@DrawableRes
internal fun RepeatMode.iconRes(): Int = when (this) {
    // One UI's play mode button: what happens when this video ends.
    RepeatMode.OFF -> R.drawable.ic_player_mode_off
    RepeatMode.ALL -> R.drawable.ic_player_mode_all
    RepeatMode.ONE -> R.drawable.ic_player_mode_one
}

internal val RepeatMode.label: String
    get() = when (this) {
        RepeatMode.OFF -> "Repeat off"
        RepeatMode.ALL -> "Repeat all"
        RepeatMode.ONE -> "Repeat one"
    }

/** Shuffle only means something with more than one queued video. */
internal val PlayerUiState.canShuffle: Boolean get() = queue.size > 1

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
    IosIconButton(onClick = onClick, modifier = modifier.size(buttonSize), enabled = enabled) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

/** One entry of the player's overflow menu. */
internal class PlayerMenuItem(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

/** The ⋮ button and its menu of [items]. */
@Composable
internal fun PlayerMoreMenu(
    items: List<PlayerMenuItem>,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    @DrawableRes icon: Int = R.drawable.ic_more_vert,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PlayerIconButton(
            icon = icon,
            contentDescription = "More options",
            onClick = { open = true },
            buttonSize = buttonSize,
            iconSize = iconSize,
        )
        NovaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                NovaMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** Cast icon; the "connected" glyph in the accent colour while the video plays on a cast device. */
@Composable
internal fun CastButton(
    castingTo: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val casting = castingTo != null
    IosIconButton(onClick = onClick, modifier = modifier.size(buttonSize)) {
        Icon(
            painter = painterResource(if (casting) R.drawable.ic_cast_connected else R.drawable.ic_cast),
            contentDescription = if (casting) "Casting to $castingTo" else "Cast",
            modifier = Modifier.size(iconSize),
            tint = if (casting) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/** On/off icon (shuffle, repeat): the accent colour and a dot underneath while on, dimmed while off. */
@Composable
internal fun PlayerToggleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    IosIconButton(
        onClick = onClick,
        modifier = modifier
            .size(buttonSize)
            .semantics { stateDescription = if (checked) "On" else "Off" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (checked) accent else LocalContentColor.current.copy(alpha = 0.7f),
            )
            if (checked) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 7.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}

/**
 * Big round play/pause; a spinner rings it while buffering so the centre never shows two things.
 * [finished] (played to the end, nothing after it): Replay, as Samsung's player shows it.
 */
@Composable
internal fun PlayPauseButton(
    isPlaying: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    finished: Boolean = false,
) {
    Box(modifier.size(size + 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                // A dark see-through disc behind the glyph, as Samsung's player draws it.
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // SF Symbols' replace: play, pause and replay shrink into one another rather than cut.
            SymbolSwap(
                icon = when {
                    finished -> R.drawable.ic_replay
                    isPlaying -> R.drawable.ic_pause
                    else -> R.drawable.ic_play_arrow
                },
                contentDescription = when {
                    finished -> "Replay"
                    isPlaying -> "Pause"
                    else -> "Play"
                },
                modifier = Modifier.size(size * if (finished) 0.46f else 0.5f),
                tint = Color.White,
            )
        }
        if (buffering) {
            PlayerSpinner(modifier = Modifier.fillMaxSize())
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
    IosIconButton(onClick = onClick, modifier = modifier.size(buttonSize), enabled = enabled) {
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

/**
 * The title beside the back arrow, as Samsung's player shows it, with its details small and grey
 * beneath.
 */
@Composable
internal fun PlayerTitle(state: PlayerUiState, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(
            text = state.title,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = state.subtitle
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Samsung's loading ring (owner: not iOS's spokes): a thin white arc turning round a faint ring,
 * growing and shrinking as it goes, as One UI's progress circle does.
 */
@Composable
internal fun PlayerSpinner(modifier: Modifier = Modifier, strokeWidth: Dp = 3.dp, color: Color = Color.White) {
    val turning = rememberInfiniteTransition(label = "playerSpinner")
    val turn by turning.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "spinnerTurn",
    )
    val sweep by turning.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "spinnerSweep",
    )
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        drawArc(color, turn - 90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private const val SPIN_MS = 1_100
private const val SWEEP_MS = 900
