package com.aakash.novafork.core.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.Locale

/** Identifies this app to Plex: the X-Plex-* headers of every request. */
data class PlexClientInfo(
    /** Stable per install (X-Plex-Client-Identifier): Plex ties PINs and sessions to it. */
    val clientIdentifier: String,
    /** The app's version. */
    val version: String,
    /** The device's model ("Pixel 8"). */
    val device: String,
    /** What the server's dashboard calls this device. */
    val deviceName: String = device,
    val product: String = "Nova Fork",
    val platform: String = "Android",
    val platformVersion: String? = null,
)

/**
 * A Plex server or plex.tv request failed. [code] is the HTTP status, 0 when no answer came (or it
 * made no sense); 401 means the token or login was refused. The message is fit for the user.
 */
class PlexException(val code: Int, message: String, cause: Throwable? = null) : IOException(message, cause)

/** What the timeline tells the server about the player. */
enum class PlexPlaybackState(val value: String) {
    PLAYING("playing"),
    PAUSED("paused"),
    STOPPED("stopped"),
}

/** Every Plex answer is wrapped in a MediaContainer. */
@Serializable
internal data class PlexResponse(
    @SerialName("MediaContainer") val container: PlexMediaContainer = PlexMediaContainer(),
)

@Serializable
data class PlexMediaContainer(
    /** Entries in this answer. */
    val size: Int? = null,
    /** Entries in the whole listing, when it was paged. */
    val totalSize: Int? = null,
    val offset: Int? = null,
    val machineIdentifier: String? = null,
    val friendlyName: String? = null,
    val version: String? = null,
    val title1: String? = null,
    val title2: String? = null,
    @SerialName("Directory") val directories: List<PlexDirectory> = emptyList(),
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
)

/** A library section (GET /library/sections) or another folder-like entry. */
@Serializable
data class PlexDirectory(
    /** A section's key ("1"), or a path ("/library/metadata/7/allLeaves"). */
    val key: String = "",
    val title: String? = null,
    /** Sections: "movie", "show", "artist", "photo". */
    val type: String? = null,
    /** "tv.plex.agents.movie", "com.plexapp.agents.none" (personal media)… */
    val agent: String? = null,
    val thumb: String? = null,
    val art: String? = null,
    val uuid: String? = null,
) {
    /** Movies or TV shows: the sections Nova Fork scans. */
    val isVideoSection: Boolean get() = type == TYPE_MOVIE || type == TYPE_SHOW

    /** "Other videos": personal media without an online agent, so its titles are plain file names. */
    val isPersonalMedia: Boolean get() = agent.orEmpty().lowercase(Locale.ROOT).endsWith(".none")

    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_SHOW = "show"
    }
}

/** A movie, show, season, episode or clip; only what Nova Fork reads. */
@Serializable
data class PlexMetadata(
    val ratingKey: String = "",
    /** "/library/metadata/7" for a playable item, "/library/metadata/7/children" for a show or season. */
    val key: String? = null,
    /** "movie", "show", "season", "episode", "clip". */
    val type: String? = null,
    val title: String? = null,
    val originalTitle: String? = null,
    val titleSort: String? = null,
    val summary: String? = null,
    val tagline: String? = null,
    val year: Int? = null,
    /** Server paths ("/library/metadata/7/thumb/1700000000"), see [PlexServerClient.imageUrl]. */
    val thumb: String? = null,
    val art: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    /** Critic rating, 0–10. */
    val rating: Double? = null,
    /** Audience rating, 0–10. */
    val audienceRating: Double? = null,
    /** Milliseconds. */
    val duration: Long? = null,
    /** Epoch seconds. */
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    /** "2022-02-18". */
    val originallyAvailableAt: String? = null,
    /** Episode number (season number for a season). */
    val index: Int? = null,
    /** Season number of an episode. */
    val parentIndex: Int? = null,
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    val grandparentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val leafCount: Int? = null,
    val childCount: Int? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val librarySectionTitle: String? = null,
    val librarySectionID: String? = null,
    /** "plex://movie/…" or a legacy agent's "com.plexapp.agents.imdb://tt0133093?lang=en". */
    val guid: String? = null,
    /** "imdb://tt0133093", "tmdb://603", "tvdb://…" (asked for with includeGuids=1). */
    @SerialName("Guid") val guids: List<PlexTag> = emptyList(),
    @SerialName("Genre") val genres: List<PlexTag> = emptyList(),
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
) {
    /** Ids in other databases: "imdb", "tmdb", "tvdb". */
    val providers: Map<String, String> get() = PlexGuids.providerIds(listOfNotNull(guid) + guids.mapNotNull { it.id })

    /** The version that plays: the first with a file. */
    val playableMedia: PlexMedia? get() = media.firstOrNull { it.part != null }

    /** The file that plays: the first part of [playableMedia]. */
    val part: PlexPart? get() = playableMedia?.part
}

@Serializable
data class PlexTag(
    val id: String? = null,
    val tag: String? = null,
)

/** One version of an item. */
@Serializable
data class PlexMedia(
    val id: String? = null,
    val duration: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val container: String? = null,
    val videoResolution: String? = null,
    @SerialName("Part") val parts: List<PlexPart> = emptyList(),
) {
    /** The first part with a key (later parts of a stacked "cd1/cd2" movie don't play on their own). */
    val part: PlexPart? get() = parts.firstOrNull { !it.key.isNullOrBlank() }
}

/** A file of a version. */
@Serializable
data class PlexPart(
    val id: String? = null,
    /** "/library/parts/123/1700000000/file.mkv", see [PlexServerClient.partUrl]. */
    val key: String? = null,
    /** The path on the server's disk. */
    val file: String? = null,
    val size: Long? = null,
    val duration: Long? = null,
    val container: String? = null,
)

/** POST /api/v2/pins on plex.tv; [authToken] is set once someone entered [code]. */
@Serializable
data class PlexPin(
    val id: Long = 0,
    val code: String = "",
    val authToken: String? = null,
    /** Seconds the code stays valid. */
    val expiresIn: Int? = null,
    val expiresAt: String? = null,
    val clientIdentifier: String? = null,
) {
    // Never print the token.
    override fun toString(): String = "PlexPin(id=$id, code=$code, linked=${authToken != null})"
}

/** A device of the account on plex.tv (GET /api/v2/resources). */
@Serializable
data class PlexResource(
    val name: String? = null,
    val product: String? = null,
    val productVersion: String? = null,
    /** A server's machineIdentifier. */
    val clientIdentifier: String = "",
    /** "server", "player,client"… */
    val provides: String? = null,
    val owned: Boolean = false,
    /** The token for this server: the account's own for a server it owns, another one for a shared server. */
    val accessToken: String? = null,
    val presence: Boolean = false,
    val connections: List<PlexConnection> = emptyList(),
) {
    val isServer: Boolean get() = provides.orEmpty().split(',').any { it.trim().equals("server", ignoreCase = true) }

    // Never print the token.
    override fun toString(): String = "PlexResource(name=$name, clientIdentifier=$clientIdentifier, owned=$owned)"
}

@Serializable
data class PlexConnection(
    /** "https://192-168-1-5.abc.plex.direct:32400", "http://192.168.1.5:32400". */
    val uri: String = "",
    val protocol: String? = null,
    val address: String? = null,
    val port: Int? = null,
    val local: Boolean = false,
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false,
)

@Serializable
internal data class PlexUser(
    val username: String? = null,
    val authToken: String? = null,
)

/** Reads provider ids out of Plex guids, new ("tmdb://603") and legacy ("com.plexapp.agents.themoviedb://603?lang=en"). */
object PlexGuids {
    /** "imdb", "tmdb" and "tvdb" ids; the first guid naming a provider wins. */
    fun providerIds(guids: List<String>): Map<String, String> {
        val ids = LinkedHashMap<String, String>()
        for (raw in guids) {
            val guid = raw.trim()
            val separator = guid.indexOf("://")
            if (separator <= 0) continue
            val provider = when (guid.substring(0, separator).lowercase(Locale.ROOT).substringAfterLast('.')) {
                "imdb" -> "imdb"
                "tmdb", "themoviedb" -> "tmdb"
                "tvdb", "thetvdb" -> "tvdb"
                else -> continue
            }
            // Legacy episode guids go on with "/season/episode"; the first part is the show's id.
            val id = guid.substring(separator + 3).substringBefore('?').substringBefore('/').trim()
            if (id.isNotEmpty()) ids.putIfAbsent(provider, id)
        }
        return ids
    }
}
