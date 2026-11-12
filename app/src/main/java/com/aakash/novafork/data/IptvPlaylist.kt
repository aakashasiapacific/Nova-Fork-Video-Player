package com.aakash.novafork.data

import java.net.HttpURLConnection
import java.net.URL

/** One channel of an IPTV list: its name, stream address, logo and group, as #EXTINF gives them. */
data class IptvChannel(val name: String, val url: String, val logo: String?, val group: String?)

/** Reads an extended M3U channel list (IPTV): "#EXTINF:-1 tvg-logo=… group-title=…,Name" then the address. */
object IptvPlaylist {
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val ATTRIBUTE = Regex("""([a-zA-Z-]+)="([^"]*)"""")

    /** A link that names a channel list rather than one stream (HLS .m3u8 plays as a stream). */
    fun isChannelList(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').lowercase().endsWith(".m3u")

    fun parse(text: String): List<IptvChannel> {
        val out = ArrayList<IptvChannel>()
        var pending: Triple<String, String?, String?>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attributes = ATTRIBUTE.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val name = line.substringAfterLast(',', "").trim().ifEmpty { attributes["tvg-name"].orEmpty() }
                    pending = Triple(name, attributes["tvg-logo"]?.takeIf { it.isNotBlank() }, attributes["group-title"]?.takeIf { it.isNotBlank() })
                }
                line.startsWith("#") -> Unit
                else -> {
                    val info = pending
                    val name = info?.first?.takeIf { it.isNotBlank() } ?: line.substringAfterLast('/').substringBefore('?')
                    out += IptvChannel(name, line, info?.second, info?.third)
                    pending = null
                }
            }
        }
        return out
    }

    /** Downloads and reads the list at [url]. Blocking; call off the main thread. */
    fun load(url: String): List<IptvChannel> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) throw java.io.IOException("The server answered ${connection.responseCode}")
            val bytes = connection.inputStream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > MAX_BYTES) break
                }
                buffer.toByteArray()
            }
            parse(String(bytes, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}
