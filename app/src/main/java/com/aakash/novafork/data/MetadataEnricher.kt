package com.aakash.novafork.data

import android.util.Log
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.parse.ParsedKind
import com.aakash.novafork.core.parse.ParsedName
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.core.tmdb.TmdbDisabledException
import com.aakash.novafork.core.tmdb.TmdbEpisode
import com.aakash.novafork.core.tmdb.TmdbException
import com.aakash.novafork.core.tmdb.TmdbMovieDetails
import com.aakash.novafork.core.tmdb.TmdbSeasonDetails
import com.aakash.novafork.core.tmdb.TmdbSeasonSummary
import com.aakash.novafork.core.tmdb.TmdbTvDetails
import com.aakash.novafork.data.db.MatchCacheEntity
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SQL_CHUNK
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MetadataEnricher"

/** Failed lookups and cached misses are retried after a week. */
private const val MATCH_RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000

private const val CONCURRENCY = 3
private const val KEY_REJECTED = "TMDB rejected the API key"
private const val UNREACHABLE = "Couldn't reach TMDB"
private val PASS_ERRORS = setOf(KEY_REJECTED, UNREACHABLE)

/**
 * Matches movies and episodes against TMDB in the background: PENDING ones, and UNMATCHED ones
 * last tried a week ago (never MANUAL). Each distinct title is looked up once per pass and the
 * answer is kept in the match cache. A pass also re-fetches metadata that videos point at but the
 * database lacks, which is how manual matches get their data back after [LibraryRepository.clearMetadata].
 */
internal class MetadataEnricher(
    database: NovaDatabase,
    private val tmdb: TmdbClient,
    private val scanState: MutableStateFlow<ScanState>,
    scope: CoroutineScope,
) {
    private val videoDao = database.videoDao()
    private val metadataDao = database.metadataDao()
    private val matchCacheDao = database.matchCacheDao()

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val generation = AtomicInteger()

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                requests.receive()
                try {
                    runPass()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Metadata pass failed", e)
                } finally {
                    scanState.update { it.copy(enriching = false) }
                }
            }
        }
    }

    /** Asks for a pass. Requests made while one runs coalesce into a single pass after it. */
    fun request() {
        requests.trySend(Unit)
    }

    /** Stops the running pass before its next write: what it fetched is outdated (e.g. language changed). */
    fun invalidate() {
        generation.incrementAndGet()
    }

    private suspend fun runPass() {
        if (!tmdb.isEnabled) return
        val pass = Pass(generation.get())
        val orphanKeys = videoDao.orphanMetadataKeys()
        val candidates = videoDao.needingMatch(retryBefore = pass.now - MATCH_RETRY_AFTER_MS)
        if (orphanKeys.isEmpty() && candidates.isEmpty()) return

        val groups = candidates.groupBy { it.matchQueryKey() }
        scanState.update {
            it.copy(enriching = true, enrichDone = 0, enrichTotal = orphanKeys.size + candidates.size)
        }
        try {
            coroutineScope {
                val permits = Semaphore(CONCURRENCY)
                for (key in orphanKeys) {
                    launch {
                        permits.withPermit { pass.restore(key) }
                        advance(1)
                    }
                }
                for ((query, group) in groups) {
                    launch {
                        permits.withPermit { pass.match(query, group) }
                        advance(group.size)
                    }
                }
            }
            // A pass that went through clears the TMDB error an earlier one left.
            scanState.update { if (it.lastError in PASS_ERRORS) it.copy(lastError = null) else it }
        } catch (e: StopPass) {
            val message = e.message
            if (message != null) scanState.update { it.copy(lastError = message) }
        }
    }

    private fun advance(count: Int) {
        scanState.update { it.copy(enrichDone = it.enrichDone + count) }
    }

    /** Ends the whole pass; [message] (if any) is shown as [ScanState.lastError]. Videos stay as they are. */
    private class StopPass(message: String?) : Exception(message)

    private class SeasonLookup(val details: TmdbSeasonDetails?)

    /** State of one pass: its start time and the seasons fetched so far. */
    private inner class Pass(private val passGeneration: Int) {
        val now: Long = System.currentTimeMillis()
        private val seasons = ConcurrentHashMap<Pair<Int, Int>, SeasonLookup>()

        /** Matches every video of one title (same [LibraryKeys.matchQueryKey]). */
        suspend fun match(query: String, group: List<VideoEntity>) {
            ensureCurrent()
            val ids = group.map { it.id }
            tmdbStep(onNotFound = { recordMiss(query, ids) }) {
                val cached = matchCacheDao.get(query)
                val cachedKey = cached?.metadataKey
                when {
                    cachedKey != null && cachedKey.startsWith(keyPrefix(group)) -> applyCachedMatch(cachedKey, group)
                    cached != null && cachedKey == null && cached.checkedAt >= now - MATCH_RETRY_AFTER_MS ->
                        markUnmatched(ids, cached.checkedAt)
                    else -> lookUp(query, group)
                }
            }
        }

        /** Re-fetches a movie or show some videos point at (manual matches included). */
        suspend fun restore(key: String) {
            ensureCurrent()
            val tmdbId = LibraryKeys.tmdbId(key) ?: return
            tmdbStep(onNotFound = { Log.i(TAG, "$key no longer exists on TMDB") }) {
                if (key.startsWith("movie:")) {
                    val movie = tmdb.movie(tmdbId)
                    ensureCurrent()
                    metadataDao.upsert(movie.toMetadata(now))
                } else {
                    val show = tmdb.tv(tmdbId)
                    ensureCurrent()
                    metadataDao.upsert(show.toMetadata(now))
                    for (video in videoDao.byMetadataKey(key)) {
                        val season = video.season ?: continue
                        val number = video.episode ?: continue
                        val episode = episode(tmdbId, season, number) ?: continue
                        ensureCurrent()
                        videoDao.setEpisodeDetails(
                            id = video.id,
                            episodeTitle = episode.name.nonBlank(),
                            episodeOverview = episode.overview.nonBlank(),
                            episodeStillPath = episode.stillPath.nonBlank(),
                            episodeAirDate = episode.airDate.nonBlank(),
                        )
                    }
                }
            }
        }

        private suspend fun lookUp(query: String, group: List<VideoEntity>) {
            val parsed = group.first().toParsedName()
            if (parsed.kind == ParsedKind.EPISODE) {
                val show = tmdb.findShow(parsed)
                ensureCurrent()
                if (show != null) metadataDao.upsert(show.toMetadata(now))
                val key = show?.let { LibraryKeys.tvKey(it.id) }
                matchCacheDao.put(MatchCacheEntity(query = query, metadataKey = key, checkedAt = now))
                if (show != null) applyShow(show.id, show.seasons, group) else markUnmatched(group.map { it.id }, now)
            } else {
                val movie = tmdb.findMovie(parsed)
                ensureCurrent()
                if (movie != null) metadataDao.upsert(movie.toMetadata(now))
                val key = movie?.let { LibraryKeys.movieKey(it.id) }
                matchCacheDao.put(MatchCacheEntity(query = query, metadataKey = key, checkedAt = now))
                if (key != null) markMovie(group.map { it.id }, key) else markUnmatched(group.map { it.id }, now)
            }
        }

        private suspend fun applyCachedMatch(key: String, group: List<VideoEntity>) {
            val tmdbId = LibraryKeys.tmdbId(key) ?: return
            if (group.first().kind == VideoKind.EPISODE) {
                // Absolute episode numbers need the season list to be placed.
                val needsShow = metadataDao.get(key) == null || group.any { it.season == null && it.episode != null }
                val seasonList = if (needsShow) {
                    val show = tmdb.tv(tmdbId)
                    ensureCurrent()
                    metadataDao.upsert(show.toMetadata(now))
                    show.seasons
                } else {
                    emptyList()
                }
                applyShow(tmdbId, seasonList, group)
            } else {
                if (metadataDao.get(key) == null) {
                    val movie = tmdb.movie(tmdbId)
                    ensureCurrent()
                    metadataDao.upsert(movie.toMetadata(now))
                }
                markMovie(group.map { it.id }, key)
            }
        }

        private suspend fun applyShow(tvId: Int, seasonList: List<TmdbSeasonSummary>, group: List<VideoEntity>) {
            val key = LibraryKeys.tvKey(tvId)
            for (video in group) {
                val (season, number) = resolveEpisodeNumbers(video.season, video.episode, seasonList)
                val episode = if (season != null && number != null) episode(tvId, season, number) else null
                ensureCurrent()
                videoDao.setEpisodeMatch(
                    id = video.id,
                    metadataKey = key,
                    season = season,
                    episode = number,
                    episodeTitle = episode?.name.nonBlank(),
                    episodeOverview = episode?.overview.nonBlank(),
                    episodeStillPath = episode?.stillPath.nonBlank(),
                    episodeAirDate = episode?.airDate.nonBlank(),
                    attemptAt = now,
                )
            }
        }

        private suspend fun episode(tvId: Int, season: Int, number: Int): TmdbEpisode? =
            seasonDetails(tvId, season)?.episodes?.firstOrNull { it.episodeNumber == number }

        private suspend fun seasonDetails(tvId: Int, season: Int): TmdbSeasonDetails? {
            val ref = tvId to season
            seasons[ref]?.let { return it.details }
            val details = try {
                tmdb.season(tvId, season)
            } catch (e: TmdbException) {
                if (e.code == 404) null else throw e
            }
            seasons[ref] = SeasonLookup(details)
            return details
        }

        private suspend fun markMovie(ids: List<Long>, key: String) {
            ensureCurrent()
            for (chunk in ids.chunked(SQL_CHUNK)) videoDao.setMovieMatch(chunk, key, now)
        }

        private suspend fun markUnmatched(ids: List<Long>, attemptAt: Long) {
            ensureCurrent()
            for (chunk in ids.chunked(SQL_CHUNK)) videoDao.setUnmatched(chunk, attemptAt)
        }

        private suspend fun recordMiss(query: String, ids: List<Long>) {
            ensureCurrent()
            matchCacheDao.put(MatchCacheEntity(query = query, metadataKey = null, checkedAt = now))
            markUnmatched(ids, now)
        }

        private fun ensureCurrent() {
            if (generation.get() != passGeneration) throw StopPass(null)
        }

        /** Runs one lookup; turns the errors that make the rest of the pass pointless into [StopPass]. */
        private suspend fun tmdbStep(onNotFound: suspend () -> Unit, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StopPass) {
                throw e
            } catch (e: TmdbDisabledException) {
                throw StopPass(null)
            } catch (e: TmdbException) {
                when (e.code) {
                    401 -> throw StopPass(KEY_REJECTED)
                    404 -> onNotFound()
                    else -> throw StopPass(UNREACHABLE)
                }
            } catch (e: IOException) {
                throw StopPass(UNREACHABLE)
            } catch (e: Exception) {
                // One odd response must not block every other title: treat it as no match.
                Log.w(TAG, "Lookup failed", e)
                onNotFound()
            }
        }
    }
}

private fun keyPrefix(group: List<VideoEntity>): String =
    if (group.first().kind == VideoKind.EPISODE) "tv:" else "movie:"

private fun VideoEntity.matchQueryKey(): String =
    LibraryKeys.matchQueryKey(if (kind == VideoKind.EPISODE) "tv" else "movie", parsedTitle, parsedYear)

private fun VideoEntity.toParsedName(): ParsedName = ParsedName(
    kind = if (kind == VideoKind.EPISODE) ParsedKind.EPISODE else ParsedKind.MOVIE,
    title = parsedTitle,
    year = parsedYear,
    season = season,
    episode = episode,
)

internal fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

internal fun TmdbMovieDetails.toMetadata(now: Long): MetadataEntity = MetadataEntity(
    key = LibraryKeys.movieKey(id),
    tmdbId = id,
    type = "movie",
    title = title.ifBlank { originalTitle.orEmpty() },
    originalTitle = originalTitle.nonBlank(),
    year = year,
    overview = overview.nonBlank(),
    tagline = tagline.nonBlank(),
    posterPath = posterPath.nonBlank(),
    backdropPath = backdropPath.nonBlank(),
    rating = voteAverage.takeIf { it > 0.0 },
    genres = genres.joinToString(", ") { it.name },
    runtimeMin = runtime?.takeIf { it > 0 },
    updatedAt = now,
)

internal fun TmdbTvDetails.toMetadata(now: Long): MetadataEntity {
    val seasonPosters = buildJsonObject {
        for (season in seasons) {
            val poster = season.posterPath
            if (!poster.isNullOrBlank()) put(season.seasonNumber.toString(), poster)
        }
    }
    return MetadataEntity(
        key = LibraryKeys.tvKey(id),
        tmdbId = id,
        type = "tv",
        title = name.ifBlank { originalName.orEmpty() },
        originalTitle = originalName.nonBlank(),
        year = year,
        overview = overview.nonBlank(),
        tagline = tagline.nonBlank(),
        posterPath = posterPath.nonBlank(),
        backdropPath = backdropPath.nonBlank(),
        rating = voteAverage.takeIf { it > 0.0 },
        genres = genres.joinToString(", ") { it.name },
        runtimeMin = episodeRunTime.firstOrNull { it > 0 },
        seasonPostersJson = if (seasonPosters.isEmpty()) null else seasonPosters.toString(),
        updatedAt = now,
    )
}

/**
 * Places an anime-style absolute episode number (season == null) into TMDB's seasons using their
 * episode counts, specials (season 0) excluded. Numbers past the listed episodes stay absolute.
 */
internal fun resolveEpisodeNumbers(season: Int?, episode: Int?, seasons: List<TmdbSeasonSummary>): Pair<Int?, Int?> {
    if (season != null || episode == null || episode <= 0) return season to episode
    var remaining: Int = episode
    for (summary in seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }.sortedBy { it.seasonNumber }) {
        if (remaining <= summary.episodeCount) return summary.seasonNumber to remaining
        remaining -= summary.episodeCount
    }
    return null to episode
}
