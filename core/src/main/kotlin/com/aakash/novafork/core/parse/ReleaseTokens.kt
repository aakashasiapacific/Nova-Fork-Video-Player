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
    /** The source had an unspaced '-' right before this token ("x264-GROUP", "2019-GRP"). */
    val dashBefore: Boolean = false,
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
        "[xh][ .]?26[45]", "hevc", "avc", "av1", "xvid", "divx", "vp9", "1[02][ .-]?bits?", "hi10p?",
        // Audio
        "ddp$CH", "dd\\+$CH", "dd[ .]?[1-7][ .][01]", "(?:e-?)?ac-?3$CH", "aac$CH",
        "dts(?:-?hd)?(?:[ .-]?(?:ma|hra|es|x))?$CH", "true[ .-]?hd$CH", "atmos", "flac$CH", "mp3", "l?pcm",
        "[257]\\.[01]",
        // Dynamic range
        "hdr(?:10(?:\\+|plus)?)?", "dovi", "dolby[ .]?vision", "sdr", "hlg",
        // Multi-disc rips, film formats
        "cd\\d{1,2}", "(?:35|70)mm",
    )

    private val TAIL: Pattern = alternatives(
        "extended(?:[ .](?:cut|edition))?", "unrated", "uncut", "uncensored", "remaster(?:ed)?",
        "director['’]?s?[ .]?cut", "imax", "theatrical(?:[ .]cut)?", "special[ .]edition", "criterion",
        "proper", "repack", "rerip", "internal", "limited", "readnfo", "dubbed", "subbed", "hardsubs?",
        "multi(?:[ .-]?subs?)?", "dual[ .-]?audio", "dual",
        "hindi", "tamil", "telugu", "malayalam", "kannada", "bengali", "eng", "ita", "vostfr", "truefrench",
    )

    // "Opus" and "8-Bit" are real title words ("Mr. Holland's Opus", "8-Bit Christmas").
    private val WEAK: Pattern = alternatives(
        "web", "cam", "ts", "nf", "dv", "hulu", "opus", "8[ .-]?bits?",
        "french", "german", "spanish", "italian", "japanese", "korean", "chinese", "russian",
    )

    private val YEAR: Pattern = Pattern.compile("\\d{4}$END")
    private val PAREN_YEAR: Pattern = Pattern.compile("\\(\\d{4}\\)")

    private val BRACKETS = Regex("\\[([^\\[\\]]*)]|\\{([^{}]*)}|【([^【】]*)】")
    private val PARENS = Regex("\\(([^()]*)\\)")
    private val SPACES = Regex("\\s+")
    private val GROUP_SUFFIX = Regex("^(.{2,}?)-([A-Z0-9]{2,})$")

    /** "[05]" / "[12v2]": an anime episode number in brackets, kept as " - 05 ". */
    private val BRACKET_EPISODE = Regex("\\d{1,3}(?:v\\d)?")

    private val RESOLUTION = Regex("(?<![\\p{L}\\p{N}])(\\d{3,4})[pPiI](?![\\p{L}\\p{N}])")
    private val UHD = Regex("(?<![\\p{L}\\p{N}])(?:4k|uhd)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val FRAME_SIZE = Regex("(?<![\\p{L}\\p{N}])\\d{3,4}x(\\d{3,4})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val KNOWN_HEIGHTS = setOf(240, 360, 480, 540, 576, 720, 1080, 1440, 2160, 4320)

    /** Abbreviations that keep their dot in a title ("Mr. Robot", "Kill Bill Vol. 1"). */
    private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "jr", "sr", "st", "vol", "vs", "prof", "sgt", "lt")

    private fun alternatives(vararg items: String): Pattern =
        Pattern.compile("(?:" + items.joinToString("|") + ")" + END, FLAGS)

    fun isPlausibleYear(year: Int): Boolean = year in MIN_YEAR..MAX_YEAR

    fun isYearText(text: String): Boolean =
        text.length == 4 && text.all { it in '0'..'9' } && isPlausibleYear(text.toInt())

    /** "2160p" for 2160p/4K/UHD, else the first "NNNp" (or "1920x1080" frame size) in [text]. */
    fun resolution(text: String): String? {
        RESOLUTION.findAll(text).forEach { match ->
            val height = match.groupValues[1].toInt()
            if (height in KNOWN_HEIGHTS) return "${height}p"
        }
        if (UHD.containsMatchIn(text)) return "2160p"
        FRAME_SIZE.findAll(text).forEach { match ->
            val height = match.groupValues[1].toInt()
            if (height in KNOWN_HEIGHTS) return "${height}p"
        }
        return null
    }

    /**
     * Drops [..] / {..} groups and (..) groups except a "(year)" ("[2019]" becomes "(2019)"), a
     * country code ("(US)" becomes "US") or a bracketed episode number ("[05]" becomes " - 05 ");
     * underscores become spaces.
     */
    fun prepare(raw: String): Prepared {
        val trimmed = raw.trim()
        val leadingGroup = trimmed.startsWith("[") || trimmed.startsWith("【")
        var junk = false
        val withoutBrackets = BRACKETS.replace(trimmed) { match ->
            val inner = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty().trim()
            when {
                isYearText(inner) -> " ($inner) "
                BRACKET_EPISODE.matches(inner) -> " - $inner "
                else -> {
                    if (containsJunk(inner)) junk = true
                    " "
                }
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
            val dashBefore = i > 0 && text[i - 1].isDash() && (i == 1 || text[i - 2] != ' ')
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
                tokens += Token(text.substring(i, end), TokenType.JUNK, strength = junkStrength, dashBefore = dashBefore)
                i = end
                continue
            }
            end = year.endAt(i)
            if (end > 0 && isYearText(text.substring(i, end))) {
                tokens += Token(
                    text.substring(i, end),
                    TokenType.YEAR,
                    dotAfter = end < n && text[end] == '.',
                    dashBefore = dashBefore,
                )
                i = end
                continue
            }
            var j = i
            while (j < n && text[j] != ' ' && text[j] != '.' && text[j] != '_') j++
            val word = text.substring(i, j).trimEnd('-', '–', '—')
            if (word.isNotEmpty()) {
                tokens += Token(word, TokenType.WORD, dotAfter = j < n && text[j] == '.', dashBefore = dashBefore)
            }
            i = j
        }
        return tokens
    }

    /**
     * For each token: does it count as release junk here? Strong junk always does; tail junk at
     * the end or before more junk; weak junk only before more junk. Upper-case flags in an
     * otherwise mixed-case name ("Movie.EXTENDED.2019") count too, and so does a scene group
     * glued to the end of the name ("…x264-GROUP", "…2019-GRP").
     */
    private fun junkFlags(tokens: List<Token>, mixedCase: Boolean): BooleanArray {
        val flags = BooleanArray(tokens.size)
        var nextIsJunk: Boolean? = null // null: nothing follows
        for (i in tokens.indices.reversed()) {
            val token = tokens[i]
            if (token.type == TokenType.DASH) continue
            flags[i] = when (token.type) {
                TokenType.JUNK -> when (token.strength) {
                    JunkStrength.STRONG -> true
                    JunkStrength.TAIL -> (nextIsJunk ?: true) || (mixedCase && token.isUpperCase)
                    JunkStrength.WEAK, null -> nextIsJunk == true || (mixedCase && token.isUpperCase)
                }
                TokenType.WORD -> nextIsJunk == null && token.dashBefore && isGroupTag(tokens, i)
                TokenType.YEAR, TokenType.DASH -> false
            }
            nextIsJunk = flags[i]
        }
        return flags
    }

    /** "x264-GROUP", "2019-GRP", or an upper-case tag right after an episode marker ("S01E01-GRP"). */
    private fun isGroupTag(tokens: List<Token>, index: Int): Boolean {
        val previous = tokens.getOrNull(index - 1) ?: return tokens[index].text.none { it.isLowerCase() }
        return previous.type == TokenType.JUNK || previous.type == TokenType.YEAR
    }

    /**
     * Title and year of a name segment: the year is the last plausible one that is followed by
     * junk or the end (or any "(year)"), never the first token; the title ends at that year or at
     * the first junk token, whichever comes first.
     */
    fun analyze(text: String): TitleInfo = analyze(text, yearAtEnd = true)

    private fun analyze(text: String, yearAtEnd: Boolean): TitleInfo {
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
            val followedOk = if (next < 0) yearAtEnd else junk[next]
            if (token.paren || (i < firstStrong && followedOk)) yearIndex = i
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

    /** True when [text] starts (after dashes) with a plausible year: "Movie - 2 (2019)" is no episode. */
    fun startsWithYear(text: String): Boolean =
        tokenize(text).firstOrNull { it.type != TokenType.DASH }?.type == TokenType.YEAR

    /**
     * cleanTitle: drops brackets and junk; a year goes only when it is a "(year)" or release junk
     * follows it, so "Wonder Woman 1984" stays whole while "Inception (2010)" loses its year.
     */
    fun clean(raw: String): String = analyze(prepare(raw).text, yearAtEnd = false).title

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
