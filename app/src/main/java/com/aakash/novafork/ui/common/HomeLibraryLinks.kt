package com.aakash.novafork.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.Route

/**
 * The way into the whole library, at the end of Home (phone and tablet): Recently added (every
 * video, newest first), Watchlist, Favorites, All videos (A–Z), Movies, TV shows, then folders,
 * sources and playlists.
 */
@Composable
fun HomeLibraryLinks(actions: AppActions, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxWidth().padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = "Library",
            modifier = Modifier.padding(start = dims.gutter, end = 4.dp, bottom = 4.dp),
        )
        LibraryLink(R.drawable.ic_history, "Recently added") {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.RECENT)
        }
        LibraryLink(R.drawable.ic_add, "Watchlist") { actions.openLibrary(LibraryFilter.WATCHLIST) }
        LibraryLink(R.drawable.ic_star, "Favorites") { actions.openLibrary(LibraryFilter.FAVORITES) }
        LibraryLink(R.drawable.ic_video_library, "All videos") {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.TITLE)
        }
        LibraryLink(R.drawable.ic_movie, "Movies") { actions.openLibrary(LibraryFilter.MOVIES) }
        LibraryLink(R.drawable.ic_tv, "TV shows") { actions.openLibrary(LibraryFilter.SHOWS) }
        LibraryLink(R.drawable.ic_folder, "Folders, sources and playlists") { actions.navigate(Route.Browse) }
    }
}

@Composable
private fun LibraryLink(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val dims = LocalNovaDimens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dims.gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NovaIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )
        NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
