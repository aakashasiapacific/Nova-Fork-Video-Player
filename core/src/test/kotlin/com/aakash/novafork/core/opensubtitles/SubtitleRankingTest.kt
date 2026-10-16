package com.aakash.novafork.core.opensubtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleRankingTest {
    private fun result(
        id: String,
        language: String = "en",
        downloads: Int = 0,
        hashMatch: Boolean = false,
        machine: Boolean = false,
    ) = SubtitleResult(
        id = id,
        fileId = id.hashCode().toLong(),
        fileName = "$id.srt",
        language = language,
        release = id,
        downloadCount = downloads,
        hearingImpaired = false,
        movieHashMatch = hashMatch,
        fps = null,
        aiTranslated = false,
        machineTranslated = machine,
    )

    @Test
    fun languageOrderThenHashMatchThenPeopleThenDownloads() {
        val results = listOf(
            result("fr-popular", language = "fr", downloads = 9_000),
            result("en-few", downloads = 10),
            result("en-machine", downloads = 50_000, machine = true),
            result("en-many", downloads = 5_000),
            result("en-hash", downloads = 1, hashMatch = true),
            result("de", language = "de", downloads = 99_999),
        )
        val sorted = SubtitleRanking.sort(results, listOf("en", "fr"))
        assertEquals(listOf("en-hash", "en-many", "en-few", "en-machine", "fr-popular", "de"), sorted.map { it.id })
    }

    @Test
    fun bestIsInTheFirstLanguageThatHasAny() {
        val results = listOf(
            result("fr", language = "fr", downloads = 100),
            result("de", language = "de", downloads = 100_000),
        )
        assertEquals("fr", SubtitleRanking.best(results, listOf("en", "fr"))?.id)
        assertNull(SubtitleRanking.best(results, listOf("en")))
    }

    @Test
    fun regionalResultsCountForTheirLanguage() {
        val results = listOf(result("br", language = "pt-br", downloads = 5), result("en", downloads = 50))
        assertEquals("br", SubtitleRanking.best(results, listOf("pt", "en"))?.id)
    }
}
