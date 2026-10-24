package com.aakash.novafork.data

import android.content.Context
import android.os.Build
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What MediaStore lists (DeviceScanner.census): counts and folder names, never file names.
 * [error] says why it could not be read; the counts are 0 then.
 */
data class MediaStoreCensus(
    /** Videos MediaStore listed. */
    val total: Int = 0,
    /** Of those, the ones a device scan keeps. */
    val kept: Int = 0,
    /** The others by why a scan leaves them out ("hidden or system folder" → 3). */
    val skipped: Map<String, Int> = emptyMap(),
    /** Folder below the storage root ("DCIM/Camera", "" = the root) → videos, most first. */
    val folders: List<Pair<String, Int>> = emptyList(),
    /** Storage volume ("external_primary", an SD card's id) → videos. Empty before Android 10. */
    val volumes: Map<String, Int> = emptyMap(),
    val error: String? = null,
)

/** Everything a diagnostics report is made of; [LibraryDiagnostics.format] turns it into text. */
data class DiagnosticsInput(
    val createdAt: Long,
    val appVersion: String,
    /** "samsung SM-G990B". */
    val device: String,
    /** "14". */
    val androidRelease: String,
    val sdkInt: Int,
    /** Permission → state, as [StoragePermissions.describe] gives them. */
    val permissions: List<Pair<String, String>>,
    val videoAccess: VideoAccess,
    val scanDeviceStorage: Boolean,
    val census: MediaStoreCensus,
    /** Null when the library couldn't be read ([statsError] says why). */
    val stats: LibraryStats?,
    val statsError: String? = null,
    val scan: ScanState = ScanState(),
    /** Null when the music library wasn't asked (older callers, tests). */
    val music: MusicDiagnostics? = null,
)

/** The music side of the report: access, songs per source (by name) and the last music scan. */
data class MusicDiagnostics(
    /** READ_MEDIA_AUDIO (or the storage permission before Android 13), or All files access. */
    val hasAccess: Boolean,
    /** Source name and type → songs shown, hidden (not found by the last scan). */
    val sources: List<MusicSourceCount> = emptyList(),
    val lastScanAt: Long? = null,
    val scanning: Boolean = false,
    val error: String? = null,
)

data class MusicSourceCount(val name: String, val type: String, val shown: Int, val hidden: Int)

/**
 * Settings › About › Library diagnostics: a plain-text report a user can copy or share when the
 * library misses videos. It holds what the app sees (Android version, storage permissions, what
 * MediaStore lists by folder, each source's name, counts, last scan and error, the library's
 * make-up) and no secrets: no passwords, tokens or keys, and folder names but never file names.
 */
object LibraryDiagnostics {
    /** Folders listed one by one; the rest are summed up. */
    const val TOP_FOLDERS = 15

    /** Reads everything the report needs (off the main thread) and formats it. */
    suspend fun collect(
        context: Context,
        library: LibraryRepository,
        settings: Settings,
        appVersion: String,
        census: suspend () -> MediaStoreCensus,
        music: suspend () -> MusicDiagnostics? = { null },
    ): String = withContext(Dispatchers.IO) {
        var statsError: String? = null
        val stats = try {
            library.libraryStats()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            statsError = describe(e)
            null
        }
        val mediaStore = try {
            census()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaStoreCensus(error = describe(e))
        }
        val musicReport = try {
            music()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MusicDiagnostics(hasAccess = StoragePermissions.hasAudioAccess(context), error = describe(e))
        }
        format(
            DiagnosticsInput(
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion,
                device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                permissions = StoragePermissions.describe(context),
                videoAccess = StoragePermissions.videoAccess(context),
                scanDeviceStorage = settings.scanDeviceStorage,
                census = mediaStore,
                stats = stats,
                statsError = statsError,
                scan = library.scanState.value,
                music = musicReport,
            ),
        )
    }

    fun format(input: DiagnosticsInput, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US).withZone(zone)
        fun at(epochMs: Long): String = time.format(Instant.ofEpochMilli(epochMs))

        appendLine("Nova Fork library diagnostics")
        appendLine("Created: ${at(input.createdAt)}")
        appendLine("App: ${input.appVersion.ifBlank { "unknown" }}")
        appendLine("Device: ${input.device.ifBlank { "unknown" }} · Android ${input.androidRelease} (API ${input.sdkInt})")

        section("Storage access")
        input.permissions.forEach { (name, state) -> appendLine("$name: $state") }
        appendLine(
            "Device videos Nova can see: " + when (input.videoAccess) {
                VideoAccess.FULL -> "all"
                VideoAccess.PARTIAL -> "only the ones picked (limited access)"
                VideoAccess.NONE -> "none"
            },
        )
        appendLine("Scan device storage (setting): ${if (input.scanDeviceStorage) "on" else "off"}")
        appendLine(
            "Device storage is scanned: " + when {
                !input.scanDeviceStorage -> "no, the setting is off"
                input.videoAccess == VideoAccess.NONE -> "no, Nova has no access to videos"
                else -> "yes"
            },
        )

        section("MediaStore (videos Android lists)")
        val census = input.census
        val censusError = census.error
        if (censusError != null) {
            appendLine("Couldn't read MediaStore: $censusError")
        } else {
            appendLine("Videos: ${census.total}")
            appendLine("Kept by a scan: ${census.kept}")
            if (census.skipped.isNotEmpty()) {
                appendLine("Left out: " + census.skipped.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
            }
            if (census.volumes.isNotEmpty()) {
                appendLine("Volumes: " + census.volumes.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
            }
            if (census.folders.isNotEmpty()) {
                appendLine("Folders:")
                census.folders.take(TOP_FOLDERS).forEach { (folder, count) ->
                    appendLine("  ${folder.ifEmpty { "(storage root)" }}: $count")
                }
                val rest = census.folders.drop(TOP_FOLDERS)
                if (rest.isNotEmpty()) appendLine("  …${rest.size} more folders: ${rest.sumOf { it.second }} videos")
            }
        }

        section("Sources")
        val stats = input.stats
        if (stats == null) {
            appendLine("Couldn't read the library: ${input.statsError ?: "unknown error"}")
        } else {
            if (stats.sources.isEmpty()) appendLine("None")
            stats.sources.forEach { entry ->
                val source = entry.source
                appendLine("${source.name} (${source.type.name}) · ${if (source.enabled) "on" else "off"}")
                appendLine("  Videos shown: ${entry.present} · hidden (not found by the last scan): ${entry.missing}")
                appendLine("  Last scan: ${source.lastScanAt?.let(::at) ?: "never finished"}")
                source.lastError?.takeIf { it.isNotBlank() }?.let { appendLine("  Last error: $it") }
                val run = entry.lastScan
                if (run != null) {
                    val took = seconds(run.durationMs)
                    val found = run.found
                    appendLine(
                        "  Since the app started: " + if (found == null) {
                            "failed at ${at(run.startedAt)} after $took: ${run.error.orEmpty()}"
                        } else {
                            "scanned at ${at(run.startedAt)} in $took, found $found, new ${run.added}, " +
                                "already in another source ${run.ownedElsewhere}, hidden ${run.hidden}"
                        },
                    )
                }
            }
        }

        section("Library (videos shown)")
        if (stats != null) {
            val byKind = stats.kinds.groupBy { it.kind }
            for (kind in VideoKind.entries) {
                val rows = byKind[kind].orEmpty()
                val states = rows.filter { it.count > 0 }
                    .sortedBy { it.matchState.ordinal }
                    .joinToString(", ") { "${matchLabel(it.matchState)} ${it.count}" }
                appendLine("${kindLabel(kind)}: ${rows.sumOf { it.count }}" + if (states.isEmpty()) "" else " ($states)")
            }
            appendLine("Total: ${stats.kinds.sumOf { it.count }}")
        } else {
            appendLine("Unknown")
        }

        section("Scanning")
        val scanning = stats?.scanningSource
        val since = stats?.scanningSince
        appendLine(
            "Now: " + when {
                scanning != null && since != null -> "$scanning, since ${at(since)}"
                input.scan.running -> "starting"
                else -> "nothing"
            },
        )
        if (input.scan.enriching) appendLine("Fetching posters: ${input.scan.enrichDone} / ${input.scan.enrichTotal}")
        input.scan.lastError?.takeIf { it.isNotBlank() }?.let { appendLine("Last error: $it") }

        val music = input.music
        if (music != null) {
            section("Music")
            appendLine("Music Nova can see on the device: ${if (music.hasAccess) "all" else "none (no access to music)"}")
            if (music.sources.isEmpty()) appendLine("Songs: none")
            music.sources.forEach { entry ->
                appendLine("${entry.name} (${entry.type}): ${entry.shown} songs shown · hidden: ${entry.hidden}")
            }
            appendLine(
                "Last music scan: " + when {
                    music.scanning -> "running now"
                    music.lastScanAt != null -> at(music.lastScanAt)
                    else -> "not since the app started"
                },
            )
            music.error?.takeIf { it.isNotBlank() }?.let { appendLine("Last music error: $it") }
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("== $title ==")
    }

    private fun kindLabel(kind: VideoKind): String = when (kind) {
        VideoKind.MOVIE -> "Movies"
        VideoKind.EPISODE -> "Episodes"
        VideoKind.VIDEO -> "Other videos"
    }

    private fun matchLabel(state: MatchState): String = when (state) {
        MatchState.PENDING -> "waiting for a match"
        MatchState.MATCHED -> "matched"
        MatchState.UNMATCHED -> "no match"
        MatchState.MANUAL -> "identified by hand"
    }

    /** "2.3 s". */
    private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000.0)

    private fun describe(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
}
