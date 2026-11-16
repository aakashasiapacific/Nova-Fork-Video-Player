package com.aakash.novafork.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.PosterArt
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.UpcomingUi
import com.aakash.novafork.ui.theme.NovaColors

/**
 * Coming Up: the shows in the library with a new episode on the way, soonest first, each with
 * the day it airs (from TMDB). A tap opens the show.
 */
@Composable
internal fun PhoneUpcomingScreen(items: List<UpcomingUi>, actions: AppActions, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "Coming Up", onBack = actions::back)
        if (items.isEmpty()) {
            Text(
                "No new episodes announced for your shows yet. Dates appear once their pages have been opened, " +
                    "or after the next scan.",
                style = MaterialTheme.typography.bodyMedium,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(horizontal = dims.gutter, vertical = 24.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 8.dp, bottom = EndOfListGap + LocalBottomBarSpace.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.showKey }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { actions.openShow(item.showKey) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosterArt(
                        artwork = item.artwork,
                        title = item.title,
                        modifier = Modifier
                            .width(64.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                        showTitleOnPlaceholder = false,
                    )
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(item.dayLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = NovaColors.IosBlue)
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = IosColors.SecondaryLabel)
                    }
                }
            }
        }
    }
}
