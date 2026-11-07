package com.aakash.novafork.core.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for a title's age rating and next episode. Every field has a default so partial responses still decode.

/** GET movie/{id}/release_dates: the releases of a movie by country, each with its certification. */
@Serializable
data class TmdbReleaseDates(val results: List<TmdbReleaseCountry> = emptyList()) {
    /**
     * The certification ("PG-13", "15") for [country], else for [fallback]; null when neither has
     * one. Within a country the theatrical release (type 3) wins, then the first one that has one.
     */
    fun certification(country: String, fallback: String = "US"): String? =
        listOf(country, fallback).distinct().firstNotNullOfOrNull { code ->
            val releases = results.filter { it.country.equals(code, ignoreCase = true) }
                .flatMap { it.releases }
                .filter { it.certification.isNotBlank() }
            (releases.firstOrNull { it.type == THEATRICAL } ?: releases.firstOrNull())?.certification?.trim()
        }

    companion object {
        const val THEATRICAL = 3
    }
}

@Serializable
data class TmdbReleaseCountry(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releases: List<TmdbRelease> = emptyList(),
)

@Serializable
data class TmdbRelease(val certification: String = "", val type: Int = 0)

/** GET tv/{id}/content_ratings: the rating of a show by country ("TV-MA", "15"). */
@Serializable
data class TmdbContentRatings(val results: List<TmdbContentRating> = emptyList()) {
    /** The rating for [country], else for [fallback]; null when neither has one. */
    fun certification(country: String, fallback: String = "US"): String? =
        listOf(country, fallback).distinct().firstNotNullOfOrNull { code ->
            results.firstOrNull { it.country.equals(code, ignoreCase = true) && it.rating.isNotBlank() }?.rating?.trim()
        }
}

@Serializable
data class TmdbContentRating(
    @SerialName("iso_3166_1") val country: String = "",
    val rating: String = "",
)

/** Just the next episode of GET tv/{id}; null when TMDB knows of none. */
@Serializable
data class TmdbTvNextEpisode(
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisode? = null,
)
