package com.aakash.novafork.data

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Wake-on-LAN: the "magic packet" (six 0xFF bytes, then the MAC address sixteen times) sent as a
 * broadcast, which wakes a sleeping NAS before the library visits it.
 */
object WakeOnLan {
    /** The six bytes of a MAC written as "AA:BB:CC:DD:EE:FF", "AA-BB-…" or "AABBCCDDEEFF"; null when it isn't one. */
    fun parseMac(text: String): ByteArray? {
        val hex = text.filter { it.isLetterOrDigit() }
        if (hex.length != MAC_HEX_LENGTH || hex.any { it.digitToIntOrNull(HEX_RADIX) == null }) return null
        return ByteArray(MAC_BYTES) { hex.substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() }
    }

    fun packet(mac: ByteArray): ByteArray = ByteArray(MAC_BYTES) { 0xFF.toByte() } + ByteArray(0).let {
        var all = ByteArray(0)
        repeat(MAC_REPEATS) { all += mac }
        all
    }

    /** Sends the packet; false when [macText] isn't a MAC or the network refuses. Blocking: call off the main thread. */
    fun wake(macText: String): Boolean {
        val mac = parseMac(macText) ?: return false
        val data = packet(mac)
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (port in PORTS) {
                    socket.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), port))
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private val PORTS = intArrayOf(9, 7)
    private const val MAC_BYTES = 6
    private const val MAC_HEX_LENGTH = 12
    private const val MAC_REPEATS = 16
    private const val HEX_RADIX = 16
}
