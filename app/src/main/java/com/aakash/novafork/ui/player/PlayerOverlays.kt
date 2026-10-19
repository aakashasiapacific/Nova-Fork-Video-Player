package com.aakash.novafork.ui.player

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.model.GestureFeedback
import com.aakash.novafork.ui.model.UpNextUi
import com.aakash.novafork.ui.theme.NovaColors

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** Centred pill for seek / label feedback; fades out after the state clears it. */
@Composable
internal fun FeedbackOverlay(feedback: GestureFeedback?, modifier: Modifier = Modifier) {
    val shown = rememberLatestNonNull(feedback)
    AnimatedVisibility(
        visible = feedback != null,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(250)),
    ) {
        if (shown != null) FeedbackPill(shown)
    }
}

@Composable
private fun FeedbackPill(feedback: GestureFeedback) {
    Surface(shape = RoundedCornerShape(28.dp), color = NovaColors.PlayerSurface, contentColor = Color.White) {
        when (feedback) {
            is GestureFeedback.Seek -> SeekFeedback(feedback)
            is GestureFeedback.Label -> Text(
                text = feedback.text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun SeekFeedback(seek: GestureFeedback.Seek) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatSignedClock(seek.deltaMs),
            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
        )
        Text(
            text = if (seek.durationMs > 0) {
                "${Formats.clock(seek.targetMs)} / ${Formats.clock(seek.durationMs)}"
            } else {
                Formats.clock(seek.targetMs)
            },
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

/**
 * The player's volume indicator after a volume key, in the style of iOS: a slim frosted pill that
 * fills with the level, on the end edge where the phone's volume keys are (portrait) or along the
 * top (landscape), with the speaker icon. It replaces the system's volume panel in the player and
 * fades a moment after the last press (see PlayerController.onVolumeKey). [modifier] should fill
 * the screen: the pill places itself.
 */
@Composable
internal fun VolumeHud(level: Float?, modifier: Modifier = Modifier) {
    val shown = (rememberLatestNonNull(level) ?: 0f).coerceIn(0f, 1f)
    val fill by animateFloatAsState(
        targetValue = shown,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "volumeHud",
    )
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(modifier, contentAlignment = if (landscape) Alignment.TopCenter else BiasAlignment(horizontalBias = 1f, verticalBias = -0.35f)) {
        AnimatedVisibility(
            visible = level != null,
            enter = fadeIn(tween(140)) + scaleIn(tween(180), initialScale = 0.85f),
            exit = fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 0.85f),
        ) {
            val icon = if (shown <= 0f) R.drawable.ic_volume_off else R.drawable.ic_volume_up
            if (landscape) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VolumeIcon(icon)
                    VolumeTrack(fill, vertical = false)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VolumeTrack(fill, vertical = true)
                    VolumeIcon(icon)
                }
            }
        }
    }
}

@Composable
private fun VolumeIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = "Volume", tint = Color.White, modifier = Modifier.size(18.dp))
}

@Composable
private fun VolumeTrack(fill: Float, vertical: Boolean) {
    val shape = RoundedCornerShape(percent = 50)
    val size = if (vertical) Modifier.size(width = 14.dp, height = 156.dp) else Modifier.size(width = 196.dp, height = 14.dp)
    Box(
        size
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(0.5.dp, Color.White.copy(alpha = 0.2f), shape),
    ) {
        val level = if (vertical) {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fill)
        } else {
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(fill)
        }
        Box(level.background(Color.White))
    }
}

@Composable
internal fun BufferingIndicator(modifier: Modifier = Modifier) {
    IosSpinner(modifier = modifier.size(48.dp), color = Color.White, strokeWidth = 3.dp)
}

/** "−10s" / "+20s" halves shown on the side that was double-tapped. */
@Composable
internal fun BoxScope.SeekBubbles(bubble: SeekBubble?) {
    SeekBubbleSide(
        bubble = bubble?.takeIf { !it.forward },
        forward = false,
        modifier = Modifier.align(AbsoluteAlignment.CenterLeft),
    )
    SeekBubbleSide(
        bubble = bubble?.takeIf { it.forward },
        forward = true,
        modifier = Modifier.align(AbsoluteAlignment.CenterRight),
    )
}

@Composable
private fun SeekBubbleSide(bubble: SeekBubble?, forward: Boolean, modifier: Modifier) {
    val shown = rememberLatestNonNull(bubble)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(bubble?.totalMs) {
        if (bubble != null) {
            pulse.snapTo(1.15f)
            pulse.animateTo(1f, tween(200))
        }
    }
    AnimatedVisibility(
        visible = bubble != null,
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.32f),
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(350)),
    ) {
        // Rounded on the side that faces the middle, like a ripple spreading from the edge.
        val shape = if (forward) {
            AbsoluteRoundedCornerShape(topLeftPercent = 50, bottomLeftPercent = 50)
        } else {
            AbsoluteRoundedCornerShape(topRightPercent = 50, bottomRightPercent = 50)
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_replay),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { if (forward) scaleX = -1f },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatSeekSeconds(forward, shown?.totalMs ?: 0L),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/** Bottom-start pill after an automatic resume: "Resumed at 12:34 · Start over · ×". */
@Composable
internal fun ResumeBanner(
    resumedFromMs: Long?,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLatestNonNull(resumedFromMs)
    AnimatedVisibility(
        visible = resumedFromMs != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        Surface(shape = RoundedCornerShape(50), color = NovaColors.PlayerSurface, contentColor = Color.White) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_history), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Resumed at ${Formats.clock(shown ?: 0L)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onStartOver) { Text("Start Over") }
                PlayerIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = "Dismiss",
                    onClick = onDismiss,
                    buttonSize = 40.dp,
                    iconSize = 20.dp,
                )
            }
        }
    }
}

/** Bottom-end card for the next episode, with its countdown. */
@Composable
internal fun UpNextCard(
    upNext: UpNextUi?,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLatestNonNull(upNext)
    AnimatedVisibility(
        visible = upNext != null,
        modifier = modifier,
        enter = fadeIn() + slideInHorizontally { it / 3 },
        exit = fadeOut(),
    ) {
        if (shown != null) UpNextContent(shown, onPlayNow, onCancel, large)
    }
}

@Composable
private fun UpNextContent(item: UpNextUi, onPlayNow: () -> Unit, onCancel: () -> Unit, large: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NovaColors.PlayerSurface,
        contentColor = Color.White,
        modifier = Modifier.widthIn(max = if (large) 460.dp else 360.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                LandscapeArt(
                    artwork = item.artwork,
                    title = item.title,
                    modifier = Modifier
                        .width(if (large) 184.dp else 128.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item.title,
                        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = item.subtitle
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val countdown = item.countdownSeconds
                    if (countdown != null) {
                        Text(
                            text = "Starts in ${countdown.coerceAtLeast(0)}s",
                            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("Cancel")
                }
                Button(onClick = onPlayNow) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play now")
                }
            }
        }
    }
}

/** Centred card when playback fails. "Try again" re-opens the media (play() retries after an error). */
@Composable
internal fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = NovaColors.PlayerSurface,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("Try again")
                }
                Button(onClick = onClose) { Text("Close") }
            }
        }
    }
}

/** The only thing on screen in lock mode, for a moment after a tap. */
@Composable
internal fun UnlockButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = NovaColors.PlayerSurface,
            contentColor = Color.White,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_open),
                    contentDescription = "Unlock",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
