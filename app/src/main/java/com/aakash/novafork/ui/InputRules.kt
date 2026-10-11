package com.aakash.novafork.ui

import com.aakash.novafork.core.smb.SmbUris
import com.aakash.novafork.ui.model.SmbForm

/** Validation for what people type into the stream and network share dialogs. Pure Kotlin. */
object InputRules {
    /** Schemes libVLC can open as a network stream. */
    val STREAM_SCHEMES: Set<String> = setOf(
        "http", "https", "rtsp", "rtsps", "rtmp", "rtp", "udp", "mms", "mmsh", "smb", "ftp", "sftp",
    )

    const val STREAM_URL_ERROR = "That doesn't look like a stream URL"

    /** True for "scheme://something" with a scheme from [STREAM_SCHEMES]. */
    fun isStreamUrl(url: String): Boolean {
        val text = url.trim()
        val separator = text.indexOf("://")
        if (separator <= 0) return false
        val scheme = text.substring(0, separator).lowercase()
        return scheme in STREAM_SCHEMES && text.length > separator + 3
    }

    /**
     * Accepts "smb://host/share/path" or "\\host\share\path" typed into the server field and spreads
     * it over the form's fields, so pasting an address from a file manager just works.
     */
    fun normalizeSmb(form: SmbForm): SmbForm {
        val host = form.host.trim().trimEnd('/', '\\')
        val address = when {
            host.startsWith("smb://", ignoreCase = true) -> host
            host.startsWith("\\\\") -> "smb://" + host.removePrefix("\\\\").replace('\\', '/')
            '/' in host -> "smb://$host"
            else -> null
        }
        val base = form.copy(
            name = form.name.trim(),
            host = host,
            port = form.port.trim(),
            share = form.share.trim().trim('/', '\\'),
            path = form.path.trim().replace('\\', '/').trim('/'),
            username = form.username.trim(),
            domain = form.domain.trim(),
        )
        if (address == null) return base
        val location = SmbUris.parse(address)
            // "smb://nas" names a server without a share: keep just the server.
            ?: return base.copy(host = address.substringAfter("://").substringBefore('/'))
        return base.copy(
            host = location.host,
            port = base.port.ifEmpty { location.port?.toString().orEmpty() },
            share = base.share.ifEmpty { location.share },
            path = base.path.ifEmpty { location.path },
        )
    }

    /** Null when the form can be saved or tested, else what to fix. Expects a [normalizeSmb]ed form. */
    fun smbFormError(form: SmbForm): String? = when {
        form.host.isBlank() -> "Enter the server name or address"
        form.share.isBlank() -> "Enter the share name"
        form.port.isNotBlank() && smbPort(form) == null -> "The port must be a number from 1 to 65535"
        else -> null
    }

    /** The typed port, or null when it is blank or out of range. */
    fun smbPort(form: SmbForm): Int? = form.port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
}
