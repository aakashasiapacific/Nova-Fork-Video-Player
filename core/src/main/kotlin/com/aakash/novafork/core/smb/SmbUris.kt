package com.aakash.novafork.core.smb

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** A decoded location on an SMB share. [path] uses '/' separators, no leading or trailing slash. */
data class SmbLocation(val host: String, val port: Int? = null, val share: String, val path: String = "") {
    val name: String get() = path.substringAfterLast('/').ifEmpty { share }
    val parentPath: String get() = if ('/' in path) path.substringBeforeLast('/') else ""
    fun child(name: String): SmbLocation = copy(path = if (path.isEmpty()) name else "$path/$name")
}

/**
 * smb:// URIs as libVLC expects them: "smb://host[:port]/share/dir/file%20name.mkv".
 * Every path segment is percent-encoded (RFC 3986 unreserved characters stay as-is);
 * credentials are never put in the URI (libVLC gets them as :smb-user / :smb-pwd / :smb-domain options).
 */
object SmbUris {
    private const val SCHEME = "smb://"
    private const val HEX = "0123456789ABCDEF"

    fun build(location: SmbLocation): String {
        val host = location.host.trim().removePrefix("[").removeSuffix("]")
        val uri = StringBuilder(SCHEME)
        // IPv6 literals need brackets so their colons are not read as a port.
        if (':' in host) uri.append('[').append(host).append(']') else uri.append(host)
        location.port?.let { uri.append(':').append(it) }
        uri.append('/').append(encodeSegment(location.share.trim('/')))
        location.path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
            uri.append('/').append(encodeSegment(segment))
        }
        return uri.toString()
    }

    fun build(host: String, share: String, path: String = "", port: Int? = null): String =
        build(SmbLocation(host = host, port = port, share = share, path = path.trim('/')))

    /** Parses an smb:// URI (encoded or not, with or without user info). Null if it is not smb://host/share… */
    fun parse(uri: String): SmbLocation? {
        val trimmed = uri.trim()
        if (!trimmed.startsWith(SCHEME, ignoreCase = true)) return null
        val rest = trimmed.substring(SCHEME.length)
        val slash = rest.indexOf('/')
        val authority = (if (slash < 0) rest else rest.substring(0, slash)).substringAfterLast('@')
        val (host, port) = parseAuthority(authority) ?: return null
        if (slash < 0) return null
        val segments = rest.substring(slash + 1).split('/').filter { it.isNotEmpty() }.map(::decode)
        val share = segments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return SmbLocation(host = host, port = port, share = share, path = segments.drop(1).joinToString("/"))
    }

    private fun parseAuthority(authority: String): Pair<String, Int?>? {
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
        if (decodedHost.isEmpty()) return null
        val port = when {
            portText.isNullOrEmpty() -> null
            else -> portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        }
        return decodedHost to port
    }

    private fun encodeSegment(segment: String): String {
        val out = StringBuilder(segment.length)
        for (byte in segment.toByteArray(StandardCharsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c.isAsciiLetterOrDigit() || c in "-._~")) {
                out.append(c)
            } else {
                out.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return out.toString()
    }

    /** Decodes valid %XX escapes as UTF-8 and leaves anything else (a raw '%', spaces, '#') untouched. */
    private fun decode(text: String): String {
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

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
