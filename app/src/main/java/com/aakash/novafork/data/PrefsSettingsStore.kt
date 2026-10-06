package com.aakash.novafork.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aakash.novafork.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [SettingsStore] persisted in the "settings" SharedPreferences file. Enums are stored by name. */
class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read(prefs))
    private val writeLock = Any()

    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun update(transform: (Settings) -> Settings) {
        synchronized(writeLock) {
            val current = state.value
            val next = transform(current)
            if (next == current) return
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
            putString(KEY_TMDB_LANGUAGE, s.tmdbLanguage)
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

        fun read(prefs: SharedPreferences): Settings {
            val defaults = Settings()
            return Settings(
                tmdbApiKey = prefs.getString(KEY_TMDB_API_KEY, null) ?: defaults.tmdbApiKey,
                tmdbLanguage = prefs.getString(KEY_TMDB_LANGUAGE, null)?.takeIf { it.isNotBlank() }
                    ?: defaults.tmdbLanguage,
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
