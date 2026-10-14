package com.aakash.novafork.data

import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import java.io.InputStream

/**
 * One video found by a scanner. The repository parses names and matches metadata afterwards,
 * unless the scanner hands over [serverMetadata].
 */
data class ScannedVideo(
    /**
     * The video's identity in the library (unique across sources) and what the player opens:
     * content:// (MediaStore/SAF), smb://host/share/… (see core SmbUris); for FTP, SFTP, NFS and
     * WebDAV the source's location plus the percent-encoded path below it (see [NetworkLocation]);
     * for UPnP and media servers the server's stream URL. It must stay the same from scan to scan
     * (watch history hangs on it), so it never carries a token or session: [PlaybackOptions] adds
     * those when the video is played.
     */
    val uri: String,
    val fileName: String,
    /**
     * Display folder path root → leaf, '/'-separated ("Movies/Action"; SMB: path inside the share).
     * The other network types give the path below the source's location ("" = its root); media
     * servers a path of their own ("Movies", "Shows/Severance/Season 1").
     */
    val folder: String,
    /** Parent folder names nearest-first, for NameParser ("Season 1", "The Office (2005)", …). */
    val parentFolders: List<String>,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Epoch ms; scanners use the file's own date when available, else now. */
    val dateAdded: Long,
    val dateModified: Long? = null,
    /** Local artwork URIs resolved with core ArtworkRules from the folder listing (null = none). */
    val localPoster: String? = null,
    val localBackdrop: String? = null,
    val localThumb: String? = null,
    /** The item's id on its server (Jellyfin/Emby item id, Plex ratingKey, UPnP object id); stored as [VideoEntity.remoteId]. */
    val remoteId: String? = null,
    /**
     * What a media server knows about the video. When set, the repository stores it as the video's
     * metadata (MATCHED, under [ServerMetadata.key]) and never asks TMDB about it; only the user's
     * Identify replaces it. Null: the file name is parsed and matched against TMDB as usual.
     */
    val serverMetadata: ServerMetadata? = null,
)

enum class ServerItemType { MOVIE, EPISODE }

/**
 * Metadata a media server supplies for one video ([ScannedVideo.serverMetadata]). For an episode
 * the main fields describe its series (shared by all its episodes) and the episode fields the
 * episode itself. Image URLs are full http(s) URLs Coil can load as they are.
 */
data class ServerMetadata(
    val type: ServerItemType,
    /**
     * The server's id of the movie, or of the series for an episode (Jellyfin/Emby item id, Plex
     * ratingKey): every episode of a series gives the same one, which groups them into one show.
     */
    val id: String,
    /** The movie's title, or the series name for an episode. Blank: the metadata is ignored. */
    val title: String,
    val originalTitle: String? = null,
    /** Release year (movie) or first-air year (series). */
    val year: Int? = null,
    /** Movie or series overview. */
    val overview: String? = null,
    val tagline: String? = null,
    /** Movie or series poster. */
    val posterUrl: String? = null,
    /** Movie or series backdrop. */
    val backdropUrl: String? = null,
    /** 0–10. */
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val runtimeMin: Int? = null,
    /**
     * Ids in other databases by lower-case provider name ("tmdb" → "603", "imdb" → "tt0133093",
     * "tvdb" → "…"). The "tmdb" one is stored as [com.aakash.novafork.data.db.MetadataEntity.tmdbId].
     */
    val providerIds: Map<String, String> = emptyMap(),

    // Episodes only
    val season: Int? = null,
    val episode: Int? = null,
    /** Last episode of a multi-episode file (S01E01E02 → 2). */
    val episodeEnd: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val episodeStillUrl: String? = null,
    /** "2022-02-18". */
    val episodeAirDate: String? = null,
    /** Poster of the episode's season; collected into the show's season posters. */
    val seasonPosterUrl: String? = null,
) {
    companion object {
        /** Every server key starts with this; TMDB keys never do. */
        const val KEY_PREFIX = "server:"

        /** The metadata key of what [metadata] describes on source [sourceId]: "server:7:movie:abc", "server:7:tv:def". */
        fun key(sourceId: Long, metadata: ServerMetadata): String =
            KEY_PREFIX + sourceId + (if (metadata.type == ServerItemType.MOVIE) ":movie:" else ":tv:") + metadata.id

        /** True for a key [key] made, i.e. metadata a media server supplied rather than TMDB. */
        fun isServerKey(key: String?): Boolean = key != null && key.startsWith(KEY_PREFIX)
    }
}

interface SourceScanner {
    /**
     * Walks [source] and returns every video in it. Runs on a background dispatcher, is cancellable,
     * skips core VideoFiles.isIgnoredFolder / isSample, never follows more than 12 folder levels.
     * Throws on a source-level failure (permission revoked, server unreachable, bad login).
     */
    suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit = {}): List<ScannedVideo>
}

data class SmbCredentials(val username: String?, val password: String?, val domain: String?)

data class SmbEntry(
    val name: String,
    /** Path inside the share, '/'-separated, no leading slash. */
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

interface SmbBrowser {
    /** Lists a directory. [path] is relative to the share root ("" = root). Hidden entries are skipped. */
    suspend fun list(host: String, port: Int?, share: String, path: String, credentials: SmbCredentials): List<SmbEntry>

    /** Connects, authenticates and opens the share; failure carries a human-readable message. */
    suspend fun test(host: String, port: Int?, share: String, path: String, credentials: SmbCredentials): Result<Unit>

    /**
     * Opens a file for reading (used by the Coil fetcher for poster.jpg on a share).
     * Credentials come from the source that owns host+share. Caller closes the stream.
     */
    suspend fun open(smbUri: String): SmbReadHandle
}

class SmbReadHandle(val stream: InputStream, val length: Long)

/**
 * One entry of a network folder listing: a folder or a file on FTP, SFTP, NFS, WebDAV or UPnP,
 * or a library, folder or item on a media server.
 */
data class RemoteEntry(
    val name: String,
    /**
     * What [NetworkBrowser.list] takes to open this entry when it is a directory. File servers use
     * the decoded path below the source's location, '/'-separated, no leading slash; UPnP and media
     * servers may use their own ids.
     */
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    /** Epoch ms. */
    val modifiedAt: Long? = null,
    /** For a playable file: the URI it is (or would be) stored under, as [ScannedVideo.uri]. Null for directories. */
    val playUri: String? = null,
    /** Poster or thumbnail URL the server offers, if any. */
    val imageUrl: String? = null,
)

/** Browsing and checking one family of network sources; registered per [SourceType] in AppGraph. */
interface NetworkBrowser {
    /**
     * Lists one directory of [source]: "" is its root (the location, or a media server's
     * libraries), anything else a [RemoteEntry.path] an earlier listing returned. Runs on a
     * background dispatcher, cancellable. Throws on failure, with a message fit for the user.
     */
    suspend fun list(source: SourceEntity, path: String): List<RemoteEntry>

    /**
     * Connects and signs in with what [source] holds, which may not be saved yet (id 0, built with
     * [NetworkSources.toSource] from what the user typed). The failure carries a message fit for the user.
     */
    suspend fun test(source: SourceEntity): Result<Unit>
}

/**
 * How the player opens a video of a network source without knowing its protocol: the URI to hand
 * libVLC and the media options to add. Registered per [SourceType] in AppGraph.
 */
interface PlaybackOptions {
    /**
     * libVLC media options for playing [uri] (a [VideoEntity.uri] of [source]), e.g.
     * ":ftp-user=…", ":ftp-pwd=…", ":sftp-user=…", ":http-user=…", ":http-pwd=…",
     * ":http-user-agent=…". Empty for none. Called off the main thread.
     */
    suspend fun mediaOptions(source: SourceEntity, uri: String): List<String>

    /**
     * The URI libVLC opens for [uri]: by default [uri] itself. Media servers add what must not be
     * stored with the video here (the access token, a playback session).
     */
    suspend fun playUri(source: SourceEntity, uri: String): String = uri
}

/** Tells a media server how far a video was watched, so its own apps and Continue Watching agree. */
interface RemoteProgressReporter {
    /**
     * Called after the library saved playback progress for [video] (savePlayback, about every ten
     * seconds while playing) or the user marked it watched or unwatched (setWatched). [video] is the
     * row as saved, so [VideoEntity.watched] holds the outcome; [finished] is true when this save
     * finished the video or marked it watched. Reports for one video arrive in order and may be
     * coalesced (only the latest of several pending ones is sent). Failures are logged and dropped.
     */
    suspend fun report(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?, finished: Boolean)

    companion object {
        /** Reports nothing: for sources whose server keeps no watch state. */
        val NONE: RemoteProgressReporter = object : RemoteProgressReporter {
            override suspend fun report(
                source: SourceEntity,
                video: VideoEntity,
                positionMs: Long,
                durationMs: Long?,
                finished: Boolean,
            ) = Unit
        }
    }
}

/**
 * What one family of network sources contributes to AppGraph: FTP/SFTP/NFS/UPnP/WebDAV come from
 * sources/net (FileServerSources), Jellyfin/Emby/Plex from sources/media (MediaServerSources).
 * A type missing from [scanners] is saved but never scanned.
 */
class SourceModule(
    val scanners: Map<SourceType, SourceScanner> = emptyMap(),
    val browsers: Map<SourceType, NetworkBrowser> = emptyMap(),
    val playbackOptions: Map<SourceType, PlaybackOptions> = emptyMap(),
    val progressReporters: Map<SourceType, RemoteProgressReporter> = emptyMap(),
)
