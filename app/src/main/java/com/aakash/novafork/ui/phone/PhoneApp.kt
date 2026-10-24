package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.audio.AudioScreen
import com.aakash.novafork.ui.audio.CoverFlight
import com.aakash.novafork.ui.audio.LocalNowPlayingSheet
import com.aakash.novafork.ui.audio.MiniPlayerBar
import com.aakash.novafork.ui.audio.MiniPlayerDockedHeight
import com.aakash.novafork.ui.audio.NowPlayingSheet
import com.aakash.novafork.ui.audio.ShellNowPlaying
import com.aakash.novafork.ui.audio.rememberMusicShell
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalMenuFocus
import com.aakash.novafork.ui.common.LocalPagePull
import com.aakash.novafork.ui.common.LocalTabReselect
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.TabReselect
import com.aakash.novafork.ui.common.activeColor
import com.aakash.novafork.ui.common.iosOverscroll
import com.aakash.novafork.ui.common.swipeBetweenTabs
import com.aakash.novafork.ui.common.tabIconRes
import com.aakash.novafork.ui.common.tabSlide
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.top
import com.aakash.novafork.ui.settings.SettingsScreen
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.launch

/**
 * Phone UI: a docked iOS tab bar over one full-screen page per route. Dialogs, snackbars and system
 * back are handled by the app shell around this.
 */
@Composable
fun PhoneApp(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier) {
    // Pages leave the composition when navigated away from; this keeps their scroll positions
    // (and other saveable state) for when the user comes back.
    val pageStates = rememberSaveableStateHolder()
    val top = state.route.top
    // The docked bar lies over the page's foot: pages run on behind it and pad their lists by
    // LocalBottomBarSpace.
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Music (the real app only): the mini player floats just above the tab bar on every tab,
    // and opens Now Playing over everything.
    val music = rememberMusicShell()
    val player = music?.second
    // The mini player, as in Apple Music: docked just above the tab bar whenever there is a
    // queue, playing or paused; a queue brought back after the app was closed only on the Audio tab.
    val hasQueue = player != null && player.active && (!player.restored || top == TopDestination.AUDIO)
    val miniAboveBar = hasQueue
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    val nowPlayingSheet = remember { NowPlayingSheet() }
    val barSpace = TabBarHeight + navBar + (if (miniAboveBar) MiniPlayerDockedHeight else 0.dp)
    // Where the last touch went down, as a fraction of the screen: a page opened from a poster
    // grows out of it and shrinks back into it (iOS 18's zoom transition).
    var tapOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val zoomOrigins = remember { mutableMapOf<Route, TransformOrigin>() }
    // While a poster's menu is open the whole shell blurs and dims behind it (iOS).
    val menuFocus = remember { mutableStateOf(0) }
    val menuDim by animateFloatAsState(if (menuFocus.value > 0) 1f else 0f, tween(220), label = "menuDim")
    val backSwipe = remember { BackSwipe() }
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val statusFade = state.route != Route.Home
    val tabReselect = remember { TabReselect() }
    val currentActions by rememberUpdatedState(actions)
    // The page transition is keyed by route alone, so the pages read the newest state through
    // this rather than being rebuilt with each change; a page that is not the current one (going
    // out, or uncovered by a swipe back) shows the state it last had.
    val latestState by rememberUpdatedState(state)
    val lastStates = remember { mutableStateMapOf<Route, AppUiState>() }
    SideEffect {
        lastStates[state.route] = state
        if (lastStates.size > MaxPageStates) {
            lastStates.keys.filter { it != state.route && it != state.previousRoute }.forEach { lastStates.remove(it) }
        }
    }
    fun stateFor(route: Route): AppUiState =
        if (route == latestState.route) latestState else lastStates[route] ?: latestState.copy(route = route)
    val isTabRoot = state.route == rootOf(top)
    CompositionLocalProvider(
        LocalNowPlayingSheet provides nowPlayingSheet,
        LocalMenuFocus provides menuFocus,
        LocalTabReselect provides tabReselect,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (menuDim > 0f) Modifier.blur((16f * menuDim).dp) else Modifier)
                .drawWithContent {
                    drawContent()
                    if (menuDim > 0f) drawRect(Color.Black.copy(alpha = 0.3f * menuDim))
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (size.width > 0 && size.height > 0) {
                            tapOrigin = TransformOrigin(down.position.x / size.width, down.position.y / size.height)
                        }
                    }
                },
        ) {
            // Android's stretch is off: the pages bounce as iOS lists do (iosOverscroll).
            CompositionLocalProvider(LocalBottomBarSpace provides barSpace, LocalOverscrollConfiguration provides null) {
                // Keyed by route: state updates within a page recompose it in place, while a route
                // change animates. Seekable, so a swipe back can move it with the finger.
                val routeState = remember { SeekableTransitionState(state.route) }
                val routeTransition = rememberTransition(routeState, label = "phoneRoute")
                val transitionIdle = routeState.currentState == routeState.targetState && !routeTransition.isRunning
                val background = MaterialTheme.colorScheme.background
                LaunchedEffect(state.route) {
                    when {
                        // Swiped back: the page that was uncovered is already in place.
                        backSwipe.committed -> {
                            if (routeState.currentState != state.route) routeState.snapTo(state.route)
                            backSwipe.reset()
                        }
                        backSwipe.from == null -> routeState.animateTo(state.route)
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        // Under Now Playing the page sinks back, smaller, rounded and dimmed, and
                        // comes forward again as Now Playing is pulled down (iOS's sheet stack).
                        .graphicsLayer {
                            val open = nowPlayingSheet.openFraction
                            val scale = 1f - 0.07f * open
                            scaleX = scale
                            scaleY = scale
                            shape = RoundedCornerShape(16.dp.toPx() * open)
                            clip = open > 0f
                        }
                        .drawWithContent {
                            drawContent()
                            // iOS 26's scroll edge: lists fade out under the clock and battery
                            // instead of running into them. Not over Home's hero, whose picture
                            // sets the status bar's own colours.
                            val fadeEnd = statusBarTop + StatusFade.toPx()
                            if (statusFade) drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = StatusFadeStrength),
                                    statusBarTop / fadeEnd to Color.Black.copy(alpha = StatusFadeStrength * 0.8f),
                                    1f to Color.Transparent,
                                    endY = fadeEnd,
                                ),
                                size = size.copy(height = fadeEnd),
                            )
                            val open = nowPlayingSheet.openFraction
                            if (open > 0f) drawRect(Color.Black.copy(alpha = 0.4f * open))
                        }
                        // On a tab's first page a sideways swipe changes tab; on a page opened from
                        // it, a swipe right goes back.
                        .swipeBetweenTabs(top, enabled = isTabRoot, onSelect = actions::selectTab)
                        .swipeBack(
                            swipe = backSwipe,
                            route = state.route,
                            previous = state.previousRoute,
                            allowed = !isTabRoot && state.canGoBack && state.previousRoute != null,
                            ready = transitionIdle,
                            transition = routeState,
                            onBack = actions::back,
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                ) {
                    routeTransition.AnimatedContent(
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val fade = fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140))
                            val from = initialState
                            val to = targetState
                            when {
                                // iOS's swipe back, driven by the finger: the page slides off to the
                                // right over the one it goes back to, which comes in from a little to
                                // the left, shaded at first.
                                from == backSwipe.from && to == backSwipe.to -> ContentTransform(
                                    targetContentEnter = slideInHorizontally(tween(SwipeMs, easing = LinearEasing)) { -(it * UnderlayLag).toInt() } +
                                        fadeIn(tween(SwipeMs, easing = LinearEasing), initialAlpha = 1f - UnderlayShade),
                                    initialContentExit = slideOutHorizontally(tween(SwipeMs, easing = LinearEasing)) { it },
                                    targetContentZIndex = -1f,
                                    sizeTransform = null,
                                )
                                from.top != to.top -> tabSlide(from = from.top, to = to.top, fade = fade)
                                to.isTitlePage() && to != from -> {
                                    val origin = tapOrigin.also { zoomOrigins[to] = it }
                                    (scaleIn(spring(dampingRatio = 0.86f, stiffness = 380f), initialScale = 0.2f, transformOrigin = origin) + fadeIn(tween(160))) togetherWith
                                        (fadeOut(tween(220)) + scaleOut(tween(300), targetScale = 0.94f))
                                }
                                from.isTitlePage() && !to.isTitlePage() -> {
                                    val origin = zoomOrigins[from] ?: TransformOrigin.Center
                                    (fadeIn(tween(220)) + scaleIn(tween(300), initialScale = 0.94f)) togetherWith
                                        (scaleOut(tween(260), targetScale = 0.2f, transformOrigin = origin) + fadeOut(tween(220, delayMillis = 60)))
                                }
                                else -> fade
                            }
                        },
                    ) { route ->
                        val swiped = backSwipe.from == route
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (swiped) {
                                        // The page being swiped away is solid, with a soft shadow down its left edge.
                                        Modifier
                                            .drawBehind {
                                                val edge = SwipeShadow.toPx()
                                                drawRect(
                                                    Brush.horizontalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                                                        startX = -edge,
                                                        endX = 0f,
                                                    ),
                                                    topLeft = Offset(-edge, 0f),
                                                    size = Size(edge, size.height),
                                                )
                                            }
                                            .background(background)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            pageStates.SaveableStateProvider(route.toString()) {
                                // iOS's edge bounce on every page; a movie or show page also closes
                                // when pulled down from its top, shrinking back toward its poster.
                                val pull = remember { mutableFloatStateOf(0f) }
                                val readPull = remember { { pull.floatValue } }
                                CompositionLocalProvider(LocalPagePull provides readPull) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .iosOverscroll(
                                                onPullClose = if (route.isTitlePage()) ({ currentActions.back() }) else null,
                                                pullState = pull,
                                            ),
                                    ) {
                                        PhonePage(state = stateFor(route), actions = currentActions)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (music != null) {
                val (viewModel, musicState) = music
                // The old Apple Music mini player (owner): a full-width strip docked on the tab
                // bar, in the bar's own material with a hairline between, sliding up from behind it.
                AnimatedVisibility(
                    visible = miniAboveBar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBar + TabBarHeight),
                    enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
                ) {
                    Column(Modifier.fillMaxWidth().background(DockColor)) {
                        Box(Modifier.fillMaxWidth().height(Hairline).background(IosColors.Separator))
                        MiniPlayerBar(
                            player = musicState,
                            actions = viewModel,
                            onOpen = { nowPlayingOpen = true },
                            docked = true,
                            // Its content fades back in as Now Playing comes down into it.
                            modifier = Modifier.graphicsLayer { alpha = 1f - nowPlayingSheet.openFraction },
                        )
                    }
                }
            }
            // Over the mini player, so it slides up from behind the bar.
            PhoneNavigationBar(
                selected = top,
                // The selected tab tapped again (iOS): from a page opened on it, back to its first
                // page; on its first page, up to the top.
                onSelect = { tab ->
                    if (tab != top || state.canGoBack && state.route != rootOf(tab)) actions.selectTab(tab) else tabReselect.fire()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (music != null) {
                val (viewModel, musicState) = music
                ShellNowPlaying(
                    viewModel = viewModel,
                    player = musicState,
                    open = nowPlayingOpen,
                    onDismiss = { nowPlayingOpen = false },
                    onShowAudio = { actions.selectTab(TopDestination.AUDIO) },
                    sheet = nowPlayingSheet,
                )
                CoverFlight(musicState.nowPlaying?.artUri)
            }
        }
    }
}

@Composable
private fun PhonePage(state: AppUiState, actions: AppActions) {
    val pageModifier = Modifier.fillMaxSize()
    when (val route = state.route) {
        Route.Home -> PhoneHomeScreen(
            home = state.home,
            scan = state.scan,
            permission = state.permission,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Library -> PhoneLibraryScreen(
            library = state.library,
            scan = state.scan,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Browse -> PhoneBrowseScreen(
            browse = state.browse,
            scan = state.scan,
            permission = state.permission,
            actions = actions,
            modifier = pageModifier,
        )
        is Route.BrowseDir -> PhoneBrowseDirScreen(
            dir = state.browseDir,
            actions = actions,
            modifier = pageModifier,
            permission = state.permission,
        )
        is Route.Playlist -> PhonePlaylistScreen(
            playlist = state.playlist?.takeIf { it.id == route.playlistId },
            actions = actions,
            modifier = pageModifier,
        )
        is Route.VideoDetails -> {
            // Guard against briefly showing the previous video while the new one loads.
            val details = state.videoDetails?.takeIf { it.id == route.videoId }
            if (details == null) {
                PhoneDetailsLoading(onBack = actions::back, modifier = pageModifier)
            } else {
                PhoneVideoDetailsScreen(details = details, actions = actions, modifier = pageModifier)
            }
        }
        is Route.ShowDetails -> {
            val details = state.showDetails?.takeIf { it.key == route.showKey }
            if (details == null) {
                PhoneDetailsLoading(onBack = actions::back, modifier = pageModifier)
            } else {
                PhoneShowDetailsScreen(details = details, actions = actions, modifier = pageModifier)
            }
        }
        Route.Search -> PhoneSearchScreen(
            search = state.search,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Audio -> AudioScreen(modifier = pageModifier)
        // statusBarsPadding (rather than contentPadding) consumes the inset, so it is not applied
        // twice whether Settings draws its own top app bar or a plain list.
        Route.Settings -> SettingsScreen(
            state = state.settings,
            actions = actions,
            layout = DeviceLayout.PHONE,
            modifier = pageModifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarSpace.current),
        )
    }
}

/**
 * The classic iOS tab bar (owner: the floating capsule was not appealing): docked full width along
 * the bottom edge over the page's foot, dark and see-through with a hairline on top, each tab an
 * icon over its name, the selected one in its colour (Video and Preferences blue, Audio red).
 */
@Composable
private fun PhoneNavigationBar(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxWidth()
            .background(DockColor),
    ) {
        Box(Modifier.fillMaxWidth().height(Hairline).background(IosColors.Separator))
        Row(Modifier.fillMaxWidth().height(TabBarHeight)) {
            TopDestination.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) tab.activeColor() else IosColors.SecondaryLabel
                // iOS 17's symbol bounce: the tapped tab's icon dips and springs back.
                val bounce = remember { Animatable(1f) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            onClick = {
                                if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    bounce.snapTo(TabBounceFrom)
                                    bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
                                }
                                onSelect(tab)
                            },
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    NovaIcon(
                        tab.tabIconRes(),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(25.dp)
                            .graphicsLayer {
                                scaleX = bounce.value
                                scaleY = bounce.value
                            },
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * A swipe back in progress: the page being swiped away ([from]) toward [to], and how far the
 * finger has moved it ([offset], px, of [width]); [committed] once it went far enough and back
 * was asked for.
 */
@Stable
private class BackSwipe {
    var from by mutableStateOf<Route?>(null)
    var to by mutableStateOf<Route?>(null)
    var offset by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var committed by mutableStateOf(false)

    /** Let go: springing back or carrying on, no longer following the finger. */
    var settling by mutableStateOf(false)

    val progress: Float get() = (offset / width).coerceIn(0f, 1f)

    fun reset() {
        from = null
        to = null
        offset = 0f
        committed = false
        settling = false
    }
}

/**
 * Swiping right on a page opened from a tab takes it back, as on iOS: the page transition is
 * seeked with the finger ([transition]), so the page follows it with the one it goes back to
 * underneath; letting go past a third of the width (or with a flick) finishes it and goes back,
 * otherwise it springs back into place. Anything that scrolls sideways itself (poster rows,
 * sliders) takes the swipe first. Only listens while [allowed], so on a tab's first page the
 * swipe is left to changing tabs; [ready] is false while another page change is running.
 */
@Composable
private fun Modifier.swipeBack(
    swipe: BackSwipe,
    route: Route,
    previous: Route?,
    allowed: Boolean,
    ready: Boolean,
    transition: SeekableTransitionState<Route>,
    onBack: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentRoute by rememberUpdatedState(route)
    val currentPrevious by rememberUpdatedState(previous)
    val back by rememberUpdatedState(onBack)
    val canStart by rememberUpdatedState(ready)
    fun following() = swipe.from == currentRoute && !swipe.committed && !swipe.settling
    fun springBack() {
        val from = swipe.from ?: return
        swipe.settling = true
        scope.launch {
            var fraction = transition.fraction
            while (fraction > 0.002f) {
                withFrameNanos { }
                fraction *= 0.72f
                transition.seekTo(fraction)
            }
            transition.snapTo(from)
            swipe.reset()
        }
    }
    return pointerInput(allowed) {
        if (!allowed) return@pointerInput
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                val to = currentPrevious
                if (canStart && swipe.from == null && to != null) {
                    swipe.from = currentRoute
                    swipe.to = to
                    swipe.width = size.width.toFloat().coerceAtLeast(1f)
                    swipe.offset = 0f
                }
            },
            onDragEnd = {
                if (!following()) return@detectHorizontalDragGestures
                val to = swipe.to ?: return@detectHorizontalDragGestures
                val velocity = tracker.calculateVelocity().x
                if (swipe.progress > SwipeCommitFraction || (velocity > SwipeFlingVelocity && swipe.offset > 0f)) {
                    swipe.settling = true
                    scope.launch {
                        transition.animateTo(to, tween(SwipeFinishMs, easing = LinearOutSlowInEasing))
                        swipe.committed = true
                        back()
                    }
                } else {
                    springBack()
                }
            },
            onDragCancel = { if (following()) springBack() },
        ) { change, amount ->
            if (!following()) return@detectHorizontalDragGestures
            val to = swipe.to ?: return@detectHorizontalDragGestures
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            swipe.offset = (swipe.offset + amount).coerceIn(0f, swipe.width)
            val fraction = swipe.progress
            scope.launch { transition.seekTo(fraction, to) }
        }
    }
}

/** How far the page underneath starts to the left (a share of the width) and how shaded. */
private const val UnderlayLag = 0.3f
private const val UnderlayShade = 0.35f
private const val SwipeCommitFraction = 0.33f
private const val SwipeFlingVelocity = 800f
private val SwipeShadow = 14.dp

/** The swipe-back transition's length (the finger drives it) and how long letting go finishes it. */
private const val SwipeMs = 300
private const val SwipeFinishMs = 220

/** Pages whose last state is kept for showing them again (going out, or under a swipe back). */
private const val MaxPageStates = 12

/** How far below the status bar the scroll-edge fade runs, and how dark it starts. */
private val StatusFade = 14.dp
private const val StatusFadeStrength = 0.6f

private val TabBarHeight = 52.dp

/** How small a tapped tab's icon dips before it springs back. */
private const val TabBounceFrom = 0.8f

/** iOS's dark tab bar material: near-black and a little see-through. */
private val DockColor = Color(0xF2121214)

/** The first page of [tab], as [AppActions.selectTab] opens it. */
private fun rootOf(tab: TopDestination): Route = when (tab) {
    TopDestination.VIDEO -> Route.Home
    TopDestination.AUDIO -> Route.Audio
    TopDestination.SETTINGS -> Route.Settings
    TopDestination.SEARCH -> Route.Search
}

/** A movie, episode or show page: the pages that zoom out of the poster that opened them. */
private fun Route.isTitlePage(): Boolean = this is Route.VideoDetails || this is Route.ShowDetails
