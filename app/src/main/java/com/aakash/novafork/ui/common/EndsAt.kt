package com.aakash.novafork.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Apple TV's "Ends at 10:42 PM": when something [remainingMs] long would finish if it played from
 * now, in the phone's 12- or 24-hour style, kept current as the clock moves; null when unknown.
 */
@Composable
fun rememberEndsAt(remainingMs: Long?): String? {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ENDS_AT_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    if (remainingMs == null || remainingMs <= 0L) return null
    return "Ends at " + format.format(Date(now + remainingMs))
}

private const val ENDS_AT_TICK_MS = 20_000L
