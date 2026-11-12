package com.aakash.novafork.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.common.Motion
import com.aakash.novafork.ui.common.RollingText
import com.aakash.novafork.ui.model.GestureFeedback
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import kotlin.math.abs
import kotlinx.coroutines.delay

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
    /** Portrait (Samsung): both times on a line above the bar, at its two ends. */
    stacked: Boolean = false,
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

    // Where the file's chapters start, as parts of the bar: small gaps in the track.
    val chapterStarts = remember(state.chapters) { state.chapters.map { it.startMs }.filter { it > 0L } }
    val marks = remember(chapterStarts, duration) {
        if (duration > 0L) chapterStarts.map { it.toFloat() / duration }.filter { it < 1f } else emptyList()
    }
    // Intro, recap and credits chapters: a slightly brighter stretch of the track. Display only.
    val highlights = remember(state.chapters, duration) { markedRanges(state.chapters, duration) }
    // iOS's scrubbing ticks: a light one each time the drag passes a 10-second mark or a chapter.
    val haptics = LocalHapticFeedback.current
    val lastTick = remember { longArrayOf(0L) }

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

    // The times' digits roll as they change (iOS), except while the bar is dragged, when they
    // follow the finger.
    val rolling = dragging == null
    val elapsed: @Composable () -> Unit = {
        TimeLabel(
            text = Formats.clock(shownMs),
            style = labelStyle,
            rolling = rolling,
            alignment = if (stacked) Alignment.CenterStart else Alignment.Center,
            modifier = if (stacked) Modifier else Modifier.widthIn(min = labelWidth),
        )
    }
    val end: @Composable () -> Unit = {
        if (live) {
            LiveBadge(large = large, modifier = Modifier.widthIn(min = labelWidth))
        } else {
            TimeLabel(
                text = if (showRemaining) formatRemaining(shownMs, duration) else Formats.clock(duration),
                style = labelStyle,
                rolling = rolling,
                alignment = Alignment.Center,
                modifier = Modifier
                    .then(if (stacked) Modifier else Modifier.widthIn(min = labelWidth))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClickLabel = "Switch between duration and time left", onClick = onToggleRemaining)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
        SeekSlider(
            fraction = fraction,
            enabled = enabled,
            large = large,
            marks = marks,
            highlights = highlights,
            onValueChange = { value ->
                val target = (value * duration).toLong()
                val from = dragFraction?.let { (it * duration).toLong() }
                val now = SystemClock.uptimeMillis()
                // At most one tick per [TICK_GAP_MS]: a fast drag over a long film passes many marks a frame.
                if (from != null && passesMark(from, target, chapterStarts) && now - lastTick[0] >= TICK_GAP_MS) {
                    lastTick[0] = now
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                dragFraction = value
                actions.scrub(target, commit = false)
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
            modifier = sliderModifier.onGloballyPositioned { bubbleCoords[1] = it },
        )
    }

    // Samsung: while the bar is dragged, the time it points at shows large above it.
    val bigTime = remember { Animatable(0f) }
    // The large time follows the finger along the bar, kept within the bar's ends (iOS).
    // [0] is this seek bar's box, [1] the slider; read at draw time, after layout.
    val bubbleCoords = remember { arrayOfNulls<LayoutCoordinates>(2) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(dragging != null) { bigTime.animateTo(if (dragging != null) 1f else 0f, Motion.content()) }
    val bigTimeLabel: @Composable BoxScope.() -> Unit = {
        if (bigTime.value > 0f) {
            // The chapter the finger is in, above the time, when the file has named chapters.
            val chapterName = state.chapters.lastOrNull { it.startMs <= shownMs }?.name?.takeIf { it.isNotBlank() && state.chapters.size > 1 }
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    if (chapterName != null) {
                        withStyle(androidx.compose.ui.text.SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))) {
                            append(chapterName)
                        }
                        append("\n")
                    }
                    append(Formats.clock(shownMs))
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.merge(TextStyle(fontFeatureSettings = "tnum")),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .graphicsLayer {
                        alpha = bigTime.value
                        val box = bubbleCoords[0]
                        val bar = bubbleCoords[1]
                        translationX = if (box != null && bar != null && box.isAttached && bar.isAttached) {
                            val barLeft = box.localPositionOf(bar, Offset.Zero).x
                            val barWidth = bar.size.width.toFloat()
                            val along = if (rtl) 1f - fraction else fraction
                            val centre = barLeft + along * barWidth
                            val maxLeft = (barLeft + barWidth - size.width).coerceAtLeast(barLeft)
                            (centre - size.width / 2f).coerceIn(barLeft, maxLeft)
                        } else {
                            (box?.size?.width?.toFloat()?.minus(size.width) ?: 0f) / 2f
                        }
                        translationY = -BigTimeRise.toPx() - (1f - bigTime.value) * 6.dp.toPx() -
                            (if (chapterName != null) ChapterLineRise.toPx() else 0f)
                    },
            )
        }
    }

    Box(modifier.onGloballyPositioned { bubbleCoords[0] = it }) {
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    elapsed()
                    Spacer(Modifier.weight(1f))
                    end()
                }
                slider(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 8.dp),
            ) {
                elapsed()
                slider(Modifier.weight(1f))
                end()
            }
        }
        bigTimeLabel()
    }
}

/** Whether a drag from [fromMs] to [toMs] passed a 10-second mark or a chapter's start. */
private fun passesMark(fromMs: Long, toMs: Long, chapterStarts: List<Long>): Boolean {
    if (fromMs == toMs) return false
    val low = minOf(fromMs, toMs)
    val high = maxOf(fromMs, toMs)
    if (low / TICK_STEP_MS != high / TICK_STEP_MS) return true
    return chapterStarts.any { it in (low + 1)..high }
}

/** The scrubbing ticks: one every 10 seconds of the video, at most one per 60 ms of dragging. */
private const val TICK_STEP_MS = 10_000L
private const val TICK_GAP_MS = 60L

/** A time beside the seek bar, white; with [rolling] its digits roll as they change. */
@Composable
private fun TimeLabel(text: String, style: TextStyle, rolling: Boolean, alignment: Alignment, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = alignment) {
        if (rolling) {
            RollingText(text, style = style, color = Color.White)
        } else {
            Text(text, style = style, color = Color.White, maxLines = 1)
        }
    }
}

/** The extra rise when a chapter's name sits above the scrubbing time. */
private val ChapterLineRise = 18.dp

/** How far above the seek bar's top the large scrubbing time sits. */
private val BigTimeRise = 40.dp

@Composable
private fun SeekSlider(
    fraction: Float,
    enabled: Boolean,
    large: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where chapters start, 0 to 1 along the bar: cut as small gaps in the track. */
    marks: List<Float> = emptyList(),
    /** Stretches of the bar (0 to 1) drawn a little brighter: intro, recap, credits. */
    highlights: List<ClosedFloatingPointRange<Float>> = emptyList(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val active = dragged || pressed
    // Samsung's seek bar (owner): a thin white line on a grey track with a round white knob;
    // while dragged the knob goes and the bar swells into a thick rounded bar, filled white.
    val trackHeight by animateDpAsState(
        targetValue = if (active) ActiveTrack else if (large) 4.dp else 3.dp,
        animationSpec = Motion.press(),
        label = "seekTrack",
    )
    val knob by animateDpAsState(
        targetValue = when {
            active -> 0.dp
            else -> if (large) 22.dp else 20.dp
        },
        label = "seekKnob",
    )

    Slider(
        value = fraction,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (enabled && knob > 0.dp) Box(Modifier.size(knob).clip(CircleShape).background(Color.White))
            }
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = range.endInclusive - range.start
            val progress = if (span > 0f) ((sliderState.value - range.start) / span).coerceIn(0f, 1f) else 0f
            val filled = Color.White.copy(alpha = if (!enabled) 0.38f else if (active) 1f else 0.85f)
            val rest = Color.White.copy(alpha = if (!enabled) 0.15f else if (active) 0.45f else 0.35f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    // Its own layer, so the chapter gaps can be cut through the track.
                    .graphicsLayer { if (marks.isNotEmpty()) compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(color = rest, cornerRadius = radius)
                if (enabled) {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    highlights.forEach { range ->
                        val from = size.width * (if (rtl) 1f - range.endInclusive else range.start)
                        drawRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(from, 0f),
                            size = Size(size.width * (range.endInclusive - range.start), size.height),
                        )
                    }
                }
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
                if (marks.isNotEmpty()) {
                    val gap = ChapterGap.toPx()
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    marks.forEach { mark ->
                        val x = size.width * (if (rtl) 1f - mark else mark)
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x - gap / 2f, 0f),
                            size = Size(gap, size.height),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
            }
        },
    )
}

/** The width of the gap where a chapter starts. */
private val ChapterGap = 2.dp

/** The seek bar's thickness while it is dragged (Samsung). */
private val ActiveTrack = 16.dp

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
