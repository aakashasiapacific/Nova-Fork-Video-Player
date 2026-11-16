package com.aakash.novafork.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One music file (schema version 4). Songs come from the same local sources as videos: the
 * DEVICE row (MediaStore's audio table) and every FOLDER (picked SAF tree). Like videos, a song
 * the last scan of its source did not see stays with [present] = false, so playlists keep it
 * until the file is back.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["albumKey"]),
        Index(value = ["artistKey"]),
    ],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    /** content:// (MediaStore or SAF) or file://; what the player opens. */
    val uri: String,
    val fileName: String,
    /** Display folder path, root → leaf, '/'-separated ("Music/Artist/Album"). */
    val folder: String,
    /** The title tag, else the file name without track number and extension. */
    val title: String,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    /** core MusicKeys.albumKey: groups the songs of one album. */
    val albumKey: String,
    /** core MusicKeys.artistKey: the album artist, else the artist. */
    val artistKey: String,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    /** Epoch ms when the file first showed up. */
    val dateAdded: Long = 0,
    val dateModified: Long? = null,
    /** cover.jpg / folder.jpg … next to the song; null = the art embedded in the file, if any. */
    val coverUri: String? = null,
    /** The .lrc lyrics file next to the song. */
    val lyricsUri: String? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val present: Boolean = true,
)

/**
 * One song in a playlist (schema version 4): playlists hold songs here and videos in
 * [PlaylistItemEntity], each list in its own order. Deleting the playlist or the song's row
 * deletes the item.
 */
@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["songId"]),
    ],
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

/**
 * A movie, show or video the user saved for later with the hero's "+" button (schema version 4).
 * [itemKey] is the library item's key as the UI knows it ("v:<videoId>" for a movie or video,
 * "s:<showKey>" for a show), so it survives metadata refreshes.
 */
@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val itemKey: String,
    val addedAt: Long,
)

/**
 * A song starred with Apple Music's star button (schema version 7). Kept apart from [SongEntity]
 * so rescans never touch it; a song that left the library just stops showing.
 */
@Entity(tableName = "song_favorites")
data class FavoriteSongEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long,
)

/**
 * A movie, show or video starred with the hero's star (schema version 8), keyed like
 * [WatchlistEntity] ("v:<videoId>", "s:<showKey>").
 */
@Entity(tableName = "video_favorites")
data class VideoFavoriteEntity(
    @PrimaryKey val itemKey: String,
    val addedAt: Long,
)
