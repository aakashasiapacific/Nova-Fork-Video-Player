package com.aakash.novafork.player

import android.content.Context
import android.content.Intent
import android.net.Uri

/** How the rest of the app starts playback. PlayerActivity reads these extras. */
object PlayerIntents {
    const val EXTRA_VIDEO_ID = "com.aakash.novafork.extra.VIDEO_ID"
    const val EXTRA_START_OVER = "com.aakash.novafork.extra.START_OVER"
    const val EXTRA_TITLE = "com.aakash.novafork.extra.TITLE"
    const val EXTRA_QUEUE = "com.aakash.novafork.extra.QUEUE"
    const val EXTRA_QUEUE_INDEX = "com.aakash.novafork.extra.QUEUE_INDEX"
    const val EXTRA_SHUFFLE = "com.aakash.novafork.extra.SHUFFLE"

    /** Brings back the player of what is already playing (the playback notification's tap); opens nothing new. */
    const val ACTION_SHOW_PLAYER = "com.aakash.novafork.action.SHOW_PLAYER"

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

    /**
     * Plays library videos one after the other, starting with [startIndex] of [videoIds]; with
     * [shuffle], the rest follow in random order. Each one resumes where it was left, like [forVideo].
     */
    fun forQueue(context: Context, videoIds: LongArray, startIndex: Int = 0, shuffle: Boolean = false): Intent {
        val start = if (videoIds.isEmpty()) 0 else startIndex.coerceIn(0, videoIds.lastIndex)
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_QUEUE, videoIds)
            .putExtra(EXTRA_QUEUE_INDEX, start)
            .putExtra(EXTRA_SHUFFLE, shuffle)
        // A reader that only knows single videos still plays the first one.
        videoIds.getOrNull(start)?.let { intent.putExtra(EXTRA_VIDEO_ID, it) }
        return intent
    }

    /** Opens the player on what is playing now, from outside an activity (the notification). */
    internal fun showPlayer(context: Context): Intent =
        Intent(context, PlayerActivity::class.java)
            .setAction(ACTION_SHOW_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
