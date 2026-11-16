package com.aakash.novafork.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityBadgesTest {
    @Test
    fun fourKFromWidthOrHeight() {
        assertEquals(listOf("4K"), QualityBadges.of(3840, 2160))
        // A cropped scope film keeps its 4K width.
        assertEquals(listOf("4K"), QualityBadges.of(3840, 1600))
        assertEquals(listOf("4K"), QualityBadges.of(3200, 1000))
        assertEquals(listOf("4K"), QualityBadges.of(1000, 2000))
    }

    @Test
    fun hdOnlyWhenNotFourK() {
        assertEquals(listOf("HD"), QualityBadges.of(1920, 1080))
        assertEquals(listOf("HD"), QualityBadges.of(1280, 720))
        assertEquals(listOf("HD"), QualityBadges.of(1920, 800))
        assertEquals(listOf("HD"), QualityBadges.of(720, 1280))
    }

    @Test
    fun nothingForSmallOrUnknownSizes() {
        assertEquals(emptyList<String>(), QualityBadges.of(640, 480))
        assertEquals(emptyList<String>(), QualityBadges.of(null, null))
        assertEquals(emptyList<String>(), QualityBadges.of(0, 0))
        assertEquals(emptyList<String>(), QualityBadges.of(1279, 719))
    }

    @Test
    fun otherBadgesOnlyFromKnownTraits() {
        val all = StreamTraits(hdr = true, dolbyVision = true, channels = 8, atmos = true)
        assertEquals(listOf("4K", "Dolby Vision", "HDR", "7.1", "Atmos"), QualityBadges.of(3840, 2160, all))
        assertEquals(listOf("HD", "5.1"), QualityBadges.of(1920, 1080, StreamTraits(channels = 6)))
        assertEquals(listOf("HD"), QualityBadges.of(1920, 1080, StreamTraits(channels = 2)))
        assertEquals(listOf("HD"), QualityBadges.of(1920, 1080, StreamTraits(channels = null)))
        assertEquals(listOf("HDR"), QualityBadges.of(null, null, StreamTraits(hdr = true)))
    }
}
