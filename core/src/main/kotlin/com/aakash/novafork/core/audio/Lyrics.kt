package com.aakash.novafork.core.audio

/** One line of lyrics. [timeMs] is null for lyrics without timing (a plain text file or tag). */
data class LyricLine(val timeMs: Long?, val text: String)

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
 * dropped. Other tags ([ar:], [ti:], [al:], [by:], [length:] …) are skipped. A file without any
 * time tag is read as plain lyrics, one line per line. Parsed by hand rather than with regexes:
 * Android's regex engine rejects patterns the JVM accepts, and these brackets are everywhere.
 */
object LrcParser {
    fun parse(text: String): Lyrics {
        val rawLines = text.removePrefix("﻿").lines()
        var offsetMs = 0L
        val timed = ArrayList<Pair<Long, String>>()
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
                    }
                    isTagLine = true
                }
                rest = rest.substring(close + 1)
            }
            if (stamps.isNotEmpty()) {
                val lyric = stripWordTimes(rest).trim()
                for (stamp in stamps) timed += stamp to lyric
            } else if (!isTagLine) {
                plain += stripWordTimes(line).trim()
            }
        }
        if (timed.isNotEmpty()) {
            val lines = timed
                .map { (time, lyric) -> LyricLine((time - offsetMs).coerceAtLeast(0L), lyric) }
                .sortedBy { it.timeMs }
            return Lyrics(lines, synced = true)
        }
        val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }.map { LyricLine(null, it) }
        return Lyrics(lines, synced = false)
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
