package com.aakash.novafork.data

import java.util.Locale

/** A movie split into files: "Film CD1.avi", "Film.Part.2.mkv", "Film - Disc 2.mp4". */
object MovieParts {
    private val PART = Regex("""(?:^|[ ._\-\[(])(?:cd|part|pt|disc|disk|dvd)[ ._\-]?([1-9])(?=$|[ ._\-\])])""")

    /** The part number in [fileName] (without its extension), or null for a whole film. */
    fun partOf(fileName: String): Int? {
        val name = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
        return PART.findAll(name).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }
}
