package com.aakash.novafork.ui

import com.aakash.novafork.core.format.Formats
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.data.db.MetadataEntity
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.HomeUi
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.ShowCardUi
import com.aakash.novafork.ui.model.VideoCardUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.util.Locale

/** A present video with the labels and artwork every screen uses. */
class IndexedVideo(
    val video: VideoEntity,
    val meta: MetadataEntity?,
    val source: SourceEntity?,
    val kind: MediaKind,
    /** Episodes only: the show they are grouped under. */
    val showKey: String?,
    /** Episodes only. */
    val showTitle: String?,
    val title: String,
    /** Folder below the source root ("" = the root), as browsed in Browse. */
    val relativeFolder: String,
    val card: VideoCardUi,
    /** Title for alphabetical sorting (show title for episodes). */
    val sortKey: String,
) {
    val id: Long get() = video.id
    val inProgress: Boolean get() = video.positionMs > 0 && !video.watched
}

class IndexedShow(
    val key: String,
    /** In watching order: by season (specials last), then episode. Never empty. */
    val episodes: List<IndexedVideo>,
    val meta: MetadataEntity?,
    val title: String,
    /** TMDB season poster paths by season number. */
    val seasonPosters: Map<Int, String>,
    val card: ShowCardUi,
    /** Newest dateAdded among the episodes. */
    val latestAdded: Long,
    val sortKey: String,
)

/**
 * The library joined with its sources, built once per database change on a background thread.
 * Every screen model is derived from it by [UiMappers].
 */
class LibraryIndex(
    /** Newest first. */
    val videos: List<IndexedVideo>,
    /** By title. */
    val shows: List<IndexedShow>,
    val sources: List<SourceEntity>,
    val preferLocalArtwork: Boolean,
) {
    val videosById: Map<Long, IndexedVideo> = videos.associateBy { it.id }
    val showsByKey: Map<String, IndexedShow> = shows.associateBy { it.key }
    val sourcesById: Map<Long, SourceEntity> = sources.associateBy { it.id }
    val videoCountBySource: Map<Long, Int> = videos.groupingBy { it.video.sourceId }.eachCount()

    /** Movies from device, folders and shares (matched movie streams stay with the streams). Newest first. */
    val movies: List<IndexedVideo> = videos.filter { it.kind == MediaKind.MOVIE }

    /** Home videos, unidentified files and streams. Newest first. */
    val otherVideos: List<IndexedVideo> = videos.filter { it.kind == MediaKind.VIDEO || it.kind == MediaKind.STREAM }

    /** Home only changes with the library, so it is built once per index rather than per UI event. */
    val home: HomeUi by lazy { UiMappers.home(this) }

    companion object {
        fun build(rows: List<VideoWithMeta>, sources: List<SourceEntity>, preferLocalArtwork: Boolean): LibraryIndex {
            val sourcesById = sources.associateBy { it.id }
            val smbRoots = sources
                .filter { it.type == SourceType.SMB }
                .associate { it.id to SmbUris.parse(it.location)?.path.orEmpty() }
            val seasonPosterCache = HashMap<String, Map<Int, String>>()
            val seasonPosters = { meta: MetadataEntity ->
                seasonPosterCache.getOrPut(meta.key) { parseSeasonPosters(meta.seasonPostersJson) }
            }
            val videos = rows
                .sortedWith(compareByDescending<VideoWithMeta> { it.video.dateAdded }.thenByDescending { it.video.id })
                .map { row ->
                    val source = sourcesById[row.video.sourceId]
                    indexVideo(row, source, smbRoots[row.video.sourceId].orEmpty(), preferLocalArtwork, seasonPosters)
                }
            val episodesByShow = LinkedHashMap<String, MutableList<IndexedVideo>>()
            for (video in videos) {
                val key = video.showKey ?: continue
                episodesByShow.getOrPut(key) { ArrayList() }.add(video)
            }
            val shows = episodesByShow
                .map { (key, episodes) -> buildShow(key, episodes, preferLocalArtwork, seasonPosters) }
                .sortedWith(compareBy(NaturalOrder) { it.sortKey })
            return LibraryIndex(videos, shows, sources, preferLocalArtwork)
        }
    }
}

// Building

private fun indexVideo(
    row: VideoWithMeta,
    source: SourceEntity?,
    smbRoot: String,
    preferLocal: Boolean,
    seasonPosters: (MetadataEntity) -> Map<Int, String>,
): IndexedVideo {
    val video = row.video
    val meta = row.meta
    val kind = mediaKindOf(video, source)
    val baseName = VideoFiles.baseName(video.fileName).ifBlank { video.fileName }
    val showKey = if (kind == MediaKind.EPISODE) video.showKey ?: LibraryKeys.showKey(null, video.parsedTitle) else null
    val showTitle = if (kind == MediaKind.EPISODE) meta?.title.nonBlank() ?: video.parsedTitle.ifBlank { baseName } else null
    val title = when (kind) {
        MediaKind.MOVIE -> meta?.title.nonBlank() ?: video.parsedTitle.ifBlank { baseName }
        MediaKind.EPISODE -> video.episodeTitle.nonBlank() ?: video.episode?.let { "Episode $it" } ?: baseName
        MediaKind.VIDEO -> baseName
        MediaKind.STREAM -> meta?.title.nonBlank() ?: source?.name.nonBlank() ?: video.parsedTitle.ifBlank { baseName }
    }
    val artwork = when (kind) {
        MediaKind.EPISODE -> episodeArtwork(
            video = video,
            meta = meta,
            seasonPosters = meta?.let(seasonPosters).orEmpty(),
            seed = showTitle ?: title,
            preferLocal = preferLocal,
        )
        MediaKind.MOVIE, MediaKind.STREAM -> movieArtwork(video, meta, seed = title, preferLocal = preferLocal)
        MediaKind.VIDEO -> Artwork(
            posterUrl = video.localPoster,
            backdropUrl = video.localBackdrop,
            stillUrl = video.localThumb,
            frameSource = frameSourceOf(video.uri),
            seed = title,
        )
    }
    val year = when (kind) {
        MediaKind.EPISODE -> yearOf(video.episodeAirDate)
        MediaKind.VIDEO -> null
        MediaKind.MOVIE, MediaKind.STREAM -> meta?.year ?: video.parsedYear
    }
    val subtitle = when (kind) {
        MediaKind.MOVIE -> joinMeta(year?.toString(), runtimeLabel(video, meta))
        MediaKind.EPISODE -> episodeLabel(video.season, video.episode, video.episodeEnd)
        MediaKind.VIDEO -> joinMeta(runtimeLabel(video, null), video.folder.trim('/').substringAfterLast('/'))
        MediaKind.STREAM -> "Stream"
    }
    val card = VideoCardUi(
        id = video.id,
        title = title,
        subtitle = subtitle,
        artwork = artwork,
        kind = kind,
        year = year,
        // An episode would otherwise show its show's score as its own.
        rating = if (kind == MediaKind.EPISODE) null else ratingOf(meta),
        durationMs = video.durationMs?.takeIf { it > 0 },
        progress = progressOf(video),
        watched = video.watched,
        showTitle = showTitle,
        season = video.season,
        episode = video.episode,
        sourceLabel = source?.let(::sourceLabel),
    )
    return IndexedVideo(
        video = video,
        meta = meta,
        source = source,
        kind = kind,
        showKey = showKey,
        showTitle = showTitle,
        title = title,
        relativeFolder = if (source == null) video.folder.trim('/') else relativeFolder(source.type, smbRoot, video.folder),
        card = card,
        sortKey = sortKeyOf(showTitle ?: title),
    )
}

private fun buildShow(
    key: String,
    episodes: List<IndexedVideo>,
    preferLocal: Boolean,
    seasonPosters: (MetadataEntity) -> Map<Int, String>,
): IndexedShow {
    val ordered = episodes.sortedWith(EPISODE_ORDER)
    val first = ordered.first()
    val meta = ordered.firstNotNullOfOrNull { it.meta }
    val title = meta?.title.nonBlank() ?: first.showTitle ?: first.title
    val artwork = Artwork(
        posterUrl = pick(
            TmdbClient.posterUrl(meta?.posterPath),
            ordered.firstNotNullOfOrNull { it.video.localPoster },
            preferLocal,
        ),
        backdropUrl = pick(
            TmdbClient.backdropUrl(meta?.backdropPath),
            ordered.firstNotNullOfOrNull { it.video.localBackdrop },
            preferLocal,
        ),
        frameSource = first.card.artwork.frameSource,
        seed = title,
    )
    // Two copies of one episode (720p and 1080p) count once.
    val distinct = ordered.distinctBy { episodeIdentity(it) }
    val card = ShowCardUi(
        key = key,
        title = title,
        artwork = artwork,
        year = meta?.year ?: ordered.firstNotNullOfOrNull { it.video.parsedYear },
        rating = ratingOf(meta),
        seasonCount = distinct.map { displaySeason(it.video.season) }.distinct().size,
        episodeCount = distinct.size,
        unwatchedCount = distinct.count { !it.video.watched },
    )
    return IndexedShow(
        key = key,
        episodes = ordered,
        meta = meta,
        title = title,
        seasonPosters = meta?.let(seasonPosters).orEmpty(),
        card = card,
        latestAdded = ordered.maxOf { it.video.dateAdded },
        sortKey = sortKeyOf(title),
    )
}

private fun episodeIdentity(video: IndexedVideo): String {
    val episode = video.video.episode ?: return "file:${video.id}"
    return "${displaySeason(video.video.season)}x$episode"
}

// Artwork

/** TMDB first, local art as the fallback, or the other way round when local art is preferred. */
internal fun pick(tmdb: String?, local: String?, preferLocal: Boolean): String? =
    if (preferLocal) local ?: tmdb else tmdb ?: local

/** Only local files can be decoded for a frame; network files would be downloaded for it. */
internal fun frameSourceOf(uri: String): String? =
    uri.takeIf { it.startsWith("content://", ignoreCase = true) || it.startsWith("file://", ignoreCase = true) }

private fun movieArtwork(video: VideoEntity, meta: MetadataEntity?, seed: String, preferLocal: Boolean): Artwork {
    val backdrop = pick(TmdbClient.backdropUrl(meta?.backdropPath), video.localBackdrop, preferLocal)
    return Artwork(
        posterUrl = pick(TmdbClient.posterUrl(meta?.posterPath), video.localPoster, preferLocal),
        backdropUrl = backdrop,
        // A movie's "<name>.jpg" is often a poster, so it is only the landscape fallback.
        stillUrl = video.localThumb.takeIf { backdrop == null },
        frameSource = frameSourceOf(video.uri),
        seed = seed,
    )
}

private fun episodeArtwork(
    video: VideoEntity,
    meta: MetadataEntity?,
    seasonPosters: Map<Int, String>,
    seed: String,
    preferLocal: Boolean,
): Artwork {
    val seasonPoster = video.season?.let { seasonPosters[it] }?.let { TmdbClient.posterUrl(it) }
    return Artwork(
        posterUrl = pick(seasonPoster ?: TmdbClient.posterUrl(meta?.posterPath), video.localPoster, preferLocal),
        backdropUrl = pick(TmdbClient.backdropUrl(meta?.backdropPath), video.localBackdrop, preferLocal),
        stillUrl = pick(TmdbClient.stillUrl(video.episodeStillPath), video.localThumb, preferLocal),
        frameSource = frameSourceOf(video.uri),
        seed = seed,
    )
}

/** {"1":"/a.jpg","2":"/b.jpg"} → {1="/a.jpg", 2="/b.jpg"}; anything malformed is skipped. */
internal fun parseSeasonPosters(json: String?): Map<Int, String> {
    if (json.isNullOrBlank()) return emptyMap()
    val root = try {
        Json.parseToJsonElement(json)
    } catch (e: IllegalArgumentException) {
        return emptyMap()
    }
    val seasons = root as? JsonObject ?: return emptyMap()
    val posters = HashMap<Int, String>()
    for ((name, value) in seasons) {
        val season = name.trim().toIntOrNull() ?: continue
        val path = (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        posters[season] = path
    }
    return posters
}

// Labels

internal fun mediaKindOf(video: VideoEntity, source: SourceEntity?): MediaKind = when {
    source?.type == SourceType.STREAM -> MediaKind.STREAM
    else -> when (video.kind) {
        VideoKind.MOVIE -> MediaKind.MOVIE
        VideoKind.EPISODE -> MediaKind.EPISODE
        VideoKind.VIDEO -> MediaKind.VIDEO
    }
}

internal fun sourceLabel(source: SourceEntity): String = when (source.type) {
    SourceType.DEVICE -> "Device"
    SourceType.FOLDER, SourceType.SMB, SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP,
    SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
    -> source.name
    SourceType.STREAM -> "Stream"
}

/** "S1 · E3", "S1 · E3–E4", "E12" (absolute numbering), null without an episode number. */
internal fun episodeLabel(season: Int?, episode: Int?, episodeEnd: Int? = null): String? {
    if (episode == null) return season?.let { "S$it" }
    val range = if (episodeEnd != null && episodeEnd > episode) "E$episode–E$episodeEnd" else "E$episode"
    return if (season != null) "S$season · $range" else range
}

/** "2h 35m" from TMDB's runtime, else from the file's duration; "0:42" for short clips. */
internal fun runtimeLabel(video: VideoEntity, meta: MetadataEntity?): String? {
    meta?.runtimeMin?.takeIf { it > 0 }?.let { return Formats.runtime(it) }
    val duration = video.durationMs?.takeIf { it > 0 } ?: return null
    return if (duration < 60_000) Formats.clock(duration) else Formats.runtime(((duration + 30_000) / 60_000).toInt())
}

/** "3840×2160 · 4K"; scope ratios (1920×800) are classed by width. */
internal fun resolutionLabel(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val label = when {
        width >= 3200 || height >= 2000 -> "4K"
        width >= 1800 || height >= 1000 -> "HD 1080p"
        width >= 1200 || height >= 700 -> "HD 720p"
        else -> "SD"
    }
    return "$width×$height · $label"
}

internal fun progressOf(video: VideoEntity): Float? {
    val duration = video.durationMs ?: return null
    if (video.watched || video.positionMs <= 0 || duration <= 0) return null
    return (video.positionMs.toDouble() / duration).toFloat().coerceIn(0f, 1f)
}

internal fun ratingOf(meta: MetadataEntity?): Float? = meta?.rating?.toFloat()?.takeIf { it > 0f }

internal fun genresOf(meta: MetadataEntity?): List<String> =
    meta?.genres.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

internal fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()

/** Season used for grouping: absolute-numbered episodes (no season yet) sit in season 1. */
internal fun displaySeason(season: Int?): Int = season ?: 1

/** Joins non-blank parts with " · "; null when nothing is left. */
internal fun joinMeta(vararg parts: String?): String? =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ").ifEmpty { null }

internal fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

private val LEADING_ARTICLES = listOf("the ", "a ", "an ")

/** Lower-case title without a leading article, so "The Matrix" sorts under M. */
internal fun sortKeyOf(title: String): String {
    val lower = title.trim().lowercase(Locale.ROOT)
    for (article in LEADING_ARTICLES) {
        if (lower.startsWith(article) && lower.length > article.length) return lower.substring(article.length).trimStart()
    }
    return lower
}

/** Episodes in watching order: season (specials last), episode, then file name. */
internal val EPISODE_ORDER: Comparator<IndexedVideo> =
    compareBy<IndexedVideo>(
        { seasonOrder(it.video.season) },
        { it.video.episode ?: Int.MAX_VALUE },
    ).thenBy(NaturalOrder) { it.video.fileName }

private fun seasonOrder(season: Int?): Int = if (season == 0) Int.MAX_VALUE else displaySeason(season)

/** Case-insensitive order that compares digit runs as numbers: "Part 2" before "Part 10". */
internal object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
                val startA = i
                while (i < a.length && a[i].isAsciiDigit()) i++
                val startB = j
                while (j < b.length && b[j].isAsciiDigit()) j++
                val numberA = a.substring(startA, i).trimStart('0')
                val numberB = b.substring(startB, j).trimStart('0')
                if (numberA.length != numberB.length) return numberA.length - numberB.length
                val byValue = numberA.compareTo(numberB)
                if (byValue != 0) return byValue
            } else {
                val byChar = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

// Folders

/**
 * The part of a video's display folder below its source root, as Browse shows it:
 * device folders are relative to the storage root already, a picked folder's paths start with
 * the folder's own name, and SMB paths start inside the share (above the source's folder).
 */
internal fun relativeFolder(type: SourceType, smbRoot: String, folder: String): String {
    val clean = folder.trim('/')
    return when (type) {
        SourceType.DEVICE -> clean
        SourceType.FOLDER -> if ('/' in clean) clean.substringAfter('/') else ""
        SourceType.SMB -> {
            val root = smbRoot.trim('/')
            when {
                root.isEmpty() -> clean
                clean.equals(root, ignoreCase = true) -> ""
                clean.startsWith("$root/", ignoreCase = true) -> clean.substring(root.length + 1)
                else -> clean
            }
        }
        SourceType.STREAM -> ""
        // Their scanners give folders below the source's location already.
        SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.UPNP, SourceType.WEBDAV, SourceType.JELLYFIN,
        SourceType.EMBY, SourceType.PLEX,
        -> clean
    }
}

/**
 * Readable location of a picked SAF folder from its tree URI:
 * ".../tree/primary%3AMovies%2FAction" → "Movies/Action", the storage root → "Internal storage".
 */
internal fun treeFolderLabel(treeUri: String): String? {
    val encoded = treeUri.substringAfter("/tree/", "").substringBefore('/')
    if (encoded.isEmpty()) return null
    val documentId = percentDecode(encoded)
    if (':' !in documentId) return documentId.ifBlank { null }
    val path = documentId.substringAfter(':').trim('/')
    return when {
        path.isNotEmpty() -> path
        documentId.startsWith("primary:") -> "Internal storage"
        else -> "SD card"
    }
}

/** Decodes %XX escapes as UTF-8; leaves '+' and malformed escapes alone (URI paths, not forms). */
internal fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val out = StringBuilder(text.length)
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%' && i + 2 < text.length) {
            val high = Character.digit(text[i + 1], 16)
            val low = Character.digit(text[i + 2], 16)
            if (high >= 0 && low >= 0) {
                bytes.write(high * 16 + low)
                i += 3
                continue
            }
        }
        if (bytes.size() > 0) {
            out.append(bytes.toString(Charsets.UTF_8.name()))
            bytes.reset()
        }
        out.append(c)
        i++
    }
    if (bytes.size() > 0) out.append(bytes.toString(Charsets.UTF_8.name()))
    return out.toString()
}
