package com.aakash.novafork.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.PlayerUiState
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
    /** Copies the frame on screen (the video draws where Compose can't read it); null where it can't. */
    copyFrame: ((onCopied: (Bitmap?) -> Unit) -> Unit)? = null,
    surface: @Composable () -> Unit,
) {
    // How far the video has been pulled down to close the player (iOS), px.
    val dismiss = remember { Animatable(0f) }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Always the first child so entering or leaving PiP never recreates the video view.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dismiss.value },
            contentAlignment = Alignment.Center,
        ) {
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
            PlayerChrome(state = state, actions = actions, layout = layout, dismiss = dismiss, copyFrame = copyFrame)
        }
    }
}

@Composable
private fun PlayerChrome(
    state: PlayerUiState,
    actions: PlayerActions,
    layout: DeviceLayout,
    dismiss: Animatable<Float, AnimationVector1D>,
    copyFrame: ((onCopied: (Bitmap?) -> Unit) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // The finger's pull, added up here: several moves can arrive before one is drawn.
    val pulled = remember { floatArrayOf(0f) }
    var controlsRequested by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf<PlayerPanel?>(null) }
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    // The full length, as Samsung's player shows it; a tap on it switches to the time left.
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var unlockHintRequests by remember { mutableIntStateOf(0) }
    var unlockHintVisible by remember { mutableStateOf(false) }
    var seekBubble by remember { mutableStateOf<SeekBubble?>(null) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // Paused, failed or with options open, the controls stay up whatever was tapped.
    val searchOpen = state.subtitleSearch != null
    val forcedVisible = !state.isPlaying || state.error != null || panel != null || sheet != null || searchOpen

    // Left paused a while, Netflix's way (owner): the picture drains to black and white and dims,
    // the controls go, and what is playing shows over it; any touch brings the controls back.
    var pausedInfo by remember { mutableStateOf(false) }
    var pausedFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val canShowInfo = !state.isPlaying && !state.isBuffering && !state.finished && state.error == null &&
        !state.locked && panel == null && sheet == null && !searchOpen && state.castingTo == null && state.durationMs > 0
    LaunchedEffect(canShowInfo, touching) {
        pausedInfo = false
        if (!canShowInfo || touching) return@LaunchedEffect
        delay(PAUSED_INFO_DELAY_MS)
        val bitmap = copyFrame?.let { copy -> suspendCancellableCoroutine<Bitmap?> { done -> copy { done.resume(it) } } }
        pausedFrame = bitmap?.asImageBitmap()
        pausedInfo = true
    }
    val controlsVisible = !state.locked && !pausedInfo && (controlsRequested || forcedVisible)

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
                .observeTouches { touching = it }
                // While the video is pulled down to close, everything over it fades away.
                .graphicsLayer {
                    val height = size.height.coerceAtLeast(1f)
                    alpha = (1f - dismiss.value / (height * 0.25f)).coerceIn(0f, 1f)
                },
        ) {
            PlayerGestureLayer(
                state = state,
                actions = actions,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onLockedTap = { unlockHintRequests += 1 },
                onPullDown = { dy ->
                    pulled[0] = (pulled[0] + dy).coerceAtLeast(0f)
                    val to = pulled[0]
                    scope.launch { dismiss.snapTo(to) }
                },
                onPullDownEnd = { velocity, height ->
                    pulled[0] = 0f
                    scope.launch {
                        if (dismiss.value > height * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
                            // Off the bottom, then the player closes (fading out, see PlayerActivity).
                            dismiss.animateTo(height, tween(180), initialVelocity = velocity)
                            backDispatcher?.onBackPressed()
                            // Still here (casting, or locked): the video comes back.
                            delay(400)
                            dismiss.snapTo(0f)
                        } else {
                            dismiss.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f), initialVelocity = velocity)
                        }
                    }
                },
            )
            SeekBubbles(seekBubble)
            if (state.isBuffering && state.error == null && !controlsVisible) {
                BufferingIndicator(Modifier.align(Alignment.Center))
            }
            FeedbackOverlay(
                feedback = state.feedback,
                frame = state.scrubFrame,
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = 0f, verticalBias = feedbackBias))
                    .padding(horizontal = 24.dp),
            )
            VolumeHud(
                level = state.volumeHud,
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(12.dp),
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
                    // The bars slide in from the top and bottom edges as they fade in, and back out.
                    val shown by transition.animateFloat(
                        transitionSpec = { tween(if (targetState == EnterExitState.Visible) 220 else 260) },
                        label = "controlsSlide",
                    ) { if (it == EnterExitState.Visible) 1f else 0f }
                    PhonePlayerControls(
                        state = state,
                        actions = actions,
                        showRemaining = showRemaining,
                        onToggleRemaining = { showRemaining = !showRemaining },
                        onOpenPanel = openPanel,
                        onOpenSheet = openSheet,
                        onBottomBarHeightChanged = { bottomBarHeightPx = it },
                        shown = { shown },
                    )
                }
            }
            AnimatedVisibility(
                visible = pausedInfo,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(PAUSED_INFO_FADE_IN_MS)),
                exit = fadeOut(tween(200)),
            ) {
                PausedInfo(state = state, frame = pausedFrame)
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

/** Pulled down past this share of the height (or flicked), the player closes. */
private const val DISMISS_FRACTION = 0.22f
private const val DISMISS_VELOCITY = 1_500f

/**
 * What shows over a picture left paused (Netflix): the frame drains from colour to black and white
 * and dims, and the title, what it is and its synopsis rise in at the left.
 */
@Composable
private fun PausedInfo(state: PlayerUiState, frame: ImageBitmap?) {
    val drain = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drain.animateTo(1f, tween(PAUSED_DRAIN_MS, easing = LinearOutSlowInEasing)) }
    Box(Modifier.fillMaxSize()) {
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - drain.value) }),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.75f * drain.value),
                        0.6f to Color.Black.copy(alpha = 0.45f * drain.value),
                        1f to Color.Black.copy(alpha = 0.3f * drain.value),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .safeDrawingPadding()
                .padding(start = 40.dp, end = 40.dp)
                .widthIn(max = 520.dp)
                .graphicsLayer {
                    alpha = drain.value
                    translationY = (1f - drain.value) * 16.dp.toPx()
                },
        ) {
            Text("You're watching", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.65f))
            Text(
                state.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            state.subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
            state.overview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** How long the picture stays paused before the info shows, and how it comes in. */
private const val PAUSED_INFO_DELAY_MS = 5_000L
private const val PAUSED_INFO_FADE_IN_MS = 500
private const val PAUSED_DRAIN_MS = 1_600
