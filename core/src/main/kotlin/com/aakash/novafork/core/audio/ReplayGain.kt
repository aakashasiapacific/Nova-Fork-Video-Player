package com.aakash.novafork.core.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Sound Check's source: the loudness a song's tags ask for. Reads the head of an audio file for
 * a ReplayGain track gain (ID3 "TXXX:REPLAYGAIN_TRACK_GAIN", a Vorbis/FLAC/Opus comment, an MP4
 * "replaygain_track_gain" atom) and, failing that, iTunes' "iTunNORM" Sound Check value. The
 * tags are found by scanning the bytes for their names, so every container is read the same way.
 */
object ReplayGain {
    /** Largest change either way, in dB. */
    const val MAX_GAIN_DB = 12.0

    /** What libVLC's volume (percent) can reach. */
    const val MAX_VOLUME_PERCENT = 200

    private const val TRACK_GAIN = "replaygain_track_gain"
    private const val TRACK_PEAK = "replaygain_track_peak"
    private const val ITUNNORM = "itunnorm"
    private const val DEFAULT_READ = 1024 * 1024
    private const val MIN_ID3_READ = 64 * 1024
    private const val MAX_ID3_READ = 8 * 1024 * 1024

    /** How many bytes of a file starting with [first] (its first 10 bytes) hold its tags. */
    fun bytesToRead(first: ByteArray): Int {
        if (first.size >= 10 && first[0] == 'I'.code.toByte() && first[1] == 'D'.code.toByte() && first[2] == '3'.code.toByte()) {
            var size = 0
            for (i in 6..9) size = (size shl 7) or (first[i].toInt() and 0x7F)
            return (size + 10).coerceIn(MIN_ID3_READ, MAX_ID3_READ)
        }
        return DEFAULT_READ
    }

    /** The song's loudness adjustment in dB (negative = turn down), clamped to ±[MAX_GAIN_DB]; null without a tag. */
    fun gainDb(head: ByteArray): Double? {
        val text = String(head, Charsets.ISO_8859_1).lowercase()
        val gain = tagNumber(text, TRACK_GAIN) ?: tagNumber(text.replace("\u0000", ""), TRACK_GAIN) ?: iTunNorm(text)
        return gain?.takeIf { it.isFinite() }?.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
    }

    /** The track peak (1.0 = full scale) when tagged; null otherwise. */
    fun peak(head: ByteArray): Double? {
        val text = String(head, Charsets.ISO_8859_1).lowercase()
        return (tagNumber(text, TRACK_PEAK) ?: tagNumber(text.replace("\u0000", ""), TRACK_PEAK))
            ?.takeIf { it > 0.0 && it.isFinite() }
    }

    /**
     * libVLC volume (percent, 100 = unchanged) for [gainDb]. A boost never takes a tagged [peak]
     * past full scale, and none passes [MAX_VOLUME_PERCENT].
     */
    fun volumePercent(gainDb: Double, peak: Double? = null): Int {
        var linear = 10.0.pow(gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB) / 20.0)
        if (linear > 1.0 && peak != null && peak > 0.0) linear = linear.coerceAtMost(max(1.0, 1.0 / peak))
        return (linear * 100).roundToInt().coerceIn(1, MAX_VOLUME_PERCENT)
    }

    /** The first number after [key] in [text]; null when the key or a number is missing. */
    private fun tagNumber(text: String, key: String): Double? {
        var from = text.indexOf(key)
        while (from >= 0) {
            numberAfter(text, from + key.length)?.let { return it }
            from = text.indexOf(key, from + key.length)
        }
        return null
    }

    /** Where a tag's value begins after its name ends at [start]: past MP4's "data" atom header, else past separators. */
    private fun valueStart(text: String, start: Int): Int {
        val data = text.indexOf("data", start)
        if (data in start..start + 12) return minOf(text.length, data + 4 + 8)
        var i = start
        // Separators: '=', ':', blanks, control bytes and a UTF-16 byte order mark (0xFF 0xFE / 0xFE 0xFF).
        while (i < text.length && i < start + 8 && (text[i] == '=' || text[i] == ':' || text[i] <= ' ' || text[i] == 'ÿ' || text[i] == 'þ')) i++
        return i
    }

    private fun numberAfter(text: String, start: Int): Double? {
        var i = valueStart(text, start)
        val begin = i
        if (i < text.length && (text[i] == '-' || text[i] == '+')) i++
        var digits = 0
        while (i < text.length && (text[i] in '0'..'9' || (text[i] == '.' && digits > 0))) {
            if (text[i] in '0'..'9') digits++
            i++
        }
        if (digits == 0) return null
        return text.substring(begin, i).removePrefix("+").toDoubleOrNull()
    }

    /** iTunes' Sound Check: ten 8-digit hex values, the first two the left and right levels (1000 = 0 dB). */
    private fun iTunNorm(text: String): Double? {
        var from = text.indexOf(ITUNNORM)
        while (from >= 0) {
            var i = valueStart(text, from + ITUNNORM.length)
            val values = ArrayList<Long>(2)
            while (values.size < 2) {
                while (i < text.length && text[i] == ' ') i++
                if (i + 8 > text.length) break
                val token = text.substring(i, i + 8)
                if (!token.all { it in '0'..'9' || it in 'a'..'f' }) break
                values += token.toLong(16)
                i += 8
            }
            if (values.size == 2) {
                val level = max(values[0], values[1]).coerceAtLeast(1L)
                return -10.0 * log10(level / 1000.0)
            }
            from = text.indexOf(ITUNNORM, from + ITUNNORM.length)
        }
        return null
    }
}
