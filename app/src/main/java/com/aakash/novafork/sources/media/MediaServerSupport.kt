package com.aakash.novafork.sources.media

import android.content.Context
import android.os.Build
import android.util.Log
import com.aakash.novafork.AppGraph
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.CrashReporter
import com.aakash.novafork.core.jellyfin.JellyfinDevice
import com.aakash.novafork.core.jellyfin.ServerSecrets
import com.aakash.novafork.core.plex.PlexClientInfo
import com.aakash.novafork.data.db.SourceEntity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * What the Jellyfin, Emby and Plex sources share: the HTTP client, how this install introduces
 * itself to servers, and where sign-ins are stored. Everything is created on first use, off the
 * main thread (the install id is read from SharedPreferences); AppGraph's library only once a
 * sign-in is stored, never while AppGraph is still building it.
 */
internal class MediaServerEnvironment(
    baseHttp: () -> OkHttpClient,
    readInstallId: () -> String,
    /** Keeps a sign-in with a saved source (LibraryRepository.updateSourceAuth). */
    private val saveAuth: suspend (sourceId: Long, token: String?, userId: String?, serverId: String?) -> Unit,
    private val appVersion: String = BuildConfig.VERSION_NAME,
) {
    constructor(graph: AppGraph) : this(
        baseHttp = { graph.http },
        readInstallId = { installIdOf(graph.app) },
        saveAuth = { sourceId, token, userId, serverId -> graph.library.updateSourceAuth(sourceId, token, userId, serverId) },
    )

    /**
     * The app's client (user agent, secure DNS) without its disk cache: listings and tokens of a
     * personal server don't belong on disk. Big libraries get time to answer.
     */
    val http: OkHttpClient by lazy {
        baseHttp().newBuilder()
            .cache(null)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    /** One random id per install: servers keep one session per device and user, Plex ties PINs to it. */
    private val installId: String by lazy(readInstallId)

    private val deviceName: String
        get() = Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android"

    val jellyfinDevice: JellyfinDevice by lazy {
        JellyfinDevice(device = deviceName, deviceId = installId, version = appVersion)
    }

    val plexClient: PlexClientInfo by lazy {
        PlexClientInfo(
            clientIdentifier = installId,
            version = appVersion,
            device = deviceName,
            deviceName = deviceName,
            platformVersion = Build.VERSION.RELEASE,
        )
    }

    /**
     * Keeps what a sign-in gave with [source] (see LibraryRepository.updateSourceAuth). A source
     * that is not saved yet (id 0, being tested) has nowhere to keep it; its sign-in lives on in the
     * caller's memory only.
     */
    suspend fun storeAuth(source: SourceEntity, token: String?, userId: String?, serverId: String?) {
        if (source.id == 0L) return
        if (source.token == token && source.userId == userId && source.serverId == serverId) return
        saveAuth(source.id, token, userId, serverId)
    }

    private companion object {
        const val PREFS = "media_servers"
        const val KEY_INSTALL_ID = "install_id"
        const val READ_TIMEOUT_S = 60L

        /** Synchronized: two environments asking at first launch must not each make an id. */
        @Synchronized
        fun installIdOf(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL_ID, it).apply() }
        }
    }
}

/** Logging for the media-server sources: tokens never reach logcat or the crash report. */
internal object MediaServerLog {
    private const val TAG = "MediaServers"

    /** Logs [error]'s message (scrubbed), never its stack trace's messages or the URLs in them. */
    fun warn(message: String, error: Throwable) {
        val detail = scrub(error.message).ifEmpty { error.javaClass.simpleName }
        Log.w(TAG, "${scrub(message)}: $detail")
        CrashReporter.log("${scrub(message)}: ${error.javaClass.simpleName}: $detail")
    }

    /** [text] without access tokens: api_key=, ApiKey=, X-Plex-Token=, X-Emby-Token= values and Token="…". */
    fun scrub(text: String?): String = ServerSecrets.redact(text)
}

/** Names as media servers give them, made fit for ScannedVideo folders and file names. */
internal object MediaServerNames {
    /** One segment of a '/'-separated folder path: no slashes, never blank. */
    fun segment(name: String?, fallback: String): String =
        name?.replace('/', '-')?.replace('\\', '-')?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

    /** The last part of a path on the server's disk ("/media/Heat (1995)/Heat.mkv" → "Heat.mkv"); null for a URL. */
    fun fileName(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotEmpty() && "://" !in it } ?: return null
        return value.substringAfterLast('/').substringAfterLast('\\').trim().takeIf { it.isNotEmpty() }
    }

    /** "Heat" + "mkv" → "Heat.mkv"; the first of a list of containers ("mov,mp4,m4a") counts. */
    fun withExtension(title: String, container: String?): String {
        val extension = container?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
        return if (extension == null) title else "$title.$extension"
    }

    /** "Season 1", "Specials" for season 0. */
    fun season(number: Int?): String = when (number) {
        null -> "Season 1"
        0 -> "Specials"
        else -> "Season $number"
    }

    /** Whole minutes of [durationMs], null when unknown or under a minute. */
    fun minutes(durationMs: Long?): Int? = durationMs?.div(60_000L)?.takeIf { it > 0 }?.toInt()
}

/** URLs of a media server's videos. */
internal object MediaServerUrls {
    /**
     * True when [uri] points at the server [location] names (same scheme, host and port): only
     * then may a token go along with it.
     */
    fun onServer(location: String, uri: String): Boolean {
        val server = location.trim().toHttpUrlOrNull() ?: return false
        val target = uri.trim().toHttpUrlOrNull() ?: return false
        return server.scheme == target.scheme && server.host.equals(target.host, ignoreCase = true) && server.port == target.port
    }
}

/** Artwork sizes asked of the servers: big enough for tablets, small enough for lists. */
internal object MediaServerArt {
    const val POSTER_WIDTH = 500
    const val POSTER_HEIGHT = 750
    const val BACKDROP_WIDTH = 1280
    const val BACKDROP_HEIGHT = 720
    const val STILL_WIDTH = 780
    const val STILL_HEIGHT = 439
    const val THUMB_WIDTH = 342
    const val THUMB_HEIGHT = 513
}
