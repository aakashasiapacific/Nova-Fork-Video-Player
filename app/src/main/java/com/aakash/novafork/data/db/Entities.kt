package com.aakash.novafork.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Stored by name: new types go at the end, and none is ever renamed. Every network type keeps its
 * base URI in [SourceEntity.location] (see there).
 */
enum class SourceType {
    DEVICE,
    FOLDER,
    SMB,
    STREAM,
    FTP,
    SFTP,
    NFS,
    UPNP,
    WEBDAV,
    JELLYFIN,
    EMBY,
    PLEX,
    ;

    /** Jellyfin, Emby and Plex: the server has its own library, metadata and watch state. */
    val isMediaServer: Boolean
        get() = this == JELLYFIN || this == EMBY || this == PLEX

    /** SMB, FTP, SFTP, NFS, WebDAV and UPnP: servers browsed as folders of files. */
    val isNetworkShare: Boolean
        get() = this == SMB || this == FTP || this == SFTP || this == NFS || this == WEBDAV || this == UPNP
}

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
 *
 * The other network types (schema version 3) keep a percent-encoded base URI without credentials
 * in [location], as [com.aakash.novafork.data.NetworkSources] normalises it:
 * FTP      – "ftp://host[:port]/path" (also ftps:// and ftpes://);
 * SFTP     – "sftp://host[:port]/path";
 * NFS      – "nfs://host/export/path";
 * UPNP     – the device description URL ("http://192.168.1.5:49152/rootDesc.xml") or its UDN ("uuid:…");
 * WEBDAV   – "https://dav.example.com/path" (or http://);
 * JELLYFIN, EMBY, PLEX – the server's base URL ("http://jellyfin.local:8096", "https://example.com/jellyfin").
 * Secrets stay in the columns below, as for SMB: [username]/[password] for logins, [token] for the
 * access token a media server hands out.
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
    /** The port in [location]; null for the scheme's default. Kept in step with [location]. */
    val port: Int? = null,
    /** Jellyfin/Emby access token, Plex X-Plex-Token. Null until signed in. */
    val token: String? = null,
    /** Jellyfin/Emby user id the token belongs to. */
    val userId: String? = null,
    /** The server's own id (Jellyfin/Emby ServerId, Plex machineIdentifier, UPnP UDN). */
    val serverId: String? = null,
    /** Comma-separated ids of the server libraries to scan; null = all of them. */
    val libraryIds: String? = null,
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
    /** content://…, file://…, smb://…, ftp://…, sftp://…, nfs://…, http(s)://…, rtsp://… */
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

    // Matching (TMDB, or the media server the video comes from)
    /**
     * "movie:603" for movies, "tv:1396" for episodes (the show), null when unmatched. Videos a
     * media server described carry its key instead ("server:<sourceId>:movie:<id>"), MATCHED.
     */
    val metadataKey: String? = null,
    /** Episodes only: grouping key (see core LibraryKeys.showKey; a server show's metadataKey). */
    val showKey: String? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    /** TMDB still path ("/abc.jpg"), or a full http(s) URL from a media server. */
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

    /**
     * The item's id on its server (schema version 3): Jellyfin/Emby item id, Plex ratingKey, UPnP
     * object id. Null for files, whose URI is all there is.
     */
    val remoteId: String? = null,
)

/**
 * One row per matched TMDB movie or show, or per movie or show a media server described
 * (key "server:<sourceId>:movie:<id>" / "server:<sourceId>:tv:<id>", see
 * [com.aakash.novafork.data.ServerMetadata.key]).
 */
@Entity(tableName = "metadata")
data class MetadataEntity(
    /** "movie:603" or "tv:1396" (core LibraryKeys), or a server key. */
    @PrimaryKey val key: String,
    /** The TMDB id; for a server row the one the server knows, else 0. */
    val tmdbId: Int,
    /** "movie" or "tv". */
    val type: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val tagline: String? = null,
    /**
     * TMDB paths ("/xyz.jpg"), turned into URLs with TmdbClient.posterUrl/backdropUrl; a server
     * row holds full http(s) URLs, which those return unchanged.
     */
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    /** Comma-separated genre names. */
    val genres: String = "",
    val runtimeMin: Int? = null,
    /** Shows only: JSON object season number → poster path (or URL), e.g. {"1":"/a.jpg","2":"/b.jpg"}. */
    val seasonPostersJson: String? = null,
    val updatedAt: Long = 0,
    /** TMDB's best poster without text (schema version 5), for the tall Home hero; null = none. */
    val textlessPosterPath: String? = null,
    /**
     * Top-billed cast, comma-separated (version 5), for search. Null = not fetched yet (rows from
     * before version 5 are fetched again); "" = TMDB lists none.
     */
    val castNames: String? = null,
    /** Directors (movies) or creators (shows), comma-separated (version 5), for search. */
    val directors: String? = null,
    /**
     * TMDB's title logo (version 6): the title drawn as transparent artwork, shown over the hero
     * in place of plain text. Null = none.
     */
    val logoPath: String? = null,
)

/**
 * A poster or backdrop the user picked for a movie or show in "Change artwork" (schema version 5).
 * Kept apart from [MetadataEntity] so refreshing the metadata never undoes the choice. Paths are
 * TMDB paths ("/abc.jpg"); null = TMDB's default.
 */
@Entity(tableName = "artwork_choices")
data class ArtworkChoiceEntity(
    /** [MetadataEntity.key]. */
    @PrimaryKey val metadataKey: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
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

/** A user playlist (schema version 2). Its videos are [PlaylistItemEntity] rows. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Last change to the name or the items; epoch ms. */
    val updatedAt: Long,
)

/**
 * One video in a playlist; a video is in a playlist at most once. [position] orders the items
 * (0, 1, 2, … as the repository writes them; deleting videos with their source can leave gaps).
 * Deleting the playlist or the video's row deletes the item; a video that is only missing from
 * its folder (present = false) keeps it.
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["videoId"]),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val videoId: Long,
    val position: Int,
)

data class VideoWithMeta(
    @Embedded val video: VideoEntity,
    @Relation(parentColumn = "metadataKey", entityColumn = "key")
    val meta: MetadataEntity?,
)
