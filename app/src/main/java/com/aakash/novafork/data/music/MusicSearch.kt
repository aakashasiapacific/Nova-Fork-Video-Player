package com.aakash.novafork.data.music

import com.aakash.novafork.core.audio.MusicKeys
import com.aakash.novafork.data.db.SongEntity

/** What a search finds in the music library, best matches first. */
data class MusicSearchResult(
    val songs: List<SongEntity> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * Finds songs (by title, artist or album), albums (by title or artist) and artists (by name).
 * Case and accents don't matter ("beyonce" finds "Beyoncé"); names that start with the query come
 * before ones that only contain it, then the library's own order.
 */
object MusicSearch {
    const val LIMIT = 20

    fun search(library: MusicLibraryData, query: String, limit: Int = LIMIT): MusicSearchResult {
        val needle = MusicKeys.normalize(query)
        if (needle.isEmpty()) return MusicSearchResult()
        fun score(vararg fields: String?): Int? {
            var best: Int? = null
            for (field in fields) {
                val value = field?.let(MusicKeys::normalize) ?: continue
                val rank = when {
                    value.startsWith(needle) -> 0
                    value.contains(needle) -> 1
                    else -> continue
                }
                if (best == null || rank < best) best = rank
            }
            return best
        }
        fun <T> ranked(items: List<T>, fields: (T) -> Array<String?>): List<T> =
            items.mapNotNull { item -> score(*fields(item))?.let { item to it } }
                .sortedBy { it.second }
                .take(limit)
                .map { it.first }
        return MusicSearchResult(
            songs = ranked(library.songs) { arrayOf(it.title, it.artist, it.albumArtist, it.album) },
            albums = ranked(library.albums) { arrayOf(it.title, it.artist) },
            artists = ranked(library.artists) { arrayOf(it.name) },
        )
    }
}
