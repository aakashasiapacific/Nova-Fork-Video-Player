package com.aakash.novafork.data

import android.content.Context
import android.content.SharedPreferences
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
 * What a details page shows beyond the metadata row: a title's age rating ("PG-13") and a show's
 * next episode date, both from TMDB. Kept in the "title_extras" SharedPreferences keyed by the
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
        if (!needsRating && !needsNext) return
        val failed = failedAt[key]
        if (failed != null && time - failed < RETRY_MS) return
        if (!inFlight.add(key)) return
        scope.launch {
            try {
                if (needsRating) fetchRating(key, id, isTv)
                if (needsNext) fetchNext(key, id)
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

    private companion object {
        const val PREFS = "title_extras"
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** After a failed fetch (offline, rate limit) a title waits this long before another try. */
        const val RETRY_MS = 10 * 60 * 1000L
    }
}
