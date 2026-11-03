package com.aakash.novafork.ui.settings

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import com.aakash.novafork.R
import com.aakash.novafork.ui.common.SwipeToDelete
import com.aakash.novafork.ui.common.IosColors
import com.aakash.novafork.ui.model.AppActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One video downloaded into Movies/Nova Fork. */
internal class DownloadedVideo(val id: Long, val name: String, val size: Long)

/**
 * Preferences › Sources › Downloads (owner: a Storage page): what was downloaded from network
 * shares into Movies/Nova Fork, each with its size, and the total. Swiping one left removes that
 * downloaded copy only; the original on the share is never touched.
 */
internal fun downloadsGroup(actions: AppActions): SettingsGroup = SettingsGroup(
    key = "downloads",
    title = "Downloads",
    rows = listOf(SettingsRow("downloads", "Downloads", R.drawable.ic_storage) { shape -> DownloadsList(shape, actions) }),
    footer = "Videos downloaded from network shares, kept in Movies/Nova Fork. Swipe one left to remove the " +
        "downloaded copy; the original on the share stays.",
)

@Composable
private fun DownloadsList(shape: Shape, actions: AppActions) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val downloads by produceState<List<DownloadedVideo>?>(null, reload) {
        value = withContext(Dispatchers.IO) { loadDownloads(context) }
    }
    val list = downloads ?: return
    Column {
        if (list.isEmpty()) {
            RowSurface(shape = shape) {
                RowTitle("Nothing downloaded", Modifier.weight(1f))
            }
            return@Column
        }
        RowSurface(shape = shape) {
            RowTitle("Total", Modifier.weight(1f))
            RowValue(Formatter.formatShortFileSize(context, list.sumOf { it.size }))
        }
        list.forEach { video ->
            RowSeparator()
            SwipeToDelete(
                onDelete = {
                    if (removeDownload(context, video.id)) {
                        reload++
                        actions.refreshQuietly()
                    } else {
                        actions.showMessage("Couldn't remove it — remove it from the Files app")
                    }
                },
                label = "Remove",
                background = IosColors.GroupedCard,
            ) {
                RowSurface(shape = shape) {
                    RowTitle(video.name, Modifier.weight(1f))
                    RowValue(Formatter.formatShortFileSize(context, video.size))
                }
            }
        }
    }
}

/** The videos in Movies/Nova Fork, largest first. */
private fun loadDownloads(context: Context): List<DownloadedVideo> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val folder = Environment.DIRECTORY_MOVIES + "/" + FOLDER
    val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("$folder%")
    } else {
        @Suppress("DEPRECATION")
        "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/$folder/%")
    }
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
    return try {
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.SIZE} DESC")?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(DownloadedVideo(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2)))
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        emptyList()
    }
}

/** Removes the downloaded copy [id] (one the app wrote, so no permission is asked); false if it can't. */
private fun removeDownload(context: Context, id: Long): Boolean = try {
    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    context.contentResolver.delete(uri, null, null) > 0
} catch (e: SecurityException) {
    false
}

private const val FOLDER = "Nova Fork"
