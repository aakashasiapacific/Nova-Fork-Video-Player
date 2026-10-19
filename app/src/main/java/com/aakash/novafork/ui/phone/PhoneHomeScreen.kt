package com.aakash.novafork.ui.phone

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.DeviceAccessCard
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.HomeLibraryLinks
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalHeroPreviewEnabled
import com.aakash.novafork.ui.common.LocalStatusBarIcons
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.deviceAccessNotice
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.rememberTopBrightness
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.delay

/** Scrolled less than this, the hero counts as on screen. */
private const val HERO_VISIBLE_SCROLL_PX = 600

/** A top of the hero brighter than this gets dark status bar icons. */
private const val BRIGHT_TOP = 0.62f

@Composable
fun PhoneHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    PhonePullToRefresh(
        scanning = scan.running,
        onRefresh = actions::refresh,
        modifier = modifier.fillMaxSize(),
        indicatorModifier = Modifier.statusBarsPadding(),
    ) {
        if (home.isEmpty) {
            HomeOnboarding(scan = scan, permission = permission, actions = actions)
        } else {
            HomeFeed(home = home, scan = scan, permission = permission, actions = actions)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Feed
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeFeed(home: HomeUi, scan: ScanUi, permission: PermissionUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val listState = rememberLazyListState()
    val hasHero = home.hero.isNotEmpty()
    val pagerState = rememberPagerState { home.hero.size }
    // Short clips play with sound; the hero's mute button silences them.
    var muted by rememberSaveable { mutableStateOf(false) }
    val previewEnabled = LocalHeroPreviewEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    // The hero is (mostly) on screen: previews play and the status bar follows its picture.
    val heroOnScreen by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < HERO_VISIBLE_SCROLL_PX }
    }
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val scrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    HeroStatusBar(
        hero = home.hero.getOrNull(pagerState.settledPage),
        // Portrait heroes start below the status bar (clear of the punch hole): only the wide
        // landscape hero has a picture behind it.
        active = hasHero && heroOnScreen && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Portrait: the 16:9 backdrop with the poster, title and Play under it; landscape: wide.
        val portrait = maxWidth <= maxHeight
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val heroHeight = if (portrait) statusBar + maxWidth * 9f / 16f + heroBelowBackdrop(dims.detailPosterWidth) else maxHeight * 0.85f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dims.sectionSpacing + LocalBottomBarSpace.current),
        ) {
            if (hasHero) {
                item(key = "hero", contentType = "hero") {
                    PhoneHeroPager(
                        heroes = home.hero,
                        height = heroHeight,
                        pagerState = pagerState,
                        previewEnabled = previewEnabled,
                        previewAllowed = heroOnScreen && resumed,
                        muted = muted,
                        onToggleMute = { muted = !muted },
                        actions = actions,
                        // Landscape: the backdrop fills the hero, the title sits on the left.
                        wide = !portrait,
                    )
                }
            } else {
                item(key = "top", contentType = "top") {
                    Spacer(Modifier.statusBarsPadding().height(16.dp))
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
                        modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 16.dp),
                    )
                }
            }
            if (home.continueWatching.isNotEmpty()) {
                item(key = "continue", contentType = "landscapeRow") {
                    HomeSection(title = "Continue Watching") {
                        LandscapeRow(home.continueWatching, actions)
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
                HomeLibraryLinks(actions)
            }
        }
        // Once the page scrolls, a dark fade sits behind the status bar so the rows passing
        // under the clock and icons don't clash with them.
        val shade by animateFloatAsState(if (scrolled) 1f else 0f, label = "statusShade")
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBar + 16.dp)
                .graphicsLayer { alpha = shade }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.6f to Color.Black.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        ScanBanner(
            scan = scan,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}

/**
 * Dark status bar icons over a bright top of the hero picture, light ones everywhere else (the
 * page is black). Light again when the hero scrolls away or Home is left.
 */
@Composable
private fun HeroStatusBar(hero: HeroUi?, active: Boolean) {
    val setDarkIcons = LocalStatusBarIcons.current
    val brightness = rememberTopBrightness(
        hero?.artwork?.let { it.backdropUrl ?: it.stillUrl ?: it.posterUrl }.takeIf { active },
    )
    val dark = active && (brightness ?: 0f) > BRIGHT_TOP
    LaunchedEffect(dark) { setDarkIcons(dark) }
    DisposableEffect(Unit) { onDispose { setDarkIcons(false) } }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeSection(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dims = LocalNovaDimens.current
    Column(Modifier.fillMaxWidth().padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = title,
            modifier = Modifier
                .padding(start = dims.gutter, end = 4.dp)
                .padding(bottom = if (onSeeAll == null) 10.dp else 0.dp),
            actionLabel = if (onSeeAll != null) "See all" else null,
            onAction = onSeeAll,
        )
        content()
    }
}

/** What the hero's "+" saved: backdrop cards that open the title. */
@Composable
private fun WatchlistRow(items: List<LibraryItemUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is LibraryItemUi.Video -> PhoneWideVideoCard(card = item.card, onClick = { actions.openVideo(item.card.id) })
                is LibraryItemUi.Show -> PhoneWideShowCard(show = item.card, onClick = { actions.openShow(item.card.key) })
            }
        }
    }
}

@Composable
private fun LandscapeRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(cards) { card ->
            VideoMenuBox(videoId = card.id, title = card.displayTitle(), actions = actions) { openMenu ->
                PhoneLandscapeCard(
                    card = card,
                    onClick = { actions.play(card.id) },
                    onLongClick = openMenu,
                    subtitle = metaLine(card.displaySubtitle(), remainingLabel(card)).ifEmpty { null },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Onboarding
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeOnboarding(scan: ScanUi, permission: PermissionUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    Box(Modifier.fillMaxSize()) {
        CenteredScrollable {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = dims.gutter, vertical = 48.dp)
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val deviceNotice = deviceAccessNotice(permission)
                if (deviceNotice != null && permission.granted) {
                    DeviceAccessCard(
                        notice = deviceNotice,
                        onAllow = actions::requestPermission,
                        onOpenSettings = actions::openAppSettings,
                    )
                }
                EmptyState(
                    icon = R.drawable.ic_video_library,
                    title = "Your videos, with posters",
                    body = "Nova Fork finds the videos on your phone, in folders and on network shares, " +
                        "and dresses them up with posters, backdrops and details.",
                    actionLabel = if (permission.granted) "Scan again" else "Allow access to videos",
                    onAction = { if (permission.granted) actions.refresh() else actions.requestPermission() },
                    // The prompt may no longer show (denied twice): the app's settings page always works.
                    secondaryLabel = if (permission.granted) null else "App settings",
                    onSecondary = actions::openAppSettings,
                )
                Column(
                    Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = actions::addFolder, modifier = Modifier.fillMaxWidth()) {
                        ButtonContent(R.drawable.ic_create_new_folder, "Add a Folder")
                    }
                    FilledTonalButton(
                        onClick = actions::addNetworkSource,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonContent(R.drawable.ic_lan, "Add a network source")
                    }
                    FilledTonalButton(
                        onClick = { actions.showDialog(DialogUi.OpenUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonContent(R.drawable.ic_link, "Open a network stream")
                    }
                }
            }
        }
        ScanBanner(
            scan = scan,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}
