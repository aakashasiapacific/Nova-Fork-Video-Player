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

    /** The subtitle look ([com.aakash.novafork.data.Settings]' size, box and height) libVLC draws with. */
    data class SubtitleStyle(val size: Int = 100, val background: Boolean = false, val margin: Int = 0) {
        val options: List<String>
            get() = buildList {
                if (size != 100) add("--sub-text-scale=$size")
                if (background) {
                    add("--freetype-background-opacity=160")
                    add("--freetype-background-color=0")
                }
                if (margin > 0) add("--sub-margin=$margin")
            }
    }

    @Volatile
    private var videoInstance: Pair<SubtitleStyle, LibVLC>? = null

    /**
     * The video player's libVLC: subtitle styling is an instance option in libVLC 3, so videos get
     * an instance of their own, made again when the style changes (music keeps [libVLC]). An
     * instance a background video still uses is left to it rather than released.
     */
    fun videoLibVLC(context: Context, style: SubtitleStyle): LibVLC {
        if (style.options.isEmpty()) return libVLC(context)
        synchronized(this) {
            videoInstance?.let { (made, instance) -> if (made == style) return instance }
            val instance = try {
                LibVLC(context.applicationContext, ArrayList(coreOptions + moduleOptions + style.options))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "libVLC rejected the subtitle style; using the shared instance", e)
                return libVLC(context)
            }
            instance.setUserAgent("Nova Fork", "NovaFork/${BuildConfig.VERSION_NAME}")
            videoInstance = style to instance
            return instance
        }
    }

    private fun create(context: Context): LibVLC {
        // LibVLC appends its default audio/chroma options to the list, so it must be mutable.
        val libVLC = try {
            LibVLC(context, ArrayList(coreOptions + moduleOptions))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "libVLC rejected the tuned options; starting with the core ones", e)
            PlayerDiagnostics.record("engine: tuned options rejected, core options only")
            LibVLC(context, ArrayList(coreOptions))
        }
        libVLC.setUserAgent("Nova Fork", "NovaFork/${BuildConfig.VERSION_NAME}")
        PlayerDiagnostics.record("engine: libVLC ${LibVLC.version()}")
        return libVLC
    }
}
