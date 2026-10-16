package com.aakash.novafork.core.opensubtitles

import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What is known about a video, for [OpenSubtitlesClient.search]. Every field is optional. */
data class SubtitleSearchRequest(
    /** OpenSubtitles language codes ("en", "pt-br"), see [OpenSubtitlesLanguages]. Empty = every language. */
    val languages: List<String> = emptyList(),
    /** [OpenSubtitlesHash] of the file, when it could be read. */
    val movieHash: String? = null,
    /** TMDB id of the movie, or of the show for an episode. */
    val tmdbId: Int? = null,
    /** IMDb id of the movie, or of the show for an episode: "tt0133093" or "133093". */
    val imdbId: String? = null,
    /** Clean title of the movie or the show ("Dune Part Two", "Severance"). */
    val title: String? = null,
    /** Release year of a movie; ignored for episodes (a show's year says little about its later seasons). */
    val year: Int? = null,
    val season: Int? = null,
    /** Set for an episode: ids then name the show, and [season] / [episode] the episode. */
    val episode: Int? = null,
) {
    val isEpisode: Boolean get() = episode != null
}

/** One subtitle OpenSubtitles offers. */
data class SubtitleResult(
    /** The subtitle's id ("6224976"). */
    val id: String,
    /** Its first file; pass to [OpenSubtitlesClient.download]. */
    val fileId: Long,
    val fileName: String?,
    /** OpenSubtitles language code, lower case: "en", "pt-br", "zh-cn"; "" when unknown. */
    val language: String,
    /** Release name ("Dune.2021.1080p.WEBRip.x264-RARBG"), else the file name, else "". */
    val release: String,
    val downloadCount: Int,
    val hearingImpaired: Boolean,
    /** Timed for exactly this file: OpenSubtitles knows the file's movie hash. */
    val movieHashMatch: Boolean,
    /** Frames per second it was timed for; null when unknown. */
    val fps: Double?,
    val aiTranslated: Boolean,
    val machineTranslated: Boolean,
)

/** The answer to POST /download: a temporary link to the file and the quota left. */
data class SubtitleDownloadLink(
    val link: String,
    val fileName: String?,
    /** Downloads left today; null when not reported. */
    val remaining: Int?,
    /** When the quota renews, if reported. */
    val resetAt: Instant?,
)

/** A subtitle file fetched by [OpenSubtitlesClient.download]. */
class DownloadedSubtitle(
    /** The name OpenSubtitles gives the file ("Dune.2021.1080p.WEBRip.x264-RARBG.srt"). */
    val fileName: String,
    val bytes: ByteArray,
    /** Downloads left today; null when not reported. */
    val remaining: Int?,
    val resetAt: Instant?,
)

/** An optional OpenSubtitles.com account: logged-in users get a bigger download quota. */
data class OpenSubtitlesLogin(val username: String, val password: String)

/** Why an OpenSubtitles call failed, with a message that can be shown as it is. */
class OpenSubtitlesException(
    val reason: Reason,
    message: String,
    /** The HTTP status, when there was an answer. */
    val code: Int? = null,
    /** [Reason.QUOTA]: when the download quota renews, if known. */
    val resetAt: Instant? = null,
    /** [Reason.QUOTA]: how long until then, as the server words it ("07 hours and 30 minutes"). */
    val resetIn: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause) {

    enum class Reason {
        /** No API key configured. */
        NO_KEY,

        /** The API key (or the app's User-Agent) was refused: 401 without a login, 403. */
        BAD_KEY,

        /** The username or password was refused. */
        LOGIN_FAILED,

        /** The daily download quota is used up (406, or 429 on a download). */
        QUOTA,

        /** Too many requests in a short time, still after waiting once. */
        RATE_LIMITED,

        /** The subtitle or its link is gone (404, 410). */
        NOT_FOUND,

        /** HTTP 5xx. */
        SERVER,

        /** Another HTTP error. */
        HTTP,

        /** No answer: no network, DNS, TLS, timeout. */
        NETWORK,

        /** An answer that could not be read, or a file that is too large or empty. */
        BAD_RESPONSE,
    }

    companion object {
        const val NO_KEY_MESSAGE = "Add an OpenSubtitles API key in Settings to download subtitles"
        const val BAD_KEY_MESSAGE = "OpenSubtitles didn't accept the API key. Check it in Settings."
        const val LOGIN_FAILED_MESSAGE = "OpenSubtitles didn't accept the username or password. Check them in Settings."
        const val RATE_LIMITED_MESSAGE = "OpenSubtitles is busy. Try again in a moment."
        const val NETWORK_MESSAGE = "Couldn't reach OpenSubtitles. Check your connection."
        const val NOT_FOUND_MESSAGE = "That subtitle is no longer available on OpenSubtitles"
        const val BAD_RESPONSE_MESSAGE = "OpenSubtitles sent an answer Nova Fork can't read"

        private const val HOURS_SHOWN_AS_TIME = 20L

        /**
         * "Download limit reached, resets at 13:03" (in [zone], [locale]'s short time format),
         * with the date when that is not within the next [HOURS_SHOWN_AS_TIME] hours. Without a
         * reset time, "resets in [resetIn]" or the plain sentence.
         */
        fun quotaMessage(
            resetAt: Instant?,
            resetIn: String?,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
        ): String {
            val base = "Download limit reached"
            if (resetAt != null && resetAt.isAfter(now)) {
                val local = resetAt.atZone(zone)
                val soon = !resetAt.isAfter(now.plusSeconds(HOURS_SHOWN_AS_TIME * 3_600))
                val style = if (soon) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                return "$base, resets at ${local.format(style.withLocale(locale))}"
            }
            val wait = resetIn?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
            return if (wait != null) "$base, resets in $wait" else "$base. Try again tomorrow."
        }
    }
}
