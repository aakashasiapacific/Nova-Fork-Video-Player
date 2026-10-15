package com.aakash.novafork.player

import com.aakash.novafork.core.opensubtitles.OpenSubtitlesException
import com.aakash.novafork.core.opensubtitles.SubtitleResult
import com.aakash.novafork.core.opensubtitles.SubtitleSearchRequest
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import java.io.IOException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDownloadRulesTest {

    private fun video(
        kind: VideoKind,
        title: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        metadataKey: String? = null,
    ) = VideoEntity(
        id = 7,
        sourceId = 1,
        uri = "content://media/external/video/media/7",
        fileName = "file.mkv",
        folder = "Movies",
        kind = kind,
        parsedTitle = title,
        parsedYear = year,
        season = season,
        episode = episode,
        metadataKey = metadataKey,
    )

    private fun result(
        id: String = "6224976",
        language: String = "en",
        fileName: String? = "Dune.2021.srt",
        machine: Boolean = false,
    ) = SubtitleResult(
        id = id,
        fileId = 6994430,
        fileName = fileName,
        language = language,
        release = "Dune.2021.1080p.WEBRip.x264-RARBG",
        downloadCount = 18946,
        hearingImpaired = true,
        movieHashMatch = true,
        fps = 23.976,
        aiTranslated = false,
        machineTranslated = machine,
    )

    @Test
    fun `the languages setting wins when it names any`() {
        val languages = SubtitleDownloadRules.languages("fr, English", "hin", listOf(Locale.GERMANY))
        assertEquals(listOf("fr", "en"), languages)
    }

    @Test
    fun `without a setting the subtitle preference then the device languages are used`() {
        val languages = SubtitleDownloadRules.languages(
            setting = " ",
            preferredSubtitleLanguage = "hin",
            deviceLocales = listOf(Locale.forLanguageTag("en-IN"), Locale.forLanguageTag("pt-BR"), Locale.FRANCE),
        )
        assertEquals(listOf("hi", "en", "pt-br"), languages)
    }

    @Test
    fun `nothing known falls back to English`() {
        assertEquals(listOf("en"), SubtitleDownloadRules.languages("", "", emptyList()))
        assertEquals(listOf("en"), SubtitleDownloadRules.languages("??", "", emptyList()))
    }

    @Test
    fun `languages label names them`() {
        assertEquals("English, Portuguese (Brazil)", SubtitleDownloadRules.languagesLabel(listOf("en", "pt-br"), Locale.ENGLISH))
    }

    @Test
    fun `a matched movie is searched by its TMDB id, title and year`() {
        val entry = VideoWithMeta(
            video = video(VideoKind.MOVIE, title = "Dune Part Two", year = 2023, metadataKey = "movie:693134"),
            meta = MetadataEntity(key = "movie:693134", tmdbId = 693134, type = "movie", title = "Dune: Part Two", year = 2024),
        )
        val request = SubtitleDownloadRules.searchRequest(entry, "file.mkv", listOf("en"), "0123456789abcdef")
        assertEquals(693134, request.tmdbId)
        assertEquals("Dune: Part Two", request.title)
        assertEquals(2024, request.year)
        assertNull(request.episode)
        assertNull(request.season)
        assertEquals("0123456789abcdef", request.movieHash)
        assertEquals(listOf("en"), request.languages)
    }

    @Test
    fun `an episode is searched by its show, season and episode`() {
        val entry = VideoWithMeta(
            video = video(VideoKind.EPISODE, title = "Severance", season = 2, episode = 3, metadataKey = "tv:95396"),
            meta = MetadataEntity(key = "tv:95396", tmdbId = 95396, type = "tv", title = "Severance", year = 2022),
        )
        val request = SubtitleDownloadRules.searchRequest(entry, "file.mkv", listOf("en"), null)
        assertEquals(95396, request.tmdbId)
        assertEquals(2, request.season)
        assertEquals(3, request.episode)
        assertEquals("Severance", request.title)
        assertTrue(request.isEpisode)
    }

    @Test
    fun `a match of the other kind is not used`() {
        val entry = VideoWithMeta(
            video = video(VideoKind.EPISODE, title = "The Office", season = 1, episode = 1, metadataKey = "movie:1"),
            meta = MetadataEntity(key = "movie:1", tmdbId = 1, type = "movie", title = "The Office Movie"),
        )
        val request = SubtitleDownloadRules.searchRequest(entry, "file.mkv", emptyList(), null)
        assertNull(request.tmdbId)
        assertEquals("The Office", request.title)
    }

    @Test
    fun `an unmatched video uses its parsed name`() {
        val entry = VideoWithMeta(video = video(VideoKind.MOVIE, title = "Some Film", year = 1999), meta = null)
        val request = SubtitleDownloadRules.searchRequest(entry, "file.mkv", emptyList(), null)
        assertNull(request.tmdbId)
        assertEquals("Some Film", request.title)
        assertEquals(1999, request.year)
    }

    @Test
    fun `a server match without a TMDB id searches by title`() {
        val entry = VideoWithMeta(
            video = video(VideoKind.MOVIE, title = "Heat", metadataKey = "server:3:movie:abc"),
            meta = MetadataEntity(key = "server:3:movie:abc", tmdbId = 0, type = "movie", title = "Heat", year = 1995),
        )
        val request = SubtitleDownloadRules.searchRequest(entry, "file.mkv", emptyList(), null)
        assertNull(request.tmdbId)
        assertEquals("Heat", request.title)
        assertEquals(1995, request.year)
    }

    @Test
    fun `a link is searched by what its file name says`() {
        val episode = SubtitleDownloadRules.searchRequest(null, "Severance.S02E03.1080p.WEB.h264.mkv", listOf("en"), null)
        assertEquals("Severance", episode.title)
        assertEquals(2, episode.season)
        assertEquals(3, episode.episode)

        val movie = SubtitleDownloadRules.searchRequest(null, "Dune.Part.Two.2024.2160p.mkv", listOf("en"), null)
        assertEquals(2024, movie.year)
        assertNull(movie.episode)
        assertNull(movie.season)
    }

    @Test
    fun `downloaded files are named by language and subtitle id`() {
        assertEquals("en.6224976", SubtitleDownloadRules.fileStem(result()))
        assertEquals("en.6224976.srt", SubtitleDownloadRules.fileName(result(), "Dune.2021.1080p.srt"))
        assertEquals("pt-br.6224976.ass", SubtitleDownloadRules.fileName(result(language = "pt-BR"), "x.ASS"))
        // Not a subtitle extension: the result's file name, else srt.
        assertEquals("en.6224976.srt", SubtitleDownloadRules.fileName(result(), "download.php"))
        assertEquals("en.6224976.srt", SubtitleDownloadRules.fileName(result(fileName = null), null))
        assertEquals("und.6224976.srt", SubtitleDownloadRules.fileName(result(language = ""), null))
        assertEquals("en.12ab.srt", SubtitleDownloadRules.fileName(result(id = "12/ab"), null))
    }

    @Test
    fun `a download is recognised by its stem`() {
        assertTrue(SubtitleDownloadRules.isDownloadOf("en.6224976.srt", result()))
        assertTrue(SubtitleDownloadRules.isDownloadOf("EN.6224976.vtt", result()))
        assertFalse(SubtitleDownloadRules.isDownloadOf("en.6224977.srt", result()))
        assertFalse(SubtitleDownloadRules.isDownloadOf("fr.6224976.srt", result()))
    }

    @Test
    fun `results become rows`() {
        val row = SubtitleDownloadRules.toUi(result(language = "pt-br", machine = true), listOf("pt-br.6224976.srt"), Locale.ENGLISH)
        assertEquals("6224976", row.id)
        assertEquals("Portuguese (Brazil)", row.language)
        assertEquals("Dune.2021.1080p.WEBRip.x264-RARBG", row.release)
        assertTrue(row.matchesFile)
        assertTrue(row.hearingImpaired)
        assertTrue(row.machineTranslated)
        assertEquals(18946, row.downloadCount)
        assertTrue(row.downloaded)
        assertFalse(SubtitleDownloadRules.toUi(result(), emptyList(), Locale.ENGLISH).downloaded)
    }

    @Test
    fun `file language is read after the video's own name`() {
        val english = SubtitleDownloadRules.aliases("en")
        assertTrue(SubtitleDownloadRules.fileIsInLanguage("Movie.eng.srt", "Movie.mkv", english))
        assertTrue(SubtitleDownloadRules.fileIsInLanguage("en.6224976.srt", "Movie.mkv", english))
        assertTrue(SubtitleDownloadRules.fileIsInLanguage("Movie.English.SDH.srt", "Movie.mkv", english))
        assertFalse(SubtitleDownloadRules.fileIsInLanguage("Movie.fr.srt", "Movie.mkv", english))
        // "English" in the video's own name says nothing about the subtitle.
        assertFalse(SubtitleDownloadRules.fileIsInLanguage("English Vinglish.hi.srt", "English Vinglish.mkv", english))
        assertTrue(SubtitleDownloadRules.fileIsInLanguage("pt-br.1.srt", "Movie.mkv", SubtitleDownloadRules.aliases("pt-br")))
    }

    @Test
    fun `only the languages before the first one present are missing`() {
        val present = setOf("en")
        assertEquals(listOf("hi"), SubtitleDownloadRules.missingLanguages(listOf("hi", "en", "fr")) { it in present })
        assertEquals(emptyList<String>(), SubtitleDownloadRules.missingLanguages(listOf("en", "fr")) { it in present })
        assertEquals(listOf("hi", "fr"), SubtitleDownloadRules.missingLanguages(listOf("hi", "fr")) { it in present })
    }

    @Test
    fun `automatic downloads need an id or the file's hash`() {
        assertTrue(SubtitleDownloadRules.canAutoPick(SubtitleSearchRequest(tmdbId = 438631, title = "Dune")))
        assertTrue(SubtitleDownloadRules.canAutoPick(SubtitleSearchRequest(imdbId = "tt1160419")))
        assertTrue(SubtitleDownloadRules.canAutoPick(SubtitleSearchRequest(movieHash = "8e245d9679d31e12", title = "birthday party")))
        // A home video on a share, a link called index.m3u8: only a name to go by.
        assertFalse(SubtitleDownloadRules.canAutoPick(SubtitleSearchRequest(title = "birthday party")))
        assertFalse(SubtitleDownloadRules.canAutoPick(SubtitleSearchRequest(imdbId = " ", title = "index")))
    }

    @Test
    fun `searched by id the best one is taken, by name only one timed for the file`() {
        val english = listOf("en")
        val popular = result(id = "1").copy(movieHashMatch = false)
        val timed = result(id = "2").copy(movieHashMatch = true, downloadCount = 10)
        val byId = SubtitleSearchRequest(languages = english, tmdbId = 438631, title = "Dune")
        assertEquals(SubtitleDownloadRules.AutoPick.Download(popular), SubtitleDownloadRules.autoPick(byId, listOf(popular), english))
        assertEquals(SubtitleDownloadRules.AutoPick.Download(timed), SubtitleDownloadRules.autoPick(byId, listOf(popular, timed), english))
        assertEquals(SubtitleDownloadRules.AutoPick.Settle, SubtitleDownloadRules.autoPick(byId, emptyList(), english))

        val byName = SubtitleSearchRequest(languages = english, movieHash = "8e245d9679d31e12", title = "birthday party")
        // Some film called "Birthday Party": not this video's.
        assertEquals(SubtitleDownloadRules.AutoPick.Later, SubtitleDownloadRules.autoPick(byName, listOf(popular), english))
        assertEquals(SubtitleDownloadRules.AutoPick.Later, SubtitleDownloadRules.autoPick(byName, emptyList(), english))
        assertEquals(SubtitleDownloadRules.AutoPick.Download(timed), SubtitleDownloadRules.autoPick(byName, listOf(popular, timed), english))
        // Timed for the file, but in a language nobody asked for.
        val french = timed.copy(id = "3", language = "fr")
        assertEquals(SubtitleDownloadRules.AutoPick.Later, SubtitleDownloadRules.autoPick(byName, listOf(popular, french), english))
    }

    @Test
    fun `error messages`() {
        val quota = OpenSubtitlesException(OpenSubtitlesException.Reason.QUOTA, "Download limit reached, resets at 13:03")
        assertEquals("Download limit reached, resets at 13:03", SubtitleDownloadRules.errorMessage(quota))
        assertEquals("Something went wrong with OpenSubtitles. Try again.", SubtitleDownloadRules.errorMessage(IOException("boom")))
    }
}
