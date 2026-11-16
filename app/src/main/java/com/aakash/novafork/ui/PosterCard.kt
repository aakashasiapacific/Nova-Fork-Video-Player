package com.aakash.novafork.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A title's poster card to share: its poster on black, the title and a short line under it, and
 * the app's name small at the foot. Saved as a picture in the cache's share folder.
 */
object PosterCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1620

    suspend fun render(context: Context, title: String, line: String?, posterUrl: String?): File = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        val poster = posterUrl?.let { url ->
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(WIDTH, HEIGHT).build())
            ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }
        if (poster != null) {
            // The poster fills the card (cropped to it), then fades into black at the foot.
            val scale = maxOf(WIDTH / poster.width.toFloat(), HEIGHT / poster.height.toFloat())
            val w = poster.width * scale
            val h = poster.height * scale
            val dst = RectF((WIDTH - w) / 2f, 0f, (WIDTH + w) / 2f, h)
            canvas.drawBitmap(poster, null as Rect?, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val fade = Paint().apply {
            shader = LinearGradient(0f, HEIGHT * 0.5f, 0f, HEIGHT.toFloat(), 0x00000000, 0xF2000000.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, HEIGHT * 0.5f, WIDTH.toFloat(), HEIGHT.toFloat(), fade)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 78f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 40f
        }
        val margin = 72
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, WIDTH - margin * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        val lineLayout = line?.takeIf { it.isNotBlank() }?.let {
            StaticLayout.Builder.obtain(it, 0, it.length, linePaint, WIDTH - margin * 2).setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        }
        val bottom = HEIGHT - 120f
        val lineHeight = lineLayout?.height ?: 0
        var y = bottom - lineHeight - 16 - titleLayout.height
        canvas.save()
        canvas.translate(margin.toFloat(), y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 16
        if (lineLayout != null) {
            canvas.save()
            canvas.translate(margin.toFloat(), y)
            lineLayout.draw(canvas)
            canvas.restore()
        }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80FFFFFF.toInt()
            textSize = 30f
        }
        canvas.drawText("Nova Fork", margin.toFloat(), HEIGHT - 56f, brand)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "poster.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }
}
