package com.aakash.novafork.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import com.aakash.novafork.NovaApp
import com.aakash.novafork.R
import com.aakash.novafork.player.PlaybackArtwork
import com.aakash.novafork.player.PlayerIntents
import com.aakash.novafork.ui.model.Artwork
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.phone.displayTitle
import kotlinx.coroutines.launch

/**
 * The home-screen Up Next widget: the next episode waiting for you, big in its own picture, with its
 * title and number; a tap plays it. The app hands it the first Up Next card whenever that changes
 * ([update]); the card is kept so the widget can draw itself again after a restart.
 */
class UpNextWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        NovaApp.graph(context).appScope.launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val PREFS = "widget_up_next"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_CAPTION = "caption"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_STILL = "still"
        private const val KEY_BACKDROP = "backdrop"
        private const val KEY_POSTER = "poster"
        private const val KEY_FRAME = "frame"
        private const val REQUEST_OPEN = 11

        /** The first Up Next card, or null when no episode is waiting. */
        fun update(context: Context, card: VideoCardUi?) {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                if (card == null) {
                    clear()
                } else {
                    putLong(KEY_ID, card.id)
                    putString(KEY_TITLE, card.displayTitle())
                    putString(KEY_CAPTION, card.subtitle)
                    putFloat(KEY_PROGRESS, 0f)
                    putString(KEY_STILL, card.artwork.stillUrl)
                    putString(KEY_BACKDROP, card.artwork.backdropUrl)
                    putString(KEY_POSTER, card.artwork.posterUrl)
                    putString(KEY_FRAME, card.artwork.frameSource)
                }
            }
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, UpNextWidget::class.java))
            if (ids.isEmpty()) return
            NovaApp.graph(appContext).appScope.launch { render(appContext, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_continue_watching)
            val id = prefs.getLong(KEY_ID, -1L)
            if (id < 0) {
                views.setViewVisibility(R.id.cw_content, View.GONE)
                views.setViewVisibility(R.id.cw_empty, View.VISIBLE)
                views.setImageViewBitmap(R.id.cw_art, null)
                views.setTextViewText(R.id.cw_empty, context.getString(R.string.widget_up_next_empty))
                views.setOnClickPendingIntent(R.id.cw_root, WidgetIntents.openApp(context))
            } else {
                views.setViewVisibility(R.id.cw_content, View.VISIBLE)
                views.setViewVisibility(R.id.cw_empty, View.GONE)
                views.setTextViewText(R.id.cw_title, prefs.getString(KEY_TITLE, "").orEmpty())
                val caption = prefs.getString(KEY_CAPTION, null)
                views.setTextViewText(R.id.cw_caption, caption.orEmpty())
                views.setViewVisibility(R.id.cw_caption, if (caption.isNullOrBlank()) View.GONE else View.VISIBLE)
                val progress = (prefs.getFloat(KEY_PROGRESS, 0f).coerceIn(0f, 1f) * PROGRESS_MAX).toInt()
                views.setProgressBar(R.id.cw_progress, PROGRESS_MAX, progress, false)
                val artwork = Artwork(
                    posterUrl = prefs.getString(KEY_POSTER, null),
                    backdropUrl = prefs.getString(KEY_BACKDROP, null),
                    stillUrl = prefs.getString(KEY_STILL, null),
                    frameSource = prefs.getString(KEY_FRAME, null),
                )
                val bitmap = PlaybackArtwork(context, NovaApp.graph(context).imageLoader).load(artwork)
                views.setImageViewBitmap(R.id.cw_art, bitmap)
                val play = PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    PlayerIntents.forVideo(context, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                views.setOnClickPendingIntent(R.id.cw_root, play)
            }
            manager.updateAppWidget(ids, views)
        }

        private const val PROGRESS_MAX = 1000
    }
}
