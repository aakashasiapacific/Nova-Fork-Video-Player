package com.aakash.novafork

import com.aakash.novafork.data.PlaylistWithCount
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.ScanState
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.TmdbHost
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.ui.LibraryIndex
import com.aakash.novafork.ui.ListingKey
import com.aakash.novafork.ui.RemoteListing
import com.aakash.novafork.ui.UiMappers
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.CastDeviceUi
import com.aakash.novafork.ui.model.DialogUi
import com.aakash.novafork.ui.model.GestureFeedback
import com.aakash.novafork.ui.model.IdentifyResultUi
import com.aakash.novafork.ui.model.KeyStatus
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.NetworkSourceForm
import com.aakash.novafork.ui.model.PermissionUi
import com.aakash.novafork.ui.model.PlayerUiState
import com.aakash.novafork.ui.model.QueueItemUi
import com.aakash.novafork.ui.model.RepeatMode
import com.aakash.novafork.ui.model.Route
import com.aakash.novafork.ui.model.ServerLibraryUi
import com.aakash.novafork.ui.model.SettingsUi
import com.aakash.novafork.ui.model.SmbForm
import com.aakash.novafork.ui.model.TrackUi
import com.aakash.novafork.ui.model.UpNextUi
import com.aakash.novafork.ui.model.VideoDetailsUi

/**
 * A believable library: TMDB-matched movies and shows on a picked folder, a NAS and the device,
 * a couple of home videos and two saved streams, plus an SFTP seedbox and a Jellyfin server that
 * have nothing scanned yet. Only entities live here; every screen model is
 * derived from them by the real [UiMappers], the same way MainViewModel does it.
 */
object FakeLibrary {
    /** Fixed "now" (11 Oct 2025) so dates, progress and orderings never depend on the clock. */
    const val NOW = 1_760_180_400_000L
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val MB = 1L shl 20

    // Sources
    const val DEVICE = 1L
    const val MOVIES_FOLDER = 2L
    const val NAS = 3L
    const val BUNNY_STREAM = 4L
    const val TEARS_STREAM = 5L
    const val SEEDBOX = 6L
    const val DEN_JELLYFIN = 7L

    // Movies (all in the picked "Movies" folder)
    const val DUNE = 101L
    const val OPPENHEIMER = 102L
    const val INTERSTELLAR = 103L
    const val SPIRITED_AWAY = 104L
    const val RRR = 105L
    const val EVERYTHING = 106L
    const val PARASITE = 107L

    // Episodes
    const val SEVERANCE_S1E1 = 201L
    const val SEVERANCE_S2E3 = 207L
    const val SEVERANCE_S2E4 = 208L
    const val OFFICE_S2E4 = 223L
    const val PANCHAYAT_S3E4 = 244L
    const val SHOGUN_S1E1 = 261L

    // Home videos and streams
    const val BEACH_CLIP = 301L
    const val CAMERA_CLIP = 302L
    const val BUNNY = 401L
    const val TEARS = 402L

    const val SEVERANCE_KEY = "tv:95396"
    const val OFFICE_KEY = "tv:2316"
    const val PANCHAYAT_KEY = "tv:101352"
    const val SHOGUN_KEY = "tv:126308"

    const val MOVIES_TREE = "content://com.android.externalstorage.documents/tree/primary%3AMovies"

    // Playlists
    const val SEVERANCE_REWATCH = 1L
    const val MOVIE_NIGHT = 2L
    const val CLIPS_TO_SORT = 3L

    /** As the playlist DAO lists them: most recently changed first, counting the videos shown. */
    val playlists: List<PlaylistWithCount> by lazy {
        listOf(
            PlaylistWithCount(SEVERANCE_REWATCH, "Severance rewatch", itemCount = 8, updatedAt = NOW - 2 * HOUR, coverVideoId = SEVERANCE_S1E1),
            PlaylistWithCount(MOVIE_NIGHT, "Movie night", itemCount = 4, updatedAt = NOW - 3 * DAY, coverVideoId = DUNE),
            PlaylistWithCount(CLIPS_TO_SORT, "Clips to sort", itemCount = 0, updatedAt = NOW - 9 * DAY, coverVideoId = null),
        )
    }

    /** Each playlist's videos in playlist order. */
    val playlistItems: Map<Long, List<Long>> by lazy {
        mapOf(
            SEVERANCE_REWATCH to (SEVERANCE_S1E1..SEVERANCE_S2E4).toList(),
            MOVIE_NIGHT to listOf(DUNE, INTERSTELLAR, SPIRITED_AWAY, RRR),
            CLIPS_TO_SORT to emptyList(),
        )
    }

    val sources: List<SourceEntity> = listOf(
        SourceEntity(id = DEVICE, type = SourceType.DEVICE, name = "Device storage", location = "device", addedAt = NOW - 60 * DAY),
        SourceEntity(id = MOVIES_FOLDER, type = SourceType.FOLDER, name = "Movies", location = MOVIES_TREE, addedAt = NOW - 40 * DAY),
        SourceEntity(
            id = NAS,
            type = SourceType.SMB,
            name = "NAS · Media",
            location = "smb://nas.local/Media",
            username = "aakash",
            password = "correct-horse",
            addedAt = NOW - 30 * DAY,
            lastScanAt = NOW - 2 * HOUR,
        ),
        SourceEntity(
            id = BUNNY_STREAM,
            type = SourceType.STREAM,
            name = "Big Buck Bunny",
            location = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            addedAt = NOW - 20 * DAY,
        ),
        SourceEntity(
            id = TEARS_STREAM,
            type = SourceType.STREAM,
            name = "Tears of Steel",
            location = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            addedAt = NOW - 18 * DAY,
        ),
        SourceEntity(
            id = SEEDBOX,
            type = SourceType.SFTP,
            name = "Seedbox",
            location = "sftp://seedbox.example.net/downloads",
            username = "aakash",
            password = "correct-horse",
            addedAt = NOW - 3 * DAY,
        ),
        SourceEntity(
            id = DEN_JELLYFIN,
            type = SourceType.JELLYFIN,
            name = "Den Jellyfin",
            location = "http://192.168.1.5:8096",
            port = 8096,
            username = "aakash",
            password = "correct-horse",
            addedAt = NOW - 2 * DAY,
        ),
    )

    /** What the Jellyfin server's browser lists: its libraries, and the Movies library (nothing scanned yet). */
    internal val jellyfinListings: Map<ListingKey, RemoteListing> by lazy {
        fun movie(id: String, title: String, gigabytes: Double) = RemoteEntry(
            name = title,
            path = id,
            isDirectory = false,
            sizeBytes = (gigabytes * (1L shl 30)).toLong(),
            playUri = "http://192.168.1.5:8096/Videos/$id/stream?static=true",
            imageUrl = "http://192.168.1.5:8096/Items/$id/Images/Primary?maxWidth=342&tag=poster",
        )
        mapOf(
            ListingKey(DEN_JELLYFIN, "") to RemoteListing(
                entries = listOf(
                    RemoteEntry(name = "Movies", path = "lib-movies", isDirectory = true),
                    RemoteEntry(name = "Shows", path = "lib-shows", isDirectory = true),
                ),
            ),
            ListingKey(DEN_JELLYFIN, "lib-movies") to RemoteListing(
                entries = listOf(
                    RemoteEntry(name = "Collections", path = "col-1", isDirectory = true),
                    movie("a1", "Arrival", 7.8),
                    movie("b2", "Blade Runner 2049", 21.4),
                    movie("h3", "Heat", 14.2),
                    movie("h4", "Her", 6.1),
                    movie("m5", "Mad Max: Fury Road", 18.9),
                ),
            ),
        )
    }

    private val dune = movieMeta(
        tmdbId = 693134,
        title = "Dune: Part Two",
        year = 2024,
        slug = "dune2",
        rating = 8.2,
        genres = "Science Fiction, Adventure",
        runtimeMin = 167,
        tagline = "Long live the fighters.",
        overview = "Paul Atreides unites with Chani and the Fremen while on a path of revenge against the " +
            "conspirators who destroyed his family. Facing a choice between the love of his life and the fate " +
            "of the known universe, he endeavors to prevent a terrible future only he can foresee.",
    )
    private val oppenheimer = movieMeta(
        tmdbId = 872585,
        title = "Oppenheimer",
        year = 2023,
        slug = "oppenheimer",
        rating = 8.1,
        genres = "Drama, History",
        runtimeMin = 181,
        tagline = "The world forever changes.",
        overview = "The story of J. Robert Oppenheimer's role in the development of the atomic bomb during World War II.",
    )
    private val interstellar = movieMeta(
        tmdbId = 157336,
        title = "Interstellar",
        year = 2014,
        slug = "interstellar",
        rating = 8.4,
        genres = "Adventure, Drama, Science Fiction",
        runtimeMin = 169,
        tagline = "Mankind was born on Earth. It was never meant to die here.",
        overview = "The adventures of a group of explorers who make use of a newly discovered wormhole to surpass " +
            "the limitations on human space travel and conquer the vast distances involved in an interstellar voyage.",
    )
    private val spiritedAway = movieMeta(
        tmdbId = 129,
        title = "Spirited Away",
        year = 2001,
        slug = "spirited-away",
        rating = 8.5,
        genres = "Animation, Family, Fantasy",
        runtimeMin = 125,
        tagline = null,
        overview = "A young girl, Chihiro, becomes trapped in a strange new world of spirits. When her parents " +
            "undergo a mysterious transformation, she must call upon the courage she never knew she had to free her family.",
    )
    private val rrr = movieMeta(
        tmdbId = 579974,
        title = "RRR",
        year = 2022,
        slug = "rrr",
        rating = 7.8,
        genres = "Action, Drama",
        runtimeMin = 187,
        tagline = "Rise Roar Revolt.",
        overview = "A fictional history of two legendary revolutionaries' journey away from home before they began " +
            "fighting for their country in the 1920s.",
    )
    private val everything = movieMeta(
        tmdbId = 545611,
        title = "Everything Everywhere All at Once",
        year = 2022,
        slug = "eeaao",
        rating = 7.8,
        genres = "Action, Adventure, Science Fiction",
        runtimeMin = 140,
        tagline = "The universe is so much bigger than you realize.",
        overview = "An aging Chinese immigrant is swept up in an insane adventure, where she alone can save what's " +
            "important to her by connecting with the lives she could have led in other universes.",
    )
    private val parasite = movieMeta(
        tmdbId = 496243,
        title = "Parasite",
        year = 2019,
        slug = "parasite",
        rating = 8.5,
        genres = "Comedy, Thriller, Drama",
        runtimeMin = 133,
        tagline = "Act like you own the place.",
        overview = "All unemployed, Ki-taek's family takes peculiar interest in the wealthy and glamorous Parks for " +
            "their livelihood until they get entangled in an unexpected incident.",
    )

    private val severance = showMeta(
        key = SEVERANCE_KEY,
        title = "Severance",
        year = 2022,
        slug = "severance",
        rating = 8.4,
        genres = "Drama, Mystery, Science Fiction",
        seasons = listOf(1, 2),
        overview = "Mark leads a team of office workers whose memories have been surgically divided between their " +
            "work and personal lives. When a mysterious colleague appears outside of work, it begins a journey to " +
            "discover the truth about their jobs.",
    )
    private val office = showMeta(
        key = OFFICE_KEY,
        title = "The Office",
        year = 2005,
        slug = "the-office",
        rating = 8.6,
        genres = "Comedy",
        seasons = listOf(2),
        overview = "The everyday lives of office employees in the Scranton, Pennsylvania branch of the fictional " +
            "Dunder Mifflin Paper Company.",
    )
    private val panchayat = showMeta(
        key = PANCHAYAT_KEY,
        title = "Panchayat",
        year = 2020,
        slug = "panchayat",
        rating = 8.9,
        genres = "Comedy, Drama",
        seasons = listOf(3),
        overview = "An engineering graduate with few job offers takes up the post of secretary of a panchayat " +
            "office in a remote village in Uttar Pradesh.",
    )
    private val shogun = showMeta(
        key = SHOGUN_KEY,
        title = "Shōgun",
        year = 2024,
        slug = "shogun",
        rating = 8.6,
        genres = "Drama, War & Politics",
        seasons = listOf(1),
        overview = "In Japan in the year 1600, at the dawn of a century-defining civil war, Lord Yoshii Toranaga is " +
            "fighting for his life as his enemies on the Council of Regents unite against him.",
    )

    val metadata: List<MetadataEntity> =
        listOf(dune, oppenheimer, interstellar, spiritedAway, rrr, everything, parasite, severance, office, panchayat, shogun)

    val rows: List<VideoWithMeta> = buildList {
        add(movie(DUNE, dune, "Sci-Fi", "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.mkv", "Dune Part Two", addedDaysAgo = 1.0, sizeMb = 18_840, width = 3840, height = 1606, positionMs = 65 * MINUTE + 12_000, lastPlayed = NOW - 3 * HOUR))
        add(movie(OPPENHEIMER, oppenheimer, "", "Oppenheimer.2023.1080p.BluRay.x265.mkv", "Oppenheimer", addedDaysAgo = 4.0, sizeMb = 6_310, width = 1920, height = 804))
        add(movie(INTERSTELLAR, interstellar, "Sci-Fi", "Interstellar (2014) [1080p].mp4", "Interstellar", addedDaysAgo = 9.0, sizeMb = 3_720, width = 1920, height = 800, watched = true))
        add(movie(SPIRITED_AWAY, spiritedAway, "Anime", "Spirited.Away.2001.JAPANESE.1080p.BluRay.mkv", "Spirited Away", addedDaysAgo = 15.0, sizeMb = 2_480, width = 1920, height = 1038, localPoster = "$MOVIES_TREE/document/primary%3AMovies%2FAnime%2Fposter.jpg"))
        add(movie(RRR, rrr, "", "RRR.2022.1080p.NF.WEB-DL.Hindi.mkv", "RRR", addedDaysAgo = 6.0, sizeMb = 5_120, width = 1920, height = 800))
        add(movie(EVERYTHING, everything, "", "Everything.Everywhere.All.at.Once.2022.1080p.mkv", "Everything Everywhere All at Once", addedDaysAgo = 12.0, sizeMb = 4_460, width = 1920, height = 1040, positionMs = 28 * MINUTE, lastPlayed = NOW - 5 * DAY))
        add(movie(PARASITE, parasite, "", "Parasite.2019.KOREAN.1080p.BluRay.mkv", "Parasite", addedDaysAgo = 20.0, sizeMb = 3_050, width = 1920, height = 804))

        val severanceS1 = listOf(
            Ep(1, "Good News About Hell", "2022-02-18", "Mark is promoted to lead Macrodata Refinement, and a new hire wakes up on a conference table with no idea who she is."),
            Ep(2, "Half Loop", "2022-02-18", "Mark trains Helly while the team chases its quarterly target; outside, a stranger claims to have known Mark's innie."),
            Ep(3, "In Perpetuity", "2022-02-25", "The refiners tour the Perpetuity Wing, and Helly starts planning a way out."),
            Ep(4, "The You You Are", "2022-03-04", "Helly tries to send a message to the outside, and Irving finds something odd in Optics and Design."),
        )
        val severanceS2 = listOf(
            Ep(1, "Hello, Ms. Cobel", "2025-01-17", "Mark returns to a severed floor that has changed in his absence."),
            Ep(2, "Goodbye, Mrs. Selvig", "2025-01-24", "Outside, Mark's life is upended by what he learned on the inside."),
            Ep(3, "Who Is Alive?", "2025-01-31", "The refiners wrestle with a new project as Lumon shows its hand."),
            Ep(4, "Woe's Hollow", "2025-02-07", "A team-building retreat takes the refiners somewhere cold and strange."),
        )
        severanceS1.forEachIndexed { i, ep ->
            add(nasEpisode(SEVERANCE_S1E1 + i, severance, "Severance", 1, ep, addedDaysAgo = 25.0, durationMin = 57, watched = true))
        }
        severanceS2.forEachIndexed { i, ep ->
            val id = SEVERANCE_S1E1 + 4 + i
            add(
                nasEpisode(
                    id = id,
                    meta = severance,
                    folderTitle = "Severance",
                    season = 2,
                    ep = ep,
                    addedDaysAgo = 3.0 - i * 0.1,
                    durationMin = 52,
                    watched = ep.number < 3,
                    positionMs = if (id == SEVERANCE_S2E3) 31 * MINUTE + 40_000 else 0,
                    lastPlayed = if (id == SEVERANCE_S2E3) NOW - 20 * HOUR else null,
                ),
            )
        }

        val officeS2 = listOf(
            Ep(1, "The Dundies", "2005-09-20", "Michael hosts the annual Dundie Awards at the local Chili's, and Pam has a night to remember."),
            Ep(3, "Office Olympics", "2005-10-04", "While Michael and Dwight are out house hunting, Jim and Pam run a set of improvised office games."),
            Ep(4, "The Fire", "2005-10-11", "A fire in the kitchen sends everyone out to the parking lot, where Jim starts a game of Desert Island."),
            Ep(5, "Halloween", "2005-10-18", "Michael has until the end of the day to fire someone, and keeps putting it off."),
        )
        officeS2.forEachIndexed { i, ep ->
            add(nasEpisode(221L + i, office, "The Office", 2, ep, addedDaysAgo = 33.0, durationMin = 22, watched = ep.number <= 3))
        }

        (1..5).forEach { number ->
            add(panchayatEpisode(240L + number, number))
        }

        val shogunS1 = listOf(
            Ep(1, "Anjin", "2024-02-27", "A stranded English navigator and a cornered warlord cross paths on the coast of Ajiro."),
            Ep(2, "Servants of Two Masters", "2024-02-27", "Blackthorne is brought to Osaka, where Toranaga weighs what the stranger knows."),
            Ep(3, "Tomorrow Is Tomorrow", "2024-03-05", "Toranaga attempts a desperate escape from Osaka."),
            Ep(4, "The Eightfold Fence", "2024-03-12", "Blackthorne begins to train Toranaga's troops in Ajiro."),
        )
        shogunS1.forEachIndexed { i, ep ->
            add(nasEpisode(SHOGUN_S1E1 + i, shogun, "Shogun", 1, ep, addedDaysAgo = 0.5, durationMin = 60))
        }

        add(
            homeVideo(
                id = BEACH_CLIP,
                mediaId = 1201,
                fileName = "Goa trip - Palolem sunset.mp4",
                folder = "DCIM/Trips",
                durationMs = 12 * MINUTE + 34_000,
                addedDaysAgo = 27.0,
            ),
        )
        add(
            homeVideo(
                id = CAMERA_CLIP,
                mediaId = 1188,
                fileName = "VID_20250914_183012.mp4",
                folder = "DCIM/Camera",
                durationMs = 94_000,
                addedDaysAgo = 28.0,
            ),
        )
        add(stream(BUNNY, BUNNY_STREAM, addedDaysAgo = 20.0))
        add(stream(TEARS, TEARS_STREAM, addedDaysAgo = 18.0))
    }

    fun index(preferLocalArtwork: Boolean = false): LibraryIndex = LibraryIndex.build(rows, sources, preferLocalArtwork)

    // Builders

    private class Ep(val number: Int, val title: String, val airDate: String, val overview: String)

    private fun tmdbPath(slug: String, kind: String): String = "/$slug-$kind.jpg"

    private fun movieMeta(
        tmdbId: Int,
        title: String,
        year: Int,
        slug: String,
        rating: Double,
        genres: String,
        runtimeMin: Int,
        tagline: String?,
        overview: String,
    ) = MetadataEntity(
        key = "movie:$tmdbId",
        tmdbId = tmdbId,
        type = "movie",
        title = title,
        originalTitle = title,
        year = year,
        overview = overview,
        tagline = tagline,
        posterPath = "/$slug.jpg",
        backdropPath = tmdbPath(slug, "backdrop"),
        rating = rating,
        genres = genres,
        runtimeMin = runtimeMin,
        updatedAt = NOW - 2 * DAY,
    )

    private fun showMeta(
        key: String,
        title: String,
        year: Int,
        slug: String,
        rating: Double,
        genres: String,
        seasons: List<Int>,
        overview: String,
    ) = MetadataEntity(
        key = key,
        tmdbId = key.substringAfter(':').toInt(),
        type = "tv",
        title = title,
        year = year,
        overview = overview,
        posterPath = "/$slug.jpg",
        backdropPath = tmdbPath(slug, "backdrop"),
        rating = rating,
        genres = genres,
        seasonPostersJson = seasons.joinToString(",", prefix = "{", postfix = "}") { "\"$it\":\"${tmdbPath(slug, "s$it")}\"" },
        updatedAt = NOW - 2 * DAY,
    )

    /** SAF document URI of a file below the picked "Movies" folder. */
    private fun movieDocument(subfolder: String, fileName: String): String {
        val path = listOf("Movies", subfolder, fileName).filter { it.isNotEmpty() }.joinToString("/")
        val encoded = path.replace("%", "%25").replace(" ", "%20").replace("/", "%2F").replace("(", "%28").replace(")", "%29")
            .replace("[", "%5B").replace("]", "%5D")
        return "$MOVIES_TREE/document/primary%3A$encoded"
    }

    private fun movie(
        id: Long,
        meta: MetadataEntity,
        subfolder: String,
        fileName: String,
        parsedTitle: String,
        addedDaysAgo: Double,
        sizeMb: Long,
        width: Int,
        height: Int,
        positionMs: Long = 0,
        lastPlayed: Long? = null,
        watched: Boolean = false,
        localPoster: String? = null,
    ) = VideoWithMeta(
        video = VideoEntity(
            id = id,
            sourceId = MOVIES_FOLDER,
            uri = movieDocument(subfolder, fileName),
            fileName = fileName,
            folder = listOf("Movies", subfolder).filter { it.isNotEmpty() }.joinToString("/"),
            sizeBytes = sizeMb * MB,
            durationMs = (meta.runtimeMin ?: 120) * MINUTE - 47_000,
            width = width,
            height = height,
            dateAdded = NOW - (addedDaysAgo * DAY).toLong(),
            kind = VideoKind.MOVIE,
            parsedTitle = parsedTitle,
            parsedYear = meta.year,
            metadataKey = meta.key,
            matchState = MatchState.MATCHED,
            localPoster = localPoster,
            positionMs = positionMs,
            lastPlayedAt = lastPlayed ?: if (watched) NOW - 8 * DAY else null,
            watched = watched,
            playCount = if (watched) 1 else 0,
        ),
        meta = meta,
    )

    private fun nasEpisode(
        id: Long,
        meta: MetadataEntity,
        folderTitle: String,
        season: Int,
        ep: Ep,
        addedDaysAgo: Double,
        durationMin: Int,
        watched: Boolean = false,
        positionMs: Long = 0,
        lastPlayed: Long? = null,
    ): VideoWithMeta {
        val code = "S${season.toString().padStart(2, '0')}E${ep.number.toString().padStart(2, '0')}"
        val fileName = "$folderTitle.$code.${ep.title.replace(Regex("[^A-Za-z0-9]+"), ".").trim('.')}.1080p.WEB-DL.mkv"
        val folder = "TV/$folderTitle/Season $season"
        val slug = meta.posterPath.orEmpty().removePrefix("/").removeSuffix(".jpg")
        return VideoWithMeta(
            video = VideoEntity(
                id = id,
                sourceId = NAS,
                uri = "smb://nas.local/Media/" + "$folder/$fileName".replace(" ", "%20"),
                fileName = fileName,
                folder = folder,
                sizeBytes = durationMin * 26 * MB,
                durationMs = durationMin * MINUTE + 13_000,
                width = 1920,
                height = 1080,
                dateAdded = NOW - (addedDaysAgo * DAY).toLong(),
                kind = VideoKind.EPISODE,
                parsedTitle = folderTitle,
                season = season,
                episode = ep.number,
                metadataKey = meta.key,
                showKey = meta.key,
                episodeTitle = ep.title,
                episodeOverview = ep.overview,
                episodeStillPath = tmdbPath(slug, "s${season}e${ep.number}"),
                episodeAirDate = ep.airDate,
                matchState = MatchState.MATCHED,
                positionMs = positionMs,
                lastPlayedAt = lastPlayed ?: if (watched) NOW - 10 * DAY else null,
                watched = watched,
                playCount = if (watched) 1 else 0,
            ),
            meta = meta,
        )
    }

    /** Downloaded to the phone, matched to the show but without episode details on TMDB yet. */
    private fun panchayatEpisode(id: Long, number: Int): VideoWithMeta {
        val inProgress = id == PANCHAYAT_S3E4
        return VideoWithMeta(
            video = VideoEntity(
                id = id,
                sourceId = DEVICE,
                uri = "content://media/external/video/media/${1840 + number}",
                fileName = "Panchayat.S03E0$number.1080p.AMZN.WEB-DL.mkv",
                folder = "Download/Panchayat S03",
                sizeBytes = 1_240 * MB,
                durationMs = (33 + number) * MINUTE,
                width = 1920,
                height = 1080,
                dateAdded = NOW - 7 * DAY,
                kind = VideoKind.EPISODE,
                parsedTitle = "Panchayat",
                season = 3,
                episode = number,
                metadataKey = PANCHAYAT_KEY,
                showKey = PANCHAYAT_KEY,
                matchState = MatchState.MATCHED,
                positionMs = if (inProgress) 12 * MINUTE + 5_000 else 0,
                lastPlayedAt = if (inProgress) NOW - 2 * DAY else if (number < 4) NOW - 3 * DAY else null,
                watched = number < 4,
                playCount = if (number < 4) 1 else 0,
            ),
            meta = panchayat,
        )
    }

    private fun homeVideo(
        id: Long,
        mediaId: Int,
        fileName: String,
        folder: String,
        durationMs: Long,
        addedDaysAgo: Double,
    ) = VideoWithMeta(
        video = VideoEntity(
            id = id,
            sourceId = DEVICE,
            uri = "content://media/external/video/media/$mediaId",
            fileName = fileName,
            folder = folder,
            sizeBytes = durationMs / 1000 * 2 * MB,
            durationMs = durationMs,
            width = 3840,
            height = 2160,
            dateAdded = NOW - (addedDaysAgo * DAY).toLong(),
            kind = VideoKind.VIDEO,
            parsedTitle = fileName.substringBeforeLast('.'),
            matchState = MatchState.UNMATCHED,
        ),
        meta = null,
    )

    private fun stream(id: Long, sourceId: Long, addedDaysAgo: Double): VideoWithMeta {
        val source = sources.first { it.id == sourceId }
        return VideoWithMeta(
            video = VideoEntity(
                id = id,
                sourceId = sourceId,
                uri = source.location,
                fileName = source.location.substringAfterLast('/'),
                folder = "",
                dateAdded = NOW - (addedDaysAgo * DAY).toLong(),
                kind = VideoKind.VIDEO,
                parsedTitle = source.name,
                matchState = MatchState.UNMATCHED,
            ),
            meta = null,
        )
    }
}

/** The app state MainViewModel would publish for each screen, built from [FakeLibrary]. */
object FakeScreens {
    private val index: LibraryIndex by lazy { FakeLibrary.index() }
    private val idle = ScanState()

    val settings: SettingsUi
        get() = SettingsUi(
            settings = Settings(
                tmdbApiKey = "0f3a9c2e7b5d4f6a8c1e2b3d4f5a6b7c",
                tmdbLanguage = "en-US",
                tmdbHost = TmdbHost.DEFAULT,
            ),
            hasBuiltInKey = true,
            keyStatus = KeyStatus.VALID,
            versionName = "1.0.42",
            sources = UiMappers.sources(index, idle),
            allFilesAccess = false,
            canRequestAllFilesAccess = true,
        )

    /** Everything loaded, as on a warm start; [route] picks the page. */
    fun app(route: Route = Route.Home): AppUiState = AppUiState(
        route = route,
        canGoBack = route !in TOP_LEVEL,
        home = index.home,
        library = UiMappers.library(index, LibraryFilter.ALL, LibrarySort.RECENT),
        browse = UiMappers.browse(index, idle, FakeLibrary.playlists),
        settings = settings,
        permission = PermissionUi(granted = true),
    )

    fun home(): AppUiState = app(Route.Home)

    /** First launch: nothing scanned and no storage permission yet. */
    fun onboarding(): AppUiState = AppUiState(
        route = Route.Home,
        settings = settings.copy(sources = emptyList()),
        permission = PermissionUi(granted = false),
    )

    fun library(filter: LibraryFilter = LibraryFilter.ALL): AppUiState =
        app(Route.Library).copy(library = UiMappers.library(index, filter, LibrarySort.RECENT))

    /** Tablet list-detail: the grid with [videoId] loaded into the preview pane. */
    fun libraryPreview(videoId: Long): AppUiState =
        library().copy(previewKey = "v:$videoId", videoDetails = videoDetailsUi(videoId))

    fun videoDetails(videoId: Long): AppUiState =
        app(Route.VideoDetails(videoId)).copy(videoDetails = videoDetailsUi(videoId))

    fun showDetails(showKey: String): AppUiState = app(Route.ShowDetails(showKey)).copy(
        showDetails = requireNotNull(UiMappers.showDetails(index, showKey)) { "No show $showKey in the fake library" },
    )

    fun browse(): AppUiState = app(Route.Browse)

    fun browseDir(sourceId: Long, path: String = ""): AppUiState = app(Route.BrowseDir(sourceId, path)).copy(
        browseDir = requireNotNull(UiMappers.browseDir(index, sourceId, path, idle)) { "No source $sourceId in the fake library" },
    )

    /** A Jellyfin library listed live: nothing scanned yet, so every movie plays straight from the server. */
    fun remoteDir(): AppUiState {
        val source = index.sourcesById.getValue(FakeLibrary.DEN_JELLYFIN)
        return app(Route.BrowseDir(source.id, "lib-movies")).copy(
            browseDir = UiMappers.remoteDir(index, source, "lib-movies", FakeLibrary.jellyfinListings),
        )
    }

    fun search(query: String): AppUiState = app(Route.Search).copy(search = UiMappers.search(index, query))

    fun playlist(playlistId: Long): AppUiState {
        val playlist = FakeLibrary.playlists.first { it.id == playlistId }
        val videoIds = FakeLibrary.playlistItems.getValue(playlistId)
        return app(Route.Playlist(playlistId)).copy(
            playlist = UiMappers.playlistDetails(index, playlistId, playlist.name, videoIds),
        )
    }

    fun settingsPage(): AppUiState = app(Route.Settings)

    /** Adding a share from Browse, details typed in. */
    fun smbDialog(): AppUiState = browse().copy(
        dialog = DialogUi.SmbServer(
            form = SmbForm(
                name = "Living room NAS",
                host = "192.168.1.20",
                share = "Media",
                path = "TV",
                username = "aakash",
                password = "correct-horse",
            ),
        ),
    )

    /** Browse › Add network source: the kinds of server. */
    fun addSourceDialog(): AppUiState = browse().copy(dialog = DialogUi.AddSource)

    /** Adding a Jellyfin server: the connection tested, its libraries listed and two of them chosen. */
    fun jellyfinDialog(): AppUiState = browse().copy(
        dialog = DialogUi.NetworkServer(
            form = NetworkSourceForm(
                type = SourceType.JELLYFIN,
                name = "Den Jellyfin",
                address = "http://192.168.1.5:8096",
                username = "aakash",
                password = "correct-horse",
                libraryIds = listOf("lib-movies", "lib-shows"),
            ),
            testResult = "",
            libraries = listOf(
                ServerLibraryUi(id = "lib-movies", name = "Movies"),
                ServerLibraryUi(id = "lib-shows", name = "Shows"),
                ServerLibraryUi(id = "lib-anime", name = "Anime"),
                ServerLibraryUi(id = "lib-home", name = "Home videos"),
            ),
        ),
    )

    /** Picking the right Shōgun for an episode that matched the 1980 miniseries. */
    fun identifyDialog(): AppUiState = videoDetails(FakeLibrary.SHOGUN_S1E1).copy(
        dialog = DialogUi.Identify(
            videoId = FakeLibrary.SHOGUN_S1E1,
            query = "Shogun",
            tv = true,
            results = listOf(
                IdentifyResultUi(
                    tmdbId = 126308,
                    isTv = true,
                    title = "Shōgun",
                    year = 2024,
                    overview = "In Japan in the year 1600, at the dawn of a century-defining civil war, Lord Yoshii " +
                        "Toranaga is fighting for his life as his enemies on the Council of Regents unite against him.",
                    posterUrl = "https://image.tmdb.org/t/p/w342/shogun.jpg",
                ),
                IdentifyResultUi(
                    tmdbId = 1606,
                    isTv = true,
                    title = "Shōgun",
                    year = 1980,
                    overview = "An English navigator shipwrecked in feudal Japan is drawn into the power struggle " +
                        "between two rival warlords.",
                    posterUrl = "https://image.tmdb.org/t/p/w342/shogun-1980.jpg",
                ),
                IdentifyResultUi(
                    tmdbId = 220150,
                    isTv = true,
                    title = "Ōoku: The Inner Chambers",
                    year = 2023,
                    overview = "A mysterious disease strikes down the men of Edo-era Japan, and women take over " +
                        "the roles of the shogunate.",
                    posterUrl = null,
                ),
            ),
        ),
    )

    private fun videoDetailsUi(videoId: Long): VideoDetailsUi =
        requireNotNull(UiMappers.videoDetails(index, videoId)) { "No video $videoId in the fake library" }

    private val TOP_LEVEL = setOf(Route.Home, Route.Audio, Route.Settings, Route.Search)
}

/** Player states as PlayerController publishes them. */
object FakePlayer {
    private const val DUNE_DURATION = 166 * 60_000L + 13_000L
    private const val DUNE_POSITION = 65 * 60_000L + 12_000L
    private const val EPISODE_DURATION = 52 * 60_000L + 13_000L

    private val audio = listOf(
        TrackUi(id = -1, name = "Disable", selected = false),
        TrackUi(id = 1, name = "English · Dolby Digital Plus 5.1", selected = true),
        TrackUi(id = 2, name = "English · Audio description", selected = false),
        TrackUi(id = 3, name = "Hindi · 5.1", selected = false),
    )

    private val subtitles = listOf(
        TrackUi(id = -1, name = "Disable", selected = false),
        TrackUi(id = 4, name = "English (SDH)", selected = true),
        TrackUi(id = 5, name = "Hindi", selected = false),
        TrackUi(id = 6, name = "Spanish (Latin America)", selected = false),
    )

    /** Dune, paused an hour in: controls stay up. */
    fun paused(): PlayerUiState = PlayerUiState(
        title = "Dune: Part Two",
        subtitle = "2160p · Movies",
        isPlaying = false,
        positionMs = DUNE_POSITION,
        durationMs = DUNE_DURATION,
        audioTracks = audio,
        subtitleTracks = subtitles,
    )

    /** Mid-drag: ten minutes ahead of where playback was. */
    fun seeking(): PlayerUiState = paused().copy(
        isPlaying = true,
        feedback = GestureFeedback.Seek(
            targetMs = DUNE_POSITION + 10 * 60_000L,
            deltaMs = 10 * 60_000L,
            durationMs = DUNE_DURATION,
        ),
    )

    /** Just reopened: playback resumed where it stopped last time. */
    fun resumed(): PlayerUiState = paused().copy(
        isPlaying = true,
        positionMs = DUNE_POSITION + 2_000L,
        resumedFromMs = DUNE_POSITION,
    )

    /** The credits of Severance S2E3, with the next episode counting down. */
    fun upNext(): PlayerUiState {
        val next = requireNotNull(FakeLibrary.index().videosById[FakeLibrary.SEVERANCE_S2E4]) { "Missing next episode" }
        return PlayerUiState(
            title = "Severance",
            subtitle = "S2 · E3 · Who Is Alive?",
            isPlaying = true,
            positionMs = EPISODE_DURATION - 24_000L,
            durationMs = EPISODE_DURATION,
            audioTracks = audio.take(2),
            subtitleTracks = subtitles.take(2),
            upNext = UpNextUi(
                videoId = next.id,
                title = next.title,
                subtitle = next.card.subtitle,
                artwork = next.card.artwork,
                countdownSeconds = 8,
            ),
            hasPrevious = true,
            hasNext = true,
        )
    }

    private val castDevices = listOf(
        CastDeviceUi(id = "chromecast-living-room", name = "Living Room TV"),
        CastDeviceUi(id = "chromecast-bedroom", name = "Bedroom"),
    )

    /**
     * Severance S2E3 from the shuffled "Severance rewatch" playlist, repeating it all; paused, so
     * the controls (with shuffle and repeat lit) stay up while the queue sheet is closed.
     */
    fun queued(): PlayerUiState {
        val index = FakeLibrary.index()
        val shuffled = listOf(205L, FakeLibrary.SEVERANCE_S2E3, 202L, FakeLibrary.SEVERANCE_S2E4, FakeLibrary.SEVERANCE_S1E1, 204L, 203L, 206L)
        val queue = shuffled.map { id ->
            val card = requireNotNull(index.videosById[id]) { "Missing episode $id" }.card
            QueueItemUi(videoId = id, title = card.title, subtitle = card.subtitle, artwork = card.artwork)
        }
        return PlayerUiState(
            title = "Severance",
            subtitle = "S2 · E3 · Who Is Alive?",
            isPlaying = false,
            positionMs = 31 * 60_000L + 40_000L,
            durationMs = EPISODE_DURATION,
            audioTracks = audio.take(2),
            subtitleTracks = subtitles.take(2),
            hasPrevious = true,
            hasNext = true,
            shuffle = true,
            repeatMode = RepeatMode.ALL,
            queue = queue,
            queueIndex = 1,
            castDevices = castDevices,
        )
    }

    /** Dune playing on the living-room Chromecast: the screen shows where instead of the video. */
    fun casting(): PlayerUiState = paused().copy(
        castDevices = castDevices,
        castingTo = "Living Room TV",
    )

    /** The share went away before the file could open. */
    fun error(): PlayerUiState = PlayerUiState(
        title = "Shōgun",
        subtitle = "S1 · E1 · Anjin",
        isPlaying = false,
        positionMs = 0,
        durationMs = 0,
        seekable = false,
        error = "Couldn't open this video. The network share nas.local didn't answer — check that it's on and " +
            "on the same Wi-Fi.",
        hasNext = true,
    )
}
