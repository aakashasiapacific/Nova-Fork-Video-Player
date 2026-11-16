package com.aakash.novafork.ui.common

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/** A folder of a source kept on Browse for a quick way back (a share's deep folder, say). */
data class FolderBookmark(val sourceId: Long, val path: String, val name: String)

/** Browse › Bookmarks: folders bookmarked from their page's top bar, kept across launches. */
object FolderBookmarks {
    private const val PREFS = "folder_bookmarks"
    private const val KEY = "list"

    val all = mutableStateListOf<FolderBookmark>()

    fun load(context: Context) {
        val lines = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty()
        all.clear()
        lines.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('\t')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            all += FolderBookmark(id, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "Folder" })
        }
    }

    fun isBookmarked(sourceId: Long, path: String): Boolean = all.any { it.sourceId == sourceId && it.path == path }

    fun toggle(context: Context, sourceId: Long, path: String, name: String) {
        val existing = all.firstOrNull { it.sourceId == sourceId && it.path == path }
        if (existing != null) all.remove(existing) else all += FolderBookmark(sourceId, path, name)
        val text = all.joinToString("\n") { "${it.sourceId}\t${it.path.replace('\t', ' ')}\t${it.name.replace('\t', ' ')}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).apply()
    }
}
