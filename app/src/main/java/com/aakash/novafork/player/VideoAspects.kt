package com.aakash.novafork.player

import android.content.Context
import com.aakash.novafork.data.AspectMode

/**
 * Each video's own fit or zoom (owner): picked once for a video, it opens that way next time.
 * Kept by the video's key, like [SubtitleDelays].
 */
internal class VideoAspects(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(videoKey: String): AspectMode? {
        val name = try {
            prefs.getString(videoKey, null)
        } catch (e: ClassCastException) {
            null
        } ?: return null
        return AspectMode.entries.firstOrNull { it.name == name }
    }

    fun set(videoKey: String, mode: AspectMode) {
        prefs.edit().putString(videoKey, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "video_aspects"
    }
}
