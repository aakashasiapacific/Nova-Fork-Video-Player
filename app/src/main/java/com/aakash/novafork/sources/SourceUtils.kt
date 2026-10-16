package com.aakash.novafork.sources

import android.database.Cursor

/** Scanners never walk deeper than this below the source root. */
internal const val MAX_FOLDER_DEPTH = 12

/** SQLite allows 999 bound arguments per statement; stay well below. */
internal const val SQL_IN_LIMIT = 900

internal fun Cursor.stringAt(column: Int): String? =
    if (column < 0 || isNull(column)) null else getString(column)

internal fun Cursor.longAt(column: Int): Long? =
    if (column < 0 || isNull(column)) null else getLong(column)

internal fun Cursor.intAt(column: Int): Int? =
    if (column < 0 || isNull(column)) null else getInt(column)
