package com.aakash.novafork.player

import android.content.Context
import android.content.Intent
import android.net.Uri

/** How the rest of the app starts playback. PlayerActivity reads these extras. */
object PlayerIntents {
    const val EXTRA_VIDEO_ID = "com.aakash.novafork.extra.VIDEO_ID"
    const val EXTRA_START_OVER = "com.aakash.novafork.extra.START_OVER"
    const val EXTRA_TITLE = "com.aakash.novafork.extra.TITLE"

    /** Plays a library video (resume position, tracks, next episode, credentials all come from the library). */
    fun forVideo(context: Context, videoId: Long, startOver: Boolean = false): Intent =
        Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_VIDEO_ID, videoId)
            .putExtra(EXTRA_START_OVER, startOver)

    /** Plays any URI once (stream URL, content:// from another app). Library entry used if one matches. */
    fun forUri(context: Context, uri: String, title: String? = null): Intent =
        Intent(context, PlayerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(uri))
            .putExtra(EXTRA_TITLE, title)
}
