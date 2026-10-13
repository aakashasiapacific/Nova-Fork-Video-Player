package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.InsetGroupDefaults
import com.aakash.novafork.ui.common.LargeTitleHeader
import com.aakash.novafork.ui.common.ListRow
import com.aakash.novafork.ui.common.RowHeader
import com.aakash.novafork.ui.common.SearchField
import com.aakash.novafork.ui.common.TextAction
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.common.rememberTitleCollapsed
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.SearchUi
import com.aakash.novafork.ui.model.VideoCardUi

/** Width of a search row's thumbnail plus its gap: where the row's hairline starts. */
private val ThumbnailInset = 64.dp + InsetGroupDefaults.IconGap

/**
 * Search: Large Title, the iOS search field, "Recently Added" before typing, then results grouped
 * into Movies, TV Shows and Videos, each a row with a 16:9 thumbnail.
 */
@Composable
internal fun PhoneSearchScreen(
    search: SearchUi,
    recentlyAdded: List<VideoCardUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    val list = rememberLazyListState()
    val collapsed = rememberTitleCollapsed(list)
    // The field owns its text while the page is shown; driving it from the asynchronously updated
    // view-model state would drop keystrokes. The view model keeps the query across navigation.
    var query by rememberSaveable { mutableStateOf(search.query) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val typed = query.isNotBlank()
    val groups = remember(search.results) { groupResults(search.results) }

    PhoneLargeTitlePage(title = "Search", collapsed = collapsed) {
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(bottom = glassTabBarClearance()),
        ) {
            item(key = "title") { LargeTitleHeader("Search", collapsed = collapsed) }
            item(key = "field") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = dims.gutter - 4.dp, end = dims.gutter - 4.dp, top = 2.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(
                        value = query,
                        onValueChange = { text ->
                            query = text
                            actions.search(text)
                        },
                        modifier = Modifier.weight(1f),
                        // Coming back to existing results should not pop the keyboard over them.
                        autoFocus = query.isEmpty(),
                        focusRequester = focusRequester,
                        onFocusChange = { focused = it },
                        onSubmit = { keyboard?.hide() },
                    )
                    if (focused) {
                        Spacer(Modifier.width(10.dp))
                        TextAction(
                            text = "Cancel",
                            onClick = {
                                query = ""
                                actions.search("")
                                focusManager.clearFocus()
                            },
                        )
                    }
                }
            }
            when {
                !typed -> recentlyAddedRows(recentlyAdded, actions)
                groups.isEmpty() -> item(key = "none") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = R.drawable.ic_search,
                            title = "No Results",
                            body = "Check the spelling or try a new search.",
                        )
                    }
                }
                else -> groups.forEach { group -> resultGroup(group, actions) }
            }
        }
    }
}

private class ResultGroup(val title: String, val items: List<LibraryItemUi>)

/** Movies, TV Shows, Videos (home videos and streams), in that order; empty groups dropped. */
private fun groupResults(results: List<LibraryItemUi>): List<ResultGroup> {
    val movies = ArrayList<LibraryItemUi>()
    val shows = ArrayList<LibraryItemUi>()
    val videos = ArrayList<LibraryItemUi>()
    for (item in results) {
        when {
            item is LibraryItemUi.Show -> shows += item
            item is LibraryItemUi.Video && item.card.kind == MediaKind.MOVIE -> movies += item
            item is LibraryItemUi.Video && item.card.kind == MediaKind.EPISODE -> shows += item
            else -> videos += item
        }
    }
    return listOf(
        ResultGroup("Movies", movies),
        ResultGroup("TV Shows", shows),
        ResultGroup("Videos", videos),
    ).filter { it.items.isNotEmpty() }
}

private fun LazyListScope.recentlyAddedRows(cards: List<VideoCardUi>, actions: AppActions) {
    if (cards.isEmpty()) return
    item(key = "recentHeader") { RowHeader("Recently Added", modifier = Modifier.padding(top = 8.dp)) }
    itemsIndexed(cards, key = { _, card -> "recent:${card.id}" }) { index, card ->
        VideoResultRow(card, actions, showDivider = index < cards.lastIndex)
    }
}

private fun LazyListScope.resultGroup(group: ResultGroup, actions: AppActions) {
    item(key = "header:${group.title}") { RowHeader(group.title, modifier = Modifier.padding(top = 16.dp)) }
    itemsIndexed(group.items, key = { _, item -> "${group.title}:${item.key}" }) { index, item ->
        val last = index == group.items.lastIndex
        when (item) {
            is LibraryItemUi.Video -> VideoResultRow(item.card, actions, showDivider = !last)
            is LibraryItemUi.Show -> {
                val show = item.card
                val menu = rememberPhoneMenuState()
                Box(Modifier.phoneContextTarget(menu)) {
                    ListRow(
                        title = show.title,
                        subtitle = listOfNotNull("TV Show", show.year?.toString()).joinToString(" · "),
                        leading = { PhoneRowThumbnail(show.artwork, show.title, kind = null) },
                        showDivider = !last,
                        dividerInset = LocalNovaDimens.current.gutter + ThumbnailInset,
                        horizontalPadding = LocalNovaDimens.current.gutter,
                        minHeight = 60.dp,
                        onLongClick = { menu.open = true },
                        onClick = { actions.openShow(show.key) },
                    )
                    PhoneContextMenu(
                        menu,
                        listOf(GlassMenuItem("Go to Details", icon = R.drawable.ic_info) { actions.openShow(show.key) }),
                    )
                }
            }
        }
    }
}

/** A video in search: thumbnail, title and kind ("Movie · 2021"); tap opens its details. */
@Composable
private fun VideoResultRow(card: VideoCardUi, actions: AppActions, showDivider: Boolean) {
    val dims = LocalNovaDimens.current
    val menu = rememberPhoneMenuState()
    Box(Modifier.phoneContextTarget(menu)) {
        ListRow(
            title = card.cardTitle(),
            subtitle = card.kindLabel(),
            leading = { PhoneRowThumbnail(card.artwork, card.title, card.kind) },
            showDivider = showDivider,
            dividerInset = dims.gutter + ThumbnailInset,
            horizontalPadding = dims.gutter,
            minHeight = 60.dp,
            onLongClick = { menu.open = true },
            onClick = { actions.openVideo(card.id) },
        )
        PhoneContextMenu(menu, videoMenuItems(card, actions))
    }
}
