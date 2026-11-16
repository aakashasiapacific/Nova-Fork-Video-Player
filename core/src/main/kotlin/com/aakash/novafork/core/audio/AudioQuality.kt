package com.aakash.novafork.core.audio

import java.util.Locale

/**
 * What a song's file is, for Apple Music's "Lossless" badge and Song Info. Pure: the codec, rate
 * and depth are read from the file elsewhere (lazily) and passed in; the file name alone already
 * answers for FLAC, WAV and AIFF.
 */
object AudioQuality {
    /** Above this sample rate, or 16 bits, lossless is "Hi-Res Lossless". */
    private const val HI_RES_SAMPLE_RATE_HZ = 48_000
    private const val CD_BIT_DEPTH = 16

    private val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "wave", "aif", "aiff", "aifc", "alac")

    /** Codec mime types (a track's, as MediaExtractor names them) that are lossless. */
    private val LOSSLESS_CODECS = setOf("audio/flac", "audio/x-flac", "audio/alac", "audio/raw")

    /**
     * Whether the file is lossless. The extension decides FLAC, WAV and AIFF; an m4a is lossless
     * only when its codec ([codecMime]) says ALAC, so it stays false until that is read.
     */
    fun isLossless(fileName: String, codecMime: String? = null): Boolean =
        AudioFiles.extension(fileName) in LOSSLESS_EXTENSIONS ||
            codecMime?.lowercase(Locale.ROOT) in LOSSLESS_CODECS

    /** Files whose lossless answer needs the codec: an m4a may hold AAC or ALAC. */
    fun needsCodec(fileName: String): Boolean = AudioFiles.extension(fileName).let { it == "m4a" || it == "mp4" }

    /** "Lossless" or "Hi-Res Lossless" (above 48 kHz or 16 bits, when known); null when it isn't lossless. */
    fun label(lossless: Boolean, sampleRateHz: Int?, bitDepth: Int?): String? = when {
        !lossless -> null
        (sampleRateHz ?: 0) > HI_RES_SAMPLE_RATE_HZ || (bitDepth ?: 0) > CD_BIT_DEPTH -> "Hi-Res Lossless"
        else -> "Lossless"
    }

    /** The format's name: "FLAC", "MP3", "AAC", "ALAC", "WAV" …, from the codec when known, else the extension. */
    fun formatName(fileName: String, codecMime: String? = null): String {
        val codec = codecMime?.lowercase(Locale.ROOT)
        val extension = AudioFiles.extension(fileName)
        when {
            codec == null -> Unit
            "flac" in codec -> return "FLAC"
            "alac" in codec -> return "ALAC"
            codec == "audio/mpeg" || codec == "audio/mpeg-l2" -> return if (extension == "mp2") "MP2" else "MP3"
            "mp4a" in codec || codec == "audio/aac" || codec == "audio/aac-adts" -> return "AAC"
            "vorbis" in codec -> return "Vorbis"
            "opus" in codec -> return "Opus"
            "ac3" in codec -> return "AC-3"
            "wma" in codec -> return "WMA"
        }
        return when (extension) {
            "" -> "Unknown"
            "mp3" -> "MP3"
            "aac" -> "AAC"
            "m4a", "mp4" -> "M4A"
            "wav", "wave" -> "WAV"
            "aif", "aiff", "aifc" -> "AIFF"
            "ogg", "oga" -> "Ogg"
            else -> extension.uppercase(Locale.ROOT)
        }
    }

    /** "320 kbps". */
    fun bitrate(kbps: Int): String = "$kbps kbps"

    /** The file's average bitrate in kbps from its size and length; null when either is missing. */
    fun averageBitrateKbps(sizeBytes: Long?, durationMs: Long?): Int? {
        if (sizeBytes == null || durationMs == null || sizeBytes <= 0 || durationMs <= 0) return null
        return (sizeBytes * 8 / durationMs).toInt()
    }

    /** "44.1 kHz", "48 kHz", "96 kHz". */
    fun sampleRate(hz: Int): String {
        val tenths = (hz + 50) / 100
        return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
    }

    /** "24-bit". */
    fun bitDepth(bits: Int): String = "$bits-bit"
}
