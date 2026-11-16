package com.aakash.novafork.data

import android.content.Context

/**
 * A subtitle language for each source (its row's "…" menu in Preferences › Library): videos of
 * that source prefer it over the general one. A show's own remembered choice still comes first.
 */
object SourceSubtitleLanguages {
    private const val PREFS = "source_subtitle_language"

    fun get(context: Context, sourceId: Long): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(sourceId.toString(), null)?.takeIf { it.isNotBlank() }

    fun set(context: Context, sourceId: Long, language: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (language.isBlank()) prefs.remove(sourceId.toString()) else prefs.putString(sourceId.toString(), language)
        prefs.apply()
    }
}
