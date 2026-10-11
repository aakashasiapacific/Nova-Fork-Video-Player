package com.aakash.novafork

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.test.FakeImageLoaderEngine
import com.aakash.novafork.ui.adaptive.DeviceLayout
import com.aakash.novafork.ui.adaptive.LocalDeviceLayout
import com.aakash.novafork.ui.adaptive.LocalNovaDimens
import com.aakash.novafork.ui.adaptive.dimensFor
import com.aakash.novafork.ui.adaptive.rememberDeviceLayout
import com.aakash.novafork.ui.dialogs.NovaDialogs
import com.aakash.novafork.ui.model.AppUiState
import com.aakash.novafork.ui.model.NoOpAppActions
import com.aakash.novafork.ui.phone.PhoneApp
import com.aakash.novafork.ui.tablet.TabletApp
import com.aakash.novafork.ui.theme.NovaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Stands in for TMDB, local artwork and video frames: Coil's singleton answers every request with
 * art generated from the URL, so screenshots look like a real library without any network or files.
 * The same URL always gives the same picture.
 */
object FakeImages {
    fun install(context: Context) {
        val engine = FakeImageLoaderEngine.Builder()
            // The engine's default request transformer turns crossfades off for intercepted requests;
            // a crossfade would time itself with the real clock and could be caught half-drawn.
            .intercept({ true }, GeneratedArt(context.resources))
            .build()
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components { add(engine) }
                .build(),
        )
    }

    fun reset() {
        Coil.reset()
    }

    /** A still from a "video", for the player's surface. */
    fun videoFrame(seed: String, width: Int = 1280, height: Int = 720): Bitmap = ArtPainter.landscape(seed, width, height)
}

private class GeneratedArt(private val resources: Resources) : FakeImageLoaderEngine.OptionalInterceptor {
    private val cache = HashMap<String, Bitmap>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toString()
        val bitmap = synchronized(cache) { cache.getOrPut(url) { ArtPainter.forUrl(url) } }
        return SuccessResult(BitmapDrawable(resources, bitmap), chain.request, DataSource.MEMORY)
    }
}

private object ArtPainter {
    private val posterHints = listOf("/w92/", "/w154/", "/w185/", "/w342/", "/w500/", "poster", "folder.jpg", "cover.jpg")
    private val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".m4v", ".webm", ".ts")

    fun forUrl(url: String): Bitmap {
        val lower = url.lowercase()
        return when {
            posterHints.any { it in lower } -> poster(url)
            lower.startsWith("content://media/external/video") || videoExtensions.any { lower.endsWith(it) } ->
                landscape(url, 1280, 720, daylight = true)
            else -> landscape(url, 1280, 720)
        }
    }

    /** 2:3 key art: moody gradient, glows, a horizon, and a title block at the bottom. */
    fun poster(seed: String): Bitmap {
        val width = 500
        val height = 750
        val random = Random(seed.hashCode())
        val hue = random.nextFloat() * 360f
        val second = hue + 25f + random.nextFloat() * 45f
        val accent = hue + 150f + random.nextFloat() * 60f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()

        fill(canvas, w, h, LinearGradient(0f, 0f, w * 0.3f, h, intArrayOf(hsv(hue, 0.55f, 0.5f), hsv(second, 0.65f, 0.2f), hsv(second, 0.5f, 0.06f)), null, Shader.TileMode.CLAMP))
        repeat(3) {
            glow(canvas, w * random.nextFloat(), h * (0.1f + random.nextFloat() * 0.5f), w * (0.25f + random.nextFloat() * 0.35f), hsv(if (it == 0) accent else hue, 0.45f, 1f, 0.45f))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = hsv(second, 0.6f, 0.1f)
        canvas.drawPath(hills(random, w, h, baseline = h * 0.66f, amplitude = h * 0.14f, points = 5), paint)
        paint.shader = LinearGradient(0f, h * 0.62f, 0f, h, intArrayOf(0x00000000, 0xD9000000.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.62f, w, h, paint)
        paint.shader = null
        paint.color = hsv(accent, 0.15f, 0.97f, 0.92f)
        canvas.drawRoundRect(RectF(w * 0.18f, h * 0.81f, w * 0.82f, h * 0.845f), 6f, 6f, paint)
        paint.color = hsv(accent, 0.1f, 0.9f, 0.55f)
        canvas.drawRoundRect(RectF(w * 0.3f, h * 0.87f, w * 0.7f, h * 0.882f), 4f, 4f, paint)
        return bitmap
    }

    /** 16:9-ish landscape: sky, a low sun and layered hills, like a backdrop or an episode still. */
    fun landscape(seed: String, width: Int, height: Int, daylight: Boolean = false): Bitmap {
        val random = Random(seed.hashCode())
        val hue = if (daylight) 190f + random.nextFloat() * 30f else random.nextFloat() * 360f
        val second = hue + 20f + random.nextFloat() * 50f
        val sun = if (daylight) 35f + random.nextFloat() * 15f else hue + 160f + random.nextFloat() * 40f
        val brightness = if (daylight) 0.92f else 0.55f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()

        fill(canvas, w, h, LinearGradient(0f, 0f, 0f, h, intArrayOf(hsv(hue, 0.45f, brightness), hsv(second, 0.5f, brightness * 0.55f)), null, Shader.TileMode.CLAMP))
        glow(canvas, w * (0.2f + random.nextFloat() * 0.6f), h * (0.25f + random.nextFloat() * 0.15f), h * 0.45f, hsv(sun, 0.4f, 1f, 0.7f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val layers = 3
        for (layer in 0 until layers) {
            val depth = layer.toFloat() / (layers - 1)
            paint.color = hsv(second + depth * 20f, 0.45f + depth * 0.2f, brightness * (0.5f - depth * 0.38f))
            canvas.drawPath(
                hills(random, w, h, baseline = h * (0.55f + depth * 0.25f), amplitude = h * (0.18f - depth * 0.08f), points = 4 + layer * 2),
                paint,
            )
        }
        // Soft vignette, so text drawn over the art stays readable as it does with real backdrops.
        fill(canvas, w, h, RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.75f, intArrayOf(0x00000000, 0x73000000), null, Shader.TileMode.CLAMP))
        return bitmap
    }

    private fun fill(canvas: Canvas, w: Float, h: Float, shader: Shader) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = RadialGradient(x, y, radius, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
    }

    /** A smooth ridge across the full width, filled down to the bottom edge. */
    private fun hills(random: Random, w: Float, h: Float, baseline: Float, amplitude: Float, points: Int): Path {
        val xs = FloatArray(points + 1) { w * it / points }
        val ys = FloatArray(points + 1) { baseline - amplitude * random.nextFloat() }
        val path = Path()
        path.moveTo(0f, h)
        path.lineTo(0f, ys[0])
        for (i in 0 until points) {
            path.quadTo(xs[i], ys[i], (xs[i] + xs[i + 1]) / 2f, (ys[i] + ys[i + 1]) / 2f)
        }
        path.lineTo(w, ys[points])
        path.lineTo(w, h)
        path.close()
        return path
    }

    /** ARGB from hue (degrees, any range), saturation, value and alpha in 0–1. */
    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Int {
        val sector = ((hue % 360f) + 360f) % 360f / 60f
        val chroma = value * saturation
        val x = chroma * (1f - abs(sector % 2f - 1f))
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        val m = value - chroma
        fun channel(v: Float): Int = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}

// Rendering harness shared by the screen tests

/**
 * The library UI as NovaRoot draws it (theme, phone or tablet family, dialogs), with no view
 * model behind it. Reports the layout family it picked for the configuration under test.
 */
@Composable
fun AppUnderTest(state: AppUiState, onLayout: (DeviceLayout) -> Unit) {
    val actions = remember { NoOpAppActions() }
    val settings = state.settings.settings
    NovaTheme(settings.themeMode) {
        val layout = rememberDeviceLayout(settings.layoutMode)
        SideEffect { onLayout(layout) }
        CompositionLocalProvider(
            LocalDeviceLayout provides layout,
            LocalNovaDimens provides dimensFor(layout),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (layout.isTablet) {
                    TabletApp(state = state, actions = actions, layout = layout)
                } else {
                    PhoneApp(state = state, actions = actions)
                }
                NovaDialogs(dialog = state.dialog, actions = actions)
            }
        }
    }
}

/**
 * Renders [content] on a paused clock, lets it settle for [settleMs] and saves
 * build/screens/[name].png. The clock only moves when told to, so spinners and pagers can't keep
 * the test busy, and timers further out (hero rotation, auto-hiding player controls) only fire
 * when [settleMs] reaches them. [wholeScreen] also captures dialog windows.
 */
fun ComposeContentTestRule.captureScreen(
    name: String,
    settleMs: Long = 1_200L,
    wholeScreen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val path = "build/screens/$name.png"
    val steps = 6
    if (wholeScreen) {
        // A dialog lives in its own window, which Robolectric lays out from Choreographer frames
        // that only run when the looper clock moves. With the compose clock paused it never gets
        // laid out and the test can't go idle, so dialogs run on the normal clock and the looper
        // is stepped forward instead.
        setContent(content)
        repeat(steps) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(settleMs / steps))
            waitForIdle()
        }
        captureScreenRoboImage(path)
        return
    }
    mainClock.autoAdvance = false
    setContent(content)
    // Several short steps: each one runs effects and recompositions, and the idle wait in between
    // lets image requests (resolved on the main looper) land before the next frame.
    repeat(steps) {
        mainClock.advanceTimeBy(settleMs / steps)
        waitForIdle()
    }
    onRoot().captureRoboImage(path)
}
