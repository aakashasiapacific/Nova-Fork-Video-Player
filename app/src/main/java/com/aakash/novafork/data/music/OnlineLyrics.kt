package com.aakash.novafork.data.music

import android.content.Context
import android.util.Log
import com.aakash.novafork.core.audio.LrcParser
import com.aakash.novafork.core.audio.Lyrics
import com.aakash.novafork.data.db.SongEntity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Time-synced lyrics from LRCLIB (lrclib.net, free and keyless) for a song without its own .lrc,
 * when Preferences › Player › Music › Lyrics from LRCLIB is on. Only the title, artist, album and
 * length are sent. A found file is kept in the app's cache, and a miss is remembered too, so a
 * song is asked about once.
 */
object OnlineLyrics {
    private const val TAG = "OnlineLyrics"
    private const val TIMEOUT_MS = 8_000
    private const val MISS = "\u0000none"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lyrics(context: Context, song: SongEntity): Lyrics? = withContext(Dispatchers.IO) {
        val artist = song.artist ?: song.albumArtist ?: return@withContext null
        val dir = File(context.cacheDir, "lyrics").apply { mkdirs() }
        val cached = File(dir, "${song.id}.lrc")
        try {
            val text = if (cached.isFile) {
                cached.readText()
            } else {
                (fetch(song.title, artist, song.album, song.durationMs) ?: MISS).also { cached.writeText(it) }
            }
            if (text == MISS) null else LrcParser.parse(text).takeUnless { it.isEmpty }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No online lyrics", e)
            null
        }
    }

    private fun fetch(title: String, artist: String, album: String?, durationMs: Long?): String? {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        val query = buildString {
            append("track_name=").append(enc(title))
            append("&artist_name=").append(enc(artist))
            if (!album.isNullOrBlank()) append("&album_name=").append(enc(album))
            if (durationMs != null && durationMs > 0) append("&duration=").append(durationMs / 1_000)
        }
        val connection = URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "NovaFork (https://github.com/aakashasiapacific/Nova-Fork-Video-Player)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val o = json.parseToJsonElement(body) as? JsonObject ?: return null
            (o["syncedLyrics"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }
}
