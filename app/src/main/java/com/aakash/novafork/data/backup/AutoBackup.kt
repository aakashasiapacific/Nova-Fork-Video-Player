package com.aakash.novafork.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AutoBackup"

/**
 * Weekly automatic backups into a folder the user picked. There is no scheduler: the app checks
 * on start whether the last one is older than [INTERVAL_MS] and writes one in the background.
 * Only files this feature made (NovaFork-auto-yyyy-mm-dd.json) are ever deleted: the newest
 * [KEEP] stay. Backups never include passwords or sign-ins.
 */
class AutoBackup(
    context: Context,
    private val backups: BackupManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext: Context = context.applicationContext
    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class State(val weekly: Boolean, val folder: Uri?, val folderName: String?, val lastAt: Long?)

    fun state(): State {
        val folder = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        return State(
            weekly = prefs.getBoolean(KEY_WEEKLY, false),
            folder = folder,
            folderName = folder?.let(::folderName),
            lastAt = prefs.getLong(KEY_LAST_AT, 0L).takeIf { it > 0L },
        )
    }

    fun setWeekly(on: Boolean) = prefs.edit { putBoolean(KEY_WEEKLY, on) }

    /** Remembers [tree] (its read and write grant is taken by the caller) and lets go of the previous folder's grant. */
    fun setFolder(tree: Uri) {
        val previous = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        prefs.edit { putString(KEY_FOLDER, tree.toString()) }
        if (previous != null && previous != tree) {
            try {
                appContext.contentResolver.releasePersistableUriPermission(
                    previous,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Releasing the previous folder failed", e)
            }
        }
    }

    fun isDue(): Boolean {
        val s = state()
        if (!s.weekly || s.folder == null) return false
        val last = s.lastAt ?: return true
        val now = clock()
        return now - last >= INTERVAL_MS || last > now
    }

    /** Writes a backup if one is due. Returns the new last time, or null if nothing was written. */
    suspend fun runIfDue(): Long? = if (isDue()) writeNow() else null

    /** Writes a backup into the folder and trims old automatic ones; failures are logged, never shown. */
    suspend fun writeNow(): Long? = withContext(Dispatchers.IO) {
        val tree = state().folder ?: return@withContext null
        try {
            val resolver = appContext.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val backup = backups.create(includeSecrets = false)
            val bytes = BackupCodec.encode(backup).toByteArray(Charsets.UTF_8)
            val name = fileName(LocalDate.now())
            val existing = children(tree).firstOrNull { it.name == name }
            val target = existing?.uri
                ?: DocumentsContract.createDocument(resolver, parent, BackupManager.MIME_TYPE, name)
                ?: return@withContext null
            // "wt" truncates a file of ours from earlier the same day.
            (resolver.openOutputStream(target, "wt") ?: return@withContext null).use { it.write(bytes) }
            prefs.edit { putLong(KEY_LAST_AT, backup.createdAt) }
            trim(tree)
            backup.createdAt
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Automatic backup failed", e)
            null
        }
    }

    private class Child(val uri: Uri, val name: String)

    private fun children(tree: Uri): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<Child>()
        appContext.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out += Child(DocumentsContract.buildDocumentUriUsingTree(tree, id), name)
            }
        }
        return out
    }

    /** Deletes all but the newest [KEEP] of the app's own automatic files. */
    private fun trim(tree: Uri) {
        val ours = children(tree).filter { NAME_PATTERN.matches(it.name) }.sortedByDescending { it.name }
        for (old in ours.drop(KEEP)) {
            try {
                DocumentsContract.deleteDocument(appContext.contentResolver, old.uri)
            } catch (e: Exception) {
                Log.w(TAG, "Removing an old automatic backup failed", e)
            }
        }
    }

    private fun folderName(tree: Uri): String? = try {
        DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifBlank { "Storage" }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val KEEP = 4
        const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private const val PREFS = "backup"
        private const val KEY_WEEKLY = "auto_weekly"
        private const val KEY_FOLDER = "auto_folder"
        private const val KEY_LAST_AT = "auto_last_at"
        private val NAME_PATTERN = Regex("""NovaFork-auto-\d{4}-\d{2}-\d{2}\.json""")

        /** "NovaFork-auto-2026-11-03.json" */
        fun fileName(date: LocalDate): String = "NovaFork-auto-$date.json"
    }
}
