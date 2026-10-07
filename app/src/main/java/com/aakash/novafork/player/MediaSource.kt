package com.aakash.novafork.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.aakash.novafork.data.HwDecoding
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SmbCredentials
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media

/** Where a playable URI lives: decides how libVLC opens it and how a failure is explained. */
internal enum class UriKind {
    /** content:// – played through a file descriptor from the content resolver. */
    CONTENT,

    /** file:// or an absolute path. */
    FILE,

    /** smb:// – libVLC's smb2 module, credentials passed as media options. */
    SMB,

    /** http(s), rtsp, rtmp, udp, mms … */
    NETWORK,
    ;

    val isLocal: Boolean get() = this == CONTENT || this == FILE

    companion object {
        fun of(uri: String): UriKind {
            if (uri.startsWith("/")) return FILE
            return when (uri.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
                "content" -> CONTENT
                "file", "" -> FILE
                "smb" -> SMB
                else -> NETWORK
            }
        }
    }
}

/** A media ready for MediaPlayer.setMedia, plus the descriptor that has to stay open while it plays. */
internal class OpenedMedia(val media: Media, val descriptor: ParcelFileDescriptor?) {
    /** Releases a media that never reached the player. */
    fun discard() {
        media.release()
        descriptor.closeQuietly()
    }
}

internal fun ParcelFileDescriptor?.closeQuietly() {
    if (this == null) return
    try {
        close()
    } catch (e: IOException) {
        Log.w("MediaSource", "Couldn't close a file descriptor", e)
    }
}

internal object MediaSource {
    private const val NETWORK_CACHING_MS = 1500

    /**
     * Builds the libVLC media for [uri] with the playback settings applied.
     * Blocking: a content provider may fetch the whole file before it hands out a descriptor,
     * so call this off the main thread.
     */
    fun open(
        context: Context,
        libVLC: LibVLC,
        uri: String,
        settings: Settings,
        smbCredentials: SmbCredentials?,
    ): OpenedMedia {
        val media = when (UriKind.of(uri)) {
            UriKind.CONTENT -> return openContent(context, libVLC, uri, settings)
            UriKind.FILE -> Media(libVLC, localPath(uri))
            UriKind.SMB -> Media(libVLC, Uri.parse(uri)).apply {
                smbCredentials?.username?.takeIf { it.isNotBlank() }?.let { addOption(":smb-user=$it") }
                smbCredentials?.password?.takeIf { it.isNotBlank() }?.let { addOption(":smb-pwd=$it") }
                smbCredentials?.domain?.takeIf { it.isNotBlank() }?.let { addOption(":smb-domain=$it") }
                addOption(":network-caching=$NETWORK_CACHING_MS")
            }
            UriKind.NETWORK -> Media(libVLC, Uri.parse(uri)).apply {
                addOption(":network-caching=$NETWORK_CACHING_MS")
            }
        }
        applySettings(media, settings)
        return OpenedMedia(media, descriptor = null)
    }

    private fun openContent(context: Context, libVLC: LibVLC, uri: String, settings: Settings): OpenedMedia {
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: throw FileNotFoundException("The provider returned no file descriptor")
        return try {
            val media = Media(libVLC, descriptor.fileDescriptor)
            applySettings(media, settings)
            OpenedMedia(media, descriptor)
        } catch (e: RuntimeException) {
            descriptor.closeQuietly()
            throw e
        }
    }

    private fun applySettings(media: Media, settings: Settings) {
        when (settings.hardwareDecoding) {
            HwDecoding.AUTO -> media.setHWDecoderEnabled(true, false)
            HwDecoding.DISABLED -> media.setHWDecoderEnabled(false, false)
            HwDecoding.FORCED -> media.setHWDecoderEnabled(true, true)
        }
        val encoding = settings.subtitleEncoding.trim()
        if (encoding.isNotEmpty()) media.addOption(":subsdec-encoding=$encoding")
    }

    private fun localPath(uri: String): String =
        if (uri.startsWith("/")) uri else Uri.parse(uri).path ?: uri.removePrefix("file://")
}
