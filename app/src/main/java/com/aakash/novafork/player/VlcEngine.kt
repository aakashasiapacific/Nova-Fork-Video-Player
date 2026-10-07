package com.aakash.novafork.player

import android.content.Context
import android.util.Log
import com.aakash.novafork.BuildConfig
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

/** The process-wide libVLC instance. Every player screen gets its own [MediaPlayer] from it. */
object VlcEngine {
    private const val TAG = "VlcEngine"

    /** Options of libVLC's core, always known to libvlc_new(). */
    private val coreOptions = listOf(
        // Keep the pitch when the playback speed changes.
        "--audio-time-stretch",
        // The app looks for sibling subtitles itself (local, SAF and SMB alike) and honours the
        // "load subtitles automatically" setting; libVLC's own scan would add duplicates.
        "--no-sub-autodetect-file",
    )

    /** Module options. libvlc_new() refuses unknown options, so these are dropped if init fails. */
    private val moduleOptions = listOf(
        // Skip the H.264 loop filter on non-reference frames only: cheaper software decoding, no visible loss.
        "--avcodec-skiploopfilter=1",
        "--http-reconnect",
        // 32-bit output instead of libVLC's RV16 default: no banding in dark scenes.
        "--android-display-chroma",
        "RV32",
    )

    @Volatile
    private var instance: LibVLC? = null

    /** Creates the instance on first use; call that first time from the main thread. */
    fun libVLC(context: Context): LibVLC =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    fun newMediaPlayer(context: Context): MediaPlayer = MediaPlayer(libVLC(context))

    private fun create(context: Context): LibVLC {
        // LibVLC appends its default audio/chroma options to the list, so it must be mutable.
        val libVLC = try {
            LibVLC(context, ArrayList(coreOptions + moduleOptions))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "libVLC rejected the tuned options; starting with the core ones", e)
            LibVLC(context, ArrayList(coreOptions))
        }
        libVLC.setUserAgent("Nova Fork", "NovaFork/${BuildConfig.VERSION_NAME}")
        return libVLC
    }
}
