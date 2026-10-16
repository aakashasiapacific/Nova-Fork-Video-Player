package com.aakash.novafork.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aakash.novafork.data.HwDecoding
import com.aakash.novafork.data.Settings
import com.aakash.novafork.player.MediaSource
import com.aakash.novafork.player.OpenedMedia
import com.aakash.novafork.player.VlcEngine
import com.aakash.novafork.player.closeQuietly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.atomic.AtomicReference

/**
 * A muted (or, with the hero's mute button, audible) loop of a local video, cropped to fill
 * [modifier], from [startMs]. It fades in on its first frame, so the artwork under it shows while
 * it loads, and everything is released when it leaves the composition. Only for local files:
 * see [com.aakash.novafork.ui.model.HeroUi.previewUri].
 */
@Composable
internal fun HeroVideoPreview(uri: String, startMs: Long, muted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showing by remember(uri) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (showing) 1f else 0f, tween(durationMillis = 500), label = "heroPreview")
    val preview = remember(uri) { PreviewPlayer(context.applicationContext) }
    DisposableEffect(preview) { onDispose { preview.release() } }
    LaunchedEffect(preview) { preview.start(uri, startMs, muted) { showing = true } }
    LaunchedEffect(preview, muted) { preview.setMuted(muted) }
    AndroidView(
        factory = { viewContext -> VLCVideoLayout(viewContext).also { preview.attach(it) } },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

/** One preview's libVLC player. Main thread only. */
private class PreviewPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var layout: VLCVideoLayout? = null
    private var muted = true
    private var released = false

    fun attach(view: VLCVideoLayout) {
        layout = view
        player?.let { attachTo(it, view) }
    }

    suspend fun start(uri: String, startMs: Long, startMuted: Boolean, onFirstFrame: () -> Unit) {
        muted = startMuted
        val libVLC = VlcEngine.libVLC(context)
        val result = AtomicReference<OpenedMedia?>(null)
        try {
            withContext(Dispatchers.IO) {
                result.set(MediaSource.open(context, libVLC, uri, Settings(), HwDecoding.AUTO, smbCredentials = null))
            }
        } catch (e: CancellationException) {
            result.getAndSet(null)?.discard()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The hero preview couldn't open its video", e)
            return
        }
        val opened = result.getAndSet(null) ?: return
        if (released) {
            opened.discard()
            return
        }
        try {
            if (startMs > 0) opened.media.addOption(":start-time=${startMs / 1000}")
            // Loops, like Apple TV's previews.
            opened.media.addOption(":input-repeat=65535")
            val mediaPlayer = VlcEngine.newMediaPlayer(context)
            mediaPlayer.setEventListener(
                object : MediaPlayer.EventListener {
                    override fun onEvent(event: MediaPlayer.Event) {
                        if (event.type == MediaPlayer.Event.Vout && event.voutCount > 0) onFirstFrame()
                    }
                },
            )
            mediaPlayer.setMedia(opened.media)
            opened.media.release()
            descriptor = opened.descriptor
            mediaPlayer.setVolume(if (muted) 0 else FULL_VOLUME)
            player = mediaPlayer
            layout?.let { attachTo(mediaPlayer, it) }
            mediaPlayer.play()
        } catch (e: RuntimeException) {
            Log.w(TAG, "The hero preview couldn't start", e)
            release()
        }
    }

    fun setMuted(on: Boolean) {
        muted = on
        player?.setVolume(if (on) 0 else FULL_VOLUME)
    }

    fun release() {
        released = true
        player?.let {
            it.setEventListener(null)
            it.stop()
            it.detachViews()
            it.release()
        }
        player = null
        descriptor.closeQuietly()
        descriptor = null
    }

    private fun attachTo(mediaPlayer: MediaPlayer, view: VLCVideoLayout) {
        // A TextureView, so the video scrolls and fades with the rest of the page.
        mediaPlayer.attachViews(view, null, false, true)
        mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL)
    }

    private companion object {
        const val TAG = "HeroPreview"
        const val FULL_VOLUME = 100
    }
}

/**
 * How bright the top of [model]'s image is (0 dark … 1 bright), for the status bar icons over
 * the hero; null until known or when it doesn't load. Coil's caches usually have the image already.
 */
@Composable
internal fun rememberTopBrightness(model: String?): Float? {
    val context = LocalContext.current
    val brightness by produceState<Float?>(initialValue = null, model) {
        value = if (model == null) null else topBrightness(context, model)
    }
    return brightness
}

private suspend fun topBrightness(context: Context, model: String): Float? {
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(BRIGHTNESS_SAMPLE_PX)
        .allowHardware(false)
        .build()
    val bitmap = try {
        ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } ?: return null
    return withContext(Dispatchers.Default) { averageLuminance(bitmap, topFraction = 0.12f) }
}

/** Mean luminance of the top [topFraction] of [bitmap]. */
internal fun averageLuminance(bitmap: Bitmap, topFraction: Float): Float? {
    val width = bitmap.width
    val rows = (bitmap.height * topFraction).toInt().coerceIn(1, bitmap.height)
    if (width <= 0 || bitmap.height <= 0) return null
    val pixels = IntArray(width * rows)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, rows)
    var total = 0.0
    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        total += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    }
    return (total / pixels.size).toFloat()
}

private const val BRIGHTNESS_SAMPLE_PX = 64
