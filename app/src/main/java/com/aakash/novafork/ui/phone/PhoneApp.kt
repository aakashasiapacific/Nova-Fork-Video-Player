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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.audio.AudioScreen
import com.aakash.novafork.ui.audio.MiniPlayerBar
import com.aakash.novafork.ui.audio.MiniPlayerHeight
import com.aakash.novafork.ui.audio.ShellNowPlaying
import com.aakash.novafork.ui.audio.rememberMusicShell
import com.aakash.novafork.ui.common.GlassCircle
import com.aakash.novafork.ui.common.swipeBetweenTabs
import com.aakash.novafork.ui.common.tabSlide
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaTabCapsule
import com.aakash.novafork.ui.common.activeColor
import com.aakash.novafork.ui.common.tabIconRes
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.top
import com.aakash.novafork.ui.settings.SettingsScreen
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.launch

/**
 * Phone UI: a floating tab bar over one full-screen page per route. Dialogs, snackbars and system
 * back are handled by the app shell around this.
 */
@Composable
fun PhoneApp(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier) {
    // Pages leave the composition when navigated away from; this keeps their scroll positions
    // (and other saveable state) for when the user comes back.
    val pageStates = rememberSaveableStateHolder()
    val top = state.route.top
    // The tab bar shrinks while a page scrolls down and comes back when it scrolls up, reaches
    // its end or the tab changes.
    var barCollapsed by remember { mutableStateOf(false) }
    val collapseThreshold = with(LocalDensity.current) { BarCollapseScroll.toPx() }
    val barScroll = remember {
        object : NestedScrollConnection {
            var travel = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // The end of the page: nothing below to make room for, so the full bar comes back.
                if (available.y < 0f) {
                    barCollapsed = false
                    travel = 0f
                    return Offset.Zero
                }
                val dy = consumed.y
                if (dy == 0f) return Offset.Zero
                travel = if (travel * dy < 0) dy else travel + dy
                if (travel < -collapseThreshold) barCollapsed = true
                if (travel > collapseThreshold) barCollapsed = false
                return Offset.Zero
            }
        }
    }
    // The bar floats over the page (no strip reserved for it): pages run on behind it and pad
    // their lists by LocalBottomBarSpace.
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Music (the real app only): the mini player floats just above the tab bar on every tab,
    // and opens Now Playing over everything.
    val music = rememberMusicShell()
    val player = music?.second
    // The mini player, as in Apple Music on iOS 26: while a song plays it floats above the full
    // bar, and sits between the current tab and Search when the bar is shrunk. A paused song
    // only shows in the shrunk bar (which shrinks to show it); a queue brought back after the
    // app was closed only on the Audio tab.
    val hasQueue = player != null && player.active && (!player.restored || top == TopDestination.AUDIO)
    val playing = player?.playRequested == true
    LaunchedEffect(top) { barCollapsed = hasQueue && !playing }
    LaunchedEffect(hasQueue, playing) { if (hasQueue && !playing) barCollapsed = true }
    val miniAboveBar = hasQueue && playing && !barCollapsed
    val miniInBar = hasQueue && barCollapsed
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    val barSpace = TabBarHeight + BarTopMargin + BarBottomMargin + navBar +
        (if (miniAboveBar) MiniPlayerHeight + MiniPlayerGap else 0.dp)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalBottomBarSpace provides barSpace) {
            // Keyed by route, so state updates within a page recompose it in place, while a route
            // change crossfades and the outgoing page keeps showing the state it was built with.
            AnimatedContent(
                targetState = state,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(barScroll)
                    .swipeBetweenTabs(top, enabled = !state.canGoBack, onSelect = actions::selectTab)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                transitionSpec = {
                    tabSlide(
                        from = initialState.route.top,
                        to = targetState.route.top,
                        fade = fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140)),
                    )
                },
                contentKey = { it.route },
                label = "phoneRoute",
            ) { pageState ->
                pageStates.SaveableStateProvider(pageState.route.toString()) {
                    PhonePage(state = pageState, actions = actions)
                }
            }
        }
        PhoneNavigationBar(
            selected = top,
            collapsed = barCollapsed,
            onSelect = actions::selectTab,
            modifier = Modifier.align(Alignment.BottomCenter),
            miniPlayer = if (music != null && miniInBar) {
                { miniModifier -> MiniPlayerBar(player = music.second, actions = music.first, onOpen = { nowPlayingOpen = true }, modifier = miniModifier, compact = true) }
            } else {
                null
            },
        )
        if (music != null) {
            val (viewModel, musicState) = music
            AnimatedVisibility(
                visible = miniAboveBar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = BarSideMargin,
                        end = BarSideMargin,
                        bottom = navBar + BarBottomMargin + TabBarHeight + MiniPlayerGap,
                    ),
                enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.9f),
                exit = fadeOut(tween(140)),
            ) {
                MiniPlayerBar(player = musicState, actions = viewModel, onOpen = { nowPlayingOpen = true })
            }
            ShellNowPlaying(
                viewModel = viewModel,
                player = musicState,
                open = nowPlayingOpen,
                onDismiss = { nowPlayingOpen = false },
                onShowAudio = { actions.selectTab(TopDestination.AUDIO) },
            )
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
 * iOS 26's floating tab bar, as in the owner's recording: a dark-grey glass capsule held off the
 * screen edges, with a lighter grey pill behind the selected tab, whose icon and label take the
 * tab's colour. A finger dragged along it moves the pill, and the tab under the finger opens when
 * it lifts. While a page scrolls down it shrinks to two circles: the other section on the left
 * (Video or Audio, never the one shown) and Search on the right; scrolling back up brings back
 * the full bar.
 */
@Composable
private fun PhoneNavigationBar(
    selected: TopDestination,
    collapsed: Boolean,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** The mini player for the shrunk bar's middle, sized by the modifier it gets; null = none. */
    miniPlayer: (@Composable (Modifier) -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = BarSideMargin, end = BarSideMargin, top = BarTopMargin, bottom = BarBottomMargin)
            .height(TabBarHeight),
    ) {
        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.9f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.9f))
            },
            label = "tabBarCollapse",
        ) { small ->
            if (small) {
                // The other section (owner: never the one already shown): Video on Audio, Audio on
                // Video, Video elsewhere; then the mini player when there is one, then Search.
                val other = if (selected == TopDestination.VIDEO) TopDestination.AUDIO else TopDestination.VIDEO
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = { onSelect(other) }, description = other.label, size = TabBarHeight) {
                        NovaIcon(other.tabIconRes(), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    if (miniPlayer != null) {
                        miniPlayer(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .height(TabBarHeight),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (selected != TopDestination.SEARCH) {
                        GlassCircle(onClick = { onSelect(TopDestination.SEARCH) }, description = TopDestination.SEARCH.label, size = TabBarHeight) {
                            NovaIcon(TopDestination.SEARCH.tabIconRes(), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            } else {
                NovaTabCapsule(selected = selected, onSelect = onSelect, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private val TabBarHeight = 64.dp
private val BarCollapseScroll = 32.dp
private val BarSideMargin = 20.dp
private val BarTopMargin = 6.dp
private val BarBottomMargin = 10.dp
private val MiniPlayerGap = 10.dp

