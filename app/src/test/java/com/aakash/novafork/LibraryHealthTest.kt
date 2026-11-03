package com.aakash.novafork

import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.ui.LibraryIndex
import com.aakash.novafork.ui.UiMappers
import com.aakash.novafork.ui.model.HealthKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.watchedCaptionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHealthTest {
    private val device = SourceEntity(id = 1, type = SourceType.DEVICE, name = "Device storage", location = "device")
    private val nas = SourceEntity(id = 2, type = SourceType.SMB, name = "NAS", location = "smb://nas.local/Media")

    private fun video(
        id: Long,
        title: String,
        kind: VideoKind = VideoKind.MOVIE,
        folder: String = "Movies",
        sourceId: Long = 1,
        fileName: String = "$title.mkv",
        size: Long? = 1_000L,
        season: Int? = null,
        episode: Int? = null,
        poster: String? = null,
    ) = VideoEntity(
        id = id,
        sourceId = sourceId,
        uri = "content://media/external/video/media/$id",
        fileName = fileName,
        folder = folder,
        sizeBytes = size,
        dateAdded = 1_000L * id,
        kind = kind,
        parsedTitle = title,
        season = season,
        episode = episode,
        localPoster = poster,
    )

    private fun meta(key: String, title: String, poster: String? = "/p.jpg") =
        MetadataEntity(key = key, tmdbId = key.substringAfter(':').toInt(), type = key.substringBefore(':'), title = title, posterPath = poster)

    private fun build(rows: List<VideoWithMeta>, overrides: Map<String, String> = emptyMap()) =
        LibraryIndex.build(rows, listOf(device, nas), preferLocalArtwork = false, titleOverrides = overrides)

    @Test
    fun healthListsOnlyGroupsWithProblems() {
        val rows = listOf(
            VideoWithMeta(video(1, "Fine", poster = null), meta("movie:1", "Fine")),
            VideoWithMeta(video(2, "Bare", poster = null), meta("movie:2", "Bare", poster = null)),
            VideoWithMeta(video(3, "Mystery", poster = "content://poster/3"), null),
            VideoWithMeta(video(4, "Some Show", kind = VideoKind.EPISODE, fileName = "Some Show special.mkv"), null),
            VideoWithMeta(video(5, "Some Show", kind = VideoKind.EPISODE, season = 1, episode = 2), null),
        )
        val health = UiMappers.libraryHealth(build(rows))
        val byKind = health.groups.associate { it.kind to it.items }
        assertEquals(listOf(2L), byKind.getValue(HealthKind.NO_ARTWORK).filter { it.key == "v:2" }.map { it.videoId })
        assertEquals(listOf(3L), byKind.getValue(HealthKind.UNIDENTIFIED).map { it.videoId })
        assertEquals(listOf(4L), byKind.getValue(HealthKind.UNPARSED_EPISODES).map { it.videoId })
        assertFalse(byKind.getValue(HealthKind.NO_ARTWORK).any { it.videoId == 1L })
    }

    @Test
    fun aCleanLibraryHasNoProblems() {
        val rows = listOf(VideoWithMeta(video(1, "Fine"), meta("movie:1", "Fine")))
        val health = UiMappers.libraryHealth(build(rows))
        assertTrue(health.groups.isEmpty())
        assertEquals(0, health.problemCount)
    }

    @Test
    fun duplicateBytesCountsTheExtraCopies() {
        val m = meta("movie:7", "Twice")
        val rows = listOf(
            VideoWithMeta(video(1, "Twice", size = 4_000), m),
            VideoWithMeta(video(2, "Twice", size = 9_000, fileName = "Twice 2160p.mkv"), m),
            VideoWithMeta(video(3, "Once", size = 5_000), meta("movie:8", "Once")),
        )
        assertEquals(4_000L, UiMappers.duplicateBytes(build(rows)))
        assertEquals(0L, UiMappers.duplicateBytes(build(rows.takeLast(1))))
    }

    @Test
    fun renamedTitlesApplyWhereverTheTitleShows() {
        val rows = listOf(
            VideoWithMeta(video(1, "Heat"), meta("movie:1", "Heat")),
            VideoWithMeta(video(2, "Home movie", kind = VideoKind.VIDEO), null),
            VideoWithMeta(video(3, "Show", kind = VideoKind.EPISODE, season = 1, episode = 1), meta("tv:9", "Show")),
        )
        val index = build(rows, mapOf("movie:1" to "Heat (Director's Cut)", "video:2" to "Goa 2024", "tv:9" to "My Show"))
        assertEquals("Heat (Director's Cut)", index.videosById.getValue(1).title)
        assertTrue(index.videosById.getValue(1).titleCustomised)
        assertEquals("movie:1", index.videosById.getValue(1).titleKey)
        assertEquals("Goa 2024", index.videosById.getValue(2).title)
        assertEquals("video:2", index.videosById.getValue(2).titleKey)
        assertEquals("My Show", index.shows.single().title)
        assertEquals("My Show", index.videosById.getValue(3).showTitle)
        // Without overrides the original title is back.
        val plain = build(rows)
        assertEquals("Heat", plain.videosById.getValue(1).title)
        assertFalse(plain.videosById.getValue(1).titleCustomised)
        assertEquals("movie:1", plain.videosById.getValue(1).titleKey)
    }

    @Test
    fun anOverrideOfAnUnidentifiedTitleSurvivesIdentifying() {
        val unmatched = build(listOf(VideoWithMeta(video(1, "Heat"), null)), mapOf("video:1" to "Mine"))
        assertEquals("Mine", unmatched.videosById.getValue(1).title)
        val matched = build(listOf(VideoWithMeta(video(1, "Heat"), meta("movie:1", "Heat"))), mapOf("video:1" to "Mine"))
        assertEquals("Mine", matched.videosById.getValue(1).title)
        assertEquals("video:1", matched.videosById.getValue(1).titleKey)
    }

    @Test
    fun downloadedCopiesAreMarkedOnBothSides() {
        val rows = listOf(
            VideoWithMeta(video(1, "Film", folder = "Movies/Nova Fork", fileName = "Film.mkv", size = 500), null),
            VideoWithMeta(video(2, "Film", sourceId = 2, folder = "Media", fileName = "Film.mkv", size = 500), null),
            VideoWithMeta(video(3, "Other", sourceId = 2, folder = "Media", fileName = "Other.mkv", size = 500), null),
            VideoWithMeta(video(4, "Film", folder = "Movies", fileName = "Film.mkv", size = 999), null),
        )
        val index = build(rows)
        assertEquals(setOf(1L, 2L), index.downloadedIds)
        assertTrue(build(rows.takeLast(1)).downloadedIds.isEmpty())
    }

    @Test
    fun watchedCaptionNeedsAnEpisodeWatched() {
        assertEquals("4 of 8 watched", watchedCaptionOf(4, 8))
        assertEquals("8 of 8 watched", watchedCaptionOf(8, 8))
        assertNull(watchedCaptionOf(0, 8))
        assertNull(watchedCaptionOf(0, 0))
        assertEquals("4 of 8 watched", ShowCardUi(key = "k", title = "T", episodeCount = 8, unwatchedCount = 4).watchedCaption)
        assertNull(ShowCardUi(key = "k", title = "T", episodeCount = 8, unwatchedCount = 8).watchedCaption)
    }
}
