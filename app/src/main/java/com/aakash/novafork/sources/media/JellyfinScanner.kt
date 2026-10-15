package com.aakash.novafork.sources.media

import com.aakash.novafork.core.jellyfin.JellyfinAuth
import com.aakash.novafork.core.jellyfin.JellyfinClient
import com.aakash.novafork.core.jellyfin.JellyfinDates
import com.aakash.novafork.core.jellyfin.JellyfinItem
import com.aakash.novafork.core.jellyfin.JellyfinQuery
import com.aakash.novafork.data.NetworkSources
import com.aakash.novafork.data.ScannedVideo
import com.aakash.novafork.data.ServerItemType
import com.aakash.novafork.data.ServerMetadata
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lists every movie, episode and video of a Jellyfin or Emby server's libraries (the chosen ones,
 * or all video libraries) as the server describes them: stream URLs to play, "<library>/<series>/
 * <season>" folders, and [ServerMetadata] so TMDB is never asked about them.
 */
internal class JellyfinScanner(private val connector: JellyfinConnector) : SourceScanner {
    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> =
        withContext(Dispatchers.IO) {
            connector.withAuth(source) { client, auth ->
                val now = System.currentTimeMillis()
                val found = ArrayList<ScannedVideo>()
                val seen = HashSet<String>()
                for (library in JellyfinItems.chosenLibraries(client.libraries(auth), source)) {
                    currentCoroutineContext().ensureActive()
                    val before = found.size
                    for (video in scanLibrary(client, auth, library, now) { onProgress(before + it) }) {
                        // An item in two libraries plays from the same URL: it is listed once.
                        if (seen.add(video.uri)) found += video
                    }
                    onProgress(found.size)
                }
                found
            }
        }

    private suspend fun scanLibrary(
        client: JellyfinClient,
        auth: JellyfinAuth,
        library: JellyfinItem,
        now: Long,
        onPage: (Int) -> Unit,
    ): List<ScannedVideo> {
        val kind = library.collectionType?.lowercase(Locale.ROOT)
        // Series and seasons only matter where episodes can be: the show's details and season posters.
        val mayHoldShows = kind != "movies" && kind != "homevideos"
        val series = if (mayHoldShows) {
            client.items(auth, JellyfinQuery(parentId = library.id, recursive = true, includeItemTypes = listOf("Series"), fields = SERIES_FIELDS))
                .associateBy { it.id }
        } else {
            emptyMap()
        }
        val seasons = if (series.isNotEmpty()) {
            client.items(auth, JellyfinQuery(parentId = library.id, recursive = true, includeItemTypes = listOf("Season"), fields = emptyList()))
                .associateBy { it.id }
        } else {
            emptyMap()
        }
        val query = JellyfinQuery(parentId = library.id, recursive = true, includeItemTypes = JellyfinItems.VIDEO_TYPES)
        return client.items(auth, query, onPage = onPage).mapNotNull { item ->
            JellyfinItems.toScanned(client, library, item, item.seriesId?.let { series[it] }, item.seasonId?.let { seasons[it] }, now)
        }
    }

    private companion object {
        val SERIES_FIELDS = listOf("Overview", "ProviderIds", "Genres", "Taglines", "OriginalTitle", "SortName")
    }
}

/** How Jellyfin and Emby items become library videos and Browse entries. */
internal object JellyfinItems {
    /** What scans list: movies, episodes and other videos (home videos, clips). */
    val VIDEO_TYPES = listOf("Movie", "Episode", "Video")

    /** Library kinds without videos to play (music, books, photos) or with copies of others (collections, playlists). */
    private val SKIPPED_LIBRARIES = setOf("music", "musicvideos", "books", "audiobooks", "photos", "playlists", "livetv", "boxsets", "trailers")

    /** The libraries [source] scans: the ones it names, else every library that holds videos. */
    fun chosenLibraries(libraries: List<JellyfinItem>, source: SourceEntity): List<JellyfinItem> {
        val wanted = NetworkSources.libraryIds(source)
        return if (wanted.isEmpty()) {
            libraries.filter { it.collectionType?.lowercase(Locale.ROOT) !in SKIPPED_LIBRARIES }
        } else {
            libraries.filter { library -> wanted.any { it.equals(library.id, ignoreCase = true) } }
        }
    }

    fun isVideo(item: JellyfinItem): Boolean = VIDEO_TYPES.any { it.equals(item.type, ignoreCase = true) }

    private fun isEpisode(item: JellyfinItem): Boolean = item.type.equals("Episode", ignoreCase = true)

    /** The URI a video is stored and browsed under: its original file's stream URL, without a token. */
    fun playUri(client: JellyfinClient, item: JellyfinItem): String =
        client.streamUrl(item.id, item.mediaSources.firstOrNull()?.id?.takeIf { it.isNotBlank() } ?: item.id)

    /** "2. Half Loop" for an episode, the name for anything else. */
    fun displayName(item: JellyfinItem): String {
        val name = item.name?.trim().orEmpty().ifEmpty { item.id }
        val number = item.indexNumber
        return if (isEpisode(item) && number != null) "$number. $name" else name
    }

    fun posterUrl(client: JellyfinClient, item: JellyfinItem, width: Int = MediaServerArt.POSTER_WIDTH): String? =
        item.imageTag("Primary")?.let { client.imageUrl(item.id, "Primary", it, width) }

    /** [item] (from [library]) as a scanned video; null for what can't play (missing episodes, folders, music). */
    fun toScanned(
        client: JellyfinClient,
        library: JellyfinItem,
        item: JellyfinItem,
        series: JellyfinItem?,
        season: JellyfinItem?,
        now: Long,
    ): ScannedVideo? {
        if (item.isMissing || item.isFolder || !isVideo(item)) return null
        val media = item.mediaSources.firstOrNull()
        val libraryName = MediaServerNames.segment(library.name, "Library")
        val folders = if (isEpisode(item)) {
            listOf(
                libraryName,
                MediaServerNames.segment(series?.name ?: item.seriesName, "Unknown series"),
                MediaServerNames.segment(item.seasonName, MediaServerNames.season(item.parentIndexNumber)),
            )
        } else {
            listOf(libraryName)
        }
        val videoStream = media?.video
        return ScannedVideo(
            uri = playUri(client, item),
            fileName = fileName(item),
            folder = folders.joinToString("/"),
            parentFolders = folders.asReversed(),
            sizeBytes = media?.size?.takeIf { it > 0 },
            durationMs = item.durationMs,
            width = videoStream?.width ?: item.width,
            height = videoStream?.height ?: item.height,
            dateAdded = JellyfinDates.epochMillis(item.dateCreated) ?: now,
            remoteId = item.id,
            serverMetadata = metadata(client, item, series, season),
        )
    }

    private fun fileName(item: JellyfinItem): String {
        val media = item.mediaSources.firstOrNull()
        return MediaServerNames.fileName(media?.path ?: item.path)
            ?: MediaServerNames.withExtension(item.name?.trim().orEmpty().ifEmpty { item.id }, media?.container ?: item.container)
    }

    /**
     * What the server knows: a movie's own details, or for an episode its series' details plus the
     * episode's. Other videos (home videos) get none: their names are parsed as usual.
     */
    private fun metadata(client: JellyfinClient, item: JellyfinItem, series: JellyfinItem?, season: JellyfinItem?): ServerMetadata? =
        when {
            item.type.equals("Movie", ignoreCase = true) -> ServerMetadata(
                type = ServerItemType.MOVIE,
                id = item.id,
                title = item.name.orEmpty(),
                originalTitle = item.originalTitle?.takeIf { it != item.name },
                year = item.productionYear,
                overview = item.overview,
                tagline = item.taglines.firstOrNull(),
                posterUrl = posterUrl(client, item),
                backdropUrl = item.backdropImageTags.firstOrNull()
                    ?.let { client.imageUrl(item.id, "Backdrop", it, MediaServerArt.BACKDROP_WIDTH, index = 0) },
                rating = item.communityRating,
                genres = item.genres,
                runtimeMin = MediaServerNames.minutes(item.durationMs),
                providerIds = item.providers,
            )
            isEpisode(item) -> episodeMetadata(client, item, series, season)
            else -> null
        }

    private fun episodeMetadata(client: JellyfinClient, item: JellyfinItem, series: JellyfinItem?, season: JellyfinItem?): ServerMetadata? {
        val seriesId = item.seriesId?.takeIf { it.isNotBlank() } ?: return null
        val posterTag = series?.imageTag("Primary") ?: item.seriesPrimaryImageTag?.takeIf { it.isNotBlank() }
        val backdrop = series?.backdropImageTags?.firstOrNull()
            ?.let { client.imageUrl(seriesId, "Backdrop", it, MediaServerArt.BACKDROP_WIDTH, index = 0) }
            ?: item.parentBackdropImageTags.firstOrNull()
                ?.let { client.imageUrl(item.parentBackdropItemId ?: seriesId, "Backdrop", it, MediaServerArt.BACKDROP_WIDTH, index = 0) }
        val still = item.imageTag("Primary")?.let { client.imageUrl(item.id, "Primary", it, MediaServerArt.STILL_WIDTH) }
            ?: item.imageTag("Thumb")?.let { client.imageUrl(item.id, "Thumb", it, MediaServerArt.STILL_WIDTH) }
        return ServerMetadata(
            type = ServerItemType.EPISODE,
            id = seriesId,
            title = series?.name ?: item.seriesName.orEmpty(),
            originalTitle = series?.let { show -> show.originalTitle?.takeIf { it != show.name } },
            year = series?.productionYear,
            overview = series?.overview,
            tagline = series?.taglines?.firstOrNull(),
            posterUrl = posterTag?.let { client.imageUrl(seriesId, "Primary", it, MediaServerArt.POSTER_WIDTH) },
            backdropUrl = backdrop,
            rating = series?.communityRating,
            genres = series?.genres.orEmpty(),
            runtimeMin = MediaServerNames.minutes(JellyfinClient.ticksToMs(series?.runTimeTicks)),
            providerIds = series?.providers.orEmpty(),
            season = item.parentIndexNumber,
            episode = item.indexNumber,
            episodeEnd = item.indexNumberEnd,
            episodeTitle = item.name,
            episodeOverview = item.overview,
            episodeStillUrl = still,
            episodeAirDate = JellyfinDates.day(item.premiereDate),
            seasonPosterUrl = season?.let { posterUrl(client, it) },
        )
    }
}
