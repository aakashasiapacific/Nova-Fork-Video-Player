package com.aakash.novafork

import com.aakash.novafork.ui.SearchPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPhraseTest {
    @Test
    fun comediesUnderTwoHours() {
        val p = requireNotNull(SearchPhrase.parse("comedies under 2 hours"))
        assertEquals(setOf("Comedy"), p.genres)
        assertEquals(120, p.maxMinutes)
        assertTrue(p.lengthMatches(100 * 60_000L))
        assertFalse(p.lengthMatches(130 * 60_000L))
        assertFalse(p.lengthMatches(null))
    }

    @Test
    fun stateSizeAndPeriod() {
        val p = requireNotNull(SearchPhrase.parse("unwatched 4k sci-fi from 2019"))
        assertTrue(p.unwatched)
        assertEquals(setOf("Science Fiction"), p.genres)
        assertTrue(p.sizeMatches(2160))
        assertFalse(p.sizeMatches(1080))
        assertTrue(p.yearMatches(2021))
        assertFalse(p.yearMatches(2015))
    }

    @Test
    fun decadesAndLeftoverWords() {
        val p = requireNotNull(SearchPhrase.parse("90s action nolan"))
        assertEquals(1990, p.yearFrom)
        assertEquals(1999, p.yearTo)
        assertEquals(listOf("nolan"), p.words)
    }

    @Test
    fun ordinarySearchesStayOrdinary() {
        assertNull(SearchPhrase.parse("dune"))
        assertNull(SearchPhrase.parse("comedy"))
        assertNull(SearchPhrase.parse("the matrix"))
        assertNotNull(SearchPhrase.parse("comedies"))
    }
}

class WakeOnLanTest {
    @Test
    fun aMacMakesA102ByteMagicPacket() {
        val mac = requireNotNull(com.aakash.novafork.data.WakeOnLan.parseMac("aa:bb:cc:dd:ee:0f"))
        assertEquals(6, mac.size)
        val packet = com.aakash.novafork.data.WakeOnLan.packet(mac)
        assertEquals(102, packet.size)
        assertEquals(0xFF.toByte(), packet[0])
        assertEquals(0x0F.toByte(), packet[101])
    }

    @Test
    fun otherTextIsNotAMac() {
        assertNull(com.aakash.novafork.data.WakeOnLan.parseMac("hello"))
        assertNull(com.aakash.novafork.data.WakeOnLan.parseMac("AA:BB:CC:DD:EE"))
        assertNull(com.aakash.novafork.data.WakeOnLan.parseMac("GG:BB:CC:DD:EE:FF"))
    }
}
