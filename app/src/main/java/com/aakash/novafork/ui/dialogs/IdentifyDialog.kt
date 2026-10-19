package com.aakash.novafork.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosSegmentedControl
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.NovaAlertDialog
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.IdentifyResultUi

private val ResultPosterShape = RoundedCornerShape(6.dp)

/** Search TMDB by hand and pick the right movie or show for a video. */
@Composable
internal fun IdentifyDialog(dialog: DialogUi.Identify, actions: AppActions) {
    var query by remember(dialog.videoId) { mutableStateOf(dialog.query) }
    var tv by remember(dialog.videoId) { mutableStateOf(dialog.tv) }
    val focusManager = LocalFocusManager.current

    fun search() {
        focusManager.clearFocus()
        actions.identifySearch(query, tv)
    }

    NovaAlertDialog(
        form = true,
        onDismissRequest = actions::dismissDialog,
        title = { Text("Identify") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (tv) "Show name" else "Movie title") },
                    leadingIcon = { NovaIcon(R.drawable.ic_search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                val kinds = listOf(false to "Movie", true to "TV show")
                IosSegmentedControl(
                    options = kinds.map { it.second },
                    selectedIndex = kinds.indexOfFirst { it.first == tv },
                    onSelect = { index ->
                        val isTv = kinds[index].first
                        if (tv != isTv) {
                            tv = isTv
                            search()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                IdentifyResults(dialog = dialog, onPick = { actions.identifyPick(dialog.videoId, it) })
            }
        },
        confirmButton = {
            TextButton(onClick = actions::dismissDialog) { Text("Cancel") }
        },
    )
}

@Composable
private fun IdentifyResults(dialog: DialogUi.Identify, onPick: (IdentifyResultUi) -> Unit) {
    val error = dialog.error
    when {
        dialog.loading -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            IosSpinner()
        }
        error != null -> Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        dialog.results.isNotEmpty() -> LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            items(dialog.results, key = { "${it.isTv}:${it.tmdbId}" }) { result ->
                IdentifyResultRow(result = result, onClick = { onPick(result) })
            }
        }
    }
}

@Composable
private fun IdentifyResultRow(result: IdentifyResultUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PosterArt(
            artwork = Artwork(posterUrl = result.posterUrl, seed = result.title),
            title = result.title,
            modifier = Modifier
                .size(width = 52.dp, height = 78.dp)
                .clip(ResultPosterShape),
            showTitleOnPlaceholder = false,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = result.year?.let { "${result.title} ($it)" } ?: result.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val overview = result.overview
            if (!overview.isNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
