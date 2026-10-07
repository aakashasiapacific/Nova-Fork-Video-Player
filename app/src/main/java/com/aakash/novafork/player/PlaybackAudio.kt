package com.aakash.novafork.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/** Audio focus for movie playback, and the "headphones unplugged" broadcast while playing. */
internal class PlaybackAudio(context: Context, private val listener: Listener) {

    interface Listener {
        /** Another app took focus; [transient] = it will give it back (a call, navigation prompt). */
        fun onFocusLost(transient: Boolean)
        fun onFocusRegained()
        fun onDuck(ducked: Boolean)
        fun onBecomingNoisy()
    }

    private val appContext = context.applicationContext
    private val audioManager: AudioManager = checkNotNull(appContext.getSystemService(AudioManager::class.java))
    private var hasFocus = false
    private var noisyRegistered = false

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener(
            AudioManager.OnAudioFocusChangeListener { change -> onFocusChange(change) },
            Handler(Looper.getMainLooper()),
        )
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) listener.onBecomingNoisy()
        }
    }

    fun requestFocus() {
        if (hasFocus) return
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun registerNoisyReceiver() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    fun unregisterNoisyReceiver() {
        if (!noisyRegistered) return
        noisyRegistered = false
        try {
            appContext.unregisterReceiver(noisyReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Noisy receiver was not registered", e)
        }
    }

    fun release() {
        unregisterNoisyReceiver()
        abandonFocus()
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                listener.onDuck(false)
                listener.onFocusRegained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandonFocus()
                listener.onFocusLost(transient = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> listener.onFocusLost(transient = true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> listener.onDuck(true)
        }
    }

    private companion object {
        const val TAG = "PlaybackAudio"
    }
}
