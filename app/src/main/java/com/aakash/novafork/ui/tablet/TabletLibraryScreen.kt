package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.GlassCapsuleRow
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.LargeTitleHeader
import com.aakash.novafork.ui.common.PullDownButton
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.largeTitleTopPadding
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.TopDestination

/**
 * The Library capsules. The view model knows four filters; Streams is split out of the videos
 * filter here so it gets its own capsule (and Home Videos holds only files).
 */
internal enum class LibraryCategory(val label: String, val filter: LibraryFilter) {
    ALL("All", LibraryFilter.ALL),
    MOVIES("Movies", LibraryFilter.MOVIES),
    SHOWS("TV Shows", LibraryFilter.SHOWS),
    HOME_VIDEOS("Home Videos", LibraryFilter.VIDEOS),
    STREAMS("Streams", LibraryFilter.VIDEOS),
    ;

    /** The items of this category out of the view model's list for [filter]. */
    fun select(items: List<LibraryItemUi>): List<LibraryItemUi> = when (this) {
        HOME_VIDEOS -> items.filterNot { it.isStream() }
        STREAMS -> items.filter { it.isStream() }
        ALL, MOVIES, SHOWS -> items
    }

    companion object {
        fun of(filter: LibraryFilter, streams: Boolean): LibraryCategory = when (filter) {
            LibraryFilter.ALL -> ALL
            LibraryFilter.MOVIES -> MOVIES
            LibraryFilter.SHOWS -> SHOWS
            LibraryFilter.VIDEOS -> if (streams) STREAMS else HOME_VIDEOS
        }
    }
}

private fun LibraryItemUi.isStream(): Boolean = this is LibraryItemUi.Video && card.kind == MediaKind.STREAM

private fun LibrarySort.menuLabel(): String = when (this) {
    LibrarySort.RECENT -> "Recently Added"
    LibrarySort.TITLE -> "Title"
    LibrarySort.YEAR -> "Release Date"
    LibrarySort.RATING -> "Rating"
}

/** Narrower windows (a forced tablet layout) have no room for the preview pane. */
private val ListDetailMinWidth = 840.dp

/**
 * Library: Large Title, category capsules with "Sort ▾", and an adaptive grid of 2:3 posters.
 * Landscape keeps a frosted preview pane on the right, driven by [AppActions.preview] and
 * [AppUiState.previewKey]: a tap previews, a second tap opens. Portrait taps open directly.
 */
@Composable
internal fun TabletLibraryScreen(
    state: AppUiState,
    layout: DeviceLayout,
    actions: AppActions,
    category: LibraryCategory,
    onCategory: (LibraryCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val library = state.library
    val items = remember(library.items, category) { category.select(library.items) }
    val previewKey = state.previewKey
    BoxWithConstraints(modifier.fillMaxSize()) {
        val listDetail = layout.isExpanded && maxWidth >= ListDetailMinWidth
        if (listDetail) {
            // Keep something in the pane: the first item initially, and again when a category
            // change removes the previewed one from the grid.
            LaunchedEffect(items, previewKey) {
                if (items.isNotEmpty() && items.none { it.key == previewKey }) actions.preview(items.first())
            }
        }
        val paneWidth = (maxWidth * 0.36f).coerceIn(360.dp, 480.dp)
        Row(Modifier.fillMaxSize()) {
            LibraryGrid(
                items = items,
                category = category,
                sort = library.sort,
                scan = state.scan,
                selectedKey = if (listDetail) previewKey else null,
                onClick = { item ->
                    if (listDetail && item.key != previewKey) actions.preview(item) else actions.openItem(item)
                },
                actions = actions,
                onCategory = onCategory,
                endPadding = if (listDetail) 24.dp else LocalNovaDimens.current.gutter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            if (listDetail) {
                TabletPreviewPane(
                    previewKey = previewKey,
                    item = items.firstOrNull { it.key == previewKey },
                    video = state.videoDetails?.takeIf { previewKey == "v:${it.id}" },
                    show = state.showDetails?.takeIf { previewKey == "s:${it.key}" },
                    actions = actions,
                    modifier = Modifier
                        .width(paneWidth)
                        .fillMaxHeight()
                        .padding(top = largeTitleTopPadding(), end = 24.dp, bottom = navigationBarBottom() + 24.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryItemUi>,
    category: LibraryCategory,
    sort: LibrarySort,
    scan: ScanUi,
    selectedKey: String?,
    onClick: (LibraryItemUi) -> Unit,
    actions: AppActions,
    onCategory: (LibraryCategory) -> Unit,
    endPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dims.gridMinCell),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dims.gutter,
            end = endPadding,
            bottom = navigationBarBottom() + 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(Modifier.fillMaxWidth()) {
                LargeTitleHeader("Library", horizontalPadding = 0.dp)
                GlassCapsuleRow(
                    items = LibraryCategory.entries.map { it.label },
                    selectedIndex = category.ordinal,
                    onSelect = { index -> onCategory(LibraryCategory.entries[index]) },
                    contentPadding = PaddingValues(0.dp),
                    trailing = {
                        PullDownButton(
                            label = "Sort",
                            items = LibrarySort.entries.map { option ->
                                GlassMenuItem(option.menuLabel(), checked = option == sort) { actions.setLibrarySort(option) }
                            },
                        )
                    },
                )
                ScanBanner(scan, Modifier.padding(top = 4.dp))
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                LibraryEmpty(category = category, actions = actions, onCategory = onCategory)
            }
        } else {
            items(items, key = { it.key }, contentType = { "poster" }) { item ->
                TabletLibraryCard(
                    item = item,
                    onClick = { onClick(item) },
                    menu = libraryItemMenu(item, actions),
                    selected = item.key == selectedKey,
                )
            }
        }
    }
}

@Composable
private fun LibraryEmpty(category: LibraryCategory, actions: AppActions, onCategory: (LibraryCategory) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (category) {
            LibraryCategory.ALL -> EmptyState(
                icon = R.drawable.ic_video_library,
                title = "Your Library Is Empty",
                body = "Add a folder or a network share, or allow access to the videos on this tablet.",
                actionLabel = "Add Folder",
                onAction = actions::addFolder,
                secondaryLabel = "Browse Sources",
                onSecondary = { actions.selectTab(TopDestination.BROWSE) },
            )
            LibraryCategory.STREAMS -> EmptyState(
                icon = R.drawable.ic_live_tv,
                title = "No Streams",
                body = "Save a live channel or a video link to watch it from here.",
                actionLabel = "Add Stream",
                onAction = { actions.showDialog(DialogUi.AddStream) },
                secondaryLabel = "Show All",
                onSecondary = { onCategory(LibraryCategory.ALL) },
            )
            LibraryCategory.MOVIES, LibraryCategory.SHOWS, LibraryCategory.HOME_VIDEOS -> EmptyState(
                icon = R.drawable.ic_filter_list,
                title = when (category) {
                    LibraryCategory.MOVIES -> "No Movies"
                    LibraryCategory.SHOWS -> "No TV Shows"
                    else -> "No Home Videos"
                },
                body = "Nothing in your library falls into this category yet.",
                actionLabel = "Show All",
                onAction = { onCategory(LibraryCategory.ALL) },
            )
        }
    }
}
