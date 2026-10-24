package com.aakash.novafork.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/** What is typed in Search, for results to show it in bold; empty everywhere else. */
val LocalSearchQuery = compositionLocalOf { "" }

/**
 * [text] with the words typed in Search set in bold wherever they appear, as iOS shows its
 * search results; plain outside Search.
 */
@Composable
fun searchHighlighted(text: String): AnnotatedString {
    val query = LocalSearchQuery.current
    return remember(text, query) { highlightMatches(text, query) }
}

/** [text] with every case-insensitive occurrence of each word of [query] in bold. */
fun highlightMatches(text: String, query: String): AnnotatedString {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return AnnotatedString(text)
    val bold = BooleanArray(text.length)
    for (word in words) {
        var from = text.indexOf(word, ignoreCase = true)
        while (from >= 0) {
            for (i in from until from + word.length) bold[i] = true
            from = text.indexOf(word, startIndex = from + word.length, ignoreCase = true)
        }
    }
    return buildAnnotatedString {
        append(text)
        var start = -1
        for (i in 0..text.length) {
            val on = i < text.length && bold[i]
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, i)
                start = -1
            }
        }
    }
}
