package com.aakash.novafork.sources.net

import com.aakash.novafork.core.artwork.ArtworkRules
import com.aakash.novafork.core.library.NaturalOrder
import com.aakash.novafork.core.library.VideoFiles
import com.aakash.novafork.core.subs.SubtitleRules
import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import java.util.Locale

/** What libVLC says an entry of a folder listing is. */
internal enum class VlcItemKind {
    DIRECTORY,
    FILE,

    /** FTP servers without MLSD (vsftpd), NFS symlinks: libVLC can't tell. */
    UNKNOWN,
}

/** One entry of a folder libVLC listed, read from its media before that was released. */
internal data class VlcItem(
    /** The MRL libVLC reports for the entry. */
    val uri: String,
    /** The entry's name: the file name for file servers, the title on a UPnP server. */
    val name: String,
    val kind: VlcItemKind,
    /** An http(s) image the server offers for it (UPnP album art, server icon). */
    val artworkUrl: String? = null,
    /** libVLC's description meta: the UDN of a server the UPnP discoverer found. */
    val description: String? = null,
    /** Carries album or track-number metadata: a UPnP music item. */
    val isAudio: Boolean = false,
)

/** Pure rules behind the libVLC-backed sources (FTP, SFTP, NFS, UPnP). */
internal object VlcBrowserRules {
    /**
     * Extensions of files that are never folders. Decides entries libVLC lists without a type
     * ([VlcItemKind.UNKNOWN]); any other name is tried as a folder.
     */
    private val FILE_EXTENSIONS: Set<String> = VideoFiles.EXTENSIONS + ArtworkRules.IMAGE_EXTENSIONS +
        SubtitleRules.EXTENSIONS + setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ac3", "dts", "mka",
            "gif", "bmp", "tif", "tiff", "heic",
            "nfo", "txt", "xml", "json", "log", "url", "lnk", "ini", "db", "md5", "sha1", "sfv", "cue",
            "m3u", "m3u8", "pls", "nzb", "par2", "torrent", "rar", "zip", "7z", "gz", "tar", "exe", "pdf",
            "doc", "docx", "html", "htm", "srr", "sup",
        )

    /** Whether an entry is a folder: its type when libVLC knows it, else its name has no file extension. */
    fun isDirectory(kind: VlcItemKind, name: String): Boolean = when (kind) {
        VlcItemKind.DIRECTORY -> true
        VlcItemKind.FILE -> false
        VlcItemKind.UNKNOWN -> {
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            extension.isEmpty() || extension !in FILE_EXTENSIONS
        }
    }

    /**
     * The decoded path below [location] of an item listed in the folder at [folderPath]: read from
     * the URI libVLC reports when that lies below the location (an SFTP server's home folder,
     * listed for a location without path, shows up as its real absolute path), else [folderPath]
     * plus the item's [name].
     */
    fun entryPath(location: NetworkLocation, folderPath: String, itemUri: String, name: String): String {
        val reported = NetworkLocation.parse(itemUri)
        if (reported != null && location.covers(reported)) {
            val relative = if (location.path.isEmpty()) {
                reported.path
            } else {
                reported.path.substring(minOf(location.path.length + 1, reported.path.length))
            }
            if (relative.isNotEmpty() && (folderPath.isEmpty() || relative.startsWith("$folderPath/"))) return relative
        }
        val clean = name.trim('/')
        return when {
            clean.isEmpty() -> folderPath
            folderPath.isEmpty() -> clean
            else -> "$folderPath/$clean"
        }
    }

    /**
     * The entries of a file-server folder: folders and files, hidden names left out. Files carry
     * their URI as [RemoteEntry.playUri] when they are videos.
     */
    fun entries(location: NetworkLocation, folderPath: String, items: List<VlcItem>): List<RemoteEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<RemoteEntry>(items.size)
        for (item in items) {
            val name = item.name.trim().trimEnd('/')
            if (name.isEmpty() || name.substringAfterLast('/').startsWith(".")) continue
            val path = entryPath(location, folderPath, item.uri, name)
            if (path.isEmpty() || path == folderPath || !seen.add(path)) continue
            val directory = isDirectory(item.kind, name)
            out += RemoteEntry(
                name = name,
                path = path,
                isDirectory = directory,
                playUri = if (!directory && VideoFiles.isVideo(name)) location.child(path).toUri() else null,
            )
        }
        return out
    }

    /** libVLC media options carrying the login of an FTP or SFTP source; NFS needs none. */
    fun loginOptions(source: SourceEntity): List<String> {
        val (userOption, passwordOption) = when (source.type) {
            SourceType.FTP -> "ftp-user" to "ftp-pwd"
            SourceType.SFTP -> "sftp-user" to "sftp-pwd"
            SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.NFS,
            SourceType.UPNP, SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
            -> return emptyList()
        }
        val options = ArrayList<String>(2)
        source.username?.takeIf { it.isNotEmpty() && it.none(Char::isISOControl) }?.let { options += ":$userOption=$it" }
        source.password?.takeIf { it.isNotEmpty() && it.none(Char::isISOControl) }?.let { options += ":$passwordOption=$it" }
        return options
    }

    /** Folders first, then names in natural order, as the Browse screen lists them. */
    val BROWSE_ORDER: Comparator<RemoteEntry> = compareByDescending<RemoteEntry> { it.isDirectory }
        .thenBy(NaturalOrder) { it.name }
}
