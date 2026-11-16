package com.aakash.novafork.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.common.IosSpinner
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.ChannelsUi

/** An IPTV list's channels, under their group headings; a tap plays the channel. */
@Composable
internal fun PhoneChannelsScreen(channels: ChannelsUi?, actions: AppActions, modifier: Modifier = Modifier) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "Channels", onBack = actions::back)
        when {
            channels == null || channels.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { IosSpinner() }
            channels.error != null -> Text(
                channels.error,
                style = MaterialTheme.typography.bodyMedium,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(horizontal = dims.gutter, vertical = 24.dp),
            )
            else -> {
                val grouped = channels.channels.groupBy { it.group ?: "" }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
                ) {
                    grouped.forEach { (group, list) ->
                        if (group.isNotBlank()) {
                            item(key = "g:$group") {
                                Text(
                                    group.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IosColors.SecondaryLabel,
                                    modifier = Modifier.padding(start = dims.gutter, end = dims.gutter, top = 18.dp, bottom = 6.dp),
                                )
                            }
                        }
                        items(list, key = { "c:$group:${it.url}:${it.name}" }) { channel ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { actions.playRemote(channel.url, channel.name) }
                                    .padding(horizontal = dims.gutter, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IosColors.GroupedCard),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (channel.logo != null) {
                                        AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                                    }
                                }
                                Text(
                                    channel.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
