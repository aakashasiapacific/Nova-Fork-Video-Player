package com.aakash.novafork.data

import com.aakash.novafork.core.net.LocalAddresses
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * A network location, decoded: scheme://host[:port]/path. [path] is '/'-separated without leading
 * or trailing slash ("" = the server's root); user info, query and fragment are not part of it.
 * [toUri] gives the percent-encoded form sources and videos are stored under.
 */
data class NetworkLocation(
    /** Lower case: "ftp", "sftp", "nfs", "http", "https", "smb"… */
    val scheme: String,
    /** Without the brackets of an IPv6 literal. */
    val host: String,
    /** Null: the scheme's default. */
    val port: Int? = null,
    val path: String = "",
) {
    /** The last path segment, "" at the root. */
    val name: String get() = path.substringAfterLast('/')

    val parentPath: String get() = if ('/' in path) path.substringBeforeLast('/') else ""

    /** The port connections go to: [port], else the scheme's default (null when there is none). */
    val effectivePort: Int? get() = port ?: defaultPort(scheme)

    /** The entry [name] inside this directory ([name] may itself hold '/'-separated segments). */
    fun child(name: String): NetworkLocation {
        val clean = name.trim('/')
        return if (clean.isEmpty()) this else copy(path = if (path.isEmpty()) clean else "$path/$clean")
    }

    /**
     * True when [other] is this location or below it, on the same server: same scheme, host (any
     * case) and port (a default port equals none). Paths compare case-sensitively except on SMB.
     */
    fun covers(other: NetworkLocation): Boolean {
        if (!scheme.equals(other.scheme, ignoreCase = true)) return false
        if (!host.equals(other.host, ignoreCase = true) || effectivePort != other.effectivePort) return false
        val ignoreCase = scheme.equals("smb", ignoreCase = true)
        return path.isEmpty() || other.path.equals(path, ignoreCase) || other.path.startsWith("$path/", ignoreCase)
    }

    /** "ftp://host:2121/My%20Movies/Action", every path segment percent-encoded. */
    fun toUri(): String {
        val uri = StringBuilder(scheme).append("://")
        // IPv6 literals need brackets so their colons are not read as a port.
        if (':' in host) uri.append('[').append(host).append(']') else uri.append(host)
        port?.let { uri.append(':').append(it) }
        path.split('/').filter { it.isNotEmpty() }.forEach { uri.append('/').append(encodeSegment(it)) }
        return uri.toString()
    }

    companion object {
        private const val HEX = "0123456789ABCDEF"

        /** Ports the URI leaves out: 21 ftp/ftpes, 990 ftps, 22 sftp, 80 http, 443 https, 445 smb. */
        fun defaultPort(scheme: String): Int? = when (scheme.lowercase(Locale.ROOT)) {
            "ftp", "ftpes" -> 21
            "ftps" -> 990
            "sftp" -> 22
            "http" -> 80
            "https" -> 443
            "smb" -> 445
            else -> null
        }

        /**
         * Parses "scheme://[user[:password]@]host[:port][/path][?query][#fragment]", encoded or not.
         * Null without a scheme and host, or with a port outside 1..65535.
         */
        fun parse(uri: String): NetworkLocation? {
            val trimmed = uri.trim()
            val separator = trimmed.indexOf("://")
            if (separator <= 0) return null
            val scheme = trimmed.substring(0, separator).lowercase(Locale.ROOT)
            if (!scheme.all { it.isLetterOrDigit() || it in "+-." }) return null
            val rest = trimmed.substring(separator + 3)
            val end = rest.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) rest.length else it }
            val body = rest.substring(0, end)
            val slash = body.indexOf('/')
            val authority = (if (slash < 0) body else body.substring(0, slash)).substringAfterLast('@')
            val (host, port) = parseAuthority(authority) ?: return null
            val path = if (slash < 0) "" else body.substring(slash + 1)
            return NetworkLocation(scheme, host, port, path.split('/').filter { it.isNotEmpty() }.joinToString("/") { decode(it) })
        }

        /** "host", "host:21", "[::1]:21" → host and port; null for an empty host or a bad port. */
        internal fun parseAuthority(authority: String): Pair<String, Int?>? {
            val host: String
            val portText: String?
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 0) return null
                host = authority.substring(1, close)
                val after = authority.substring(close + 1)
                portText = when {
                    after.isEmpty() -> null
                    after.startsWith(":") -> after.substring(1)
                    else -> return null
                }
            } else if (authority.count { it == ':' } == 1) {
                host = authority.substringBefore(':')
                portText = authority.substringAfter(':')
            } else {
                // No colon, or a bare IPv6 literal without brackets (and so without a port).
                host = authority
                portText = null
            }
            val decodedHost = decode(host).trim()
            if (decodedHost.isEmpty() || decodedHost.any { it.isWhitespace() || it in "/\\@" }) return null
            val port = when {
                portText.isNullOrEmpty() -> null
                else -> portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            }
            return decodedHost to port
        }

        /** Percent-encodes one path segment as UTF-8; RFC 3986 unreserved characters stay as they are. */
        fun encodeSegment(segment: String): String {
            val out = StringBuilder(segment.length)
            for (byte in segment.toByteArray(StandardCharsets.UTF_8)) {
                val b = byte.toInt() and 0xFF
                val c = b.toChar()
                if (b < 0x80 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~")) {
                    out.append(c)
                } else {
                    out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
                }
            }
            return out.toString()
        }

        /** Decodes valid %XX escapes as UTF-8 and leaves anything else (a raw '%', spaces) untouched. */
        fun decode(text: String): String {
            if ('%' !in text) return text
            val out = StringBuilder(text.length)
            val bytes = ByteArrayOutputStream()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                val high = if (c == '%' && i + 2 < text.length) hexValue(text[i + 1]) else -1
                val low = if (high >= 0) hexValue(text[i + 2]) else -1
                if (high >= 0 && low >= 0) {
                    bytes.write(high * 16 + low)
                    i += 3
                    continue
                }
                flushUtf8(bytes, out)
                out.append(c)
                i++
            }
            flushUtf8(bytes, out)
            return out.toString()
        }

        private fun flushUtf8(bytes: ByteArrayOutputStream, out: StringBuilder) {
            if (bytes.size() == 0) return
            out.append(String(bytes.toByteArray(), StandardCharsets.UTF_8))
            bytes.reset()
        }

        private fun hexValue(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
    }
}

/**
 * What the add/edit dialog collects for an FTP, SFTP, NFS, UPnP, WebDAV, Jellyfin, Emby or Plex
 * source (SMB keeps [LibraryRepository.saveSmbServer]). [NetworkSources.toSource] checks and
 * normalises it.
 */
data class NetworkSourceInput(
    val type: SourceType,
    /** Blank: named after the server (and folder). */
    val name: String = "",
    /**
     * The address as typed: "nas.local/videos", "ftp://nas:2121/videos", "https://dav.example.com/remote.php/dav",
     * "jellyfin.local", "http://192.168.1.5:32400"; for UPnP the device's description URL or its UDN
     * ("uuid:…"). User info in it ("ftp://bob:secret@nas") becomes [username]/[password] when those are blank.
     */
    val address: String,
    /** Overrides the address's port. */
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    /** An access token the user pasted (Plex X-Plex-Token) or a sign-in returned. */
    val token: String? = null,
    val userId: String? = null,
    val serverId: String? = null,
    /** Server libraries to scan; empty = all of them. */
    val libraryIds: List<String> = emptyList(),
)

/** Address rules of the network source types (everything but SMB, which has core SmbUris). */
object NetworkSources {
    /** The types [toSource] and [LibraryRepository.saveNetworkSource] take. */
    val TYPES: Set<SourceType> = setOf(
        SourceType.FTP,
        SourceType.SFTP,
        SourceType.NFS,
        SourceType.UPNP,
        SourceType.WEBDAV,
        SourceType.JELLYFIN,
        SourceType.EMBY,
        SourceType.PLEX,
    )

    private const val HTTPS = "https"
    private const val JELLYFIN_PORT = 8096
    private const val PLEX_PORT = 32400
    private const val UDN_PREFIX = "uuid:"

    /** Schemes typed for WebDAV that libVLC knows as plain http(s). */
    private val SCHEME_ALIASES = mapOf("dav" to "http", "webdav" to "http", "davs" to "https", "webdavs" to "https")

    /**
     * URI schemes a source of [type] may use; the first is the one an address without scheme gets
     * (except a Jellyfin or Emby server on the internet: https, see [toSource]).
     */
    fun schemes(type: SourceType): List<String> = when (type) {
        SourceType.FTP -> listOf("ftp", "ftps", "ftpes")
        SourceType.SFTP -> listOf("sftp")
        SourceType.NFS -> listOf("nfs")
        // Credentials go along with every request: https unless the address says otherwise.
        SourceType.WEBDAV -> listOf("https", "http")
        SourceType.UPNP, SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX -> listOf("http", "https")
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM -> emptyList()
    }

    /** The port a media server listens on out of the box; given to an address typed without scheme and port. */
    fun defaultServerPort(type: SourceType): Int? = when (type) {
        SourceType.JELLYFIN, SourceType.EMBY -> JELLYFIN_PORT
        SourceType.PLEX -> PLEX_PORT
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP,
        SourceType.SFTP, SourceType.NFS, SourceType.UPNP, SourceType.WEBDAV,
        -> null
    }

    /** "FTP", "Jellyfin", … for messages and default names. */
    fun label(type: SourceType): String = when (type) {
        SourceType.DEVICE -> "Device"
        SourceType.FOLDER -> "Folder"
        SourceType.SMB -> "SMB"
        SourceType.STREAM -> "Stream"
        SourceType.FTP -> "FTP"
        SourceType.SFTP -> "SFTP"
        SourceType.NFS -> "NFS"
        SourceType.UPNP -> "UPnP"
        SourceType.WEBDAV -> "WebDAV"
        SourceType.JELLYFIN -> "Jellyfin"
        SourceType.EMBY -> "Emby"
        SourceType.PLEX -> "Plex"
    }

    /**
     * The source [input] describes, checked and normalised but not saved (id 0, addedAt 0): the
     * location as an encoded URI without user info or default port, [SourceEntity.port] in step
     * with it, blank fields as null. Throws IllegalArgumentException with a message for the user.
     */
    fun toSource(input: NetworkSourceInput): SourceEntity {
        val type = input.type
        require(type in TYPES) { "${label(type)} sources are not added with a network address" }
        val address = input.address.trim()
        require(address.isNotEmpty()) { addressPrompt(type) }
        input.port?.let { require(it in 1..65535) { "The port must be between 1 and 65535" } }

        val username = input.username.clean()
        val password = input.password?.takeIf { it.isNotEmpty() }
        val token = input.token.clean()
        val libraries = input.libraryIds.mapNotNull { it.clean() }.distinct()
        require(libraries.none { ',' in it }) { "A library id can't contain a comma" }
        val base = SourceEntity(
            type = type,
            name = input.name.trim(),
            location = "",
            username = username,
            password = password,
            domain = input.domain.clean(),
            token = token,
            userId = input.userId.clean(),
            serverId = input.serverId.clean(),
            libraryIds = libraries.joinToString(",").ifEmpty { null },
        )

        if (type == SourceType.UPNP && address.startsWith(UDN_PREFIX, ignoreCase = true)) {
            val udn = UDN_PREFIX + address.substring(UDN_PREFIX.length).trim()
            require(udn.length > UDN_PREFIX.length && udn.none { it.isWhitespace() }) { addressPrompt(type) }
            return base.copy(
                name = base.name.ifEmpty { label(type) },
                location = udn,
                serverId = base.serverId ?: udn,
            )
        }

        val separator = address.indexOf("://")
        val typedScheme = if (separator > 0) address.substring(0, separator).lowercase(Locale.ROOT) else null
        val schemes = schemes(type)
        val namedScheme = typedScheme?.let { SCHEME_ALIASES[it] ?: it }
        require(namedScheme == null || namedScheme in schemes) {
            "${label(type)} addresses start with " + schemes.joinToString(" or ") { "$it://" }
        }
        val rest = if (separator > 0) address.substring(separator + 3) else address
        val end = rest.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) rest.length else it }
        val body = rest.substring(0, end)
        val slash = body.indexOf('/')
        val authority = if (slash < 0) body else body.substring(0, slash)
        val at = authority.lastIndexOf('@')
        val (host, addressPort) = NetworkLocation.parseAuthority(authority.substring(at + 1))
            ?: throw IllegalArgumentException(authorityProblem(authority.substring(at + 1)))
        val path = if (slash < 0) {
            ""
        } else {
            body.substring(slash + 1).split('/').filter { it.isNotEmpty() }.joinToString("/") { NetworkLocation.decode(it) }
        }
        // A Jellyfin or Emby server on the internet typed bare is asked over https (behind a proxy,
        // on 443): the password and token would cross the internet unencrypted on its own http port.
        val internetServer = namedScheme == null && (type == SourceType.JELLYFIN || type == SourceType.EMBY) &&
            !LocalAddresses.isLocal(host)
        val scheme = namedScheme ?: if (internetServer) HTTPS else schemes.first()
        val port = (input.port ?: addressPort ?: defaultServerPort(type)?.takeIf { typedScheme == null && !internetServer })
            ?.takeIf { it != NetworkLocation.defaultPort(scheme) }
        val location = NetworkLocation(scheme, host, port, path)
        // A description URL may need its query; the other types are base URIs without one.
        val query = if (type == SourceType.UPNP) rest.substring(end).substringBefore('#') else ""

        // Credentials typed into the address go to their columns, never into the stored location.
        val userInfo = if (at < 0) null else authority.substring(0, at)
        val infoUser = userInfo?.substringBefore(':')?.let { NetworkLocation.decode(it) }.clean()
        val infoPassword = userInfo?.takeIf { ':' in it }?.substringAfter(':')?.let { NetworkLocation.decode(it) }
            ?.takeIf { it.isNotEmpty() }
        val user = username ?: infoUser
        if (type == SourceType.JELLYFIN || type == SourceType.EMBY) {
            require(user != null || token != null) { "Enter the user name" }
        }
        val defaultName = if (type.isMediaServer || type == SourceType.UPNP || path.isEmpty()) host else "$host/$path"
        return base.copy(
            name = base.name.ifEmpty { defaultName },
            location = location.toUri() + query,
            port = port,
            username = user,
            password = password ?: infoPassword,
        )
    }

    /**
     * A warning when [source] would send its login or token unencrypted over the internet: WebDAV,
     * Jellyfin, Emby or Plex over http://, FTP over plain ftp://, to a host that isn't on the local
     * network (see core LocalAddresses). Null when the connection is encrypted, the host is local,
     * or there is nothing secret to send.
     */
    fun cleartextWarning(source: SourceEntity): String? {
        val location = NetworkLocation.parse(source.location) ?: return null
        val login = !source.username.isNullOrEmpty()
        val token = !source.token.isNullOrEmpty()
        val http = location.scheme == "http"
        // What would go unencrypted, and the scheme that would encrypt it.
        val (secret, secure) = when (source.type) {
            SourceType.WEBDAV -> (if (http && login) "your password" else null) to "https://"
            SourceType.JELLYFIN, SourceType.EMBY -> when {
                http && login -> "your password and sign-in"
                http && token -> "your sign-in"
                else -> null
            } to "https://"
            SourceType.PLEX -> (if (http && token) "your Plex token" else null) to "https://"
            SourceType.FTP -> (if (location.scheme == "ftp" && login) "your password" else null) to "ftps:// or ftpes://"
            SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.SFTP, SourceType.NFS,
            SourceType.UPNP,
            -> return null
        }
        if (secret == null || LocalAddresses.isLocal(location.host)) return null
        return "Not encrypted: $secret would cross the internet to ${location.host} readable by anyone on the way. " +
            "Use $secure if the server supports it."
    }

    /** [SourceEntity.libraryIds] as a list; empty = every library. */
    fun libraryIds(source: SourceEntity): List<String> =
        source.libraryIds.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun addressPrompt(type: SourceType): String = when (type) {
        SourceType.UPNP -> "Enter the device's description URL or UDN"
        SourceType.JELLYFIN, SourceType.EMBY, SourceType.PLEX -> "Enter the server address"
        SourceType.DEVICE, SourceType.FOLDER, SourceType.SMB, SourceType.STREAM, SourceType.FTP,
        SourceType.SFTP, SourceType.NFS, SourceType.WEBDAV,
        -> "Enter the server name or address"
    }

    private fun authorityProblem(authority: String): String {
        val portText = when {
            authority.startsWith("[") -> authority.substringAfter("]", "").removePrefix(":")
            authority.count { it == ':' } == 1 -> authority.substringAfter(':')
            else -> ""
        }
        return if (portText.isNotEmpty() && portText.toIntOrNull()?.takeIf { it in 1..65535 } == null) {
            "The port must be between 1 and 65535"
        } else {
            "Enter the server name or address"
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
