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
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okio.buffer
import okio.source

/**
 * Artist photos and missing album covers from Deezer's public catalogue (no key), for Coil, when
 * Preferences › Player › Music › Artist Photos and Covers is on. [artist] and [album] make image
 * addresses ("novaonline:artist:<name>", "novaonline:album:<artist>\n<album>"); the picture is
 * found once, kept in the app's cache, and a miss is remembered, so nothing is asked twice.
 */
object OnlineMusicArt {
    const val SCHEME = "novaonline"
    private const val TIMEOUT_MS = 8_000
    private const val MISS = "none"
    private val json = Json { ignoreUnknownKeys = true }

    fun artist(name: String): String = "$SCHEME:artist:${Uri.encode(name)}"

    fun album(artist: String, title: String): String = "$SCHEME:album:${Uri.encode("$artist\n$title")}"

    internal fun file(context: Context, key: String): File {
        val dir = File(context.cacheDir, "online_music_art").apply { mkdirs() }
        return File(dir, Integer.toHexString(key.hashCode()) + "_" + key.length)
    }

    /** The picture's address for [kind] ("artist" / "album") and [query], or null when Deezer has none. */
    internal fun lookUp(kind: String, query: String): String? {
        val (path, field, q) = when (kind) {
            "artist" -> Triple("search/artist", "picture_xl", query)
            else -> {
                val (artist, title) = query.split('\n', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                Triple("search/album", "cover_xl", "artist:\"$artist\" album:\"$title\"")
            }
        }
        val connection = URL("https://api.deezer.com/$path?limit=1&q=${URLEncoder.encode(q, "UTF-8")}").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val first = ((json.parseToJsonElement(body) as? JsonObject)?.get("data") as? JsonArray)?.firstOrNull() as? JsonObject
            (first?.get(field) as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("https://") }
        } finally {
            connection.disconnect()
        }
    }

    internal fun download(url: String, into: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw FileNotFoundException(url)
            val temp = File(into.parentFile, into.name + ".tmp")
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(into)) temp.delete()
        } finally {
            connection.disconnect()
        }
    }

    internal const val MISS_MARK = MISS
}

class OnlineMusicArtFetcher(private val data: Uri, private val context: Context) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val parts = data.schemeSpecificPart.split(':', limit = 2)
        val kind = parts.getOrNull(0) ?: throw FileNotFoundException("No kind")
        val query = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: throw FileNotFoundException("No name")
        val key = "$kind:$query"
        val image = OnlineMusicArt.file(context, key)
        val miss = File(image.parentFile, image.name + ".miss")
        if (!image.isFile) {
            if (miss.isFile) throw FileNotFoundException("Not found before")
            val url = OnlineMusicArt.lookUp(kind, query)
            if (url == null) {
                miss.writeText(OnlineMusicArt.MISS_MARK)
                throw FileNotFoundException("Not on Deezer")
            }
            OnlineMusicArt.download(url, image)
        }
        if (!image.isFile) throw FileNotFoundException("Download failed")
        SourceResult(
            source = ImageSource(image.source().buffer(), context),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == OnlineMusicArt.SCHEME) OnlineMusicArtFetcher(data, options.context) else null
    }
}
