package com.aakash.novafork.core.format

/** What is known for sure about a video's streams beyond its size; every default means "not known". */
data class StreamTraits(
    val hdr: Boolean = false,
    val dolbyVision: Boolean = false,
    /** Audio channels of the main track (6 = 5.1, 8 = 7.1). */
    val channels: Int? = null,
    val atmos: Boolean = false,
)

/** The small outlined badges after a details page's year and length ("4K", "HDR", "5.1"). */
object QualityBadges {
    /**
     * Badges in Apple TV's order. The resolution comes from the size (4K from 3200×2000 up, else HD
     * from 1280×720); the rest only from what [traits] says, so nothing is guessed.
     */
    fun of(width: Int?, height: Int?, traits: StreamTraits = StreamTraits()): List<String> = buildList {
        val w = width ?: 0
        val h = height ?: 0
        when {
            w >= 3200 || h >= 2000 -> add("4K")
            w >= 1280 || h >= 720 -> add("HD")
        }
        if (traits.dolbyVision) add("Dolby Vision")
        if (traits.hdr) add("HDR")
        when (traits.channels) {
            6 -> add("5.1")
            8 -> add("7.1")
        }
        if (traits.atmos) add("Atmos")
    }
}
