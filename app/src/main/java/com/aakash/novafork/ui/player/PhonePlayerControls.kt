package com.aakash.novafork.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState

private val CenterButtonSize = 48.dp
private val CenterIconSize = 28.dp

/**
 * Phone controls after Samsung's own video player (owner): small and few. Back and the title at
 * the top with subtitles and "⋮"; plain play/pause and the 10-second steps in the middle, with no
 * circles; the seek bar and a short icon row (lock, rotate | speed, picture-in-picture) below. Track pickers open in [TrackSheet] via
 * [onOpenPanel]; the queue and cast devices in a sheet via [onOpenSheet].
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
) {
    Box(modifier.fillMaxSize()) {
        ControlScrims(top = 104.dp, bottom = 150.dp)
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
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (state.error == null) {
                PhoneTransportRow(state = state, actions = actions, modifier = Modifier.align(Alignment.Center))
            }
            PhoneBottomBar(
                state = state,
                actions = actions,
                showRemaining = showRemaining,
                onToggleRemaining = onToggleRemaining,
                onOpenPanel = onOpenPanel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { onBottomBarHeightChanged(it.height) },
            )
        }
    }
}

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
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Back", onClick = actions::close)
        PlayerTitle(state, Modifier.weight(1f).padding(horizontal = 4.dp))
        // Cast shows here only while casting, so where the picture went is never hidden.
        if (state.castingTo != null) CastButton(castingTo = state.castingTo, onClick = { onOpenSheet(PlayerSheet.CAST) })
        PlayerIconButton(
            icon = if (subtitlesOn(state.subtitleTracks)) R.drawable.ic_subtitles else R.drawable.ic_subtitles_off,
            contentDescription = "Subtitles",
            onClick = { onOpenPanel(PlayerPanel.SUBTITLES) },
            iconSize = 22.dp,
        )
        PlayerMoreMenu(
            items = buildList {
                add(PlayerMenuItem(R.drawable.ic_audiotrack, "Audio Track") { onOpenPanel(PlayerPanel.AUDIO) })
                if (state.castingTo == null) add(PlayerMenuItem(R.drawable.ic_cast, "Play on TV") { onOpenSheet(PlayerSheet.CAST) })
                add(PlayerMenuItem(R.drawable.ic_speed, "Speed and aspect ratio") { onOpenPanel(PlayerPanel.SPEED) })
                if (state.queue.size > 1) {
                    add(PlayerMenuItem(R.drawable.ic_playlist_play, "Queue") { onOpenSheet(PlayerSheet.QUEUE) })
                }
                add(PlayerMenuItem(R.drawable.ic_volume_up, "Play in Background", actions::playInBackground))
                // Moved here from the bottom row, which keeps only lock, speed and picture-in-picture
                // as iOS's player keeps its bar short.
                add(PlayerMenuItem(R.drawable.ic_aspect_ratio, "Aspect Ratio: ${state.aspect.label}", actions::cycleAspect))
                add(PlayerMenuItem(state.repeatMode.iconRes(), state.repeatMode.label, actions::cycleRepeat))
                if (state.canShuffle) {
                    add(PlayerMenuItem(R.drawable.ic_shuffle, if (state.shuffle) "Shuffle On" else "Shuffle Off", actions::toggleShuffle))
                }
            },
        )
    }
}

@Composable
private fun PhoneTransportRow(state: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    // Previous/next keep both slots when either exists so play/pause stays centred.
    val showSkips = state.hasPrevious || state.hasNext
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (showSkips) {
            if (state.hasPrevious) {
                PlayerIconButton(
                    icon = R.drawable.ic_skip_previous,
                    contentDescription = "Previous",
                    onClick = actions::playPrevious,
                    buttonSize = CenterButtonSize,
                    iconSize = CenterIconSize,
                )
            } else {
                Spacer(Modifier.size(CenterButtonSize))
            }
        }
        SeekStepButton(
            forward = false,
            stepMs = state.seekStepMs,
            onClick = { actions.seekBy(-state.seekStepMs) },
            enabled = state.seekable,
            buttonSize = CenterButtonSize,
            iconSize = CenterIconSize,
        )
        PlayPauseButton(
            isPlaying = state.isPlaying,
            buffering = state.isBuffering,
            onClick = actions::togglePlay,
            size = 56.dp,
        )
        SeekStepButton(
            forward = true,
            stepMs = state.seekStepMs,
            onClick = { actions.seekBy(state.seekStepMs) },
            enabled = state.seekable,
            buttonSize = CenterButtonSize,
            iconSize = CenterIconSize,
        )
        if (showSkips) {
            if (state.hasNext) {
                PlayerIconButton(
                    icon = R.drawable.ic_skip_next,
                    contentDescription = "Next",
                    onClick = actions::playNext,
                    buttonSize = CenterButtonSize,
                    iconSize = CenterIconSize,
                )
            } else {
                Spacer(Modifier.size(CenterButtonSize))
            }
        }
    }
}

@Composable
private fun PhoneBottomBar(
    state: PlayerUiState,
    actions: PlayerActions,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
    ) {
        PlayerSeekBar(
            state = state,
            actions = actions,
            showRemaining = showRemaining,
            onToggleRemaining = onToggleRemaining,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerIconButton(icon = R.drawable.ic_lock, contentDescription = "Lock screen", onClick = actions::toggleLock, iconSize = 22.dp)
            // Samsung keeps rotate by the lock, at hand while holding the phone sideways.
            if (state.canRotate) {
                PlayerIconButton(icon = R.drawable.ic_screen_rotation, contentDescription = "Rotate", onClick = actions::rotate, iconSize = 22.dp)
            }
            Spacer(Modifier.weight(1f))
            SpeedButton(rate = state.rate, onClick = { onOpenPanel(PlayerPanel.SPEED) })
            if (state.canEnterPictureInPicture) {
                PlayerIconButton(
                    icon = R.drawable.ic_picture_in_picture_alt,
                    contentDescription = "Picture-in-picture",
                    onClick = actions::enterPictureInPicture,
                )
            }
        }
    }
}

/** Plain icon at normal speed; shows the rate next to it otherwise so a changed speed is never a surprise. */
@Composable
private fun SpeedButton(rate: Float, onClick: () -> Unit) {
    if (isRate(rate, 1f)) {
        PlayerIconButton(icon = R.drawable.ic_speed, contentDescription = "Playback speed", onClick = onClick)
    } else {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_speed),
                contentDescription = "Playback speed",
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = formatRate(rate), style = MaterialTheme.typography.labelLarge)
        }
    }
}
