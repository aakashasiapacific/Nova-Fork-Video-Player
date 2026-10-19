package com.aakash.novafork.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    /** Songs the library shows: present, from a source that is turned on. */
    @Query(
        "SELECT * FROM songs WHERE present = 1 AND sourceId IN (SELECT id FROM sources WHERE enabled = 1) " +
            "ORDER BY dateAdded DESC, id DESC",
    )
    fun observeShown(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun byId(id: Long): SongEntity?

    /** At most [SQL_CHUNK] ids per call. */
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE sourceId = :sourceId")
    suspend fun bySource(sourceId: Long): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun updateAll(songs: List<SongEntity>)

    /** At most [SQL_CHUNK] ids per call. */
    @Query("UPDATE songs SET present = 0 WHERE id IN (:ids)")
    suspend fun markMissing(ids: List<Long>)

    /** Songs whose source was removed (sources have no foreign key on songs). */
    @Query("DELETE FROM songs WHERE sourceId NOT IN (SELECT id FROM sources)")
    suspend fun deleteOrphans(): Int

    /** Songs with plays, for backups. */
    @Query("SELECT * FROM songs WHERE playCount > 0 OR lastPlayedAt IS NOT NULL")
    suspend fun withPlays(): List<SongEntity>

    /** Every song, present or not, as a restored backup's entries are matched against it. */
    @Query("SELECT id, sourceId, uri, folder, fileName, sizeBytes FROM songs")
    suspend fun identities(): List<SongIdentity>

    /** Puts back a song's plays from a backup. */
    @Query("UPDATE songs SET playCount = :playCount, lastPlayedAt = :lastPlayedAt WHERE id = :id")
    suspend fun restorePlays(id: Long, playCount: Int, lastPlayedAt: Long?)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun markPlayed(id: Long, playedAt: Long)

    @Query("UPDATE songs SET durationMs = :durationMs WHERE id = :id AND (durationMs IS NULL OR durationMs = 0)")
    suspend fun fillDuration(id: Long, durationMs: Long)

    /** Songs per source, shown (present) and hidden, for Library diagnostics. */
    @Query("SELECT sourceId, present, COUNT(*) AS count FROM songs GROUP BY sourceId, present")
    suspend fun countsBySource(): List<SourceVideoCount>
}

/** What identifies a song when a restored backup's entries are matched to the library. */
data class SongIdentity(
    val id: Long,
    val sourceId: Long,
    val uri: String,
    val folder: String,
    val fileName: String,
    val sizeBytes: Long?,
)

/** A playlist as the Audio tab lists it, with the number of songs it shows. */
data class MusicPlaylistRow(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val songCount: Int,
    /** The first shown song, for the cover. */
    val coverSongId: Long?,
)

/**
 * The songs of playlists. Like videos, songs the library hides stay in their playlists but are
 * left out of what is counted and listed.
 */
@Dao
interface PlaylistSongDao {
    /**
     * Playlists with songs, and empty ones (just created, from either tab); a playlist with only
     * videos is the Video tab's. Most recently changed first.
     */
    @Query(
        "SELECT p.id AS id, p.name AS name, p.updatedAt AS updatedAt, " +
            "(SELECT COUNT(*) FROM playlist_songs ps JOIN songs s ON s.id = ps.songId " +
            "WHERE ps.playlistId = p.id AND s.present = 1 " +
            "AND s.sourceId IN (SELECT so.id FROM sources so WHERE so.enabled = 1)) AS songCount, " +
            "(SELECT ps.songId FROM playlist_songs ps JOIN songs s ON s.id = ps.songId " +
            "WHERE ps.playlistId = p.id AND s.present = 1 " +
            "AND s.sourceId IN (SELECT so.id FROM sources so WHERE so.enabled = 1) " +
            "ORDER BY ps.`position`, ps.id LIMIT 1) AS coverSongId " +
            "FROM playlists p WHERE EXISTS (SELECT 1 FROM playlist_songs ps WHERE ps.playlistId = p.id) " +
            "OR NOT EXISTS (SELECT 1 FROM playlist_items i WHERE i.playlistId = p.id) " +
            "ORDER BY p.updatedAt DESC, p.id DESC",
    )
    fun observePlaylists(): Flow<List<MusicPlaylistRow>>

    @Query(
        "SELECT s.* FROM playlist_songs ps JOIN songs s ON s.id = ps.songId " +
            "WHERE ps.playlistId = :playlistId AND s.present = 1 " +
            "AND s.sourceId IN (SELECT so.id FROM sources so WHERE so.enabled = 1) " +
            "ORDER BY ps.`position`, ps.id",
    )
    suspend fun shownSongs(playlistId: Long): List<SongEntity>

    /** Every item, shown or not, in playlist order. */
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY `position`, id")
    suspend fun items(playlistId: Long): List<PlaylistSongEntity>

    @Insert
    suspend fun insertItems(items: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)
}

@Dao
interface WatchlistDao {
    /** Most recently added first. */
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC, itemKey")
    fun observe(): Flow<List<WatchlistEntity>>

    /** Adding a saved item again keeps its first date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE itemKey = :itemKey")
    suspend fun remove(itemKey: String)

    @Query("SELECT COUNT(*) FROM watchlist WHERE itemKey = :itemKey")
    suspend fun count(itemKey: String): Int
}

@Dao
interface FavoriteSongDao {
    /** Starred song ids, most recently starred first. */
    @Query("SELECT songId FROM song_favorites ORDER BY addedAt DESC, songId")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: FavoriteSongEntity)

    @Query("DELETE FROM song_favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Query("SELECT COUNT(*) FROM song_favorites WHERE songId = :songId")
    suspend fun count(songId: Long): Int
}

@Dao
interface VideoFavoriteDao {
    /** Most recently starred first. */
    @Query("SELECT itemKey FROM video_favorites ORDER BY addedAt DESC, itemKey")
    fun observeKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: VideoFavoriteEntity)

    @Query("DELETE FROM video_favorites WHERE itemKey = :itemKey")
    suspend fun remove(itemKey: String)
}
