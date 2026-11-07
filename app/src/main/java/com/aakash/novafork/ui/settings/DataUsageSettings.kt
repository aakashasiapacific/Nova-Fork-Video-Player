package com.aakash.novafork.ui.settings

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import com.aakash.novafork.R

/**
 * What the app has sent and received, counted by Android for this app since it was last reset
 * (Wi-Fi and mobile together): scans of servers, artwork, streamed videos. Tapping the row starts
 * the count again.
 */
internal object DataUsage {
    private const val PREFS = "data_usage"
    private const val KEY_BASE = "base"

    private fun total(): Long {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        return rx + tx
    }

    /** Bytes since the last reset; a restart of the phone zeroes Android's counter, so the base follows. */
    fun used(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = total()
        var base = prefs.getLong(KEY_BASE, 0L)
        if (base > now) {
            base = 0L
            prefs.edit().putLong(KEY_BASE, 0L).apply()
        }
        return now - base
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_BASE, total()).apply()
    }
}

internal fun dataUsageGroup(): SettingsGroup = SettingsGroup(
    key = "data-usage",
    title = "Data",
    rows = listOf(
        SettingsRow("data-usage", "Data used", R.drawable.ic_storage) { shape -> DataUsageRow(shape) },
    ),
    footer = "Everything Nova Fork has sent and received since you last reset the count, on Wi-Fi and mobile data " +
        "(scans, artwork, streams). Tap to start again from zero.",
)

@Composable
private fun DataUsageRow(shape: Shape) {
    val context = LocalContext.current
    var used by remember { mutableLongStateOf(DataUsage.used(context)) }
    ActionRow(
        shape = shape,
        icon = R.drawable.ic_storage,
        title = "Data used",
        value = Formatter.formatShortFileSize(context, used),
        onClick = {
            DataUsage.reset(context)
            used = DataUsage.used(context)
        },
    )
}
