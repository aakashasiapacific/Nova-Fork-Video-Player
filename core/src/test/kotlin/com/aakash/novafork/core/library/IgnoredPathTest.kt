package com.aakash.novafork.core.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoredPathTest {
    private fun ignored(path: String) = VideoFiles.isIgnoredPath(path.split('/'))

    @Test
    fun messagingAppVideosUnderAndroidMediaAreKept() {
        assertFalse(ignored("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"))
        assertFalse(ignored("Android/media/org.telegram.messenger/Telegram/Telegram Video"))
        assertFalse(ignored("android/Media/com.whatsapp/WhatsApp/Media/WhatsApp Video/"))
    }

    @Test
    fun theAndroidFolderItselfCanBeWalked() {
        assertFalse(ignored("Android"))
        assertFalse(ignored("Android/media"))
    }

    @Test
    fun restOfAndroidStaysSkipped() {
        assertTrue(ignored("Android/data/com.example/files"))
        assertTrue(ignored("Android/obb/com.example"))
    }

    @Test
    fun hiddenAndSampleFoldersStaySkippedEverywhere() {
        assertTrue(ignored("Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
        assertTrue(ignored("Movies/Inception (2010)/Sample"))
        assertTrue(ignored("DCIM/.thumbnails"))
        assertTrue(ignored("Movies/Android"))
    }

    @Test
    fun cameraAndOrdinaryFoldersAreKept() {
        assertFalse(ignored("DCIM/Camera"))
        assertFalse(ignored("DCIM/Screen recordings"))
        assertFalse(ignored("Movies/Screen Recordings"))
        assertFalse(ignored(""))
    }
}
