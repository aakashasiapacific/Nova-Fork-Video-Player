package com.aakash.novafork.data.music

import android.content.Context
import androidx.core.content.edit
import com.aakash.novafork.data.db.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Song names fixed in the app (Now Playing › Fix Name…): a title and artist shown in place of the
 * file's tags. The files are never written; clearing both fields goes back to the tags.
 */
class SongNames(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)

    /** Bumps on every change, so the library regroups with the new names. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun apply(song: SongEntity): SongEntity {
        val title = prefs.getString(TITLE + song.id, null)
        val artist = prefs.getString(ARTIST + song.id, null)
        if (title == null && artist == null) return song
        return song.copy(title = title ?: song.title, artist = artist ?: song.artist)
    }

    fun set(songId: Long, title: String, artist: String) {
        prefs.edit {
            if (title.isBlank()) remove(TITLE + songId) else putString(TITLE + songId, title.trim())
            if (artist.isBlank()) remove(ARTIST + songId) else putString(ARTIST + songId, artist.trim())
        }
        _changes.value += 1
    }

    private companion object {
        const val PREFS = "song_names"
        const val TITLE = "t:"
        const val ARTIST = "a:"
    }
}
