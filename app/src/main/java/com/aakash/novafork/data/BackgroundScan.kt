package com.aakash.novafork.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aakash.novafork.NovaApp
import com.aakash.novafork.R
import com.aakash.novafork.data.db.VideoKind
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.widget.WidgetIntents
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Preferences › Sources › Scan schedule: the library is scanned in the background every few hours
 * (on any network, not on low battery), as well as each time the app opens. After a scan the
 * optional new-episode note looks for episodes that arrived for shows being watched.
 */
object ScanSchedule {
    private const val WORK = "library-scan"

    fun apply(context: Context, hours: Int) {
        val work = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (e: IllegalStateException) {
            Log.w("ScanSchedule", "WorkManager is not available", e)
            return
        }
        if (hours <= 0) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class LibraryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = NovaApp.graph(applicationContext)
        try {
            graph.library.refreshAll()
            if (graph.settings.settings.value.newEpisodeAlerts) NewEpisodeAlerts.check(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LibraryScanWorker", "Background scan failed", e)
            return Result.retry()
        }
        return Result.success()
    }
}

/**
 * One quiet, grouped note (no sound, no pop-up) when episodes arrived for a show that has been
 * watched and they are not watched yet. The first check only sets the starting point, so turning
 * it on never lists the whole library.
 */
object NewEpisodeAlerts {
    private const val PREFS = "new_episode_alerts"
    private const val KEY_LAST = "last_check"
    private const val CHANNEL = "new_episodes"
    private const val NOTIFICATION_ID = 4_201
    private const val MAX_LINES = 5

    suspend fun check(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST, 0L)
        prefs.edit().putLong(KEY_LAST, now).apply()
        if (last == 0L) return
        val videos = NovaApp.graph(context).database.videoDao().observePresent().first()
        val lines = newEpisodes(videos, last)
        if (lines.isEmpty()) return
        notify(context, lines)
    }

    /** "Severance · S2 · E4" for each new, unwatched episode of a show with watched episodes. */
    fun newEpisodes(videos: List<VideoWithMeta>, since: Long): List<String> {
        val watchedShows = videos
            .filter { it.video.watched || it.video.positionMs > 0 }
            .mapNotNull { it.video.showKey }
            .toSet()
        return videos
            .filter { (v) -> v.kind == VideoKind.EPISODE && v.dateAdded > since && !v.watched && v.showKey in watchedShows }
            .sortedWith(compareBy({ it.video.showKey }, { it.video.season ?: 0 }, { it.video.episode ?: 0 }))
            .map { item ->
                val show = item.meta?.title ?: item.video.parsedTitle
                listOfNotNull(show, item.video.season?.let { "S$it" }, item.video.episode?.let { "E$it" }).joinToString(" · ")
            }
            .distinct()
    }

    private fun notify(context: Context, lines: List<String>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Episodes that arrived for shows you are watching"
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val title = if (lines.size == 1) "New episode" else "${lines.size} new episodes"
        val style = NotificationCompat.InboxStyle()
        lines.take(MAX_LINES).forEach { style.addLine(it) }
        if (lines.size > MAX_LINES) style.setSummaryText("and ${lines.size - MAX_LINES} more")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tv)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(WidgetIntents.openApp(context))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed: nothing to show.
        }
    }
}
