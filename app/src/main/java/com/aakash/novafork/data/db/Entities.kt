package com.aakash.novafork.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class SourceType { DEVICE, FOLDER, SMB, STREAM }

/** What a video is, after parsing and matching. VIDEO = home video / unidentified. */
enum class VideoKind { MOVIE, EPISODE, VIDEO }

enum class MatchState {
    /** Not looked up yet (or reset). */
    PENDING,
    /** Matched automatically. */
    MATCHED,
    /** Looked up, nothing convincing. Retried after a week or when the key/settings change. */
    UNMATCHED,
    /** Picked by the user in "Identify"; never overwritten by automatic matching. */
    MANUAL,
}

/**
 * A place videos come from.
 * DEVICE – one row, location "device", scanned through MediaStore.
 * FOLDER – a SAF tree; location is the tree URI (persisted permission).
 * SMB    – location is "smb://host[:port]/share/optional/path" (see core SmbUris); credentials below.
 * STREAM – one row per saved network stream; location is the stream URL (it also gets one VideoEntity).
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SourceType,
    val name: String,
    val location: String,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    val enabled: Boolean = true,
    val addedAt: Long = 0,
    val lastScanAt: Long? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "videos",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["metadataKey"]),
        Index(value = ["showKey"]),
    ],
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    /** content://…, file://…, smb://…, http(s)://…, rtsp://… */
    val uri: String,
    val fileName: String,
    /** Human folder path for display and folder browsing, root → leaf, '/'-separated, e.g. "Movies/Action". */
    val folder: String,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Epoch ms when the file first showed up (MediaStore DATE_ADDED / first scan time). */
    val dateAdded: Long = 0,
    val dateModified: Long? = null,

    // Parsed from the name (core NameParser)
    val kind: VideoKind = VideoKind.VIDEO,
    val parsedTitle: String,
    val parsedYear: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeEnd: Int? = null,

    // Matching (TMDB)
    /** "movie:603" for movies, "tv:1396" for episodes (the show), null when unmatched. */
    val metadataKey: String? = null,
    /** Episodes only: grouping key (see core LibraryKeys.showKey). */
    val showKey: String? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    /** TMDB still path ("/abc.jpg"), not a full URL. */
    val episodeStillPath: String? = null,
    val episodeAirDate: String? = null,
    val matchState: MatchState = MatchState.PENDING,
    val matchAttemptAt: Long? = null,

    // Local artwork found next to the file (full URIs: content://, file://, smb://)
    val localPoster: String? = null,
    val localBackdrop: String? = null,
    val localThumb: String? = null,

    // Playback
    val positionMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val watched: Boolean = false,
    val playCount: Int = 0,
    /** libVLC track ids remembered per file (null = default). */
    val audioTrackId: Int? = null,
    val subtitleTrackId: Int? = null,

    /** False when the last scan of its source did not see the file (kept for watch history). */
    val present: Boolean = true,
)

/** One row per matched TMDB movie or show. */
@Entity(tableName = "metadata")
data class MetadataEntity(
    /** "movie:603" or "tv:1396" (core LibraryKeys). */
    @PrimaryKey val key: String,
    val tmdbId: Int,
    /** "movie" or "tv". */
    val type: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val tagline: String? = null,
    /** TMDB paths ("/xyz.jpg"), turned into URLs with TmdbClient.posterUrl/backdropUrl. */
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    /** Comma-separated genre names. */
    val genres: String = "",
    val runtimeMin: Int? = null,
    /** Shows only: JSON object season number → poster path, e.g. {"1":"/a.jpg","2":"/b.jpg"}. */
    val seasonPostersJson: String? = null,
    val updatedAt: Long = 0,
)

/** Remembers TMDB lookups by normalised query so every episode of a show costs one search. */
@Entity(tableName = "match_cache")
data class MatchCacheEntity(
    /** core LibraryKeys.matchQueryKey(type, title, year) */
    @PrimaryKey val query: String,
    /** Null = looked up, no match. */
    val metadataKey: String?,
    val checkedAt: Long,
)

data class VideoWithMeta(
    @Embedded val video: VideoEntity,
    @Relation(parentColumn = "metadataKey", entityColumn = "key")
    val meta: MetadataEntity?,
)
