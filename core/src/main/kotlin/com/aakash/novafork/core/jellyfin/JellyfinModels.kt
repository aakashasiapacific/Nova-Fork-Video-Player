package com.aakash.novafork.core.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale

/** Jellyfin and Emby share one API family; these are the few places where they differ. */
enum class JellyfinFlavor(val label: String) {
    /** Jellyfin: API at the server's root, `Authorization: MediaBrowser …`. */
    JELLYFIN("Jellyfin"),

    /** Emby: API below /emby, `X-Emby-Authorization: Emby …` plus `X-Emby-Token`. */
    EMBY("Emby"),
}

/** Who is calling: sent in the authorization header of every request. */
data class JellyfinDevice(
    /** The device's name as the server's dashboard shows it ("Pixel 8"). */
    val device: String,
    /** Stable per install: the server keeps one session per device id and user. */
    val deviceId: String,
    /** The app's version. */
    val version: String,
    val client: String = "Nova Fork",
)

/** What signing in gave: the access token and the user it belongs to. */
data class JellyfinAuth(
    val token: String,
    val userId: String,
    /** The server's own id, when the server said. */
    val serverId: String? = null,
    val userName: String? = null,
) {
    // Never print the token.
    override fun toString(): String = "JellyfinAuth(userId=$userId, serverId=$serverId)"
}

/**
 * A Jellyfin or Emby request failed. [code] is the HTTP status, 0 when no answer came (or it made
 * no sense); 401 means the sign-in was refused. The message is fit for the user.
 */
class JellyfinException(val code: Int, message: String, cause: Throwable? = null) : IOException(message, cause)

/** GET /System/Info/Public: what a server says about itself before anyone signs in. */
@Serializable
data class JellyfinPublicInfo(
    @SerialName("Id") val id: String = "",
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    /** "Jellyfin Server", "Emby Server". */
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("LocalAddress") val localAddress: String? = null,
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("Policy") val policy: JellyfinUserPolicy? = null,
)

@Serializable
data class JellyfinUserPolicy(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false,
    @SerialName("IsDisabled") val isDisabled: Boolean = false,
)

/** POST /Users/AuthenticateByName. */
@Serializable
internal data class JellyfinAuthResult(
    @SerialName("User") val user: JellyfinUser? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)

@Serializable
internal data class JellyfinAuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

/** One page of /Users/{id}/Items or /Users/{id}/Views. */
@Serializable
data class JellyfinItemsPage(
    @SerialName("Items") val items: List<JellyfinItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)

/** A library, folder, series, season, movie, episode or video (BaseItemDto); only what Nova Fork reads. */
@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    /** "Movie", "Episode", "Video", "Series", "Season", "CollectionFolder", "Folder", "BoxSet"… */
    @SerialName("Type") val type: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    /** Libraries: "movies", "tvshows", "homevideos", "music", "boxsets"…; null for mixed content. */
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("ProductionYear") val productionYear: Int? = null,
    /** ISO 8601, "2022-02-18T00:00:00.0000000Z". */
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    /** 1 tick = 100 ns. */
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    /** "Tmdb" → "603", "Imdb" → "tt0133093", "Tvdb" → … (see [providers]). */
    @SerialName("ProviderIds") val providerIds: Map<String, String?> = emptyMap(),
    /** The file on the server's disk. */
    @SerialName("Path") val path: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    /** "FileSystem", "Remote", "Virtual" (a missing episode). */
    @SerialName("LocationType") val locationType: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSource> = emptyList(),
    /** Image type ("Primary", "Thumb", "Logo") → tag. */
    @SerialName("ImageTags") val imageTags: Map<String, String?> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    /** Season number of an episode. */
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    /** Episode number (or season number of a season). */
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("IndexNumberEnd") val indexNumberEnd: Int? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("UserData") val userData: JellyfinUserData? = null,
) {
    val isMissing: Boolean get() = locationType.equals("Virtual", ignoreCase = true)

    /** [providerIds] with lower-case names and without blank ids: "tmdb" → "603". */
    val providers: Map<String, String>
        get() = providerIds.entries
            .mapNotNull { (name, id) ->
                val value = id?.trim()
                if (name.isBlank() || value.isNullOrEmpty()) null else name.trim().lowercase(Locale.ROOT) to value
            }
            .toMap()

    /** The tag of the image of [type] ("Primary", "Thumb"), null when there is none. */
    fun imageTag(type: String): String? =
        imageTags.entries.firstOrNull { it.key.equals(type, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }

    val durationMs: Long? get() = JellyfinClient.ticksToMs(mediaSources.firstOrNull()?.runTimeTicks ?: runTimeTicks)
}

@Serializable
data class JellyfinMediaSource(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Path") val path: String? = null,
    /** "File", "Http"… */
    @SerialName("Protocol") val protocol: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStream> = emptyList(),
) {
    /** The first video stream. */
    val video: JellyfinMediaStream? get() = mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
}

@Serializable
data class JellyfinMediaStream(
    /** "Video", "Audio", "Subtitle". */
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Language") val language: String? = null,
)

@Serializable
data class JellyfinUserData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("Played") val played: Boolean = false,
)

/** Body of /Sessions/Playing, /Sessions/Playing/Progress and /Sessions/Playing/Stopped. */
@Serializable
internal data class JellyfinPlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("PlayMethod") val playMethod: String = "DirectPlay",
    @SerialName("EventName") val eventName: String? = null,
)

/** Which items a listing returns. */
data class JellyfinQuery(
    val parentId: String? = null,
    val recursive: Boolean = false,
    /** "Movie", "Episode", "Series"…; empty = every type. */
    val includeItemTypes: List<String> = emptyList(),
    val fields: List<String> = JellyfinClient.ITEM_FIELDS,
    val sortBy: List<String> = listOf("SortName"),
    /** Leaves out missing episodes (IsMissing=false). */
    val excludeMissing: Boolean = true,
)

/** Jellyfin and Emby write dates as ISO 8601 with up to 7 fraction digits, "Z" or an offset. */
object JellyfinDates {
    /** Epoch ms, or null for a missing, unreadable or placeholder ("0001-01-01…") date. */
    fun epochMillis(text: String?): Long? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val millis = try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (e2: DateTimeParseException) {
                try {
                    // Some servers leave the zone out; their dates are UTC.
                    LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (e3: DateTimeParseException) {
                    return null
                }
            }
        }
        // .NET writes DateTime.MinValue for "unknown".
        return millis.takeIf { it > 0 }
    }

    /** The date part ("2022-02-18") of an ISO 8601 date-time, or null. */
    fun day(text: String?): String? {
        val value = text?.trim() ?: return null
        if (value.length < 10 || value.startsWith("0001-")) return null
        val day = value.substring(0, 10)
        return day.takeIf { DAY.matches(it) }
    }

    private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")
}
