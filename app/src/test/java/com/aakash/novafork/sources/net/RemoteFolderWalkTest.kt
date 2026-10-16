package com.aakash.novafork.sources.net

import com.aakash.novafork.data.RemoteEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The folder walk the FTP, SFTP, NFS and WebDAV scanners share. */
class RemoteFolderWalkTest {
    private fun dir(path: String) = RemoteEntry(name = path.substringAfterLast('/'), path = path, isDirectory = true)

    private fun file(path: String, modified: Long? = null) = RemoteEntry(
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = false,
        sizeBytes = 5,
        modifiedAt = modified,
        playUri = if (path.endsWith(".mkv")) "ftp://nas/v/$path" else null,
    )

    private fun uriOf(path: String) = "ftp://nas/v/$path"

    @Test
    fun walksBelowTheRootSkippingJunk() = runTest {
        val tree = mapOf(
            "" to listOf(dir("Movies"), dir("@eaDir"), dir("Sample"), file("intro.mkv", modified = 1_000L)),
            "Movies" to listOf(file("Movies/Heat.mkv"), file("Movies/Heat-sample.mkv"), file("Movies/poster.jpg"), dir("Movies/Heat")),
            "Movies/Heat" to listOf(file("Movies/Heat/Heat.mkv")),
        )
        val listed = ArrayList<String>()
        val progress = ArrayList<Int>()
        val found = RemoteFolderWalk.scan(
            rootSegments = listOf("v"),
            list = { path -> listed += path; tree.getValue(path) },
            uriOf = ::uriOf,
            withArtwork = false,
            onProgress = { progress += it },
            onFolderFailure = { _, e -> throw e },
        )
        assertEquals(listOf("", "Movies", "Movies/Heat"), listed)
        assertEquals(listOf("intro.mkv", "Heat.mkv", "Heat.mkv"), found.map { it.fileName })
        assertEquals(listOf(1, 2, 3), progress)

        val intro = found[0]
        assertEquals("ftp://nas/v/intro.mkv", intro.uri)
        assertEquals("", intro.folder)
        assertEquals(listOf("v"), intro.parentFolders)
        assertEquals(1_000L, intro.dateAdded)
        assertEquals(1_000L, intro.dateModified)
        assertEquals(5L, intro.sizeBytes)

        val nested = found[2]
        assertEquals("Movies/Heat", nested.folder)
        assertEquals(listOf("Heat", "Movies", "v"), nested.parentFolders)
        // Without artwork support no image is offered, even a matching one.
        assertNull(found[1].localPoster)
        assertTrue(nested.dateModified == null && nested.dateAdded > 0)
    }

    @Test
    fun depthIsLimited() = runTest {
        val listed = ArrayList<String>()
        RemoteFolderWalk.scan(
            rootSegments = emptyList(),
            list = { path ->
                listed += path
                listOf(dir(if (path.isEmpty()) "d" else "$path/d"))
            },
            uriOf = ::uriOf,
            withArtwork = false,
            onProgress = {},
            onFolderFailure = { _, e -> throw e },
        )
        // The root and twelve levels below it.
        assertEquals(13, listed.size)
    }

    @Test
    fun aFailingSubfolderIsReportedAndARootFailureThrows() = runTest {
        val failures = ArrayList<String>()
        val found = RemoteFolderWalk.scan(
            rootSegments = emptyList(),
            list = { path ->
                when (path) {
                    "" -> listOf(dir("locked"), file("a.mkv"))
                    else -> throw IOException("denied")
                }
            },
            uriOf = ::uriOf,
            withArtwork = true,
            onProgress = {},
            onFolderFailure = { path, _ -> failures += path },
        )
        assertEquals(listOf("locked"), failures)
        assertEquals(listOf("a.mkv"), found.map { it.fileName })

        try {
            RemoteFolderWalk.scan(
                rootSegments = emptyList(),
                list = { throw IOException("gone") },
                uriOf = ::uriOf,
                withArtwork = false,
                onProgress = {},
                onFolderFailure = { _, _ -> },
            )
            fail("The root folder's failure fails the scan")
        } catch (e: IOException) {
            assertEquals("gone", e.message)
        }
    }

    @Test
    fun artworkUrisComeFromTheListing() = runTest {
        val found = RemoteFolderWalk.scan(
            rootSegments = emptyList(),
            list = { listOf(file("Heat.mkv"), file("poster.jpg"), file("fanart.jpg")) },
            uriOf = ::uriOf,
            withArtwork = true,
            onProgress = {},
            onFolderFailure = { _, e -> throw e },
        )
        assertEquals("ftp://nas/v/poster.jpg", found.single().localPoster)
        assertEquals("ftp://nas/v/fanart.jpg", found.single().localBackdrop)
    }
}
