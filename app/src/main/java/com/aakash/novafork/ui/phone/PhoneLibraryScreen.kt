package com.aakash.novafork.ui.phone

import com.aakash.novafork.ui.common.PosterSkeletonGrid
import com.aakash.novafork.ui.common.LocalLibraryLoading
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.NovaDropdownMenu
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaMenuDivider
import com.aakash.novafork.ui.common.NovaMenuItem
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.ScanUi

@Composable
fun PhoneLibraryScreen(
    library: LibraryUi,
    scan: ScanUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    // A new filter or sort is a different list: start it from the top. Saved so that coming back
    // to the tab (restored scroll position) does not count as a change.
    var shownListKey by rememberSaveable { mutableStateOf("${library.filter.name}/${library.sort.name}") }
    LaunchedEffect(library.filter, library.sort) {
        val key = "${library.filter.name}/${library.sort.name}"
        if (key != shownListKey) {
            shownListKey = key
            gridState.scrollToItem(0)
        }
    }

    // Select (owner: batch actions): tick several videos, then act on all of them at once.
    var selection by remember { mutableStateOf<VideoSelection?>(null) }
    BackHandler(enabled = selection != null) { selection = null }
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(
            title = when {
                !library.filter.isChip -> library.filter.label
                library.filter == LibraryFilter.ALL && library.sort == LibrarySort.RECENT -> "Recently Added"
                library.filter == LibraryFilter.ALL -> "All Videos"
                else -> "Library"
            },
            onBack = actions::back,
            actions = {
                val picking = selection
                if (picking != null) {
                    // Selecting: only Done, so nothing crowds the title (owner).
                    IosTextButton(onClick = { selection = null }) {
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    IosIconButton(onClick = { actions.navigate(Route.Search) }) {
                        NovaIcon(R.drawable.ic_search, contentDescription = "Search")
                    }
                    // What to show and Select sit in this menu with the order (owner: not on the page
                    // itself). The Watchlist and Favorites pages are lists of their own, without it.
                    if (library.filter.isChip) {
                        SortMenu(
                            filter = library.filter,
                            sort = library.sort,
                            onFilter = actions::setLibraryFilter,
                            onSort = actions::setLibrarySort,
                            onSelect = if (library.items.isNotEmpty()) ({ selection = VideoSelection() }) else null,
                            sources = library.sources,
                            source = library.sourceFilter,
                            onSource = actions::setLibrarySource,
                        )
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            CompositionLocalProvider(LocalVideoSelection provides selection) {
                if (library.items.isEmpty() && (LocalLibraryLoading.current || scan.running)) {
                    PosterSkeletonGrid()
                } else if (library.items.isEmpty()) {
                    LibraryEmpty(filter = library.filter, actions = actions)
                } else if (library.timelineMonths.isNotEmpty()) {
                    // All videos, newest first, under month headings (each card says the day it arrived).
                    PhoneTimelineGrid(months = library.timelineMonths, actions = actions, state = gridState)
                } else {
                    PhoneLibraryGrid(items = library.items, actions = actions, state = gridState)
                }
            }
            selection?.let { picked ->
                SelectionBar(
                    picked = picked,
                    actions = actions,
                    onDone = { selection = null },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * The sort menu: "Show" (All, Movies, TV Shows) over "Sort by", a tick on the current choice of
 * each, as iOS's pull-down menus pick values.
 */
@Composable
private fun SortMenu(
    filter: LibraryFilter,
    sort: LibrarySort,
    onFilter: (LibraryFilter) -> Unit,
    onSort: (LibrarySort) -> Unit,
    /** Starts picking videos (Select), when there are any. */
    onSelect: (() -> Unit)? = null,
    /** The library's sources; with two or more the menu offers one at a time. */
    sources: List<Pair<Long, String>> = emptyList(),
    source: Long? = null,
    onSource: (Long?) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IosIconButton(onClick = { expanded = true }) {
            NovaIcon(R.drawable.ic_sort, contentDescription = "Sort")
        }
        NovaDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onSelect != null) {
                NovaMenuItem(
                    text = { Text("Select Videos") },
                    leadingIcon = { NovaIcon(R.drawable.ic_check_circle, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect()
                    },
                )
                NovaMenuDivider()
            }
            MenuHeading("Show")
            LibraryFilter.entries.filter { it.isChip }.forEach { option ->
                NovaMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onFilter(option)
                    },
                    checked = option == filter,
                )
            }
            NovaMenuDivider()
            MenuHeading("Sort by")
            LibrarySort.entries.forEach { option ->
                NovaMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSort(option)
                    },
                    checked = option == sort,
                )
            }
            if (sources.size > 1) {
                NovaMenuDivider()
                MenuHeading("Source")
                NovaMenuItem(
                    text = { Text("All Sources") },
                    onClick = {
                        expanded = false
                        onSource(null)
                    },
                    checked = source == null,
                )
                sources.forEach { (id, name) ->
                    NovaMenuItem(
                        text = { Text(name, maxLines = 1) },
                        onClick = {
                            expanded = false
                            onSource(id)
                        },
                        checked = source == id,
                    )
                }
            }
        }
    }
}

/** A section's small grey heading inside a menu, set as the menu's own title is. */
@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = IosColors.SecondaryLabel,
        maxLines = 1,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun LibraryEmpty(filter: LibraryFilter, actions: AppActions) {
    val content = emptyContentFor(filter)
    CenteredScrollable {
        PhoneEmptyLine(
            text = content.title,
            // Only a library with nothing at all has an obvious next step: a source to scan.
            actionLabel = if (filter == LibraryFilter.ALL) "Add a source" else null,
            onAction = if (filter == LibraryFilter.ALL) ({ actions.navigate(Route.Browse) }) else null,
        )
    }
}

private class EmptyContent(val title: String)

private fun emptyContentFor(filter: LibraryFilter): EmptyContent = when (filter) {
    LibraryFilter.ALL -> EmptyContent("Nothing here yet")
    LibraryFilter.MOVIES -> EmptyContent("No movies yet")
    LibraryFilter.SHOWS -> EmptyContent("No TV shows yet")
    LibraryFilter.VIDEOS -> EmptyContent("No other videos")
    LibraryFilter.WATCHLIST -> EmptyContent("Your Watchlist is empty")
    LibraryFilter.FAVORITES -> EmptyContent("No favorites yet")
    LibraryFilter.DUPLICATES -> EmptyContent("No duplicates found")
}

/**
 * What to do with the ticked videos: mark them watched, put them on the Watchlist (an episode puts
 * its show), or add them to a playlist; each ends the selection. Floats over the grid's foot.
 */
@Composable
private fun SelectionBar(picked: VideoSelection, actions: AppActions, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val ids = picked.selected.toList()
    Row(
        modifier
            .padding(bottom = LocalBottomBarSpace.current + 12.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
            .background(IosColors.Popover, RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            if (ids.isEmpty()) "Select videos" else "${ids.size} selected",
            style = MaterialTheme.typography.bodyMedium,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(start = 8.dp),
        )
        IosTextButton(enabled = ids.isNotEmpty(), onClick = {
            ids.forEach { actions.setWatched(it, true) }
            onDone()
        }) { Text("Watched") }
        IosTextButton(enabled = ids.isNotEmpty(), onClick = {
            ids.mapNotNull { actions.saveKeyFor(it) }.distinct()
                .filterNot { actions.isOnWatchlist(it) }
                .forEach { actions.toggleWatchlist(it) }
            onDone()
        }) { Text("Watchlist") }
        IosTextButton(enabled = ids.isNotEmpty(), onClick = {
            actions.addToPlaylist(ids)
            onDone()
        }) { Text("Playlist") }
    }
}
