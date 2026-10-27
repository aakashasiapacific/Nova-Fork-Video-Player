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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.aakash.novafork.ui.audio.PlayingBars
import com.aakash.novafork.ui.audio.ShellNowPlaying
import com.aakash.novafork.ui.audio.miniContentAlpha
import com.aakash.novafork.ui.audio.rememberMusicShell
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalMenuFocus
import com.aakash.novafork.ui.common.LocalPagePull
import com.aakash.novafork.ui.common.LocalTabReselect
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.TabReselect
import com.aakash.novafork.ui.common.softTopEdge
import com.aakash.novafork.ui.common.activeColor
import com.aakash.novafork.ui.common.iosOverscroll
import com.aakash.novafork.ui.common.rememberScreenCornerRadius
import com.aakash.novafork.ui.common.tabIconRes
import com.aakash.novafork.ui.common.tabSlide
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.top
import com.aakash.novafork.ui.settings.SettingsScreen
import com.aakash.novafork.ui.settings.SettingsSectionScreen
import com.aakash.novafork.ui.theme.NovaColors
import kotlin.math.roundToInt
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
    // queue, playing or paused, on the Audio tab only (owner); elsewhere the Audio tab's icon
    // moves with the song instead.
    val hasQueue = player != null && player.active && top == TopDestination.AUDIO
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
    // iOS 15's scroll edge: at the very end of a page the bars turn clear (their line goes too);
    // as soon as content scrolls back under them they are dark again.
    var atPageEnd by remember { mutableStateOf(false) }
    // iOS 26/27: scrolling down a page tucks the tab names away (icons only, a shorter bar);
    // scrolling back up, or reaching the end, brings them back.
    var barCompact by remember { mutableStateOf(false) }
    LaunchedEffect(state.route) {
        atPageEnd = false
        barCompact = false
    }
    val pageEnd = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                when {
                    // Pushed on past the end: nothing more below.
                    available.y < 0f -> {
                        atPageEnd = true
                        barCompact = false
                    }
                    consumed.y != 0f -> {
                        atPageEnd = false
                        if (consumed.y < -CompactScrollPx) barCompact = true else if (consumed.y > CompactScrollPx) barCompact = false
                    }
                }
                return Offset.Zero
            }
        }
    }
    val barClear by animateFloatAsState(if (atPageEnd) 1f else 0f, tween(220), label = "barClear")
    val barShrink by animateFloatAsState(
        targetValue = if (barCompact) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "barShrink",
    )
    // The sunk page's corners follow the screen's own (iOS 27's concentric corners).
    val screenCorner = rememberScreenCornerRadius()
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
                            shape = RoundedCornerShape(screenCorner.toPx() * open)
                            clip = open > 0f
                        }
                        .drawWithContent {
                            drawContent()
                            // iOS 26's scroll edge: lists fade out under the clock and battery
                            // instead of running into them. Not over Home's hero, whose picture
                            // sets the status bar's own colours.
                            val fadeEnd = statusBarTop + StatusEdgeDepth.toPx()
                            if (statusFade) drawRect(softTopEdge(), size = size.copy(height = fadeEnd))
                            val open = nowPlayingSheet.openFraction
                            if (open > 0f) drawRect(Color.Black.copy(alpha = 0.4f * open))
                        }
                        // On a tab's first page a sideways swipe changes tab; on a page opened from
                        // it, a swipe right goes back. Either way both pages follow the finger.
                        .swipePages(
                            swipe = backSwipe,
                            route = state.route,
                            top = top,
                            tabs = isTabRoot,
                            previous = state.previousRoute,
                            allowed = isTabRoot || (state.canGoBack && state.previousRoute != null),
                            ready = transitionIdle,
                            transition = routeState,
                            onBack = actions::back,
                            onSelectTab = actions::selectTab,
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
                                // Swiping between tabs: the pages move side by side with the finger,
                                // the next one coming in from the side it lies on.
                                from == backSwipe.from && to == backSwipe.to && backSwipe.tab != null -> {
                                    val side = -backSwipe.sign
                                    ContentTransform(
                                        targetContentEnter = slideInHorizontally(tween(SwipeMs, easing = LinearEasing)) { it * side },
                                        initialContentExit = slideOutHorizontally(tween(SwipeMs, easing = LinearEasing)) { -it * side },
                                        sizeTransform = null,
                                    )
                                }
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
                        val swipedBack = swiped && backSwipe.tab == null
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (swiped) {
                                        // The page being swiped away is solid, with a soft shadow down its left edge.
                                        Modifier
                                            .drawBehind {
                                                if (!swipedBack) return@drawBehind
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
                                                // Anywhere else, pulling down past the top looks for new videos, silently.
                                                onPullRefresh = if (route.isTitlePage()) null else currentActions::refreshQuietly,
                                            )
                                            // Inside the bounce, so it still sees the push past the end.
                                            .nestedScroll(pageEnd),
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
                        .padding(bottom = navBar + TabBarHeight)
                        // Down with the bar while its names are tucked away.
                        .graphicsLayer { translationY = (TabBarHeight - CompactTabBarHeight).toPx() * barShrink },
                    enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
                ) {
                    // Pulled up, it opens Now Playing with the finger (Apple Music); let go early
                    // and it settles back.
                    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
                    val pullScope = rememberCoroutineScope()
                    val pullState = rememberDraggableState { delta ->
                        nowPlayingSheet.pull = (nowPlayingSheet.pull - delta / screenPx).coerceIn(0f, 1f)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                state = pullState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    // The finger's speed, in sheet heights a second (up is positive), carries on into
                                    // whichever way it settles.
                                    val upward = -velocity / screenPx
                                    if (nowPlayingSheet.pull > MiniPullOpen || velocity < -MiniFlingVelocity) {
                                        nowPlayingSheet.releaseVelocity = upward
                                        nowPlayingOpen = true
                                    } else {
                                        pullScope.launch {
                                            animate(nowPlayingSheet.pull, 0f, upward, spring(dampingRatio = 1f, stiffness = 500f)) { value, _ ->
                                                nowPlayingSheet.pull = value
                                            }
                                        }
                                    }
                                },
                            )
                            .drawBehind { drawRect(DockColor.copy(alpha = DockColor.alpha * (1f - barClear))) },
                    ) {
                        Box(Modifier.fillMaxWidth().height(Hairline).graphicsLayer { alpha = 1f - barClear }.background(IosColors.Separator))
                        MiniPlayerBar(
                            player = musicState,
                            actions = viewModel,
                            onOpen = { nowPlayingOpen = true },
                            docked = true,
                            // Its title and buttons fade in only over the last part of Now Playing coming
                            // down into it (and out over the first part of its going up).
                            modifier = Modifier.graphicsLayer { alpha = miniContentAlpha(nowPlayingSheet.openFraction) },
                        )
                    }
                }
            }
            // Over the mini player, so it slides up from behind the bar.
            PhoneNavigationBar(
                clear = { barClear },
                shrink = { barShrink },
                selected = top,
                musicPlaying = player?.isPlaying == true,
                onHoldAudio = if (player != null && player.active) ({ nowPlayingOpen = true }) else null,
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
        Route.ContinueWatching -> PhoneContinueWatchingScreen(
            cards = state.home.continueWatching,
            actions = actions,
            modifier = pageModifier,
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
            contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
        )
        is Route.SettingsPage -> SettingsSectionScreen(
            section = route.section,
            state = state.settings,
            actions = actions,
            modifier = pageModifier,
            contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
        )
    }
}

/**
 * The classic iOS tab bar (owner: the floating capsule was not appealing): docked full width along
 * the bottom edge over the page's foot, dark and see-through with a hairline on top, each tab an
 * icon over its name, the selected one in its colour (Audio red, the rest blue) on a soft glass pill (iOS 26/27) that
 * slides to the next selected tab, stretching on the way.
 */
@Composable
private fun PhoneNavigationBar(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** 0 dark (content under it) to 1 clear (at the end of the page). */
    clear: () -> Float = { 0f },
    /** 0 full to 1 compact: the names tucked away and the bar shorter, while a page scrolls down. */
    shrink: () -> Float = { 0f },
    /** A song plays: the Audio tab's icon becomes the "now playing" bars, moving with it. */
    musicPlaying: Boolean = false,
    /** Holding the Audio tab opens Now Playing from any tab (null: nothing is queued). */
    onHoldAudio: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tabs = TopDestination.entries
    // iOS 26's liquid pill: the edge in front races to the new tab, the one behind follows a beat
    // later, so the pill stretches as it travels and settles back into shape. In tab slots.
    val index = tabs.indexOf(selected)
    var lastIndex by remember { mutableIntStateOf(index) }
    val forward = index >= lastIndex
    SideEffect { lastIndex = index }
    val fast = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
    val slow = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    val pillLeft by animateFloatAsState(index.toFloat(), animationSpec = if (forward) slow else fast, label = "tabPillLeft")
    val pillRight by animateFloatAsState(index + 1f, animationSpec = if (forward) fast else slow, label = "tabPillRight")
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(DockColor.copy(alpha = DockColor.alpha * (1f - clear()))) },
    ) {
        Box(Modifier.fillMaxWidth().height(Hairline).graphicsLayer { alpha = 1f - clear() }.background(IosColors.Separator))
        Row(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val full = TabBarHeight.toPx()
                    val height = (full + (CompactTabBarHeight.toPx() - full) * shrink()).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, height) { placeable.place(0, 0) }
                }
                .clipToBounds()
                .drawBehind {
                    val slot = size.width / tabs.size
                    val inset = TabPillInset.toPx()
                    val top = TabPillInsetVertical.toPx()
                    val pillHeight = size.height - top * 2
                    val pillWidth = (pillRight - pillLeft) * slot - inset * 2
                    if (pillHeight > 0f && pillWidth > 0f) {
                        drawRoundRect(
                            color = TabPillColor,
                            topLeft = Offset(pillLeft * slot + inset, top),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
                        )
                    }
                },
        ) {
            tabs.forEach { tab ->
                val active = tab == selected
                val tint = if (active) tab.activeColor() else IosColors.SecondaryLabel
                // iOS 17's symbol bounce: the tapped tab's icon dips and springs back.
                val bounce = remember { Animatable(1f) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            this.selected = active
                            role = Role.Tab
                        }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    bounce.snapTo(TabBounceFrom)
                                    bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
                                }
                                onSelect(tab)
                            },
                            onLongClickLabel = if (tab == TopDestination.AUDIO && onHoldAudio != null) "Open Now Playing" else null,
                            onLongClick = if (tab == TopDestination.AUDIO && onHoldAudio != null) {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onHoldAudio()
                                }
                            } else {
                                null
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // From the top, so the icon stays put as the bar shortens under its name.
                    verticalArrangement = Arrangement.Top,
                ) {
                    val iconModifier = Modifier
                        .padding(top = TabIconTop)
                        .size(25.dp)
                        .graphicsLayer {
                            scaleX = bounce.value
                            scaleY = bounce.value
                        }
                    if (tab == TopDestination.AUDIO && musicPlaying) {
                        Box(iconModifier, contentAlignment = Alignment.Center) {
                            // Apple Music's red, on any tab (owner).
                            PlayingBars(playing = true, color = NovaColors.AppleMusicRed, modifier = Modifier.height(20.dp), barWidth = 3.5.dp)
                        }
                    } else {
                        NovaIcon(tab.tabIconRes(), contentDescription = null, tint = tint, modifier = iconModifier)
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .graphicsLayer {
                                val tucked = shrink()
                                alpha = 1f - tucked
                                translationY = TabLabelDrop.toPx() * tucked
                            },
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * A page swipe in progress: the page being swiped away ([from]) toward [to], and how far the
 * finger has moved it ([offset], px, of [width], in the direction [sign]: 1 right, -1 left);
 * [tab] when it goes to another tab rather than back; [committed] once it went far enough and
 * the change was asked for.
 */
@Stable
private class BackSwipe {
    var from by mutableStateOf<Route?>(null)
    var to by mutableStateOf<Route?>(null)
    var tab by mutableStateOf<TopDestination?>(null)
    var sign by mutableIntStateOf(1)
    var offset by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var committed by mutableStateOf(false)

    /** Let go: springing back or carrying on, no longer following the finger. */
    var settling by mutableStateOf(false)

    val progress: Float get() = (offset * sign / width).coerceIn(0f, 1f)

    fun reset() {
        from = null
        to = null
        tab = null
        sign = 1
        offset = 0f
        committed = false
        settling = false
    }
}

/**
 * Page swipes, driven by the finger through the page transition ([transition] is seeked):
 * on a tab's first page ([tabs]) a sideways swipe brings in the next or previous tab beside it,
 * like pages; on a page opened from a tab a swipe right takes it back, as on iOS, with the page
 * it goes back to underneath. Letting go past a third of the width (or with a flick) finishes
 * it, otherwise it springs back; nothing jumps either way. Anything that scrolls sideways itself
 * (poster rows, sliders) takes the swipe first. [ready] is false while another page change runs.
 */
@Composable
private fun Modifier.swipePages(
    swipe: BackSwipe,
    route: Route,
    top: TopDestination,
    tabs: Boolean,
    previous: Route?,
    allowed: Boolean,
    ready: Boolean,
    transition: SeekableTransitionState<Route>,
    onBack: () -> Unit,
    onSelectTab: (TopDestination) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentRoute by rememberUpdatedState(route)
    val currentTop by rememberUpdatedState(top)
    val onTabs by rememberUpdatedState(tabs)
    val currentPrevious by rememberUpdatedState(previous)
    val back by rememberUpdatedState(onBack)
    val selectTab by rememberUpdatedState(onSelectTab)
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
        // Which way it goes is known only once the finger has moved.
        var deciding = false
        // Past the point where letting go finishes it: a tick says so (and again if it goes back).
        var armed = false
        detectHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                deciding = canStart && swipe.from == null
                armed = false
            },
            onDragEnd = {
                deciding = false
                if (!following()) return@detectHorizontalDragGestures
                val to = swipe.to ?: return@detectHorizontalDragGestures
                val velocity = tracker.calculateVelocity().x * swipe.sign
                if (swipe.progress > SwipeCommitFraction || (velocity > SwipeFlingVelocity && swipe.progress > 0f)) {
                    swipe.settling = true
                    val tab = swipe.tab
                    if (tab != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        transition.animateTo(to, tween(SwipeFinishMs, easing = LinearOutSlowInEasing))
                        swipe.committed = true
                        if (tab != null) selectTab(tab) else back()
                    }
                } else {
                    springBack()
                }
            },
            onDragCancel = {
                deciding = false
                if (following()) springBack()
            },
        ) { change, amount ->
            if (deciding && amount != 0f) {
                deciding = false
                val sign = if (amount < 0f) -1 else 1
                if (onTabs) {
                    // Left brings in the next tab, right the one before.
                    val entries = TopDestination.entries
                    val tab = entries.getOrNull(entries.indexOf(currentTop) - sign)
                    if (tab != null) {
                        swipe.tab = tab
                        swipe.to = rootOf(tab)
                    }
                } else if (sign > 0) {
                    swipe.to = currentPrevious
                }
                if (swipe.to != null) {
                    swipe.from = currentRoute
                    swipe.sign = sign
                    swipe.width = size.width.toFloat().coerceAtLeast(1f)
                    swipe.offset = 0f
                } else {
                    swipe.reset()
                }
            }
            if (!following()) return@detectHorizontalDragGestures
            val to = swipe.to ?: return@detectHorizontalDragGestures
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val reach = swipe.width * swipe.sign
            swipe.offset = (swipe.offset + amount).let { if (swipe.sign > 0) it.coerceIn(0f, reach) else it.coerceIn(reach, 0f) }
            val fraction = swipe.progress
            if ((fraction > SwipeCommitFraction) != armed) {
                armed = !armed
                if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
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


private val TabBarHeight = 52.dp

/** The tab bar with its names tucked away (icons only), while a page scrolls down. */
private val CompactTabBarHeight = 38.dp

/** Scrolled this far in one step (px), the tab bar tucks its names away or brings them back. */
private const val CompactScrollPx = 2f

/** Where a tab's icon sits from the bar's top, the same full or compact. */
private val TabIconTop = 6.dp

/** How far a tab's name sinks as it fades away. */
private val TabLabelDrop = 6.dp

/** The selected tab's glass pill: inset from its slot's sides and from the bar's top and bottom. */
private val TabPillInset = 8.dp
private val TabPillInsetVertical = 4.dp
private val TabPillColor = Color.White.copy(alpha = 0.1f)

/** Pulled up past this share of the screen (or flicked), the mini player opens Now Playing. */
private const val MiniPullOpen = 0.18f
private const val MiniFlingVelocity = 900f

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

/**
 * How far below the status bar the page's always-on soft edge runs: shallow, so a large title at
 * the top of a page is not dimmed by it (the deeper [com.aakash.novafork.ui.common.SoftEdgeDepth]
 * only comes in as a page scrolls).
 */
private val StatusEdgeDepth = 20.dp
