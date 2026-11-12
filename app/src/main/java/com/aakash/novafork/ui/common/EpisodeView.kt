package com.aakash.novafork.ui.common

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How a show page lists a season's episodes, chosen in its season menu and kept for every show:
 * watched episodes hidden or not, and newest first or in order.
 */
object EpisodeView {
    private const val PREFS = "episode_view"
    private const val KEY_HIDE_WATCHED = "hide_watched"
    private const val KEY_NEWEST_FIRST = "newest_first"

    var hideWatched by mutableStateOf(false)
        private set
    var newestFirst by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        hideWatched = prefs.getBoolean(KEY_HIDE_WATCHED, false)
        newestFirst = prefs.getBoolean(KEY_NEWEST_FIRST, false)
    }

    fun setHideWatched(context: Context, on: Boolean) {
        hideWatched = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_WATCHED, on).apply()
    }

    fun setNewestFirst(context: Context, on: Boolean) {
        newestFirst = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NEWEST_FIRST, on).apply()
    }

    /** [rows] (in episode order) as the person wants them listed; [watched] tells a row's state. */
    fun <T> arrange(rows: List<T>, watched: (T) -> Boolean?): List<T> {
        val shown = if (hideWatched) rows.filter { watched(it) != true } else rows
        return if (newestFirst) shown.asReversed() else shown
    }
}

/** Spoiler-safe mode (Preferences › Appearance): unwatched episodes hide their summary and blur their still. */
val LocalSpoilerSafe = compositionLocalOf { false }
