package com.aakash.novafork.ui.audio

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings

/**
 * Opens the system's picker for where sound plays (phone speaker, Bluetooth headphones or
 * speakers, a cast device): Android's answer to the AirPlay button in Apple Music's Now Playing.
 * Older Android without a picker opens the Bluetooth settings instead.
 */
internal fun openAudioOutputPicker(context: Context) {
    if (Build.VERSION.SDK_INT >= 34 &&
        runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
    ) {
        return
    }
    val opened = when {
        // Android 12 and 13: SystemUI's media output dialog for this app.
        Build.VERSION.SDK_INT >= 31 -> runCatching {
            context.sendBroadcast(
                Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                    .setPackage("com.android.systemui")
                    .putExtra("package_name", context.packageName),
            )
        }.isSuccess
        // Android 11: the same dialog as a Settings panel.
        Build.VERSION.SDK_INT >= 30 -> runCatching {
            context.startActivity(
                Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                    .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        else -> false
    }
    if (!opened) {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
