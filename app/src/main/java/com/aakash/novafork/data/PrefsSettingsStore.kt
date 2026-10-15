package com.aakash.novafork.data

import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import androidx.core.content.edit
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.core.tmdb.TmdbLanguages
import com.aakash.novafork.data.backup.SettingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SettingsStore] persisted in the "settings" SharedPreferences file. Enums are stored by name.
 *
 * Until the user picks a TMDB language, [Settings.tmdbLanguage] follows the device's languages
 * (see [TmdbLanguages]); picking one stores it for good. A value stored with another type than
 * [KEY_TYPES] says (a damaged or hand-edited file) reads as the default instead of failing.
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
            putBoolean(KEY_BACKGROUND_AUDIO, s.backgroundAudio)
            putString(KEY_OPENSUBTITLES_API_KEY, s.opensubtitlesApiKey)
            putString(KEY_OPENSUBTITLES_USERNAME, s.opensubtitlesUsername)
            putString(KEY_OPENSUBTITLES_PASSWORD, s.opensubtitlesPassword)
            putString(KEY_SUBTITLE_LANGUAGES, s.subtitleLanguages)
            putBoolean(KEY_AUTO_DOWNLOAD_SUBTITLES, s.autoDownloadSubtitles)
        }
    }

    internal companion object {
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
        const val KEY_BACKGROUND_AUDIO = "background_audio"
        const val KEY_OPENSUBTITLES_API_KEY = "opensubtitles_api_key"
        const val KEY_OPENSUBTITLES_USERNAME = "opensubtitles_username"
        const val KEY_OPENSUBTITLES_PASSWORD = "opensubtitles_password"
        const val KEY_SUBTITLE_LANGUAGES = "subtitle_languages"
        const val KEY_AUTO_DOWNLOAD_SUBTITLES = "auto_download_subtitles"

        /**
         * The type [write] stores each key with. A restored backup's entry of another type is
         * not written (see com.aakash.novafork.data.backup.SettingsBackup.restore).
         */
        val KEY_TYPES: Map<String, SettingType> = mapOf(
            KEY_TMDB_API_KEY to SettingType.STRING,
            KEY_TMDB_LANGUAGE to SettingType.STRING,
            KEY_TMDB_LANGUAGE_CHOSEN to SettingType.BOOLEAN,
            KEY_TMDB_HOST to SettingType.STRING,
            KEY_SECURE_DNS to SettingType.BOOLEAN,
            KEY_PREFER_LOCAL_ARTWORK to SettingType.BOOLEAN,
            KEY_SCAN_DEVICE_STORAGE to SettingType.BOOLEAN,
            KEY_LAYOUT_MODE to SettingType.STRING,
            KEY_THEME_MODE to SettingType.STRING,
            KEY_HARDWARE_DECODING to SettingType.STRING,
            KEY_DEFAULT_ASPECT to SettingType.STRING,
            KEY_RESUME_PLAYBACK to SettingType.BOOLEAN,
            KEY_AUTO_LOAD_SUBTITLES to SettingType.BOOLEAN,
            KEY_SUBTITLE_ENCODING to SettingType.STRING,
            KEY_PREFERRED_AUDIO_LANGUAGE to SettingType.STRING,
            KEY_PREFERRED_SUBTITLE_LANGUAGE to SettingType.STRING,
            KEY_SEEK_STEP_SECONDS to SettingType.INT,
            KEY_AUTO_PLAY_NEXT to SettingType.BOOLEAN,
            KEY_BACKGROUND_AUDIO to SettingType.BOOLEAN,
            KEY_OPENSUBTITLES_API_KEY to SettingType.STRING,
            KEY_OPENSUBTITLES_USERNAME to SettingType.STRING,
            KEY_OPENSUBTITLES_PASSWORD to SettingType.STRING,
            KEY_SUBTITLE_LANGUAGES to SettingType.STRING,
            KEY_AUTO_DOWNLOAD_SUBTITLES to SettingType.BOOLEAN,
        )

        /**
         * Whether the stored TMDB language is the user's pick. Settings saved before the flag
         * existed always stored a language; the library's metadata is in it, so it is kept.
         */
        fun readLanguageChosen(prefs: SharedPreferences): Boolean =
            if (prefs.contains(KEY_TMDB_LANGUAGE_CHOSEN)) {
                prefs.boolean(KEY_TMDB_LANGUAGE_CHOSEN, false)
            } else {
                !prefs.string(KEY_TMDB_LANGUAGE).isNullOrBlank()
            }

        /** The TMDB tag for the device's languages, most preferred first ("hi-IN", "en-GB", else "en-US"). */
        fun deviceTmdbLanguage(): String {
            val locales = LocaleList.getDefault()
            return TmdbLanguages.forLocales((0 until locales.size()).mapNotNull { locales.get(it) })
        }

        fun read(prefs: SharedPreferences, languageChosen: Boolean, deviceLanguage: String): Settings {
            val defaults = Settings()
            val storedLanguage = prefs.string(KEY_TMDB_LANGUAGE)?.takeIf { it.isNotBlank() }
            return Settings(
                tmdbApiKey = prefs.string(KEY_TMDB_API_KEY) ?: defaults.tmdbApiKey,
                tmdbLanguage = storedLanguage?.takeIf { languageChosen } ?: deviceLanguage,
                tmdbHost = enumOrDefault(prefs.string(KEY_TMDB_HOST), defaults.tmdbHost),
                secureDns = prefs.boolean(KEY_SECURE_DNS, defaults.secureDns),
                preferLocalArtwork = prefs.boolean(KEY_PREFER_LOCAL_ARTWORK, defaults.preferLocalArtwork),
                scanDeviceStorage = prefs.boolean(KEY_SCAN_DEVICE_STORAGE, defaults.scanDeviceStorage),
                layoutMode = enumOrDefault(prefs.string(KEY_LAYOUT_MODE), defaults.layoutMode),
                themeMode = enumOrDefault(prefs.string(KEY_THEME_MODE), defaults.themeMode),
                hardwareDecoding = enumOrDefault(prefs.string(KEY_HARDWARE_DECODING), defaults.hardwareDecoding),
                defaultAspect = enumOrDefault(prefs.string(KEY_DEFAULT_ASPECT), defaults.defaultAspect),
                resumePlayback = prefs.boolean(KEY_RESUME_PLAYBACK, defaults.resumePlayback),
                autoLoadSubtitles = prefs.boolean(KEY_AUTO_LOAD_SUBTITLES, defaults.autoLoadSubtitles),
                subtitleEncoding = prefs.string(KEY_SUBTITLE_ENCODING) ?: defaults.subtitleEncoding,
                preferredAudioLanguage = prefs.string(KEY_PREFERRED_AUDIO_LANGUAGE)
                    ?: defaults.preferredAudioLanguage,
                preferredSubtitleLanguage = prefs.string(KEY_PREFERRED_SUBTITLE_LANGUAGE)
                    ?: defaults.preferredSubtitleLanguage,
                seekStepSeconds = prefs.int(KEY_SEEK_STEP_SECONDS, defaults.seekStepSeconds),
                autoPlayNext = prefs.boolean(KEY_AUTO_PLAY_NEXT, defaults.autoPlayNext),
                backgroundAudio = prefs.boolean(KEY_BACKGROUND_AUDIO, defaults.backgroundAudio),
                opensubtitlesApiKey = prefs.string(KEY_OPENSUBTITLES_API_KEY) ?: defaults.opensubtitlesApiKey,
                opensubtitlesUsername = prefs.string(KEY_OPENSUBTITLES_USERNAME) ?: defaults.opensubtitlesUsername,
                opensubtitlesPassword = prefs.string(KEY_OPENSUBTITLES_PASSWORD) ?: defaults.opensubtitlesPassword,
                subtitleLanguages = prefs.string(KEY_SUBTITLE_LANGUAGES) ?: defaults.subtitleLanguages,
                autoDownloadSubtitles = prefs.boolean(KEY_AUTO_DOWNLOAD_SUBTITLES, defaults.autoDownloadSubtitles),
            )
        }

        inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default

        // SharedPreferences casts what it holds to the type asked for: a key of another type throws.
        private fun SharedPreferences.string(key: String): String? =
            try {
                getString(key, null)
            } catch (e: ClassCastException) {
                null
            }

        private fun SharedPreferences.boolean(key: String, default: Boolean): Boolean =
            try {
                getBoolean(key, default)
            } catch (e: ClassCastException) {
                default
            }

        private fun SharedPreferences.int(key: String, default: Int): Int =
            try {
                getInt(key, default)
            } catch (e: ClassCastException) {
                default
            }
    }
}
