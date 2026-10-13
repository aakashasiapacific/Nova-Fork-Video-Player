package com.aakash.novafork.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.GlassNavBar
import com.aakash.novafork.ui.common.GlassPage
import com.aakash.novafork.ui.common.GlassTabBar
import com.aakash.novafork.ui.common.GlassTabItem
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.settings.SettingsScreen
import com.aakash.novafork.ui.theme.NovaColors

/** The three tabs of the phone tab bar; Search is the separate circle, Settings the account circle. */
private val PhoneTabs = listOf(TopDestination.HOME, TopDestination.LIBRARY, TopDestination.BROWSE)

private val PhoneTabItems = listOf(
    GlassTabItem("Home", R.drawable.ic_home),
    GlassTabItem("Library", R.drawable.ic_video_library),
    GlassTabItem("Browse", R.drawable.ic_folder),
)

/**
 * True for the page the user is on, false for a page that is animating out. Pages only take the
 * system back gesture while they are current.
 */
internal val LocalPhonePageActive = staticCompositionLocalOf { true }

/**
 * Phone UI (the iPhone Apple TV app): one page per route under the floating iOS 26 tab bar
 * (Home, Library, Browse + the Search circle). Settings is pushed from the account circle on Home
 * and Library. Dialogs, toasts and the app-level back stack are handled by the shell around this.
 */
@Composable
fun PhoneApp(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier) {
    // Pages leave the composition when navigated away from; this keeps their scroll positions
    // (and other saveable state) for when the user comes back.
    val pageStates = rememberSaveableStateHolder()
    // Settings is the root of its own stack; back returns to the tab whose account circle opened it.
    var settingsOrigin by rememberSaveable { mutableStateOf(TopDestination.HOME) }
    val history = remember { PhoneNavHistory() }
    val direction = history.onRoute(state.route)
    val dim = remember { PhoneContextDim() }
    val route = state.route

    val openSettings: (TopDestination) -> Unit = { from ->
        settingsOrigin = from
        actions.selectTab(TopDestination.SETTINGS)
    }
    val highlighted = if (state.tab == TopDestination.SETTINGS) settingsOrigin else state.tab
    val selectedIndex = if (route == Route.Search) -1 else PhoneTabs.indexOf(highlighted)

    CompositionLocalProvider(LocalPhoneContextDim provides dim) {
        Box(modifier.fillMaxSize()) {
            GlassPage(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NovaColors.background),
                chrome = {
                    GlassTabBar(
                        items = PhoneTabItems,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            val tab = PhoneTabs[index]
                            // Tapping the current tab pops it to its root, as on iOS.
                            if (tab == TopDestination.LIBRARY && state.tab == TopDestination.LIBRARY) {
                                actions.setLibraryFilter(LibraryFilter.ALL)
                            }
                            actions.selectTab(tab)
                        },
                        onSearch = { actions.navigate(Route.Search) },
                        searchSelected = route == Route.Search,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                },
            ) {
                // Keyed by route, so state updates within a page recompose it in place, while a
                // route change pushes, pops or crossfades and the outgoing page keeps the state it
                // was built with.
                AnimatedContent(
                    targetState = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    transitionSpec = { pageTransition(direction) },
                    contentKey = { it.route },
                    label = "phoneRoute",
                ) { pageState ->
                    CompositionLocalProvider(LocalPhonePageActive provides (pageState.route == route)) {
                        pageStates.SaveableStateProvider(pageState.route.toString()) {
                            PhonePage(
                                state = pageState,
                                actions = actions,
                                onAccount = openSettings,
                                onCloseSettings = { actions.selectTab(settingsOrigin) },
                            )
                        }
                    }
                }
            }
            PhoneContextDimOverlay(dim, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PhonePage(
    state: AppUiState,
    actions: AppActions,
    onAccount: (TopDestination) -> Unit,
    onCloseSettings: () -> Unit,
) {
    val pageModifier = Modifier.fillMaxSize()
    val resumeEnabled = state.settings.settings.resumePlayback
    when (val route = state.route) {
        Route.Home -> PhoneHomeScreen(
            home = state.home,
            scan = state.scan,
            permission = state.permission,
            resumeEnabled = resumeEnabled,
            actions = actions,
            onAccount = { onAccount(TopDestination.HOME) },
            modifier = pageModifier,
        )
        Route.Library -> PhoneLibraryScreen(
            library = state.library,
            recentlyAdded = state.home.recentlyAdded,
            streams = state.browse.streams,
            scan = state.scan,
            actions = actions,
            onAccount = { onAccount(TopDestination.LIBRARY) },
            modifier = pageModifier,
        )
        Route.Browse -> PhoneBrowseScreen(
            browse = state.browse,
            scan = state.scan,
            permission = state.permission,
            actions = actions,
            modifier = pageModifier,
        )
        is Route.BrowseDir -> {
            val dir = state.browseDir?.takeIf { it.sourceId == route.sourceId && it.path == route.path }
            if (dir == null) {
                PhoneMissingPage(onBack = actions::back, modifier = pageModifier, what = "folder")
            } else {
                PhoneBrowseDirScreen(dir = dir, actions = actions, modifier = pageModifier)
            }
        }
        is Route.VideoDetails -> {
            // Guard against briefly showing the previous video while the new one loads.
            val details = state.videoDetails?.takeIf { it.id == route.videoId }
            if (details == null) {
                PhoneMissingPage(onBack = actions::back, modifier = pageModifier, what = "video")
            } else {
                PhoneVideoDetailsScreen(
                    details = details,
                    // Saved streams are removable from their page (VideoDetailsUi has no source id).
                    streamSourceId = state.browse.streams.firstOrNull { it.id == details.id }?.sourceId,
                    resumeEnabled = resumeEnabled,
                    actions = actions,
                    modifier = pageModifier,
                )
            }
        }
        is Route.ShowDetails -> {
            val details = state.showDetails?.takeIf { it.key == route.showKey }
            if (details == null) {
                PhoneMissingPage(onBack = actions::back, modifier = pageModifier, what = "show")
            } else {
                PhoneShowDetailsScreen(
                    details = details,
                    resumeEnabled = resumeEnabled,
                    actions = actions,
                    modifier = pageModifier,
                )
            }
        }
        Route.Search -> PhoneSearchScreen(
            search = state.search,
            recentlyAdded = state.home.recentlyAdded,
            actions = actions,
            modifier = pageModifier,
        )
        Route.Settings -> PhoneSettingsPage(
            state = state,
            actions = actions,
            onBack = onCloseSettings,
            modifier = pageModifier,
        )
    }
}

/**
 * Settings as a pushed page: the shared SettingsScreen under a floating bar with a back circle.
 * The bar's glass and inline title fade in once the list has scrolled (measured through nested
 * scrolling, since the list belongs to SettingsScreen).
 */
@Composable
private fun PhoneSettingsPage(state: AppUiState, actions: AppActions, onBack: () -> Unit, modifier: Modifier) {
    if (LocalPhonePageActive.current) BackHandler(onBack = onBack)
    val thresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    val scroll = remember { ScrollTracker() }
    GlassPage(
        modifier = modifier
            .fillMaxSize()
            .background(NovaColors.background),
        chrome = {
            GlassNavBar(
                title = "Settings",
                showBackground = scroll.offset > thresholdPx,
                leading = { BackButton(onClick = onBack) },
            )
        },
    ) {
        SettingsScreen(
            state = state.settings,
            actions = actions,
            layout = DeviceLayout.PHONE,
            modifier = Modifier
                .fillMaxSize()
                .background(NovaColors.background)
                .nestedScroll(scroll),
            contentPadding = PaddingValues(bottom = glassTabBarClearance()),
        )
    }
}

/** Follows how far a nested scrollable has scrolled from its top (sum of consumed scroll). */
private class ScrollTracker : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        offset = (offset - consumed.y).coerceAtLeast(0f)
        // Pulling past the top means the list is at its start, whatever the running sum says.
        if (available.y > 0f) offset = 0f
        return Offset.Zero
    }
}

// ───────────────────────────── Page transitions ─────────────────────────────

private enum class NavDirection { Push, Pop, Switch }

/**
 * A mirror of the back stack as the phone has seen it, to tell a push from a pop or a tab
 * switch: going back to a route already in it is a pop, a tab root starts over (switch), anything
 * else is a push. Called during composition, so it is idempotent for the same route.
 */
private class PhoneNavHistory {
    private val stack = ArrayList<Route>()
    private var direction = NavDirection.Switch

    fun onRoute(route: Route): NavDirection {
        if (stack.lastOrNull() == route) return direction
        val existing = stack.indexOf(route)
        direction = when {
            stack.isEmpty() -> {
                stack += route
                NavDirection.Switch
            }
            existing >= 0 -> {
                while (stack.size > existing + 1) stack.removeAt(stack.lastIndex)
                NavDirection.Pop
            }
            route == Route.Home || route == Route.Library || route == Route.Browse -> {
                stack.clear()
                stack += route
                NavDirection.Switch
            }
            else -> {
                stack += route
                NavDirection.Push
            }
        }
        return direction
    }
}

/** iOS push (new page slides in over the old one, which drifts left) and pop; tabs crossfade. */
private fun pageTransition(direction: NavDirection): ContentTransform = when (direction) {
    NavDirection.Push -> phonePushTransition()
    NavDirection.Pop -> phonePopTransition()
    NavDirection.Switch ->
        fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140))
}
