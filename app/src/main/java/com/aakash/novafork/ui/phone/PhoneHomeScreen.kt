package com.aakash.novafork.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.heroHeightFor
import com.aakash.novafork.ui.common.AccountButton
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.GlassBackdrop
import com.aakash.novafork.ui.common.GlassCircleButton
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.GlassNavBar
import com.aakash.novafork.ui.common.GlassPage
import com.aakash.novafork.ui.common.GlassStyle
import com.aakash.novafork.ui.common.InsetGroup
import com.aakash.novafork.ui.common.LargeTitleHeader
import com.aakash.novafork.ui.common.ListRow
import com.aakash.novafork.ui.common.ListRowIcon
import com.aakash.novafork.ui.common.LocalGlassState
import com.aakash.novafork.ui.common.NavBarDefaults
import com.aakash.novafork.ui.common.NovaGlyphs
import com.aakash.novafork.ui.common.PageDots
import com.aakash.novafork.ui.common.PrimaryCapsuleButton
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.common.novaClickable
import com.aakash.novafork.ui.common.rememberGlassState
import com.aakash.novafork.ui.common.rememberTitleCollapsed
import com.aakash.novafork.ui.common.topScrim
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.delay

private const val HERO_AUTO_ADVANCE_MS = 8_000L

/** Rows of Home that open a full page of their own ("Continue Watching ›"). */
private enum class HomeRowPage(val title: String) {
    CONTINUE("Continue Watching"),
    RECENT("Recently Added"),
}

/**
 * Home: a full-bleed hero carousel from the very top of the screen, then rows of 16:9 cards.
 * No title bar; the account circle top-right opens Settings.
 */
@Composable
internal fun PhoneHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    resumeEnabled: Boolean,
    actions: AppActions,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted so the feed keeps its place while a row's full page is open.
    val feedList = rememberLazyListState()
    val heroPager = rememberPagerState { home.hero.size }
    var expanded by rememberSaveable { mutableStateOf<HomeRowPage?>(null) }
    if (expanded != null && LocalPhonePageActive.current) BackHandler { expanded = null }

    AnimatedContent(
        targetState = expanded,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { if (targetState != null) phonePushTransition() else phonePopTransition() },
        label = "homeRowPage",
    ) { page ->
        when {
            page != null -> HomeFullRow(
                page = page,
                home = home,
                actions = actions,
                onBack = { expanded = null },
            )
            home.isEmpty -> HomeOnboarding(scan = scan, permission = permission, actions = actions, onAccount = onAccount)
            else -> HomeFeed(
                home = home,
                scan = scan,
                permission = permission,
                resumeEnabled = resumeEnabled,
                actions = actions,
                onAccount = onAccount,
                onExpand = { expanded = it },
                listState = feedList,
                pagerState = heroPager,
            )
        }
    }
}

/** Movies ›, TV Shows ›, Home Videos › open that category of the Library tab. */
private fun AppActions.openLibraryCategory(filter: LibraryFilter) {
    selectTab(TopDestination.LIBRARY)
    setLibraryFilter(filter)
}

// ───────────────────────────── Feed ─────────────────────────────

@Composable
private fun HomeFeed(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    resumeEnabled: Boolean,
    actions: AppActions,
    onAccount: () -> Unit,
    onExpand: (HomeRowPage) -> Unit,
    listState: LazyListState,
    pagerState: PagerState,
) {
    val dims = LocalNovaDimens.current
    val density = LocalDensity.current
    val hasHero = home.hero.isNotEmpty()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val homeVideos = remember(home.videos) { home.videos.filter { it.kind != MediaKind.STREAM } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Width × 1.25; a phone in landscape would make that taller than the screen.
        val heroHeight = heroHeightFor(DeviceLayout.PHONE, maxWidth, maxHeight).coerceAtMost(maxHeight)
        val pastHeroPx = with(density) { (heroHeight - statusTop - NavBarDefaults.Height).toPx() }.coerceAtLeast(0f)
        val pastHero by remember(listState, hasHero, pastHeroPx) {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > (if (hasHero) pastHeroPx else 0f)
            }
        }
        GlassPage(
            modifier = Modifier
                .fillMaxSize()
                .background(NovaColors.background),
            chrome = {
                GlassNavBar(
                    showBackground = pastHero,
                    trailing = {
                        AccountButton(
                            onClick = onAccount,
                            style = if (hasHero && !pastHero) GlassStyle.Clear else GlassStyle.Regular,
                        )
                    },
                )
            },
        ) {
            PhoneRefreshBox(
                scanning = scan.running,
                onRefresh = actions::refresh,
                indicatorTop = if (hasHero) statusTop + 8.dp else phoneNavBarBottom(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NovaColors.background),
                    contentPadding = PaddingValues(bottom = glassTabBarClearance()),
                ) {
                    if (hasHero) {
                        item(key = "hero", contentType = "hero") {
                            PhoneHero(
                                heroes = home.hero,
                                height = heroHeight,
                                pagerState = pagerState,
                                resumeEnabled = resumeEnabled,
                                actions = actions,
                            )
                        }
                    } else {
                        item(key = "top", contentType = "top") {
                            Spacer(Modifier.height(phoneNavBarBottom()))
                        }
                    }
                    item(key = "scan", contentType = "scan") {
                        ScanBanner(scan, Modifier.padding(top = 12.dp))
                    }
                    if (permission.partial) {
                        item(key = "partial", contentType = "partial") {
                            PartialAccessGroup(
                                onAllowAll = actions::requestPermission,
                                modifier = Modifier.padding(top = 20.dp),
                            )
                        }
                    }
                    if (home.continueWatching.isNotEmpty()) {
                        item(key = "continue", contentType = "row") {
                            PhoneCardRow(
                                title = "Continue Watching",
                                items = home.continueWatching,
                                key = { it.id },
                                onHeaderClick = { onExpand(HomeRowPage.CONTINUE) },
                                modifier = Modifier.padding(top = dims.sectionSpacing),
                            ) { card ->
                                ContinueCard(card, actions, width = dims.continueCardWidth)
                            }
                        }
                    }
                    if (home.recentlyAdded.isNotEmpty()) {
                        item(key = "recent", contentType = "row") {
                            PhoneCardRow(
                                title = "Recently Added",
                                items = home.recentlyAdded,
                                key = { it.id },
                                onHeaderClick = { onExpand(HomeRowPage.RECENT) },
                                modifier = Modifier.padding(top = dims.sectionSpacing),
                            ) { card ->
                                VideoCard(card, actions)
                            }
                        }
                    }
                    if (home.movies.isNotEmpty()) {
                        item(key = "movies", contentType = "row") {
                            PhoneCardRow(
                                title = "Movies",
                                items = home.movies,
                                key = { it.id },
                                onHeaderClick = { actions.openLibraryCategory(LibraryFilter.MOVIES) },
                                modifier = Modifier.padding(top = dims.sectionSpacing),
                            ) { card ->
                                VideoCard(card, actions)
                            }
                        }
                    }
                    if (home.shows.isNotEmpty()) {
                        item(key = "shows", contentType = "row") {
                            PhoneCardRow(
                                title = "TV Shows",
                                items = home.shows,
                                key = { it.key },
                                onHeaderClick = { actions.openLibraryCategory(LibraryFilter.SHOWS) },
                                modifier = Modifier.padding(top = dims.sectionSpacing),
                            ) { show ->
                                PhoneWideCard(
                                    title = show.title,
                                    caption = show.cardCaption(),
                                    artwork = show.artwork,
                                    onClick = { actions.openShow(show.key) },
                                    menuItems = listOf(
                                        GlassMenuItem("Go to Details", icon = R.drawable.ic_info) { actions.openShow(show.key) },
                                    ),
                                )
                            }
                        }
                    }
                    if (homeVideos.isNotEmpty()) {
                        item(key = "videos", contentType = "row") {
                            PhoneCardRow(
                                title = "Home Videos",
                                items = homeVideos,
                                key = { it.id },
                                onHeaderClick = { actions.openLibraryCategory(LibraryFilter.VIDEOS) },
                                modifier = Modifier.padding(top = dims.sectionSpacing),
                            ) { card ->
                                VideoCard(card, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A Continue Watching card: progress inside the art, "S2, E3 · 20m left"; tap resumes. */
@Composable
private fun ContinueCard(card: VideoCardUi, actions: AppActions, width: Dp?, modifier: Modifier = Modifier) {
    PhoneWideCard(
        title = card.cardTitle(),
        caption = card.continueCaption(),
        artwork = card.artwork,
        onClick = { actions.play(card.id) },
        modifier = modifier,
        width = width,
        progress = card.progress,
        preferStill = card.kind == MediaKind.EPISODE,
        menuItems = videoMenuItems(card, actions),
    )
}

/** A 16:9 card for a movie, episode or home video; tap opens its details (streams play). */
@Composable
private fun VideoCard(card: VideoCardUi, actions: AppActions, modifier: Modifier = Modifier, width: Dp? = LocalNovaDimens.current.landscapeCardWidth) {
    PhoneWideCard(
        title = card.cardTitle(),
        caption = card.cardCaption(),
        artwork = card.artwork,
        onClick = { if (card.kind == MediaKind.STREAM) actions.play(card.id) else actions.openVideo(card.id) },
        modifier = modifier,
        width = width,
        preferStill = card.kind == MediaKind.EPISODE,
        menuItems = videoMenuItems(card, actions),
    )
}

// ───────────────────────────── Hero ─────────────────────────────

@Composable
private fun PhoneHero(
    heroes: List<HeroUi>,
    height: Dp,
    pagerState: PagerState,
    resumeEnabled: Boolean,
    actions: AppActions,
) {
    val dims = LocalNovaDimens.current
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    // Restarts on every settle, so a manual swipe also resets the timer.
    LaunchedEffect(pagerState, dragged, pagerState.settledPage, heroes.size) {
        if (dragged || heroes.size < 2) return@LaunchedEffect
        delay(HERO_AUTO_ADVANCE_MS)
        pagerState.animateScrollToPage((pagerState.currentPage + 1) % heroes.size)
    }
    val dotsSpace = if (heroes.size > 1) dims.pageDotSize + 12.dp else 0.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> heroes.getOrNull(page)?.target?.let(::heroKey) ?: "page:$page" },
        ) { page ->
            val hero = heroes.getOrNull(page)
            if (hero != null) {
                HeroPage(
                    hero = hero,
                    bottomInset = dims.heroMargin + dotsSpace,
                    resumeEnabled = resumeEnabled,
                    actions = actions,
                )
            }
        }
        PageDots(
            count = heroes.size,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dims.heroMargin),
        )
    }
}

private fun heroKey(target: HeroTarget): String = when (target) {
    is HeroTarget.Video -> "v:${target.id}"
    is HeroTarget.Show -> "s:${target.key}"
}

@Composable
private fun HeroPage(hero: HeroUi, bottomInset: Dp, resumeEnabled: Boolean, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val heroGlass = rememberGlassState()
    val open: () -> Unit = {
        when (val target = hero.target) {
            is HeroTarget.Video -> actions.openVideo(target.id)
            is HeroTarget.Show -> actions.openShow(target.key)
        }
    }
    val play: () -> Unit = {
        val videoId = hero.playVideoId
        if (videoId != null) actions.play(videoId) else open()
    }
    val genres = hero.genres.take(2).joinToString(" · ").ifEmpty { hero.metaLine.orEmpty() }
    Box(
        Modifier
            .fillMaxSize()
            .novaClickable(onClickLabel = "Details", pressScale = 1f, pressAlpha = 1f, onClick = open),
    ) {
        // The artwork is a backdrop of its own, so the glass circle over it blurs the picture.
        GlassBackdrop(heroGlass, Modifier.matchParentSize()) {
            BackdropArt(
                artwork = hero.artwork,
                modifier = Modifier
                    .matchParentSize()
                    .bottomScrim(Color.Black, startFraction = 0.55f)
                    .topScrim(),
            )
        }
        CompositionLocalProvider(LocalGlassState provides heroGlass) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = dims.heroMargin, end = dims.heroMargin, bottom = bottomInset)
                    .widthIn(max = dims.heroTextMaxWidth),
            ) {
                Text(
                    text = hero.title,
                    style = NovaType.title1,
                    color = NovaColors.OnMedia,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (genres.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = genres,
                        style = NovaType.subhead,
                        color = NovaColors.OnMediaSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryCapsuleButton(text = heroPlayLabel(hero.playLabel, resumeEnabled), onClick = play)
                    Spacer(Modifier.width(12.dp))
                    GlassCircleButton(
                        onClick = open,
                        icon = NovaGlyphs.Info,
                        contentDescription = "Details",
                        style = GlassStyle.Clear,
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Full row page ─────────────────────────────

/** "Continue Watching" / "Recently Added" as a page: a large title over a 2-column 16:9 grid. */
@Composable
private fun HomeFullRow(page: HomeRowPage, home: HomeUi, actions: AppActions, onBack: () -> Unit) {
    val list = rememberLazyListState()
    val collapsed = rememberTitleCollapsed(list)
    val cards = when (page) {
        HomeRowPage.CONTINUE -> home.continueWatching
        HomeRowPage.RECENT -> home.recentlyAdded
    }
    val rows = remember(cards) { cards.chunked(2) }
    PhoneLargeTitlePage(
        title = page.title,
        collapsed = collapsed,
        leading = { BackButton(onClick = onBack) },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = glassTabBarClearance()),
        ) {
            item(key = "title") { LargeTitleHeader(page.title, collapsed = collapsed) }
            items(rows, key = { pair -> pair.first().id }) { pair ->
                PhoneTwoColumnRow(pair, Modifier.padding(top = 8.dp, bottom = 12.dp)) { card, cell ->
                    when (page) {
                        HomeRowPage.CONTINUE -> ContinueCard(card, actions, width = null, modifier = cell)
                        HomeRowPage.RECENT -> VideoCard(card, actions, modifier = cell, width = null)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Onboarding ─────────────────────────────

/** An empty library: what Nova Fork does, and the ways to add videos. */
@Composable
private fun HomeOnboarding(scan: ScanUi, permission: PermissionUi, actions: AppActions, onAccount: () -> Unit) {
    GlassPage(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaColors.background),
        chrome = {
            GlassNavBar(showBackground = false, trailing = { AccountButton(onClick = onAccount) })
        },
    ) {
        PhoneRefreshBox(scanning = scan.running, onRefresh = actions::refresh) {
            PhoneCenteredScroll(Modifier.background(NovaColors.background)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = phoneNavBarBottom(), bottom = glassTabBarClearance()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ScanBanner(scan)
                    if (permission.partial) {
                        PartialAccessGroup(onAllowAll = actions::requestPermission, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    EmptyState(
                        icon = R.drawable.ic_video_library,
                        title = "Your Videos, with Posters",
                        body = "Nova Fork finds the videos on your phone, in folders and on network shares, " +
                            "and dresses them up with posters, backdrops and details.",
                        actionLabel = if (permission.granted) "Scan Again" else "Allow Access to Videos",
                        onAction = { if (permission.granted) actions.refresh() else actions.requestPermission() },
                    )
                    Spacer(Modifier.height(8.dp))
                    InsetGroup {
                        ListRow(
                            title = "Add Folder",
                            leading = { ListRowIcon(R.drawable.ic_folder, NovaColors.tint) },
                            chevron = true,
                            onClick = actions::addFolder,
                        )
                        ListRow(
                            title = "Add Network Share",
                            leading = { ListRowIcon(R.drawable.ic_lan, NovaColors.green) },
                            chevron = true,
                            onClick = { actions.showDialog(DialogUi.SmbServer()) },
                        )
                        ListRow(
                            title = "Open Network Stream",
                            leading = { ListRowIcon(R.drawable.ic_live_tv, NovaColors.orange) },
                            chevron = true,
                            showDivider = false,
                            onClick = { actions.showDialog(DialogUi.OpenUrl) },
                        )
                    }
                }
            }
        }
    }
}

/** Android 14 "selected photos and videos" access: offer to widen it. */
@Composable
private fun PartialAccessGroup(onAllowAll: () -> Unit, modifier: Modifier = Modifier) {
    InsetGroup(
        modifier = modifier,
        footer = "Nova Fork can only see the videos you picked. Allow access to all videos to see your whole library.",
    ) {
        ListRow(
            title = "Allow Access to All Videos",
            leading = { ListRowIcon(R.drawable.ic_visibility, NovaColors.tint) },
            titleColor = NovaColors.tint,
            showDivider = false,
            onClick = onAllowAll,
        )
    }
}
