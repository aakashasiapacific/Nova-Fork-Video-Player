package com.aakash.novafork.ui.phone

import com.aakash.novafork.ui.common.HomeSkeleton
import com.aakash.novafork.ui.common.LocalLibraryLoading
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.HeroTitle
import com.aakash.novafork.ui.common.HideStatusBar
import com.aakash.novafork.ui.common.HomeLibraryLinks
import com.aakash.novafork.ui.common.IosFilledTonalButton
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalHeroPreviewEnabled
import com.aakash.novafork.ui.common.LocalSheetDepth
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.OnTabReselect
import com.aakash.novafork.ui.common.PictureStatusBar
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.HomeSectionGap
import com.aakash.novafork.ui.common.LocalHeroStyle
import com.aakash.novafork.ui.common.HomeSectionHeader
import com.aakash.novafork.ui.common.SoftEdgeDepth
import com.aakash.novafork.ui.common.ShowMenuBox
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.deviceAccessNotice
import com.aakash.novafork.ui.common.fadeEndEdge
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.common.rememberRowSnap
import com.aakash.novafork.ui.common.softTopEdge
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.delay

/** Scrolled less than this, the hero counts as on screen. */
private const val HERO_VISIBLE_SCROLL_PX = 600

/** Home rests (the hero fills the screen) after this long untouched (owner: 6 seconds). */
private const val REST_AFTER_MS = 6_000L

/**
 * Home at rest: after [REST_AFTER_MS] untouched with the hero on screen, the hero fills the
 * screen and the bars fade (a screensaver); any touch brings everything back. The shell owns it
 * so the tab bar can fade, and [blocked] (a dialog, Now Playing, another page) holds it off.
 */
@Stable
internal class HomeRest {
    var resting by mutableStateOf(false)
    var blocked by mutableStateOf(false)
}

/** The shell's [HomeRest]; null where nothing rests (tablet, previews, tests). */
internal val LocalHomeRest = staticCompositionLocalOf<HomeRest?> { null }

@Composable
fun PhoneHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (home.isEmpty && (LocalLibraryLoading.current || scan.running)) {
            // Before the first scan finishes: soft placeholders, never a blank page or the onboarding.
            HomeSkeleton()
        } else if (home.isEmpty) {
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
    OnTabReselect { listState.animateScrollToItem(0) }
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
    // The hero's picture runs under the status bar, in portrait as in landscape: its icons
    // follow the picture (owner: no black shade under the clock).
    PictureStatusBar(
        model = home.hero.getOrNull(pagerState.settledPage)?.artwork?.let { it.backdropUrl ?: it.stillUrl ?: it.posterUrl },
        active = hasHero && heroOnScreen,
    )
    // Rest when idle: only in the real app (previews and tests never rest), at the very top of
    // Home, resumed, with no keyboard, sheet or clip playing with sound.
    val rest = LocalHomeRest.current
    val sheets = LocalSheetDepth.current
    val atTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val clipWithSound = !muted && home.hero.getOrNull(pagerState.currentPage)?.previewUri != null
    val keyboard = WindowInsets.isImeVisible
    val canRest = rest != null && previewEnabled && hasHero && atTop && resumed && !rest.blocked &&
        !clipWithSound && !keyboard && (sheets?.open ?: 0) == 0
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(canRest, touches) {
        if (!canRest) {
            rest?.resting = false
            return@LaunchedEffect
        }
        delay(REST_AFTER_MS)
        rest?.resting = true
    }
    DisposableEffect(rest) { onDispose { rest?.resting = false } }
    val resting = rest?.resting == true
    HideStatusBar(hidden = resting)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Any touch anywhere on Home wakes it and starts the wait again.
            .pointerInput(rest) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        touches++
                        rest?.resting = false
                    }
                }
            },
    ) {
        // Portrait: the 16:9 backdrop with the poster, title and Play under it; landscape: wide.
        val portrait = maxWidth <= maxHeight
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val heroHeight = if (portrait) heroHeightFor(LocalHeroStyle.current, statusBar, maxWidth, dims.detailPosterWidth) else maxHeight * 0.85f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
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
                    // Right under the hero's dots (owner: no empty band before it).
                    HomeSection(title = "Continue Watching", topPadding = 0.dp, onOpen = { actions.navigate(Route.ContinueWatching) }) {
                        LandscapeRow(home.continueWatching, actions)
                    }
                }
            }
            if (home.upNext.isNotEmpty()) {
                item(key = "upNext", contentType = "landscapeRow") {
                    HomeSection(title = "Up Next", topPadding = if (home.continueWatching.isEmpty()) 0.dp else null, onOpen = { actions.navigate(Route.UpNext) }) {
                        UpNextRow(home.upNext, actions)
                    }
                }
            }
            if (home.watchlist.isNotEmpty()) {
                item(key = "watchlist", contentType = "landscapeRow") {
                    HomeSection(
                        title = "Watchlist",
                        topPadding = if (home.continueWatching.isEmpty() && home.upNext.isEmpty()) 0.dp else null,
                        onOpen = { actions.openLibrary(LibraryFilter.WATCHLIST) },
                    ) {
                        WatchlistRow(home.watchlist, actions)
                    }
                }
            }
            item(key = "library", contentType = "library") {
                HomeLibraryLinks(actions, counts = home.libraryCounts)
            }
        }
        // Once the hero has gone up past the clock, the rows passing under it melt away softly
        // (owner: no dark band); never over the hero's own picture.
        val edgePx = with(LocalDensity.current) { (heroHeight - statusBar - SoftEdgeDepth).toPx() }
        val underClock by remember(listState, edgePx, hasHero) {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > (if (hasHero) edgePx else 0f)
            }
        }
        val shade by animateFloatAsState(if (underClock) 1f else 0f, label = "statusShade")
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBar + SoftEdgeDepth)
                .graphicsLayer { alpha = shade }
                .background(softTopEdge()),
        )
        HomeRestView(hero = home.hero.getOrNull(pagerState.currentPage), resting = resting)
    }
}

/**
 * Home at rest: the hero's picture over the whole screen, drifting slowly, with its title small
 * at the foot; it grows out of the hero and crossfades as the hero moves on. A touch on it only
 * wakes Home (it does not open the title underneath).
 */
@Composable
private fun HomeRestView(hero: HeroUi?, resting: Boolean) {
    val dims = LocalNovaDimens.current
    AnimatedVisibility(
        visible = resting && hero != null,
        enter = fadeIn(tween(900)) + scaleIn(tween(900), initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0f)),
        exit = fadeOut(tween(250)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Crossfade(targetState = hero, animationSpec = tween(1_200), label = "restHero") { shown ->
                if (shown != null) {
                    Box(Modifier.fillMaxSize()) {
                        BackdropArt(artwork = shown.artwork, modifier = Modifier.fillMaxSize().slowZoom())
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = dims.gutter, end = dims.gutter, bottom = 56.dp)
                                .graphicsLayer { alpha = 0.85f },
                        ) {
                            HeroTitle(
                                title = shown.title,
                                logoUrl = shown.artwork.logoUrl,
                                textStyle = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Start,
                                logoAlignment = Alignment.BottomStart,
                                maxWidthFraction = 0.6f,
                                logoHeight = 40.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeSection(
    title: String,
    /** Space above the title; null = the usual gap between sections. */
    topPadding: Dp? = null,
    /** The whole list behind the row: the title then reads "Watchlist ›" and opens it (Apple TV). */
    onOpen: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = topPadding ?: HomeSectionGap)) {
        HomeSectionHeader(title = title, onOpen = onOpen)
        content()
    }
}

/** What the hero's "+" saved: backdrop cards that open the title. */
@Composable
private fun WatchlistRow(items: List<LibraryItemUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        flingBehavior = rememberRowSnap(rowState),
        // The card peeking in at the end fades into the page (iOS 27).
        modifier = Modifier.fadeEndEdge(rowState),
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            // The pictures drift against the scroll inside their frames (Apple TV).
            CompositionLocalProvider(LocalCardParallax provides remember(rowState, index) { rowState.parallaxAt(index) }) {
                when (item) {
                    is LibraryItemUi.Video -> PhoneWideVideoCard(card = item.card, onClick = { actions.openVideo(item.card.id) })
                    is LibraryItemUi.Show -> ShowMenuBox(showKey = item.card.key, actions = actions) { openMenu ->
                        PhoneWideShowCard(show = item.card, onClick = { actions.openShow(item.card.key) }, onLongClick = openMenu)
                    }
                }
            }
        }
    }
}

/** A Continue Watching card's caption. */
internal fun continueCaption(card: VideoCardUi): String? {
    val left = remainingLabel(card) ?: return card.displaySubtitle()
    return if (card.kind == MediaKind.EPISODE) metaLine(card.displaySubtitle(), left).ifEmpty { null } else left
}

/** The next episode of each show being watched: plays on a tap, captioned "S2 · E4 · Title". */
@Composable
private fun UpNextRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        flingBehavior = rememberRowSnap(rowState),
        // The card peeking in at the end fades into the page (iOS 27).
        modifier = Modifier.fadeEndEdge(rowState),
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
            CompositionLocalProvider(LocalCardParallax provides remember(rowState, index) { rowState.parallaxAt(index) }) {
                VideoMenuBox(videoId = card.id, title = card.displayTitle(), actions = actions) { openMenu ->
                    PhoneLandscapeCard(card = card, onClick = { actions.play(card.id) }, onLongClick = openMenu)
                }
            }
        }
    }
}

@Composable
private fun LandscapeRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        flingBehavior = rememberRowSnap(rowState),
        // The card peeking in at the end fades into the page (iOS 27).
        modifier = Modifier.fadeEndEdge(rowState),
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        itemsIndexed(cards) { index, card ->
            CompositionLocalProvider(LocalCardParallax provides remember(rowState, index) { rowState.parallaxAt(index) }) {
                ContinueWatchingCard(card, actions)
            }
        }
    }
}

/** A video left partway: plays on from there; its menu can take it off Continue Watching. */
@Composable
internal fun ContinueWatchingCard(
    card: VideoCardUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
    width: Dp = LocalNovaDimens.current.landscapeCardWidth,
) {
    VideoMenuBox(
        videoId = card.id,
        title = card.displayTitle(),
        actions = actions,
        modifier = modifier,
        // Apple TV's "Remove from Up Next": it forgets where it was left, so it leaves the row.
        extraItems = { close ->
            NovaMenuItem(
                text = { Text("Remove from Continue Watching") },
                destructive = true,
                leadingIcon = { NovaIcon(R.drawable.ic_close, contentDescription = null) },
                onClick = {
                    close()
                    actions.setWatched(card.id, false)
                },
            )
        },
    ) { openMenu ->
        PhoneLandscapeCard(
            card = card,
            onClick = { actions.play(card.id) },
            onLongClick = openMenu,
            // Apple TV's caption: what is left ("42m left"); an episode keeps its "S1 · E3".
            subtitle = continueCaption(card),
            width = width,
            rollingSubtitle = true,
        )
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
                    actionLabel = if (permission.granted) "Scan Again" else "Allow Access to Videos",
                    onAction = { if (permission.granted) actions.refresh() else actions.requestPermission() },
                    // The prompt may no longer show (denied twice): the app's settings page always works.
                    secondaryLabel = if (permission.granted) null else "App Settings",
                    onSecondary = actions::openAppSettings,
                )
                Column(
                    Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IosFilledTonalButton(onClick = actions::addFolder, modifier = Modifier.fillMaxWidth()) {
                        ButtonContent(R.drawable.ic_create_new_folder, "Add a Folder")
                    }
                    IosFilledTonalButton(
                        onClick = actions::addNetworkSource,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonContent(R.drawable.ic_lan, "Add a Network Source")
                    }
                    IosFilledTonalButton(
                        onClick = { actions.showDialog(DialogUi.OpenUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonContent(R.drawable.ic_link, "Open a Network Stream")
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
