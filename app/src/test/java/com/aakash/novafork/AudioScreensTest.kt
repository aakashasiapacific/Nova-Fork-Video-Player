package com.aakash.novafork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.core.audio.LrcParser
import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.db.MusicPlaylistRow
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.music.MusicLibrary
import com.aakash.novafork.data.music.MusicScanState
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.music.NowPlaying
import com.aakash.novafork.ui.audio.AudioActions
import com.aakash.novafork.ui.audio.AudioContent
import com.aakash.novafork.ui.audio.MiniPlayerBar
import com.aakash.novafork.ui.audio.NowPlayingScreen
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.theme.AudioTheme
import com.aakash.novafork.ui.theme.NovaTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the Audio tab, Now Playing and the mini player to build/screens/audio_*.png with a fake music library. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-420dpi")
class AudioScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installImages() {
        FakeImages.install(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetImages() {
        FakeImages.reset()
    }

    private val songs = listOf(
        song(1, "Bohemian Rhapsody", "Queen", "A Night at the Opera", 11, 354_000),
        song(2, "Love of My Life", "Queen", "A Night at the Opera", 9, 219_000),
        song(3, "Yesterday", "The Beatles", "Help!", 13, 125_000),
        song(4, "Angie", "The Rolling Stones", "Goats Head Soup", 5, 273_000),
        song(5, "Clocks", "Coldplay", "A Rush of Blood to the Head", 5, 307_000),
    )

    @Test
    fun songsWithThePlayer() {
        val library = MusicLibrary.build(songs)
        val player = MusicPlayerState(
            active = true,
            nowPlaying = NowPlaying(1, "Bohemian Rhapsody", "Queen", "A Night at the Opera", MusicLibrary.artUri(songs[0])),
            isPlaying = true,
            positionMs = 75_000,
            durationMs = 354_000,
            shuffle = true,
            repeat = RepeatMode.ALL,
            hasNext = true,
            hasPrevious = true,
            queueIndex = 0,
            queueSize = 5,
            lyrics = LrcParser.parse("[01:10.00]Is this the real life?\n[01:14.00]Is this just fantasy?"),
        )
        compose.captureScreen("audio_songs") {
            NovaTheme { AudioTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AudioContent(
                        library = library,
                        scan = MusicScanState(lastScanAt = 1L),
                        playlists = listOf(MusicPlaylistRow(1, "Road trip", 1L, 3, 1)),
                        player = player,
                        hasAccess = true,
                        openPlaylist = null,
                        actions = NoOpAudioActions,
                        onRequestAccess = {},
                    )
                }
            } }
        }
    }

    private fun playing(lyrics: Boolean = false) = MusicPlayerState(
        active = true,
        nowPlaying = NowPlaying(1, "Bohemian Rhapsody", "Queen", "A Night at the Opera", MusicLibrary.artUri(songs[0])),
        isPlaying = true,
        positionMs = 75_000,
        durationMs = 354_000,
        hasNext = true,
        hasPrevious = true,
        queueIndex = 0,
        queueSize = 5,
        lyrics = if (lyrics) LrcParser.parse("[01:10.00]Is this the real life?\n[01:14.00]Is this just fantasy?\n[01:18.00]Caught in a landslide") else null,
    )

    @Test
    fun nowPlaying() {
        compose.captureScreen("audio_now_playing") {
            NovaTheme {
                // No volume row: its polling loop never lets a test go idle.
                NowPlayingScreen(player = playing(), actions = NoOpAudioActions, onDismiss = {}, showVolume = false)
            }
        }
    }

    @Test
    fun miniPlayer() {
        compose.captureScreen("audio_mini_player") {
            NovaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
                        MiniPlayerBar(player = playing(), actions = NoOpAudioActions, onOpen = {})
                    }
                }
            }
        }
    }

    @Test
    fun noAccessYet() {
        compose.captureScreen("audio_no_access") {
            NovaTheme { AudioTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AudioContent(
                        library = MusicLibrary.build(emptyList()),
                        scan = MusicScanState(error = "Allow access to music to scan this device"),
                        playlists = emptyList(),
                        player = MusicPlayerState(),
                        hasAccess = false,
                        openPlaylist = null,
                        actions = NoOpAudioActions,
                        onRequestAccess = {},
                    )
                }
            } }
        }
    }

    private fun song(id: Long, title: String, artist: String, album: String, track: Int, durationMs: Long) = SongEntity(
        id = id,
        sourceId = 1,
        uri = "content://media/external/audio/media/$id",
        fileName = "$track $title.mp3",
        folder = "Music/$artist/$album",
        title = title,
        artist = artist,
        album = album,
        albumKey = MusicKeys.albumKey(null, album, "Music/$artist/$album"),
        artistKey = MusicKeys.artistKey(null, artist),
        trackNumber = track,
        genre = "Rock",
        durationMs = durationMs,
        dateAdded = id,
    )

    private object NoOpAudioActions : AudioActions {
        override fun rescan() = Unit
        override fun play(songs: List<SongEntity>, index: Int, shuffle: Boolean) = Unit
        override fun togglePlay() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeat() = Unit
        override fun closePlayer() = Unit
        override fun showPlaylist(row: MusicPlaylistRow) = Unit
        override fun closePlaylist() = Unit
        override fun addToPlaylist(song: SongEntity, playlistId: Long?, newName: String) = Unit
        override fun removeFromPlaylist(song: SongEntity) = Unit
        override fun deletePlaylist(playlistId: Long) = Unit
    }
}
