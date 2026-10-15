package com.aakash.novafork.data

import android.app.Application
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/** Settings › About › Library diagnostics: what the report says, and what it never holds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryDiagnosticsTest {
    @Test
    fun reportShowsAccessMediaStoreSourcesAndTheLibrary() {
        val report = LibraryDiagnostics.format(input(), ZoneOffset.UTC)

        assertContains(report, "Created: 2026-10-15 09:30:00")
        assertContains(report, "App: 1.0.42")
        assertContains(report, "Device: samsung SM-G990B · Android 14 (API 34)")
        // Storage access
        assertContains(report, "Videos (READ_MEDIA_VIDEO): granted")
        assertContains(report, "All files access (MANAGE_EXTERNAL_STORAGE): allowed")
        assertContains(report, "Device videos Nova can see: all")
        assertContains(report, "Device storage is scanned: yes")
        // MediaStore, by folder
        assertContains(report, "Videos: 140")
        assertContains(report, "Kept by a scan: 138")
        assertContains(report, "Left out: hidden or system folder 2")
        assertContains(report, "Volumes: external_primary 130, 1234-5678 10")
        assertContains(report, "  DCIM/Camera: 80")
        assertContains(report, "  Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video: 30")
        assertContains(report, "  (storage root): 3")
        assertContains(report, "  …2 more folders: 2 videos")
        // Sources: counts, last scan, errors
        assertContains(report, "Device storage (DEVICE) · on")
        assertContains(report, "  Videos shown: 0 · hidden (not found by the last scan): 12")
        assertContains(report, "  Last scan: 2026-10-15 09:00:00")
        assertContains(
            report,
            "  Since the app started: scanned at 2026-10-15 09:00:00 in 2.3 s, found 0, new 0, " +
                "already in another source 0, hidden 12",
        )
        assertContains(report, "Movies (FOLDER) · on")
        assertContains(report, "  Last error: Permission revoked")
        assertContains(report, "  Since the app started: failed at 2026-10-15 09:01:00 after 0.5 s: Permission revoked")
        assertContains(report, "NAS (SMB) · off")
        assertContains(report, "  Last scan: never finished")
        // The library's make-up
        assertContains(report, "Movies: 3 (matched 2, no match 1)")
        assertContains(report, "Episodes: 0")
        assertContains(report, "Other videos: 5 (no match 5)")
        assertContains(report, "Total: 8")
        assertContains(report, "Now: nothing")
    }

    @Test
    fun reportNeverHoldsSecretsOrServerDetails() {
        val report = LibraryDiagnostics.format(input(), ZoneOffset.UTC)

        for (secret in listOf("s3cret", "alice", "tok-123", "user-9", "nas.local", "jf.local", "primary%3AMovies")) {
            assertFalse("The report holds \"$secret\"", report.contains(secret))
        }
    }

    @Test
    fun reportSaysWhyDeviceStorageIsNotScanned() {
        val noAccess = LibraryDiagnostics.format(
            input().copy(
                videoAccess = VideoAccess.NONE,
                census = MediaStoreCensus(error = "Allow access to videos to scan this device"),
            ),
            ZoneOffset.UTC,
        )
        assertContains(noAccess, "Device videos Nova can see: none")
        assertContains(noAccess, "Device storage is scanned: no, Nova has no access to videos")
        assertContains(noAccess, "Couldn't read MediaStore: Allow access to videos to scan this device")

        val limited = LibraryDiagnostics.format(input().copy(videoAccess = VideoAccess.PARTIAL), ZoneOffset.UTC)
        assertContains(limited, "Device videos Nova can see: only the ones picked (limited access)")

        val off = LibraryDiagnostics.format(input().copy(scanDeviceStorage = false), ZoneOffset.UTC)
        assertContains(off, "Scan device storage (setting): off")
        assertContains(off, "Device storage is scanned: no, the setting is off")

        val unreadable = LibraryDiagnostics.format(input().copy(stats = null, statsError = "database is locked"), ZoneOffset.UTC)
        assertContains(unreadable, "Couldn't read the library: database is locked")

        val scanning = LibraryDiagnostics.format(
            input().copy(
                stats = input().stats?.copy(scanningSource = "Device storage", scanningSince = T0),
                scan = ScanState(running = true, sourceName = "Device storage", enriching = true, enrichDone = 3, enrichTotal = 9),
            ),
            ZoneOffset.UTC,
        )
        assertContains(scanning, "Now: Device storage, since 2026-10-15 09:00:00")
        assertContains(scanning, "Fetching posters: 3 / 9")
    }

    private fun assertContains(report: String, line: String) {
        assertTrue("Missing \"$line\" in:\n$report", report.contains(line))
    }

    private fun input(): DiagnosticsInput {
        val device = SourceEntity(id = 1, type = SourceType.DEVICE, name = "Device storage", location = "device", lastScanAt = T0)
        val folder = SourceEntity(
            id = 2,
            type = SourceType.FOLDER,
            name = "Movies",
            location = "content://com.android.externalstorage.documents/tree/primary%3AMovies",
            lastScanAt = T0 - 86_400_000L,
            lastError = "Permission revoked",
        )
        val share = SourceEntity(
            id = 3,
            type = SourceType.SMB,
            name = "NAS",
            location = "smb://nas.local/media",
            username = "alice",
            password = "s3cret",
            enabled = false,
        )
        val server = SourceEntity(
            id = 4,
            type = SourceType.JELLYFIN,
            name = "Jellyfin",
            location = "http://jf.local:8096",
            token = "tok-123",
            userId = "user-9",
        )
        return DiagnosticsInput(
            createdAt = T0 + 30 * 60_000L,
            appVersion = "1.0.42",
            device = "samsung SM-G990B",
            androidRelease = "14",
            sdkInt = 34,
            permissions = listOf(
                "Videos (READ_MEDIA_VIDEO)" to "granted",
                "All files access (MANAGE_EXTERNAL_STORAGE)" to "allowed",
            ),
            videoAccess = VideoAccess.FULL,
            scanDeviceStorage = true,
            census = MediaStoreCensus(
                total = 140,
                kept = 138,
                skipped = mapOf("hidden or system folder" to 2),
                folders = listOf(
                    "DCIM/Camera" to 80,
                    "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video" to 30,
                    "DCIM/Screen recordings" to 10,
                    "Movies" to 6,
                    "Download" to 4,
                    "" to 3,
                ) + (1..9).map { "Folder $it" to 1 } + listOf("Extra A" to 1, "Extra B" to 1),
                volumes = mapOf("external_primary" to 130, "1234-5678" to 10),
            ),
            stats = LibraryStats(
                sources = listOf(
                    SourceStats(device, present = 0, missing = 12, lastScan = ScanSummary(T0, 2_300, found = 0, hidden = 12)),
                    SourceStats(
                        folder,
                        present = 3,
                        missing = 0,
                        lastScan = ScanSummary(T0 + 60_000L, 500, found = null, error = "Permission revoked"),
                    ),
                    SourceStats(share, present = 0, missing = 0, lastScan = null),
                    SourceStats(server, present = 5, missing = 0, lastScan = null),
                ),
                kinds = listOf(
                    KindCount(VideoKind.MOVIE, MatchState.MATCHED, 2),
                    KindCount(VideoKind.MOVIE, MatchState.UNMATCHED, 1),
                    KindCount(VideoKind.VIDEO, MatchState.UNMATCHED, 5),
                ),
                deviceScannable = true,
            ),
        )
    }

    private companion object {
        /** 2026-10-15 09:00:00 UTC. */
        const val T0 = 1_792_054_800_000L
    }
}
