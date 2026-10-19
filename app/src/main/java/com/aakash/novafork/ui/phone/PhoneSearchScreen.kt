package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.glass
import com.aakash.novafork.ui.common.musicSearchResults
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.SearchUi

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
    val keyboard = LocalSoftwareKeyboardController.current
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
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = dims.gutter, end = dims.gutter, top = 16.dp, bottom = 8.dp),
            )
            val query = search.query.trim()
            // Lists end above the field.
            CompositionLocalProvider(LocalBottomBarSpace provides listSpace) {
                when {
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
            onSearch = { keyboard?.hide() },
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
            IconButton(onClick = onClear) {
                NovaIcon(R.drawable.ic_close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

private val SearchFieldHeight = 52.dp
