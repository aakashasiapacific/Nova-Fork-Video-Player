package com.aakash.novafork.player

import android.content.Intent

/** What the player should open, read from the intent that started it (see [PlayerIntents]). */
data class PlayRequest(
    /** A library video: its URI, resume point, tracks and neighbours come from the library. */
    val videoId: Long? = null,
    /** Any playable URI (another app's ACTION_VIEW, a stream URL). A matching library entry is reused. */
    val uri: String? = null,
    val title: String? = null,
    val startOver: Boolean = false,
    /** Exact position to continue from after the activity was re-created; shows no resume banner. */
    val startPositionMs: Long? = null,
) {
    companion object {
        /** VLC and MX Player read a plain "title" extra, so apps that hand off videos often send it. */
        private const val EXTRA_TITLE_COMPAT = "title"

        fun fromIntent(intent: Intent): PlayRequest? {
            val uri = intent.data?.toString()?.takeIf { it.isNotBlank() }
            return try {
                val videoId = intent.getLongExtra(PlayerIntents.EXTRA_VIDEO_ID, 0L).takeIf { it > 0 }
                if (videoId == null && uri == null) return null
                val title = intent.getStringExtra(PlayerIntents.EXTRA_TITLE)
                    ?: intent.getStringExtra(EXTRA_TITLE_COMPAT)
                PlayRequest(
                    videoId = videoId,
                    uri = uri,
                    title = title?.takeIf { it.isNotBlank() },
                    startOver = intent.getBooleanExtra(PlayerIntents.EXTRA_START_OVER, false),
                )
            } catch (e: RuntimeException) {
                // Extras from another app can fail to unparcel; the URI alone is enough to play.
                uri?.let { PlayRequest(uri = it) }
            }
        }
    }
}
