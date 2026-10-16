package com.aakash.novafork.sources

import android.net.Uri
import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.aakash.novafork.data.SmbBrowser
import okio.buffer
import okio.source
import java.util.Locale

/**
 * Loads local artwork from smb:// URIs (poster.jpg, fanart.jpg next to videos on a share).
 * Images go through Coil's disk cache so a library scrolls without asking the server each time.
 */
@OptIn(ExperimentalCoilApi::class)
class SmbFetcher(
    private val uri: Uri,
    private val options: Options,
    private val smb: SmbBrowser,
    private val diskCache: Lazy<DiskCache?>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = options.diskCacheKey ?: uri.toString()
        val mimeType = mimeTypeOf(uri)
        val cache = diskCache.value

        if (cache != null && options.diskCachePolicy.readEnabled) {
            val snapshot = cache.openSnapshot(key)
            if (snapshot != null) {
                return SourceResult(ImageSource(snapshot.data, cache.fileSystem, key, snapshot), mimeType, DataSource.DISK)
            }
        }

        val handle = smb.open(uri.toString())
        val editor = try {
            if (cache != null && options.diskCachePolicy.writeEnabled) cache.openEditor(key) else null
        } catch (e: Exception) {
            handle.stream.close()
            throw e
        }
        if (cache == null || editor == null) {
            return SourceResult(ImageSource(handle.stream.source().buffer(), options.context), mimeType, DataSource.NETWORK)
        }

        val snapshot = try {
            handle.stream.use { input ->
                cache.fileSystem.write(editor.data) { writeAll(input.source()) }
            }
            editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (abortError: Exception) {
                e.addSuppressed(abortError)
            }
            throw e
        }
        if (snapshot != null) {
            return SourceResult(ImageSource(snapshot.data, cache.fileSystem, key, snapshot), mimeType, DataSource.NETWORK)
        }
        // The entry was evicted right after writing it (tiny cache): read it once more directly.
        val again = smb.open(uri.toString())
        return SourceResult(ImageSource(again.stream.source().buffer(), options.context), mimeType, DataSource.NETWORK)
    }

    private fun mimeTypeOf(uri: Uri): String? {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (extension.isNullOrEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    class Factory(private val smb: SmbBrowser) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.scheme.equals("smb", ignoreCase = true)) return null
            return SmbFetcher(data, options, smb, lazy { imageLoader.diskCache })
        }
    }
}
