package com.aakash.novafork.core.parse

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

internal enum class TokenType { WORD, YEAR, JUNK, DASH }

internal enum class JunkStrength {
    /** Never part of a title: resolutions, sources, codecs, audio, HDR flags. */
    STRONG,

    /** Release flags that may also end a name ("EXTENDED", "PROPER", "HINDI"). */
    TAIL,

    /** Ordinary words too ("Web", "Cam", "French"): junk only when more junk follows. */
    WEAK,
}

internal class Token(
    val text: String,
    val type: TokenType,
    val strength: JunkStrength? = null,
    /** A "(1999)" year. */
    val paren: Boolean = false,
    /** The source had a '.' right after this token ("Mr.", "Vol.", "S.H.I.E.L.D."). */
    val dotAfter: Boolean = false,
) {
    val year: Int? get() = if (type == TokenType.YEAR) text.trim('(', ')').toIntOrNull() else null

    val isUpperCase: Boolean
        get() = text.count { it.isLetter() } >= 2 && text.none { it.isLowerCase() }
}

/** A name after bracket/paren clean-up, ready for tokenizing and marker search. */
internal class Prepared(
    val text: String,
    /** A dropped [..] or (..) group held release junk ("[1080p]", "(BluRay x265)"). */
    val bracketJunk: Boolean,
    /** The name started with a "[Group]" tag (anime fansub style). */
    val leadingGroup: Boolean,
)

/** Title, year and junk found in one name segment (no episode marker inside). */
internal class TitleInfo(val title: String, val year: Int?, val hasJunk: Boolean)

/** Tokenizer and title rules shared by file and folder parsing. */
internal object ReleaseTokens {
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2035

    /** Two-letter suffixes that distinguish versions of a show ("The Office US"). */
    val COUNTRY_CODES: Set<String> = setOf(
        "US", "UK", "AU", "NZ", "CA", "IE", "IN", "FR", "DE", "JP", "KR", "ES", "IT", "NL", "SE", "DK", "NO", "BR", "MX",
    )

    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    private const val END = "(?![\\p{L}\\p{N}])"

    /** Optional channel layout glued to an audio codec: "DDP5.1", "AAC 2.0", "DD+7.1". */
    private const val CH = "(?:[ .]?[1-7][ .][01])?"

    private val STRONG: Pattern = alternatives(
        // Resolution
        "\\d{3,4}[pi]", "[48]k", "uhd", "\\d{3,4}x\\d{3,4}",
        // Source / streaming service
        "web[ .-]?dl", "web[ .-]?rip", "blu[ .-]?ray", "b[dr][ .-]?rip", "bd[ .-]?remux", "remux", "bd(?:25|50)",
        "hdtv(?:rip)?", "pdtv", "sdtv", "dvd[ .-]?(?:rip|scr|r)", "dvd[59]?", "hd[ .-]?rip", "hd[ .-]?cam", "hd[ .-]?ts",
        "telesync", "telecine", "amzn", "dsnp", "hmax", "atvp", "pcok", "pmtp",
        // Video codec
        "[xh][ .]?26[45]", "hevc", "avc", "av1", "xvid", "divx", "vp9", "1[02][ .-]?bits?", "8[ .-]?bits?", "hi10p?",
        // Audio
        "ddp$CH", "dd\\+$CH", "dd[ .]?[1-7][ .][01]", "e-?ac-?3$CH", "aac$CH",
        "dts(?:-?hd)?(?:[ .-]?(?:ma|hra|es|x))?$CH", "true[ .-]?hd$CH", "atmos", "flac$CH", "mp3", "opus", "l?pcm",
        "[257]\\.[01]",
        // Dynamic range
        "hdr(?:10(?:\\+|plus)?)?", "dovi", "dolby[ .]?vision", "sdr", "hlg",
        // Multi-disc rips
        "cd\\d{1,2}",
    )

    private val TAIL: Pattern = alternatives(
        "extended(?:[ .](?:cut|edition))?", "unrated", "uncut", "uncensored", "remaster(?:ed)?",
        "director['’]?s?[ .]?cut", "imax", "theatrical(?:[ .]cut)?", "special[ .]edition", "criterion",
        "proper", "repack", "rerip", "internal", "limited", "readnfo", "dubbed", "subbed", "hardsubs?",
        "multi(?:[ .-]?subs?)?", "dual[ .-]?audio", "dual",
        "hindi", "tamil", "telugu", "malayalam", "kannada", "bengali", "eng", "ita", "vostfr", "truefrench",
    )

    private val WEAK: Pattern = alternatives(
        "web", "cam", "ts", "nf", "dv", "hulu",
        "french", "german", "spanish", "italian", "japanese", "korean", "chinese", "russian",
    )

    private val YEAR: Pattern = Pattern.compile("\\d{4}$END")
    private val PAREN_YEAR: Pattern = Pattern.compile("\\(\\d{4}\\)")

    private val BRACKETS = Regex("\\[([^\\[\\]]*)]|\\{([^{}]*)}|【([^【】]*)】")
    private val PARENS = Regex("\\(([^()]*)\\)")
    private val SPACES = Regex("\\s+")
    private val GROUP_SUFFIX = Regex("^(.{2,}?)-([A-Z0-9]{2,})$")

    /** Abbreviations that keep their dot in a title ("Mr. Robot", "Kill Bill Vol. 1"). */
    private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "jr", "sr", "st", "vol", "vs", "prof", "sgt", "lt")

    private fun alternatives(vararg items: String): Pattern =
        Pattern.compile("(?:" + items.joinToString("|") + ")" + END, FLAGS)

    fun isPlausibleYear(year: Int): Boolean = year in MIN_YEAR..MAX_YEAR

    private fun isYearText(text: String): Boolean =
        text.length == 4 && text.all { it in '0'..'9' } && isPlausibleYear(text.toInt())

    /**
     * Drops [..] / {..} groups and (..) groups except a "(year)" ("[2019]" becomes "(2019)") or a
     * country code ("(US)" becomes "US"); underscores become spaces.
     */
    fun prepare(raw: String): Prepared {
        val trimmed = raw.trim()
        val leadingGroup = trimmed.startsWith("[") || trimmed.startsWith("【")
        var junk = false
        val withoutBrackets = BRACKETS.replace(trimmed) { match ->
            val inner = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty().trim()
            if (isYearText(inner)) {
                " ($inner) "
            } else {
                if (containsJunk(inner)) junk = true
                " "
            }
        }
        val withoutParens = PARENS.replace(withoutBrackets) { match ->
            val inner = match.groupValues[1].trim()
            when {
                isYearText(inner) -> " ($inner) "
                inner in COUNTRY_CODES -> " $inner "
                else -> {
                    if (containsJunk(inner)) junk = true
                    " "
                }
            }
        }
        val text = withoutParens.replace('_', ' ').replace(SPACES, " ").trim()
        return Prepared(text, junk, leadingGroup)
    }

    private fun containsJunk(text: String): Boolean = tokenize(text).any { it.type == TokenType.JUNK }

    fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        val parenYear = PAREN_YEAR.matcher(text).transparent()
        val strong = STRONG.matcher(text).transparent()
        val tail = TAIL.matcher(text).transparent()
        val weak = WEAK.matcher(text).transparent()
        val year = YEAR.matcher(text).transparent()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == ' ' || c == '.' || c == '_') {
                i++
                continue
            }
            if (c.isDash()) {
                // Only a spaced dash separates title parts ("Title - Subtitle"); "Spider-Man" is one
                // word and "x264-GROUP" just ends the junk token.
                val spacedBefore = i == 0 || text[i - 1] == ' '
                val spacedAfter = i + 1 == n || text[i + 1] == ' '
                if (spacedBefore && spacedAfter) tokens += Token("-", TokenType.DASH)
                i++
                continue
            }
            var end = parenYear.endAt(i)
            if (end > 0) {
                val inner = text.substring(i + 1, end - 1)
                if (isYearText(inner)) {
                    tokens += Token(text.substring(i, end), TokenType.YEAR, paren = true)
                    i = end
                    continue
                }
            }
            val junkStrength = when {
                strong.endAt(i).also { end = it } > 0 -> JunkStrength.STRONG
                tail.endAt(i).also { end = it } > 0 -> JunkStrength.TAIL
                weak.endAt(i).also { end = it } > 0 -> JunkStrength.WEAK
                else -> null
            }
            if (junkStrength != null) {
                tokens += Token(text.substring(i, end), TokenType.JUNK, strength = junkStrength)
                i = end
                continue
            }
            end = year.endAt(i)
            if (end > 0 && isYearText(text.substring(i, end))) {
                tokens += Token(text.substring(i, end), TokenType.YEAR, dotAfter = end < n && text[end] == '.')
                i = end
                continue
            }
            var j = i
            while (j < n && text[j] != ' ' && text[j] != '.' && text[j] != '_') j++
            val word = text.substring(i, j).trimEnd('-', '–', '—')
            if (word.isNotEmpty()) {
                tokens += Token(word, TokenType.WORD, dotAfter = j < n && text[j] == '.')
            }
            i = j
        }
        return tokens
    }

    /**
     * For each token: does it count as release junk here? Strong junk always does; tail junk at
     * the end or before more junk; weak junk only before more junk. Upper-case flags in an
     * otherwise mixed-case name ("Movie.EXTENDED.2019") count too.
     */
    private fun junkFlags(tokens: List<Token>, mixedCase: Boolean): BooleanArray {
        val flags = BooleanArray(tokens.size)
        var nextIsJunk: Boolean? = null // null: nothing follows
        for (i in tokens.indices.reversed()) {
            val token = tokens[i]
            if (token.type == TokenType.DASH) continue
            flags[i] = token.type == TokenType.JUNK && when (token.strength) {
                JunkStrength.STRONG -> true
                JunkStrength.TAIL -> nextIsJunk ?: true || (mixedCase && token.isUpperCase)
                JunkStrength.WEAK, null -> nextIsJunk == true || (mixedCase && token.isUpperCase)
            }
            nextIsJunk = flags[i]
        }
        return flags
    }

    /**
     * Title and year of a name segment: the year is the last plausible one that is followed by
     * junk or the end (or any "(year)"), never the first token; the title ends at that year or at
     * the first junk token, whichever comes first.
     */
    fun analyze(text: String): TitleInfo {
        val tokens = tokenize(text)
        val first = tokens.indexOfFirst { it.type != TokenType.DASH }
        if (first < 0) return TitleInfo("", null, false)
        val junk = junkFlags(tokens, text.isMixedCase())
        val firstStrong = tokens.indexOfFirst { it.strength == JunkStrength.STRONG }.let { if (it < 0) tokens.size else it }

        var yearIndex = -1
        for (i in first + 1 until tokens.size) {
            val token = tokens[i]
            if (token.type != TokenType.YEAR) continue
            val next = nextNonDash(tokens, i)
            if (token.paren || (i < firstStrong && (next < 0 || junk[next]))) yearIndex = i
        }
        val firstJunk = (first + 1 until tokens.size).firstOrNull { junk[it] } ?: -1
        val cut = listOf(yearIndex, firstJunk).filter { it >= 0 }.minOrNull() ?: tokens.size
        val title = format(tokens.subList(0, cut), stripGroup = cut == tokens.size && ' ' !in text)
        return TitleInfo(title, tokens.getOrNull(yearIndex)?.year, firstJunk >= 0)
    }

    /** Episode title after a marker: cut at the first junk or year token, null when nothing is left. */
    fun episodeTitle(text: String): String? {
        val tokens = tokenize(text)
        val junk = junkFlags(tokens, text.isMixedCase())
        val cut = tokens.indices.firstOrNull { junk[it] || tokens[it].type == TokenType.YEAR } ?: tokens.size
        return format(tokens.subList(0, cut), stripGroup = false).takeIf { title -> title.any { it.isLetterOrDigit() } }
    }

    /** cleanTitle: cut at the first junk token (never the first token) and format. */
    fun clean(raw: String): String {
        val prepared = prepare(raw)
        val tokens = tokenize(prepared.text)
        val first = tokens.indexOfFirst { it.type != TokenType.DASH }
        if (first < 0) return ""
        val junk = junkFlags(tokens, prepared.text.isMixedCase())
        val cut = (first + 1 until tokens.size).firstOrNull { junk[it] } ?: tokens.size
        return format(tokens.subList(0, cut), stripGroup = cut == tokens.size && ' ' !in prepared.text)
    }

    private fun nextNonDash(tokens: List<Token>, index: Int): Int {
        for (i in index + 1 until tokens.size) if (tokens[i].type != TokenType.DASH) return i
        return -1
    }

    /**
     * Joins title tokens with spaces: dotted acronyms stay together ("S.H.I.E.L.D."), known
     * abbreviations keep their dot ("Mr."), and a scene "-GROUP" suffix on the last word goes.
     */
    private fun format(tokens: List<Token>, stripGroup: Boolean): String {
        var from = 0
        var to = tokens.size
        while (from < to && tokens[from].type == TokenType.DASH) from++
        while (to > from && tokens[to - 1].type == TokenType.DASH) to--
        val parts = ArrayList<String>()
        var k = from
        while (k < to) {
            val token = tokens[k]
            if (token.isSingleLetter() && token.dotAfter) {
                var e = k + 1
                while (e < to && tokens[e].isSingleLetter() && tokens[e - 1].dotAfter) e++
                if (e - k >= 2) {
                    val letters = tokens.subList(k, e).joinToString(".") { it.text }
                    parts += if (tokens[e - 1].dotAfter) "$letters." else letters
                    k = e
                    continue
                }
            }
            val isLast = k == to - 1
            parts += when {
                token.type == TokenType.WORD && token.dotAfter &&
                    token.text.lowercase(Locale.ROOT) in ABBREVIATIONS -> token.text + "."
                stripGroup && isLast && k > from && token.type == TokenType.WORD ->
                    GROUP_SUFFIX.matchEntire(token.text)?.groupValues?.get(1) ?: token.text
                else -> token.text
            }
            k++
        }
        return parts.joinToString(" ").replace(SPACES, " ").trim()
    }

    private fun Token.isSingleLetter(): Boolean = type == TokenType.WORD && text.length == 1 && text[0].isLetter()

    private fun Char.isDash(): Boolean = this == '-' || this == '–' || this == '—'

    private fun String.isMixedCase(): Boolean = any { it.isLowerCase() } && any { it.isUpperCase() }

    private fun Matcher.transparent(): Matcher = useTransparentBounds(true).useAnchoringBounds(false)

    /** End of a match starting exactly at [start], or -1. */
    private fun Matcher.endAt(start: Int): Int {
        region(start, regionEnd().coerceAtLeast(start))
        return if (lookingAt()) end() else -1
    }
}
