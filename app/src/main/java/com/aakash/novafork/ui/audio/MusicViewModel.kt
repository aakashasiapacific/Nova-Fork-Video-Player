package com.aakash.novafork.ui.audio

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aakash.novafork.NovaApp
import com.aakash.novafork.data.StoragePermissions
import com.aakash.novafork.data.db.MusicPlaylistRow
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.MusicLibraryData
import com.aakash.novafork.data.music.MusicScanState
import com.aakash.novafork.music.MusicPlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A playlist opened on the Audio tab, with its songs. */
data class OpenPlaylist(val id: Long, val name: String, val songs: List<SongEntity>)

/**
 * The temporary Audio tab's state and actions: the music library, the scan, playlists and the
 * player. The Apple Music-style screens will replace the screen; this stays as their source.
 */
class MusicViewModel(app: Application) : AndroidViewModel(app), AudioActions {
    private val graph = NovaApp.graph(app)
    private val music = graph.music
    private val player = graph.musicPlayer

    val library: StateFlow<MusicLibraryData> =
        music.observeLibrary().stateIn(viewModelScope, SharingStarted.Eagerly, MusicLibraryData())
    val scan: StateFlow<MusicScanState> = music.scanState
    val playlists: StateFlow<List<MusicPlaylistRow>> =
        music.observePlaylists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playerState: StateFlow<MusicPlayerState> = player.state

    /** Starred song ids, most recently starred first. */
    val favorites: StateFlow<List<Long>> =
        music.observeFavorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _hasAccess = MutableStateFlow(StoragePermissions.hasAudioAccess(app))
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _openPlaylist = MutableStateFlow<OpenPlaylist?>(null)
    val openPlaylist: StateFlow<OpenPlaylist?> = _openPlaylist.asStateFlow()

    val permission: String get() = StoragePermissions.audioPermission()

    /** After a permission prompt and when the app comes back: rescans once access was granted. */
    fun onAccessChecked() {
        val granted = StoragePermissions.hasAudioAccess(getApplication())
        val gained = granted && !_hasAccess.value
        _hasAccess.value = granted
        if (gained) music.rescanInBackground()
    }

    override fun rescan() = music.rescanInBackground()

    override fun play(songs: List<SongEntity>, index: Int, shuffle: Boolean) {
        player.playSongs(songs.map { it.id }, index, shuffle)
    }

    override fun enqueue(songs: List<SongEntity>, next: Boolean) = player.enqueue(songs.map { it.id }, next)

    override fun skipAhead(ahead: Int) = player.skipTo(player.state.value.queueIndex + ahead)

    override fun togglePlay() = player.togglePlay()

    override fun next() = player.next()

    override fun previous() = player.previous()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun toggleShuffle() = player.setShuffle(!player.state.value.shuffle)

    override fun cycleRepeat() = player.cycleRepeat()

    override fun closePlayer() = player.close()

    override fun showPlaylist(row: MusicPlaylistRow) = launchSafely {
        _openPlaylist.value = OpenPlaylist(row.id, row.name, music.playlistSongs(row.id))
    }

    override fun closePlaylist() {
        _openPlaylist.value = null
    }

    /** Adds [song] to the playlist, or to a new one named [newName] when [playlistId] is null. */
    override fun addToPlaylist(song: SongEntity, playlistId: Long?, newName: String) = launchSafely {
        val id = playlistId ?: music.createPlaylist(newName)
        music.addToPlaylist(id, listOf(song.id))
        refreshOpenPlaylist()
    }

    override fun createPlaylist(name: String) = launchSafely {
        music.createPlaylist(name)
    }

    override fun removeFromPlaylist(song: SongEntity) = launchSafely {
        val open = _openPlaylist.value ?: return@launchSafely
        music.removeFromPlaylist(open.id, song.id)
        refreshOpenPlaylist()
    }

    override fun deletePlaylist(playlistId: Long) = launchSafely {
        music.deletePlaylist(playlistId)
        if (_openPlaylist.value?.id == playlistId) _openPlaylist.value = null
    }

    override fun toggleFavorite(songId: Long) = launchSafely {
        music.setFavorite(songId, favorite = songId !in favorites.value)
    }

    private suspend fun refreshOpenPlaylist() {
        val open = _openPlaylist.value ?: return
        _openPlaylist.value = open.copy(songs = music.playlistSongs(open.id))
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Music action failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "MusicViewModel"
    }
}
