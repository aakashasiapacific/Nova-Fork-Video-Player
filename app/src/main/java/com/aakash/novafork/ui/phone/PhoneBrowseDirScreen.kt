package com.aakash.novafork.ui.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.ActivityIndicator
import com.aakash.novafork.ui.common.BackButton
import com.aakash.novafork.ui.common.EmptyState
import com.aakash.novafork.ui.common.GlassMenuButton
import com.aakash.novafork.ui.common.GlassMenuItem
import com.aakash.novafork.ui.common.InsetGroup
import com.aakash.novafork.ui.common.InsetGroupDefaults
import com.aakash.novafork.ui.common.LargeTitleHeader
import com.aakash.novafork.ui.common.ListRow
import com.aakash.novafork.ui.common.ListRowIcon
import com.aakash.novafork.ui.common.ListSectionFooter
import com.aakash.novafork.ui.common.glassTabBarClearance
import com.aakash.novafork.ui.common.rememberTitleCollapsed
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.BrowseDirUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors

/** Where the hairline of a row with a 64dp 16:9 thumbnail starts. */
private val ThumbnailDividerInset = InsetGroupDefaults.RowPadding + 64.dp + InsetGroupDefaults.IconGap

/**
 * One folder of a source: Large Title = folder name, an inset group of subfolders (folder icon,
 * video count, "›") and one of videos (16:9 thumbnail, title, caption). Tap plays a video; long
 * press opens its context menu.
 */
@Composable
internal fun PhoneBrowseDirScreen(
    dir: BrowseDirUi,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val collapsed = rememberTitleCollapsed(list)
    val isEmpty = dir.folders.isEmpty() && dir.videos.isEmpty()
    val error = dir.error
    PhoneLargeTitlePage(
        modifier = modifier,
        title = dir.title,
        collapsed = collapsed,
        leading = { BackButton(onClick = actions::back) },
        trailing = {
            GlassMenuButton(
                items = listOf(
                    GlassMenuItem("Rescan", icon = R.drawable.ic_refresh, enabled = !dir.isLoading) {
                        actions.rescanSource(dir.sourceId)
                    },
                ),
            )
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = glassTabBarClearance()),
        ) {
            item(key = "title") { LargeTitleHeader(dir.title, collapsed = collapsed) }
            when {
                isEmpty && dir.isLoading -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ActivityIndicator(size = 36.dp)
                    }
                }
                isEmpty && !error.isNullOrBlank() -> item(key = "error") {
                    EmptyState(
                        icon = R.drawable.ic_error,
                        title = "Can't Open Folder",
                        body = error,
                        actionLabel = "Try Again",
                        onAction = { actions.rescanSource(dir.sourceId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
                isEmpty -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_folder_open,
                        title = "No Videos",
                        body = "This folder has no videos or subfolders.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
                else -> {
                    if (dir.isLoading) {
                        item(key = "refreshing") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ActivityIndicator()
                            }
                        }
                    }
                    if (dir.folders.isNotEmpty()) {
                        item(key = "folders") {
                            InsetGroup {
                                dir.folders.forEachIndexed { index, folder ->
                                    ListRow(
                                        title = folder.name,
                                        value = folder.videoCount.toString(),
                                        leading = { ListRowIcon(R.drawable.ic_folder, NovaColors.tint) },
                                        chevron = true,
                                        showDivider = index < dir.folders.lastIndex,
                                        onClick = { actions.openFolder(folder) },
                                    )
                                }
                            }
                        }
                    }
                    if (dir.folders.isNotEmpty() && dir.videos.isNotEmpty()) {
                        item(key = "gap") { Spacer(Modifier.height(InsetGroupDefaults.SectionSpacing)) }
                    }
                    if (dir.videos.isNotEmpty()) {
                        item(key = "videos") {
                            InsetGroup(header = countLabel(dir.videos.size, "Video")) {
                                dir.videos.forEachIndexed { index, video ->
                                    FolderVideoRow(video, actions, showDivider = index < dir.videos.lastIndex)
                                }
                            }
                        }
                    }
                    if (!error.isNullOrBlank()) {
                        item(key = "errorNote") {
                            ListSectionFooter(error, Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderVideoRow(video: VideoCardUi, actions: AppActions, showDivider: Boolean) {
    val menu = rememberPhoneMenuState()
    Box(Modifier.phoneContextTarget(menu)) {
        ListRow(
            title = video.cardTitle(),
            subtitle = video.cardCaption(),
            leading = { PhoneRowThumbnail(video.artwork, video.title, video.kind) },
            showDivider = showDivider,
            dividerInset = ThumbnailDividerInset,
            minHeight = 60.dp,
            onLongClick = { menu.open = true },
            onClick = { actions.play(video.id) },
        )
        PhoneContextMenu(menu, videoMenuItems(video, actions))
    }
}
