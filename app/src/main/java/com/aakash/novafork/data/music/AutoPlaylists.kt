package com.aakash.novafork.data.music

import com.aakash.novafork.data.db.SongEntity

/**
 * Apple Music's smart playlists: derived from the library every time, so they keep themselves up
 * to date and have nothing stored (nor anything to edit or delete). Unit-tested.
 */
enum class AutoPlaylist(val title: String, val limit: Int) {
    FAVOURITES("Favourite Songs", Int.MAX_VALUE),
    TOP_PLAYED("Top 25 Most Played", 25),
    RECENTLY_ADDED("Recently Added", 50),
    RECENTLY_PLAYED("Recently Played", 50),
    ;

    /**
     * The playlist's songs: [library]'s songs, picked and ordered by this playlist's rule.
     * [favourites] are the starred song ids, newest favourite first (only [FAVOURITES] reads them).
     */
    fun songs(library: MusicLibraryData, favourites: List<Long> = emptyList()): List<SongEntity> = when (this) {
        FAVOURITES -> {
            val byId = library.songs.associateBy { it.id }
            favourites.mapNotNull { byId[it] }
        }
        // Ties: the one played more recently first, then the older id, so the order is stable.
        TOP_PLAYED -> library.songs
            .filter { it.playCount > 0 }
            .sortedWith(compareByDescending<SongEntity> { it.playCount }.thenByDescending { it.lastPlayedAt ?: 0L }.thenBy { it.id })
        RECENTLY_ADDED -> library.songs
            .sortedWith(compareByDescending<SongEntity> { it.dateAdded }.thenByDescending { it.id })
        RECENTLY_PLAYED -> library.songs
            .filter { it.lastPlayedAt != null }
            .sortedWith(compareByDescending<SongEntity> { it.lastPlayedAt ?: 0L }.thenByDescending { it.id })
    }.take(limit)

    companion object {
        /** The playlists that have songs now, with them, in the order the Playlists page lists them. */
        fun withSongs(library: MusicLibraryData, favourites: List<Long> = emptyList()): Map<AutoPlaylist, List<SongEntity>> =
            entries.associateWith { it.songs(library, favourites) }.filterValues { it.isNotEmpty() }
    }
}
