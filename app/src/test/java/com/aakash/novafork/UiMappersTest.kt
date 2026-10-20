package com.aakash.novafork

import com.aakash.novafork.FakeLibrary.BEACH_CLIP
import com.aakash.novafork.FakeLibrary.BUNNY
import com.aakash.novafork.FakeLibrary.CAMERA_CLIP
import com.aakash.novafork.FakeLibrary.DEVICE
import com.aakash.novafork.FakeLibrary.DUNE
import com.aakash.novafork.FakeLibrary.EVERYTHING
import com.aakash.novafork.FakeLibrary.INTERSTELLAR
import com.aakash.novafork.FakeLibrary.MOVIES_FOLDER
import com.aakash.novafork.FakeLibrary.NAS
import com.aakash.novafork.FakeLibrary.OFFICE_KEY
import com.aakash.novafork.FakeLibrary.OFFICE_S2E4
import com.aakash.novafork.FakeLibrary.OPPENHEIMER
import com.aakash.novafork.FakeLibrary.PANCHAYAT_KEY
import com.aakash.novafork.FakeLibrary.PANCHAYAT_S3E4
import com.aakash.novafork.FakeLibrary.PARASITE
import com.aakash.novafork.FakeLibrary.RRR
import com.aakash.novafork.FakeLibrary.SEVERANCE_KEY
import com.aakash.novafork.FakeLibrary.SEVERANCE_S1E1
import com.aakash.novafork.FakeLibrary.SEVERANCE_S2E3
import com.aakash.novafork.FakeLibrary.SEVERANCE_S2E4
import com.aakash.novafork.FakeLibrary.SHOGUN_KEY
import com.aakash.novafork.FakeLibrary.SHOGUN_S1E1
import com.aakash.novafork.FakeLibrary.SPIRITED_AWAY
import com.aakash.novafork.FakeLibrary.TEARS
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.ScanState
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.ui.LibraryIndex
import com.aakash.novafork.ui.ListingKey
import com.aakash.novafork.ui.NaturalOrder
import com.aakash.novafork.ui.RemoteListing
import com.aakash.novafork.ui.UiMappers
import com.aakash.novafork.ui.common.deviceAccessNotice
import com.aakash.novafork.ui.frameSourceOf
import com.aakash.novafork.ui.model.withRecentSearch
import com.aakash.novafork.ui.model.Crumb
import com.aakash.novafork.ui.model.FolderUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.pick
import com.aakash.novafork.ui.progressOf
import com.aakash.novafork.ui.treeFolderLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMappersTest {
    private val index = FakeLibrary.index()
    private val idle = ScanState()

    private fun card(id: Long) = requireNotNull(index.videosById[id]) { "No video $id" }.card

    // Progress and watched state

    @Test
    fun progressIsPositionOverDurationUntilWatched() {
        val dune = card(DUNE)
        assertEquals((65 * 60_000f + 12_000f) / (167 * 60_000f - 47_000f), requireNotNull(dune.progress), 1e-4f)
        assertFalse(dune.watched)

        val interstellar = card(INTERSTELLAR)
        assertTrue(interstellar.watched)
        assertNull("watched videos show a check, not a bar", interstellar.progress)

        assertNull("never played", card(OPPENHEIMER).progress)
    }

    @Test
    fun progressNeedsADurationAndStaysInRange() {
        val video = video(id = 1, sourceId = DEVICE, uri = "content://media/external/video/media/1", positionMs = 90_000)
        assertNull(progressOf(video.copy(durationMs = null)))
        assertNull(progressOf(video.copy(durationMs = 0)))
        assertNull(progressOf(video.copy(durationMs = 120_000, watched = true)))
        assertEquals(0.75f, requireNotNull(progressOf(video.copy(durationMs = 120_000))), 1e-6f)
        assertEquals(1f, requireNotNull(progressOf(video.copy(durationMs = 60_000))), 1e-6f)
    }

    // Home

    @Test
    fun continueWatchingIsLastPlayedFirst() {
        val ids = index.home.continueWatching.map { it.id }
        assertEquals(listOf(DUNE, SEVERANCE_S2E3, PANCHAYAT_S3E4, EVERYTHING), ids)
    }

    @Test
    fun continueWatchingKeepsOneEpisodePerShow() {
        val older = episode(id = 10, season = 1, episode = 1, positionMs = 60_000, lastPlayedAt = 1_000)
        val newer = episode(id = 11, season = 1, episode = 2, positionMs = 60_000, lastPlayedAt = 2_000)
        val home = UiMappers.home(LibraryIndex.build(listOf(row(older), row(newer)), listOf(nas), preferLocalArtwork = false))
        assertEquals(listOf(11L), home.continueWatching.map { it.id })
    }

    @Test
    fun homeRows() {
        val home = index.home
        assertFalse(home.isEmpty)
        assertEquals(
            listOf(DUNE, EVERYTHING, INTERSTELLAR, OPPENHEIMER, PARASITE, RRR, SPIRITED_AWAY),
            home.movies.map { it.id },
        )
        assertEquals(listOf("The Office", "Panchayat", "Severance", "Shōgun"), home.shows.map { it.title })
        assertEquals("newest first", listOf(TEARS, BUNNY, BEACH_CLIP, CAMERA_CLIP), home.videos.map { it.id })
    }

    @Test
    fun recentlyAddedCollapsesAShowIntoItsNewestEpisode() {
        val recent = index.home.recentlyAdded
        assertEquals(listOf(SHOGUN_S1E1 + 3, DUNE, SEVERANCE_S2E4), recent.take(3).map { it.id })
        val perShow = recent.filter { it.kind == MediaKind.EPISODE }.groupingBy { it.showTitle }.eachCount()
        assertEquals(mapOf("Shōgun" to 1, "Severance" to 1, "Panchayat" to 1, "The Office" to 1), perShow)
        // The card wears the show's poster rather than a season or episode image.
        assertEquals("https://image.tmdb.org/t/p/w500/shogun.jpg", recent.first().artwork.posterUrl)
    }

    @Test
    fun heroStartsWithWhatIsInProgressThenTheNewest() {
        val hero = index.home.hero
        assertEquals(
            listOf(
                HeroTarget.Video(DUNE),
                HeroTarget.Show(SEVERANCE_KEY),
                HeroTarget.Show(PANCHAYAT_KEY),
                HeroTarget.Video(EVERYTHING),
                HeroTarget.Show(SHOGUN_KEY),
                HeroTarget.Video(OPPENHEIMER),
            ),
            hero.map { it.target },
        )
        val dune = hero[0]
        assertEquals("Resume", dune.playLabel)
        assertEquals("2024 · 2h 47m · Science Fiction, Adventure", dune.metaLine)
        val severance = hero[1]
        assertEquals("Resume S2 · E3", severance.playLabel)
        assertEquals(SEVERANCE_S2E3, severance.playVideoId)
        assertEquals("S2 · E3 · Who Is Alive?", severance.metaLine)
        assertEquals("Play S1 · E1", hero[4].playLabel)
    }

    @Test
    fun searchFindsTitlesByCastAndDirector() {
        val meta = MetadataEntity(
            key = "movie:603",
            tmdbId = 603,
            type = "movie",
            title = "The Matrix",
            castNames = "Keanu Reeves, Carrie-Anne Moss",
            directors = "Lana Wachowski, Lilly Wachowski",
        )
        val rows = listOf(
            VideoWithMeta(video(id = 1, sourceId = DEVICE, uri = "content://media/external/video/media/1", kind = VideoKind.MOVIE, metadataKey = meta.key), meta),
        )
        val small = LibraryIndex.build(rows, listOf(device), preferLocalArtwork = false)
        assertEquals(1, UiMappers.search(small, "keanu").results.size)
        assertEquals(1, UiMappers.search(small, "wachowski").results.size)
        // Two letters are too few to search names by.
        assertTrue(UiMappers.search(small, "ke").results.isEmpty())
    }

    @Test
    fun allVideosIsAGaplessTimelineNewestFirst() {
        val utc = java.time.ZoneOffset.UTC
        val library = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.RECENT, now = FakeLibrary.NOW, zone = utc)
        // Every video, episodes one by one, newest first.
        assertEquals(index.videos.size, library.timeline.size)
        val added = library.timeline.map { card -> index.videos.first { it.id == card.id }.video.dateAdded }
        assertEquals(added.sortedDescending(), added)
        // Each card says when it arrived: Dune came a day before "now".
        val dune = requireNotNull(library.timeline.first { it.id == DUNE }.subtitle)
        assertTrue(dune, dune.startsWith("Yesterday · 2h "))
        // Other sorts and filters are the plain grid.
        assertTrue(UiMappers.library(index, LibraryFilter.ALL, LibrarySort.TITLE).timeline.isEmpty())
        assertTrue(UiMappers.library(index, LibraryFilter.MOVIES, LibrarySort.RECENT).timeline.isEmpty())
    }

    @Test
    fun arrivalLabelsGoFromTodayToDates() {
        val today = java.time.LocalDate.of(2026, 10, 19)
        assertEquals("Today", UiMappers.arrivedLabel(today, today))
        assertEquals("Yesterday", UiMappers.arrivedLabel(today.minusDays(1), today))
        val weekday = today.minusDays(5).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        assertEquals(weekday, UiMappers.arrivedLabel(today.minusDays(5), today))
        val march = java.time.Month.MARCH.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        assertEquals("2 $march", UiMappers.arrivedLabel(java.time.LocalDate.of(2026, 3, 2), today))
        assertEquals("2 $march 2024", UiMappers.arrivedLabel(java.time.LocalDate.of(2024, 3, 2), today))
    }

    @Test
    fun meaninglessFileNamesGetReadableTitles() {
        val utc = java.time.ZoneOffset.UTC
        val day = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault())
        fun on(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).format(day)
        assertEquals("Video · ${on(2024, 5, 12)}", com.aakash.novafork.ui.friendlyVideoTitle("VID_20240512_183012", 0L, utc))
        assertEquals("Video · ${on(2024, 5, 12)}", com.aakash.novafork.ui.friendlyVideoTitle("VID-20240512-WA0003", 0L, utc))
        assertEquals("Video · ${on(2024, 5, 12)}", com.aakash.novafork.ui.friendlyVideoTitle("PXL_20240512_101500123", 0L, utc))
        assertEquals("Screen recording · ${on(2026, 10, 16)}", com.aakash.novafork.ui.friendlyVideoTitle("Screen_Recording_20261016_145003", 0L, utc))
        // A number for a name: the day the file arrived.
        val added = java.time.LocalDate.of(2026, 2, 25).atStartOfDay(utc).toInstant().toEpochMilli()
        assertEquals("Video · ${on(2026, 2, 25)}", com.aakash.novafork.ui.friendlyVideoTitle("17936004744169077", added, utc))
        // Real names stay.
        assertEquals("Goa trip - Palolem sunset", com.aakash.novafork.ui.friendlyVideoTitle("Goa trip - Palolem sunset", added, utc))
        assertEquals("Science Fiction and Identity", com.aakash.novafork.ui.friendlyVideoTitle("Science Fiction and Identity", added, utc))
    }

    @Test
    fun cardsCarryTheTitleLogoForTheHero() {
        val meta = MetadataEntity(key = "movie:603", tmdbId = 603, type = "movie", title = "The Matrix", logoPath = "/matrix-logo.png")
        val rows = listOf(
            VideoWithMeta(video(id = 1, sourceId = DEVICE, uri = "content://media/external/video/media/1", kind = VideoKind.MOVIE, metadataKey = meta.key), meta),
        )
        val small = LibraryIndex.build(rows, listOf(device), preferLocalArtwork = false)
        assertEquals("https://image.tmdb.org/t/p/w500/matrix-logo.png", requireNotNull(small.videosById[1L]).card.artwork.logoUrl)
        // Without a logo the title is drawn as text.
        assertNull(card(DUNE).artwork.logoUrl)
    }

    @Test
    fun personalVideosFillAHeroThatHasRoom() {
        val rows = listOf(
            row(video(id = 1, sourceId = DEVICE, uri = "content://media/external/video/media/1", fileName = "Birthday.mp4")),
            row(video(id = 2, sourceId = DEVICE, uri = "content://media/external/video/media/2", fileName = "Trip.mp4")),
            // A network file has no frame to show: it stays out.
            row(video(id = 3, sourceId = NAS, uri = "smb://nas.local/Media/Clip.mkv")),
        )
        val small = LibraryIndex.build(rows, listOf(device, nas), preferLocalArtwork = false)
        val hero = UiMappers.home(small).hero
        assertEquals(setOf(HeroTarget.Video(1), HeroTarget.Video(2)), hero.map { it.target }.toSet())
        assertTrue(hero.all { it.artwork.frameSource != null })
        // Camera-roll style videos have only a frame: one picture, not a backdrop plus a poster.
        assertTrue(hero.all { it.artwork.frameOnly })
        // A matched movie has real artwork.
        assertFalse(index.home.hero.first { it.target == HeroTarget.Video(DUNE) }.artwork.frameOnly)
        // These run two minutes: longer than a hero clip, so they show a still frame and don't play.
        assertTrue(hero.all { it.previewUri == null })
    }

    @Test
    fun heroesNeverPlayClips() {
        // The owner dropped playing short clips in the hero: even a 45-second clip shows still art.
        val clipRows = FakeLibrary.rows.map { row ->
            if (row.video.id == DUNE) row.copy(video = row.video.copy(durationMs = 45_000)) else row
        }
        val clipIndex = LibraryIndex.build(clipRows, FakeLibrary.sources, preferLocalArtwork = false)
        assertTrue(UiMappers.home(clipIndex).hero.all { it.previewUri == null })
        assertEquals("v:$DUNE", index.home.hero.first { it.target == HeroTarget.Video(DUNE) }.itemKey)
        assertEquals("s:$SEVERANCE_KEY", index.home.hero.first { it.target == HeroTarget.Show(SEVERANCE_KEY) }.itemKey)
    }

    @Test
    fun emptyLibraryAsksForOnboarding() {
        val home = UiMappers.home(LibraryIndex.build(emptyList(), emptyList(), preferLocalArtwork = false))
        assertTrue(home.isEmpty)
        assertTrue(home.hero.isEmpty())
    }

    // Shows

    @Test
    fun episodesGroupIntoShowsAndSeasons() {
        val show = requireNotNull(index.showsByKey[SEVERANCE_KEY]).card
        assertEquals("Severance", show.title)
        assertEquals(2, show.seasonCount)
        assertEquals(8, show.episodeCount)
        assertEquals(2, show.unwatchedCount)

        val details = requireNotNull(UiMappers.showDetails(index, SEVERANCE_KEY))
        assertEquals(listOf("Season 1", "Season 2"), details.seasons.map { it.title })
        assertEquals(listOf("E1", "E2", "E3", "E4"), details.seasons[1].episodes.map { it.code })
        assertEquals("https://image.tmdb.org/t/p/w500/severance-s2.jpg", details.seasons[1].posterUrl)
        assertEquals(SEVERANCE_S2E3, details.nextUp?.videoId)
        assertEquals("Resume S2 · E3", details.playLabel)
        assertTrue(details.isMatched)
    }

    @Test
    fun nextUpIsTheFirstUnwatchedEpisodeAfterTheLastWatchedOne() {
        val office = requireNotNull(UiMappers.showDetails(index, OFFICE_KEY))
        assertEquals(OFFICE_S2E4, office.nextUp?.videoId)
        assertEquals("Play S2 · E4", office.playLabel)

        val shogun = requireNotNull(UiMappers.showDetails(index, SHOGUN_KEY))
        assertEquals(SHOGUN_S1E1, shogun.nextUp?.videoId)
        assertEquals("Play S1 · E1", shogun.playLabel)
    }

    @Test
    fun twoCopiesOfAnEpisodeCountOnceAndSpecialsComeLast() {
        val rows = listOf(
            row(episode(id = 1, season = 1, episode = 1, fileName = "Show.S01E01.720p.mkv")),
            row(episode(id = 2, season = 1, episode = 1, fileName = "Show.S01E01.1080p.mkv")),
            row(episode(id = 3, season = 1, episode = 2)),
            row(episode(id = 4, season = 0, episode = 1)),
            row(episode(id = 5, season = null, episode = 3)),
        )
        val index = LibraryIndex.build(rows, listOf(nas), preferLocalArtwork = false)
        val show = index.shows.single()
        assertEquals(4, show.card.episodeCount)
        assertEquals(2, show.card.seasonCount)

        val details = requireNotNull(UiMappers.showDetails(index, show.key))
        assertEquals(listOf("Season 1", "Specials"), details.seasons.map { it.title })
        // Absolute-numbered episodes have no season yet and sit in season 1.
        assertEquals(listOf(1L, 2L, 3L, 5L), details.seasons[0].episodes.map { it.videoId })
    }

    // Artwork

    @Test
    fun tmdbArtworkWinsUnlessLocalArtworkIsPreferred() {
        val meta = MetadataEntity(
            key = "movie:1",
            tmdbId = 1,
            type = "movie",
            title = "Heat",
            year = 1995,
            posterPath = "/heat.jpg",
            backdropPath = "/heat-backdrop.jpg",
        )
        val movie = video(
            id = 1,
            sourceId = DEVICE,
            uri = "content://media/external/video/media/1",
            kind = VideoKind.MOVIE,
            metadataKey = meta.key,
            localPoster = "content://media/external/images/media/7",
            localBackdrop = "content://media/external/images/media/8",
            localThumb = "content://media/external/images/media/9",
        )
        val rows = listOf(VideoWithMeta(movie, meta))

        val tmdbFirst = LibraryIndex.build(rows, listOf(device), preferLocalArtwork = false).videos.single().card.artwork
        assertEquals("https://image.tmdb.org/t/p/w500/heat.jpg", tmdbFirst.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/heat-backdrop.jpg", tmdbFirst.backdropUrl)
        assertNull("a movie's thumb is only a fallback for a missing backdrop", tmdbFirst.stillUrl)

        val localFirst = LibraryIndex.build(rows, listOf(device), preferLocalArtwork = true).videos.single().card.artwork
        assertEquals("content://media/external/images/media/7", localFirst.posterUrl)
        assertEquals("content://media/external/images/media/8", localFirst.backdropUrl)

        val withoutFanart = VideoWithMeta(movie.copy(localBackdrop = null), null)
        val unmatched = LibraryIndex.build(listOf(withoutFanart), listOf(device), preferLocalArtwork = false)
            .videos.single().card.artwork
        assertEquals("content://media/external/images/media/7", unmatched.posterUrl)
        assertNull(unmatched.backdropUrl)
        assertEquals("content://media/external/images/media/9", unmatched.stillUrl)

        assertEquals("tmdb", pick("tmdb", "local", preferLocal = false))
        assertEquals("local", pick("tmdb", "local", preferLocal = true))
        assertEquals("tmdb", pick("tmdb", null, preferLocal = true))
        assertEquals("local", pick(null, "local", preferLocal = false))
    }

    @Test
    fun fakeLibraryArtworkFollowsThePriority() {
        assertEquals("https://image.tmdb.org/t/p/w500/spirited-away.jpg", card(SPIRITED_AWAY).artwork.posterUrl)
        val localFirst = FakeLibrary.index(preferLocalArtwork = true)
        assertEquals(
            "${FakeLibrary.MOVIES_TREE}/document/primary%3AMovies%2FAnime%2Fposter.jpg",
            requireNotNull(localFirst.videosById[SPIRITED_AWAY]).card.artwork.posterUrl,
        )

        val episode = card(SEVERANCE_S1E1).artwork
        assertEquals("https://image.tmdb.org/t/p/w780/severance-s1e1.jpg", episode.stillUrl)
        assertEquals("season poster first", "https://image.tmdb.org/t/p/w500/severance-s1.jpg", episode.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/severance-backdrop.jpg", episode.backdropUrl)
    }

    @Test
    fun frameSourceOnlyForLocalFiles() {
        assertEquals("content://media/external/video/media/5", frameSourceOf("content://media/external/video/media/5"))
        assertEquals("file:///sdcard/Movies/a.mkv", frameSourceOf("file:///sdcard/Movies/a.mkv"))
        assertNull(frameSourceOf("smb://nas.local/Media/a.mkv"))
        assertNull(frameSourceOf("https://example.com/live.m3u8"))
        assertNull(frameSourceOf("rtsp://camera.local/stream"))

        assertNotNull(card(BEACH_CLIP).artwork.frameSource)
        assertNotNull(card(DUNE).artwork.frameSource)
        assertNull("files on the NAS are never downloaded for a frame", card(SEVERANCE_S2E3).artwork.frameSource)
        assertNull(card(BUNNY).artwork.frameSource)
    }

    // Library

    @Test
    fun libraryFiltersAndCounts() {
        val all = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.RECENT)
        assertEquals(
            mapOf(LibraryFilter.ALL to 15, LibraryFilter.MOVIES to 7, LibraryFilter.SHOWS to 4, LibraryFilter.VIDEOS to 4),
            all.counts,
        )
        assertEquals(15, all.items.size)
        assertEquals("Shōgun", all.items.first().title)

        val shows = UiMappers.library(index, LibraryFilter.SHOWS, LibrarySort.TITLE)
        assertTrue(shows.items.all { it is LibraryItemUi.Show })
        assertEquals(listOf("The Office", "Panchayat", "Severance", "Shōgun"), shows.items.map { it.title })
    }

    @Test
    fun librarySorts() {
        val byTitle = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.TITLE).items.map { it.title }
        assertEquals(listOf("Big Buck Bunny", "Dune: Part Two", "Everything Everywhere All at Once"), byTitle.take(3))
        assertEquals("articles are ignored", listOf("The Office", "Oppenheimer"), byTitle.subList(5, 7))

        val byRating = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.RATING).items.map { it.title }
        assertEquals(listOf("Panchayat", "The Office", "Shōgun"), byRating.take(3))
        assertEquals(
            "unrated last",
            listOf("Big Buck Bunny", "Goa trip - Palolem sunset", "Tears of Steel", com.aakash.novafork.ui.friendlyVideoTitle("VID_20250914_183012", 0L)),
            byRating.takeLast(4),
        )

        val byYear = UiMappers.library(index, LibraryFilter.MOVIES, LibrarySort.YEAR).items.map { it.title }
        assertEquals(
            listOf("Dune: Part Two", "Oppenheimer", "Everything Everywhere All at Once", "RRR", "Parasite", "Interstellar", "Spirited Away"),
            byYear,
        )
    }

    // Details

    @Test
    fun movieDetails() {
        val dune = requireNotNull(UiMappers.videoDetails(index, DUNE))
        assertEquals(MediaKind.MOVIE, dune.kind)
        assertEquals("Dune: Part Two", dune.title)
        assertNull("same as the title", dune.originalTitle)
        assertEquals("Long live the fighters.", dune.tagline)
        assertEquals("2h 47m", dune.runtimeLabel)
        assertEquals(listOf("Science Fiction", "Adventure"), dune.genres)
        assertEquals("Resume from 1:05:12", dune.playLabel)
        assertEquals("Movies › Sci-Fi", dune.location)
        assertEquals("18.4 GB", dune.sizeLabel)
        assertEquals("3840×1606 · 4K", dune.resolutionLabel)
        assertEquals("Movies", dune.sourceLabel)
        assertTrue(dune.isMatched)
    }

    @Test
    fun episodeDetails() {
        val episode = requireNotNull(UiMappers.videoDetails(index, SEVERANCE_S2E3))
        assertEquals(MediaKind.EPISODE, episode.kind)
        assertEquals("Who Is Alive?", episode.title)
        assertEquals("Severance", episode.showTitle)
        assertEquals(SEVERANCE_KEY, episode.showKey)
        assertEquals("S02E03", episode.episodeCode)
        assertEquals("2025-01-31", episode.airDate)
        assertEquals(2025, episode.year)
        assertEquals("52m", episode.runtimeLabel)
        assertEquals("Resume from 31:40", episode.playLabel)
        assertEquals(SEVERANCE_S2E4, episode.nextEpisode?.id)
        assertEquals("NAS · Media › TV/Severance/Season 2", episode.location)
        assertNull("show taglines are not episode taglines", episode.tagline)
    }

    @Test
    fun homeVideoAndStreamDetails() {
        val clip = requireNotNull(UiMappers.videoDetails(index, BEACH_CLIP))
        assertEquals(MediaKind.VIDEO, clip.kind)
        assertEquals("Goa trip - Palolem sunset", clip.title)
        assertFalse(clip.isMatched)
        assertNull(clip.overview)
        assertEquals("Device storage › DCIM/Trips", clip.location)
        assertEquals("Play", clip.playLabel)

        val stream = requireNotNull(UiMappers.videoDetails(index, BUNNY))
        assertEquals(MediaKind.STREAM, stream.kind)
        assertEquals("Big Buck Bunny", stream.title)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", stream.location)
        assertNull(UiMappers.videoDetails(index, 9_999))
    }

    // Browse

    @Test
    fun sourcesAreDeviceThenFoldersThenNetworkSourcesWithoutStreams() {
        val browse = UiMappers.browse(index, idle)
        assertEquals(
            listOf(SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.SFTP, SourceType.JELLYFIN),
            browse.sources.map { it.type },
        )
        assertEquals(
            listOf(
                "Internal storage and SD cards",
                "Movies",
                "nas.local/Media",
                "SFTP · seedbox.example.net/downloads",
                "Jellyfin · 192.168.1.5:8096",
            ),
            browse.sources.map { it.detail },
        )
        assertEquals(listOf(7, 7, 16, 0, 0), browse.sources.map { it.videoCount })
        assertEquals(listOf(TEARS, BUNNY), browse.streams.map { it.id })

        val scanning = UiMappers.browse(index, ScanState(running = true, sourceName = "NAS · Media"))
        assertEquals(listOf(false, false, true, false, false), scanning.sources.map { it.isScanning })
    }

    @Test
    fun networkSourceDetailsNameTheTypeAndWhere() {
        fun detail(type: SourceType, location: String) =
            UiMappers.sourceDetail(SourceEntity(type = type, name = "x", location = location))
        assertEquals("FTPS · nas.local:2121/My Movies", detail(SourceType.FTP, "ftps://nas.local:2121/My%20Movies"))
        assertEquals("NFS · nas/export/video", detail(SourceType.NFS, "nfs://nas/export/video"))
        assertEquals("WebDAV · dav.example.com/remote.php/dav", detail(SourceType.WEBDAV, "https://dav.example.com/remote.php/dav"))
        assertEquals("DLNA · 192.168.1.9", detail(SourceType.UPNP, "http://192.168.1.9:8200/rootDesc.xml"))
        assertEquals("DLNA server", detail(SourceType.UPNP, "uuid:4d696e69-444c-164e-9d41-b827eb54e939"))
        assertEquals("Plex · [fd00::5]:32400", detail(SourceType.PLEX, "http://[fd00::5]:32400"))
    }

    // Live listings of network sources

    @Test
    fun remoteDirListsFoldersThenFilesMatchingLibraryVideosByUri() {
        val source = SourceEntity(id = 40, type = SourceType.FTP, name = "Basement", location = "ftp://nas/Videos")
        val dune = requireNotNull(index.videosById[DUNE])
        val listings = mapOf(
            ListingKey(40, "Films") to RemoteListing(
                entries = listOf(
                    RemoteEntry(name = "Sci-Fi", path = "Films/Sci-Fi", isDirectory = true),
                    RemoteEntry(name = "Sci-Fi", path = "Films/Sci-Fi", isDirectory = true),
                    RemoteEntry(name = "dune.mkv", path = "Films/dune.mkv", isDirectory = false, playUri = dune.video.uri),
                    RemoteEntry(name = "cover.jpg", path = "Films/cover.jpg", isDirectory = false),
                    RemoteEntry(
                        name = "Heat (1995).mkv",
                        path = "Films/Heat (1995).mkv",
                        isDirectory = false,
                        sizeBytes = 3L shl 30,
                        playUri = "ftp://nas/Videos/Films/Heat%20%281995%29.mkv",
                    ),
                ),
            ),
        )
        val dir = UiMappers.remoteDir(index, source, "Films", listings)
        assertEquals("Films", dir.title)
        assertEquals(SourceType.FTP, dir.sourceType)
        assertEquals(listOf(Crumb("Basement", ""), Crumb("Films", "Films")), dir.crumbs)
        // Listed twice, shown once; nothing scanned below it yet.
        assertEquals(listOf(FolderUi(40, "Films/Sci-Fi", "Sci-Fi", note = "Folder")), dir.folders)
        assertEquals(listOf(dune.card), dir.videos)
        assertEquals(listOf("Heat (1995).mkv"), dir.files.map { it.name })
        assertEquals("ftp://nas/Videos/Films/Heat%20%281995%29.mkv", dir.files.single().uri)
        assertEquals("3.0 GB", dir.files.single().subtitle)
        assertFalse(dir.isLoading)
        assertFalse(dir.isEmpty)

        val notListedYet = UiMappers.remoteDir(index, source, "Films/Sci-Fi", listings)
        assertTrue(notListedYet.isLoading)
        assertTrue(notListedYet.isEmpty)
        assertEquals(listOf("Basement", "Films", "Sci-Fi"), notListedYet.crumbs.map { it.label })
    }

    @Test
    fun remoteDirOfAMediaServerFollowsItsListingsForTheTrail() {
        val source = requireNotNull(index.sourcesById[FakeLibrary.DEN_JELLYFIN])
        val listings = FakeLibrary.jellyfinListings + mapOf(
            ListingKey(source.id, "col-1") to RemoteListing(loading = true),
            ListingKey(source.id, "lost") to RemoteListing(error = "The server didn't answer"),
        )
        val root = UiMappers.remoteDir(index, source, "", listings)
        assertEquals("Den Jellyfin", root.title)
        assertEquals(listOf("Library", "Library"), root.folders.map { it.note })

        val movies = UiMappers.remoteDir(index, source, "lib-movies", listings)
        assertEquals(listOf(Crumb("Den Jellyfin", ""), Crumb("Movies", "lib-movies")), movies.crumbs)
        assertEquals(listOf("Collections"), movies.folders.map { it.name })
        assertEquals(5, movies.files.size)
        assertEquals("http://192.168.1.5:8096/Items/a1/Images/Primary?maxWidth=342&tag=poster", movies.files.first().artwork.posterUrl)
        assertTrue(movies.videos.isEmpty())

        val collections = UiMappers.remoteDir(index, source, "col-1", listings)
        assertEquals(listOf("Den Jellyfin", "Movies", "Collections"), collections.crumbs.map { it.label })
        assertTrue(collections.isLoading)

        val lost = UiMappers.remoteDir(index, source, "lost", listings)
        assertEquals(listOf(Crumb("Den Jellyfin", ""), Crumb("Folder", "lost")), lost.crumbs)
        assertEquals("The server didn't answer", lost.error)
        assertFalse(lost.isLoading)
    }

    // Playlists

    @Test
    fun playlistsKeepTheirOrderAndTakeTheCoverVideosArt() {
        val browse = UiMappers.browse(index, idle, FakeLibrary.playlists)
        assertEquals(listOf("Severance rewatch", "Movie night", "Clips to sort"), browse.playlists.map { it.name })
        assertEquals(listOf(8, 4, 0), browse.playlists.map { it.itemCount })
        assertEquals(card(SEVERANCE_S1E1).artwork, browse.playlists[0].artwork)
        assertEquals(card(DUNE).artwork, browse.playlists[1].artwork)
        assertNull(browse.playlists[2].artwork)
        assertTrue(UiMappers.browse(index, idle).playlists.isEmpty())
    }

    @Test
    fun playlistDetailsListShownVideosInPlaylistOrderOnce() {
        val details = UiMappers.playlistDetails(index, id = 9L, name = "Mix", videoIds = listOf(RRR, 999_999L, DUNE, RRR))
        assertEquals(9L, details.id)
        assertEquals("Mix", details.name)
        // An id the library doesn't show (missing file) is left out, and a repeat is listed once.
        assertEquals(listOf(RRR, DUNE), details.videos.map { it.id })
        assertEquals(card(DUNE), details.videos[1])
    }

    @Test
    fun pickedFolderLabels() {
        val tree = "content://com.android.externalstorage.documents/tree/"
        assertEquals("Movies", treeFolderLabel(FakeLibrary.MOVIES_TREE))
        assertEquals("Movies/Action", treeFolderLabel("${tree}primary%3AMovies%2FAction"))
        assertEquals("Internal storage", treeFolderLabel("${tree}primary%3A"))
        assertEquals("SD card", treeFolderLabel("${tree}1A2B-3C4D%3A"))
        assertNull(treeFolderLabel("content://media/external/video/media/1"))
    }

    @Test
    fun browseDirListsSubfoldersWithRecursiveCountsAndTheVideosHere() {
        val root = requireNotNull(UiMappers.browseDir(index, MOVIES_FOLDER, "", idle))
        assertEquals("Movies", root.title)
        assertEquals(SourceType.FOLDER, root.sourceType)
        assertEquals(listOf(Crumb("Movies", "")), root.crumbs)
        assertEquals(listOf("Anime" to 1, "Sci-Fi" to 2), root.folders.map { it.name to it.videoCount })
        assertEquals(listOf("Anime", "Sci-Fi"), root.folders.map { it.path })
        assertEquals(listOf(EVERYTHING, OPPENHEIMER, PARASITE, RRR), root.videos.map { it.id })

        val sciFi = requireNotNull(UiMappers.browseDir(index, MOVIES_FOLDER, "Sci-Fi", idle))
        assertEquals("Sci-Fi", sciFi.title)
        assertEquals(listOf(Crumb("Movies", ""), Crumb("Sci-Fi", "Sci-Fi")), sciFi.crumbs)
        assertTrue(sciFi.folders.isEmpty())
        assertEquals(listOf(DUNE, INTERSTELLAR), sciFi.videos.map { it.id })
    }

    @Test
    fun browseDirOnTheNasAndTheDevice() {
        val nasRoot = requireNotNull(UiMappers.browseDir(index, NAS, "", idle))
        assertEquals(listOf("TV" to 16), nasRoot.folders.map { it.name to it.videoCount })
        assertTrue(nasRoot.videos.isEmpty())

        val tv = requireNotNull(UiMappers.browseDir(index, NAS, "/TV/", idle))
        assertEquals("TV", tv.path)
        assertEquals(listOf("Severance" to 8, "Shogun" to 4, "The Office" to 4), tv.folders.map { it.name to it.videoCount })
        assertEquals("TV/Severance", tv.folders.first().path)

        val device = requireNotNull(UiMappers.browseDir(index, DEVICE, "", idle))
        assertEquals(listOf("DCIM" to 2, "Download" to 5), device.folders.map { it.name to it.videoCount })

        assertNull(UiMappers.browseDir(index, 99, "", idle))
    }

    // Search

    @Test
    fun searchRanksTitlesThatStartWithTheQueryFirst() {
        val results = UiMappers.search(index, "pa").results
        assertEquals(
            listOf("Panchayat", "Parasite", "Dune: Part Two", "Goa trip - Palolem sunset", "Spirited Away"),
            results.map { it.title },
        )
        assertTrue(results.first() is LibraryItemUi.Show)
    }

    @Test
    fun searchFindsEpisodesAsTheirShow() {
        val byEpisodeTitle = UiMappers.search(index, "who is alive").results
        assertEquals(listOf("s:$SEVERANCE_KEY"), byEpisodeTitle.map { it.key })

        val byShowTitle = UiMappers.search(index, "OFFICE").results
        assertEquals(listOf("s:$OFFICE_KEY"), byShowTitle.map { it.key })

        val byFileName = UiMappers.search(index, "x36xhzz").results
        assertEquals(listOf("v:$BUNNY"), byFileName.map { it.key })

        val blank = UiMappers.search(index, "  ")
        assertEquals("  ", blank.query)
        assertTrue(blank.results.isEmpty())
    }

    // Scan

    @Test
    fun scanLabels() {
        assertEquals(ScanUi(), UiMappers.scan(ScanState()))
        assertEquals(
            ScanUi(running = true, label = "Scanning NAS · Media · 214 found"),
            UiMappers.scan(ScanState(running = true, sourceName = "NAS · Media", found = 214)),
        )
        assertEquals(ScanUi(running = true, label = "Scanning library"), UiMappers.scan(ScanState(running = true)))
        val enriching = UiMappers.scan(ScanState(enriching = true, enrichDone = 12, enrichTotal = 80))
        assertEquals("Fetching posters 12 / 80", enriching.label)
        assertEquals(0.15f, requireNotNull(enriching.progress), 1e-6f)
        // Fetching posters shows in the banner but never disables pull to refresh or the rescan button.
        assertFalse(enriching.running)
        assertTrue(enriching.fetchingArtwork)
        // A file scan during a poster fetch is what the buttons wait for.
        val both = UiMappers.scan(ScanState(running = true, sourceName = "Device storage", enriching = true))
        assertTrue(both.running)
        assertEquals("Scanning Device storage", both.label)
    }

    @Test
    fun deviceAccessNoticeSaysWhatKeepsDeviceVideosOut() {
        assertNull(deviceAccessNotice(PermissionUi(granted = true)))
        // The user turned device scanning off: nothing to nag about.
        assertNull(deviceAccessNotice(PermissionUi(granted = false, deviceScanning = false)))
        val none = requireNotNull(deviceAccessNotice(PermissionUi(granted = false)))
        assertEquals("Nova can't see the videos on this device", none.title)
        assertEquals("Allow Access", none.allowLabel)
        val limited = requireNotNull(deviceAccessNotice(PermissionUi(granted = true, partial = true)))
        assertEquals("Nova sees only the videos you picked", limited.title)
        assertEquals("Allow All", limited.allowLabel)
        assertTrue(PermissionUi(granted = false).needsAttention)
        assertFalse(PermissionUi(granted = true).needsAttention)
    }

    @Test
    fun naturalOrderComparesNumbersByValue() {
        val sorted = listOf("Part 10", "part 2", "Part 1", "Part 02b").sortedWith(NaturalOrder)
        assertEquals(listOf("Part 1", "part 2", "Part 02b", "Part 10"), sorted)
    }

    // Fixtures

    private val device = SourceEntity(id = DEVICE, type = SourceType.DEVICE, name = "Device storage", location = "device")
    private val nas = SourceEntity(id = NAS, type = SourceType.SMB, name = "NAS", location = "smb://nas.local/Media")

    private fun row(video: VideoEntity) = VideoWithMeta(video, null)

    private fun video(
        id: Long,
        sourceId: Long,
        uri: String,
        kind: VideoKind = VideoKind.VIDEO,
        fileName: String = uri.substringAfterLast('/'),
        positionMs: Long = 0,
        metadataKey: String? = null,
        localPoster: String? = null,
        localBackdrop: String? = null,
        localThumb: String? = null,
    ) = VideoEntity(
        id = id,
        sourceId = sourceId,
        uri = uri,
        fileName = fileName,
        folder = "Movies",
        durationMs = 120_000,
        dateAdded = 1_000L * id,
        kind = kind,
        parsedTitle = "Heat",
        metadataKey = metadataKey,
        localPoster = localPoster,
        localBackdrop = localBackdrop,
        localThumb = localThumb,
        positionMs = positionMs,
    )

    private fun episode(
        id: Long,
        season: Int?,
        episode: Int,
        fileName: String = "Show.S${season ?: 0}E$episode.mkv",
        positionMs: Long = 0,
        lastPlayedAt: Long? = null,
    ) = VideoEntity(
        id = id,
        sourceId = NAS,
        uri = "smb://nas.local/Media/TV/Show/$fileName",
        fileName = fileName,
        folder = "TV/Show",
        durationMs = 1_800_000,
        dateAdded = 1_000L * id,
        kind = VideoKind.EPISODE,
        parsedTitle = "Show",
        season = season,
        episode = episode,
        showKey = "name:show",
        positionMs = positionMs,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun recentSearchesPutTheNewestFirstWithoutRepeats() {
        val recent = listOf("dune", "Queen", "beatles")
        assertEquals(listOf("queen", "dune", "beatles"), withRecentSearch(recent, " queen "))
        assertEquals(recent, withRecentSearch(recent, "   "))
        assertEquals(listOf("new", "dune"), withRecentSearch(recent, "new", max = 2))
    }
}
