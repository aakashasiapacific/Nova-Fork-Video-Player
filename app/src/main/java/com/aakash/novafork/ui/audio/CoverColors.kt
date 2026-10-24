package com.aakash.novafork.ui.audio

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest

/** The three colours of Now Playing's background, top to bottom, taken from the cover. */
internal data class CoverColors(val top: Color, val middle: Color, val bottom: Color) {
    companion object {
        /** No cover, or it has not loaded: Apple Music's dark grey. */
        val Fallback = CoverColors(Color(0xFF3A3A3C), Color(0xFF2C2C2E), Color(0xFF1C1C1E))
    }
}

/**
 * The cover's colours as Apple Music paints its Now Playing background: the average of the top,
 * middle and bottom thirds of the picture, made a little richer and kept dark enough for white
 * text on top.
 */
internal fun coverColors(bitmap: Bitmap): CoverColors {
    val width = bitmap.width
    val height = bitmap.height
    fun band(from: Int, to: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in from until to.coerceAtMost(height)) {
            for (x in 0 until width) {
                val p = bitmap.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        if (n == 0) return Color.DarkGray
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
    val third = (height / 3).coerceAtLeast(1)
    return CoverColors(
        top = tune(band(0, third)),
        middle = tune(band(third, third * 2)),
        bottom = tune(band(third * 2, height), darker = true),
    )
}

private fun tune(color: Color, darker: Boolean = false): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceIn(0.16f, if (darker) 0.42f else 0.58f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Apple Music's Now Playing background: a smooth gradient in the cover's colours, crossfading
 * to the next song's. It stays still (owner: no turning); a soft dark fade at the bottom keeps
 * the controls readable.
 */
@Composable
internal fun CoverGradient(artUri: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors by produceState(CoverColors.Fallback, artUri) {
        value = artUri?.let { uri ->
            runCatching {
                val request = ImageRequest.Builder(context).data(uri).size(GRADIENT_PX).allowHardware(false).build()
                (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap?.let(::coverColors)
            }.getOrNull()
        } ?: CoverColors.Fallback
    }
    val fade = tween<Color>(durationMillis = 700)
    val top by animateColorAsState(colors.top, fade, label = "gradientTop")
    val middle by animateColorAsState(colors.middle, fade, label = "gradientMiddle")
    val bottom by animateColorAsState(colors.bottom, fade, label = "gradientBottom")
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, middle, bottom))))
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))),
    )
}

/** The size the cover is read at: plenty for three averages. */
private const val GRADIENT_PX = 24
