package com.aakash.novafork.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.PlayerActions
import com.aakash.novafork.ui.model.SubtitleResultUi
import com.aakash.novafork.ui.model.SubtitleSearchUi
import java.text.NumberFormat

internal const val SUBTITLE_SEARCH_TITLE = "Download subtitles"

/** "Download subtitles…" under the subtitle tracks (phone sheet and tablet panel). */
@Composable
internal fun DownloadSubtitlesRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cloud),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(text = "$SUBTITLE_SEARCH_TITLE…", style = MaterialTheme.typography.bodyLarge)
    }
}

/** Phone: the subtitle download list in a modal bottom sheet; closing it closes the search. */
@Composable
internal fun SubtitleSearchSheet(search: SubtitleSearchUi, actions: PlayerActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = actions::dismissSubtitleSearch,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = SUBTITLE_SEARCH_TITLE,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
        )
        SubtitleSearchContent(search = search, actions = actions)
    }
}

/**
 * Where the subtitles come from, then a spinner, an error with "Try again", "nothing found", or
 * the results; a tap on one downloads it. Shared by the phone sheet and the tablet side panel.
 */
@Composable
internal fun SubtitleSearchContent(search: SubtitleSearchUi, actions: PlayerActions, modifier: Modifier = Modifier) {
    val dim = LocalContentColor.current.copy(alpha = 0.7f)
    Column(modifier.fillMaxWidth()) {
        Text(
            text = if (search.languages.isBlank()) "From OpenSubtitles.com" else "From OpenSubtitles.com in ${search.languages}",
            style = MaterialTheme.typography.bodySmall,
            color = dim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val error = search.error
        if (error != null) {
            SearchError(message = error, onRetry = actions::searchSubtitles, retryEnabled = !search.loading && search.downloadingId == null)
        }
        when {
            search.loading -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(14.dp))
                Text(text = "Searching…", style = MaterialTheme.typography.bodyMedium, color = dim)
            }
            search.results.isEmpty() && error == null -> Text(
                text = "No subtitles found. Matching works best for videos in your library with posters and details.",
                style = MaterialTheme.typography.bodyMedium,
                color = dim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            search.results.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(search.results, key = { it.id }) { item ->
                    SubtitleResultRow(
                        item = item,
                        downloading = search.downloadingId == item.id,
                        enabled = search.downloadingId == null,
                        onClick = { actions.downloadSubtitle(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit, retryEnabled: Boolean) {
    val errorColor = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = errorColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, enabled = retryEnabled) { Text("Try again") }
    }
}

@Composable
private fun SubtitleResultRow(item: SubtitleResultUi, downloading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val dim = LocalContentColor.current.copy(alpha = 0.7f)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Download", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.release.ifBlank { item.language },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ResultBadge(text = item.language, color = LocalContentColor.current)
                if (item.matchesFile) ResultBadge(text = "Matches this file", color = accent, icon = R.drawable.ic_check_circle)
                if (item.hearingImpaired) ResultBadge(text = "Hearing impaired", color = LocalContentColor.current)
                if (item.machineTranslated) ResultBadge(text = "Machine translated", color = LocalContentColor.current)
                Text(
                    text = downloadsLabel(item.downloadCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = dim,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            downloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            item.downloaded -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Downloaded",
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
            else -> Icon(
                painter = painterResource(R.drawable.ic_cloud),
                contentDescription = null,
                tint = if (enabled) LocalContentColor.current else dim,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ResultBadge(text: String, color: Color, @DrawableRes icon: Int? = null) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** "1 download", "18,946 downloads" (digits grouped the device's way). */
internal fun downloadsLabel(count: Int): String =
    if (count == 1) "1 download" else "${NumberFormat.getIntegerInstance().format(count.coerceAtLeast(0))} downloads"
