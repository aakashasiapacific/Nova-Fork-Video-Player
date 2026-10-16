package com.aakash.novafork.ui.audio

import com.aakash.novafork.data.db.MusicPlaylistRow
import com.aakash.novafork.data.db.SongEntity

/** What the Audio tab can do (implemented by [MusicViewModel]; tests pass a no-op). */
interface AudioActions {
    fun rescan()

    /** Plays [songs] from [index]; with [shuffle] the rest in random order. */
    fun play(songs: List<SongEntity>, index: Int, shuffle: Boolean)
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun closePlayer()
    fun showPlaylist(row: MusicPlaylistRow)
    fun closePlaylist()

    /** To the playlist [playlistId], or to a new one named [newName] when it is null. */
    fun addToPlaylist(song: SongEntity, playlistId: Long?, newName: String)
    fun removeFromPlaylist(song: SongEntity)
    fun deletePlaylist(playlistId: Long)
}
