package com.aakash.novafork.ui.audio

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aakash.novafork.R
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.ui.common.IosActionSheet
import com.aakash.novafork.ui.common.LocalHud
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Songs asked to be deleted from a menu: one song, or an album's; [label] names them. */
internal class SongDeleteRequest(val songs: List<SongEntity>, val label: String)

/**
 * Deleting songs from their long-press menu (owner), as videos are: our own question first, then
 * on Android 11+ Android's own bin (restorable for 30 days) for songs on the phone; songs in a
 * picked folder are deleted for good. The songs leave the library once their files are gone.
 */
@Composable
internal fun SongDeleteDialog(request: SongDeleteRequest?, actions: AudioActions, onClose: () -> Unit) {
    val context = LocalContext.current
    val hud = LocalHud.current
    val scope = rememberCoroutineScope()
    // The songs waiting on Android's own prompt.
    var pending by remember { mutableStateOf<SongDeleteRequest?>(null) }

    fun done(songs: List<SongEntity>, toBin: Boolean) {
        if (songs.isEmpty()) return
        actions.forgetDeletedSongs(songs.map { it.id })
        hud?.show(R.drawable.ic_delete, if (toBin) "Moved to Bin" else "Deleted")
    }

    lateinit var deleteFiles: (SongDeleteRequest) -> Unit
    val prompt = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val asked = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        // Android 11+ binned them on "Allow"; Android 10 only granted the right, so try again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) done(asked.songs, toBin = true) else deleteFiles(asked)
    }

    fun ask(request: SongDeleteRequest, sender: IntentSender) {
        pending = request
        try {
            prompt.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: ActivityNotFoundException) {
            pending = null
            hud?.show(R.drawable.ic_close, "Couldn't Delete")
        }
    }

    deleteFiles = { request ->
        scope.launch {
            val resolver = context.contentResolver
            var needsYes: IntentSender? = null
            val deleted = withContext(Dispatchers.IO) {
                request.songs.filter { song ->
                    val uri = Uri.parse(song.uri)
                    try {
                        when {
                            song.uri.startsWith("content://media/") -> resolver.delete(uri, null, null) > 0
                            song.uri.startsWith("content://") -> DocumentsContract.deleteDocument(resolver, uri)
                            song.uri.startsWith("file://") -> uri.path?.let { File(it).delete() } == true
                            else -> false
                        }
                    } catch (e: SecurityException) {
                        // Android 10: a song another app made needs the user's yes first.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                            needsYes = needsYes ?: e.userAction.actionIntent.intentSender
                        }
                        false
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            done(deleted, toBin = false)
            val left = request.songs - deleted.toSet()
            val sender = needsYes
            when {
                left.isEmpty() -> Unit
                sender != null -> ask(SongDeleteRequest(left, request.label), sender)
                else -> hud?.show(R.drawable.ic_close, "Couldn't Delete")
            }
        }
    }

    if (request == null) return
    val toBin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && request.songs.all { it.uri.startsWith("content://media/") }
    // iOS asks from the bottom, in thumb reach (owner).
    IosActionSheet(
        title = "Delete ${request.label}?",
        message = if (toBin) {
            "It moves to your phone's bin, where it can be restored for 30 days."
        } else {
            "Songs in picked folders have no bin: the file is deleted for good."
        },
        actionLabel = if (toBin) "Delete" else "Delete for Good",
        onAction = {
            onClose()
            if (toBin && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = request.songs.map { Uri.parse(it.uri) }
                ask(request, MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender)
            } else {
                deleteFiles(request)
            }
        },
        onDismiss = onClose,
    )
}
