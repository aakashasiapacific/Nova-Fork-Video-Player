package com.aakash.novafork.core.audio

/**
 * One line of lyrics. [timeMs] is null for lyrics without timing (a plain text file or tag).
 * [words]: when each word starts being sung, for enhanced LRC; empty otherwise.
 */
data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    /** The line translated, when the .lrc has one for it (a second line at the same time, or a "[tr:...]" tag). */
    val translation: String? = null,
)

/** One sung word of an enhanced-LRC line: from [timeMs], the characters [start] until [end] of its text. */
data class LyricWord(val timeMs: Long, val start: Int, val end: Int)

/** A song's lyrics. [synced]: every line has a time, sorted, so the playing line can be followed. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /**
     * Index of the line playing at [positionMs]: the last one that started at or before it. -1
     * before the first line, and always for lyrics that are not [synced].
     */
    fun activeIndex(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if ((lines[mid].timeMs ?: 0L) <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Reads .lrc lyrics: "[mm:ss.xx]text" lines, with any number of time tags in front of one text
 * ("[00:12.00][01:30.00]chorus"), "[mm:ss]" and "[mm:ss:xx]" spellings, an "[offset:±ms]" tag
 * (positive = the lyrics come sooner) and enhanced-LRC word times ("<00:12.34>word"), which are
 * taken out of the text and kept as the line's [LyricLine.words]. Other tags ([ar:], [ti:], [al:], [by:], [length:] …) are skipped. A file without any
 * time tag is read as plain lyrics, one line per line. A translation travels with its line: a
 * second line at the very same time, a "[tr:...]" (or "[translation:...]") tag on the next line or
 * at the end of the line, becomes the first line's [LyricLine.translation]. Parsed by hand rather than with regexes:
 * Android's regex engine rejects patterns the JVM accepts, and these brackets are everywhere.
 */
object LrcParser {
    fun parse(text: String): Lyrics {
        val rawLines = text.removePrefix("﻿").lines()
        var offsetMs = 0L
        val timed = ArrayList<Timed>()
        // Where the last time-tagged line's entries begin, which a "[tr:...]" line after it translates.
        var lastGroupStart = 0
        val plain = ArrayList<String>()
        for (raw in rawLines) {
            val line = raw.trim()
            if (line.isEmpty()) {
                plain += ""
                continue
            }
            val stamps = ArrayList<Long>()
            var rest = line
            var isTagLine = false
            while (rest.startsWith("[")) {
                val close = rest.indexOf(']')
                if (close < 0) break
                val inside = rest.substring(1, close)
                val time = parseTime(inside)
                if (time != null) {
                    stamps += time
                } else {
                    val colon = inside.indexOf(':')
                    if (colon <= 0) break
                    val name = inside.substring(0, colon).trim().lowercase()
                    if (name == "offset") {
                        inside.substring(colon + 1).trim().removePrefix("+").toLongOrNull()?.let { offsetMs = it }
                    } else if (name in TRANSLATION_TAGS && stamps.isEmpty()) {
                        val value = inside.substring(colon + 1).trim()
                        if (value.isNotEmpty()) {
                            for (i in lastGroupStart until timed.size) timed[i] = timed[i].copy(translation = value)
                        }
                    }
                    isTagLine = true
                }
                rest = rest.substring(close + 1)
            }
            if (stamps.isNotEmpty()) {
                var body = rest
                var inlineTranslation: String? = null
                if (body.endsWith("]")) {
                    val open = TRANSLATION_TAGS.maxOf { body.lastIndexOf("[$it:") }
                    if (open >= 0) {
                        inlineTranslation = body.substring(body.indexOf(':', open) + 1, body.length - 1).trim().ifEmpty { null }
                        body = body.substring(0, open)
                    }
                }
                val lyric = stripWordTimes(body).trim()
                val words = wordTimes(body, lyric)
                lastGroupStart = timed.size
                // A line sung again later ([00:12][01:30]) keeps its words' timing from each start.
                for (stamp in stamps) {
                    val shift = stamp - stamps[0]
                    val shifted = if (shift == 0L) words else words.map { it.copy(timeMs = it.timeMs + shift) }
                    timed += Timed(stamp, lyric, shifted, inlineTranslation)
                }
            } else if (!isTagLine) {
                plain += stripWordTimes(line).trim()
            }
        }
        if (timed.isNotEmpty()) {
            val lines = timed
                .map { entry ->
                    LyricLine(
                        (entry.timeMs - offsetMs).coerceAtLeast(0L),
                        entry.text,
                        entry.words.map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0L)) },
                        entry.translation,
                    )
                }
                .sortedBy { it.timeMs }
            return Lyrics(withTranslations(lines), synced = true)
        }
        val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }.map { LyricLine(null, it) }
        return Lyrics(lines, synced = false)
    }

    private data class Timed(val timeMs: Long, val text: String, val words: List<LyricWord>, val translation: String?)

    private val TRANSLATION_TAGS = listOf("tr", "translation")

    /**
     * The common two-language .lrc: a line followed by another at exactly the same time is the
     * first one's translation. Blank or identical lines, and a line with a translation already,
     * are left as they are.
     */
    private fun withTranslations(lines: List<LyricLine>): List<LyricLine> {
        val out = ArrayList<LyricLine>(lines.size)
        for (line in lines) {
            val previous = out.lastOrNull()
            if (previous != null && previous.timeMs == line.timeMs && previous.translation == null && line.translation == null &&
                previous.text.isNotEmpty() && line.text.isNotEmpty() && previous.text != line.text && line.words.isEmpty()
            ) {
                out[out.lastIndex] = previous.copy(translation = line.text)
            } else {
                out += line
            }
        }
        return out
    }

    /** "mm:ss", "mm:ss.f…" or "mm:ss:xx" (hundredths), also with hours ("h:mm:ss.xx"); null otherwise. */
    internal fun parseTime(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size < 2 || parts.size > 3) return null
        val fractionSeparator = parts.last().indexOf('.')
        val numbers: List<String>
        val fraction: String?
        if (fractionSeparator >= 0) {
            numbers = parts.dropLast(1) + parts.last().substring(0, fractionSeparator)
            fraction = parts.last().substring(fractionSeparator + 1)
        } else if (parts.size == 3 && parts[0].length <= 3 && parts[2].length == 2 && parts[1].length == 2) {
            // "mm:ss:xx": the third part is hundredths, not seconds.
            numbers = parts.take(2)
            fraction = parts[2]
        } else {
            numbers = parts
            fraction = null
        }
        if (numbers.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        if (fraction != null && (fraction.isEmpty() || !fraction.all(Char::isDigit))) return null
        val values = numbers.map { it.toLongOrNull() ?: return null }
        val (hours, minutes, seconds) = when (values.size) {
            2 -> Triple(0L, values[0], values[1])
            else -> Triple(values[0], values[1], values[2])
        }
        if (seconds >= 60 || (values.size == 3 && minutes >= 60)) return null
        val fractionMs = fraction?.let { (it + "00").take(3).toLong() } ?: 0L
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fractionMs
    }

    /**
     * The enhanced-LRC word times of [raw] (a line's text after its time tags), each placed on
     * its word in [text] (the same line with the times taken out); empty for a line without them.
     */
    private fun wordTimes(raw: String, text: String): List<LyricWord> {
        if ('<' !in raw) return emptyList()
        val words = ArrayList<LyricWord>()
        var cursor = 0
        var time: Long? = null
        val piece = StringBuilder()
        fun flush() {
            val start = time
            val word = piece.toString().trim()
            piece.setLength(0)
            if (start == null || word.isEmpty()) return
            val at = text.indexOf(word, cursor)
            if (at < 0) return
            words += LyricWord(start, at, at + word.length)
            cursor = at + word.length
        }
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '<') {
                val close = raw.indexOf('>', i + 1)
                if (close > i) {
                    val stamp = parseTime(raw.substring(i + 1, close))
                    if (stamp != null) {
                        flush()
                        time = stamp
                        i = close + 1
                        continue
                    }
                }
            }
            piece.append(c)
            i++
        }
        flush()
        return words
    }

    /** Drops enhanced-LRC word times ("<00:12.34>"); other angle brackets stay. */
    private fun stripWordTimes(text: String): String {
        if ('<' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '<') {
                val close = text.indexOf('>', i + 1)
                if (close > i && parseTime(text.substring(i + 1, close)) != null) {
                    i = close + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString().replace("  ", " ")
    }
}
