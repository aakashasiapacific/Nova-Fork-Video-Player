package com.aakash.novafork.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.NovaIcon
import com.aakash.novafork.ui.common.VideoMenuBox
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.BrowseDirUi
import com.aakash.novafork.ui.model.FolderUi
import com.aakash.novafork.ui.model.Route

@Composable
fun PhoneBrowseDirScreen(
    dir: BrowseDirUi?,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PhoneTopBar(
            title = dir?.title.orEmpty(),
            onBack = actions::back,
            actions = {
                if (dir != null) {
                    IconButton(onClick = { actions.rescanSource(dir.sourceId) }, enabled = !dir.isLoading) {
                        NovaIcon(R.drawable.ic_refresh, contentDescription = "Rescan")
                    }
                }
            },
        )
        if (dir == null) {
            LoadingBox()
        } else {
            BrowseDirContent(dir = dir, actions = actions)
        }
    }
}

@Composable
private fun BrowseDirContent(dir: BrowseDirUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val isEmpty = dir.folders.isEmpty() && dir.videos.isEmpty()
    val error = dir.error

    if (dir.crumbs.size > 1) {
        Breadcrumbs(dir = dir, actions = actions)
    }
    if (dir.isLoading && !isEmpty) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    when {
        isEmpty && dir.isLoading -> LoadingBox()
        isEmpty && !error.isNullOrBlank() -> CenteredScrollable {
            EmptyState(
                icon = R.drawable.ic_error,
                title = "Couldn't open this folder",
                body = error,
                actionLabel = "Go back",
                onAction = actions::back,
            )
        }
        isEmpty -> CenteredScrollable {
            EmptyState(
                icon = R.drawable.ic_folder_open,
                title = "No videos here",
                body = "This folder has no videos or subfolders.",
            )
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = dims.sectionSpacing),
        ) {
            if (!error.isNullOrBlank()) {
                item(key = "error") {
                    ErrorNote(
                        message = error,
                        modifier = Modifier.padding(horizontal = dims.gutter, vertical = 8.dp),
                    )
                }
            }
            if (dir.videos.isNotEmpty()) {
                item(key = "playAll") {
                    // Plays the videos directly in this folder, not those in its subfolders.
                    PhonePlayAllRow(
                        onPlayAll = { actions.playFolder(dir.sourceId, dir.path) },
                        onShuffle = { actions.playFolder(dir.sourceId, dir.path, shuffle = true) },
                        modifier = Modifier.padding(horizontal = dims.gutter, vertical = 8.dp),
                    )
                }
            }
            items(dir.folders, key = { "folder:${it.path}" }) { folder ->
                FolderRow(folder = folder, onClick = { actions.openFolder(folder) })
            }
            items(dir.videos, key = { "video:${it.id}" }) { video ->
                VideoMenuBox(videoId = video.id, title = video.displayTitle(), actions = actions) { openMenu ->
                    PhoneVideoRow(
                        card = video,
                        onClick = { actions.play(video.id) },
                        onLongClick = openMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(dir: BrowseDirUi, actions: AppActions) {
    val dims = LocalNovaDimens.current
    val crumbs = dir.crumbs
    val listState = rememberLazyListState()
    // Deep paths overflow the row; keep the current folder in view.
    LaunchedEffect(crumbs.size) {
        if (crumbs.isNotEmpty()) listState.scrollToItem(crumbs.lastIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dims.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(crumbs) { index, crumb ->
            val isCurrent = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    NovaIcon(
                        R.drawable.ic_chevron_right,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(18.dp),
                    )
                }
                AssistChip(
                    onClick = { if (!isCurrent) actions.navigate(Route.BrowseDir(dir.sourceId, crumb.path)) },
                    label = { Text(crumb.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = if (index == 0) {
                        { NovaIcon(dir.sourceType.iconRes(), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    colors = if (isCurrent) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderUi, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                NovaIcon(R.drawable.ic_folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        },
        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(countLabel(folder.videoCount, "video")) },
        trailingContent = {
            NovaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ErrorNote(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalNovaDimens.current.cardRadius),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NovaIcon(R.drawable.ic_error, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
