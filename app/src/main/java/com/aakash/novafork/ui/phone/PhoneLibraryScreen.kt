package com.aakash.novafork.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
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
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
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
            title = "Library",
            onBack = actions::back,
            actions = {
                IconButton(onClick = { actions.navigate(Route.Search) }) {
                    NovaIcon(R.drawable.ic_search, contentDescription = "Search")
                }
                SortMenu(current = library.sort, onSelect = actions::setLibrarySort)
            },
        )
        ScanBanner(scan)
        FilterChipsRow(library = library, onSelect = actions::setLibraryFilter)
        PhonePullToRefresh(
            scanning = scan.running,
            onRefresh = actions::refresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (library.items.isEmpty()) {
                LibraryEmpty(filter = library.filter, actions = actions)
            } else if (library.timeline.isNotEmpty()) {
                // All videos, newest first, as one gapless grid (each card says when it arrived).
                PhoneLibraryGrid(items = library.timeline.map { LibraryItemUi.Video(it) }, actions = actions, state = gridState)
            } else {
                PhoneLibraryGrid(items = library.items, actions = actions, state = gridState)
            }
        }
    }
}

@Composable
private fun SortMenu(current: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            NovaIcon(R.drawable.ic_sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                    trailingIcon = if (sort == current) {
                        { NovaIcon(R.drawable.ic_check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(library: LibraryUi, onSelect: (LibraryFilter) -> Unit) {
    val dims = LocalNovaDimens.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dims.gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "Videos" left the library (owner): unidentified files are in All videos' timeline.
        items(LibraryFilter.entries - LibraryFilter.VIDEOS, key = { it.name }) { filter ->
            val selected = filter == library.filter
            val count = library.counts[filter]
            FilterChip(
                selected = selected,
                onClick = { onSelect(filter) },
                label = { Text(if (count != null) "${filter.label} · $count" else filter.label) },
                leadingIcon = if (selected) {
                    { NovaIcon(R.drawable.ic_check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun LibraryEmpty(filter: LibraryFilter, actions: AppActions) {
    val content = emptyContentFor(filter)
    CenteredScrollable {
        EmptyState(
            icon = content.icon,
            title = content.title,
            body = content.body,
            actionLabel = if (filter == LibraryFilter.ALL) "Add a folder" else null,
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
}
