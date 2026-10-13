package com.aakash.novafork.ui.phone

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.AccountButton
import com.aakash.novafork.ui.common.ActivityIndicator
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.LargeTitleHeader
import com.aakash.novafork.ui.common.ListRow
import com.aakash.novafork.ui.common.PullDownButton
import com.aakash.novafork.ui.common.RowHeader
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.common.rememberTitleCollapsed
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.VideoCardUi

/** The Library list on the phone. Each maps onto a library filter (streams are part of Videos). */
private enum class LibraryCategory(val title: String, val filter: LibraryFilter, @DrawableRes val emptyIcon: Int) {
    MOVIES("Movies", LibraryFilter.MOVIES, R.drawable.ic_movie),
    SHOWS("TV Shows", LibraryFilter.SHOWS, R.drawable.ic_tv),
    HOME_VIDEOS("Home Videos", LibraryFilter.VIDEOS, R.drawable.ic_video_file),
    STREAMS("Streams", LibraryFilter.VIDEOS, R.drawable.ic_live_tv),
    ;

    /** Videos and streams share one filter; split them here. */
    fun accepts(item: LibraryItemUi): Boolean = when (this) {
        HOME_VIDEOS -> item !is LibraryItemUi.Video || item.card.kind != MediaKind.STREAM
        STREAMS -> item is LibraryItemUi.Video && item.card.kind == MediaKind.STREAM
        MOVIES, SHOWS -> true
    }

    companion object {
        /** The page a filter set elsewhere (Home's "Movies ›") opens; ALL is the list itself. */
        fun forFilter(filter: LibraryFilter): LibraryCategory? = when (filter) {
            LibraryFilter.ALL -> null
            LibraryFilter.MOVIES -> MOVIES
            LibraryFilter.SHOWS -> SHOWS
            LibraryFilter.VIDEOS -> HOME_VIDEOS
        }
    }
}

/**
 * Library: Large Title, the category list (Movies, TV Shows, Home Videos, Streams with counts)
 * and a 2-column grid of Recently Added 16:9 cards. A category opens as a pushed page with a
 * "Sort ▾" menu and a 3-column poster grid; the open category is this screen's own state, kept
 * in step with the view model's library filter.
 */
@Composable
internal fun PhoneLibraryScreen(
    library: LibraryUi,
    recentlyAdded: List<VideoCardUi>,
    streams: List<VideoCardUi>,
    scan: ScanUi,
    actions: AppActions,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootList = rememberLazyListState()
    var category by rememberSaveable { mutableStateOf(LibraryCategory.forFilter(library.filter)) }
    // Follow a filter chosen elsewhere (Home's row headers, a tab re-tap that resets it to ALL).
    LaunchedEffect(library.filter) {
        val current = category
        if (current == null || current.filter != library.filter) category = LibraryCategory.forFilter(library.filter)
    }
    val open: (LibraryCategory) -> Unit = { picked ->
        category = picked
        actions.setLibraryFilter(picked.filter)
    }
    val close: () -> Unit = {
        category = null
        actions.setLibraryFilter(LibraryFilter.ALL)
    }
    if (category != null && LocalPhonePageActive.current) BackHandler(onBack = close)

    AnimatedContent(
        targetState = category,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { if (targetState != null) phonePushTransition() else phonePopTransition() },
        label = "libraryCategory",
    ) { shown ->
        if (shown == null) {
            LibraryRoot(
                library = library,
                recentlyAdded = recentlyAdded,
                streamCount = streams.size,
                scan = scan,
                actions = actions,
                onAccount = onAccount,
                onOpen = open,
                listState = rootList,
            )
        } else {
            LibraryCategoryPage(
                category = shown,
                library = library,
                scan = scan,
                actions = actions,
                onBack = close,
            )
        }
    }
}

// ───────────────────────────── Root ─────────────────────────────

@Composable
private fun LibraryRoot(
    library: LibraryUi,
    recentlyAdded: List<VideoCardUi>,
    streamCount: Int,
    scan: ScanUi,
    actions: AppActions,
    onAccount: () -> Unit,
    onOpen: (LibraryCategory) -> Unit,
    listState: LazyListState,
) {
    val dims = LocalNovaDimens.current
    val collapsed = rememberTitleCollapsed(listState)
    val counts = library.counts
    val videos = counts[LibraryFilter.VIDEOS] ?: 0
    val categories = listOf(
        LibraryCategory.MOVIES to (counts[LibraryFilter.MOVIES] ?: 0),
        LibraryCategory.SHOWS to (counts[LibraryFilter.SHOWS] ?: 0),
        LibraryCategory.HOME_VIDEOS to (videos - streamCount).coerceAtLeast(0),
        LibraryCategory.STREAMS to streamCount,
    ).filter { it.second > 0 }
    val rows = remember(recentlyAdded) { recentlyAdded.chunked(2) }
    PhoneLargeTitlePage(
        title = "Library",
        collapsed = collapsed,
        trailing = { AccountButton(onClick = onAccount) },
    ) {
        PhoneRefreshBox(scanning = scan.running, onRefresh = actions::refresh) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = glassTabBarClearance()),
            ) {
                item(key = "title") { LargeTitleHeader("Library", collapsed = collapsed) }
                item(key = "scan") { ScanBanner(scan) }
                if (categories.isEmpty() && recentlyAdded.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_video_library,
                            title = "Nothing Here Yet",
                            body = "Videos from your device, folders and network shares show up here after a scan.",
                            actionLabel = "Add Folder",
                            onAction = actions::addFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                        )
                    }
                }
                categories.forEachIndexed { index, (category, count) ->
                    item(key = "category:${category.name}") {
                        ListRow(
                            title = category.title,
                            value = count.toString(),
                            chevron = true,
                            showDivider = index < categories.lastIndex,
                            horizontalPadding = dims.gutter,
                            onClick = { onOpen(category) },
                        )
                    }
                }
                if (rows.isNotEmpty()) {
                    item(key = "recentHeader") {
                        RowHeader("Recently Added", modifier = Modifier.padding(top = dims.sectionSpacing))
                    }
                    items(rows, key = { pair -> "recent:${pair.first().id}" }) { pair ->
                        PhoneTwoColumnRow(pair, Modifier.padding(bottom = 16.dp)) { card, cell ->
                            PhoneWideCard(
                                title = card.cardTitle(),
                                caption = card.cardCaption(),
                                artwork = card.artwork,
                                onClick = { if (card.kind == MediaKind.STREAM) actions.play(card.id) else actions.openVideo(card.id) },
                                modifier = cell,
                                width = null,
                                preferStill = card.kind == MediaKind.EPISODE,
                                menuItems = videoMenuItems(card, actions),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Category ─────────────────────────────

@Composable
private fun LibraryCategoryPage(
    category: LibraryCategory,
    library: LibraryUi,
    scan: ScanUi,
    actions: AppActions,
    onBack: () -> Unit,
) {
    val dims = LocalNovaDimens.current
    val grid = rememberLazyGridState()
    val collapsed = rememberTitleCollapsed(grid)
    // Until the view model has applied the filter, library.items still holds the previous list.
    val ready = library.filter == category.filter
    val items = remember(library.items, category, ready) {
        if (ready) library.items.filter(category::accepts) else emptyList()
    }
    // A new sort is a different list: start it from the top. Saved so that coming back to the
    // page (restored scroll position) does not count as a change.
    var shownSort by rememberSaveable { mutableStateOf(library.sort) }
    LaunchedEffect(library.sort) {
        if (library.sort != shownSort) {
            shownSort = library.sort
            grid.scrollToItem(0)
        }
    }
    val sortItems = LibrarySort.entries.map { sort ->
        GlassMenuItem(sort.menuLabel, checked = sort == library.sort) { actions.setLibrarySort(sort) }
    }
    PhoneLargeTitlePage(
        title = category.title,
        collapsed = collapsed,
        leading = { BackButton(onClick = onBack) },
        trailing = {
            PullDownButton(
                label = "Sort",
                items = sortItems,
                menuTitle = "Sort By",
                alignEnd = true,
                height = dims.toolbarButtonSize,
            )
        },
    ) {
        PhoneRefreshBox(scanning = scan.running, onRefresh = actions::refresh) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(dims.posterGridColumns ?: 3),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, bottom = glassTabBarClearance()),
                horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                    LargeTitleHeader(category.title, collapsed = collapsed, horizontalPadding = 0.dp)
                }
                when {
                    !ready -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ActivityIndicator()
                        }
                    }
                    items.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = category.emptyIcon,
                            title = "No ${category.title}",
                            body = emptyBody(category),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                        )
                    }
                    else -> items(items, key = { it.key }) { item ->
                        LibraryPoster(item, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPoster(item: LibraryItemUi, actions: AppActions) {
    when (item) {
        is LibraryItemUi.Video -> {
            val card = item.card
            PhonePosterCard(
                title = card.cardTitle(),
                caption = card.year?.toString() ?: minutesLabel(card.durationMs) ?: card.cardCaption(),
                artwork = card.artwork,
                onClick = { if (card.kind == MediaKind.STREAM) actions.play(card.id) else actions.openVideo(card.id) },
                progress = card.progress,
                watched = card.watched,
                menuItems = videoMenuItems(card, actions),
            )
        }
        is LibraryItemUi.Show -> {
            val show = item.card
            PhonePosterCard(
                title = show.title,
                caption = show.year?.toString() ?: show.cardCaption(),
                artwork = show.artwork,
                onClick = { actions.openShow(show.key) },
                watched = show.episodeCount > 0 && show.unwatchedCount == 0,
                menuItems = listOf(
                    GlassMenuItem("Go to Details", icon = R.drawable.ic_info) { actions.openShow(show.key) },
                ),
            )
        }
    }
}

private fun emptyBody(category: LibraryCategory): String = when (category) {
    LibraryCategory.MOVIES ->
        "Movies are recognised from names like “Dune (2021).mkv”. Use Identify on a video to match it by hand."
    LibraryCategory.SHOWS -> "Episodes are grouped into shows from names like “Show.S01E02.mkv”."
    LibraryCategory.HOME_VIDEOS -> "Home videos and files that aren't movies or episodes appear here."
    LibraryCategory.STREAMS -> "Streams you save in Browse appear here."
}
