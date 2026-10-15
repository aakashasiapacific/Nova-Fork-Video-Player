package com.aakash.novafork.ui

import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.data.PlaylistWithCount
import com.aakash.novafork.data.ScanState
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.model.BrowseDirUi
import com.aakash.novafork.ui.model.BrowseUi
import com.aakash.novafork.ui.model.Crumb
import com.aakash.novafork.ui.model.EpisodeUi
import com.aakash.novafork.ui.model.FolderUi
import com.aakash.novafork.ui.model.HeroTarget
import com.aakash.novafork.ui.model.HeroUi
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.LibraryFilter
import com.aakash.novafork.ui.model.LibraryItemUi
import com.aakash.novafork.ui.model.LibrarySort
import com.aakash.novafork.ui.model.LibraryUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.PlaylistDetailsUi
import com.aakash.novafork.ui.model.PlaylistUi
import com.aakash.novafork.ui.model.ScanUi
import com.aakash.novafork.ui.model.SearchUi
import com.aakash.novafork.ui.model.SeasonUi
import com.aakash.novafork.ui.model.ShowDetailsUi
import com.aakash.novafork.ui.model.SourceUi
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.model.VideoDetailsUi

/**
 * Turns a [LibraryIndex] (plus the scan state and what the user picked) into the screen models.
 * Pure Kotlin, so every rule here can be unit-tested without Android.
 */
object UiMappers {
    private const val CONTINUE_WATCHING_LIMIT = 20
    private const val RECENTLY_ADDED_LIMIT = 30
    private const val HERO_LIMIT = 6
    /** Newest movies and shows considered for the hero before the backdrop preference is applied. */
    private const val HERO_RECENT_CANDIDATES = 12
    private const val SEARCH_LIMIT = 60

    // Home

    fun home(index: LibraryIndex): HomeUi {
        val inProgress = continueWatching(index)
        return HomeUi(
            hero = hero(index, inProgress),
            continueWatching = inProgress.map { it.card },
            recentlyAdded = recentlyAdded(index),
            movies = index.movies.sortedWith(TITLE_ORDER).map { it.card },
            shows = index.shows.map { it.card },
            videos = index.otherVideos.map { it.card },
            isEmpty = index.videos.isEmpty(),
        )
    }

    /** Partly watched videos, last played first, one per show. */
    private fun continueWatching(index: LibraryIndex): List<IndexedVideo> =
        index.videos
            .filter { it.inProgress }
            .sortedByDescending { it.video.lastPlayedAt ?: 0L }
            .distinctBy { it.showKey ?: "v:${it.id}" }
            .take(CONTINUE_WATCHING_LIMIT)

    /** Newest first; a show's new episodes collapse into one card for its newest episode. */
    private fun recentlyAdded(index: LibraryIndex): List<VideoCardUi> {
        val cards = ArrayList<VideoCardUi>()
        val seenShows = HashSet<String>()
        for (video in index.videos) {
            if (cards.size == RECENTLY_ADDED_LIMIT) break
            val showKey = video.showKey
            if (showKey == null) {
                cards += video.card
                continue
            }
            if (!seenShows.add(showKey)) continue
            val showPoster = index.showsByKey[showKey]?.card?.artwork?.posterUrl
            cards += if (showPoster == null) {
                video.card
            } else {
                video.card.copy(artwork = video.card.artwork.copy(posterUrl = showPoster))
            }
        }
        return cards
    }

    /** In-progress items first, then the newest movies and shows; items with a backdrop win. */
    private fun hero(index: LibraryIndex, inProgress: List<IndexedVideo>): List<HeroUi> {
        val candidates = LinkedHashMap<HeroTarget, HeroUi>()
        fun add(hero: HeroUi) {
            if (hero.target !in candidates) candidates[hero.target] = hero
        }
        inProgress.forEach { video -> add(heroForInProgress(index, video)) }

        val recentMovies = index.movies
            .take(HERO_RECENT_CANDIDATES)
            .map { it.video.dateAdded to heroForVideo(it) }
        val recentShows = index.shows
            .sortedByDescending { it.latestAdded }
            .take(HERO_RECENT_CANDIDATES)
            .map { it.latestAdded to heroForShow(it) }
        (recentMovies + recentShows)
            .sortedByDescending { it.first }
            .forEach { add(it.second) }
        // sortedByDescending is stable, so the order above survives within each group.
        return candidates.values
            .sortedByDescending { it.artwork.backdropUrl != null }
            .take(HERO_LIMIT)
    }

    private fun heroForInProgress(index: LibraryIndex, video: IndexedVideo): HeroUi {
        val show = video.showKey?.let { index.showsByKey[it] } ?: return heroForVideo(video)
        val code = episodeLabel(video.video.season, video.video.episode, video.video.episodeEnd)
        return HeroUi(
            target = HeroTarget.Show(show.key),
            title = show.title,
            overview = video.video.episodeOverview.nonBlank() ?: show.meta?.overview.nonBlank(),
            artwork = show.card.artwork.copy(stillUrl = video.card.artwork.stillUrl),
            metaLine = joinMeta(code, video.title),
            rating = show.card.rating,
            genres = genresOf(show.meta),
            progress = video.card.progress,
            playVideoId = video.id,
            playLabel = playLabel(resume = true, code = code),
        )
    }

    private fun heroForVideo(video: IndexedVideo): HeroUi {
        val isMovie = video.kind == MediaKind.MOVIE
        val genres = if (isMovie) genresOf(video.meta) else emptyList()
        return HeroUi(
            target = HeroTarget.Video(video.id),
            title = video.title,
            overview = if (isMovie) video.meta?.overview.nonBlank() else null,
            artwork = video.card.artwork,
            metaLine = joinMeta(
                video.card.year?.toString(),
                runtimeLabel(video.video, video.meta.takeIf { isMovie }),
                genres.take(3).joinToString(", "),
            ),
            rating = video.card.rating,
            genres = genres,
            progress = video.card.progress,
            playVideoId = video.id,
            playLabel = if (video.inProgress) "Resume" else "Play",
        )
    }

    private fun heroForShow(show: IndexedShow): HeroUi {
        val next = nextUp(show)
        val genres = genresOf(show.meta)
        return HeroUi(
            target = HeroTarget.Show(show.key),
            title = show.title,
            overview = show.meta?.overview.nonBlank(),
            artwork = show.card.artwork,
            metaLine = joinMeta(
                show.card.year?.toString(),
                countLabel(show.card.seasonCount, "season"),
                genres.take(3).joinToString(", "),
            ),
            rating = show.card.rating,
            genres = genres,
            playVideoId = next.id,
            playLabel = showPlayLabel(next),
        )
    }

    // Library

    fun library(index: LibraryIndex, filter: LibraryFilter, sort: LibrarySort): LibraryUi {
        val movies = index.movies.map(::videoEntry)
        val shows = index.shows.map(::showEntry)
        val videos = index.otherVideos.map(::videoEntry)
        val entries = when (filter) {
            LibraryFilter.ALL -> movies + shows + videos
            LibraryFilter.MOVIES -> movies
            LibraryFilter.SHOWS -> shows
            LibraryFilter.VIDEOS -> videos
        }
        return LibraryUi(
            filter = filter,
            sort = sort,
            items = entries.sortedWith(comparatorFor(sort)).map { it.item },
            counts = mapOf(
                LibraryFilter.ALL to movies.size + shows.size + videos.size,
                LibraryFilter.MOVIES to movies.size,
                LibraryFilter.SHOWS to shows.size,
                LibraryFilter.VIDEOS to videos.size,
            ),
        )
    }

    private class Entry(
        val item: LibraryItemUi,
        val added: Long,
        val sortKey: String,
        val year: Int?,
        val rating: Float?,
    )

    private fun videoEntry(video: IndexedVideo) = Entry(
        item = LibraryItemUi.Video(video.card),
        added = video.video.dateAdded,
        sortKey = video.sortKey,
        year = video.card.year,
        rating = video.card.rating,
    )

    private fun showEntry(show: IndexedShow) = Entry(
        item = LibraryItemUi.Show(show.card),
        added = show.latestAdded,
        sortKey = show.sortKey,
        year = show.card.year,
        rating = show.card.rating,
    )

    private val ENTRY_TITLE_ORDER: Comparator<Entry> = compareBy(NaturalOrder) { it.sortKey }

    private fun comparatorFor(sort: LibrarySort): Comparator<Entry> = when (sort) {
        LibrarySort.RECENT -> compareByDescending<Entry> { it.added }.then(ENTRY_TITLE_ORDER)
        LibrarySort.TITLE -> ENTRY_TITLE_ORDER
        LibrarySort.YEAR -> compareBy<Entry, Int?>(nullsLast(reverseOrder<Int>())) { it.year }.then(ENTRY_TITLE_ORDER)
        LibrarySort.RATING -> compareBy<Entry, Float?>(nullsLast(reverseOrder<Float>())) { it.rating }.then(ENTRY_TITLE_ORDER)
    }

    private val TITLE_ORDER: Comparator<IndexedVideo> = compareBy(NaturalOrder) { it.sortKey }

    // Details

    fun videoDetails(index: LibraryIndex, videoId: Long): VideoDetailsUi? {
        val item = index.videosById[videoId] ?: return null
        val video = item.video
        val meta = item.meta
        val isMovie = item.kind == MediaKind.MOVIE
        val isEpisode = item.kind == MediaKind.EPISODE
        val season = video.season
        val episode = video.episode
        val runtime = if (isEpisode) {
            runtimeLabel(video, null) ?: runtimeLabel(video, meta)
        } else {
            runtimeLabel(video, meta.takeIf { isMovie })
        }
        return VideoDetailsUi(
            id = video.id,
            kind = item.kind,
            title = item.title,
            originalTitle = meta?.originalTitle.nonBlank()
                ?.takeIf { isMovie && !it.equals(item.title, ignoreCase = true) },
            tagline = if (isMovie) meta?.tagline.nonBlank() else null,
            overview = when (item.kind) {
                MediaKind.EPISODE -> video.episodeOverview.nonBlank()
                MediaKind.MOVIE, MediaKind.STREAM -> meta?.overview.nonBlank()
                MediaKind.VIDEO -> null
            },
            year = item.card.year,
            runtimeLabel = runtime,
            rating = item.card.rating,
            genres = genresOf(meta),
            artwork = item.card.artwork,
            progress = item.card.progress,
            positionMs = if (item.inProgress) video.positionMs else 0L,
            durationMs = item.card.durationMs,
            playLabel = if (item.inProgress) "Resume from ${Formats.clock(video.positionMs)}" else "Play",
            watched = video.watched,
            showKey = item.showKey,
            showTitle = item.showTitle,
            season = season,
            episode = episode,
            episodeCode = if (isEpisode && season != null && episode != null) {
                Formats.episodeCode(season, episode, video.episodeEnd)
            } else {
                null
            },
            airDate = video.episodeAirDate.nonBlank(),
            nextEpisode = nextEpisode(index, item)?.card,
            fileName = video.fileName,
            location = locationOf(item),
            sizeLabel = video.sizeBytes?.takeIf { it > 0 }?.let(Formats::size),
            resolutionLabel = resolutionLabel(video.width, video.height),
            sourceLabel = item.source?.let(::sourceLabel).orEmpty(),
            isMatched = meta != null,
        )
    }

    /** The episode after [current] in watching order, skipping other copies of the same episode. */
    private fun nextEpisode(index: LibraryIndex, current: IndexedVideo): IndexedVideo? {
        val show = current.showKey?.let { index.showsByKey[it] } ?: return null
        val position = show.episodes.indexOfFirst { it.id == current.id }
        if (position < 0) return null
        val season = displaySeason(current.video.season)
        val lastEpisode = current.video.episodeEnd ?: current.video.episode ?: return show.episodes.getOrNull(position + 1)
        return show.episodes.drop(position + 1).firstOrNull { other ->
            displaySeason(other.video.season) != season || (other.video.episode ?: Int.MAX_VALUE) > lastEpisode
        }
    }

    /** "Device storage › Movies/Action"; a picked folder's own name is not repeated. */
    private fun locationOf(item: IndexedVideo): String {
        val source = item.source
        val folder = when (source?.type) {
            SourceType.FOLDER -> item.relativeFolder
            else -> item.video.folder.trim('/')
        }
        return when {
            item.kind == MediaKind.STREAM -> item.video.uri
            source == null -> folder
            folder.isEmpty() -> source.name
            else -> "${source.name} › $folder"
        }
    }

    fun showDetails(index: LibraryIndex, showKey: String): ShowDetailsUi? {
        val show = index.showsByKey[showKey] ?: return null
        // Episodes are in watching order (specials last), so the season groups come out in order too.
        val seasons = show.episodes
            .groupBy { displaySeason(it.video.season) }
            .map { (number, episodes) ->
                val seasonPoster = show.seasonPosters[number]?.let { TmdbClient.posterUrl(it) }
                SeasonUi(
                    number = number,
                    title = if (number == 0) "Specials" else "Season $number",
                    posterUrl = if (index.preferLocalArtwork) {
                        show.card.artwork.posterUrl ?: seasonPoster
                    } else {
                        seasonPoster ?: show.card.artwork.posterUrl
                    },
                    episodes = episodes.map(::episodeUi),
                )
            }
        val next = nextUp(show)
        return ShowDetailsUi(
            key = show.key,
            title = show.title,
            overview = show.meta?.overview.nonBlank(),
            tagline = show.meta?.tagline.nonBlank(),
            year = show.card.year,
            rating = show.card.rating,
            genres = genresOf(show.meta),
            artwork = show.card.artwork,
            seasons = seasons,
            nextUp = episodeUi(next),
            playLabel = showPlayLabel(next),
            isMatched = show.meta != null,
        )
    }

    private fun episodeUi(item: IndexedVideo): EpisodeUi {
        val video = item.video
        val number = video.episode
        val end = video.episodeEnd
        return EpisodeUi(
            videoId = video.id,
            season = displaySeason(video.season),
            episode = number ?: 0,
            title = item.title,
            overview = video.episodeOverview.nonBlank(),
            artwork = item.card.artwork,
            durationMs = item.card.durationMs,
            progress = item.card.progress,
            watched = video.watched,
            airDate = video.episodeAirDate.nonBlank(),
            code = when {
                number == null -> ""
                end != null && end > number -> "E$number–E$end"
                else -> "E$number"
            },
        )
    }

    /**
     * What "Play" on a show starts: the episode being watched most recently, else the first
     * unwatched one after the last watched one, else the first episode.
     */
    internal fun nextUp(show: IndexedShow): IndexedVideo {
        show.episodes
            .filter { it.inProgress }
            .maxByOrNull { it.video.lastPlayedAt ?: 0L }
            ?.let { return it }
        val lastWatched = show.episodes.indexOfLast { it.video.watched }
        return show.episodes.drop(lastWatched + 1).firstOrNull { !it.video.watched } ?: show.episodes.first()
    }

    private fun showPlayLabel(next: IndexedVideo): String =
        playLabel(
            resume = next.inProgress,
            code = episodeLabel(next.video.season, next.video.episode, next.video.episodeEnd),
        )

    private fun playLabel(resume: Boolean, code: String?): String {
        val verb = if (resume) "Resume" else "Play"
        return if (code == null) verb else "$verb $code"
    }

    // Browse

    fun browse(index: LibraryIndex, scan: ScanState, playlists: List<PlaylistWithCount> = emptyList()): BrowseUi = BrowseUi(
        sources = sources(index, scan),
        streams = index.videos.filter { it.kind == MediaKind.STREAM }.map { it.card },
        playlists = playlists(index, playlists),
    )

    // Playlists

    fun playlists(index: LibraryIndex, playlists: List<PlaylistWithCount>): List<PlaylistUi> = playlists.map { playlist ->
        PlaylistUi(
            id = playlist.id,
            name = playlist.name,
            itemCount = playlist.itemCount,
            artwork = playlist.coverVideoId?.let { index.videosById[it]?.card?.artwork },
        )
    }

    /** [videoIds] in playlist order; videos the index doesn't show are left out. */
    fun playlistDetails(index: LibraryIndex, id: Long, name: String, videoIds: List<Long>): PlaylistDetailsUi =
        PlaylistDetailsUi(
            id = id,
            name = name,
            videos = videoIds.distinct().mapNotNull { index.videosById[it]?.card },
        )

    /** Device, then picked folders, then network shares; streams are listed as videos instead. */
    fun sources(index: LibraryIndex, scan: ScanState): List<SourceUi> =
        index.sources
            .filter { it.type != SourceType.STREAM }
            .sortedWith(compareBy<SourceEntity> { it.type.ordinal }.thenBy(NaturalOrder) { it.name })
            .map { source ->
                SourceUi(
                    id = source.id,
                    type = source.type,
                    name = source.name,
                    detail = sourceDetail(source),
                    videoCount = index.videoCountBySource[source.id] ?: 0,
                    isScanning = isScanning(source, scan),
                    error = source.lastError.nonBlank(),
                )
            }

    private fun isScanning(source: SourceEntity, scan: ScanState): Boolean =
        scan.running && scan.sourceName == source.name

    internal fun sourceDetail(source: SourceEntity): String = when (source.type) {
        SourceType.DEVICE -> "Internal storage and SD cards"
        SourceType.FOLDER -> treeFolderLabel(source.location) ?: source.location
        SourceType.SMB -> SmbUris.parse(source.location)
            ?.let { location ->
                val host = location.port?.let { "${location.host}:$it" } ?: location.host
                listOf(host, location.share, location.path).filter { it.isNotEmpty() }.joinToString("/")
            }
            ?: source.location
        SourceType.STREAM, SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP, SourceType.WEBDAV,
        SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
        -> source.location
    }

    /**
     * One folder of a source, built from the library: the videos directly in [path] and the
     * subfolders below it that contain videos (with recursive counts). Null when the source is gone.
     */
    fun browseDir(index: LibraryIndex, sourceId: Long, path: String, scan: ScanState): BrowseDirUi? {
        val source = index.sourcesById[sourceId] ?: return null
        val dir = path.trim('/')
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val subfolderCounts = HashMap<String, Int>()
        val here = ArrayList<IndexedVideo>()
        for (video in index.videos) {
            if (video.video.sourceId != sourceId) continue
            val folder = video.relativeFolder
            if (folder == dir) {
                here += video
                continue
            }
            if (!folder.startsWith(prefix)) continue
            val next = folder.substring(prefix.length).substringBefore('/')
            if (next.isNotEmpty()) subfolderCounts[next] = (subfolderCounts[next] ?: 0) + 1
        }
        val segments = dir.split('/').filter { it.isNotEmpty() }
        val crumbs = ArrayList<Crumb>(segments.size + 1)
        crumbs += Crumb(label = source.name, path = "")
        segments.forEachIndexed { i, segment ->
            crumbs += Crumb(label = segment, path = segments.subList(0, i + 1).joinToString("/"))
        }
        return BrowseDirUi(
            sourceId = source.id,
            sourceType = source.type,
            path = dir,
            title = segments.lastOrNull() ?: source.name,
            crumbs = crumbs,
            folders = subfolderCounts
                .map { (name, count) -> FolderUi(sourceId = source.id, path = prefix + name, name = name, videoCount = count) }
                .sortedWith(compareBy(NaturalOrder) { it.name }),
            videos = here.sortedWith(compareBy(NaturalOrder) { it.video.fileName }).map { it.card },
            isLoading = isScanning(source, scan),
            error = source.lastError.nonBlank(),
        )
    }

    // Search

    /** Title, show title and file name, case-insensitive; episodes show up as their show. */
    fun search(index: LibraryIndex, query: String): SearchUi {
        val needle = query.trim()
        if (needle.isEmpty()) return SearchUi(query = query)
        val entries = ArrayList<Entry>()
        val showKeys = LinkedHashSet<String>()
        for (video in index.videos) {
            val matches = video.title.contains(needle, ignoreCase = true) ||
                video.showTitle?.contains(needle, ignoreCase = true) == true ||
                video.video.fileName.contains(needle, ignoreCase = true)
            if (!matches) continue
            val showKey = video.showKey
            if (showKey != null) showKeys += showKey else entries += videoEntry(video)
        }
        // A matched show's TMDB name can differ from what its files are called.
        index.shows.filter { it.title.contains(needle, ignoreCase = true) }.forEach { showKeys += it.key }
        showKeys.mapNotNullTo(entries) { key -> index.showsByKey[key]?.let(::showEntry) }
        val ranked = entries.sortedWith(
            compareBy<Entry> { !it.item.title.startsWith(needle, ignoreCase = true) }.then(ENTRY_TITLE_ORDER),
        )
        return SearchUi(query = query, results = ranked.take(SEARCH_LIMIT).map { it.item })
    }

    // Scan

    fun scan(state: ScanState): ScanUi = when {
        state.running -> ScanUi(
            running = true,
            label = state.sourceName.nonBlank()
                ?.let { name -> if (state.found > 0) "Scanning $name · ${state.found} found" else "Scanning $name" }
                ?: "Scanning library",
        )
        state.enriching -> {
            val total = state.enrichTotal
            val done = state.enrichDone.coerceIn(0, total.coerceAtLeast(0))
            ScanUi(
                running = true,
                label = if (total > 0) "Fetching posters $done / $total" else "Fetching posters",
                progress = if (total > 0) done.toFloat() / total else null,
            )
        }
        else -> ScanUi()
    }
}

/** "1 season", "3 seasons". */
private fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"
