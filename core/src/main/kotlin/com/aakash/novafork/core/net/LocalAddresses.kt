package com.aakash.novafork.core.net

import java.net.InetAddress
import java.util.Locale

/**
 * Tells hosts on the local network (the home LAN, a VPN's addresses, this device) from internet
 * ones by their name or IP literal alone, never with a DNS lookup. A login sent unencrypted to a
 * local host stays in the home; to an internet host it crosses networks anyone may read.
 */
object LocalAddresses {
    /** Name suffixes only local networks use ("nas.local", "media.lan", "box.fritz.box"…). */
    private val LOCAL_SUFFIXES = listOf(
        "local",
        "lan",
        "home",
        "home.arpa",
        "internal",
        "intranet",
        "localdomain",
        "localhost",
        "fritz.box",
    )

    private const val IPV4_BYTES = 4
    private const val IPV6_GROUPS = 8
    private const val CGNAT_FIRST = 100
    private const val CGNAT_SECOND_MASK = 0xC0
    private const val CGNAT_SECOND = 0x40
    private const val ULA_MASK = 0xFE
    private const val ULA_PREFIX = 0xFC

    /**
     * True for a host on the local network: a private, loopback or link-local IPv4 address, one
     * of the shared range 100.64.0.0/10 (carrier NAT, and VPNs such as Tailscale), a loopback,
     * link-local or unique local (fc00::/7) IPv6 address, a name without a dot ("nas") or a name
     * under a local-network suffix ("jellyfin.local"). False for any other name or address, and
     * for blank text. IPv6 literals may come in brackets and with a zone ("fe80::1%wlan0").
     */
    fun isLocal(host: String): Boolean {
        val name = host.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase(Locale.ROOT)
        if (name.isEmpty()) return false
        val bytes = if (':' in name) ipv6Bytes(name) ?: return false else ipv4Bytes(name)
        if (bytes != null) return isLocalAddress(bytes)
        if ('.' !in name) return true
        return LOCAL_SUFFIXES.any { name == it || name.endsWith(".$it") }
    }

    private fun isLocalAddress(bytes: ByteArray): Boolean {
        // Built from the bytes: no name service is asked. An IPv4-mapped IPv6 address comes back as IPv4.
        val address = InetAddress.getByAddress(bytes)
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isAnyLocalAddress) {
            return true
        }
        val raw = address.address
        val first = raw[0].toInt() and 0xFF
        return if (raw.size == IPV4_BYTES) {
            first == CGNAT_FIRST && (raw[1].toInt() and CGNAT_SECOND_MASK) == CGNAT_SECOND
        } else {
            (first and ULA_MASK) == ULA_PREFIX
        }
    }

    /** "192.168.1.5" as its 4 bytes; null for anything but four decimal numbers 0..255. */
    private fun ipv4Bytes(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != IPV4_BYTES) return null
        val bytes = ByteArray(IPV4_BYTES)
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            val value = part.toInt()
            if (value > 255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }

    /** An IPv6 literal ("fd12::1", "::ffff:192.168.1.5", "fe80::1%wlan0") as its 16 bytes; null when it isn't one. */
    private fun ipv6Bytes(text: String): ByteArray? {
        var value = text.substringBefore('%')
        // A trailing IPv4 part ("::ffff:192.168.1.5") becomes the two groups it stands for.
        val last = value.substringAfterLast(':')
        if ('.' in last) {
            val v4 = ipv4Bytes(last) ?: return null
            val high = ((v4[0].toInt() and 0xFF) shl 8) or (v4[1].toInt() and 0xFF)
            val low = ((v4[2].toInt() and 0xFF) shl 8) or (v4[3].toInt() and 0xFF)
            value = value.dropLast(last.length) + Integer.toHexString(high) + ":" + Integer.toHexString(low)
        }
        val halves = value.split("::")
        if (halves.size > 2) return null
        val head = groups(halves[0]) ?: return null
        val groups = if (halves.size == 2) {
            val tail = groups(halves[1]) ?: return null
            val missing = IPV6_GROUPS - head.size - tail.size
            if (missing < 1) return null
            head + List(missing) { 0 } + tail
        } else {
            head
        }
        if (groups.size != IPV6_GROUPS) return null
        val bytes = ByteArray(IPV6_GROUPS * 2)
        for ((i, group) in groups.withIndex()) {
            bytes[2 * i] = (group shr 8).toByte()
            bytes[2 * i + 1] = group.toByte()
        }
        return bytes
    }

    /** "fe80:0:1" as its groups; empty for ""; null when a group isn't 1 to 4 hex digits. */
    private fun groups(text: String): List<Int>? {
        if (text.isEmpty()) return emptyList()
        return text.split(':').map { group ->
            if (group.isEmpty() || group.length > 4 || !group.all { it in '0'..'9' || it in 'a'..'f' }) return null
            group.toInt(16)
        }
    }
}
