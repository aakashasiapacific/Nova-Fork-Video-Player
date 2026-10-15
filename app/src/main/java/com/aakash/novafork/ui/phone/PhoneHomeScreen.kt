package com.aakash.novafork.ui.phone

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.RatingPill
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.SectionHeader
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.metaLine
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.TopDestination
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import kotlinx.coroutines.delay

private val HomeTopBarHeight = 56.dp

/** Space below the hero art taken by the title block (it overlaps the art's faded bottom). */
private val HeroTextOverhang = 72.dp

private const val HERO_AUTO_ADVANCE_MS = 7_000L

@Composable
fun PhoneHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val indicatorTop = if (home.isEmpty) 0.dp else HomeTopBarHeight
    PhonePullToRefresh(
        scanning = scan.running,
        onRefresh = actions::refresh,
        modifier = modifier.fillMaxSize(),
        indicatorModifier = Modifier
            .statusBarsPadding()
            .padding(top = indicatorTop),
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keeps the hero from swallowing the whole screen on a phone in landscape.
        val maxHeroArtHeight = if (constraints.hasBoundedHeight) maxHeight * 0.7f else Dp.Infinity
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = dims.sectionSpacing),
        ) {
            if (hasHero) {
                item(key = "hero", contentType = "hero") {
                    HeroPager(heroes = home.hero, maxArtHeight = maxHeroArtHeight, actions = actions)
                }
            } else {
                item(key = "top", contentType = "top") {
                    Column(Modifier.statusBarsPadding()) {
                        Spacer(Modifier.height(HomeTopBarHeight))
                        ScanBanner(scan)
                    }
                }
            }
            if (permission.partial) {
                item(key = "partial", contentType = "partial") {
                    PartialAccessCard(
                        onAllowAll = actions::requestPermission,
                        modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 16.dp),
                    )
                }
            }
            if (home.continueWatching.isNotEmpty()) {
                item(key = "continue", contentType = "landscapeRow") {
                    HomeSection(title = "Continue watching") {
                        LandscapeRow(home.continueWatching, actions)
                    }
                }
            }
            if (home.recentlyAdded.isNotEmpty()) {
                item(key = "recent", contentType = "posterRow") {
                    HomeSection(title = "Recently added") {
                        VideoPosterRow(home.recentlyAdded, actions)
                    }
                }
            }
            if (home.movies.isNotEmpty()) {
                item(key = "movies", contentType = "posterRow") {
                    HomeSection(
                        title = "Movies",
                        onSeeAll = { actions.seeAll(LibraryFilter.MOVIES) },
                    ) {
                        VideoPosterRow(home.movies, actions)
                    }
                }
            }
            if (home.shows.isNotEmpty()) {
                item(key = "shows", contentType = "posterRow") {
                    HomeSection(
                        title = "TV shows",
                        onSeeAll = { actions.seeAll(LibraryFilter.SHOWS) },
                    ) {
                        ShowPosterRow(home.shows, actions)
                    }
                }
            }
            if (home.videos.isNotEmpty()) {
                item(key = "videos", contentType = "posterRow") {
                    HomeSection(
                        title = "Videos",
                        onSeeAll = { actions.seeAll(LibraryFilter.VIDEOS) },
                    ) {
                        VideoPosterRow(home.videos, actions)
                    }
                }
            }
        }
        HomeTopBar(
            listState = listState,
            overHero = hasHero,
            scan = scan,
            onSearch = { actions.navigate(Route.Search) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private fun AppActions.seeAll(filter: LibraryFilter) {
    selectTab(TopDestination.LIBRARY)
    setLibraryFilter(filter)
}

/**
 * Wordmark and search floating over the hero. It starts transparent (with a soft shade for the
 * status bar) and turns into a solid bar once the hero scrolls away.
 */
@Composable
private fun HomeTopBar(
    listState: LazyListState,
    overHero: Boolean,
    scan: ScanUi,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val solidAfterPx = with(LocalDensity.current) { 160.dp.toPx() }
    val scrolled by remember(listState, solidAfterPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > solidAfterPx
        }
    }
    val solid = !overHero || scrolled
    val solidness by animateFloatAsState(if (solid) 1f else 0f, label = "homeTopBar")
    val background = MaterialTheme.colorScheme.background
    val iconTint = if (solid) MaterialTheme.colorScheme.onBackground else Color.White
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f * (1f - solidness)), Color.Transparent),
                    ),
                )
                drawRect(background.copy(alpha = 0.96f * solidness))
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(HomeTopBarHeight)
                .padding(start = dims.gutter, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Nova Fork",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.horizontalGradient(listOf(NovaColors.Amber, NovaColors.Coral)),
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearch) {
                NovaIcon(R.drawable.ic_search, contentDescription = "Search", tint = iconTint)
            }
        }
        // Without a hero the banner lives in the list, so it never covers the first row.
        if (overHero) ScanBanner(scan, Modifier.background(background.copy(alpha = 0.85f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeroPager(heroes: List<HeroUi>, maxArtHeight: Dp, actions: AppActions) {
    val pagerState = rememberPagerState { heroes.size }
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    // Restarts on every settle, so a manual swipe also resets the 7 s timer.
    LaunchedEffect(pagerState, dragged, pagerState.settledPage, heroes.size) {
        if (dragged || heroes.size < 2) return@LaunchedEffect
        delay(HERO_AUTO_ADVANCE_MS)
        pagerState.animateScrollToPage((pagerState.currentPage + 1) % heroes.size)
    }
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artHeight = (maxWidth * 0.62f + 80.dp).coerceAtMost(maxArtHeight)
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val hero = heroes.getOrNull(page)
                if (hero != null) HeroPage(hero = hero, artHeight = artHeight, actions = actions)
            }
        }
        if (heroes.size > 1) {
            PagerDots(
                count = heroes.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun HeroPage(hero: HeroUi, artHeight: Dp, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val openTarget: () -> Unit = {
        when (val target = hero.target) {
            is HeroTarget.Video -> actions.openVideo(target.id)
            is HeroTarget.Show -> actions.openShow(target.key)
        }
    }
    val play: () -> Unit = {
        val videoId = hero.playVideoId
        if (videoId != null) actions.play(videoId) else openTarget()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(artHeight + HeroTextOverhang),
    ) {
        BackdropArt(
            artwork = hero.artwork,
            modifier = Modifier
                .fillMaxWidth()
                .height(artHeight)
                .bottomScrim(MaterialTheme.colorScheme.background, startFraction = 0.4f)
                .clickable(onClickLabel = "Details", onClick = openTarget),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = dims.gutter),
        ) {
            Text(
                text = hero.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val rating = hero.rating
            val meta = hero.metaLine
            val hasRating = rating != null && rating > 0f
            if (hasRating || !meta.isNullOrBlank()) {
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RatingPill(rating)
                    if (!meta.isNullOrBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = play,
                    modifier = Modifier.widthIn(max = 240.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    ButtonContent(R.drawable.ic_play_arrow, hero.playLabel)
                }
                FilledTonalButton(
                    onClick = openTarget,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    ButtonContent(R.drawable.ic_info, "Info")
                }
            }
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 18.dp else 6.dp, label = "pagerDot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                    ),
            )
        }
    }
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

@Composable
private fun VideoPosterRow(cards: List<VideoCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(cards) { card ->
            VideoMenuBox(videoId = card.id, title = card.displayTitle(), actions = actions, showDetails = false) { openMenu ->
                PhonePosterCard(
                    card = card,
                    onClick = { actions.openVideo(card.id) },
                    modifier = Modifier.width(dims.posterWidth),
                    onLongClick = openMenu,
                )
            }
        }
    }
}

@Composable
private fun ShowPosterRow(shows: List<ShowCardUi>, actions: AppActions) {
    val dims = LocalNovaDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
    ) {
        items(shows) { show ->
            PhoneShowCard(
                show = show,
                onClick = { actions.openShow(show.key) },
                modifier = Modifier.width(dims.posterWidth),
            )
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
                if (permission.partial) {
                    PartialAccessCard(onAllowAll = actions::requestPermission)
                }
                EmptyState(
                    icon = R.drawable.ic_video_library,
                    title = "Your videos, with posters",
                    body = "Nova Fork finds the videos on your phone, in folders and on network shares, " +
                        "and dresses them up with posters, backdrops and details.",
                    actionLabel = if (permission.granted) "Scan again" else "Allow access to videos",
                    onAction = { if (permission.granted) actions.refresh() else actions.requestPermission() },
                )
                Column(
                    Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = actions::addFolder, modifier = Modifier.fillMaxWidth()) {
                        ButtonContent(R.drawable.ic_create_new_folder, "Add a folder")
                    }
                    OutlinedButton(
                        onClick = actions::addNetworkSource,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonContent(R.drawable.ic_lan, "Add a network source")
                    }
                    OutlinedButton(
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
