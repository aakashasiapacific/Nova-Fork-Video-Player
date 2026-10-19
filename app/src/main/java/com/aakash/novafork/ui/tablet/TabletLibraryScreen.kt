package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.ScanBanner
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.Route

/**
 * Library. Landscape: list-detail (poster grid + preview pane driven by [AppActions.preview]).
 * Portrait: the grid alone, cells open the details page.
 */
@Composable
internal fun TabletLibraryScreen(
    state: AppUiState,
    layout: DeviceLayout,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val library = state.library
    // All videos sorted by Recently added: every video newest first, one gapless grid.
    val items = if (library.timeline.isNotEmpty()) library.timeline.map { LibraryItemUi.Video(it) } else library.items
    val previewKey = state.previewKey
    val listDetail = layout.isExpanded

    if (listDetail) {
        // Keep something in the pane: the first item initially, and again when a filter change
        // removes the previewed one from the list.
        LaunchedEffect(items, previewKey) {
            if (items.isNotEmpty() && items.none { it.key == previewKey }) actions.preview(items.first())
        }
    }

    Column(modifier.fillMaxSize()) {
        LibraryHeader(
            library = library,
            actions = actions,
            // Portrait is too narrow for title, chips and sort on one line.
            stacked = !listDetail,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = dims.gutter, end = dims.gutter - 8.dp, top = 20.dp, bottom = 12.dp),
        )
        ScanBanner(state.scan, Modifier.padding(horizontal = dims.gutter - 16.dp))
        when {
            items.isEmpty() -> LibraryEmpty(
                filter = library.filter,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            listDetail -> Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LibraryGrid(
                    items = items,
                    selectedKey = previewKey,
                    // A second tap on the previewed title opens it.
                    onClick = { item -> if (item.key == previewKey) actions.openItem(item) else actions.preview(item) },
                    onLongClick = { item -> actions.openItem(item) },
                    longClickLabel = { "Open details" },
                    contentPadding = PaddingValues(
                        start = dims.gutter,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = navigationBarBottom() + 24.dp,
                    ),
                    actions = actions,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )
                TabletPreviewPane(
                    previewKey = previewKey,
                    item = items.firstOrNull { it.key == previewKey },
                    video = state.videoDetails?.takeIf { previewKey == "v:${it.id}" },
                    show = state.showDetails?.takeIf { previewKey == "s:${it.key}" },
                    actions = actions,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
            }
            else -> LibraryGrid(
                items = items,
                selectedKey = null,
                onClick = { item -> actions.openItem(item) },
                onLongClick = { item ->
                    when (item) {
                        is LibraryItemUi.Video -> actions.play(item.card.id)
                        is LibraryItemUi.Show -> actions.openShow(item.card.key)
                    }
                },
                longClickLabel = { item -> if (item is LibraryItemUi.Video) "Play" else "Open details" },
                contentPadding = PaddingValues(
                    start = dims.gutter,
                    end = dims.gutter,
                    top = 8.dp,
                    bottom = navigationBarBottom() + 24.dp,
                ),
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

internal fun AppActions.openItem(item: LibraryItemUi) {
    when (item) {
        is LibraryItemUi.Video -> openVideo(item.card.id)
        is LibraryItemUi.Show -> openShow(item.card.key)
    }
}

@Composable
private fun LibraryHeader(library: LibraryUi, actions: AppActions, stacked: Boolean, modifier: Modifier = Modifier) {
    if (stacked) {
        Column(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                SortMenu(sort = library.sort, onSort = actions::setLibrarySort)
            }
            Spacer(Modifier.height(12.dp))
            FilterChipRow(library = library, onFilter = actions::setLibraryFilter)
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(28.dp))
            FilterChipRow(library = library, onFilter = actions::setLibraryFilter, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            SortMenu(sort = library.sort, onSort = actions::setLibrarySort)
        }
    }
}

@Composable
private fun FilterChipRow(library: LibraryUi, onFilter: (LibraryFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Videos" left the library (owner): unidentified files are in All videos' timeline.
        LibraryFilter.entries.filter { it.isChip }.forEach { filter ->
            val selected = filter == library.filter
            val count = library.counts[filter]
            FilterChip(
                selected = selected,
                onClick = { onFilter(filter) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(filter.label)
                        if (count != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(count.toString(), color = LocalContentColor.current.copy(alpha = 0.65f))
                        }
                    }
                },
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
private fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            NovaIcon(R.drawable.ic_sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(sort.label)
            Spacer(Modifier.width(4.dp))
            NovaIcon(R.drawable.ic_expand_more, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    trailingIcon = if (option == sort) {
                        { NovaIcon(R.drawable.ic_check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryItemUi>,
    selectedKey: String?,
    onClick: (LibraryItemUi) -> Unit,
    onLongClick: (LibraryItemUi) -> Unit,
    longClickLabel: (LibraryItemUi) -> String,
    contentPadding: PaddingValues,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dims.gridMinCell),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(items, key = { it.key }, contentType = { "poster" }) { item ->
            LibraryGridCard(item, selectedKey, onClick, onLongClick, longClickLabel, actions)
        }
    }
}

@Composable
private fun LibraryGridCard(
    item: LibraryItemUi,
    selectedKey: String?,
    onClick: (LibraryItemUi) -> Unit,
    onLongClick: (LibraryItemUi) -> Unit,
    longClickLabel: (LibraryItemUi) -> String,
    actions: AppActions,
) {
    when (item) {
        // Videos get a menu (Play, Details, Add to playlist) instead of one long-press action.
        is LibraryItemUi.Video -> VideoMenuBox(
            videoId = item.card.id,
            title = item.card.primaryLine(),
            actions = actions,
            extraItems = { close ->
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { NovaIcon(R.drawable.ic_play_arrow, contentDescription = null) },
                    onClick = {
                        close()
                        actions.play(item.card.id)
                    },
                )
            },
        ) { openMenu ->
            TabletLibraryItemCard(
                item = item,
                onClick = { onClick(item) },
                onLongClick = openMenu,
                onLongClickLabel = "More options",
                selected = item.key == selectedKey,
            )
        }
        is LibraryItemUi.Show -> TabletLibraryItemCard(
            item = item,
            onClick = { onClick(item) },
            onLongClick = { onLongClick(item) },
            onLongClickLabel = longClickLabel(item),
            selected = item.key == selectedKey,
        )
    }
}

@Composable
private fun LibraryEmpty(filter: LibraryFilter, actions: AppActions, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (filter == LibraryFilter.ALL) {
            EmptyState(
                icon = R.drawable.ic_video_library,
                title = "Your library is empty",
                body = "Add a folder or a network share, or allow access to the videos on this tablet.",
                actionLabel = "Add a folder",
                onAction = actions::addFolder,
                secondaryLabel = "Browse sources",
                onSecondary = { actions.navigate(Route.Browse) },
            )
        } else {
            val title = when (filter) {
                LibraryFilter.MOVIES -> "No movies yet"
                LibraryFilter.SHOWS -> "No TV shows yet"
                LibraryFilter.VIDEOS, LibraryFilter.ALL -> "No videos yet"
                LibraryFilter.WATCHLIST -> "Your Watchlist is empty"
                LibraryFilter.FAVORITES -> "No favorites yet"
            }
            EmptyState(
                icon = R.drawable.ic_filter_list,
                title = title,
                body = "Nothing in your library falls into this group yet.",
                actionLabel = "Show everything",
                onAction = { actions.setLibraryFilter(LibraryFilter.ALL) },
            )
        }
    }
}
