package com.aakash.novafork.core.tmdb

import com.aakash.novafork.core.match.TitleMatcher
import com.aakash.novafork.core.parse.ParsedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

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

    suspend fun searchMovie(query: String, year: Int? = null): List<TmdbMovieSummary> {
        if (query.isBlank()) return emptyList()
        val params = listOf(
            "query" to query.trim(),
            "year" to year?.toString(),
            "include_adult" to "false",
            "language" to language(),
            "page" to "1",
        )
        return get("search/movie", params, TmdbPage.serializer(TmdbMovieSummary.serializer())).results
    }

    suspend fun searchTv(query: String, year: Int? = null): List<TmdbTvSummary> = searchTvHits(query, year).map { it.summary }

    suspend fun movie(id: Int): TmdbMovieDetails =
        get("movie/$id", listOf("language" to language()), TmdbMovieDetails.serializer())

    suspend fun tv(id: Int): TmdbTvDetails =
        get("tv/$id", listOf("language" to language()), TmdbTvDetails.serializer())

    suspend fun season(tvId: Int, seasonNumber: Int): TmdbSeasonDetails =
        get("tv/$tvId/season/$seasonNumber", listOf("language" to language()), TmdbSeasonDetails.serializer())

    /**
     * Search + [com.aakash.novafork.core.match.TitleMatcher.best] + details for a parsed movie.
     * Retries without the year when the year-filtered search finds nothing. Null = no confident match.
     */
    suspend fun findMovie(parsed: ParsedName): TmdbMovieDetails? {
        for (query in searchQueries(parsed.title)) {
            for (year in yearAttempts(parsed.year)) {
                val results = searchMovie(query.text, year)
                val match = bestOf(parsed, query.text, results) { candidates, wanted ->
                    TitleMatcher.best(
                        query = wanted,
                        year = parsed.year,
                        candidates = candidates,
                        title = { it.title },
                        originalTitle = { it.originalTitle },
                        candidateYear = { it.year },
                        popularity = { it.popularity },
                    )
                }
                if (match != null) return movie(match.id)
            }
        }
        return null
    }

    /** Same as [findMovie] for the show of a parsed episode (uses parsed.title / parsed.year). */
    suspend fun findShow(parsed: ParsedName): TmdbTvDetails? {
        for (query in searchQueries(parsed.title)) {
            for (year in yearAttempts(parsed.year)) {
                val hits = searchTvHits(query.text, year)
                // "The Office UK" and "The Office US" share a name: prefer the show from that country.
                val preferred = query.country
                    ?.let { country -> hits.filter { country in it.originCountries } }
                    ?.takeIf { it.isNotEmpty() }
                    ?: hits
                val match = bestOf(parsed, query.text, preferred) { candidates, wanted ->
                    TitleMatcher.best(
                        query = wanted,
                        year = parsed.year,
                        candidates = candidates,
                        title = { it.summary.name },
                        originalTitle = { it.summary.originalName },
                        candidateYear = { it.summary.year },
                        popularity = { it.summary.popularity },
                    )
                }
                if (match != null) return tv(match.summary.id)
            }
        }
        return null
    }

    /** Calls /configuration; success means the key works. */
    suspend fun testCredentials(): Result<Unit> =
        try {
            get("configuration", emptyList(), JsonObject.serializer())
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }

    private class SearchQuery(val text: String, /** ISO 3166 code when a country suffix was dropped. */ val country: String?)

    private class TvHit(val summary: TmdbTvSummary, val originCountries: List<String>)

    /**
     * The parsed title, then shortened forms: without a trailing country code ("The Office US")
     * and before a " - " subtitle ("Show - Something").
     */
    private fun searchQueries(title: String): List<SearchQuery> {
        val full = title.trim()
        val country = countryOf(full)
        val queries = buildList {
            add(SearchQuery(full, country))
            if (country != null) add(SearchQuery(full.substringBeforeLast(' ').trim(), country))
            if (" - " in full) add(SearchQuery(full.substringBefore(" - ").trim(), null))
        }
        return queries.filter { it.text.isNotEmpty() }.distinctBy { it.text }
    }

    /** "The Office US" → "US", "The Office UK" → "GB"; null when the title has no country suffix. */
    private fun countryOf(title: String): String? {
        val lastWord = title.substringAfterLast(' ', "")
        if (lastWord.length != 2 || lastWord !in COUNTRY_SUFFIXES) return null
        return if (lastWord == "UK") "GB" else lastWord
    }

    private fun yearAttempts(year: Int?): List<Int?> = if (year != null) listOf(year, null) else listOf(null)

    /** Matches against the full parsed title first, then against the shortened query that found [candidates]. */
    private fun <T> bestOf(
        parsed: ParsedName,
        queryText: String,
        candidates: List<T>,
        pick: (List<T>, String) -> T?,
    ): T? {
        if (candidates.isEmpty()) return null
        return pick(candidates, parsed.title) ?: if (queryText != parsed.title.trim()) pick(candidates, queryText) else null
    }

    private suspend fun searchTvHits(query: String, year: Int?): List<TvHit> {
        if (query.isBlank()) return emptyList()
        val params = listOf(
            "query" to query.trim(),
            "first_air_date_year" to year?.toString(),
            "include_adult" to "false",
            "language" to language(),
        )
        val page = get("search/tv", params, TmdbPage.serializer(JsonObject.serializer()))
        return page.results.mapNotNull { item ->
            // One malformed result should not hide the others. (SerializationException is an IllegalArgumentException.)
            val summary = try {
                json.decodeFromJsonElement(TmdbTvSummary.serializer(), item)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            val countries = (item["origin_country"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
            TvHit(summary, countries)
        }
    }

    private suspend fun <T> get(path: String, params: List<Pair<String, String?>>, deserializer: DeserializationStrategy<T>): T {
        val body = fetch(path, params)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: IllegalArgumentException) {
            throw IOException("Unexpected TMDB response for $path", e)
        }
    }

    private suspend fun fetch(path: String, params: List<Pair<String, String?>>): String {
        val key = credentials() ?: throw TmdbDisabledException()
        val request = buildRequest(key, path, params)
        return withContext(Dispatchers.IO) {
            var response = http.newCall(request).await()
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                val waitSeconds = retryAfterSeconds(response.header("Retry-After"))
                response.close()
                delay(waitSeconds * 1000L)
                response = http.newCall(request).await()
            }
            response.use { readBody(it) }
        }
    }

    private fun buildRequest(credentials: TmdbCredentials, path: String, params: List<Pair<String, String?>>): Request {
        val address = baseUrl()
        val base: HttpUrl = address.toHttpUrlOrNull() ?: throw IOException("Invalid TMDB address: $address")
        val key = credentials.key.trim()
        val url = base.newBuilder().apply {
            addPathSegments(path)
            if (!credentials.isBearer) addQueryParameter("api_key", key)
            for ((name, value) in params) {
                if (!value.isNullOrBlank()) addQueryParameter(name, value)
            }
        }.build()
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (credentials.isBearer) {
            try {
                builder.header("Authorization", "Bearer $key")
            } catch (e: IllegalArgumentException) {
                // OkHttp rejects control or non-ASCII characters in header values.
                throw IOException("The TMDB access token contains invalid characters", e)
            }
        }
        return builder.get().build()
    }

    private fun readBody(response: Response): String {
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw TmdbException(response.code, statusMessage(body) ?: "TMDB request failed (HTTP ${response.code})")
        }
        return body
    }

    /** TMDB errors look like {"status_code":7,"status_message":"Invalid API key…","success":false}. */
    private fun statusMessage(body: String): String? =
        try {
            ((json.parseToJsonElement(body) as? JsonObject)?.get("status_message") as? JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun retryAfterSeconds(header: String?): Long =
        (header?.trim()?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS).coerceIn(0L, MAX_RETRY_AFTER_SECONDS)

    /** Enqueues the call and cancels it when the coroutine is cancelled. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    // Arrived after cancellation: nobody reads it, so release the connection.
                    continuation.resume(response) { _, unread, _ -> unread.close() }
                }
            },
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
        /** Official alias that some ISPs (e.g. in India) do not block when they block the main host. */
        const val ALT_BASE_URL = "https://api.tmdb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val DEFAULT_RETRY_AFTER_SECONDS = 2L
        private const val MAX_RETRY_AFTER_SECONDS = 10L

        /** Suffixes that tell versions of a show apart ("The Office US"); "UK" is GB on TMDB. */
        private val COUNTRY_SUFFIXES = setOf(
            "US", "UK", "AU", "NZ", "CA", "IE", "IN", "FR", "DE", "JP", "KR", "ES", "IT", "NL", "SE", "DK", "NO", "BR", "MX",
        )

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }

        /** Full image URL or null. Sizes: posters w185/w342/w500/w780, backdrops w780/w1280/original, stills w300/w780. */
        fun imageUrl(path: String?, size: String): String? =
            if (path.isNullOrBlank()) null else IMAGE_BASE_URL + size + (if (path.startsWith("/")) path else "/$path")

        fun posterUrl(path: String?, size: String = "w500"): String? = imageUrl(path, size)
        fun backdropUrl(path: String?, size: String = "w1280"): String? = imageUrl(path, size)
        fun stillUrl(path: String?, size: String = "w780"): String? = imageUrl(path, size)
    }
}
