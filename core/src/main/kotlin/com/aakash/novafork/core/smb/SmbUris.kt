package com.aakash.novafork.core.smb

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
    fun build(location: SmbLocation): String {
        TODO("core agent")
    }

    fun build(host: String, share: String, path: String = "", port: Int? = null): String =
        build(SmbLocation(host = host, port = port, share = share, path = path.trim('/')))

    /** Parses an smb:// URI (encoded or not, with or without user info). Null if it is not smb://host/share… */
    fun parse(uri: String): SmbLocation? {
        TODO("core agent")
    }
}
