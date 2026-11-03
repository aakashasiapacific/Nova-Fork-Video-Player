package com.aakash.novafork.ui.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId

/*
 * Preferences' Storage and Watch Stats groups: pure sums over what the database already holds
 * (file sizes, watched flags, play counts, last-played times), unit-tested. View only.
 */

// Storage

/** One video or song file: where it lives and how big it is. A file whose size is unknown has none. */
class StoredFile(val sourceId: Long, val folder: String, val sizeBytes: Long?)

/** The files of one folder of one source. */
@Immutable
data class FolderSpace(
    val sourceName: String,
    /** '/'-separated, root to leaf; "" for the source's root. */
    val folder: String,
    val bytes: Long,
    val files: Int,
)

/** The library's files by folder, largest first. */
@Immutable
data class SpaceUsage(
    val totalBytes: Long = 0,
    val totalFiles: Int = 0,
    /** The largest folders, at most the limit [spaceByFolder] was given. */
    val folders: List<FolderSpace> = emptyList(),
    /** Folders smaller than the last of [folders], left out of the list but counted in the totals. */
    val moreFolders: Int = 0,
) {
    val isEmpty: Boolean get() = totalFiles == 0
}

/**
 * Groups [files] by source and folder, largest first, with the total at the top. Files whose size
 * isn't stored (a stream, a server's file) are left out: nothing honest can be said of their space.
 * Only the [limit] largest folders are listed.
 */
fun spaceByFolder(files: List<StoredFile>, sourceNames: Map<Long, String>, limit: Int = 30): SpaceUsage {
    val sized = files.filter { (it.sizeBytes ?: 0L) > 0L }
    if (sized.isEmpty()) return SpaceUsage()
    val all = sized
        .groupBy { it.sourceId to it.folder.trim('/') }
        .map { (place, group) ->
            FolderSpace(
                sourceName = sourceNames[place.first].orEmpty(),
                folder = place.second,
                bytes = group.sumOf { it.sizeBytes ?: 0L },
                files = group.size,
            )
        }
        .sortedWith(compareByDescending<FolderSpace> { it.bytes }.thenBy { it.sourceName }.thenBy { it.folder })
    return SpaceUsage(
        totalBytes = all.sumOf { it.bytes },
        totalFiles = all.sumOf { it.files },
        folders = all.take(limit),
        moreFolders = (all.size - limit).coerceAtLeast(0),
    )
}

// Watch stats

/** What the database keeps of one video's watching. */
class PlayRecord(
    val isMovie: Boolean,
    val isEpisode: Boolean,
    /** Episodes: the show they belong to, and its name. */
    val showKey: String?,
    val showTitle: String?,
    val durationMs: Long?,
    val positionMs: Long,
    val watched: Boolean,
    /** Finished viewings (the player counts one each time a video plays through). */
    val playCount: Int,
    val lastPlayedAt: Long?,
)

@Immutable
data class WatchedShow(val title: String, val episodes: Int)

@Immutable
data class WatchStats(
    /** Time watched in the calendar month of "now", as each video's latest viewing (see [watchStats]). */
    val monthMs: Long = 0,
    /** Every finished viewing plus the part of the one under way. */
    val allTimeMs: Long = 0,
    val filmsFinishedThisMonth: Int = 0,
    /** The shows with most episodes watched, at most three. */
    val topShows: List<WatchedShow> = emptyList(),
) {
    val isEmpty: Boolean get() = allTimeMs == 0L && filmsFinishedThisMonth == 0 && topShows.isEmpty()
}

/**
 * Watching, as far as it is stored. The database holds one "last played" time per video and no log
 * of plays, so:
 * - all time is each video's finished viewings (play count times its length) plus how far it was
 *   watched since; a video only marked watched by hand has no play and adds nothing;
 * - this month counts the videos last played this month, each once: the viewing under way if there
 *   is one, else the last one it was played through. Earlier viewings in the month are not known;
 * - a film is finished this month when it was last played to its end this month;
 * - the top shows are those with most watched episodes.
 */
fun watchStats(
    records: List<PlayRecord>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): WatchStats {
    val monthStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1)
    val from = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = monthStart.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    var allTime = 0L
    var month = 0L
    var films = 0
    val episodes = HashMap<String, Pair<String, Int>>()
    for (record in records) {
        val length = (record.durationMs ?: 0L).coerceAtLeast(0L)
        val partial = if (length > 0L) record.positionMs.coerceIn(0L, length) else 0L
        allTime += length * record.playCount.coerceAtLeast(0) + partial
        val playedAt = record.lastPlayedAt
        val thisMonth = playedAt != null && playedAt >= from && playedAt < until
        // Watched and back at the start: its last viewing played through.
        val finished = record.watched && record.positionMs == 0L && playedAt != null
        if (thisMonth) {
            month += if (partial > 0L) partial else if (finished) length else 0L
            if (record.isMovie && finished) films++
        }
        val key = record.showKey
        if (record.isEpisode && record.watched && key != null) {
            val seen = episodes[key]
            episodes[key] = (seen?.first ?: record.showTitle.orEmpty().ifBlank { key }) to ((seen?.second ?: 0) + 1)
        }
    }
    val top = episodes.values
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        .take(TOP_SHOWS)
        .map { WatchedShow(it.first, it.second) }
    return WatchStats(monthMs = month, allTimeMs = allTime, filmsFinishedThisMonth = films, topShows = top)
}

private const val TOP_SHOWS = 3

/** "42 min", "7.5 hours", "120 hours": a watched time at a glance. */
fun watchTimeLabel(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0L) + 30_000L) / 60_000L
    if (minutes < 60L) return "$minutes min"
    val tenths = (minutes * 10 + 30) / 60
    return if (tenths >= 1_000L) "${tenths / 10} hours" else "${tenths / 10}.${tenths % 10} hours"
}
