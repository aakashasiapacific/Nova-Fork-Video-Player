package com.aakash.novafork.player

import android.content.Context
import android.util.Log
import com.aakash.novafork.NovaApp
import com.aakash.novafork.data.EQUALIZER_CUSTOM
import com.aakash.novafork.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer

/**
 * The equaliser (Preferences' and Now Playing's, the video player's too), applied to a libVLC
 * player: off, one of libVLC's presets, or the person's own ten band gains. libVLC keeps it on
 * the player for every media it plays afterwards, so it is set once per player and again when
 * the setting changes. libVLC's equaliser calls are native: everything that touches them is
 * guarded, so screens that list the presets also draw where libVLC isn't loaded (tests).
 */
object AudioEffects {
    /** libVLC's presets in libVLC's own order, for when its library can't be asked. */
    private val FallbackPresets = listOf(
        "Flat", "Classical", "Club", "Dance", "Full bass", "Full bass and treble", "Full treble",
        "Headphones", "Large Hall", "Live", "Party", "Pop", "Reggae", "Rock", "Ska", "Soft",
        "Soft rock", "Techno",
    )

    /** libVLC's ten bands, Hz, for when its library can't be asked. */
    private val FallbackBands = listOf(60f, 170f, 310f, 600f, 1_000f, 3_000f, 6_000f, 12_000f, 14_000f, 16_000f)

    /** The gain range of a band, dB. */
    const val MIN_GAIN = -20f
    const val MAX_GAIN = 20f

    /** libVLC's preset names, in its order ("Flat", "Classical" …). */
    fun presetNames(): List<String> = native(FallbackPresets) {
        (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
    }

    /** The bands' centre frequencies, Hz, low to high. */
    fun bandFrequencies(): List<Float> = native(FallbackBands) {
        (0 until MediaPlayer.Equalizer.getBandCount()).map { MediaPlayer.Equalizer.getBandFrequency(it) }
    }

    /** "60 Hz", "1 kHz", "16 kHz". */
    fun frequencyLabel(hz: Float): String =
        if (hz >= 1_000f) {
            val khz = hz / 1_000f
            if (khz % 1f == 0f) "${khz.toInt()} kHz" else "%.1f kHz".format(java.util.Locale.ROOT, khz)
        } else {
            "${hz.toInt()} Hz"
        }

    /** [Settings.equalizerBands] as gains, one per band (missing or unreadable = 0 dB). */
    fun parseBands(csv: String, count: Int = bandFrequencies().size): List<Float> {
        val values = csv.split(',').map { it.trim().toFloatOrNull()?.coerceIn(MIN_GAIN, MAX_GAIN) }
        return List(count) { values.getOrNull(it) ?: 0f }
    }

    /** Gains back to [Settings.equalizerBands]. */
    fun formatBands(gains: List<Float>): String = gains.joinToString(",") { "%.1f".format(java.util.Locale.ROOT, it) }

    /**
     * The gains [preset] gives each band, to start the person's own from what they last heard
     * (all 0 dB when unknown).
     */
    fun presetGains(preset: String): List<Float> {
        val count = bandFrequencies().size
        val index = presetNames().indexOf(preset)
        if (index < 0) return List(count) { 0f }
        return native(List(count) { 0f }) {
            val equalizer = MediaPlayer.Equalizer.createFromPreset(index)
            List(count) { band -> equalizer.getAmp(band) }
        }
    }

    /**
     * Sets [settings]' equaliser on [player], or takes it off. With [nightMode] (videos) the
     * night curve is added on top of it: see [NightCurve].
     */
    fun applyEqualizer(player: MediaPlayer, settings: Settings, nightMode: Boolean = false) {
        native(Unit) {
            val preset = settings.equalizerPreset
            val count = MediaPlayer.Equalizer.getBandCount()
            val presetIndex = (0 until MediaPlayer.Equalizer.getPresetCount())
                .firstOrNull { MediaPlayer.Equalizer.getPresetName(it) == preset }
            val base: MediaPlayer.Equalizer? = when {
                preset == EQUALIZER_CUSTOM -> MediaPlayer.Equalizer.create().apply {
                    setPreAmp(0f)
                    parseBands(settings.equalizerBands, count).forEachIndexed { band, gain -> setAmp(band, gain) }
                }
                presetIndex != null -> MediaPlayer.Equalizer.createFromPreset(presetIndex)
                else -> null
            }
            val equalizer = if (!nightMode) {
                base
            } else {
                MediaPlayer.Equalizer.create().apply {
                    setPreAmp((base?.preAmp ?: 0f) + NIGHT_PREAMP)
                    for (band in 0 until count) {
                        val gain = (base?.getAmp(band) ?: 0f) + NightCurve.getOrElse(band) { 0f }
                        setAmp(band, gain.coerceIn(MIN_GAIN, MAX_GAIN))
                    }
                }
            }
            player.setEqualizer(equalizer)
        }
    }

    /**
     * Night mode (videos): the bass where explosions and music boom is cut and the band where
     * voices sit is lifted, so dialogue stays clear at a low volume without loud scenes jumping
     * out. libVLC's compressor would be the textbook tool, but libVLC 3 only runs audio filters
     * set on its whole engine (shared with music here), and its equaliser call replaces a
     * player's filter list with the equaliser alone, so the two can't be combined on one
     * player. The equaliser can: this curve rides on top of the person's own setting. Per band:
     * 60 Hz, 170 Hz, 310 Hz, 600 Hz, 1 kHz, 3 kHz, 6 kHz, 12 kHz, 14 kHz, 16 kHz.
     */
    private val NightCurve = listOf(-8f, -5f, -2f, 1f, 4f, 5f, 3f, 0f, -1f, -2f)

    /** Headroom for the lifted voice band, so it doesn't clip. */
    private const val NIGHT_PREAMP = -2f

    /** Sets the saved equaliser on [player] (a player just made). */
    fun applyCurrent(context: Context, player: MediaPlayer) {
        val settings = settingsOf(context) ?: return
        applyEqualizer(player, settings.settings.value)
    }

    /**
     * Keeps [player] (whichever player is current, null while there is none) on the saved
     * equaliser as it changes, for as long as [scope] lives.
     */
    fun follow(context: Context, scope: CoroutineScope, player: () -> MediaPlayer?) {
        val store = settingsOf(context) ?: return
        scope.launch {
            store.settings
                .map { it.equalizerPreset to it.equalizerBands }
                .distinctUntilChanged()
                .collect { player()?.let { current -> applyEqualizer(current, store.settings.value) } }
        }
    }

    private fun settingsOf(context: Context) =
        try {
            (context.applicationContext as? NovaApp)?.graph?.settings
        } catch (e: UninitializedPropertyAccessException) {
            null
        }

    /** [block] with libVLC's native library; [fallback] where it isn't there (tests) or refuses. */
    private inline fun <T> native(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            Log.w(TAG, "libVLC equaliser unavailable", e)
            fallback
        }

    private const val TAG = "AudioEffects"
}
