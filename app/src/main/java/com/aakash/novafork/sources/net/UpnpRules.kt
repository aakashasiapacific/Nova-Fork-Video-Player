package com.aakash.novafork.sources.net

import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.subs.SubtitleRules
import com.aakash.novafork.data.NetworkLocation
import java.util.Locale

/**
 * MRLs and naming rules of UPnP/DLNA media servers as libVLC's UPnP module sees them: a server is
 * browsed as "upnp://<description URL without scheme>?ObjectID=<id>" ("upnps://" for https), each
 * container listed comes back as such an MRL and each item as its http stream URL.
 */
internal object UpnpRules {
    /** The ContentDirectory root every server has. */
    const val ROOT_OBJECT_ID = "0"

    private const val UDN_PREFIX = "uuid:"
    private const val OBJECT_ID = "ObjectID="

    /** Containers that hold no videos (their names in a few languages); a scan doesn't open them. */
    private val SKIPPED_CONTAINERS = setOf(
        "music", "audio", "audiobooks", "audio books", "podcasts", "radio", "playlists", "pictures", "picture",
        "photos", "photo", "images", "image", "musique", "musik", "música", "musica", "muziek", "musiikki",
        "bilder", "fotos", "foto", "immagini", "imágenes", "imagenes", "afbeeldingen", "kuvat", "zdjęcia",
    )

    /** Containers that show the server's folders as they are on disk: when offered, the only view a scan walks. */
    private val FOLDER_VIEWS = setOf(
        "folders", "browse folders", "by folder", "folder", "directories", "ordner", "dossiers", "carpetas", "cartelle",
        "mappen",
    )

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ac3", "dts", "mka", "alac", "aiff")

    fun isUdn(text: String?): Boolean = text != null && text.trim().startsWith(UDN_PREFIX, ignoreCase = true)

    /** The MRL that lists container [objectId] of the server described at [descriptionUrl]; null if that isn't http(s). */
    fun browseMrl(descriptionUrl: String, objectId: String): String? {
        val url = descriptionUrl.trim()
        val scheme = url.substringBefore("://", "").lowercase(Locale.ROOT)
        val base = when (scheme) {
            "http" -> "upnp" + url.substring(4)
            "https" -> "upnps" + url.substring(5)
            else -> return null
        }
        val separator = if ('?' in base) '&' else '?'
        return base + separator + OBJECT_ID + NetworkLocation.encodeSegment(objectId)
    }

    /** The decoded ObjectID of a container MRL libVLC listed; null when it has none. */
    fun objectId(mrl: String): String? {
        val query = mrl.substringAfter('?', "")
        if (query.isEmpty()) return null
        val value = query.split('&').lastOrNull { it.startsWith(OBJECT_ID) }?.substring(OBJECT_ID.length) ?: return null
        return NetworkLocation.decode(value).takeIf { it.isNotEmpty() }
    }

    /** The description URL of a server the discoverer listed ("upnp://host:port/desc.xml?ObjectID=0"); null for others. */
    fun descriptionUrl(serverMrl: String): String? {
        val mrl = serverMrl.trim()
        val scheme = mrl.substringBefore("://", "").lowercase(Locale.ROOT)
        val http = when (scheme) {
            "upnp" -> "http" + mrl.substring(4)
            "upnps" -> "https" + mrl.substring(5)
            else -> return null
        }
        val marker = http.lastIndexOf(OBJECT_ID)
        if (marker <= 0) return http
        val delimiter = http[marker - 1]
        if (delimiter != '?' && delimiter != '&') return http
        val end = http.indexOf('&', marker).let { if (it < 0) http.length else it }
        val before = http.substring(0, marker - 1)
        val after = http.substring(end)
        return when {
            after.isEmpty() -> before
            delimiter == '?' -> before + "?" + after.removePrefix("&")
            else -> before + after
        }
    }

    /** Whether a scan skips the container named [title] (music and photo branches). */
    fun isSkippedContainer(title: String): Boolean = title.trim().lowercase(Locale.ROOT) in SKIPPED_CONTAINERS

    /** The index of the folder view among a container's child container [titles], if it offers one. */
    fun folderViewIndex(titles: List<String>): Int? =
        titles.indexOfFirst { it.trim().lowercase(Locale.ROOT) in FOLDER_VIEWS }.takeIf { it >= 0 }

    /**
     * Whether an item a server listed is a video. libVLC lists music and photo items too and
     * doesn't say which is which: an extension (of the title or the stream URL) or music
     * metadata decides; an item without either counts as a video.
     */
    fun isVideoItem(title: String, uri: String, isAudio: Boolean): Boolean {
        if (isAudio) return false
        for (name in listOf(title, lastSegment(uri))) {
            val extension = VideoFiles.extension(name)
            if (extension.isEmpty() || extension.length > 5) continue
            if (extension in VideoFiles.EXTENSIONS) return true
            if (extension in AUDIO_EXTENSIONS || extension in ArtworkRules.IMAGE_EXTENSIONS ||
                extension in SubtitleRules.EXTENSIONS || extension in OTHER_IMAGE_EXTENSIONS
            ) {
                return false
            }
        }
        return true
    }

    /** A file name for NameParser: the title, with the stream's video extension when the title has none. */
    fun fileName(title: String, uri: String): String {
        val clean = folderName(title)
        if (VideoFiles.isVideo(clean)) return clean
        val extension = VideoFiles.extension(lastSegment(uri))
        return if (extension in VideoFiles.EXTENSIONS) "$clean.$extension" else clean
    }

    /** A title usable as one folder name: no '/' (folder paths are '/'-separated). */
    fun folderName(title: String): String = title.trim().replace('/', '-').ifEmpty { "Untitled" }

    private val OTHER_IMAGE_EXTENSIONS = setOf("gif", "bmp", "tif", "tiff", "heic")

    private fun lastSegment(uri: String): String =
        NetworkLocation.decode(uri.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/'))
}
