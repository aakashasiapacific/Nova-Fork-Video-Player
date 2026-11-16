package com.aakash.novafork.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import com.aakash.novafork.NovaApp
import com.aakash.novafork.R
import com.aakash.novafork.music.MusicPlayerState
import com.aakash.novafork.music.MusicService
import com.aakash.novafork.player.PlaybackArtwork
import com.aakash.novafork.ui.model.Artwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The home-screen Now Playing widget (owner), as Apple Music's: the song's cover big on the left,
 * its title and artist, and play/pause and next in Apple Music's red. [MusicService] hands it the
 * player's state while music is queued ([follow]); the song is kept so the widget can draw itself
 * after a restart, and with nothing queued it says so.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        NovaApp.graph(context).appScope.launch {
            try {
                render(context, manager, appWidgetIds, read(context))
            } finally {
                pending.finish()
            }
        }
    }

    /** What the widget shows; equal snapshots draw the same, so only real changes redraw it. */
    private data class Song(val title: String, val artist: String, val artUri: String?, val playing: Boolean)

    companion object {
        private const val PREFS = "widget_now_playing"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ART = "art"
        private const val KEY_PLAYING = "playing"

        /** The cover drawn last, so play and pause don't load it again. */
        @Volatile private var cover: Pair<String, Bitmap?>? = null

        /** Redraws the widget each time the song or play state in [state] changes, while [scope] lives. */
        fun follow(context: Context, state: StateFlow<MusicPlayerState>, scope: CoroutineScope) {
            val appContext = context.applicationContext
            scope.launch {
                state
                    .map { player ->
                        player.nowPlaying?.takeIf { player.active }?.let { Song(it.title, it.artist, it.artUri, player.playRequested) }
                    }
                    .distinctUntilChanged()
                    .collect { song ->
                        save(appContext, song)
                        val manager = AppWidgetManager.getInstance(appContext)
                        val ids = manager.getAppWidgetIds(ComponentName(appContext, NowPlayingWidget::class.java))
                        if (ids.isNotEmpty()) NovaApp.graph(appContext).appScope.launch { render(appContext, manager, ids, song) }
                    }
            }
        }

        private fun save(context: Context, song: Song?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                if (song == null) {
                    clear()
                } else {
                    putString(KEY_TITLE, song.title)
                    putString(KEY_ARTIST, song.artist)
                    putString(KEY_ART, song.artUri)
                    // After a restart nothing plays until asked.
                    putBoolean(KEY_PLAYING, false)
                }
            }
        }

        private fun read(context: Context): Song? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString(KEY_TITLE, null) ?: return null
            return Song(title, prefs.getString(KEY_ARTIST, "").orEmpty(), prefs.getString(KEY_ART, null), prefs.getBoolean(KEY_PLAYING, false))
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray, song: Song?) {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setOnClickPendingIntent(R.id.np_root, WidgetIntents.openApp(context))
            if (song == null) {
                views.setTextViewText(R.id.np_title, "Not Playing")
                views.setTextViewText(R.id.np_artist, "")
                views.setImageViewResource(R.id.np_art, R.drawable.widget_cover_placeholder)
                views.setViewVisibility(R.id.np_controls, View.GONE)
            } else {
                views.setTextViewText(R.id.np_title, song.title)
                views.setTextViewText(R.id.np_artist, song.artist)
                val bitmap = coverFor(context, song.artUri)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.np_art, bitmap)
                } else {
                    views.setImageViewResource(R.id.np_art, R.drawable.widget_cover_placeholder)
                }
                views.setViewVisibility(R.id.np_controls, View.VISIBLE)
                views.setImageViewResource(R.id.np_play_pause, if (song.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow)
                views.setContentDescription(R.id.np_play_pause, if (song.playing) "Pause" else "Play")
                views.setOnClickPendingIntent(
                    R.id.np_play_pause,
                    WidgetIntents.music(context, if (song.playing) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY),
                )
                views.setOnClickPendingIntent(R.id.np_next, WidgetIntents.music(context, MusicService.ACTION_NEXT))
            }
            manager.updateAppWidget(ids, views)
        }

        private suspend fun coverFor(context: Context, artUri: String?): Bitmap? {
            if (artUri == null) return null
            cover?.let { (uri, bitmap) -> if (uri == artUri) return bitmap }
            val bitmap = PlaybackArtwork(context, NovaApp.graph(context).imageLoader).load(Artwork(posterUrl = artUri))
            cover = artUri to bitmap
            return bitmap
        }
    }
}
