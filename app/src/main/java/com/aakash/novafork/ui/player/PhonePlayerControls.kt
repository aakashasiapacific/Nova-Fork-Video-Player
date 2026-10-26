package com.aakash.novafork.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.RollingText
import com.aakash.novafork.ui.common.rememberEndsAt
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState

/**
 * Phone controls drawn after Samsung's own video player (owner's screenshots): the whole picture
 * dimmed; a bold title with repeat, CC and a vertical "⋮" at the top; previous, play/pause and
 * next on dark see-through discs in the middle; the seek bar with a big white knob; and fit,
 * pop-up, a "1.0x" speed pill, rotate and lock along the bottom. In portrait the times sit above
 * the bar and the bottom icons spread across the width. Track pickers open via [onOpenPanel];
 * the queue and cast devices via [onOpenSheet].
 */
@Composable
internal fun PhonePlayerControls(
    state: PlayerUiState,
    actions: PlayerActions,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onBottomBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 0 hidden to 1 shown: the bars ride in from the edges with it. */
    shown: () -> Float = { 1f },
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // Samsung only shades the picture lightly under its controls.
            .background(Color.Black.copy(alpha = 0.3f)),
    ) {
        val portrait = maxHeight > maxWidth
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            PhoneTopBar(
                state = state,
                actions = actions,
                onOpenPanel = onOpenPanel,
                onOpenSheet = onOpenSheet,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = -(1f - shown()) * ControlsSlide.toPx() },
            )
            if (state.error == null) {
                PhoneTransportRow(state = state, actions = actions, modifier = Modifier.align(Alignment.Center))
            }
            // Portrait: Samsung's Capture sits under the title, on the left.
            if (portrait && state.castingTo == null) {
                PlayerIconButton(
                    icon = R.drawable.ic_player_capture,
                    contentDescription = "Capture",
                    onClick = actions::captureFrame,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = PortraitCaptureTop)
                        .graphicsLayer { translationY = -(1f - shown()) * ControlsSlide.toPx() },
                )
            }
            PhoneBottomBar(
                state = state,
                actions = actions,
                portrait = portrait,
                showRemaining = showRemaining,
                onToggleRemaining = onToggleRemaining,
                onOpenPanel = onOpenPanel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { onBottomBarHeightChanged(it.height) }
                    .graphicsLayer { translationY = (1f - shown()) * ControlsSlide.toPx() },
            )
        }
    }
}

/** How far the top and bottom bars travel as they come and go. */
private val ControlsSlide = 36.dp

/** Where Capture sits below the title in portrait, as in Samsung's player. */
private val PortraitCaptureTop = 76.dp

@Composable
private fun PhoneTopBar(
    state: PlayerUiState,
    actions: PlayerActions,
    onOpenPanel: (PlayerPanel) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No back arrow, as in Samsung's player: the system back gesture closes it.
        Column(Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Apple TV's "Ends at 10:42 PM", at the speed it is playing.
            val left = if (state.durationMs > 0L) ((state.durationMs - state.positionMs) / state.rate.coerceAtLeast(0.1f)).toLong() else null
            rememberEndsAt(left)?.let { endsAt ->
                // Its digits roll when the time moves (a seek, another speed).
                RollingText(endsAt, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
        // Cast shows here only while casting, so where the picture went is never hidden.
        if (state.castingTo != null) CastButton(castingTo = state.castingTo, onClick = { onOpenSheet(PlayerSheet.CAST) })
        PlayerIconButton(icon = state.repeatMode.iconRes(), contentDescription = state.repeatMode.label, onClick = actions::cycleRepeat)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Subtitles", role = Role.Button) { onOpenPanel(PlayerPanel.SUBTITLES) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "CC",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = if (subtitlesOn(state.subtitleTracks)) 1f else 0.6f),
            )
        }
        PlayerMoreMenu(
            icon = R.drawable.ic_player_more,
            items = buildList {
                add(PlayerMenuItem(R.drawable.ic_audiotrack, "Audio Track") { onOpenPanel(PlayerPanel.AUDIO) })
                if (state.castingTo == null) add(PlayerMenuItem(R.drawable.ic_cast, "Play on TV") { onOpenSheet(PlayerSheet.CAST) })
                if (state.queue.size > 1) {
                    add(PlayerMenuItem(R.drawable.ic_playlist_play, "Queue") { onOpenSheet(PlayerSheet.QUEUE) })
                }
                if (state.canShuffle) {
                    add(PlayerMenuItem(R.drawable.ic_shuffle, if (state.shuffle) "Shuffle On" else "Shuffle Off", actions::toggleShuffle))
                }
                add(PlayerMenuItem(R.drawable.ic_volume_up, "Play in Background", actions::playInBackground))
            },
        )
    }
}

@Composable
private fun PhoneTransportRow(state: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        // Samsung's sizes: small discs either side of a larger play disc, 96dp centre to centre.
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        // With nothing before it, previous goes back to the start, as Samsung's does.
        DiscButton(
            icon = R.drawable.ic_player_previous,
            description = if (state.hasPrevious) "Previous" else "Start over",
            onClick = { if (state.hasPrevious) actions.playPrevious() else actions.seekTo(0L) },
        )
        val haptics = LocalHapticFeedback.current
        PlayPauseButton(
            isPlaying = state.isPlaying,
            buffering = state.isBuffering,
            finished = state.finished,
            // A light tick with each play and pause.
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                actions.togglePlay()
            },
            size = 58.dp,
        )
        DiscButton(
            icon = R.drawable.ic_player_next,
            description = "Next",
            onClick = actions::playNext,
            enabled = state.hasNext,
        )
    }
}

/** A white glyph on a dark see-through disc: Samsung's previous and next. */
@Composable
private fun DiscButton(icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PhoneBottomBar(
    state: PlayerUiState,
    actions: PlayerActions,
    portrait: Boolean,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        PlayerSeekBar(
            state = state,
            actions = actions,
            showRemaining = showRemaining,
            onToggleRemaining = onToggleRemaining,
            stacked = portrait,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (portrait) 16.dp else 8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            // Landscape keeps them to the right; portrait spreads them across, as Samsung does.
            horizontalArrangement = if (portrait) Arrangement.SpaceEvenly else Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            // Landscape: Samsung's Capture at the far left of the row.
            if (!portrait && state.castingTo == null) {
                PlayerIconButton(icon = R.drawable.ic_player_capture, contentDescription = "Capture", onClick = actions::captureFrame)
                Spacer(Modifier.weight(1f))
            }
            PlayerIconButton(icon = R.drawable.ic_player_fit, contentDescription = "Fit or fill: ${state.aspect.label}", onClick = actions::cycleAspect)
            if (state.canEnterPictureInPicture) {
                PlayerIconButton(icon = R.drawable.ic_player_popup, contentDescription = "Pop-up play", onClick = actions::enterPictureInPicture)
            }
            SpeedPill(rate = state.rate, onClick = { onOpenPanel(PlayerPanel.SPEED) })
            if (state.canRotate) {
                PlayerIconButton(icon = R.drawable.ic_player_rotate, contentDescription = "Rotate", onClick = actions::rotate)
            }
            PlayerIconButton(icon = R.drawable.ic_player_lock, contentDescription = "Lock screen", onClick = actions::toggleLock)
        }
    }
}

/** Samsung's speed button: the rate, "1.0x", small in a low grey pill, in a full-size touch target. */
@Composable
private fun SpeedPill(rate: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClickLabel = "Playback speed", role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("%.1fx".format(rate), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
