package com.aakash.novafork.ui.tablet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.NovaTextField
import com.aakash.novafork.ui.common.musicSearchResults
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.SearchUi
import com.aakash.novafork.ui.phone.RecentSearches

@Composable
internal fun TabletSearchScreen(
    search: SearchUi,
    canGoBack: Boolean,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    // Local text state: echoing every keystroke through the view model would fight the cursor.
    // Seeded from the view model, which outlives rotation, so the two never disagree on return.
    var query by remember { mutableStateOf(search.query) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.padding(start = dims.gutter, end = dims.gutter, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                TabletBackButton(onClick = actions::back)
                Spacer(Modifier.width(16.dp))
            }
            NovaTextField(
                value = query,
                onValueChange = {
                    query = it
                    actions.search(it)
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text("Search videos and music", style = MaterialTheme.typography.titleLarge) },
                leadingIcon = { NovaIcon(R.drawable.ic_search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IosIconButton(
                            onClick = {
                                query = ""
                                actions.search("")
                                focusRequester.requestFocus()
                            },
                        ) {
                            NovaIcon(R.drawable.ic_close, contentDescription = "Clear")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        actions.rememberSearch(query)
                        keyboard?.hide()
                    },
                ),
            )
        }
        val results = search.results
        when {
            query.isBlank() && search.recent.isNotEmpty() -> RecentSearches(
                recent = search.recent,
                onPick = { picked ->
                    query = picked
                    actions.search(picked)
                    actions.rememberSearch(picked)
                    keyboard?.hide()
                },
                onClear = actions::clearRecentSearches,
                bottomSpace = 24.dp,
                onForget = actions::forgetSearch,
            )
            query.isBlank() -> SearchMessage {
                EmptyState(
                    icon = R.drawable.ic_logo,
                    title = "Search your library",
                    body = "Find movies, shows, episodes, videos, songs, albums and artists.",
                )
            }
            !search.isEmpty -> LazyVerticalGrid(
                columns = GridCells.Adaptive(dims.gridMinCell),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dims.gutter,
                    end = dims.gutter,
                    top = 12.dp,
                    bottom = navigationBarBottom() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(dims.rowSpacing),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(results, key = { it.key }, contentType = { "poster" }) { item ->
                    when (item) {
                        is LibraryItemUi.Video -> TabletVideoPosterCard(
                            card = item.card,
                            onOpen = { actions.openVideo(it) },
                            onPlay = { actions.play(it) },
                        )
                        is LibraryItemUi.Show -> TabletShowPosterCard(
                            card = item.card,
                            onClick = { actions.openShow(item.card.key) },
                        )
                    }
                }
                musicSearchResults(search) { actions.playMusic(it.songIds, it.startIndex) }
            }
            // Only claim "no matches" once the results belong to what is typed.
            search.query.trim() == query.trim() -> SearchMessage {
                EmptyState(
                    icon = R.drawable.ic_logo,
                    title = "No Matches",
                    body = "Nothing in your library matches “${query.trim()}”.",
                )
            }
        }
    }
}

@Composable
private fun SearchMessage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
