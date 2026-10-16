package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.NovaIcon
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

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = dims.gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions::back) {
                NovaIcon(R.drawable.ic_arrow_back, contentDescription = "Back")
            }
            OutlinedTextField(
                value = field,
                onValueChange = { value ->
                    val changed = value.text != field.text
                    field = value
                    if (changed) actions.search(value.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search videos, music and cast") },
                leadingIcon = { NovaIcon(R.drawable.ic_search, contentDescription = null) },
                trailingIcon = if (field.text.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                field = TextFieldValue("")
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
                shape = RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        }

        val query = search.query.trim()
        when {
            field.text.isBlank() -> CenteredScrollable(Modifier.imePadding()) {
                EmptyState(
                    icon = R.drawable.ic_search,
                    title = "Search your library",
                    body = "Find movies, shows, videos, songs, albums and artists.",
                )
            }
            search.isEmpty && query.isNotEmpty() -> CenteredScrollable(Modifier.imePadding()) {
                EmptyState(
                    icon = R.drawable.ic_search,
                    title = "No results",
                    body = "Nothing in your library matches “$query”.",
                )
            }
            else -> PhoneLibraryGrid(
                items = search.results,
                actions = actions,
                modifier = Modifier.imePadding(),
                footer = { musicSearchResults(search) { actions.playMusic(it.songIds, it.startIndex) } },
            )
        }
    }
}
