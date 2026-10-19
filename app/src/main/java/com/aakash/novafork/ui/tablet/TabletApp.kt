package com.aakash.novafork.ui.tablet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.dimensFor
import com.aakash.novafork.ui.audio.AudioScreen
import com.aakash.novafork.ui.audio.MiniPlayerBar
import com.aakash.novafork.ui.audio.MiniPlayerHeight
import com.aakash.novafork.ui.audio.NowPlayingHost
import com.aakash.novafork.ui.audio.rememberMusicShell
import com.aakash.novafork.ui.audio.rememberUpNext
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.top
import com.aakash.novafork.ui.settings.SettingsScreen

/**
 * Tablet UI: a floating tab bar at the top centre over one page per route. Pages with artwork draw behind the status
 * bar; the others pad themselves. Dialogs, snackbars and system back belong to the app shell.
 */
@Composable
fun TabletApp(state: AppUiState, actions: AppActions, layout: DeviceLayout, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalDeviceLayout provides layout,
        LocalNovaDimens provides dimensFor(layout),
    ) {
        // Pages leave the composition when navigated away from; this keeps their scroll positions
        // for when the user comes back.
        val pageStates = rememberSaveableStateHolder()
        // Music (the real app only): a floating mini player at the bottom, Now Playing over all.
        val music = rememberMusicShell()
        // A queue brought back after the app was closed shows only on the Audio tab.
        val miniPlayerShown = music?.second?.let { it.active && (!it.restored || state.route.top == TopDestination.AUDIO) } == true
        var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
        val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // A Surface (not just a background) so text and icons get onBackground by default.
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalBottomBarSpace provides if (miniPlayerShown) MiniPlayerHeight + 24.dp + navBar else 0.dp,
                ) {
                    // Keyed by page, so updates within a page recompose it in place while a page change
                    // crossfades and the outgoing page keeps the state it was built with.
                    AnimatedContent(
                        targetState = state,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
                            ),
                        transitionSpec = { fadeIn(tween(durationMillis = 240, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 160)) },
                        contentAlignment = Alignment.TopStart,
                        contentKey = { it.route.pageKey() },
                        label = "tabletPage",
                    ) { pageState ->
                        pageStates.SaveableStateProvider(pageState.route.pageKey().toString()) {
                            // Pages with art at the top (Home's hero, details) run under the floating
                            // bar; the others start below it.
                            val underBar = pageState.route is Route.VideoDetails || pageState.route is Route.ShowDetails ||
                                (pageState.route == Route.Home && pageState.home.hero.isNotEmpty())
                            Box(if (underBar) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(top = TabletTopBarSpace)) {
                                TabletPage(state = pageState, actions = actions, layout = layout)
                            }
                        }
                    }
                }
                TabletTopBar(
                    selected = state.route.top,
                    onSelect = actions::selectTab,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                if (music != null) {
                    val (viewModel, player) = music
                    if (miniPlayerShown) {
                        MiniPlayerBar(
                            player = player,
                            actions = viewModel,
                            onOpen = { nowPlayingOpen = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = navBar + 16.dp)
                                .widthIn(max = 520.dp),
                        )
                    }
                    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
                    NowPlayingHost(
                        open = nowPlayingOpen,
                        player = player,
                        actions = viewModel,
                        favorite = player.nowPlaying?.songId in favorites,
                        upNext = rememberUpNext(viewModel, player),
                        onDismiss = { nowPlayingOpen = false },
                    )
                }
            }
        }
    }
}

/** Browse, its folders and playlists share one page so the sources pane stays put while they change. */
private fun Route.pageKey(): Any = when (this) {
    Route.Browse, is Route.BrowseDir, is Route.Playlist -> Route.Browse
    else -> this
}

@Composable
private fun TabletPage(state: AppUiState, actions: AppActions, layout: DeviceLayout) {
    val pageModifier = Modifier.fillMaxSize()
    when (val route = state.route) {
        Route.Home -> TabletHomeScreen(
            home = state.home,
            scan = state.scan,
            permission = state.permission,
            layout = layout,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Library -> TabletLibraryScreen(
            state = state,
            layout = layout,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Browse, is Route.BrowseDir, is Route.Playlist -> TabletBrowseScreen(
            state = state,
            layout = layout,
            actions = actions,
            modifier = pageModifier,
        )
        is Route.VideoDetails -> {
            // Never show the previous video while the new one loads.
            val details = state.videoDetails?.takeIf { it.id == route.videoId }
            if (details == null) {
                TabletDetailsLoading(onBack = actions::back, modifier = pageModifier)
            } else {
                TabletVideoDetailsScreen(details = details, layout = layout, actions = actions, modifier = pageModifier)
            }
        }
        is Route.ShowDetails -> {
            val details = state.showDetails?.takeIf { it.key == route.showKey }
            if (details == null) {
                TabletDetailsLoading(onBack = actions::back, modifier = pageModifier)
            } else {
                TabletShowDetailsScreen(details = details, layout = layout, actions = actions, modifier = pageModifier)
            }
        }
        Route.Search -> TabletSearchScreen(
            search = state.search,
            canGoBack = state.canGoBack,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Audio -> AudioScreen(modifier = pageModifier)
        Route.Settings -> SettingsScreen(
            state = state.settings,
            actions = actions,
            layout = layout,
            modifier = pageModifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = navigationBarBottom() + 16.dp),
        )
    }
}

@Composable
private fun TabletDetailsLoading(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        TabletLoading(Modifier.fillMaxSize())
        TabletBackButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = LocalNovaDimens.current.gutter, top = 12.dp),
        )
    }
}
