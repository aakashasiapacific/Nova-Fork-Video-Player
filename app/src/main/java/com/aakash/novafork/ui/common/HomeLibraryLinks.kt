package com.aakash.novafork.ui.common

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.aakash.novafork.ui.model.LibraryCounts
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.theme.NovaColors

/**
 * The way into the whole library, at the end of Home (phone and tablet): Recently added (every
 * video, newest first), Watchlist, Favorites, All videos (A–Z), Movies, TV shows, then folders,
 * sources and playlists, and Recently Deleted. Laid out like the Library list in Apple's TV and Music apps: blue icons,
 * grey chevrons and hairlines inset to the labels between the rows, with how many each holds
 * in grey before the chevron ([counts]). Recently Deleted shows only while the bin holds some.
 */
@Composable
fun HomeLibraryLinks(actions: AppActions, modifier: Modifier = Modifier, counts: LibraryCounts = LibraryCounts()) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxWidth().padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = "Library",
            modifier = Modifier.padding(start = dims.gutter, end = 4.dp, bottom = 4.dp),
        )
        LibraryLink(R.drawable.ic_history, "Recently Added", count = counts.recent) {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.RECENT)
        }
        LibraryLink(R.drawable.ic_bookmark, "Watchlist", count = counts.watchlist) { actions.openLibrary(LibraryFilter.WATCHLIST) }
        LibraryLink(R.drawable.ic_star, "Favorites", count = counts.favorites) { actions.openLibrary(LibraryFilter.FAVORITES) }
        LibraryLink(R.drawable.ic_video_library, "All Videos", count = counts.all) {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.TITLE)
        }
        LibraryLink(R.drawable.ic_movie, "Movies", count = counts.movies) { actions.openLibrary(LibraryFilter.MOVIES) }
        LibraryLink(R.drawable.ic_tv, "TV Shows", count = counts.shows) { actions.openLibrary(LibraryFilter.SHOWS) }
        // Android's bin (Android 11 and later): deleted videos wait here 30 days, as in Photos. Not
        // shown while it is empty (owner: no empty rows).
        val hasBin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && counts.bin != 0
        LibraryLink(R.drawable.ic_folder, "Folders, sources and playlists", last = !hasBin) { actions.navigate(Route.Browse) }
        if (hasBin) LibraryLink(R.drawable.ic_delete, "Recently Deleted", last = true, count = counts.bin) { actions.showRecentlyDeleted() }
    }
}

@Composable
private fun LibraryLink(@DrawableRes icon: Int, label: String, last: Boolean = false, count: Int? = null, onClick: () -> Unit) {
    val dims = LocalNovaDimens.current
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = dims.gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovaIcon(icon, contentDescription = null, tint = NovaColors.IosBlue)
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = LabelGap)
                    .weight(1f),
            )
            if (count != null) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = IosColors.SecondaryLabel,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 6.dp),
                )
            }
            NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = IosColors.TertiaryLabel)
        }
        if (!last) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = dims.gutter + IconSize + LabelGap)
                    .fillMaxWidth()
                    .height(Hairline)
                    .background(IosColors.Separator),
            )
        }
    }
}

private val IconSize = 24.dp
private val LabelGap = 16.dp
