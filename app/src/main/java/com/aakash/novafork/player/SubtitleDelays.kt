package com.aakash.novafork.player

import android.content.Context

/**
 * Each video's subtitle timing (owner): set once for a video, it is back the next time that video
 * plays. Kept by the video's key (its library id, or a hash of a link; see
 * com.aakash.novafork.sources.DownloadedSubtitles.videoKey); a video in time keeps no entry.
 */
internal class SubtitleDelays(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** [videoKey]'s delay in ms (positive: later), 0 when none was set. */
    fun get(videoKey: String): Long = try {
        prefs.getLong(videoKey, 0L)
    } catch (e: ClassCastException) {
        0L
    }

    fun set(videoKey: String, delayMs: Long) {
        prefs.edit().apply {
            if (delayMs == 0L) remove(videoKey) else putLong(videoKey, delayMs)
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "subtitle_delays"
    }
}
