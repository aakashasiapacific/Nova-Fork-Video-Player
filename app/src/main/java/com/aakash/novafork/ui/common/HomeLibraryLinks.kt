package com.aakash.novafork.ui.common

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
 * The way into the whole library, at the end of Home (phone and tablet): Favorites, All videos
 * (A–Z), then network shares, streams and playlists. The Watchlist has its own row above
 * (owner: listed here as well it showed twice). Laid out like the Library list
 * in Apple's TV and Music apps: blue icons, grey chevrons and hairlines inset to the labels
 * between the rows, with how many each holds in grey before the chevron ([counts]).
 */
@Composable
fun HomeLibraryLinks(actions: AppActions, modifier: Modifier = Modifier, counts: LibraryCounts = LibraryCounts()) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxWidth().padding(top = dims.sectionSpacing)) {
        SectionHeader(
            title = "Library",
            modifier = Modifier.padding(start = dims.gutter, end = 4.dp, bottom = 4.dp),
        )
        LibraryLink(R.drawable.ic_star, "Favorites", count = counts.favorites) { actions.openLibrary(LibraryFilter.FAVORITES) }
        LibraryLink(R.drawable.ic_video_library, "All Videos", count = counts.all) {
            actions.openLibrary(LibraryFilter.ALL)
            actions.setLibrarySort(LibrarySort.TITLE)
        }
        LibraryLink(R.drawable.ic_language, "Streams and playlists", last = true) { actions.navigate(Route.Browse) }
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
