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
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.theme.NovaColors

/**
 * The way into the whole library, at the end of Home (phone and tablet): Recently added (every
 * video, newest first), Watchlist, Favorites, All videos (A–Z), Movies, TV shows, then folders,
 * sources and playlists, and Recently Deleted. Laid out like the Library list in Apple's TV and Music apps: blue icons,
 * grey chevrons and hairlines inset to the labels between the rows.
 */
@Composable
fun HomeLibraryLinks(actions: AppActions, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxWidth().padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = "Library",
            modifier = Modifier.padding(start = dims.gutter, end = 4.dp, bottom = 4.dp),
        )
        LibraryLink(R.drawable.ic_history, "Recently Added") {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.RECENT)
        }
        LibraryLink(R.drawable.ic_add, "Watchlist") { actions.openLibrary(LibraryFilter.WATCHLIST) }
        LibraryLink(R.drawable.ic_star, "Favorites") { actions.openLibrary(LibraryFilter.FAVORITES) }
        LibraryLink(R.drawable.ic_video_library, "All Videos") {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.TITLE)
        }
        LibraryLink(R.drawable.ic_movie, "Movies") { actions.openLibrary(LibraryFilter.MOVIES) }
        LibraryLink(R.drawable.ic_tv, "TV Shows") { actions.openLibrary(LibraryFilter.SHOWS) }
        val hasBin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        LibraryLink(R.drawable.ic_folder, "Folders, sources and playlists", last = !hasBin) { actions.navigate(Route.Browse) }
        // Android's bin (Android 11 and later): deleted videos wait here 30 days, as in Photos.
        if (hasBin) LibraryLink(R.drawable.ic_delete, "Recently Deleted", last = true) { actions.showRecentlyDeleted() }
    }
}

@Composable
private fun LibraryLink(@DrawableRes icon: Int, label: String, last: Boolean = false, onClick: () -> Unit) {
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
