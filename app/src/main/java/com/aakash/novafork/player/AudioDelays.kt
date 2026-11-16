package com.aakash.novafork.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The sound's delay per output (owner): earbuds that lag get their own offset, remembered by the
 * Bluetooth device's name and put back whenever it is the one playing; the phone's speaker and
 * wired headphones share "speaker".
 */
internal class AudioDelays(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The output playing now: a Bluetooth device's name, else [SPEAKER]. */
    fun currentOutput(): String {
        val audio = context.getSystemService(AudioManager::class.java) ?: return SPEAKER
        val bluetooth = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        return bluetooth?.productName?.toString()?.takeIf { it.isNotBlank() } ?: SPEAKER
    }

    /** [output]'s delay in ms (positive: the sound later), 0 when none was set. */
    fun get(output: String): Long = try {
        prefs.getLong(output, 0L)
    } catch (e: ClassCastException) {
        0L
    }

    fun set(output: String, delayMs: Long) {
        prefs.edit().apply { if (delayMs == 0L) remove(output) else putLong(output, delayMs) }.apply()
    }

    companion object {
        const val SPEAKER = "speaker"
        private const val PREFS_NAME = "audio_delays"
    }
}
