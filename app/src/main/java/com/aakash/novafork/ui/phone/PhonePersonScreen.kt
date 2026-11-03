package com.aakash.novafork.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aakash.novafork.ui.common.EndOfListGap
import com.aakash.novafork.ui.common.LocalBottomBarSpace
import com.aakash.novafork.ui.common.SwipeToDelete
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.HiddenItemUi
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors

/**
 * An actor's or director's page (a name tapped under Starring, Director or Created by): the
 * library's movies and shows they are credited in, newest first, in the library's own poster grid.
 */
@Composable
internal fun PhonePersonScreen(
    name: String,
    items: List<LibraryItemUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = name, onBack = actions::back)
        if (items.isEmpty()) {
            CenteredScrollable {
                PhoneEmptyLine("Nothing in your library")
            }
        } else {
            PhoneLibraryGrid(items = items, actions = actions)
        }
    }
}

/**
 * Preferences › Hidden Videos: what was hidden from the library (a show's hidden episodes as one
 * row). "Unhide", on the row or by swiping it, brings it back; nothing was ever deleted.
 */
@Composable
internal fun PhoneHiddenVideosScreen(
    items: List<HiddenItemUi>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(title = "Hidden Videos", onBack = actions::back)
        if (items.isEmpty()) {
            CenteredScrollable {
                PhoneEmptyLine("No hidden videos")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = EndOfListGap + LocalBottomBarSpace.current),
            ) {
                items(items, key = { it.key }) { item ->
                    val card = remember(item) { VideoCardUi(id = 0L, title = item.title, subtitle = item.subtitle, artwork = item.artwork) }
                    SwipeToDelete(onDelete = { actions.unhideVideos(item.videoIds) }, label = "Unhide") {
                        PhoneVideoRow(
                            card = card,
                            onClick = { if (item.videoIds.size == 1) actions.openVideo(item.videoIds.first()) },
                            trailing = {
                                Text(
                                    text = "Unhide",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NovaColors.IosBlue,
                                    modifier = Modifier.clickable { actions.unhideVideos(item.videoIds) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
