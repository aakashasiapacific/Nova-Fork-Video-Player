package com.aakash.novafork.core.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressesTest {
    @Test
    fun homeNetworkAddressesAreLocal() {
        val local = listOf(
            "192.168.1.5",
            "10.0.0.2",
            "172.16.0.1",
            "172.31.255.254",
            "127.0.0.1",
            "169.254.10.20",
            // Carrier NAT and VPNs (Tailscale hands out 100.64.0.0/10).
            "100.64.0.1",
            "100.101.102.103",
            "::1",
            "[::1]",
            "fe80::1",
            "fe80::1%wlan0",
            "fd12:3456:789a::5",
            "FC00::1",
            "::ffff:192.168.1.5",
            "nas",
            "localhost",
            "NAS.Local",
            "jellyfin.local.",
            "media.lan",
            "box.fritz.box",
            "nas.home.arpa",
            "plex.internal",
        )
        for (host in local) assertTrue(host, LocalAddresses.isLocal(host))
    }

    @Test
    fun internetAddressesAreNot() {
        val internet = listOf(
            "jellyfin.example.com",
            "cloud.example.org",
            "8.8.8.8",
            "203.0.113.5",
            "172.32.0.1",
            "100.128.0.1",
            "192.169.1.1",
            "2001:db8::1",
            "[2606:4700:4700::1111]",
            "::ffff:8.8.8.8",
            "notlocal.com",
            "local.example.com",
            "",
            "  ",
            // Not an address at all.
            "zz::zz",
            "1:2:3:4:5:6:7:8:9",
            "999.1.1.1",
        )
        for (host in internet) assertFalse(host, LocalAddresses.isLocal(host))
    }
}
