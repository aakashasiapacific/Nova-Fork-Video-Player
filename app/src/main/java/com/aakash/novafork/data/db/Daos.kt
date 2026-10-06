package com.aakash.novafork.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** SQLite allows 999 bound arguments per statement on older Android versions; stay well below. */
const val SQL_CHUNK = 900

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY type, name COLLATE NOCASE, id")
    fun observeEnabled(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY type, id")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: Long): SourceEntity?

    /** [type] is a [SourceType] name. */
    @Query("SELECT * FROM sources WHERE type = :type AND location = :location ORDER BY id LIMIT 1")
    suspend fun byLocation(type: String, location: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE type = 'DEVICE' ORDER BY id LIMIT 1")
    suspend fun device(): SourceEntity?

    @Query("SELECT * FROM sources WHERE type = 'SMB' ORDER BY id")
    suspend fun smbSources(): List<SourceEntity>

    @Insert
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE sources SET lastScanAt = :scannedAt, lastError = NULL WHERE id = :id")
    suspend fun markScanned(id: Long, scannedAt: Long)

    @Query("UPDATE sources SET lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VideoDao {
    @Transaction
    @Query(
        "SELECT * FROM videos WHERE present = 1 AND sourceId IN (SELECT id FROM sources WHERE enabled = 1) " +
            "ORDER BY dateAdded DESC, id DESC",
    )
    fun observePresent(): Flow<List<VideoWithMeta>>

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun withMeta(id: Long): VideoWithMeta?

    @Transaction
    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun withMetaByUri(uri: String): VideoWithMeta?

    @Transaction
    @Query("SELECT * FROM videos WHERE showKey = :showKey AND present = 1")
    suspend fun episodesOf(showKey: String): List<VideoWithMeta>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun byId(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun byUri(uri: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE sourceId = :sourceId")
    suspend fun bySource(sourceId: Long): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE metadataKey = :metadataKey")
    suspend fun byMetadataKey(metadataKey: String): List<VideoEntity>

    /** Movies and episodes to look up: never tried, or tried without success before [retryBefore]. */
    @Query(
        "SELECT * FROM videos WHERE present = 1 AND kind IN ('MOVIE', 'EPISODE') AND " +
            "(matchState = 'PENDING' OR (matchState = 'UNMATCHED' AND (matchAttemptAt IS NULL OR matchAttemptAt < :retryBefore)))",
    )
    suspend fun needingMatch(retryBefore: Long): List<VideoEntity>

    /** Keys that videos point at but whose metadata row is missing (cleared, or a failed write). */
    @Query(
        "SELECT DISTINCT metadataKey FROM videos WHERE metadataKey IS NOT NULL " +
            "AND metadataKey NOT IN (SELECT `key` FROM metadata)",
    )
    suspend fun orphanMetadataKeys(): List<String>

    @Query("SELECT DISTINCT parsedTitle FROM videos WHERE kind = 'EPISODE' AND matchState != 'MANUAL'")
    suspend fun automaticEpisodeTitles(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(videos: List<VideoEntity>): List<Long>

    @Update
    suspend fun update(video: VideoEntity)

    @Update
    suspend fun updateAll(videos: List<VideoEntity>)

    /** At most [SQL_CHUNK] ids per call. */
    @Query("UPDATE videos SET present = 0 WHERE id IN (:ids)")
    suspend fun markMissing(ids: List<Long>)

    @Query("DELETE FROM videos WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    // Playback

    @Query("UPDATE videos SET positionMs = :positionMs, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long, playedAt: Long)

    /**
     * Marks a video finished. The play count only grows when this finishes a viewing in progress,
     * so the player saving the end position twice does not count two plays.
     */
    @Query(
        "UPDATE videos SET playCount = playCount + (CASE WHEN watched = 1 AND positionMs = 0 THEN 0 ELSE 1 END), " +
            "watched = 1, positionMs = 0, lastPlayedAt = :playedAt WHERE id = :id",
    )
    suspend fun markFinished(id: Long, playedAt: Long)

    @Query("UPDATE videos SET durationMs = :durationMs WHERE id = :id AND (durationMs IS NULL OR durationMs = 0)")
    suspend fun fillDuration(id: Long, durationMs: Long)

    @Query("UPDATE videos SET watched = :watched, positionMs = 0 WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean)

    @Query("UPDATE videos SET audioTrackId = :audioTrackId, subtitleTrackId = :subtitleTrackId WHERE id = :id")
    suspend fun saveTracks(id: Long, audioTrackId: Int?, subtitleTrackId: Int?)

    // Automatic matching. These only touch rows still waiting for a match, so a manual match or a
    // rename made while a lookup was in flight is never overwritten.

    /** At most [SQL_CHUNK] ids per call. */
    @Query(
        "UPDATE videos SET metadataKey = :metadataKey, matchState = 'MATCHED', matchAttemptAt = :attemptAt " +
            "WHERE id IN (:ids) AND matchState IN ('PENDING', 'UNMATCHED')",
    )
    suspend fun setMovieMatch(ids: List<Long>, metadataKey: String, attemptAt: Long)

    @Query(
        "UPDATE videos SET metadataKey = :metadataKey, showKey = :metadataKey, season = :season, episode = :episode, " +
            "episodeTitle = :episodeTitle, episodeOverview = :episodeOverview, episodeStillPath = :episodeStillPath, " +
            "episodeAirDate = :episodeAirDate, matchState = 'MATCHED', matchAttemptAt = :attemptAt " +
            "WHERE id = :id AND matchState IN ('PENDING', 'UNMATCHED')",
    )
    suspend fun setEpisodeMatch(
        id: Long,
        metadataKey: String,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        episodeOverview: String?,
        episodeStillPath: String?,
        episodeAirDate: String?,
        attemptAt: Long,
    )

    /** At most [SQL_CHUNK] ids per call. */
    @Query(
        "UPDATE videos SET matchState = 'UNMATCHED', matchAttemptAt = :attemptAt " +
            "WHERE id IN (:ids) AND matchState IN ('PENDING', 'UNMATCHED')",
    )
    suspend fun setUnmatched(ids: List<Long>, attemptAt: Long)

    /** Refreshes TMDB episode data without touching the match itself (used for manual matches too). */
    @Query(
        "UPDATE videos SET episodeTitle = :episodeTitle, episodeOverview = :episodeOverview, " +
            "episodeStillPath = :episodeStillPath, episodeAirDate = :episodeAirDate WHERE id = :id",
    )
    suspend fun setEpisodeDetails(
        id: Long,
        episodeTitle: String?,
        episodeOverview: String?,
        episodeStillPath: String?,
        episodeAirDate: String?,
    )

    @Query("UPDATE videos SET matchState = 'PENDING' WHERE matchState = 'UNMATCHED' AND kind != 'VIDEO'")
    suspend fun resetUnmatched()

    /** Forgets every automatic match. Episode show keys are reset separately ([setShowKeyForTitle]). */
    @Query(
        "UPDATE videos SET matchState = 'PENDING', matchAttemptAt = NULL, metadataKey = NULL, episodeTitle = NULL, " +
            "episodeOverview = NULL, episodeStillPath = NULL, episodeAirDate = NULL " +
            "WHERE kind IN ('MOVIE', 'EPISODE') AND matchState != 'MANUAL'",
    )
    suspend fun resetAutomaticMatches()

    @Query("UPDATE videos SET showKey = :showKey WHERE kind = 'EPISODE' AND matchState != 'MANUAL' AND parsedTitle = :parsedTitle")
    suspend fun setShowKeyForTitle(parsedTitle: String, showKey: String)
}

@Dao
interface MetadataDao {
    @Query("SELECT * FROM metadata WHERE `key` = :key")
    suspend fun get(key: String): MetadataEntity?

    @Upsert
    suspend fun upsert(metadata: MetadataEntity)

    @Query("DELETE FROM metadata")
    suspend fun clear()

    @Query("DELETE FROM metadata WHERE `key` NOT IN (SELECT metadataKey FROM videos WHERE metadataKey IS NOT NULL)")
    suspend fun deleteUnreferenced()
}

@Dao
interface MatchCacheDao {
    @Query("SELECT * FROM match_cache WHERE `query` = :query")
    suspend fun get(query: String): MatchCacheEntity?

    @Upsert
    suspend fun put(entry: MatchCacheEntity)

    @Query("DELETE FROM match_cache")
    suspend fun clear()

    @Query("DELETE FROM match_cache WHERE metadataKey IS NULL")
    suspend fun clearMisses()
}
