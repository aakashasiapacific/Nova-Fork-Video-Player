package com.aakash.novafork.data.music

import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.core.library.NaturalOrder
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.sources.music.SongArt

data class Album(
    /** [SongEntity.albumKey]. */
    val key: String,
    val title: String,
    /** The album artist, the one artist of its songs, or "Various Artists". */
    val artist: String,
    val artistKey: String,
    val year: Int?,
    /** Disc, then track order. */
    val songs: List<SongEntity>,
    /** Newest file of the album, for "Recently Added". */
    val dateAdded: Long,
) {
    /** The album's art: its cover file, else the picture in its first song. */
    val artUri: String? get() = songs.firstNotNullOfOrNull { it.coverUri } ?: songs.firstOrNull()?.let { SongArt.embedded(it.uri) }
}

data class Artist(
    /** [SongEntity.artistKey]. */
    val key: String,
    val name: String,
    /** Newest first. */
    val albums: List<Album>,
    /** Most played first, then by title. */
    val songs: List<SongEntity>,
)

data class Genre(val name: String, val songs: List<SongEntity>)

/** The music library grouped the way the Audio tab shows it. */
data class MusicLibraryData(
    /** By title, ignoring a leading "The". */
    val songs: List<SongEntity> = emptyList(),
    /** By title. */
    val albums: List<Album> = emptyList(),
    /** By name, ignoring a leading "The". */
    val artists: List<Artist> = emptyList(),
    /** By name. */
    val genres: List<Genre> = emptyList(),
    /** Albums, newest first. */
    val recentlyAdded: List<Album> = emptyList(),
)

/** Pure grouping of songs into albums, artists and genres (unit-tested). */
object MusicLibrary {
    /** The song's art: the cover next to it, else the picture embedded in it. */
    fun artUri(song: SongEntity): String = song.coverUri ?: SongArt.embedded(song.uri)

    /** An album's songs in play order: disc, track, then file name; songs without numbers last. */
    val trackOrder: Comparator<SongEntity> = compareBy<SongEntity> { it.discNumber ?: 1 }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy(NaturalOrder) { it.fileName }

    private val titleOrder: Comparator<SongEntity> = compareBy<SongEntity> { MusicKeys.sortKey(it.title) }
        .thenBy { MusicKeys.sortKey(it.artist.orEmpty()) }
        .thenBy { it.id }

    fun build(songs: List<SongEntity>): MusicLibraryData {
        val albums = albums(songs)
        return MusicLibraryData(
            songs = songs.sortedWith(titleOrder),
            albums = albums.sortedWith(compareBy<Album> { MusicKeys.sortKey(it.title) }.thenBy { it.key }),
            artists = artists(songs, albums),
            genres = genres(songs),
            recentlyAdded = albums.sortedWith(compareByDescending<Album> { it.dateAdded }.thenBy { it.key }),
        )
    }

    fun albums(songs: List<SongEntity>): List<Album> =
        songs.groupBy { it.albumKey }.map { (key, group) ->
            val ordered = group.sortedWith(trackOrder)
            val artist = MusicKeys.albumArtistOf(group.map { it.albumArtist }, group.map { it.artist })
            Album(
                key = key,
                title = group.firstNotNullOfOrNull { it.album } ?: MusicKeys.UNKNOWN_ALBUM,
                artist = artist,
                artistKey = group.first().artistKey,
                year = group.mapNotNull { it.year }.maxOrNull(),
                songs = ordered,
                dateAdded = group.maxOf { it.dateAdded },
            )
        }

    private fun artists(songs: List<SongEntity>, albums: List<Album>): List<Artist> {
        val albumsByArtist = albums.groupBy { it.artistKey }
        return songs.groupBy { it.artistKey }.map { (key, group) ->
            Artist(
                key = key,
                name = MusicKeys.artistOf(group.firstNotNullOfOrNull { it.albumArtist }, group.firstNotNullOfOrNull { it.artist }),
                albums = albumsByArtist[key].orEmpty().sortedWith(
                    compareByDescending<Album> { it.year ?: 0 }.thenBy { MusicKeys.sortKey(it.title) },
                ),
                songs = group.sortedWith(compareByDescending<SongEntity> { it.playCount }.then(titleOrder)),
            )
        }.sortedWith(compareBy<Artist> { MusicKeys.sortKey(it.name) }.thenBy { it.key })
    }

    private fun genres(songs: List<SongEntity>): List<Genre> =
        songs.filter { !it.genre.isNullOrBlank() }
            .groupBy { MusicKeys.normalize(it.genre.orEmpty()) }
            .map { (_, group) -> Genre(group.first().genre.orEmpty().trim(), group.sortedWith(titleOrder)) }
            .sortedBy { MusicKeys.sortKey(it.name) }
}
