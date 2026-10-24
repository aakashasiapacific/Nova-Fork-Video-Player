package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.aakash.novafork.ui.audio.MiniPlayerHeight
import com.aakash.novafork.ui.audio.NowPlayingSheet
import com.aakash.novafork.ui.audio.ShellNowPlaying
import com.aakash.novafork.ui.audio.rememberMusicShell
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalMenuFocus
import com.aakash.novafork.ui.common.NovaIcon
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
    val barSpace = TabBarHeight + navBar +
        (if (miniAboveBar) MiniPlayerHeight + MiniPlayerGap * 2 else 0.dp)
    // Where the last touch went down, as a fraction of the screen: a page opened from a poster
    // grows out of it and shrinks back into it (iOS 18's zoom transition).
    var tapOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val zoomOrigins = remember { mutableMapOf<Route, TransformOrigin>() }
    // While a poster's menu is open the whole shell blurs and dims behind it (iOS).
    val menuFocus = remember { mutableStateOf(0) }
    val menuDim by animateFloatAsState(if (menuFocus.value > 0) 1f else 0f, tween(220), label = "menuDim")
    CompositionLocalProvider(
        LocalNowPlayingSheet provides nowPlayingSheet,
        LocalMenuFocus provides menuFocus,
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
                // Keyed by route, so state updates within a page recompose it in place, while a route
                // change crossfades and the outgoing page keeps showing the state it was built with.
                AnimatedContent(
                    targetState = state,
                    modifier = Modifier
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
                            val open = nowPlayingSheet.openFraction
                            if (open > 0f) drawRect(Color.Black.copy(alpha = 0.4f * open))
                        }
                        .swipeBetweenTabs(top, enabled = !state.canGoBack, onSelect = actions::selectTab, onBack = actions::back)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    transitionSpec = {
                        val fade = fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140))
                        val from = initialState.route
                        val to = targetState.route
                        when {
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
                    contentKey = { it.route },
                    label = "phoneRoute",
                ) { pageState ->
                    pageStates.SaveableStateProvider(pageState.route.toString()) {
                        // iOS's edge bounce on every page; a movie or show page also closes
                        // when pulled down from its top, shrinking back toward its poster.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .iosOverscroll(onPullClose = if (pageState.route.isTitlePage()) actions::back else null),
                        ) {
                            PhonePage(state = pageState, actions = actions)
                        }
                    }
                }
            }
            PhoneNavigationBar(
                selected = top,
                onSelect = actions::selectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (music != null) {
                val (viewModel, musicState) = music
                AnimatedVisibility(
                    visible = miniAboveBar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = MiniPlayerGap,
                            end = MiniPlayerGap,
                            bottom = navBar + TabBarHeight + MiniPlayerGap,
                        ),
                    enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.9f),
                    exit = fadeOut(tween(140)),
                ) {
                    MiniPlayerBar(
                        player = musicState,
                        actions = viewModel,
                        onOpen = { nowPlayingOpen = true },
                        // It fades back in as Now Playing comes down into it.
                        modifier = Modifier.graphicsLayer { alpha = 1f - nowPlayingSheet.openFraction },
                    )
                }
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
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            onClick = {
                                if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            },
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    NovaIcon(tab.tabIconRes(), contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
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

private val TabBarHeight = 52.dp
private val MiniPlayerGap = 8.dp

/** iOS's dark tab bar material: near-black and a little see-through. */
private val DockColor = Color(0xF2121214)

/** A movie, episode or show page: the pages that zoom out of the poster that opened them. */
private fun Route.isTitlePage(): Boolean = this is Route.VideoDetails || this is Route.ShowDetails
