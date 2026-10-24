package com.aakash.novafork.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.aakash.novafork.R
import com.aakash.novafork.player.PlayerIntents
import com.aakash.novafork.ui.model.MediaKind
import com.aakash.novafork.ui.model.VideoCardUi
import com.aakash.novafork.ui.theme.NovaColors

/**
 * The app icon's long-press shortcuts (owner): "Continue" the video left off most recently, and
 * Search. Android's own feature: nothing is added inside the app.
 */
internal object AppShortcuts {
    const val ACTION_SEARCH = "com.aakash.novafork.action.SEARCH"
    private const val ACTION_CONTINUE = "com.aakash.novafork.action.CONTINUE"
    private const val TAG = "AppShortcuts"

    /** Sets the shortcuts; [resume] is the first Continue Watching card, or null for none. */
    fun update(context: Context, resume: VideoCardUi?) {
        val shortcuts = buildList {
            if (resume != null) {
                val title = if (resume.kind == MediaKind.EPISODE && !resume.showTitle.isNullOrBlank()) resume.showTitle else resume.title
                add(
                    ShortcutInfoCompat.Builder(context, "continue")
                        .setShortLabel(title)
                        .setLongLabel("Continue $title")
                        .setIcon(icon(context, R.drawable.ic_play_arrow))
                        .setIntent(PlayerIntents.forVideo(context, resume.id).setAction(ACTION_CONTINUE))
                        .setRank(0)
                        .build(),
                )
            }
            add(
                ShortcutInfoCompat.Builder(context, "search")
                    .setShortLabel("Search")
                    .setLongLabel("Search your library")
                    .setIcon(icon(context, R.drawable.ic_search))
                    .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION_SEARCH))
                    .setRank(1)
                    .build(),
            )
        }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: RuntimeException) {
            // Rate limits or a launcher without shortcuts: nothing in the app depends on them.
            Log.w(TAG, "Couldn't set the app shortcuts", e)
        }
    }

    /** The glyph in white on iOS blue, as an adaptive icon the launcher shapes. */
    private fun icon(context: Context, @DrawableRes glyph: Int): IconCompat {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(NovaColors.IosBlue.toArgb())
        ContextCompat.getDrawable(context, glyph)?.mutate()?.let { drawable ->
            drawable.setTint(android.graphics.Color.WHITE)
            val glyphSize = (40 * density).toInt()
            val inset = (size - glyphSize) / 2
            drawable.setBounds(inset, inset, inset + glyphSize, inset + glyphSize)
            drawable.draw(canvas)
        }
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }
}
