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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.dimensFor
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.top
import com.aakash.novafork.ui.settings.SettingsScreen

/**
 * Tablet UI: navigation rail plus one page per route. Pages with artwork draw behind the status
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
        // A Surface (not just a background) so text and icons get onBackground by default.
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                TabletNavRail(
                    selected = state.route.top,
                    searchSelected = state.route == Route.Search,
                    onSelect = actions::selectTab,
                    onSearch = { if (state.route != Route.Search) actions.navigate(Route.Search) },
                    modifier = Modifier.fillMaxHeight(),
                )
                // Keyed by page, so updates within a page recompose it in place while a page change
                // crossfades and the outgoing page keeps the state it was built with.
                AnimatedContent(
                    targetState = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(
                            WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.End),
                        ),
                    transitionSpec = { fadeIn(tween(durationMillis = 240, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 160)) },
                    contentAlignment = Alignment.TopStart,
                    contentKey = { it.route.pageKey() },
                    label = "tabletPage",
                ) { pageState ->
                    pageStates.SaveableStateProvider(pageState.route.pageKey().toString()) {
                        TabletPage(state = pageState, actions = actions, layout = layout)
                    }
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
