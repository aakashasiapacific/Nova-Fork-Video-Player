package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.audio.AudioScreen
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.top
import com.aakash.novafork.ui.settings.SettingsScreen
import com.aakash.novafork.ui.theme.NovaColors

/**
 * Phone UI: bottom navigation plus one full-screen page per route. Dialogs, snackbars and system
 * back are handled by the app shell around this.
 */
@Composable
fun PhoneApp(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier) {
    // Pages leave the composition when navigated away from; this keeps their scroll positions
    // (and other saveable state) for when the user comes back.
    val pageStates = rememberSaveableStateHolder()
    Scaffold(
        modifier = modifier,
        bottomBar = {
            PhoneNavigationBar(selected = state.route.top, onSelect = actions::selectTab)
        },
        // Only the bottom bar is reserved here: pages handle the status bar themselves so that
        // Home and details artwork can run behind it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        // Keyed by route, so state updates within a page recompose it in place, while a route
        // change crossfades and the outgoing page keeps showing the state it was built with.
        AnimatedContent(
            targetState = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            transitionSpec = { fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140)) },
            contentKey = { it.route },
            label = "phoneRoute",
        ) { pageState ->
            pageStates.SaveableStateProvider(pageState.route.toString()) {
                PhonePage(state = pageState, actions = actions)
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
            contentPadding = PaddingValues(bottom = 16.dp),
        )
    }
}

/**
 * Apple TV's tab bar, slim (52dp, not Material's 80dp): black with a hairline above it; the
 * selected tab has a dark-grey pill behind a white icon and a bold label, the others are grey.
 * Like iOS 26's bar, a finger dragged along it moves the pill, and the tab under the finger is
 * opened when it lifts.
 */
@Composable
private fun PhoneNavigationBar(selected: TopDestination, onSelect: (TopDestination) -> Unit) {
    val tabs = TopDestination.entries
    val currentOnSelect by rememberUpdatedState(onSelect)
    var dragX by remember { mutableStateOf<Float?>(null) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.background)) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(TabBarHeight)
                .pointerInput(tabs.size) {
                    fun tabAt(x: Float): TopDestination = tabs[(x / size.width * tabs.size).toInt().coerceIn(0, tabs.lastIndex)]
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = it.x },
                        onDragEnd = {
                            dragX?.let { currentOnSelect(tabAt(it)) }
                            dragX = null
                        },
                        onDragCancel = { dragX = null },
                    ) { change, amount ->
                        change.consume()
                        dragX = (dragX ?: change.position.x) + amount
                    }
                },
        ) {
            val slot = maxWidth / tabs.size
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val active = dragX?.let { x -> (x / widthPx * tabs.size).toInt().coerceIn(0, tabs.lastIndex) } ?: tabs.indexOf(selected)
            val pillWidth = slot - 10.dp
            val pillX by animateDpAsState(slot * active + 5.dp, label = "tabPill")
            // Apple TV's selected tab: a big dark-grey pill around icon and label.
            Box(
                Modifier
                    .offset(x = pillX, y = 2.dp)
                    .size(width = pillWidth, height = TabBarHeight - 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.surfaceContainerHigh),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == active
                    val tint = if (isActive) tab.activeColor() else colors.onSurfaceVariant
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = tab == selected,
                                onClick = { onSelect(tab) },
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TabIcon(tab = tab, active = isActive, tint = tint, modifier = Modifier.padding(top = 5.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) Color.White else tint,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private val TabBarHeight = 54.dp

/**
 * The selected tab's disc colour, as the owner chose: Video and Preferences iOS blue, Audio
 * Apple Music red, Search white.
 */
private fun TopDestination.activeColor(): Color = when (this) {
    TopDestination.VIDEO, TopDestination.SETTINGS -> NovaColors.IosBlue
    TopDestination.AUDIO -> NovaColors.AppleMusicRed
    TopDestination.SEARCH -> Color.White
}

/**
 * A tab's icon. The selected one "lights up" as in Apple TV's bar: a filled disc in the tab's
 * colour with the glyph on it (white, or black on Search's white disc), popping in with a short
 * spring; the others are plain grey glyphs.
 */
@Composable
private fun TabIcon(tab: TopDestination, active: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tabIcon",
    )
    Box(
        modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(tint),
            )
            NovaIcon(tab.iconRes(), contentDescription = null, tint = if (tab == TopDestination.SEARCH) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        } else {
            NovaIcon(tab.iconRes(), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

@DrawableRes
private fun TopDestination.iconRes(): Int = when (this) {
    TopDestination.VIDEO -> R.drawable.ic_tv
    TopDestination.AUDIO -> R.drawable.ic_music_notes
    TopDestination.SETTINGS -> R.drawable.ic_person
    TopDestination.SEARCH -> R.drawable.ic_search
}
