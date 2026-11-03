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
    /**
     * Background play for videos: leaving the player while a video plays (Home, screen off,
     * another app) keeps its sound playing, with controls in a notification. Off (the default,
     * owner's choice): leaving the player pauses the video. Music always plays in the background.
     */
    val backgroundAudio: Boolean = false,
    /** OpenSubtitles.com API key pasted by the user. Empty = the built-in BuildConfig.OPENSUBTITLES_API_KEY, if any. */
    val opensubtitlesApiKey: String = "",
    /** Optional OpenSubtitles.com account; logged in, the daily download quota is bigger. */
    val opensubtitlesUsername: String = "",
    val opensubtitlesPassword: String = "",
    /**
     * Languages to download subtitles in, as typed ("en, fr"; codes or English names), most
     * wanted first. "" = [preferredSubtitleLanguage], then the device's languages.
     */
    val subtitleLanguages: String = "",
    /** Download the best subtitle (once per video) when a video has none in the first of those languages. */
    val autoDownloadSubtitles: Boolean = false,
    /**
     * How tinted the app's glass buttons and bars are, 0 (clear) to 1 (tinted), as iOS 27's
     * Liquid Glass slider sets it. The default is iOS 27's (ui.common.DEFAULT_GLASS_TINT).
     */
    val glassTint: Float = 1f,
    /** Seconds one song fades into the next, 0 (gapless, no fade) to 8. */
    val crossfadeSeconds: Int = 0,
    /**
     * The equaliser, for songs and videos alike: the name of a preset of libVLC's ("Flat",
     * "Rock" …), [EQUALIZER_CUSTOM] for [equalizerBands], or "" for off.
     */
    val equalizerPreset: String = "",
    /** The custom equaliser's ten band gains in dB (-20 to 20), comma separated; "" = all flat. */
    val equalizerBands: String = "",
    /** Night mode for videos: quiet dialogue louder and loud scenes softer (a compressor). */
    val nightMode: Boolean = false,
    /** Subtitle text size, percent of libVLC's own (50 to 200). */
    val subtitleSize: Int = 100,
    /** A dark box behind subtitle lines, for reading over bright pictures. */
    val subtitleBackground: Boolean = false,
    /** How far above the bottom of the picture subtitles sit, in px of the video (0 = libVLC's own). */
    val subtitleMargin: Int = 0,
    /** The small ticks on taps, swipes and switches; off silences every one. */
    val haptics: Boolean = true,
)

/** [Settings.equalizerPreset]'s value for the person's own band gains. */
const val EQUALIZER_CUSTOM = "Custom"

interface SettingsStore {
    val settings: StateFlow<Settings>
    fun update(transform: (Settings) -> Settings)

    /** The key in use: the user's key if set, else the built-in one, else "". */
    val effectiveTmdbKey: String

    /** True when the APK was built with a TMDB key (BuildConfig.TMDB_API_KEY not blank). */
    val hasBuiltInTmdbKey: Boolean
}
