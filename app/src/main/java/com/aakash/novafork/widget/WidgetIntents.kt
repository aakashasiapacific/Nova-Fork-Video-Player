package com.aakash.novafork.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aakash.novafork.music.MusicService

/** What the widgets' taps do: open the app, or drive the music the way its notification does. */
internal object WidgetIntents {
    private const val REQUEST_OPEN_APP = 20
    private const val REQUEST_MUSIC = 21

    fun openApp(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** A [MusicService] action; started in the foreground, as it may not be running. */
    fun music(context: Context, action: String): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            REQUEST_MUSIC + action.hashCode(),
            Intent(context, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
