package com.aakash.novafork.data

import kotlinx.coroutines.flow.StateFlow

enum class TmdbHost(val baseUrl: String, val label: String) {
    DEFAULT("https://api.themoviedb.org/3/", "api.themoviedb.org"),
    /** Official alias; helps when an ISP blocks the main host. */
    ALTERNATE("https://api.tmdb.org/3/", "api.tmdb.org"),
}

/** AUTO picks from the screen: smallest width ≥ 600dp → tablet UI. */
enum class LayoutMode(val label: String) { AUTO("Automatic"), PHONE("Phone"), TABLET("Tablet") }

enum class HwDecoding(val label: String) { AUTO("Automatic"), DISABLED("Software only"), FORCED("Force hardware") }

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

/** Mirrors libVLC MediaPlayer.ScaleType, in the order the player's aspect button cycles through. */
enum class AspectMode(val label: String) {
    BEST_FIT("Fit"),
    FIT_SCREEN("Fit screen"),
    FILL("Stretch"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ORIGINAL("Original size"),
}

data class Settings(
    /** Key pasted by the user. Empty = use the built-in BuildConfig.TMDB_API_KEY, if any. */
    val tmdbApiKey: String = "",
    /** TMDB language, e.g. "en-US", "hi-IN". */
    val tmdbLanguage: String = "en-US",
    val tmdbHost: TmdbHost = TmdbHost.DEFAULT,
    /** Resolve names over DNS-over-HTTPS (Cloudflare, Google fallback) for TMDB and artwork. */
    val secureDns: Boolean = true,
    /** Use poster.jpg/fanart.jpg next to a video even when TMDB has art. */
    val preferLocalArtwork: Boolean = false,
    val scanDeviceStorage: Boolean = true,
    val layoutMode: LayoutMode = LayoutMode.AUTO,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val hardwareDecoding: HwDecoding = HwDecoding.AUTO,
    val defaultAspect: AspectMode = AspectMode.BEST_FIT,
    val resumePlayback: Boolean = true,
    val autoLoadSubtitles: Boolean = true,
    /** libVLC --subsdec-encoding; "" = automatic (UTF-8 with fallback). */
    val subtitleEncoding: String = "",
    /** ISO 639-2 code ("eng", "hin") or "" for the file's default. */
    val preferredAudioLanguage: String = "",
    val preferredSubtitleLanguage: String = "",
    val seekStepSeconds: Int = 10,
    /** Play the next episode automatically when one ends. */
    val autoPlayNext: Boolean = true,
)

interface SettingsStore {
    val settings: StateFlow<Settings>
    fun update(transform: (Settings) -> Settings)

    /** The key in use: the user's key if set, else the built-in one, else "". */
    val effectiveTmdbKey: String

    /** True when the APK was built with a TMDB key (BuildConfig.TMDB_API_KEY not blank). */
    val hasBuiltInTmdbKey: Boolean
}
