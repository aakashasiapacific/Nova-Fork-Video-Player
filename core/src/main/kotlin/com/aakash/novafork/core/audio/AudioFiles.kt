package com.aakash.novafork.core.audio

import java.util.Locale

/** Music files, their sidecar files (cover art, .lrc lyrics) and the tag values scanners read. */
object AudioFiles {
    val EXTENSIONS: Set<String> = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "aiff", "aif", "aifc", "alac",
        "ape", "wv", "mka", "mpc", "dsf", "dff", "tta", "ac3", "dts", "mp2",
    )

    /** Album cover file names, best first; Windows Media Player's "AlbumArt_{…}_Large" comes after them. */
    private val COVER_STEMS = listOf("cover", "folder", "front", "album", "albumart", "albumartlarge")

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val EXTENSION_ORDER = listOf("jpg", "jpeg", "png", "webp")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun baseName(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    fun isAudio(name: String): Boolean = extension(name) in EXTENSIONS && !name.startsWith("._")

    fun isLyrics(name: String): Boolean = extension(name) == "lrc" && !name.startsWith("._")

    fun isImage(name: String): Boolean = extension(name) in IMAGE_EXTENSIONS && !name.startsWith("._")

    /**
     * The album cover among the images of a song's folder: "cover", "folder", "front", "album",
     * "albumart" (any case, jpg before png), then Windows' "AlbumArt_{…}_Large", then the only
     * image of a folder that has just one. Null when there is none.
     */
    fun coverOf(folderImageNames: List<String>): String? {
        val images = folderImageNames.filter(::isImage)
        if (images.isEmpty()) return null
        val byStem = images.groupBy { baseName(it).lowercase(Locale.ROOT) }
            .mapValues { (_, names) -> names.sortedWith(compareBy({ EXTENSION_ORDER.indexOf(extension(it)) }, { it })) }
        COVER_STEMS.firstNotNullOfOrNull { byStem[it]?.firstOrNull() }?.let { return it }
        images.filter {
            val stem = baseName(it).lowercase(Locale.ROOT)
            stem.startsWith("albumart_") && stem.endsWith("_large")
        }.minOrNull()?.let { return it }
        return images.singleOrNull()
    }

    /** The .lrc file with the song's name ("01 Song.lrc" for "01 Song.flac"), any case; null when there is none. */
    fun lyricsFileFor(songFileName: String, siblingNames: Collection<String>): String? {
        val base = baseName(songFileName).lowercase(Locale.ROOT)
        return siblingNames.filter { isLyrics(it) && baseName(it).lowercase(Locale.ROOT) == base }.minOrNull()
    }

    /** A tag value worth keeping: trimmed, null when blank or MediaStore's "<unknown>". */
    fun cleanTag(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.equals("<unknown>", ignoreCase = true)) null else trimmed
    }

    /** The leading number of a track or disc tag: "3", "03", "3/12" and "3 of 12" all give 3. Null for 0 or none. */
    fun leadingNumber(value: String?): Int? {
        val digits = value?.trim()?.takeWhile(Char::isDigit)?.takeIf { it.isNotEmpty() && it.length <= 6 } ?: return null
        return digits.toInt().takeIf { it > 0 }
    }

    /** The year of a date tag: "2004", "2004-05-01" or "2004/05". Null when it isn't a plausible year. */
    fun year(value: String?): Int? {
        val digits = value?.trim()?.takeWhile(Char::isDigit)?.takeIf { it.length == 4 } ?: return null
        return digits.toInt().takeIf { it in 1000..2999 }
    }

    /**
     * MediaStore's TRACK column holds disc × 1000 + track ("2005" = disc 2, track 5); plain track
     * numbers below 1000 have no disc. Gives (track, disc), each null when 0.
     */
    fun splitMediaStoreTrack(value: Int?): Pair<Int?, Int?> {
        if (value == null || value <= 0) return null to null
        val track = (value % 1000).takeIf { it > 0 }
        val disc = (value / 1000).takeIf { it > 0 }
        return track to disc
    }

    /**
     * A title for a song without a title tag: the file name without its extension and without a
     * leading track number ("01 - Song", "01. Song", "1-03 Song", "03_Song").
     */
    fun titleFromFileName(fileName: String): String {
        val base = baseName(fileName).trim()
        var i = 0
        while (i < base.length && base[i].isDigit()) i++
        if (i == 0 || i > 3) return base
        // "1-03 Song": a disc number, a dash, then the track number.
        if (i < base.length && base[i] == '-' && i + 1 < base.length && base[i + 1].isDigit()) {
            var j = i + 1
            while (j < base.length && base[j].isDigit()) j++
            if (j - i - 1 in 1..3) i = j
        }
        val rest = base.substring(i).trimStart(' ', '.', '-', '_', ')').trim()
        return rest.ifEmpty { base }
    }
}
