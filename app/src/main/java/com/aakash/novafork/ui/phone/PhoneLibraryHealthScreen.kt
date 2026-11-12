package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.GreyCaption
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.HealthGroupUi
import com.aakash.novafork.ui.model.HealthItemUi
import com.aakash.novafork.ui.model.HealthKind
import com.aakash.novafork.ui.model.LibraryHealthUi
import com.aakash.novafork.ui.model.VideoCardUi

/**
 * Preferences › Library Health: what may need a look, in groups with counts. A movie or show
 * without artwork opens "Change Artwork", a movie without details opens "Identify", an
 * unrecognised episode opens its page. View only: nothing is changed until you pick something.
 */
@Composable
internal fun PhoneLibraryHealthScreen(
    health: LibraryHealthUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val dims = LocalNovaDimens.current
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "Library Health", onBack = actions::back)
        if (health.groups.isEmpty()) {
            CenteredScrollable {
                PhoneEmptyLine("No problems found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
            ) {
                health.groups.forEach { group ->
                    item(key = "header:${group.kind.name}") { HealthHeader(group, Modifier.padding(horizontal = dims.gutter, vertical = 12.dp)) }
                    items(group.items, key = { "${group.kind.name}:${it.key}" }) { item ->
                        val card = remember(item) {
                            VideoCardUi(id = item.videoId, title = item.title, subtitle = item.subtitle, artwork = item.artwork)
                        }
                        PhoneVideoRow(card = card, onClick = { openHealthItem(group.kind, item, actions) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthHeader(group: HealthGroupUi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("${group.kind.title} · ${group.items.size}", style = MaterialTheme.typography.titleLarge)
        GreyCaption(group.kind.footer)
    }
}

private fun openHealthItem(kind: HealthKind, item: HealthItemUi, actions: AppActions) {
    when (kind) {
        HealthKind.NO_ARTWORK -> actions.changeArtwork(item.videoId)
        HealthKind.UNIDENTIFIED -> actions.identify(item.videoId)
        HealthKind.UNPARSED_EPISODES -> actions.openVideo(item.videoId)
    }
}
