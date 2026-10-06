package com.aakash.novafork.data

import com.aakash.novafork.data.db.SourceEntity
import java.io.InputStream

/** One video found by a scanner. The repository parses names and matches metadata afterwards. */
data class ScannedVideo(
    /** Playable URI: content:// (MediaStore/SAF), smb://host/share/… (see core SmbUris). */
    val uri: String,
    val fileName: String,
    /** Display folder path root → leaf, '/'-separated ("Movies/Action"; SMB: path inside the share). */
    val folder: String,
    /** Parent folder names nearest-first, for NameParser ("Season 1", "The Office (2005)", …). */
    val parentFolders: List<String>,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Epoch ms; scanners use the file's own date when available, else now. */
    val dateAdded: Long,
    val dateModified: Long? = null,
    /** Local artwork URIs resolved with core ArtworkRules from the folder listing (null = none). */
    val localPoster: String? = null,
    val localBackdrop: String? = null,
    val localThumb: String? = null,
)

interface SourceScanner {
    /**
     * Walks [source] and returns every video in it. Runs on a background dispatcher, is cancellable,
     * skips core VideoFiles.isIgnoredFolder / isSample, never follows more than 12 folder levels.
     * Throws on a source-level failure (permission revoked, server unreachable, bad login).
     */
    suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit = {}): List<ScannedVideo>
}

data class SmbCredentials(val username: String?, val password: String?, val domain: String?)

data class SmbEntry(
    val name: String,
    /** Path inside the share, '/'-separated, no leading slash. */
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

interface SmbBrowser {
    /** Lists a directory. [path] is relative to the share root ("" = root). Hidden entries are skipped. */
    suspend fun list(host: String, port: Int?, share: String, path: String, credentials: SmbCredentials): List<SmbEntry>

    /** Connects, authenticates and opens the share; failure carries a human-readable message. */
    suspend fun test(host: String, port: Int?, share: String, path: String, credentials: SmbCredentials): Result<Unit>

    /**
     * Opens a file for reading (used by the Coil fetcher for poster.jpg on a share).
     * Credentials come from the source that owns host+share. Caller closes the stream.
     */
    suspend fun open(smbUri: String): SmbReadHandle
}

class SmbReadHandle(val stream: InputStream, val length: Long)
