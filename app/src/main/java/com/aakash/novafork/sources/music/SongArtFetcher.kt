package com.aakash.novafork.sources.music

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.FileNotFoundException

/**
 * Album art embedded in a music file, for Coil: [SongArt.embedded] turns a song's URI into an
 * image address ("novaart:<song uri>") that `AsyncImage` and image requests take like any other.
 * Songs without a picture fail the request, so the caller's placeholder shows.
 */
object SongArt {
    const val SCHEME = "novaart"

    fun embedded(songUri: String): String = "$SCHEME:${Uri.encode(songUri)}"
}

class SongArtFetcher(private val songUri: Uri, private val context: Context) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = withContext(Dispatchers.IO) { AudioTags.embeddedPicture(context, songUri) }
            ?: throw FileNotFoundException("No picture in the file")
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != SongArt.SCHEME) return null
            val song = data.schemeSpecificPart?.takeIf { it.isNotEmpty() } ?: return null
            return SongArtFetcher(Uri.parse(song), options.context)
        }
    }
}
