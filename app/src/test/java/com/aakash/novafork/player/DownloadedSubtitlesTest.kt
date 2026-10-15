package com.aakash.novafork.player

import com.aakash.novafork.sources.DownloadedSubtitles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadedSubtitlesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a library video is keyed by its id, a link by a hash of its address`() {
        assertEquals("42", DownloadedSubtitles.videoKey(42, "content://media/external/video/media/9"))
        val link = DownloadedSubtitles.videoKey(null, "https://example.com/movie.mp4")
        assertTrue(link, link.matches(Regex("link-[0-9a-f]{24}")))
        assertEquals(link, DownloadedSubtitles.videoKey(null, "https://example.com/movie.mp4"))
        assertNotEquals(link, DownloadedSubtitles.videoKey(null, "https://example.com/other.mp4"))
    }

    @Test
    fun `the folder is under filesDir and its name stays a single safe segment`() {
        val files = folder.root
        assertEquals(File(files, "subtitles/42"), DownloadedSubtitles.directory(files, "42"))
        assertEquals(File(files, "subtitles/link-ab"), DownloadedSubtitles.directory(files, "link-ab"))
        assertEquals(File(files, "subtitles/etc"), DownloadedSubtitles.directory(files, "../../etc"))
        assertEquals(File(files, "subtitles/unknown"), DownloadedSubtitles.directory(files, "/"))
    }

    @Test
    fun `lists subtitle files oldest first and leaves out the rest`() {
        val dir = DownloadedSubtitles.directory(folder.root, "7").apply { mkdirs() }
        fun write(name: String, modified: Long, text: String = "1\n00:00:01,000 --> 00:00:02,000\nHi\n") =
            File(dir, name).apply {
                writeText(text)
                setLastModified(modified)
            }
        write("fr.2.srt", 2_000_000L)
        write("en.1.srt", 1_000_000L)
        write("en.3.ass", 3_000_000L)
        write(".auto-download", 500_000L, text = "")
        write(".en.4.srt.part", 4_000_000L)
        write("notes.txt", 5_000_000L)
        write("empty.srt", 6_000_000L, text = "")
        assertEquals(listOf("en.1.srt", "fr.2.srt", "en.3.ass"), DownloadedSubtitles.list(dir).map { it.name })
    }

    @Test
    fun `keeps the newest when there are too many`() {
        val dir = DownloadedSubtitles.directory(folder.root, "8").apply { mkdirs() }
        val total = DownloadedSubtitles.MAX_FILES + 3
        for (i in 1..total) {
            File(dir, "en.$i.srt").apply {
                writeText("x")
                setLastModified(i * 1_000_000L)
            }
        }
        val listed = DownloadedSubtitles.list(dir).map { it.name }
        assertEquals(DownloadedSubtitles.MAX_FILES, listed.size)
        assertEquals("en.4.srt", listed.first())
        assertEquals("en.$total.srt", listed.last())
    }

    @Test
    fun `a missing folder lists nothing`() {
        assertEquals(emptyList<File>(), DownloadedSubtitles.list(File(folder.root, "nope")))
    }
}
