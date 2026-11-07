package com.aakash.novafork.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.core.content.ContextCompat
import com.aakash.novafork.R

/** Picture-in-picture for the player: window shape from the video, a play/pause action, auto-enter. */
internal class PipController(
    private val activity: Activity,
    private val onTogglePlay: () -> Unit,
) {
    val isSupported: Boolean = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private var playing = false
    private var autoEnter = false
    private var aspectRatio = DEFAULT_ASPECT
    private var registered = false

    /**
     * Where the video view sits in the window. The picture within it (fitted to [aspectRatio])
     * is handed to the system, so going into the pop-up window shrinks the picture itself into
     * place in one motion instead of jumping.
     */
    var videoBounds: Rect? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TOGGLE_PLAY) onTogglePlay()
        }
    }

    private val toggleIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            activity,
            REQUEST_TOGGLE_PLAY,
            Intent(ACTION_TOGGLE_PLAY).setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun register() {
        if (!isSupported || registered) return
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_TOGGLE_PLAY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        registered = false
        try {
            activity.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "PiP receiver was not registered", e)
        }
    }

    /**
     * Keeps the system's PiP parameters current, so auto-enter and the action icon match playback.
     * [autoEnter] = leaving the app while this shows enters PiP (Android 12+); off while leaving
     * plays the sound in the background instead.
     */
    fun update(playing: Boolean, videoSize: VideoSize?, autoEnter: Boolean = playing) {
        this.playing = playing
        this.autoEnter = autoEnter
        aspectRatio = videoSize?.let { aspectFor(it.width, it.height) } ?: DEFAULT_ASPECT
        if (!isSupported) return
        try {
            activity.setPictureInPictureParams(buildParams())
        } catch (e: IllegalStateException) {
            Log.w(TAG, "PiP parameters rejected", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "PiP parameters rejected", e)
        }
    }

    fun enter(): Boolean {
        if (!isSupported) return false
        return try {
            activity.enterPictureInPictureMode(buildParams())
        } catch (e: IllegalStateException) {
            // PiP turned off for the app in system settings.
            Log.w(TAG, "Can't enter picture-in-picture", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Can't enter picture-in-picture", e)
            false
        }
    }

    private fun buildParams(): PictureInPictureParams {
        val label = if (playing) "Pause" else "Play"
        val icon = Icon.createWithResource(activity, if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(listOf(RemoteAction(icon, label, label, toggleIntent)))
        pictureBounds()?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /** The video's picture inside [videoBounds]: the largest [aspectRatio] rectangle, centred. */
    private fun pictureBounds(): Rect? {
        val bounds = videoBounds ?: return null
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val ratio = aspectRatio.toFloat()
        val width: Int
        val height: Int
        if (bounds.width() / bounds.height().toFloat() > ratio) {
            height = bounds.height()
            width = (height * ratio).toInt()
        } else {
            width = bounds.width()
            height = (width / ratio).toInt()
        }
        val left = bounds.left + (bounds.width() - width) / 2
        val top = bounds.top + (bounds.height() - height) / 2
        return Rect(left, top, left + width, top + height)
    }

    /** The system rejects ratios outside [1/2.39, 2.39]; the bounds used here stay just inside. */
    private fun aspectFor(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return DEFAULT_ASPECT
        val ratio = width.toFloat() / height
        return when {
            ratio > MAX_RATIO -> Rational(MAX_RATIO_HUNDREDTHS, 100)
            ratio < 1f / MAX_RATIO -> Rational(100, MAX_RATIO_HUNDREDTHS)
            else -> Rational(width, height)
        }
    }

    private companion object {
        const val TAG = "PipController"
        const val ACTION_TOGGLE_PLAY = "com.aakash.novafork.player.action.PIP_TOGGLE_PLAY"
        const val REQUEST_TOGGLE_PLAY = 1
        const val MAX_RATIO_HUNDREDTHS = 238
        const val MAX_RATIO = MAX_RATIO_HUNDREDTHS / 100f
        val DEFAULT_ASPECT = Rational(16, 9)
    }
}
