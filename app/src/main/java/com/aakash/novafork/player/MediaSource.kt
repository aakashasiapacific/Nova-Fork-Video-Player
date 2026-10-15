package com.aakash.novafork.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.aakash.novafork.NovaApp
import com.aakash.novafork.data.HwDecoding
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.SmbCredentials
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
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
    private const val TAG = "MediaSource"
    private const val NETWORK_CACHING_MS = 1500

    /** Videos (by URI) whose hardware decoding failed in this process; they play in software now. */
    private val softwareOnly: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    /**
     * The decoding [uri] gets: the setting, except that "Automatic" stays in software for a video
     * whose hardware decoding already failed in this process, so a fallback never repeats itself.
     */
    fun decodingFor(uri: String, setting: HwDecoding): HwDecoding =
        if (setting == HwDecoding.AUTO && uri in softwareOnly) HwDecoding.DISABLED else setting

    /** Plays [uri] with software decoding (under "Automatic") until the process ends. */
    fun rememberSoftwareDecoding(uri: String) {
        softwareOnly.add(uri)
    }

    /**
     * Builds the libVLC media for [uri] with the playback settings and [decoding] applied;
     * [forRenderer] = it will play on a cast device. A network video of a source type with
     * [com.aakash.novafork.data.PlaybackOptions] (FTP, SFTP, WebDAV, media servers) is opened at
     * the URI and with the media options its source adds (login, token). Blocking: a content
     * provider may fetch the whole file before it hands out a descriptor, so call this off the
     * main thread.
     */
    suspend fun open(
        context: Context,
        libVLC: LibVLC,
        uri: String,
        settings: Settings,
        decoding: HwDecoding,
        smbCredentials: SmbCredentials?,
        forRenderer: Boolean = false,
    ): OpenedMedia {
        val media = when (UriKind.of(uri)) {
            UriKind.CONTENT -> return openContent(context, libVLC, uri, settings, decoding, forRenderer)
            UriKind.FILE -> Media(libVLC, localPath(uri))
            UriKind.SMB -> Media(libVLC, Uri.parse(uri)).apply {
                smbCredentials?.username?.takeIf { it.isNotBlank() }?.let { addOption(":smb-user=$it") }
                smbCredentials?.password?.takeIf { it.isNotBlank() }?.let { addOption(":smb-pwd=$it") }
                smbCredentials?.domain?.takeIf { it.isNotBlank() }?.let { addOption(":smb-domain=$it") }
                addOption(":network-caching=$NETWORK_CACHING_MS")
            }
            UriKind.NETWORK -> {
                val network = networkPlayback(context, uri)
                Media(libVLC, Uri.parse(network.uri)).apply {
                    addOption(":network-caching=$NETWORK_CACHING_MS")
                    for (option in network.options) addOption(option)
                }
            }
        }
        applySettings(media, settings, decoding, forRenderer)
        return OpenedMedia(media, descriptor = null)
    }

    private fun openContent(
        context: Context,
        libVLC: LibVLC,
        uri: String,
        settings: Settings,
        decoding: HwDecoding,
        forRenderer: Boolean,
    ): OpenedMedia {
        val started = SystemClock.elapsedRealtime()
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: throw FileNotFoundException("The provider returned no file descriptor")
        // Cloud providers download the whole file first: a slow start shows up here.
        PlayerDiagnostics.record("open: provider gave a descriptor in ${SystemClock.elapsedRealtime() - started} ms")
        return try {
            val media = Media(libVLC, descriptor.fileDescriptor)
            applySettings(media, settings, decoding, forRenderer)
            OpenedMedia(media, descriptor)
        } catch (e: RuntimeException) {
            descriptor.closeQuietly()
            throw e
        }
    }

    private fun applySettings(media: Media, settings: Settings, decoding: HwDecoding, forRenderer: Boolean) {
        when (decoding) {
            HwDecoding.AUTO -> media.setHWDecoderEnabled(true, false)
            HwDecoding.DISABLED -> media.setHWDecoderEnabled(false, false)
            HwDecoding.FORCED -> media.setHWDecoderEnabled(true, true)
        }
        if (forRenderer && decoding != HwDecoding.DISABLED) {
            // A video the cast device can't play is decoded here and re-encoded: the hardware
            // decoder must hand its frames over in memory, there is no surface to draw them on.
            media.addOption(":no-mediacodec-dr")
            media.addOption(":no-omxil-dr")
        }
        val encoding = settings.subtitleEncoding.trim()
        if (encoding.isNotEmpty()) media.addOption(":subsdec-encoding=$encoding")
    }

    private fun localPath(uri: String): String =
        if (uri.startsWith("/")) uri else Uri.parse(uri).path ?: uri.removePrefix("file://")

    /** What libVLC opens for a network video: the URI and the media options its source adds. */
    private class NetworkPlayback(val uri: String, val options: List<String>)

    /**
     * Asks the [com.aakash.novafork.data.PlaybackOptions] of the source [uri] belongs to (looked
     * up by the library) for the URI to open and the options to add. A video of no such source,
     * or a failed lookup, plays [uri] as it is.
     */
    private suspend fun networkPlayback(context: Context, uri: String): NetworkPlayback {
        val plain = NetworkPlayback(uri, emptyList())
        return try {
            val graph = (context.applicationContext as? NovaApp)?.graph ?: return plain
            val source = graph.library.sourceForUri(uri) ?: return plain
            val hook = graph.playbackOptions[source.type] ?: return plain
            val options = hook.mediaOptions(source, uri)
            val playUri = hook.playUri(source, uri)
            PlayerDiagnostics.record("open: ${source.type.name} source adds ${options.size} option(s)")
            NetworkPlayback(playUri, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No playback options for a network video", e)
            PlayerDiagnostics.record("open: the source's playback options failed: ${e.javaClass.simpleName}")
            plain
        }
    }
}
