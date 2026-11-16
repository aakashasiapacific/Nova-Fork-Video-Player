package com.aakash.novafork.player

import android.content.Context
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A saved spot in a video: [positionMs] with a [label] ("1:05:12" unless the person named it). */
data class VideoBookmark(val positionMs: Long, val label: String, val createdAtMs: Long)

/**
 * Bookmarks inside videos, kept per video in SharedPreferences under the video's key (its library
 * id, or a hash of a link; see com.aakash.novafork.sources.DownloadedSubtitles.videoKey). The
 * player adds, lists and removes them; [bookmarks] is the read API a details page can use later.
 */
class VideoBookmarkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** [videoKey]'s bookmarks by position, earliest first. */
    fun bookmarks(videoKey: String): List<VideoBookmark> {
        val raw = try {
            prefs.getString(videoKey, null)
        } catch (e: ClassCastException) {
            null
        } ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                VideoBookmark(
                    positionMs = item.optLong(POSITION, -1L).takeIf { it >= 0L } ?: return@mapNotNull null,
                    label = item.optString(LABEL),
                    createdAtMs = item.optLong(CREATED),
                )
            }.sortedBy { it.positionMs }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Saves [positionMs]; a bookmark within a second of an existing one is replaced. Returns the new list. */
    fun add(videoKey: String, positionMs: Long, label: String): List<VideoBookmark> {
        val kept = bookmarks(videoKey).filter { abs(it.positionMs - positionMs) > DUPLICATE_MS }
        return save(videoKey, kept + VideoBookmark(positionMs, label, System.currentTimeMillis()))
    }

    /** Removes the bookmark at [positionMs]. Returns the new list. */
    fun remove(videoKey: String, positionMs: Long): List<VideoBookmark> =
        save(videoKey, bookmarks(videoKey).filter { it.positionMs != positionMs })

    private fun save(videoKey: String, list: List<VideoBookmark>): List<VideoBookmark> {
        val sorted = list.sortedBy { it.positionMs }
        val array = JSONArray()
        sorted.forEach { array.put(JSONObject().put(POSITION, it.positionMs).put(LABEL, it.label).put(CREATED, it.createdAtMs)) }
        prefs.edit().apply { if (sorted.isEmpty()) remove(videoKey) else putString(videoKey, array.toString()) }.apply()
        return sorted
    }

    private companion object {
        const val PREFS_NAME = "video_bookmarks"
        const val POSITION = "p"
        const val LABEL = "l"
        const val CREATED = "t"
        const val DUPLICATE_MS = 1_000L
    }
}
