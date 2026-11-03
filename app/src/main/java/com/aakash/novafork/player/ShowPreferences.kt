package com.aakash.novafork.player

import android.content.Context
import java.util.Locale

/**
 * What a show remembers between its episodes (owner): the playback speed last chosen for it and
 * the language of the audio track last picked, so the next episode starts the same way. Kept by
 * the show's key (the subtitle choice too: a language, or off); other shows and films are not affected.
 */
internal class ShowPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The speed chosen for [showKey], or null when none was. */
    fun rate(showKey: String?): Float? {
        if (showKey == null) return null
        return try {
            prefs.getFloat(RATE + showKey, 0f).takeIf { it > 0f }
        } catch (e: ClassCastException) {
            null
        }
    }

    fun setRate(showKey: String, rate: Float) {
        prefs.edit().apply { if (rate == 1f) remove(RATE + showKey) else putFloat(RATE + showKey, rate) }.apply()
    }

    /** The names [showKey]'s chosen audio language goes by (see [TrackLanguages.aliases]); null when none. */
    fun audioLanguage(showKey: String?): Set<String>? {
        if (showKey == null) return null
        val name = try {
            prefs.getString(AUDIO + showKey, null)
        } catch (e: ClassCastException) {
            null
        } ?: return null
        return TrackLanguages.aliases(name).takeIf { it.isNotEmpty() }
    }

    /** Remembers the language in [trackName] ("English", "Hindi [hin]") for [showKey], if it names one. */
    fun setAudioTrack(showKey: String, trackName: String) {
        val language = trackName.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}]+"))
            .firstOrNull { word -> word.isNotBlank() && TrackLanguages.aliases(word).size > 1 }
            ?: return
        prefs.edit().putString(AUDIO + showKey, language).apply()
    }

    /** The subtitle choice remembered for [showKey]: [SUBTITLES_OFF], a language word, or null when none. */
    private fun subtitleChoice(showKey: String?): String? {
        if (showKey == null) return null
        return try {
            prefs.getString(SUBTITLE + showKey, null)
        } catch (e: ClassCastException) {
            null
        }
    }

    /** True when subtitles were last turned off for [showKey]. */
    fun subtitlesOff(showKey: String?): Boolean = subtitleChoice(showKey) == SUBTITLES_OFF

    /** The names [showKey]'s chosen subtitle language goes by; null when none, or when subtitles were turned off. */
    fun subtitleLanguage(showKey: String?): Set<String>? {
        val name = subtitleChoice(showKey)?.takeIf { it != SUBTITLES_OFF } ?: return null
        return TrackLanguages.aliases(name).takeIf { it.isNotEmpty() }
    }

    /** Remembers that subtitles were turned off for [showKey]. */
    fun setSubtitlesOff(showKey: String) {
        prefs.edit().putString(SUBTITLE + showKey, SUBTITLES_OFF).apply()
    }

    /** Remembers the language in the subtitle track [trackName] for [showKey], if it names one. */
    fun setSubtitleTrack(showKey: String, trackName: String) {
        val language = languageOf(trackName) ?: return
        prefs.edit().putString(SUBTITLE + showKey, language).apply()
    }

    private fun languageOf(trackName: String): String? = trackName.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}]+"))
        .firstOrNull { word -> word.isNotBlank() && TrackLanguages.aliases(word).size > 1 }

    private companion object {
        const val PREFS_NAME = "show_preferences"
        const val RATE = "rate:"
        const val AUDIO = "audio:"
        const val SUBTITLE = "subtitle:"
        const val SUBTITLES_OFF = "__off__"
    }
}
