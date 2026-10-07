package com.aakash.novafork.sources

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.core.subs.SubtitleRules
import com.aakash.novafork.data.SmbBrowser
import com.aakash.novafork.data.SmbCredentials
import com.aakash.novafork.data.StoragePermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Finds external subtitle files next to a video (core SubtitleRules naming) and makes them
 * readable for libVLC: files on local storage are returned in place, files behind SAF or on SMB
 * shares are copied into the cache first.
 */
class SubtitleFinder(
    private val context: Context,
    private val smb: SmbBrowser,
    private val credentialsFor: suspend (host: String, share: String) -> SmbCredentials?,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Best effort and never throws: whatever was found before a failure is returned. */
    suspend fun find(videoUri: String, fileName: String, preferredLanguages: List<String>): List<File> =
        withContext(Dispatchers.IO) {
            val found = ArrayList<File>()
            try {
                collect(videoUri, fileName, preferredLanguages, found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Subtitles are optional; play without the rest.
            }
            found
        }

    /** Copies a subtitle the user picked into the cache so libVLC can open it by path. */
    suspend fun importPicked(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "subs/picked")
            if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
            val target = File(dir, safeFileName(displayName(uri)) ?: DEFAULT_PICKED_NAME)
            val input = resolver.openInputStream(uri) ?: return@withContext null
            if (input.use { copyLimited(it, target, MAX_PICKED_BYTES) }) target else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun collect(videoUri: String, fileName: String, languages: List<String>, out: MutableList<File>) {
        if (videoUri.startsWith("/")) {
            fromDirectory(File(videoUri).parentFile, fileName, languages, out)
            return
        }
        val uri = Uri.parse(videoUri)
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { fromDirectory(File(it).parentFile, fileName, languages, out) }
            ContentResolver.SCHEME_CONTENT -> fromContent(uri, videoUri, fileName, languages, out)
            "smb" -> fromSmb(videoUri, fileName, languages, out)
        }
    }

    /**
     * Local folder readable with java.io.File: the files are returned where they are.
     * False when the folder can't be listed at all.
     */
    private fun fromDirectory(directory: File?, fileName: String, languages: List<String>, out: MutableList<File>): Boolean {
        val files = directory?.listFiles()?.filter { it.isFile } ?: return false
        val byName = files.associateBy { it.name }
        for (name in SubtitleRules.match(fileName, files.map { it.name }, languages)) {
            if (out.size >= MAX_FILES) break
            val file = byName[name] ?: continue
            if (file.length() in 1L..MAX_BYTES && file.canRead()) out += file
        }
        return true
    }

    private suspend fun fromContent(uri: Uri, videoUri: String, fileName: String, languages: List<String>, out: MutableList<File>) {
        when {
            uri.authority == MediaStore.AUTHORITY -> fromMediaStore(uri, videoUri, fileName, languages, out)
            DocumentsContract.isDocumentUri(context, uri) -> fromDocument(uri, videoUri, fileName, languages, out)
        }
    }

    /**
     * MediaStore hides .srt files from apps without "All files access", so the folder is listed
     * directly when that is granted. Otherwise (or when Android 10's scoped storage blocks the
     * listing) MediaStore may still list subtitle files, which are then copied through their
     * content URIs.
     */
    private suspend fun fromMediaStore(uri: Uri, videoUri: String, fileName: String, languages: List<String>, out: MutableList<File>) {
        if (StoragePermissions.hasAllFilesAccess(context)) {
            val path = querySingle(uri, DATA_COLUMN)
            if (path != null && fromDirectory(File(path).parentFile, fileName, languages, out)) return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) fromMediaStoreFolder(uri, videoUri, fileName, languages, out)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun fromMediaStoreFolder(
        uri: Uri,
        videoUri: String,
        fileName: String,
        languages: List<String>,
        out: MutableList<File>,
    ) {
        val relativePath = querySingle(uri, MediaStore.MediaColumns.RELATIVE_PATH) ?: return
        val volume = querySingle(uri, MediaStore.MediaColumns.VOLUME_NAME) ?: return
        val collection = MediaStore.Files.getContentUri(volume)
        val siblings = HashMap<String, Sibling>()
        val cursor = resolver.query(
            collection,
            arrayOf(BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relativePath),
            null,
        ) ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.longAt(0) ?: continue
                val name = c.stringAt(1) ?: continue
                siblings[name] = Sibling(ContentUris.withAppendedId(collection, id), c.longAt(2))
            }
        }
        copyMatches(videoUri, fileName, siblings, languages, out) { sibling ->
            resolver.openInputStream(sibling.uri)
        }
    }

    private suspend fun fromDocument(uri: Uri, videoUri: String, fileName: String, languages: List<String>, out: MutableList<File>) {
        val authority = uri.authority ?: return
        val documentId = DocumentsContract.getDocumentId(uri)
        when (authority) {
            MEDIA_DOCUMENTS -> {
                // "video:123" from the system picker's media section.
                val id = documentId.substringAfter("video:", "").toLongOrNull() ?: return
                val mediaUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                return fromMediaStore(mediaUri, videoUri, fileName, languages, out)
            }
            DOWNLOAD_DOCUMENTS -> {
                if (documentId.startsWith(RAW_PREFIX)) {
                    fromDirectory(File(documentId.removePrefix(RAW_PREFIX)).parentFile, fileName, languages, out)
                }
                return
            }
        }

        val isTree = DocumentsContract.isTreeUri(uri)
        val parentId = (if (isTree) treeParent(uri) else null)
            ?: if (authority == EXTERNAL_STORAGE_DOCUMENTS) externalStorageParent(documentId) else null
        val treeUri = when {
            parentId == null -> null
            isTree -> DocumentsContract.buildTreeDocumentUri(authority, DocumentsContract.getTreeDocumentId(uri))
            else -> grantedTreeCovering(authority, parentId)
        }
        if (parentId != null && treeUri != null) {
            fromTree(treeUri, parentId, videoUri, fileName, languages, out)
            return
        }
        // A single picked file without folder access: plain file access may still reach its folder.
        if (authority == EXTERNAL_STORAGE_DOCUMENTS && StoragePermissions.hasAllFilesAccess(context)) {
            fromDirectory(externalStorageFile(documentId)?.parentFile, fileName, languages, out)
        }
    }

    private suspend fun fromTree(
        treeUri: Uri,
        parentId: String,
        videoUri: String,
        fileName: String,
        languages: List<String>,
        out: MutableList<File>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_MIME_TYPE)
        val siblings = HashMap<String, Sibling>()
        val cursor = resolver.query(childrenUri, projection, null, null, null) ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                if (c.stringAt(3) == Document.MIME_TYPE_DIR) continue
                val id = c.stringAt(0) ?: continue
                val name = c.stringAt(1) ?: continue
                siblings[name] = Sibling(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), c.longAt(2))
            }
        }
        copyMatches(videoUri, fileName, siblings, languages, out) { sibling ->
            resolver.openInputStream(sibling.uri)
        }
    }

    private suspend fun fromSmb(videoUri: String, fileName: String, languages: List<String>, out: MutableList<File>) {
        val location = SmbUris.parse(videoUri) ?: return
        val credentials = credentialsFor(location.host, location.share) ?: SmbCredentials(null, null, null)
        val entries = smb.list(location.host, location.port, location.share, location.parentPath, credentials)
        val siblings = HashMap<String, Sibling>()
        for (entry in entries) {
            if (entry.isDirectory) continue
            siblings[entry.name] = Sibling(Uri.parse(SmbUris.build(location.copy(path = entry.path))), entry.sizeBytes)
        }
        copyMatches(videoUri, fileName, siblings, languages, out) { sibling ->
            smb.open(sibling.uri.toString()).stream
        }
    }

    private class Sibling(val uri: Uri, val sizeBytes: Long?)

    private suspend fun copyMatches(
        videoUri: String,
        fileName: String,
        siblings: Map<String, Sibling>,
        languages: List<String>,
        out: MutableList<File>,
        open: suspend (Sibling) -> InputStream?,
    ) {
        val matches = SubtitleRules.match(fileName, siblings.keys.toList(), languages)
        if (matches.isEmpty()) return
        val dir = File(context.cacheDir, "subs/" + Integer.toHexString(videoUri.hashCode()))
        if (!dir.isDirectory && !dir.mkdirs()) return
        for (name in matches) {
            if (out.size >= MAX_FILES) return
            val sibling = siblings[name] ?: continue
            val size = sibling.sizeBytes
            if (size != null && size > MAX_BYTES) continue
            val target = File(dir, safeFileName(name) ?: continue)
            try {
                val input = open(sibling) ?: continue
                if (input.use { copyLimited(it, target, MAX_BYTES) }) out += target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                target.delete()
            }
        }
    }

    /** Copies [input] into [target]; false (and no file left behind) when it is larger than [limit]. */
    private fun copyLimited(input: InputStream, target: File, limit: Long): Boolean {
        var total = 0L
        val complete = target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return@use false
                output.write(buffer, 0, read)
            }
            true
        }
        if (!complete || total == 0L) {
            target.delete()
            return false
        }
        return true
    }

    /** For a document inside a tree: the tree's path to it lists the parent just before it. */
    private fun treeParent(documentUri: Uri): String? {
        val path = try {
            DocumentsContract.findDocumentPath(resolver, documentUri)?.path
        } catch (e: Exception) {
            null
        }
        if (path == null || path.size < 2) return null
        return path[path.size - 2]
    }

    /** "primary:Movies/x.mkv" → "primary:Movies", "primary:x.mkv" → "primary:". */
    private fun externalStorageParent(documentId: String): String? {
        val colon = documentId.indexOf(':')
        if (colon < 0) return null
        val relative = documentId.substring(colon + 1)
        return documentId.substring(0, colon + 1) + relative.substringBeforeLast('/', "")
    }

    /** A folder the user granted earlier (e.g. a library folder) that contains [documentId]. */
    private fun grantedTreeCovering(authority: String, documentId: String): Uri? =
        resolver.persistedUriPermissions.asSequence()
            .filter { it.isReadPermission }
            .map { it.uri }
            .filter { it.authority == authority && DocumentsContract.isTreeUri(it) }
            .firstOrNull { tree ->
                val treeId = DocumentsContract.getTreeDocumentId(tree)
                documentId == treeId || documentId.startsWith(if (treeId.endsWith(":")) treeId else "$treeId/")
            }

    private fun externalStorageFile(documentId: String): File? {
        val volume = documentId.substringBefore(':', "")
        if (volume.isEmpty()) return null
        val relative = documentId.substringAfter(':')
        val root = if (volume.equals("primary", ignoreCase = true)) {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage", volume)
        }
        return File(root, relative)
    }

    private fun querySingle(uri: Uri, column: String): String? {
        val cursor = resolver.query(uri, arrayOf(column), null, null, null) ?: return null
        return cursor.use { c -> if (c.moveToFirst()) c.stringAt(0) else null }
    }

    private fun displayName(uri: Uri): String? {
        val name = try {
            querySingle(uri, OpenableColumns.DISPLAY_NAME)
        } catch (e: Exception) {
            null
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    /** A name that is safe as a single file name in the cache, keeping its extension; null if nothing is left. */
    private fun safeFileName(raw: String?): String? {
        val cleaned = raw.orEmpty().replace(UNSAFE_NAME_CHARS, "_").trim().trimStart('.')
        if (cleaned.isEmpty()) return null
        if (cleaned.length <= MAX_NAME_LENGTH) return cleaned
        val extension = cleaned.substringAfterLast('.', "").take(MAX_EXTENSION_LENGTH)
        val stem = cleaned.substringBeforeLast('.').take(MAX_NAME_LENGTH - extension.length - 1)
        return if (extension.isEmpty()) cleaned.take(MAX_NAME_LENGTH) else "$stem.$extension"
    }

    private companion object {
        const val MAX_FILES = 8
        const val MAX_BYTES = 5L * 1024 * 1024

        /** Picked files get more room (ASS files with embedded fonts) but a picked video is refused. */
        const val MAX_PICKED_BYTES = 20L * 1024 * 1024
        const val DEFAULT_PICKED_NAME = "subtitle.srt"
        const val MAX_NAME_LENGTH = 120
        const val MAX_EXTENSION_LENGTH = 8

        const val EXTERNAL_STORAGE_DOCUMENTS = "com.android.externalstorage.documents"
        const val MEDIA_DOCUMENTS = "com.android.providers.media.documents"
        const val DOWNLOAD_DOCUMENTS = "com.android.providers.downloads.documents"
        const val RAW_PREFIX = "raw:"

        @Suppress("DEPRECATION")
        const val DATA_COLUMN = MediaStore.MediaColumns.DATA

        val UNSAFE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")
    }
}
