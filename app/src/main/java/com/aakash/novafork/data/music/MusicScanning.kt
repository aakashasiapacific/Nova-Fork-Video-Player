package com.aakash.novafork.data.music

import com.aakash.novafork.data.db.SourceEntity

/** One music file a scanner found, with the tags it could read (null = not tagged). */
data class ScannedSong(
    /** content:// or file://; unique across sources and stable from scan to scan (play counts and playlists hang on it). */
    val uri: String,
    val fileName: String,
    /** Display folder path root → leaf ("Music/Artist/Album"). */
    val folder: String,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    /** Epoch ms; the file's own date when known, else now. */
    val dateAdded: Long,
    val dateModified: Long? = null,
    val coverUri: String? = null,
    val lyricsUri: String? = null,
    /**
     * The file is the one the library already has (same size and date) and the scanner skipped
     * reading its tags: the repository keeps the stored ones. Only the cover and lyrics are new.
     */
    val tagsUnchanged: Boolean = false,
)

/** What the library already knows about a file, so a scanner can skip reading unchanged tags. */
data class KnownSong(val sizeBytes: Long?, val dateModified: Long?)

/** Finds the music of one local source (DEVICE or FOLDER). */
interface MusicScanner {
    /**
     * Every music file of [source]. [known] maps the URIs the library already has to their size
     * and date. Throws an IOException (with a message for people) when the source can't be read
     * at all; the library then stays as it is. Main-safe.
     */
    suspend fun scan(source: SourceEntity, known: Map<String, KnownSong>): List<ScannedSong>
}
