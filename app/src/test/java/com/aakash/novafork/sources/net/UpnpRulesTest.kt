package com.aakash.novafork.sources.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The MRLs libVLC's UPnP module takes and gives, and how listed items are told apart. */
class UpnpRulesTest {
    @Test
    fun browseMrlsReplaceTheSchemeAndCarryTheObjectId() {
        assertEquals(
            "upnp://192.168.1.5:8200/rootDesc.xml?ObjectID=0",
            UpnpRules.browseMrl("http://192.168.1.5:8200/rootDesc.xml", "0"),
        )
        assertEquals(
            "upnps://nas:5001/desc?dev=1&ObjectID=64%241%2412",
            UpnpRules.browseMrl("https://nas:5001/desc?dev=1", "64$1$12"),
        )
        assertNull(UpnpRules.browseMrl("uuid:4d696e69-444c-164e-9d41-b827eb000000", "0"))
    }

    @Test
    fun objectIdsAreReadBackFromListedContainers() {
        assertEquals("64$1$12", UpnpRules.objectId("upnp://192.168.1.5:8200/rootDesc.xml?ObjectID=64%241%2412"))
        assertEquals("2", UpnpRules.objectId("upnps://nas/desc?dev=1&ObjectID=2"))
        assertNull(UpnpRules.objectId("http://192.168.1.5:8200/MediaItems/22.mkv"))
    }

    @Test
    fun discoveredServersGiveTheirDescriptionUrl() {
        assertEquals(
            "http://192.168.1.5:8200/rootDesc.xml",
            UpnpRules.descriptionUrl("upnp://192.168.1.5:8200/rootDesc.xml?ObjectID=0"),
        )
        assertEquals("https://nas/desc?dev=1", UpnpRules.descriptionUrl("upnps://nas/desc?dev=1&ObjectID=0"))
        assertEquals("http://nas/desc?dev=1", UpnpRules.descriptionUrl("upnp://nas/desc?ObjectID=0&dev=1"))
        // SAT>IP servers come with their http location: not a media server to browse.
        assertNull(UpnpRules.descriptionUrl("http://192.168.1.9:8875/desc.xml"))
        assertTrue(UpnpRules.isUdn("uuid:4d696e69-444c-164e-9d41-b827eb000000"))
        assertFalse(UpnpRules.isUdn("http://nas/desc.xml"))
    }

    @Test
    fun videosAreToldFromMusicAndPhotos() {
        assertTrue(UpnpRules.isVideoItem("The.Matrix.1999.1080p", "http://h:8200/MediaItems/22.mkv", isAudio = false))
        assertTrue(UpnpRules.isVideoItem("Heat.mkv", "http://h/stream?id=4", isAudio = false))
        assertTrue(UpnpRules.isVideoItem("Severance S01E01", "http://h:9000/resource/7/MEDIA_ITEM/AVC_MKV", isAudio = false))
        assertFalse(UpnpRules.isVideoItem("Track 01", "http://h:8200/MediaItems/12.mp3", isAudio = false))
        assertFalse(UpnpRules.isVideoItem("IMG_0001", "http://h:8200/MediaItems/9.jpg", isAudio = false))
        assertFalse(UpnpRules.isVideoItem("Some song", "http://h/stream?id=5", isAudio = true))
    }

    @Test
    fun fileNamesGetTheStreamExtension() {
        assertEquals("The.Matrix.1999.mkv", UpnpRules.fileName("The.Matrix.1999", "http://h:8200/MediaItems/22.mkv"))
        assertEquals("Heat (1995).mp4", UpnpRules.fileName("Heat (1995).mp4", "http://h/x.mkv"))
        assertEquals("AC-DC Live", UpnpRules.fileName("AC/DC Live", "http://h/resource/7"))
        assertEquals("Untitled", UpnpRules.folderName("  "))
    }

    @Test
    fun scansSkipMusicAndPreferTheFolderView() {
        assertTrue(UpnpRules.isSkippedContainer("Music"))
        assertTrue(UpnpRules.isSkippedContainer(" Photos "))
        assertFalse(UpnpRules.isSkippedContainer("Music Videos"))
        assertFalse(UpnpRules.isSkippedContainer("Video"))
        assertEquals(1, UpnpRules.folderViewIndex(listOf("All Video", "Folders", "Recently Added")))
        assertEquals(0, UpnpRules.folderViewIndex(listOf("By Folder", "Genre")))
        assertNull(UpnpRules.folderViewIndex(listOf("Movies", "TV Shows")))
    }
}
