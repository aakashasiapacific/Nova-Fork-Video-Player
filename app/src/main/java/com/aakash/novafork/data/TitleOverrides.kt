package com.aakash.novafork.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Rename in Library": titles the user typed in place of the ones the library shows. Only the
 * name shown in the app changes; no file is touched. Kept in the "title_overrides"
 * SharedPreferences, so the database needs no migration. A movie or show with TMDB details is
 * keyed by its metadata key ("movie:603", "tv:1396"), any other video by "video:<id>" and an
 * unidentified show by "show:<showKey>" (see [com.aakash.novafork.ui.LibraryIndex]).
 */
class TitleOverrides(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _titles = MutableStateFlow(read())

    /** Every override by key; the library is rebuilt when it changes. */
    val titles: StateFlow<Map<String, String>> = _titles.asStateFlow()

    /** Shows [title] for [key] from now on; a blank [title] goes back to the original (same as [reset]). */
    fun set(key: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) {
            reset(key)
            return
        }
        prefs.edit().putString(key, clean).apply()
        _titles.value = _titles.value + (key to clean)
    }

    /** Back to the original title for [key]. */
    fun reset(key: String) {
        prefs.edit().remove(key).apply()
        _titles.value = _titles.value - key
    }

    private fun read(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.takeIf { it.isNotBlank() }?.let { key to it } }.toMap()

    private companion object {
        const val PREFS = "title_overrides"
    }
}
