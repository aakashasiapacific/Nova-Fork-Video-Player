package com.aakash.novafork.player

import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesException
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesLanguages
import com.aakash.novafork.core.opensubtitles.SubtitleResult
import com.aakash.novafork.core.opensubtitles.SubtitleSearchRequest
import com.aakash.novafork.core.parse.NameParser
import com.aakash.novafork.core.parse.ParsedKind
import com.aakash.novafork.core.subs.SubtitleRules
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.ui.model.SubtitleResultUi
import java.util.Locale

/** The plain decisions behind subtitle downloads: which languages, what to search for, file names. */
internal object SubtitleDownloadRules {
    /** Languages taken from the device when the setting is empty, at most. */
    private const val MAX_DERIVED_LANGUAGES = 3
    private const val FALLBACK_LANGUAGE = "en"

    /**
     * The languages to download in, most wanted first: [setting] ("en, fr") when it names any,
     * else [preferredSubtitleLanguage] ("eng") and the [deviceLocales]; English when nothing does.
     */
    fun languages(setting: String, preferredSubtitleLanguage: String, deviceLocales: List<Locale>): List<String> {
        val chosen = OpenSubtitlesLanguages.parse(setting)
        if (chosen.isNotEmpty()) return chosen
        val derived = buildList {
            addAll(OpenSubtitlesLanguages.codesFor(preferredSubtitleLanguage))
            for (locale in deviceLocales) addAll(OpenSubtitlesLanguages.forLocale(locale))
        }.distinct()
        return derived.take(MAX_DERIVED_LANGUAGES).ifEmpty { listOf(FALLBACK_LANGUAGE) }
    }

    /** "English, Portuguese (Brazil)". */
    fun languagesLabel(languages: List<String>, locale: Locale = Locale.getDefault()): String =
        languages.joinToString(", ") { OpenSubtitlesLanguages.displayName(it, locale) }

    /**
     * What OpenSubtitles is asked for the video: the library's TMDB match (show id, season and
     * episode for an episode), its title and year, else what the file name says. [movieHash] is
     * the file's, when it could be read.
     */
    fun searchRequest(entry: VideoWithMeta?, fileName: String, languages: List<String>, movieHash: String?): SubtitleSearchRequest {
        val video = entry?.video
        if (video == null) {
            val parsed = NameParser.parse(fileName)
            val episode = parsed.episode?.takeIf { parsed.kind == ParsedKind.EPISODE }
            return SubtitleSearchRequest(
                languages = languages,
                movieHash = movieHash,
                title = parsed.title,
                year = parsed.year,
                season = parsed.season.takeIf { episode != null },
                episode = episode,
            )
        }
        val meta = entry.meta
        val episode = video.episode?.takeIf { video.kind == VideoKind.EPISODE }
        // A movie match says nothing about an episode, and the other way round.
        val matchesKind = meta != null && (if (episode != null) meta.type == "tv" else meta.type == "movie")
        val tmdbId = meta?.tmdbId?.takeIf { it > 0 && matchesKind }
        val title = (if (matchesKind) meta?.title else null)?.takeIf { it.isNotBlank() } ?: video.parsedTitle
        val year = if (matchesKind) meta?.year ?: video.parsedYear else video.parsedYear
        return SubtitleSearchRequest(
            languages = languages,
            movieHash = movieHash,
            tmdbId = tmdbId,
            title = title,
            year = year,
            season = video.season.takeIf { episode != null },
            episode = episode,
        )
    }

    /**
     * "en.6224976" — language and subtitle id: the same subtitle is never downloaded twice, and
     * the language part is what track matching reads.
     */
    fun fileStem(result: SubtitleResult): String {
        val language = result.language.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "und" }
        val id = result.id.filter { it.isLetterOrDigit() }.ifEmpty { result.fileId.toString() }
        return "$language.$id"
    }

    /** [fileStem] with the extension of the file OpenSubtitles sent ("srt" when it isn't a subtitle one). */
    fun fileName(result: SubtitleResult, downloadedName: String?): String {
        val extension = listOfNotNull(downloadedName, result.fileName)
            .map { VideoFiles.extension(it) }
            .firstOrNull { it.isNotEmpty() && SubtitleRules.isSubtitle("subtitle.$it") }
            ?: "srt"
        return "${fileStem(result)}.$extension"
    }

    /** True when [fileName] is a download of [result] ([fileStem] plus an extension). */
    fun isDownloadOf(fileName: String, result: SubtitleResult): Boolean =
        VideoFiles.baseName(fileName).equals(fileStem(result), ignoreCase = true)

    fun toUi(result: SubtitleResult, downloadedNames: Collection<String>, locale: Locale = Locale.getDefault()): SubtitleResultUi =
        SubtitleResultUi(
            id = result.id,
            language = OpenSubtitlesLanguages.displayName(result.language, locale),
            release = result.release,
            matchesFile = result.movieHashMatch,
            hearingImpaired = result.hearingImpaired,
            downloadCount = result.downloadCount,
            machineTranslated = result.aiTranslated || result.machineTranslated,
            downloaded = downloadedNames.any { isDownloadOf(it, result) },
        )

    /** The names a language goes by in track names and file names ("pt-br" → pt, por, portuguese …). */
    fun aliases(language: String): Set<String> = TrackLanguages.aliases(language.substringBefore('-'))

    /**
     * Whether a subtitle file says it is in the language of [aliases]: only the part after the
     * video's own name counts ("Movie.eng.srt" for "Movie.mkv"; "en.6224976.srt" for a download).
     */
    fun fileIsInLanguage(subtitleFileName: String, videoFileName: String, aliases: Set<String>): Boolean {
        if (aliases.isEmpty()) return false
        val videoBase = VideoFiles.baseName(videoFileName)
        val name = VideoFiles.baseName(subtitleFileName)
        val languagePart = if (videoBase.isNotEmpty() && name.startsWith(videoBase, ignoreCase = true)) {
            name.substring(videoBase.length)
        } else {
            name
        }
        return TrackLanguages.matches(languagePart, aliases)
    }

    /**
     * The languages worth fetching without asking: [languages] in order, up to the first one the
     * video already has a subtitle in ([has]). Empty when it has the first.
     */
    fun missingLanguages(languages: List<String>, has: (String) -> Boolean): List<String> =
        languages.takeWhile { !has(it) }

    /** A message to show for a failed search or download. */
    fun errorMessage(error: Throwable): String = when (error) {
        is OpenSubtitlesException -> error.message ?: OpenSubtitlesException.NETWORK_MESSAGE
        else -> "Something went wrong with OpenSubtitles. Try again."
    }
}
