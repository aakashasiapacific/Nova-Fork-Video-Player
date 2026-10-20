package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aakash.novafork.R
import com.aakash.novafork.ui.model.MusicResultUi
import com.aakash.novafork.ui.model.SearchUi

/**
 * The music part of search results, for a grid of poster cards: Songs, Albums and Artists, each a
 * full-width header and full-width rows, after the videos. Tapping a row plays it ([onPlay] gets
 * the result's songs and where to start).
 */
fun LazyGridScope.musicSearchResults(search: SearchUi, onPlay: (MusicResultUi) -> Unit) {
    section("Songs", search.songs, onPlay)
    section("Albums", search.albums, onPlay)
    section("Artists", search.artists, onPlay)
}

private fun LazyGridScope.section(title: String, results: List<MusicResultUi>, onPlay: (MusicResultUi) -> Unit) {
    if (results.isEmpty()) return
    item(key = "music-header:$title", span = { GridItemSpan(maxLineSpan) }, contentType = "music-header") {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    items(results, key = { it.key }, span = { GridItemSpan(maxLineSpan) }, contentType = { "music" }) { result ->
        MusicResultRow(result, onClick = { onPlay(result) })
    }
}

@Composable
private fun MusicResultRow(result: MusicResultUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (result.round) CircleShape else RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(R.drawable.ic_audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            result.artUri?.let { uri ->
                AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                result.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
