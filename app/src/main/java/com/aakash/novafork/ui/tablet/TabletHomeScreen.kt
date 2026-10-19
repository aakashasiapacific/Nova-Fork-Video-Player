package com.aakash.novafork.ui.tablet

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.DeviceAccessCard
import com.aakash.novafork.ui.common.HomeLibraryLinks
import com.aakash.novafork.ui.common.LandscapeArt
import com.aakash.novafork.ui.common.LocalHeroPreviewEnabled
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.deviceAccessNotice
import com.aakash.novafork.ui.common.startScrim
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.phone.PhoneHeroPager
import kotlinx.coroutines.delay

/** Scrolled this far, the hero is mostly off screen: its preview stops. */
private const val HERO_VISIBLE_SCROLL_PX = 600

@Composable
internal fun TabletHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    TabletPullToRefresh(
        scanning = scan.running,
        onRefresh = actions::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        if (home.isEmpty) {
            HomeOnboarding(scan = scan, permission = permission, layout = layout, actions = actions)
        } else {
            HomeFeed(home = home, scan = scan, permission = permission, layout = layout, actions = actions)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Feed
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeFeed(home: HomeUi, scan: ScanUi, permission: PermissionUi, layout: DeviceLayout, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState { home.hero.size }
    // Short clips play with sound; the hero's mute button silences them.
    var muted by rememberSaveable { mutableStateOf(false) }
    val previewEnabled = LocalHeroPreviewEnabled.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val heroOnScreen by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < HERO_VISIBLE_SCROLL_PX }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The iPad's hero: most of the screen, leaving the top of the first row in view.
        val heroHeight = if (constraints.hasBoundedHeight) (maxHeight * 0.72f).coerceIn(420.dp, 760.dp) else 560.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navigationBarBottom() + dims.sectionSpacing),
        ) {
            if (home.hero.isNotEmpty()) {
                item(key = "hero", contentType = "hero") {
                    PhoneHeroPager(
                        heroes = home.hero,
                        height = heroHeight,
                        pagerState = pagerState,
                        previewEnabled = previewEnabled,
                        previewAllowed = heroOnScreen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                        muted = muted,
                        onToggleMute = { muted = !muted },
                        actions = actions,
                        wide = true,
                        contentStart = dims.gutter,
                    )
                }
            } else {
                item(key = "header", contentType = "header") {
                    Column(Modifier.statusBarsPadding()) {
                        ScanBanner(scan)
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 24.dp),
                        )
                    }
                }
            }
            val deviceNotice = deviceAccessNotice(permission)
            if (deviceNotice != null) {
                // Shown above folders' and shares' posters too: device videos missing must never be silent.
                item(key = "deviceAccess", contentType = "deviceAccess") {
                    DeviceAccessCard(
                        notice = deviceNotice,
                        onAllow = actions::requestPermission,
                        onOpenSettings = actions::openAppSettings,
                        modifier = Modifier
                            .padding(start = dims.gutter, end = dims.gutter, top = 24.dp)
                            .widthIn(max = 720.dp),
                    )
                }
            }
            if (home.continueWatching.isNotEmpty()) {
                item(key = "continue", contentType = "landscapeRow") {
                    HomeSection(title = "Continue Watching") {
                        ContinueWatchingRow(home.continueWatching, actions)
                    }
                }
            }
            if (home.watchlist.isNotEmpty()) {
                item(key = "watchlist", contentType = "landscapeRow") {
                    HomeSection(title = "Watchlist") {
                        WatchlistRow(home.watchlist, actions)
                    }
                }
            }
            item(key = "library", contentType = "library") {
                HomeLibraryLinks(actions, Modifier.widthIn(max = 720.dp))
            }
        }
        if (home.hero.isNotEmpty()) {
            ScanBanner(
                scan = scan,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = TabletTopBarSpace),
            )
        }
    }
}

@Composable
private fun HomeSection(title: String, onSeeAll: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val dims = LocalNovaDimens.current
    Column(Modifier.padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(start = dims.gutter, end = dims.gutter - 8.dp),
            actionLabel = if (onSeeAll != null) "See all" else null,
            onAction = onSeeAll,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** What the hero's "+" saved: 16:9 backdrop cards that open the title. */
@Composable
private fun WatchlistRow(items: List<LibraryItemUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(items, key = { it.key }) { item ->
            val open: () -> Unit = {
                when (item) {
                    is LibraryItemUi.Video -> actions.openVideo(item.card.id)
                    is LibraryItemUi.Show -> actions.openShow(item.card.key)
                }
            }
            TabletStillCard(
                artwork = when (item) {
                    is LibraryItemUi.Video -> item.card.artwork
                    is LibraryItemUi.Show -> item.card.artwork
                },
                title = item.title,
                onClick = open,
                onLongClick = open,
                onClickLabel = "Details",
                modifier = Modifier.width(dims.landscapeCardWidth),
            )
        }
    }
}

@Composable
private fun ContinueWatchingRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(cards, key = { it.id }) { card ->
            VideoMenuBox(videoId = card.id, title = card.primaryLine(), actions = actions) { openMenu ->
                TabletLandscapeCard(
                    card = card,
                    onClick = { actions.play(card.id) },
                    onLongClick = openMenu,
                    modifier = Modifier.width(dims.landscapeCardWidth),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Empty library
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeOnboarding(scan: ScanUi, permission: PermissionUi, layout: DeviceLayout, actions: AppActions) {
    val dims = LocalNovaDimens.current
    // Scrollable so pull-to-refresh works, centred while it fits.
    CenteredScrollColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dims.gutter,
            end = dims.gutter,
            top = statusBarTop() + 24.dp,
            bottom = navigationBarBottom() + 24.dp,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            if (layout.isExpanded) {
                Row(Modifier.padding(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PosterCollage() }
                    Spacer(Modifier.width(48.dp))
                    OnboardingText(scan, permission, actions, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PosterCollage()
                    Spacer(Modifier.height(36.dp))
                    OnboardingText(scan, permission, actions, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OnboardingText(scan: ScanUi, permission: PermissionUi, actions: AppActions, modifier: Modifier = Modifier) {
    val title: String
    val body: String
    when {
        !permission.granted -> {
            title = "Your videos, beautifully organised"
            body = "Nova finds the movies, shows and clips on this tablet, in folders you pick and on " +
                "network shares, then dresses them up with posters and details."
        }
        scan.running -> {
            title = "Looking for videos…"
            body = scan.label ?: "This can take a moment the first time."
        }
        permission.partial -> {
            title = "No videos yet"
            body = "Nova can only see the videos you picked. Allow access to all of them, add a folder, " +
                "connect to a network share or open a stream."
        }
        else -> {
            title = "No videos yet"
            body = "Nothing turned up on this tablet. Add a folder, connect to a network share or open a stream."
        }
    }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (scan.running) {
            Spacer(Modifier.height(20.dp))
            ScanBanner(scan.copy(label = null), Modifier.widthIn(max = 360.dp))
        }
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permission.granted || permission.partial) {
                Button(onClick = actions::requestPermission, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    ButtonLabel(
                        R.drawable.ic_video_library,
                        if (permission.partial) "Allow access to all videos" else "Allow access to videos",
                    )
                }
            }
            FilledTonalButton(onClick = actions::addFolder, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                ButtonLabel(R.drawable.ic_create_new_folder, "Add a Folder")
            }
            FilledTonalButton(
                onClick = actions::addNetworkSource,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_lan, "Add a network source")
            }
            FilledTonalButton(
                onClick = { actions.showDialog(DialogUi.OpenUrl) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                ButtonLabel(R.drawable.ic_link, "Open a network stream")
            }
        }
    }
}
