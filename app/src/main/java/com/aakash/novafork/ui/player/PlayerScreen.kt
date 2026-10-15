package com.aakash.novafork.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_500L
private const val UNLOCK_HINT_MS = 2_000L

/** A tap on the same side within this time keeps a double-tap seek series going. */
private const val SEEK_SERIES_MS = 800L

/**
 * The video player screen. [surface] draws the video and fills the screen; everything else is
 * drawn from [state] and reports through [actions], so the screen renders the same from fake state.
 * Only presentation state (controls shown, which picker is open) lives here.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    layout: DeviceLayout,
    modifier: Modifier = Modifier,
    surface: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Always the first child so entering or leaving PiP never recreates the video view.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            surface()
        }
        val castingTo = state.castingTo
        if (castingTo != null) {
            CastingPlaceholder(
                deviceName = castingTo,
                title = state.title,
                compact = state.inPictureInPicture,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.inPictureInPicture) {
            if (state.isBuffering) BufferingIndicator(Modifier.align(Alignment.Center))
        } else {
            PlayerChrome(state = state, actions = actions, layout = layout)
        }
    }
}

@Composable
private fun PlayerChrome(state: PlayerUiState, actions: PlayerActions, layout: DeviceLayout) {
    var controlsRequested by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf<PlayerPanel?>(null) }
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var unlockHintRequests by remember { mutableIntStateOf(0) }
    var unlockHintVisible by remember { mutableStateOf(false) }
    var seekBubble by remember { mutableStateOf<SeekBubble?>(null) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // Paused, failed or with options open, the controls stay up whatever was tapped.
    val searchOpen = state.subtitleSearch != null
    val forcedVisible = !state.isPlaying || state.error != null || panel != null || sheet != null || searchOpen
    val controlsVisible = !state.locked && (controlsRequested || forcedVisible)

    // Keep them requested across a pause so resuming doesn't make them vanish under the finger.
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) controlsRequested = true
    }
    LaunchedEffect(state.locked) {
        if (state.locked) {
            panel = null
            sheet = null
            if (state.subtitleSearch != null) actions.dismissSubtitleSearch()
            unlockHintRequests += 1
        } else {
            controlsRequested = true
        }
    }
    LaunchedEffect(unlockHintRequests) {
        if (unlockHintRequests > 0) {
            unlockHintVisible = true
            delay(UNLOCK_HINT_MS)
            unlockHintVisible = false
        }
    }
    // Any touch restarts the countdown: the key flips while a finger is down and again when it lifts.
    val autoHide = controlsVisible && !forcedVisible && !touching
    LaunchedEffect(autoHide) {
        if (autoHide) {
            delay(AUTO_HIDE_MS)
            controlsRequested = false
        }
    }
    LaunchedEffect(seekBubble) {
        if (seekBubble != null) {
            delay(SEEK_SERIES_MS)
            seekBubble = null
        }
    }

    fun seekStep(forward: Boolean) {
        if (!state.seekable) return
        val step = state.seekStepMs
        actions.seekBy(if (forward) step else -step)
        val previous = seekBubble
        seekBubble = if (previous != null && previous.forward == forward) {
            previous.copy(totalMs = previous.totalMs + step)
        } else {
            SeekBubble(forward = forward, totalMs = step)
        }
    }

    val onTap: (TapZone) -> Unit = { zone ->
        val series = seekBubble
        val sameSide = series != null && zone != TapZone.CENTER && series.forward == (zone == TapZone.RIGHT)
        if (series != null && sameSide) {
            seekStep(series.forward)
        } else {
            controlsRequested = forcedVisible || !controlsRequested
        }
    }
    val onDoubleTap: (TapZone) -> Unit = { zone ->
        when (zone) {
            TapZone.LEFT -> seekStep(forward = false)
            TapZone.CENTER -> actions.togglePlay()
            TapZone.RIGHT -> seekStep(forward = true)
        }
    }
    val openPanel: (PlayerPanel) -> Unit = {
        sheet = null
        panel = it
    }
    val openSheet: (PlayerSheet) -> Unit = {
        panel = null
        sheet = it
    }

    val density = LocalDensity.current
    val bannerLift by animateDpAsState(
        targetValue = if (controlsVisible) with(density) { bottomBarHeightPx.toDp() } else 0.dp,
        label = "bannerLift",
    )
    // With the controls up, feedback moves above the play button instead of covering it.
    val feedbackBias by animateFloatAsState(if (controlsVisible) -0.5f else 0f, label = "feedbackBias")

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            Modifier
                .fillMaxSize()
                .observeTouches { touching = it },
        ) {
            PlayerGestureLayer(
                state = state,
                actions = actions,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onLockedTap = { unlockHintRequests += 1 },
            )
            SeekBubbles(seekBubble)
            if (state.isBuffering && state.error == null && !controlsVisible) {
                BufferingIndicator(Modifier.align(Alignment.Center))
            }
            FeedbackOverlay(
                feedback = state.feedback,
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = 0f, verticalBias = feedbackBias))
                    .padding(horizontal = 24.dp),
            )
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(260)),
            ) {
                if (layout.isTablet) {
                    TabletPlayerControls(
                        state = state,
                        actions = actions,
                        layout = layout,
                        showRemaining = showRemaining,
                        onToggleRemaining = { showRemaining = !showRemaining },
                        onOpenPanel = openPanel,
                        onOpenSheet = openSheet,
                        onBottomBarHeightChanged = { bottomBarHeightPx = it },
                    )
                } else {
                    PhonePlayerControls(
                        state = state,
                        actions = actions,
                        showRemaining = showRemaining,
                        onToggleRemaining = { showRemaining = !showRemaining },
                        onOpenPanel = openPanel,
                        onOpenSheet = openSheet,
                        onBottomBarHeightChanged = { bottomBarHeightPx = it },
                    )
                }
            }
            if (!state.locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp + bannerLift),
                ) {
                    ResumeBanner(
                        resumedFromMs = state.resumedFromMs,
                        onStartOver = actions::startOver,
                        onDismiss = actions::dismissResumeBanner,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                    UpNextCard(
                        upNext = state.upNext,
                        onPlayNow = actions::playNext,
                        onCancel = actions::cancelUpNext,
                        large = layout.isTablet,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
            val error = state.error
            if (error != null) {
                ErrorCard(
                    message = error,
                    onRetry = actions::play,
                    onClose = actions::close,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            UnlockButton(
                visible = state.locked && unlockHintVisible,
                onClick = actions::toggleLock,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .safeDrawingPadding()
                    .padding(start = 24.dp),
            )
            if (layout.isTablet) {
                TrackPanel(
                    section = panel,
                    state = state,
                    actions = actions,
                    onDismiss = { panel = null },
                )
                PlayerSheetPanel(
                    sheet = sheet,
                    state = state,
                    actions = actions,
                    onDismiss = { sheet = null },
                )
                SubtitleSearchPanel(search = state.subtitleSearch, actions = actions)
            }
        }

        val trackSheet = panel
        if (!layout.isTablet && trackSheet != null) {
            TrackSheet(
                initial = trackSheet,
                state = state,
                actions = actions,
                onDismiss = { panel = null },
            )
        }
        val listSheet = sheet
        if (!layout.isTablet && listSheet != null) {
            PlayerListSheet(
                sheet = listSheet,
                state = state,
                actions = actions,
                onDismiss = { sheet = null },
            )
        }
        val subtitleSearch = state.subtitleSearch
        if (!layout.isTablet && subtitleSearch != null) {
            SubtitleSearchSheet(search = subtitleSearch, actions = actions)
        }
    }

    // The phone sheets handle back themselves; this closes the tablet panels before back leaves the player.
    BackHandler(enabled = panel != null || sheet != null || (layout.isTablet && searchOpen)) {
        if (layout.isTablet && searchOpen) actions.dismissSubtitleSearch()
        panel = null
        sheet = null
    }
}
