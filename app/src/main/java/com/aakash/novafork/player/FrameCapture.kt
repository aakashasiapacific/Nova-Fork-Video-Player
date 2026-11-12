package com.aakash.novafork.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

/**
 * Samsung's Capture: the frame on screen, saved as a picture in Pictures/Nova Fork, where the
 * gallery finds it. libVLC draws on a SurfaceView, which only PixelCopy can read.
 */
internal object FrameCapture {
    /** Copies [layout]'s video picture; [onCopied] gets it on the main thread, or null when it could not. */
    fun copy(layout: ViewGroup, onCopied: (Bitmap?) -> Unit) {
        val surface = videoSurfaceOf(layout)
        if (surface == null || surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) {
            onCopied(null)
            return
        }
        val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, bitmap, { result ->
                onCopied(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Couldn't copy the video frame", e)
            onCopied(null)
        }
    }

    /** Saves [bitmap] as a JPEG named after [title]; false when it could not be written. Off the main thread. */
    fun save(context: Context, bitmap: Bitmap, title: String?): Boolean {
        val name = (title?.takeIf { it.isNotBlank() } ?: "Nova Fork")
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .take(MAX_NAME) + " " + System.currentTimeMillis() + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nova Fork")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        return try {
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } == true
            if (!written) {
                resolver.delete(uri, null, null)
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            true
        } catch (e: Exception) {
            // Android 9 and older want storage access for this, which the app does not ask for.
            Log.w(TAG, "Couldn't save the captured frame", e)
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** libVLC's video surface inside its layout (not the subtitle overlay above it). */
    private fun videoSurfaceOf(layout: ViewGroup): SurfaceView? {
        val id = layout.resources.getIdentifier("surface_video", "id", layout.context.packageName)
        if (id != 0) (layout.findViewById<View>(id) as? SurfaceView)?.let { return it }
        return firstSurface(layout)
    }

    private fun firstSurface(group: ViewGroup): SurfaceView? {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is SurfaceView -> return child
                is ViewGroup -> firstSurface(child)?.let { return it }
            }
        }
        return null
    }

    private const val TAG = "FrameCapture"
    private const val JPEG_QUALITY = 95
    private const val MAX_NAME = 60
}
