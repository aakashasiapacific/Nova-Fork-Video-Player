package com.aakash.novafork.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** What the "format" field of every backup file says. */
const val BACKUP_FORMAT = "nova-fork-backup"

/**
 * The newest backup format this app reads and the one it writes. A change older apps would get
 * wrong bumps it; new optional fields do not (unknown fields are ignored when reading).
 */
const val BACKUP_FORMAT_VERSION = 1

/**
 * A backup file: settings, sources, the user's state per video, manual matches and playlists.
 * Videos are referred to by [BackupVideoRef], which a restore matches against the library.
 */
@Serializable
data class NovaBackup(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    /** versionName of the app that wrote it. */
    val appVersion: String = "",
    /** Epoch ms. */
    val createdAt: Long = 0,
    /** True when passwords, sign-in tokens and API keys are in it, in plain text. */
    val includesSecrets: Boolean = false,
    val settings: List<BackupSetting> = emptyList(),
    val sources: List<BackupSource> = emptyList(),
    val videos: List<BackupVideoState> = emptyList(),
    val matches: List<BackupMatch> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
) {
    fun summary(): BackupSummary = BackupSummary(
        createdAt = createdAt,
        appVersion = appVersion,
        includesSecrets = includesSecrets,
        settings = settings.size,
        sources = sources.size,
        videos = videos.size,
        matches = matches.size,
        playlists = playlists.size,
        playlistItems = playlists.sumOf { it.items.size },
    )
}

/** The type a SharedPreferences value had; it is written back with the same type. */
@Serializable
enum class SettingType {
    @SerialName("string")
    STRING,

    @SerialName("boolean")
    BOOLEAN,

    @SerialName("int")
    INT,

    @SerialName("long")
    LONG,

    @SerialName("float")
    FLOAT,

    @SerialName("string_set")
    STRING_SET,
}

/** One entry of the settings file: a JSON string, boolean or number, or an array of strings. */
@Serializable
data class BackupSetting(val key: String, val type: SettingType, val value: JsonElement)

/** A source as the user set it up. [password], [token] and [userId] only travel in a backup with secrets. */
@Serializable
data class BackupSource(
    /** A SourceType name. */
    val type: String,
    val name: String,
    val location: String,
    val port: Int? = null,
    val username: String? = null,
    val domain: String? = null,
    val enabled: Boolean = true,
    val serverId: String? = null,
    /** Comma-separated server library ids; null = all of them. */
    val libraryIds: String? = null,
    val password: String? = null,
    val token: String? = null,
    val userId: String? = null,
)

/**
 * Which video an entry is about. A restore looks for it by [uri] first, then by its place in its
 * source ([sourceType] + [sourceLocation], [folder], [fileName]), then by [fileName] + [sizeBytes].
 */
@Serializable
data class BackupVideoRef(
    val uri: String,
    /** The SourceType name of the source the video belonged to. */
    val sourceType: String? = null,
    val sourceLocation: String? = null,
    /** VideoEntity.folder: root → leaf, '/'-separated. */
    val folder: String = "",
    val fileName: String,
    val sizeBytes: Long? = null,
)

/** A video's playback state: resume point, watched, plays and the tracks picked for it. */
@Serializable
data class BackupVideoState(
    val video: BackupVideoRef,
    val positionMs: Long = 0,
    val watched: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val audioTrackId: Int? = null,
    val subtitleTrackId: Int? = null,
)

/** A match the user picked in Identify. [season]/[episode] are the numbers it had (episodes only). */
@Serializable
data class BackupMatch(
    val video: BackupVideoRef,
    val tmdbId: Int,
    /** "movie" or "tv". */
    val kind: String,
    val season: Int? = null,
    val episode: Int? = null,
)

/** A playlist and its videos in playlist order (hidden ones included). */
@Serializable
data class BackupPlaylist(
    val name: String,
    val createdAt: Long = 0,
    val items: List<BackupVideoRef> = emptyList(),
)

/** What a backup holds, as counts: shown before a restore and after a backup. */
data class BackupSummary(
    val createdAt: Long,
    val appVersion: String,
    val includesSecrets: Boolean,
    val settings: Int,
    val sources: Int,
    val videos: Int,
    val matches: Int,
    val playlists: Int,
    val playlistItems: Int,
)

/** Entries of a restore: video states, manual matches and playlist items. */
data class RestoreCounts(val videos: Int = 0, val matches: Int = 0, val playlistItems: Int = 0) {
    val total: Int get() = videos + matches + playlistItems

    operator fun plus(other: RestoreCounts): RestoreCounts =
        RestoreCounts(videos + other.videos, matches + other.matches, playlistItems + other.playlistItems)
}

/** One pass over the waiting entries of a restore ([PendingRestore.apply]). */
data class PendingApplyResult(
    /** Entries that reached their video in this pass (or found it already as the backup had it). */
    val applied: RestoreCounts = RestoreCounts(),
    /** Entries still waiting for their video. */
    val waiting: RestoreCounts = RestoreCounts(),
    /** Manual matches whose video is there, left for a pass that may ask TMDB. */
    val matchesReady: Int = 0,
    /** Manual matches TMDB no longer knows; dropped. */
    val matchesFailed: Int = 0,
)

/** What [BackupManager.restore] did. */
data class RestoreReport(
    /** Settings entries written. */
    val settings: Int,
    val sourcesAdded: Int,
    /** Sources already here (same type and location): left as they are. */
    val sourcesExisting: Int,
    /** Sources of a type this version doesn't know, without a location, or that couldn't be added. */
    val sourcesSkipped: Int,
    /** Names of picked folders this device has no access to: they have to be picked again. */
    val foldersToPick: List<String>,
    /** Ids of the sources added that need a scan. */
    val addedSourceIds: List<Long>,
    val playlistsCreated: Int,
    val applied: RestoreCounts,
    /** Entries waiting for their videos (sources still to scan, files on another device). */
    val waiting: RestoreCounts,
    val matchesFailed: Int,
)

/** A file that is not a backup this app can read; [message] says why, for people. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
