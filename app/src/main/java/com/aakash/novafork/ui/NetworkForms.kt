package com.aakash.novafork.ui

import com.aakash.novafork.data.NetworkLocation
import com.aakash.novafork.data.NetworkSourceInput
import com.aakash.novafork.data.NetworkSources
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.ui.model.NetworkSourceForm
import java.util.Locale

/**
 * The add/edit dialog of the network sources other than SMB: which fields each type asks for,
 * the form as data's NetworkSourceInput, and a saved source back as a form. Pure Kotlin.
 */
object NetworkForms {
    const val PORT_ERROR = "The port must be between 1 and 65535"

    /** "FTP", "DLNA", "Jellyfin"… as the UI names a source type. */
    fun typeLabel(type: SourceType): String = when (type) {
        SourceType.UPNP -> "DLNA"
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP, SourceType.SFTP,
        SourceType.NFS, SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
        -> NetworkSources.label(type)
    }

    /** File servers: a folder below the server can be picked, and Browse folders are paths. */
    fun takesPath(type: SourceType): Boolean = when (type) {
        SourceType.FTP, SourceType.SFTP, SourceType.NFS, SourceType.WEBDAV -> true
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.UPNP,
        SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
        -> false
    }

    /** A port field next to the server (media servers take theirs in the URL, NFS has none). */
    fun takesPort(type: SourceType): Boolean = when (type) {
        SourceType.FTP, SourceType.SFTP, SourceType.WEBDAV -> true
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.NFS, SourceType.UPNP,
        SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX,
        -> false
    }

    /** A user name and password. */
    fun takesLogin(type: SourceType): Boolean = when (type) {
        SourceType.FTP, SourceType.SFTP, SourceType.WEBDAV, SourceType.JELLYFIN, SourceType.EMBY -> true
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.NFS, SourceType.UPNP,
        SourceType.PLEX,
        -> false
    }

    /** The port typed, null when blank; IllegalArgumentException when it is not 1..65535. */
    fun port(form: NetworkSourceForm): Int? {
        if (!takesPort(form.type)) return null
        val text = form.port.trim()
        if (text.isEmpty()) return null
        return text.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw IllegalArgumentException(PORT_ERROR)
    }

    /** The server field and the folder field as one address: "nas.local" + "TV/Anime" → "nas.local/TV/Anime". */
    fun address(form: NetworkSourceForm): String {
        val server = form.address.trim()
        val path = if (takesPath(form.type)) form.path.trim().replace('\\', '/').trim('/') else ""
        return if (path.isEmpty()) server else server.trimEnd('/') + "/" + path
    }

    /**
     * What LibraryRepository.saveNetworkSource takes for [form], with only the fields its type
     * uses. Throws IllegalArgumentException (a message for the user) for a bad port; the rest is
     * checked by NetworkSources.toSource.
     */
    fun toInput(form: NetworkSourceForm): NetworkSourceInput {
        val login = takesLogin(form.type)
        val mediaLibraries = form.type == SourceType.JELLYFIN || form.type == SourceType.EMBY
        return NetworkSourceInput(
            type = form.type,
            name = form.name.trim(),
            address = address(form),
            port = port(form),
            username = if (login) form.username.trim().ifEmpty { null } else null,
            password = if (login) form.password.ifEmpty { null } else null,
            token = if (form.type == SourceType.PLEX) form.token.trim().ifEmpty { null } else null,
            libraryIds = if (mediaLibraries) form.libraryIds else emptyList(),
        )
    }

    /** A saved source as the dialog shows it for editing. */
    fun formFor(source: SourceEntity): NetworkSourceForm {
        val base = NetworkSourceForm(
            id = source.id,
            type = source.type,
            name = source.name,
            address = source.location,
            username = if (takesLogin(source.type)) source.username.orEmpty() else "",
            password = if (takesLogin(source.type)) source.password.orEmpty() else "",
            token = if (source.type == SourceType.PLEX) source.token.orEmpty() else "",
            libraryIds = NetworkSources.libraryIds(source),
        )
        if (!takesPath(source.type)) return base
        val location = NetworkLocation.parse(source.location) ?: return base
        // The scheme stays visible unless it is the type's own (WebDAV is thought of as a URL).
        val plainScheme = NetworkSources.schemes(source.type).firstOrNull()
        val showScheme = source.type == SourceType.WEBDAV || location.scheme != plainScheme
        val host = hostLabel(location.host)
        return base.copy(
            address = if (showScheme) "${location.scheme}://$host" else host,
            port = location.port?.toString().orEmpty(),
            path = location.path,
        )
    }

    /** "FTP · nas.local:2121/Movies", "Jellyfin · 192.168.1.5:8096", "DLNA · 192.168.1.9". */
    fun sourceDetail(source: SourceEntity): String {
        val location = NetworkLocation.parse(source.location)
        if (location == null) {
            return if (source.type == SourceType.UPNP) "DLNA server" else source.location
        }
        val host = hostLabel(location.host) + (location.port?.let { ":$it" } ?: "")
        val where = when (source.type) {
            // A description URL's path says nothing to people.
            SourceType.UPNP -> location.host
            else -> listOf(host, location.path).filter { it.isNotEmpty() }.joinToString("/")
        }
        val label = when (source.type) {
            // ftps:// and ftpes:// say more than "FTP".
            SourceType.FTP -> location.scheme.uppercase(Locale.ROOT)
            else -> typeLabel(source.type)
        }
        return "$label · $where"
    }

    /** An IPv6 literal in brackets, so a port can follow it. */
    private fun hostLabel(host: String): String = if (':' in host) "[$host]" else host
}
