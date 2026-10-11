package com.aakash.novafork

import com.aakash.novafork.data.LayoutMode
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.PhoneDimens
import com.aakash.novafork.ui.adaptive.TabletDimens
import com.aakash.novafork.ui.adaptive.deviceLayoutFor
import com.aakash.novafork.ui.adaptive.dimensFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLayoutTest {

    private data class Case(val name: String, val smallestWidthDp: Int, val widthDp: Int, val expected: DeviceLayout)

    @Test
    fun automaticLayoutFollowsSmallestWidthThenWidth() {
        val cases = listOf(
            Case("compact phone, portrait", 360, 360, DeviceLayout.PHONE),
            Case("phone, portrait", 411, 411, DeviceLayout.PHONE),
            // Orientation never turns a phone into a tablet, however wide it gets.
            Case("phone, landscape", 411, 891, DeviceLayout.PHONE),
            Case("large phone, landscape", 599, 1280, DeviceLayout.PHONE),
            Case("small tablet at the threshold", 600, 600, DeviceLayout.TABLET_COMPACT),
            Case("foldable, unfolded", 673, 841, DeviceLayout.TABLET_EXPANDED),
            Case("tablet, portrait", 800, 800, DeviceLayout.TABLET_COMPACT),
            Case("just below expanded", 800, 839, DeviceLayout.TABLET_COMPACT),
            Case("expanded threshold", 800, 840, DeviceLayout.TABLET_EXPANDED),
            Case("tablet, landscape", 800, 1280, DeviceLayout.TABLET_EXPANDED),
        )
        for (case in cases) {
            assertEquals(case.name, case.expected, deviceLayoutFor(case.smallestWidthDp, case.widthDp))
            assertEquals(case.name, case.expected, deviceLayoutFor(case.smallestWidthDp, case.widthDp, LayoutMode.AUTO))
        }
    }

    @Test
    fun phoneModeForcesThePhoneUiOnTablets() {
        assertEquals(DeviceLayout.PHONE, deviceLayoutFor(800, 1280, LayoutMode.PHONE))
        assertEquals(DeviceLayout.PHONE, deviceLayoutFor(800, 800, LayoutMode.PHONE))
        assertEquals(DeviceLayout.PHONE, deviceLayoutFor(411, 411, LayoutMode.PHONE))
    }

    @Test
    fun tabletModeStillUsesWidthForCompactOrExpanded() {
        assertEquals(DeviceLayout.TABLET_COMPACT, deviceLayoutFor(411, 411, LayoutMode.TABLET))
        assertEquals(DeviceLayout.TABLET_EXPANDED, deviceLayoutFor(411, 891, LayoutMode.TABLET))
        assertEquals(DeviceLayout.TABLET_EXPANDED, deviceLayoutFor(800, 1280, LayoutMode.TABLET))
    }

    @Test
    fun layoutFlags() {
        assertFalse(DeviceLayout.PHONE.isTablet)
        assertFalse(DeviceLayout.PHONE.isExpanded)
        assertTrue(DeviceLayout.TABLET_COMPACT.isTablet)
        assertFalse(DeviceLayout.TABLET_COMPACT.isExpanded)
        assertTrue(DeviceLayout.TABLET_EXPANDED.isTablet)
        assertTrue(DeviceLayout.TABLET_EXPANDED.isExpanded)
    }

    @Test
    fun eachFamilyGetsItsOwnDimens() {
        assertSame(PhoneDimens, dimensFor(DeviceLayout.PHONE))
        assertSame(TabletDimens, dimensFor(DeviceLayout.TABLET_COMPACT))
        assertSame(TabletDimens, dimensFor(DeviceLayout.TABLET_EXPANDED))
        assertTrue(TabletDimens.posterWidth > PhoneDimens.posterWidth)
        assertTrue(TabletDimens.gutter > PhoneDimens.gutter)
    }
}
