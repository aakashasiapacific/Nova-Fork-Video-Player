package com.aakash.novafork.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.Hairline
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosIconButton
import com.aakash.novafork.ui.common.IosTextButton
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.LocalSearchQuery
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.OnTabReselect
import com.aakash.novafork.ui.common.SwipeToDelete
import com.aakash.novafork.ui.common.glass
import com.aakash.novafork.ui.common.musicSearchResults
import com.aakash.novafork.ui.common.stretchWithPull
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.SearchUi
import kotlin.math.abs

@Composable
fun PhoneSearchScreen(
    search: SearchUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    // The field owns its text and cursor while the screen is shown; driving it from the
    // asynchronously updated view-model state would drop keystrokes. The view model keeps the
    // query across rotation and navigation, so it seeds the field on entry.
    var field by remember {
        mutableStateOf(TextFieldValue(search.query, TextRange(search.query.length)))
    }
    val focusRequester = remember { FocusRequester() }
    // Leaving Search with results on screen keeps the query in the recent searches, as iOS does
    // once a search has been used.
    val latest by rememberUpdatedState(field.text to !search.isEmpty)
    DisposableEffect(Unit) {
        onDispose {
            val (text, hadResults) = latest
            if (hadResults) actions.rememberSearch(text)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Tapping Search again goes to the field, keyboard up, as on iOS.
    OnTabReselect { focusRequester.requestFocus() }
    LaunchedEffect(focusRequester) {
        // Coming back to existing results should not pop the keyboard over them.
        if (field.text.isEmpty()) focusRequester.requestFocus()
    }

    // iOS 26's search: a big title on top, the field at the bottom in thumb reach, riding just
    // above the keyboard while typing and above the tab bar otherwise.
    val barSpace = LocalBottomBarSpace.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val fieldBottom = maxOf(imeBottom, barSpace) + 8.dp
    val listSpace = fieldBottom + SearchFieldHeight + 8.dp
    // Scrolling the results puts the keyboard away, so more of them show (iOS).
    val currentKeyboard by rememberUpdatedState(keyboard)
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 1f) currentKeyboard?.hide()
                return Offset.Zero
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(hideOnScroll)) {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = dims.gutter, end = dims.gutter, top = 16.dp, bottom = 8.dp)
                    .stretchWithPull(),
            )
            val query = search.query.trim()
            // Lists end above the field; results show what was typed in bold.
            CompositionLocalProvider(LocalBottomBarSpace provides listSpace, LocalSearchQuery provides field.text) {
                when {
                    field.text.isBlank() && search.recent.isNotEmpty() -> RecentSearches(
                        recent = search.recent,
                        onPick = { picked ->
                            field = TextFieldValue(picked, TextRange(picked.length))
                            actions.search(picked)
                            actions.rememberSearch(picked)
                            keyboard?.hide()
                        },
                        onClear = actions::clearRecentSearches,
                        bottomSpace = listSpace,
                        onForget = actions::forgetSearch,
                    )
                    field.text.isBlank() -> CenteredScrollable(Modifier.padding(bottom = listSpace)) {
                        EmptyState(
                            icon = R.drawable.ic_search,
                            title = "Search your library",
                            body = "Find movies, shows, videos, songs, albums and artists.",
                        )
                    }
                    search.isEmpty && query.isNotEmpty() -> CenteredScrollable(Modifier.padding(bottom = listSpace)) {
                        EmptyState(
                            icon = R.drawable.ic_search,
                            title = "No Results",
                            body = "Nothing in your library matches “$query”.",
                        )
                    }
                    else -> PhoneLibraryGrid(
                        items = search.results,
                        actions = actions,
                        footer = { musicSearchResults(search) { actions.playMusic(it.songIds, it.startIndex) } },
                    )
                }
            }
        }
        // Putting the keyboard away (its arrow, or back) ends typing, so the cursor goes too.
        val keyboardUp = imeBottom > 0.dp
        var keyboardWasUp by remember { mutableStateOf(false) }
        LaunchedEffect(keyboardUp) {
            if (keyboardUp) {
                keyboardWasUp = true
            } else if (keyboardWasUp) {
                keyboardWasUp = false
                focusManager.clearFocus()
            }
        }
        // Always full width (owner: no Cancel beside it); its ✕ clears what was typed.
        SearchField(
            value = field,
            onValueChange = { value ->
                val changed = value.text != field.text
                field = value
                if (changed) actions.search(value.text)
            },
            onClear = {
                field = TextFieldValue("")
                actions.search("")
                focusRequester.requestFocus()
            },
            onSearch = {
                actions.rememberSearch(field.text)
                keyboard?.hide()
            },
            focusRequester = focusRequester,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = fieldBottom),
        )
    }
}

/** A slim glass capsule: magnifier, one line of text (or a short hint), clear. */
@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .glass(RoundedCornerShape(percent = 50))
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NovaIcon(R.drawable.ic_search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.text.isEmpty()) {
                // One line always (owner: it broke in two once Cancel narrowed the field).
                Text(
                    "Movies, music and more",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (value.text.isNotEmpty()) {
            IosIconButton(onClick = onClear) {
                NovaIcon(R.drawable.ic_close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

private val SearchFieldHeight = 52.dp

/**
 * iOS's recent searches: a header with Clear, then each search, tap to run it again or swipe it
 * left to take just that one away.
 */
@Composable
internal fun RecentSearches(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    bottomSpace: Dp,
    onForget: (String) -> Unit = {},
) {
    val dims = LocalNovaDimens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 8.dp, bottom = bottomSpace),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Searches", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IosTextButton(onClick = onClear) { Text("Clear") }
            }
        }
        items(recent, key = { it }) { query ->
            SwipeToDelete(onDelete = { onForget(query) }, label = "Remove", modifier = Modifier.animateItem()) {
                Column(Modifier.clickable { onPick(query) }) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaIcon(R.drawable.ic_search, contentDescription = null, tint = IosColors.SecondaryLabel, modifier = Modifier.size(18.dp))
                        Text(
                            query,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(Hairline).background(IosColors.Separator))
                }
            }
        }
    }
}
