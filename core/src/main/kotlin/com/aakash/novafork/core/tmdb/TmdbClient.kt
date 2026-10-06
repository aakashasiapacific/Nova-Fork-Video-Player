package com.aakash.novafork.core.tmdb

import com.aakash.novafork.core.parse.ParsedName
import okhttp3.OkHttpClient
import java.io.IOException

/** A TMDB v3 API key (32 hex chars, sent as `api_key=`) or a v4 read access token (JWT, sent as Bearer). */
data class TmdbCredentials(val key: String) {
    val isBearer: Boolean get() = key.startsWith("eyJ") && key.length > 60
}

/** HTTP error from TMDB. 401 = bad key, 404 = not found, 429 = rate limited. */
class TmdbException(val code: Int, message: String) : IOException(message)

/** Thrown when no credentials are configured. */
class TmdbDisabledException : IOException("No TMDB API key")

/**
 * Small TMDB v3 client. All functions are main-safe (they switch to Dispatchers.IO) and
 * cancellable. Responses decode with ignoreUnknownKeys / coerceInputValues.
 *
 * Retries a 429 once after the Retry-After delay (default 2 s), capped at 10 s.
 */
class TmdbClient(
    private val http: OkHttpClient,
    /** Read on every call; null disables the client ([TmdbDisabledException]). */
    private val credentials: () -> TmdbCredentials?,
    /** Read on every call, e.g. [DEFAULT_BASE_URL] or [ALT_BASE_URL]. Must end with "/". */
    private val baseUrl: () -> String = { DEFAULT_BASE_URL },
    /** Read on every call, e.g. "en-US". */
    private val language: () -> String = { "en-US" },
) {
    val isEnabled: Boolean get() = credentials() != null

    suspend fun searchMovie(query: String, year: Int? = null): List<TmdbMovieSummary> = TODO("core agent")

    suspend fun searchTv(query: String, year: Int? = null): List<TmdbTvSummary> = TODO("core agent")

    suspend fun movie(id: Int): TmdbMovieDetails = TODO("core agent")

    suspend fun tv(id: Int): TmdbTvDetails = TODO("core agent")

    suspend fun season(tvId: Int, seasonNumber: Int): TmdbSeasonDetails = TODO("core agent")

    /**
     * Search + [com.aakash.novafork.core.match.TitleMatcher.best] + details for a parsed movie.
     * Retries without the year when the year-filtered search finds nothing. Null = no confident match.
     */
    suspend fun findMovie(parsed: ParsedName): TmdbMovieDetails? = TODO("core agent")

    /** Same as [findMovie] for the show of a parsed episode (uses parsed.title / parsed.year). */
    suspend fun findShow(parsed: ParsedName): TmdbTvDetails? = TODO("core agent")

    /** Calls /configuration; success means the key works. */
    suspend fun testCredentials(): Result<Unit> = TODO("core agent")

    companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
        /** Official alias that some ISPs (e.g. in India) do not block when they block the main host. */
        const val ALT_BASE_URL = "https://api.tmdb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

        /** Full image URL or null. Sizes: posters w185/w342/w500/w780, backdrops w780/w1280/original, stills w300/w780. */
        fun imageUrl(path: String?, size: String): String? =
            if (path.isNullOrBlank()) null else IMAGE_BASE_URL + size + (if (path.startsWith("/")) path else "/$path")

        fun posterUrl(path: String?, size: String = "w500"): String? = imageUrl(path, size)
        fun backdropUrl(path: String?, size: String = "w1280"): String? = imageUrl(path, size)
        fun stillUrl(path: String?, size: String = "w780"): String? = imageUrl(path, size)
    }
}
