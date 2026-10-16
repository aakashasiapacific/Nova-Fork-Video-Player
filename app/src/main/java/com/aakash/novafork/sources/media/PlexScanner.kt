package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexDirectory
import com.aakash.novafork.core.plex.PlexMetadata
import com.aakash.novafork.core.plex.PlexServerClient
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

/**
 * Lists every movie and episode of a Plex server's movie and TV sections (the chosen ones, or all
 * of them) as the server describes them: part URLs to play, "<section>/<show>/<season>" folders,
 * and [ServerMetadata] so TMDB is never asked about them. "Other videos" sections (personal media,
 * no online agent) are listed without metadata: their file names are parsed as usual.
 */
internal class PlexScanner(private val connector: PlexConnector) : SourceScanner {
    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> =
        withContext(Dispatchers.IO) {
            connector.withServer(source) { client, token ->
                connector.rememberServer(source, token, client.identity().machineIdentifier)
                val now = System.currentTimeMillis()
                val found = ArrayList<ScannedVideo>()
                val seen = HashSet<String>()
                for (section in PlexItems.chosenSections(client.sections(), source)) {
                    currentCoroutineContext().ensureActive()
                    val before = found.size
                    val report: (Int) -> Unit = { onProgress(before + it) }
                    val videos = when (section.type) {
                        PlexDirectory.TYPE_MOVIE -> movies(client, section, now, report)
                        PlexDirectory.TYPE_SHOW -> episodes(client, section, now, report)
                        else -> emptyList()
                    }
                    for (video in videos) if (seen.add(video.uri)) found += video
                    onProgress(found.size)
                }
                found
            }
        }

    private suspend fun movies(client: PlexServerClient, section: PlexDirectory, now: Long, onPage: (Int) -> Unit): List<ScannedVideo> =
        client.sectionItems(section.key, PlexServerClient.TYPE_MOVIE, onPage = onPage)
            .mapNotNull { PlexItems.movie(client, section, it, now) }

    private suspend fun episodes(client: PlexServerClient, section: PlexDirectory, now: Long, onPage: (Int) -> Unit): List<ScannedVideo> {
        val shows = if (section.isPersonalMedia) {
            emptyMap()
        } else {
            client.sectionItems(section.key, PlexServerClient.TYPE_SHOW).associateBy { it.ratingKey }
        }
        val episodes = client.sectionItems(section.key, PlexServerClient.TYPE_EPISODE, onPage = onPage)
        // A file holding two episodes (S01E01E02) is listed under each: it plays once, as the first,
        // and ends with the last.
        val lastEpisode = HashMap<String, Int>()
        for (episode in episodes) {
            val partKey = episode.part?.key ?: continue
            val number = episode.index ?: continue
            lastEpisode[partKey] = maxOf(number, lastEpisode[partKey] ?: number)
        }
        return episodes
            .sortedWith(compareBy<PlexMetadata>({ it.parentIndex ?: Int.MAX_VALUE }, { it.index ?: Int.MAX_VALUE }))
            .mapNotNull { episode ->
                val show = episode.grandparentRatingKey?.let { shows[it] }
                PlexItems.episode(client, section, episode, show, episode.part?.key?.let { lastEpisode[it] }, now)
            }
    }
}

/** How Plex items become library videos and Browse entries. */
internal object PlexItems {
    /** The sections [source] scans: the ones it names, else every movie and TV section. */
    fun chosenSections(sections: List<PlexDirectory>, source: SourceEntity): List<PlexDirectory> {
        val wanted = NetworkSources.libraryIds(source)
        val video = sections.filter { it.isVideoSection }
        return if (wanted.isEmpty()) video else video.filter { it.key in wanted }
    }

    /** The URI an item is stored and browsed under: its file's URL, without a token. */
    fun playUri(client: PlexServerClient, item: PlexMetadata): String? = item.part?.key?.let { client.partUrl(it) }

    /** "2. Half Loop" for an episode, the title for anything else. */
    fun displayName(item: PlexMetadata): String {
        val title = item.title?.trim().orEmpty().ifEmpty { item.ratingKey }
        val number = item.index
        return if (item.type == "episode" && number != null) "$number. $title" else title
    }

    fun movie(client: PlexServerClient, section: PlexDirectory, item: PlexMetadata, now: Long): ScannedVideo? {
        val sectionName = MediaServerNames.segment(section.title, "Movies")
        val metadata = if (section.isPersonalMedia || item.ratingKey.isBlank()) {
            null
        } else {
            ServerMetadata(
                type = ServerItemType.MOVIE,
                id = item.ratingKey,
                title = item.title.orEmpty(),
                originalTitle = item.originalTitle?.takeIf { it != item.title },
                year = item.year,
                overview = item.summary,
                tagline = item.tagline,
                posterUrl = client.imageUrl(item.thumb, MediaServerArt.POSTER_WIDTH, MediaServerArt.POSTER_HEIGHT),
                backdropUrl = client.imageUrl(item.art, MediaServerArt.BACKDROP_WIDTH, MediaServerArt.BACKDROP_HEIGHT),
                rating = item.audienceRating ?: item.rating,
                genres = item.genres.mapNotNull { it.tag },
                runtimeMin = MediaServerNames.minutes(item.duration),
                providerIds = item.providers,
            )
        }
        return scanned(client, item, listOf(sectionName), metadata, now)
    }

    fun episode(
        client: PlexServerClient,
        section: PlexDirectory,
        item: PlexMetadata,
        show: PlexMetadata?,
        lastEpisode: Int?,
        now: Long,
    ): ScannedVideo? {
        val folders = listOf(
            MediaServerNames.segment(section.title, "TV Shows"),
            MediaServerNames.segment(show?.title ?: item.grandparentTitle, "Unknown show"),
            MediaServerNames.segment(item.parentTitle, MediaServerNames.season(item.parentIndex)),
        )
        val showKey = item.grandparentRatingKey?.takeIf { it.isNotBlank() }
        val metadata = if (section.isPersonalMedia || showKey == null) {
            null
        } else {
            ServerMetadata(
                type = ServerItemType.EPISODE,
                id = showKey,
                title = show?.title ?: item.grandparentTitle.orEmpty(),
                originalTitle = show?.let { it.originalTitle?.takeIf { original -> original != it.title } },
                year = show?.year,
                overview = show?.summary,
                tagline = show?.tagline,
                posterUrl = client.imageUrl(show?.thumb ?: item.grandparentThumb, MediaServerArt.POSTER_WIDTH, MediaServerArt.POSTER_HEIGHT),
                backdropUrl = client.imageUrl(show?.art ?: item.grandparentArt, MediaServerArt.BACKDROP_WIDTH, MediaServerArt.BACKDROP_HEIGHT),
                rating = show?.let { it.audienceRating ?: it.rating },
                genres = show?.genres.orEmpty().mapNotNull { it.tag },
                runtimeMin = MediaServerNames.minutes(show?.duration),
                providerIds = show?.providers.orEmpty(),
                season = item.parentIndex,
                episode = item.index,
                episodeEnd = lastEpisode,
                episodeTitle = item.title,
                episodeOverview = item.summary,
                episodeStillUrl = client.imageUrl(item.thumb, MediaServerArt.STILL_WIDTH, MediaServerArt.STILL_HEIGHT),
                episodeAirDate = item.originallyAvailableAt?.trim()?.take(AIR_DATE_LENGTH),
                seasonPosterUrl = client.imageUrl(item.parentThumb, MediaServerArt.POSTER_WIDTH, MediaServerArt.POSTER_HEIGHT),
            )
        }
        return scanned(client, item, folders, metadata, now)
    }

    /** Null for an item without a file (nothing to play). */
    private fun scanned(
        client: PlexServerClient,
        item: PlexMetadata,
        folders: List<String>,
        metadata: ServerMetadata?,
        now: Long,
    ): ScannedVideo? {
        val media = item.playableMedia ?: return null
        val part = media.part ?: return null
        val uri = playUri(client, item) ?: return null
        val title = item.title?.trim().orEmpty().ifEmpty { item.ratingKey }
        return ScannedVideo(
            uri = uri,
            fileName = MediaServerNames.fileName(part.file) ?: MediaServerNames.withExtension(title, part.container ?: media.container),
            folder = folders.joinToString("/"),
            parentFolders = folders.asReversed(),
            sizeBytes = part.size?.takeIf { it > 0 },
            durationMs = (media.duration ?: part.duration ?: item.duration)?.takeIf { it > 0 },
            width = media.width?.takeIf { it > 0 },
            height = media.height?.takeIf { it > 0 },
            dateAdded = item.addedAt?.takeIf { it > 0 }?.times(MILLIS_PER_SECOND) ?: now,
            dateModified = item.updatedAt?.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
            remoteId = item.ratingKey.takeIf { it.isNotBlank() },
            serverMetadata = metadata,
        )
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val AIR_DATE_LENGTH = 10
}
