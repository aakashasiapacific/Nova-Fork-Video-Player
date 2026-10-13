package com.aakash.novafork.ui.tablet

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.heroHeightFor
import com.aakash.novafork.ui.common.ActivityIndicator
import com.aakash.novafork.ui.common.BackdropArt
import com.aakash.novafork.ui.common.GlassBackdrop
import com.aakash.novafork.ui.common.GlassCircleButton
import com.aakash.novafork.ui.common.GlassStyle
import com.aakash.novafork.ui.common.InsetGroup
import com.aakash.novafork.ui.common.ListRow
import com.aakash.novafork.ui.common.ListRowIcon
import com.aakash.novafork.ui.common.LocalGlassState
import com.aakash.novafork.ui.common.NovaGlyphs
import com.aakash.novafork.ui.common.PageDots
import com.aakash.novafork.ui.common.PrimaryCapsuleButton
import com.aakash.novafork.ui.common.RowHeader
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.TextAction
import com.aakash.novafork.ui.common.bottomScrim
import com.aakash.novafork.ui.common.largeTitleTopPadding
import com.aakash.novafork.ui.common.rememberGlassState
import com.aakash.novafork.ui.common.startScrim
import com.aakash.novafork.ui.common.topScrim
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaType
import kotlinx.coroutines.delay

/** The hero advances on its own every 8 s (Apple TV). */
private const val HERO_ADVANCE_MS = 8_000L

/** Space between the bottom of the hero text and the hero's bottom edge, above the page dots. */
private val HeroDotsBottom = 20.dp

/**
 * Home: a full-bleed hero carousel under the floating tabs (78% of the height in landscape, 60%
 * in portrait), then rows of 16:9 cards. [onSeeAll] opens a Library category (row headers).
 */
@Composable
internal fun TabletHomeScreen(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    layout: DeviceLayout,
    actions: AppActions,
    onSeeAll: (LibraryCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val heroHeight = if (constraints.hasBoundedHeight) heroHeightFor(layout, maxWidth, maxHeight) else 560.dp
        val overHero = !home.isEmpty && home.hero.isNotEmpty()
        TabletRefreshBox(
            scanning = scan.running,
            onRefresh = actions::refresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = largeTitleTopPadding() - 12.dp,
            indicatorColor = if (overHero) Color.White else NovaColors.secondaryLabel,
        ) {
            if (home.isEmpty) {
                HomeOnboarding(scan = scan, permission = permission, actions = actions)
            } else {
                HomeFeed(
                    home = home,
                    scan = scan,
                    permission = permission,
                    heroHeight = heroHeight,
                    actions = actions,
                    onSeeAll = onSeeAll,
                )
            }
        }
    }
}

// ─────────────────────────────── Feed ───────────────────────────────

private enum class HomeRowKind { CONTINUE, RECENT, MOVIES, SHOWS, VIDEOS }

@Composable
private fun HomeFeed(
    home: HomeUi,
    scan: ScanUi,
    permission: PermissionUi,
    heroHeight: Dp,
    actions: AppActions,
    onSeeAll: (LibraryCategory) -> Unit,
) {
    val dims = LocalNovaDimens.current
    val rows = listOfNotNull(
        HomeRowKind.CONTINUE.takeIf { home.continueWatching.isNotEmpty() },
        HomeRowKind.RECENT.takeIf { home.recentlyAdded.isNotEmpty() },
        HomeRowKind.MOVIES.takeIf { home.movies.isNotEmpty() },
        HomeRowKind.SHOWS.takeIf { home.shows.isNotEmpty() },
        HomeRowKind.VIDEOS.takeIf { home.videos.isNotEmpty() },
    )
    val topClearance = largeTitleTopPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navigationBarBottom() + dims.sectionSpacing),
    ) {
        if (home.hero.isNotEmpty()) {
            item(key = "hero", contentType = "hero") {
                TabletHero(heroes = home.hero, height = heroHeight, actions = actions)
            }
        } else {
            item(key = "top", contentType = "top") { Spacer(Modifier.height(topClearance)) }
        }
        if (scan.running || scan.fetchingArtwork) {
            item(key = "scan", contentType = "scan") {
                ScanBanner(scan, Modifier.padding(start = dims.gutter - 16.dp, end = dims.gutter - 16.dp, top = 12.dp))
            }
        }
        if (permission.partial) {
            item(key = "partial", contentType = "partial") {
                PartialAccessRow(onAllow = actions::requestPermission, modifier = Modifier.padding(top = 20.dp))
            }
        }
        rows.forEachIndexed { index, kind ->
            item(key = kind.name, contentType = "row") {
                HomeRow(
                    kind = kind,
                    home = home,
                    actions = actions,
                    onSeeAll = onSeeAll,
                    topPadding = if (index == 0) 20.dp else dims.sectionSpacing,
                )
            }
        }
    }
}

@Composable
private fun HomeRow(
    kind: HomeRowKind,
    home: HomeUi,
    actions: AppActions,
    onSeeAll: (LibraryCategory) -> Unit,
    topPadding: Dp,
) {
    val dims = LocalNovaDimens.current
    when (kind) {
        HomeRowKind.CONTINUE -> CardRow(title = "Continue Watching", onHeader = null, topPadding = topPadding) {
            items(home.continueWatching, key = { it.id }) { card ->
                TabletWideCard(
                    artwork = card.artwork,
                    title = card.primaryLine(),
                    caption = card.continueCaption(),
                    onClick = { actions.play(card.id) },
                    onClickLabel = "Resume",
                    progress = card.progress,
                    menu = videoMenu(card, actions),
                    modifier = Modifier.width(dims.continueCardWidth),
                )
            }
        }
        HomeRowKind.RECENT -> CardRow(
            title = "Recently Added",
            onHeader = {
                actions.setLibrarySort(LibrarySort.RECENT)
                onSeeAll(LibraryCategory.ALL)
            },
            topPadding = topPadding,
        ) {
            items(home.recentlyAdded, key = { it.id }) { card ->
                VideoRowCard(card, actions)
            }
        }
        HomeRowKind.MOVIES -> CardRow(title = "Movies", onHeader = { onSeeAll(LibraryCategory.MOVIES) }, topPadding = topPadding) {
            items(home.movies, key = { it.id }) { card -> VideoRowCard(card, actions) }
        }
        HomeRowKind.SHOWS -> CardRow(title = "TV Shows", onHeader = { onSeeAll(LibraryCategory.SHOWS) }, topPadding = topPadding) {
            items(home.shows, key = { it.key }) { show ->
                TabletWideCard(
                    artwork = show.artwork,
                    title = show.title,
                    caption = show.caption(),
                    onClick = { actions.openShow(show.key) },
                    menu = showMenu(show.key, actions),
                    modifier = Modifier.width(dims.landscapeCardWidth),
                )
            }
        }
        HomeRowKind.VIDEOS -> CardRow(title = "Home Videos", onHeader = { onSeeAll(LibraryCategory.HOME_VIDEOS) }, topPadding = topPadding) {
            items(home.videos, key = { it.id }) { card -> VideoRowCard(card, actions) }
        }
    }
}

@Composable
private fun VideoRowCard(card: VideoCardUi, actions: AppActions) {
    TabletWideCard(
        artwork = card.artwork,
        title = card.primaryLine(),
        caption = card.caption(),
        onClick = { actions.openCard(card) },
        watched = card.watched,
        menu = videoMenu(card, actions),
        modifier = Modifier.width(LocalNovaDimens.current.landscapeCardWidth),
    )
}

/** Row header ("Movies ›") and a horizontally snapping row that starts on the page margin. */
@Composable
private fun CardRow(title: String, onHeader: (() -> Unit)?, topPadding: Dp, content: LazyListScope.() -> Unit) {
    val dims = LocalNovaDimens.current
    val state = rememberLazyListState()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
    ) {
        RowHeader(title, onClick = onHeader)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            content = content,
        )
    }
}

@Composable
private fun PartialAccessRow(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    InsetGroup(modifier = modifier.widthIn(max = 720.dp), horizontalPadding = dims.gutter) {
        ListRow(
            title = "Allow Access to All Videos",
            subtitle = "Nova can see only some of the videos on this tablet.",
            leading = { ListRowIcon(R.drawable.ic_video_library, NovaColors.tint) },
            chevron = true,
            showDivider = false,
            onClick = onAllow,
        )
    }
}

// ─────────────────────────────── Hero ───────────────────────────────

@Composable
private fun TabletHero(heroes: List<HeroUi>, height: Dp, actions: AppActions) {
    val pager = rememberPagerState { heroes.size }
    if (heroes.size > 1) {
        // Restarts whenever a page settles, so a swipe also resets the timer.
        LaunchedEffect(pager.settledPage, heroes.size) {
            delay(HERO_ADVANCE_MS)
            if (!pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.settledPage + 1) % heroes.size, animationSpec = tween(durationMillis = 700))
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.matchParentSize(),
            key = { page -> heroes.getOrNull(page)?.target?.pageKey() ?: page },
        ) { page ->
            val hero = heroes.getOrNull(page)
            if (hero != null) HeroPage(hero = hero, actions = actions)
        }
        PageDots(
            count = heroes.size,
            current = pager.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = HeroDotsBottom),
        )
    }
}

private fun HeroTarget.pageKey(): String = when (this) {
    is HeroTarget.Video -> "v:$id"
    is HeroTarget.Show -> "s:$key"
}

@Composable
private fun HeroPage(hero: HeroUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    // The artwork is a backdrop of its own, so the glass circle over it blurs the picture.
    val heroGlass = rememberGlassState()
    val open = { actions.openHero(hero.target) }
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(
            heroGlass,
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Details",
                    onClick = open,
                ),
        ) {
            BackdropArt(
                artwork = hero.artwork,
                modifier = Modifier
                    .matchParentSize()
                    .topScrim(Color.Black.copy(alpha = 0.35f), endFraction = 0.22f)
                    .startScrim(Color.Black.copy(alpha = 0.7f), endFraction = 0.7f)
                    .bottomScrim(Color.Black, startFraction = 0.55f),
            )
        }
        CompositionLocalProvider(LocalGlassState provides heroGlass) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = dims.heroMargin, end = dims.heroMargin, bottom = dims.heroMargin)
                    .widthIn(max = dims.heroTextMaxWidth),
            ) {
                Text(
                    text = hero.title,
                    style = NovaType.heroTitle,
                    color = NovaColors.OnMedia,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = hero.metaLine?.takeIf { it.isNotBlank() } ?: hero.genres.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = meta,
                        style = NovaType.subheadSemibold,
                        color = NovaColors.OnMediaSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val overview = hero.overview
                if (!overview.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = overview,
                        style = NovaType.subhead,
                        color = NovaColors.OnMediaSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryCapsuleButton(
                        text = hero.playLabel,
                        onClick = {
                            val id = hero.playVideoId
                            if (id != null) actions.play(id) else open()
                        },
                    )
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

// ─────────────────────────────── Empty library ───────────────────────────────

@Composable
private fun HomeOnboarding(scan: ScanUi, permission: PermissionUi, actions: AppActions) {
    val needsAccess = !permission.granted || permission.partial
    val title: String
    val body: String
    when {
        !permission.granted -> {
            title = "Your Videos, Beautifully Organised"
            body = "Nova finds the movies, shows and clips on this tablet, in folders you pick and on " +
                "network shares, then dresses them up with posters and details."
        }
        scan.running -> {
            title = "Looking for Videos…"
            body = scan.label ?: "This can take a moment the first time."
        }
        permission.partial -> {
            title = "No Videos Yet"
            body = "Nova can only see the videos you picked. Allow access to all of them, add a folder, " +
                "connect to a network share or open a stream."
        }
        else -> {
            title = "No Videos Yet"
            body = "Nothing turned up on this tablet. Add a folder, connect to a network share or open a stream."
        }
    }
    CenteredScrollColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 40.dp,
            end = 40.dp,
            top = largeTitleTopPadding(),
            bottom = navigationBarBottom() + 24.dp,
        ),
    ) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_video_library),
                contentDescription = null,
                tint = NovaColors.secondaryLabel,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(text = title, style = NovaType.title1, color = NovaColors.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text = body, style = NovaType.callout, color = NovaColors.secondaryLabel, textAlign = TextAlign.Center)
            if (scan.running) {
                Spacer(Modifier.height(20.dp))
                ActivityIndicator()
            }
            Spacer(Modifier.height(28.dp))
            if (needsAccess) {
                PrimaryCapsuleButton(
                    text = if (permission.partial) "Allow Access to All Videos" else "Allow Access to Videos",
                    onClick = actions::requestPermission,
                    playGlyph = false,
                    minWidth = 280.dp,
                )
                Spacer(Modifier.height(8.dp))
                TextAction("Add Folder", onClick = actions::addFolder)
            } else {
                PrimaryCapsuleButton(
                    text = "Add Folder",
                    onClick = actions::addFolder,
                    playGlyph = false,
                    minWidth = 280.dp,
                )
                Spacer(Modifier.height(8.dp))
            }
            TextAction("Add Network Share", onClick = { actions.showDialog(DialogUi.SmbServer()) })
            TextAction("Open Network Stream", onClick = { actions.showDialog(DialogUi.OpenUrl) })
        }
    }
}
