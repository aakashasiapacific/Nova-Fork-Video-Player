package com.aakash.novafork.data

import android.content.Context
import android.content.SharedPreferences
import com.aakash.novafork.core.tmdb.CastMember
import com.aakash.novafork.core.tmdb.TmdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What a details page shows beyond the metadata row: a title's age rating ("PG-13"), a show's
 * next episode date, its trailer's YouTube key and its cast row, all from TMDB. Kept in the "title_extras" SharedPreferences keyed by the
 * metadata key ("movie:603", "tv:1396"), so the database needs no migration. A page asks with
 * [request] when it opens; nothing is shown until an answer is cached.
 */
class TitleExtras(
    context: Context,
    private val tmdb: TmdbClient,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failedAt = ConcurrentHashMap<String, Long>()

    private val _changes = MutableStateFlow(0L)

    /** Counts up whenever something was cached, so a page built from [certification] / [nextAirDate] is rebuilt. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** The age rating cached for [key] in the device's country; null while unknown or when TMDB has none. */
    fun certification(key: String): String? = string(certKey(key))?.takeIf { it.isNotBlank() }

    /** The air date ("2025-11-07") of the show's next episode cached for [key]; null when unknown. */
    fun nextAirDate(key: String): String? = string(nextKey(key))?.takeIf { it.isNotBlank() }

    /** The YouTube key of the title's trailer cached for [key]; null while unknown or when it has none. */
    fun trailerKey(key: String): String? = string(trailerPrefKey(key))?.takeIf { it.isNotBlank() }

    /** The top-billed cast cached for [key] (at most 15, with photo paths); empty while unknown. */
    fun cast(key: String): List<CastMember> = decodeCast(string(castPrefKey(key)))

    /**
     * Fetches what is missing or out of date for a movie or show ("movie:603" / "tv:1396"; any other
     * key, such as a media server's, is ignored). A rating is fetched once (again after a day when
     * TMDB had none); the next episode at most once a day. Returns at once; safe to call often.
     */
    fun request(key: String) {
        val parts = key.split(':')
        if (parts.size != 2) return
        val id = parts[1].toIntOrNull() ?: return
        val isTv = when (parts[0]) {
            "tv" -> true
            "movie" -> false
            else -> return
        }
        if (!tmdb.isEnabled) return
        val time = now()
        val needsRating = certification(key) == null && time - timeOf(certAtKey(key)) >= DAY_MS
        val needsNext = isTv && time - timeOf(nextAtKey(key)) >= DAY_MS
        // A trailer or a cast is fetched once; a title that has none is asked again after a week.
        val trailerAt = timeOf(trailerAtKey(key))
        val needsTrailer = trailerAt == 0L || (trailerKey(key) == null && time - trailerAt >= WEEK_MS)
        val castAt = timeOf(castAtKey(key))
        val needsCast = castAt == 0L || (cast(key).isEmpty() && time - castAt >= WEEK_MS)
        if (!needsRating && !needsNext && !needsTrailer && !needsCast) return
        val failed = failedAt[key]
        if (failed != null && time - failed < RETRY_MS) return
        if (!inFlight.add(key)) return
        scope.launch {
            try {
                if (needsRating) fetchRating(key, id, isTv)
                if (needsNext) fetchNext(key, id)
                if (needsTrailer) fetchTrailer(key, id, isTv)
                if (needsCast) fetchCast(key, id, isTv)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun fetchRating(key: String, id: Int, isTv: Boolean) {
        val country = country()
        try {
            val rating = if (isTv) tmdb.tvCertification(id, country) else tmdb.movieCertification(id, country)
            prefs.edit().putString(certKey(key), rating.orEmpty()).putLong(certAtKey(key), now()).apply()
            failedAt.remove(key)
            _changes.update { it + 1 }
        } catch (e: IOException) {
            failedAt[key] = now()
        }
    }

    private suspend fun fetchNext(key: String, id: Int) {
        try {
            val date = tmdb.tvNextEpisode(id)?.airDate
            prefs.edit().putString(nextKey(key), date.orEmpty()).putLong(nextAtKey(key), now()).apply()
            failedAt.remove(key)
            _changes.update { it + 1 }
        } catch (e: IOException) {
            failedAt[key] = now()
        }
    }

    private suspend fun fetchTrailer(key: String, id: Int, isTv: Boolean) {
        try {
            val languages = listOf(Locale.getDefault().language, "en").filter { it.isNotBlank() }.distinct()
            val video = tmdb.trailerKey(if (isTv) "tv" else "movie", id, languages)
            prefs.edit().putString(trailerPrefKey(key), video.orEmpty()).putLong(trailerAtKey(key), now()).apply()
            failedAt.remove(key)
            _changes.update { it + 1 }
        } catch (e: IOException) {
            failedAt[key] = now()
        }
    }

    private suspend fun fetchCast(key: String, id: Int, isTv: Boolean) {
        try {
            val cast = tmdb.castOf(if (isTv) "tv" else "movie", id)
            prefs.edit().putString(castPrefKey(key), encodeCast(cast)).putLong(castAtKey(key), now()).apply()
            failedAt.remove(key)
            _changes.update { it + 1 }
        } catch (e: IOException) {
            failedAt[key] = now()
        }
    }

    // SharedPreferences casts what it holds to the type asked for: a key of another type throws.
    private fun string(name: String): String? =
        try {
            prefs.getString(name, null)
        } catch (e: ClassCastException) {
            null
        }

    private fun timeOf(name: String): Long =
        try {
            prefs.getLong(name, 0L)
        } catch (e: ClassCastException) {
            0L
        }

    private fun country(): String = Locale.getDefault().country.ifBlank { "US" }

    // The rating is per country: moving to another region fetches it again.
    private fun certKey(key: String) = "cert:${country()}:$key"
    private fun certAtKey(key: String) = "certAt:${country()}:$key"
    private fun nextKey(key: String) = "next:$key"
    private fun nextAtKey(key: String) = "nextAt:$key"
    private fun trailerPrefKey(key: String) = "trailer:$key"
    private fun trailerAtKey(key: String) = "trailerAt:$key"
    private fun castPrefKey(key: String) = "cast:$key"
    private fun castAtKey(key: String) = "castAt:$key"

    private companion object {
        const val PREFS = "title_extras"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val WEEK_MS = 7 * DAY_MS

        /** After a failed fetch (offline, rate limit) a title waits this long before another try. */
        const val RETRY_MS = 10 * 60 * 1000L
    }
}

/** One line per actor: name, character and photo path, tab-separated (none of them holds a tab or a newline). */
internal fun encodeCast(cast: List<CastMember>): String =
    cast.joinToString("\n") { listOf(it.name, it.character.orEmpty(), it.profilePath.orEmpty()).joinToString("\t") { part -> part.replace('\t', ' ').replace('\n', ' ') } }

internal fun decodeCast(text: String?): List<CastMember> =
    text.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CastMember(name, parts.getOrNull(1)?.takeIf { it.isNotBlank() }, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }.toList()
