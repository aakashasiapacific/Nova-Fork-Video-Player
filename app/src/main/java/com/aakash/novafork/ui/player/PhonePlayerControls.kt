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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.playerGlass
import com.aakash.novafork.ui.common.RollingText
import com.aakash.novafork.ui.common.rememberEndsAt
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState

/**
 * Phone controls drawn after Samsung's own video player: the whole picture dimmed; a bold title
 * with "Ends at" under it at the top; previous, play/pause and next on dark see-through discs in
 * the middle; the seek bar with a big white knob; and under it every action the player has, none
 * of them hidden in a menu (owner): fit, pop-up, a "1.0x" speed pill, rotate, lock, play mode,
 * subtitles, audio track, play on TV, the queue, shuffle and play in background. In portrait the
 * times sit above the bar and the icons spread over two rows; in landscape they take one row with
 * Capture at its left. Track pickers open via [onOpenPanel]; the queue and cast devices via
 * [onOpenSheet].
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
                onOpenSheet = onOpenSheet,
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
private fun PhoneTopBar(state: PlayerUiState, modifier: Modifier = Modifier) {
    // No back arrow, as in Samsung's player: the system back gesture closes it. What used to sit
    // beside the title (play mode, subtitles, cast) and in the ⋮ menu is under the seek bar now.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
    ) {
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

/**
 * A white glyph on a dark see-through disc: Samsung's previous and next, in iOS 27's tinted glass
 * so they hold up over bright scenes.
 */
@Composable
private fun DiscButton(icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(42.dp)
            .playerGlass(CircleShape)
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
    onOpenSheet: (PlayerSheet) -> Unit,
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
        Spacer(Modifier.height(if (portrait) 8.dp else 4.dp))
        PlayerOptionRows(
            options = playerOptions(state, actions, onOpenPanel, onOpenSheet),
            // Landscape: Samsung's Capture at the far left of the row (portrait puts it under the title).
            onCapture = if (!portrait && state.castingTo == null) actions::captureFrame else null,
        )
    }
}

/** One control of the rows under the seek bar: whatever it draws goes in a cell of the row. */
private class PlayerOption(val content: @Composable () -> Unit)

/**
 * Everything the player can do besides play, pause and skip, in the order they sit under the
 * seek bar: how the picture is shown, then what is played and where. Nothing is left out of
 * sight in a menu (owner); queue and shuffle only show when there is a queue.
 */
private fun playerOptions(
    state: PlayerUiState,
    actions: PlayerActions,
    onOpenPanel: (PlayerPanel) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
): List<PlayerOption> = buildList {
    add(PlayerOption { PlayerIconButton(R.drawable.ic_player_fit, "Fit or fill: ${state.aspect.label}", actions::cycleAspect) })
    if (state.canEnterPictureInPicture) {
        add(PlayerOption { PlayerIconButton(R.drawable.ic_player_popup, "Pop-up play", actions::enterPictureInPicture) })
    }
    add(PlayerOption { SpeedPill(rate = state.rate, onClick = { onOpenPanel(PlayerPanel.SPEED) }) })
    if (state.canRotate) {
        add(PlayerOption { PlayerIconButton(R.drawable.ic_player_rotate, "Rotate", actions::rotate) })
    }
    add(PlayerOption { PlayerIconButton(R.drawable.ic_player_lock, "Lock screen", actions::toggleLock) })
    add(PlayerOption { PlayerIconButton(state.repeatMode.iconRes(), state.repeatMode.label, actions::cycleRepeat) })
    add(
        PlayerOption {
            PlayerIconButton(
                R.drawable.ic_player_subtitles,
                "Subtitles",
                { onOpenPanel(PlayerPanel.SUBTITLES) },
                tint = Color.White.copy(alpha = if (subtitlesOn(state.subtitleTracks)) 1f else 0.6f),
            )
        },
    )
    add(PlayerOption { PlayerIconButton(R.drawable.ic_player_audio, "Audio track", { onOpenPanel(PlayerPanel.AUDIO) }) })
    add(PlayerOption { CastButton(castingTo = state.castingTo, onClick = { onOpenSheet(PlayerSheet.CAST) }) })
    if (state.queue.size > 1) {
        add(PlayerOption { PlayerIconButton(R.drawable.ic_player_queue, "Queue", { onOpenSheet(PlayerSheet.QUEUE) }) })
    }
    if (state.canShuffle) {
        add(
            PlayerOption {
                PlayerToggleButton(
                    icon = R.drawable.ic_player_shuffle,
                    contentDescription = if (state.shuffle) "Shuffle On" else "Shuffle Off",
                    checked = state.shuffle,
                    onClick = actions::toggleShuffle,
                )
            },
        )
    }
    add(PlayerOption { PlayerIconButton(R.drawable.ic_player_headphones, "Play in background", actions::playInBackground) })
}

/**
 * The [options] under the seek bar, each in an equal cell. One wide row when every cell can keep
 * at least [OptionCellMin] (landscape), Capture ([onCapture]) at its left; otherwise two balanced
 * rows, centred (portrait). A cell never grows past [OptionCellMax], so nothing clips or spreads
 * thin, whatever number of options there are.
 */
@Composable
private fun PlayerOptionRows(options: List<PlayerOption>, onCapture: (() -> Unit)?, modifier: Modifier = Modifier) {
    val capture = onCapture?.let { onClick ->
        PlayerOption { PlayerIconButton(R.drawable.ic_player_capture, "Capture", onClick) }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val total = options.size + if (capture != null) 1 else 0
        if (maxWidth / total >= OptionCellMin) {
            val cell = (maxWidth / total).coerceAtMost(OptionCellMax)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (capture != null) {
                    OptionCell(capture, cell)
                    Spacer(Modifier.weight(1f))
                }
                options.forEach { OptionCell(it, cell) }
            }
        } else {
            val all = listOfNotNull(capture) + options
            val perRow = (all.size + 1) / 2
            val cell = (maxWidth / perRow).coerceAtMost(OptionCellMax)
            Column(Modifier.fillMaxWidth()) {
                all.chunked(perRow).forEach { rowOptions ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowOptions.forEach { OptionCell(it, cell) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCell(option: PlayerOption, width: Dp) {
    Box(Modifier.width(width).height(OptionHeight), contentAlignment = Alignment.Center) { option.content() }
}

private val OptionCellMin = 44.dp
private val OptionCellMax = 56.dp
private val OptionHeight = 48.dp

/** Samsung's speed button: the rate, "1.0x", small in a low grey pill, in a full-size touch target. */
@Composable
private fun SpeedPill(rate: Float, onClick: () -> Unit) {
    Box(
        Modifier
            // A narrow cell lets it spill over its sides rather than squeeze the label.
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClickLabel = "Playback speed", role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(28.dp)
                .playerGlass(RoundedCornerShape(percent = 50))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("%.1fx".format(rate), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false)
        }
    }
}
