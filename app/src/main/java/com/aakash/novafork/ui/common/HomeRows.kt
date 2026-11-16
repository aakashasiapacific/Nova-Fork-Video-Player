package com.aakash.novafork.ui.common

import androidx.compose.runtime.compositionLocalOf

/** The rows Home can show between its hero and the library links, in their default order. */
enum class HomeRow(val key: String, val label: String) {
    CONTINUE("continue", "Continue Watching"),
    UP_NEXT("upNext", "Up Next"),
    WATCHLIST("watchlist", "Watchlist"),
    ;

    companion object {
        /** [order] as saved ("upNext,continue"), with any row it misses added at the end. */
        fun ordered(order: String): List<HomeRow> {
            val saved = order.split(',').mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }.distinct()
            return saved + entries.filter { it !in saved }
        }

        fun hidden(hidden: String): Set<HomeRow> =
            hidden.split(',').mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }.toSet()

        /** The rows Home shows, in the person's order. */
        fun shown(order: String, hidden: String): List<HomeRow> = ordered(order) - hidden(hidden)
    }
}

/** Home's rows, in order, without the ones turned off (Preferences › Appearance › Home Rows). */
val LocalHomeRows = compositionLocalOf { HomeRow.entries.toList() }
