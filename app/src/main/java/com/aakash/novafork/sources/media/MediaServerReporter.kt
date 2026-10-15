package com.aakash.novafork.sources.media

import android.os.SystemClock
import com.aakash.novafork.data.RemoteProgressReporter
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** One media server's playback API, as [MediaServerReporter] drives it. Failures throw. */
internal interface MediaServerPlayback {
    /** Playback of [video] began (or resumed after a stop) at [positionMs]. */
    suspend fun start(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?)

    suspend fun progress(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?)

    /** Playback ended at [positionMs]: the server keeps it as the resume point. */
    suspend fun stop(source: SourceEntity, video: VideoEntity, positionMs: Long, durationMs: Long?)

    suspend fun setWatched(source: SourceEntity, video: VideoEntity, watched: Boolean)
}

/**
 * Turns the library's progress saves into a media server's playback session: the first save of a
 * video starts it, later ones become progress reports (at most one per [progressIntervalMs]), and
 * the session stops when the video finishes, another video starts, or no save came for
 * [idleStopMs] (the player was paused or closed) — then with the last position, so the server
 * resumes where the person stopped. A save at 0 with no session running is the person marking
 * the video unwatched (savePlayback writes lastPlayedAt; setWatched doesn't). One player plays
 * at a time, so one session is tracked across [servers].
 */
internal class MediaServerReporter(
    private val servers: Map<SourceType, MediaServerPlayback>,
    /** Runs the idle stop; it must outlive the player (the app's scope). */
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    private val progressIntervalMs: Long = PROGRESS_INTERVAL_MS,
    private val idleStopMs: Long = IDLE_STOP_MS,
) : RemoteProgressReporter {

    private class Session(
        val videoId: Long,
        val server: MediaServerPlayback,
        var source: SourceEntity,
        var video: VideoEntity,
        var positionMs: Long,
        var durationMs: Long?,
        /** [clock] time of the last report the server took. */
        var sentAt: Long,
    ) {
        var idleStop: Job? = null
    }

    private val mutex = Mutex()

    /** The video playing now; read and written with [mutex] held. */
    private var current: Session? = null

    override suspend fun report(
        source: SourceEntity,
        video: VideoEntity,
        positionMs: Long,
        durationMs: Long?,
        finished: Boolean,
    ) {
        val server = servers[source.type] ?: return
        mutex.withLock {
            val position = positionMs.coerceAtLeast(0L)
            val running = current
            val active = running?.takeIf { it.videoId == video.id && it.server === server }
            // Another video playing means the previous one stopped.
            if (running != null && active == null) closeCurrent()
            when {
                finished -> {
                    if (active != null || position > 0L) {
                        active?.idleStop?.cancel()
                        current = null
                        // Watched matters more than the session: a failed stop doesn't keep it from the server.
                        try {
                            server.stop(source, video, position, durationMs)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            MediaServerLog.warn("Couldn't tell the server playback stopped", e)
                        }
                    }
                    server.setWatched(source, video, watched = true)
                }
                active == null && position == 0L && !justPlayed(video) -> {
                    if (!video.watched) server.setWatched(source, video, watched = false)
                }
                active == null -> {
                    server.start(source, video, position, durationMs)
                    val session = Session(video.id, server, source, video, position, durationMs, sentAt = clock())
                    current = session
                    scheduleStop(session)
                }
                else -> {
                    active.source = source
                    active.video = video
                    active.positionMs = position
                    active.durationMs = durationMs ?: active.durationMs
                    val now = clock()
                    if (now - active.sentAt >= progressIntervalMs - INTERVAL_SLACK_MS) {
                        server.progress(source, video, position, active.durationMs)
                        active.sentAt = now
                    }
                    scheduleStop(active)
                }
            }
        }
    }

    /** A save written moments ago is playback; setWatched leaves lastPlayedAt alone. */
    private fun justPlayed(video: VideoEntity): Boolean =
        video.lastPlayedAt?.let { abs(wallClock() - it) < JUST_PLAYED_MS } == true

    /** Stops [session] once no save came for [idleStopMs]. */
    private fun scheduleStop(session: Session) {
        session.idleStop?.cancel()
        session.idleStop = scope.launch {
            delay(idleStopMs)
            mutex.withLock {
                if (current !== session) return@withLock
                // This job is the one running: closeCurrent must not cancel it mid-request.
                session.idleStop = null
                closeCurrent()
            }
        }
    }

    /** Ends the running session with its last position. Call with [mutex] held. */
    private suspend fun closeCurrent() {
        val session = current ?: return
        current = null
        session.idleStop?.cancel()
        try {
            session.server.stop(session.source, session.video, session.positionMs, session.durationMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediaServerLog.warn("Couldn't tell the server playback stopped", e)
        }
    }

    companion object {
        /** Progress goes to the server at most this often. */
        const val PROGRESS_INTERVAL_MS = 10_000L

        /** The player saves every 10 s while playing; this long without a save means it stopped. */
        const val IDLE_STOP_MS = 45_000L

        /** Saves come every 10 s and jitter a little; one arriving just early still counts. */
        private const val INTERVAL_SLACK_MS = 1_000L

        private const val JUST_PLAYED_MS = 15_000L
    }
}
