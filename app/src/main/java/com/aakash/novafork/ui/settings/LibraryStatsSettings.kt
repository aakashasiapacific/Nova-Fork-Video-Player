package com.aakash.novafork.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.model.AppActions
import com.aakash.novafork.ui.model.FolderSpace
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.SpaceUsage
import com.aakash.novafork.ui.model.WatchStats
import com.aakash.novafork.ui.model.watchTimeLabel

/**
 * Preferences › Sources › Storage: the folders that hold the videos and music, largest first, each
 * with its size and file count, and the total at the top. Worked out from the sizes the library
 * already stores. View only: nothing here deletes or moves a file.
 */
internal fun storageGroup(space: SpaceUsage, duplicateBytes: Long, actions: AppActions): SettingsGroup = SettingsGroup(
    key = "storage",
    title = "Storage",
    rows = buildList {
        add(SettingsRow("storage", "Storage", R.drawable.ic_storage) { shape -> StorageList(shape, space) })
        // The extra copies of films and episodes stored twice; the page it opens only lists them, nothing is deleted.
        if (duplicateBytes > 0L) {
            add(
                SettingsRow("storage-duplicates", "Duplicates", R.drawable.ic_video_library) { shape ->
                    val context = LocalContext.current
                    ActionRow(
                        shape = shape,
                        icon = R.drawable.ic_video_library,
                        title = "Duplicates use ${Formatter.formatShortFileSize(context, duplicateBytes)}",
                        onClick = { actions.openLibrary(LibraryFilter.DUPLICATES) },
                    )
                },
            )
        }
    },
    footer = "Room taken by your videos and music. Files of unknown size, such as streams, aren't counted. Duplicates counts the extra copies; nothing is deleted.",
)

@Composable
private fun StorageList(shape: Shape, space: SpaceUsage) {
    val context = LocalContext.current
    Column {
        if (space.isEmpty) {
            RowSurface(shape = shape) { RowTitle("Nothing to show", Modifier.weight(1f)) }
            return@Column
        }
        RowSurface(shape = shape) {
            RowTitle("Total", Modifier.weight(1f))
            RowValue(Formatter.formatShortFileSize(context, space.totalBytes))
        }
        space.folders.forEach { folder ->
            RowSeparator()
            RowSurface(shape = shape) {
                Column(Modifier.weight(1f)) {
                    RowTitle(folder.name())
                    Text(
                        text = folder.detail(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IosColors.SecondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RowValue(Formatter.formatShortFileSize(context, folder.bytes))
            }
        }
        if (space.moreFolders > 0) {
            RowSeparator()
            RowSurface(shape = shape) {
                RowTitle(if (space.moreFolders == 1) "1 smaller folder" else "${space.moreFolders} smaller folders", Modifier.weight(1f))
            }
        }
    }
}

/** The folder's own name, or its source's when the files sit in the source's root. */
private fun FolderSpace.name(): String = folder.substringAfterLast('/').ifBlank { sourceName }

/** "Device storage · Movies/Action · 12 files". */
private fun FolderSpace.detail(): String {
    val count = if (files == 1) "1 file" else "$files files"
    return listOf(sourceName, folder).filter { it.isNotBlank() }.plus(count).joinToString(" · ")
}

/**
 * Preferences › Sources › Watch Stats: the time watched this month and in all, the films finished
 * this month and the shows with most episodes watched. Only what the library stores can be summed,
 * so this month counts each video's latest viewing once (see [com.aakash.novafork.ui.model.watchStats]).
 */
internal fun watchStatsGroup(watch: WatchStats): SettingsGroup = SettingsGroup(
    key = "watch-stats",
    title = "Watch Stats",
    rows = if (watch.isEmpty) {
        listOf(SettingsRow("watch-stats-none", "Watch Stats", R.drawable.ic_history) { shape -> NothingWatchedRow(shape) })
    } else {
        buildList {
            add(infoRow("watch-month", R.drawable.ic_history, "Watched this month", watchTimeLabel(watch.monthMs)))
            add(infoRow("watch-all", R.drawable.ic_timer, "Watched in all", watchTimeLabel(watch.allTimeMs)))
            add(infoRow("watch-films", R.drawable.ic_movie, "Films finished this month", watch.filmsFinishedThisMonth.toString()))
            watch.topShows.forEachIndexed { index, show ->
                // Not searchable: a show's name has no place among Preferences' search results.
                add(
                    SettingsRow("watch-show-$index") { shape ->
                        InfoRow(shape, R.drawable.ic_tv, show.title, if (show.episodes == 1) "1 episode" else "${show.episodes} episodes")
                    },
                )
            }
        }
    },
    footer = "Time is counted from each video's last viewing, so a video watched twice in a month counts once.",
)

@Composable
private fun NothingWatchedRow(shape: Shape) {
    RowSurface(shape = shape) { RowTitle("Nothing watched yet", Modifier.weight(1f)) }
}
