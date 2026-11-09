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

/** Preferences › Appearance › Home hero: what the Home hero's picture is made of. */
/** How the library pages lay out their titles (Preferences › Appearance › Library). */
enum class LibraryLayout(val label: String) {
    GRID("Grid"),

    /** One title per row: a small poster, the title and its caption. */
    LIST("List"),
}

/** Posters per row in the library grids; [AUTOMATIC] fits them to the screen (pinch still works). */
enum class PosterColumns(val label: String, val count: Int) {
    AUTOMATIC("Automatic", 0),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
}

enum class HeroStyle(val label: String) {
    /** Apple TV: the full-bleed backdrop, title and meta over its foot; no poster card. */
    BACKDROP("Backdrop"),

    /** A large centred poster over a dimmed, blurred wash of the backdrop. */
    POSTER("Poster"),

    /** The backdrop with the poster card inset on the left, the title beside it. */
    BOTH("Both"),
}

/** Preferences › Appearance › Title page header: the top of a movie, episode or show page. */
enum class DetailsHeaderStyle(val label: String) {
    /** The full-bleed backdrop with the title, meta and buttons over and below its foot. */
    BACKDROP("Backdrop"),

    /** The backdrop with the poster beside the title. */
    POSTER_AND_BACKDROP("Poster and backdrop"),
}

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
     * Quietly add the best OpenSubtitles subtitle (once per video) when a video has none in the
     * preferred language: no message, not turned on, just in the subtitle menu.
     */
    val fetchMissingSubtitles: Boolean = false,
    /**
     * How tinted the app's glass buttons and bars are, 0 (clear) to 1 (tinted), as iOS 27's
     * Liquid Glass slider sets it. The default is iOS 27's (ui.common.DEFAULT_GLASS_TINT).
     */
    val glassTint: Float = 1f,
    /** Seconds one song fades into the next, 0 (gapless, no fade) to 8. */
    val crossfadeSeconds: Int = 0,
    /**
     * Apple Music's Autoplay (the infinity button): when the queue runs out, songs like the ones
     * played go on from the library itself. Off: the music stops at the end of the queue.
     */
    val musicAutoplay: Boolean = true,
    /** Autoplay after a whole album: the next album (the artist's, else the most alike) instead of mixed songs. */
    val autoplayAlbums: Boolean = false,
    /**
     * Sound Check: songs play at the volume their ReplayGain (or iTunNORM) tag asks for, so
     * quiet and loud albums sit at about the same level. Songs without the tag play unchanged.
     */
    val soundCheck: Boolean = false,
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
    /** Subtitle text colour as 0xRRGGBB (white by default). */
    val subtitleColor: Int = 0xFFFFFF,
    /** Subtitle text in bold. */
    val subtitleBold: Boolean = false,
    /** Subtitles turn on by themselves when the phone's media volume is off as a video opens. */
    val captionsWhenMuted: Boolean = false,
    /** The MAC address of a NAS to wake (Wake-on-LAN) before the library visits it; empty = never. */
    val wakeOnLanMac: String = "",
    /** Scan the library in the background every this many hours; 0 = only when the app opens. */
    val scanEveryHours: Int = 0,
    /** A quiet note when episodes arrive for a show being watched (after a background scan). */
    val newEpisodeAlerts: Boolean = false,
    /** The player shows its stream's bitrate and dropped frames in a corner. */
    val showStreamStats: Boolean = false,
    /** How much of a network video libVLC buffers ahead, in ms (more rides out a slow connection). */
    val networkBufferMs: Int = 1500,
    /** Leaving the app while a video plays shrinks it into a picture-in-picture window. */
    val pipOnLeave: Boolean = false,
    /** The small ticks on taps, swipes and switches; off silences every one. */
    val haptics: Boolean = true,
    /** Opening Hidden Videos asks for a fingerprint or the screen lock first. */
    val requireFingerprint: Boolean = false,
    /** Subtitle typeface: an Android font family ("serif", "monospace" …) or "" for libVLC's own. */
    val subtitleFont: String = "",
    /** Subtitle outline thickness as libVLC takes it: 0 none, 2 thin, 4 normal, 6 thick. */
    val subtitleOutline: Int = 4,
    /** Subtitles turn on by themselves only for a forced track (signs and foreign lines). */
    val forcedSubtitlesOnly: Boolean = false,
    /** Deinterlacing: -1 libVLC decides, 0 off, 1 on (for old TV recordings with comb lines). */
    val deinterlace: Int = -1,
    /** How much of the screen's width each side's double-tap seek zone takes, in percent. */
    val doubleTapZonePercent: Int = 33,
    /** Player buttons the person has hidden (keys of PlayerButton), comma separated. */
    val hiddenPlayerButtons: String = "",
    /** Home's rows in the person's order (HomeRow keys, comma separated); "" = the default order. */
    val homeRowOrder: String = "",
    /** Home rows turned off (HomeRow keys, comma separated). */
    val homeHiddenRows: String = "",
    /** Songs without a .lrc get time-synced lyrics from LRCLIB (title and artist are sent). */
    val onlineLyrics: Boolean = false,
    /** Artist photos and missing album covers from Deezer's public catalogue (names are sent). */
    val onlineMusicArt: Boolean = false,
    /** The tab the app opens on: "VIDEO", "AUDIO" or "SEARCH". */
    val startTab: String = "VIDEO",
    /** Unwatched episodes hide their summary and blur their still. */
    val spoilerSafe: Boolean = false,
    /** Music paused by headphones going away carries on when they come back (within 30 minutes). */
    val resumeOnHeadphones: Boolean = false,
    /** Folders whose videos stay out of the library's screens, one per line (Browse still lists them). */
    val hiddenFolders: String = "",
    /** A small colour dot on posters says which source a video is on. */
    val sourceDots: Boolean = false,
    /** The Home hero's look; see [HeroStyle]. */
    val heroStyle: HeroStyle = HeroStyle.BACKDROP,
    /** Library pages as a poster grid or a list. */
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    /** Posters per row in the library grids. */
    val posterColumns: PosterColumns = PosterColumns.AUTOMATIC,
    /** The top of a title's page; see [DetailsHeaderStyle]. */
    val detailsHeaderStyle: DetailsHeaderStyle = DetailsHeaderStyle.BACKDROP,
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
