package com.aakash.novafork.ui.common

/**
 * A file name or path for display only: a zero-width space after every dot, underscore, dash and
 * slash, so a long name wraps at those points instead of splitting a word ("mk" / "v"). Never use
 * the result to copy, compare or save; keep the original for that.
 */
fun String.breakableName(): String = buildString(length + length / 4) {
    for (c in this@breakableName) {
        append(c)
        if (c == '.' || c == '_' || c == '-' || c == '/') append('\u200B')
    }
}

/** The name cut in the middle with an ellipsis ("Some.Long.Na…1080p.mkv") for single-line places. */
fun String.middleEllipsis(max: Int = 32): String {
    if (length <= max || max < 8) return this
    val tail = (max - 1) / 2
    val head = max - 1 - tail
    return take(head) + "\u2026" + takeLast(tail)
}
