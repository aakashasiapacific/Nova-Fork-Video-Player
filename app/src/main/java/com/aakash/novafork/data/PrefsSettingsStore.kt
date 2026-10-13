package com.aakash.novafork.data

import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import androidx.core.content.edit
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.core.tmdb.TmdbLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SettingsStore] persisted in the "settings" SharedPreferences file. Enums are stored by name.
 *
 * Until the user picks a TMDB language, [Settings.tmdbLanguage] follows the device's languages
 * (see [TmdbLanguages]); picking one stores it for good.
 */
class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val writeLock = Any()

    /** True once the user picked the TMDB language. Guarded by [writeLock] after construction. */
    private var languageChosen: Boolean = readLanguageChosen(prefs)
    private val state = MutableStateFlow(read(prefs, languageChosen, deviceTmdbLanguage()))

    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun update(transform: (Settings) -> Settings) {
        synchronized(writeLock) {
            val current = state.value
            val next = transform(current)
            if (next == current) return
            if (next.tmdbLanguage != current.tmdbLanguage) languageChosen = true
            state.value = next
            write(next)
        }
    }

    override val effectiveTmdbKey: String
        get() = state.value.tmdbApiKey.trim().ifEmpty { BuildConfig.TMDB_API_KEY.trim() }

    override val hasBuiltInTmdbKey: Boolean
        get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    private fun write(s: Settings) {
        prefs.edit {
            putString(KEY_TMDB_API_KEY, s.tmdbApiKey)
            // A language the user never picked is not stored, so it keeps following the device.
            putBoolean(KEY_TMDB_LANGUAGE_CHOSEN, languageChosen)
            if (languageChosen) putString(KEY_TMDB_LANGUAGE, s.tmdbLanguage) else remove(KEY_TMDB_LANGUAGE)
            putString(KEY_TMDB_HOST, s.tmdbHost.name)
            putBoolean(KEY_SECURE_DNS, s.secureDns)
            putBoolean(KEY_PREFER_LOCAL_ARTWORK, s.preferLocalArtwork)
            putBoolean(KEY_SCAN_DEVICE_STORAGE, s.scanDeviceStorage)
            putString(KEY_LAYOUT_MODE, s.layoutMode.name)
            putString(KEY_THEME_MODE, s.themeMode.name)
            putString(KEY_HARDWARE_DECODING, s.hardwareDecoding.name)
            putString(KEY_DEFAULT_ASPECT, s.defaultAspect.name)
            putBoolean(KEY_RESUME_PLAYBACK, s.resumePlayback)
            putBoolean(KEY_AUTO_LOAD_SUBTITLES, s.autoLoadSubtitles)
            putString(KEY_SUBTITLE_ENCODING, s.subtitleEncoding)
            putString(KEY_PREFERRED_AUDIO_LANGUAGE, s.preferredAudioLanguage)
            putString(KEY_PREFERRED_SUBTITLE_LANGUAGE, s.preferredSubtitleLanguage)
            putInt(KEY_SEEK_STEP_SECONDS, s.seekStepSeconds)
            putBoolean(KEY_AUTO_PLAY_NEXT, s.autoPlayNext)
        }
    }

    private companion object {
        const val PREFS_NAME = "settings"

        const val KEY_TMDB_API_KEY = "tmdb_api_key"
        const val KEY_TMDB_LANGUAGE = "tmdb_language"
        const val KEY_TMDB_LANGUAGE_CHOSEN = "tmdb_language_chosen"
        const val KEY_TMDB_HOST = "tmdb_host"
        const val KEY_SECURE_DNS = "secure_dns"
        const val KEY_PREFER_LOCAL_ARTWORK = "prefer_local_artwork"
        const val KEY_SCAN_DEVICE_STORAGE = "scan_device_storage"
        const val KEY_LAYOUT_MODE = "layout_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HARDWARE_DECODING = "hardware_decoding"
        const val KEY_DEFAULT_ASPECT = "default_aspect"
        const val KEY_RESUME_PLAYBACK = "resume_playback"
        const val KEY_AUTO_LOAD_SUBTITLES = "auto_load_subtitles"
        const val KEY_SUBTITLE_ENCODING = "subtitle_encoding"
        const val KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language"
        const val KEY_PREFERRED_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        const val KEY_SEEK_STEP_SECONDS = "seek_step_seconds"
        const val KEY_AUTO_PLAY_NEXT = "auto_play_next"

        /**
         * Whether the stored TMDB language is the user's pick. Settings saved before the flag
         * existed always stored a language; the library's metadata is in it, so it is kept.
         */
        fun readLanguageChosen(prefs: SharedPreferences): Boolean =
            if (prefs.contains(KEY_TMDB_LANGUAGE_CHOSEN)) {
                prefs.getBoolean(KEY_TMDB_LANGUAGE_CHOSEN, false)
            } else {
                !prefs.getString(KEY_TMDB_LANGUAGE, null).isNullOrBlank()
            }

        /** The TMDB tag for the device's languages, most preferred first ("hi-IN", "en-GB", else "en-US"). */
        fun deviceTmdbLanguage(): String {
            val locales = LocaleList.getDefault()
            return TmdbLanguages.forLocales((0 until locales.size()).mapNotNull { locales.get(it) })
        }

        fun read(prefs: SharedPreferences, languageChosen: Boolean, deviceLanguage: String): Settings {
            val defaults = Settings()
            val storedLanguage = prefs.getString(KEY_TMDB_LANGUAGE, null)?.takeIf { it.isNotBlank() }
            return Settings(
                tmdbApiKey = prefs.getString(KEY_TMDB_API_KEY, null) ?: defaults.tmdbApiKey,
                tmdbLanguage = storedLanguage?.takeIf { languageChosen } ?: deviceLanguage,
                tmdbHost = enumOrDefault(prefs.getString(KEY_TMDB_HOST, null), defaults.tmdbHost),
                secureDns = prefs.getBoolean(KEY_SECURE_DNS, defaults.secureDns),
                preferLocalArtwork = prefs.getBoolean(KEY_PREFER_LOCAL_ARTWORK, defaults.preferLocalArtwork),
                scanDeviceStorage = prefs.getBoolean(KEY_SCAN_DEVICE_STORAGE, defaults.scanDeviceStorage),
                layoutMode = enumOrDefault(prefs.getString(KEY_LAYOUT_MODE, null), defaults.layoutMode),
                themeMode = enumOrDefault(prefs.getString(KEY_THEME_MODE, null), defaults.themeMode),
                hardwareDecoding = enumOrDefault(prefs.getString(KEY_HARDWARE_DECODING, null), defaults.hardwareDecoding),
                defaultAspect = enumOrDefault(prefs.getString(KEY_DEFAULT_ASPECT, null), defaults.defaultAspect),
                resumePlayback = prefs.getBoolean(KEY_RESUME_PLAYBACK, defaults.resumePlayback),
                autoLoadSubtitles = prefs.getBoolean(KEY_AUTO_LOAD_SUBTITLES, defaults.autoLoadSubtitles),
                subtitleEncoding = prefs.getString(KEY_SUBTITLE_ENCODING, null) ?: defaults.subtitleEncoding,
                preferredAudioLanguage = prefs.getString(KEY_PREFERRED_AUDIO_LANGUAGE, null)
                    ?: defaults.preferredAudioLanguage,
                preferredSubtitleLanguage = prefs.getString(KEY_PREFERRED_SUBTITLE_LANGUAGE, null)
                    ?: defaults.preferredSubtitleLanguage,
                seekStepSeconds = prefs.getInt(KEY_SEEK_STEP_SECONDS, defaults.seekStepSeconds),
                autoPlayNext = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, defaults.autoPlayNext),
            )
        }

        inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
