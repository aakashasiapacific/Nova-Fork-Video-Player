package com.aakash.novafork.ui.tablet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.TopDestination

/** Brand mark and search on top, the four tabs centred vertically. */
@Composable
internal fun TabletNavRail(
    selected: TopDestination,
    searchSelected: Boolean,
    onSelect: (TopDestination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Also keeps the rail clear of a display cutout on the start edge in landscape.
        windowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        header = {
            Spacer(Modifier.height(4.dp))
            BrandMark()
            Spacer(Modifier.height(12.dp))
            IconButton(onClick = onSearch) {
                NovaIcon(
                    R.drawable.ic_search,
                    contentDescription = "Search",
                    tint = if (searchSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        TopDestination.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selected && !searchSelected,
                onClick = { onSelect(tab) },
                icon = { NovaIcon(tab.iconRes(), contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@DrawableRes
private fun TopDestination.iconRes(): Int = when (this) {
    TopDestination.HOME -> R.drawable.ic_home
    TopDestination.LIBRARY -> R.drawable.ic_video_library
    TopDestination.BROWSE -> R.drawable.ic_folder
    TopDestination.AUDIO -> R.drawable.ic_audiotrack
    TopDestination.SETTINGS -> R.drawable.ic_settings
}
