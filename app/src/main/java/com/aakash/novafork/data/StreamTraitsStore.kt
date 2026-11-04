package com.aakash.novafork.data

import android.content.Context
import androidx.core.content.edit

/**
 * What a video's streams turned out to be once it was played (libVLC reports its tracks only
 * then): today the most audio channels of any track, for the details page's "5.1" / "7.1"
 * badge. Kept by video id in SharedPreferences, so nothing is guessed and no migration is needed.
 */
class StreamTraitsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Audio channels seen for [videoId], or null when it hasn't been played since this was added. */
    fun channels(videoId: Long): Int? = prefs.getInt(videoId.toString(), 0).takeIf { it > 0 }

    fun recordChannels(videoId: Long, channels: Int) {
        if (channels <= 0 || prefs.getInt(videoId.toString(), 0) == channels) return
        prefs.edit { putInt(videoId.toString(), channels) }
    }

    private companion object {
        const val PREFS = "stream_traits"
    }
}
