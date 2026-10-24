package com.aakash.novafork.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState

private val TransportButtonSize = 64.dp
private val TransportIconSize = 40.dp

/**
 * Tablet controls: larger targets, title top-left with cast, queue and more on the right,
 * transport in the centre, a full-width seek bar and one row of labelled buttons. Track options
 * open in the side [TrackPanel] via [onOpenPanel]; the queue and cast devices via [onOpenSheet].
 */
@Composable
internal fun TabletPlayerControls(
    state: PlayerUiState,
    actions: PlayerActions,
    layout: DeviceLayout,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onBottomBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ControlScrims(top = 160.dp, bottom = 230.dp)
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            TabletTopBar(
                state = state,
                actions = actions,
                onOpenSheet = onOpenSheet,
                onOpenPanel = onOpenPanel,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (state.error == null) {
                TabletTransportRow(state = state, actions = actions, modifier = Modifier.align(Alignment.Center))
            }
            TabletBottomBar(
                state = state,
                actions = actions,
                // Portrait tablets lack the width for seven labels: the secondary actions become icons.
                labelSecondary = layout.isExpanded,
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
private fun TabletTopBar(
    state: PlayerUiState,
    actions: PlayerActions,
    onOpenSheet: (PlayerSheet) -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = "Back",
            onClick = actions::close,
            buttonSize = 56.dp,
            iconSize = 28.dp,
        )
        Spacer(Modifier.width(12.dp))
        PlayerTitle(state, Modifier.weight(1f), large = true)
        Spacer(Modifier.width(12.dp))
        CastButton(
            castingTo = state.castingTo,
            onClick = { onOpenSheet(PlayerSheet.CAST) },
            buttonSize = 56.dp,
            iconSize = 26.dp,
        )
        if (state.queue.size > 1) {
            PlayerIconButton(
                icon = R.drawable.ic_playlist_play,
                contentDescription = "Queue",
                onClick = { onOpenSheet(PlayerSheet.QUEUE) },
                buttonSize = 56.dp,
                iconSize = 26.dp,
            )
        }
        PlayerMoreMenu(
            // Aspect ratio, repeat, shuffle and rotate live here, as on the phone, so the bars
            // keep only what iOS's player shows.
            items = buildList {
                add(PlayerMenuItem(R.drawable.ic_volume_up, "Play in Background", actions::playInBackground))
                add(PlayerMenuItem(R.drawable.ic_aspect_ratio, "Aspect Ratio: ${state.aspect.label}") { onOpenPanel(PlayerPanel.ASPECT) })
                add(PlayerMenuItem(state.repeatMode.iconRes(), state.repeatMode.label, actions::cycleRepeat))
                if (state.canShuffle) {
                    add(PlayerMenuItem(R.drawable.ic_shuffle, if (state.shuffle) "Shuffle On" else "Shuffle Off", actions::toggleShuffle))
                }
                add(PlayerMenuItem(R.drawable.ic_screen_rotation, "Rotate", actions::rotate))
            },
            buttonSize = 56.dp,
            iconSize = 26.dp,
        )
    }
}

@Composable
private fun TabletTransportRow(state: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    // Previous/next keep both slots when either exists so play/pause stays centred.
    val showSkips = state.hasPrevious || state.hasNext
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        if (showSkips) {
            if (state.hasPrevious) {
                PlayerIconButton(
                    icon = R.drawable.ic_skip_previous,
                    contentDescription = "Previous",
                    onClick = actions::playPrevious,
                    buttonSize = TransportButtonSize,
                    iconSize = TransportIconSize,
                )
            } else {
                Spacer(Modifier.size(TransportButtonSize))
            }
        }
        SeekStepButton(
            forward = false,
            stepMs = state.seekStepMs,
            onClick = { actions.seekBy(-state.seekStepMs) },
            enabled = state.seekable,
            buttonSize = TransportButtonSize,
            iconSize = TransportIconSize,
        )
        PlayPauseButton(
            isPlaying = state.isPlaying,
            buffering = state.isBuffering,
            onClick = actions::togglePlay,
            size = 72.dp,
        )
        SeekStepButton(
            forward = true,
            stepMs = state.seekStepMs,
            onClick = { actions.seekBy(state.seekStepMs) },
            enabled = state.seekable,
            buttonSize = TransportButtonSize,
            iconSize = TransportIconSize,
        )
        if (showSkips) {
            if (state.hasNext) {
                PlayerIconButton(
                    icon = R.drawable.ic_skip_next,
                    contentDescription = "Next",
                    onClick = actions::playNext,
                    buttonSize = TransportButtonSize,
                    iconSize = TransportIconSize,
                )
            } else {
                Spacer(Modifier.size(TransportButtonSize))
            }
        }
    }
}

@Composable
private fun TabletBottomBar(
    state: PlayerUiState,
    actions: PlayerActions,
    labelSecondary: Boolean,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    onOpenPanel: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    ) {
        PlayerSeekBar(
            state = state,
            actions = actions,
            showRemaining = showRemaining,
            onToggleRemaining = onToggleRemaining,
            large = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The option buttons scroll rather than squeeze if a large font makes them too wide.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabelledButton(
                    icon = R.drawable.ic_audiotrack,
                    label = "Audio",
                    onClick = { onOpenPanel(PlayerPanel.AUDIO) },
                )
                LabelledButton(
                    icon = if (subtitlesOn(state.subtitleTracks)) R.drawable.ic_subtitles else R.drawable.ic_subtitles_off,
                    label = "Subtitles",
                    onClick = { onOpenPanel(PlayerPanel.SUBTITLES) },
                )
                LabelledButton(
                    icon = R.drawable.ic_speed,
                    label = "Speed ${formatRate(state.rate)}",
                    onClick = { onOpenPanel(PlayerPanel.SPEED) },
                )
            }
            Spacer(Modifier.width(8.dp))
            LabelledButton(
                icon = R.drawable.ic_lock,
                label = "Lock",
                onClick = actions::toggleLock,
                showLabel = labelSecondary,
            )
            if (state.canEnterPictureInPicture) {
                LabelledButton(
                    icon = R.drawable.ic_picture_in_picture_alt,
                    label = "Picture-in-picture",
                    onClick = actions::enterPictureInPicture,
                    showLabel = labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun LabelledButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    showLabel: Boolean = true,
) {
    if (showLabel) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 52.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    } else {
        PlayerIconButton(icon = icon, contentDescription = label, onClick = onClick, buttonSize = 56.dp, iconSize = 26.dp)
    }
}
