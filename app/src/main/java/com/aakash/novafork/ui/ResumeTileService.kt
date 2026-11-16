package com.aakash.novafork.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aakash.novafork.R
import com.aakash.novafork.player.PlayerIntents
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.VideoCardUi

/**
 * A Quick Settings tile that resumes the first Continue Watching video in one tap, from the
 * notification shade. It reads what [remember] last stored (the app shortcuts' "Continue"),
 * and is greyed out when nothing is in progress.
 */
class ResumeTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val title = prefs(this).getString(KEY_TITLE, null)
        tile.label = "Resume"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = title ?: "Nothing in progress"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_play_arrow)
        tile.state = if (title != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val id = prefs(this).getLong(KEY_ID, -1L).takeIf { it > 0 } ?: return
        val intent = PlayerIntents.forVideo(this, id).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val PREFS = "resume_tile"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Keeps the video the tile resumes ([resume] null: nothing) and refreshes the tile. */
        fun remember(context: Context, resume: VideoCardUi?) {
            val edit = prefs(context).edit()
            if (resume == null) {
                edit.remove(KEY_ID).remove(KEY_TITLE)
            } else {
                val title = if (resume.kind == MediaKind.EPISODE && !resume.showTitle.isNullOrBlank()) resume.showTitle else resume.title
                edit.putLong(KEY_ID, resume.id).putString(KEY_TITLE, title)
            }
            edit.apply()
            try {
                requestListeningState(context, ComponentName(context, ResumeTileService::class.java))
            } catch (e: Exception) {
                // Not added to the shade, or the system refused: it reads the value next time it shows.
            }
        }
    }
}
