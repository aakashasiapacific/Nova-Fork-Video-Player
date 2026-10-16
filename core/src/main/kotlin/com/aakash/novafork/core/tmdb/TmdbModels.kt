package com.aakash.novafork.core.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the TMDB v3 API. Every field has a default so partial responses still decode.

@Serializable
data class TmdbPage<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
)

@Serializable
data class TmdbGenre(val id: Int = 0, val name: String = "")

@Serializable
data class TmdbMovieSummary(
    val id: Int,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val popularity: Double = 0.0,
) {
    val year: Int? get() = releaseDate?.take(4)?.toIntOrNull()
}

@Serializable
data class TmdbTvSummary(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val popularity: Double = 0.0,
) {
    val year: Int? get() = firstAirDate?.take(4)?.toIntOrNull()
}

@Serializable
data class TmdbMovieDetails(
    val id: Int,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("imdb_id") val imdbId: String? = null,
) {
    val year: Int? get() = releaseDate?.take(4)?.toIntOrNull()
}

@Serializable
data class TmdbSeasonSummary(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class TmdbTvDetails(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val seasons: List<TmdbSeasonSummary> = emptyList(),
) {
    val year: Int? get() = firstAirDate?.take(4)?.toIntOrNull()
}

@Serializable
data class TmdbEpisode(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class TmdbSeasonDetails(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    val episodes: List<TmdbEpisode> = emptyList(),
)
