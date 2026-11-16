package com.aakash.novafork.core.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for a title's trailer and cast photos. Every field has a default so partial responses still decode.

/** GET movie/{id}/videos or tv/{id}/videos. */
@Serializable
data class TmdbVideos(val results: List<TmdbVideo> = emptyList()) {
    /** The YouTube key of the best trailer for the given languages ([Trailers.pick]); null when none. */
    fun trailerKey(languages: List<String>): String? = Trailers.pick(results, languages)
}

@Serializable
data class TmdbVideo(
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    @SerialName("iso_639_1") val language: String? = null,
    /** ISO date-time ("2024-02-01T16:00:00.000Z"); sorts newest-first as text. */
    @SerialName("published_at") val publishedAt: String? = null,
)

object Trailers {
    /**
     * The key of the YouTube video to open as a title's trailer, or null when there is none. Only
     * YouTube videos of type "Trailer" count. Official ones win; then the earlier of [languages]
     * (the device's language, then English; a video with no language or another one comes last);
     * then the newest.
     */
    fun pick(videos: List<TmdbVideo>, languages: List<String>): String? {
        val wanted = languages.map { it.substringBefore('-').lowercase() }
        fun languageRank(video: TmdbVideo): Int {
            val index = wanted.indexOf(video.language?.substringBefore('-')?.lowercase())
            return if (index >= 0) index else wanted.size
        }
        return videos
            .filter { it.key.isNotBlank() && it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
            .sortedWith(
                compareByDescending<TmdbVideo> { it.official }
                    .thenBy { languageRank(it) }
                    .thenByDescending { it.publishedAt.orEmpty() },
            )
            .firstOrNull()
            ?.key
    }
}

/** GET movie/{id}/credits, or tv/{id}/aggregate_credits (where each actor lists several [TmdbCastEntry.roles]). */
@Serializable
data class TmdbCastCredits(val cast: List<TmdbCastEntry> = emptyList()) {
    /** The first billed actors with their photo path, at most [limit]; people without a name are left out. */
    fun topCast(limit: Int = CAST_LIMIT): List<CastMember> =
        cast.filter { it.name.isNotBlank() }
            .sortedBy { it.order }
            .distinctBy { it.name }
            .take(limit)
            .map { CastMember(it.name.trim(), it.characterName(), it.profilePath?.takeIf { path -> path.isNotBlank() }) }

    companion object {
        const val CAST_LIMIT = 15
    }
}

@Serializable
data class TmdbCastEntry(
    val name: String = "",
    /** Movies: the one role. */
    val character: String? = null,
    /** Shows (aggregate credits): every role, with the episodes it was played in. */
    val roles: List<TmdbCastRole> = emptyList(),
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = Int.MAX_VALUE,
) {
    /** The character played ("Neo"); for a show the role with most episodes; null when unknown. */
    fun characterName(): String? =
        character?.trim()?.takeIf { it.isNotEmpty() }
            ?: roles.filter { !it.character.isNullOrBlank() }.maxByOrNull { it.episodeCount }?.character?.trim()
}

@Serializable
data class TmdbCastRole(
    val character: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
)

/** An actor of a title's cast row: [profilePath] is a TMDB image path ("/abc.jpg") or null. */
data class CastMember(val name: String, val character: String?, val profilePath: String?)
