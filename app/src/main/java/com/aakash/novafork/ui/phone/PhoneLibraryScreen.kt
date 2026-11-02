package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.EmptyState
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
                IosIconButton(onClick = { actions.navigate(Route.Search) }) {
                    NovaIcon(R.drawable.ic_search, contentDescription = "Search")
                }
                // What to show sits in this menu with the order (owner: not on the page itself).
                // The Watchlist and Favorites pages are lists of their own, without it.
                if (library.filter.isChip) {
                    SortMenu(
                        filter = library.filter,
                        sort = library.sort,
                        onFilter = actions::setLibraryFilter,
                        onSort = actions::setLibrarySort,
                    )
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (library.items.isEmpty()) {
                LibraryEmpty(filter = library.filter, actions = actions)
            } else if (library.timelineMonths.isNotEmpty()) {
                // All videos, newest first, under month headings (each card says the day it arrived).
                PhoneTimelineGrid(months = library.timelineMonths, actions = actions, state = gridState)
            } else {
                PhoneLibraryGrid(items = library.items, actions = actions, state = gridState)
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
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IosIconButton(onClick = { expanded = true }) {
            NovaIcon(R.drawable.ic_sort, contentDescription = "Sort")
        }
        NovaDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, title = "Show") {
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
        EmptyState(
            icon = content.icon,
            title = content.title,
            body = content.body,
            actionLabel = if (filter == LibraryFilter.ALL) "Add a Folder" else null,
            onAction = if (filter == LibraryFilter.ALL) actions::addFolder else null,
        )
    }
}

private class EmptyContent(@DrawableRes val icon: Int, val title: String, val body: String)

private fun emptyContentFor(filter: LibraryFilter): EmptyContent = when (filter) {
    LibraryFilter.ALL -> EmptyContent(
        R.drawable.ic_video_library,
        "Nothing here yet",
        "Videos from your device, folders and network shares show up here after a scan.",
    )
    LibraryFilter.MOVIES -> EmptyContent(
        R.drawable.ic_movie,
        "No movies yet",
        "Movies are recognised from names like “Dune (2021).mkv”. Use Identify on a video to match it by hand.",
    )
    LibraryFilter.SHOWS -> EmptyContent(
        R.drawable.ic_tv,
        "No TV shows yet",
        "Episodes are grouped into shows from names like “Show.S01E02.mkv”.",
    )
    LibraryFilter.VIDEOS -> EmptyContent(
        R.drawable.ic_video_file,
        "No other videos",
        "Home videos, unidentified files and saved streams appear here.",
    )
    LibraryFilter.WATCHLIST -> EmptyContent(
        R.drawable.ic_bookmark,
        "Your Watchlist is empty",
        "Tap + on the Home hero to save something for later.",
    )
    LibraryFilter.FAVORITES -> EmptyContent(
        R.drawable.ic_star,
        "No favorites yet",
        "Tap the star on the Home hero to add a movie, show or video.",
    )
}
