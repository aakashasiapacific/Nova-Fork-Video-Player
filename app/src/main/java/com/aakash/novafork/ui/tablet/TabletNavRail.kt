package com.aakash.novafork.ui.tablet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.model.TopDestination

/**
 * The four tabs (Video, Audio, Settings, Search) centred vertically, on black. No app mark: the
 * Apple TV look shows no title. (The iPad-style floating top bar comes in a later round.)
 */
@Composable
internal fun TabletNavRail(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Also keeps the rail clear of a display cutout on the start edge in landscape.
        windowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
    ) {
        Spacer(Modifier.weight(1f))
        TopDestination.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selected,
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
    TopDestination.VIDEO -> R.drawable.ic_tv
    TopDestination.AUDIO -> R.drawable.ic_audiotrack
    TopDestination.SETTINGS -> R.drawable.ic_settings
    TopDestination.SEARCH -> R.drawable.ic_search
}
