package com.aakash.novafork.player

import android.content.Context
import android.net.Uri
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.util.Log
import com.aakash.novafork.AppGraph
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesClient
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesException
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesHash
import com.aakash.novafork.core.opensubtitles.OpenSubtitlesLogin
import com.aakash.novafork.core.opensubtitles.SubtitleRanking
import com.aakash.novafork.core.opensubtitles.SubtitleResult
import com.aakash.novafork.data.Settings
import com.aakash.novafork.data.db.VideoWithMeta
import com.aakash.novafork.player.SubtitleDownloadRules.AutoPick
import com.aakash.novafork.sources.SubtitleFinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The one OpenSubtitles client of the process: its login token outlives the player sessions.
 * Reads the key and the account from Settings on every call.
 */
internal object OpenSubtitlesAccess {
    @Volatile
    private var client: OpenSubtitlesClient? = null

    fun client(graph: AppGraph): OpenSubtitlesClient =
        client ?: synchronized(this) {
            client ?: OpenSubtitlesClient(
                http = graph.http,
                apiKey = { effectiveKey(graph.settings.settings.value) },
                login = { loginOf(graph.settings.settings.value) },
                appVersion = BuildConfig.VERSION_NAME,
            ).also { client = it }
        }

    /** The user's key when set, else the one built into the APK, else "". */
    fun effectiveKey(settings: Settings): String =
        settings.opensubtitlesApiKey.trim().ifEmpty { BuildConfig.OPENSUBTITLES_API_KEY.trim() }

    private fun loginOf(settings: Settings): OpenSubtitlesLogin? {
        val username = settings.opensubtitlesUsername.trim()
        val password = settings.opensubtitlesPassword
        return if (username.isEmpty() || password.isEmpty()) null else OpenSubtitlesLogin(username, password)
    }
}

/**
 * Subtitle downloads for the player: searches OpenSubtitles for the playing video and keeps what
 * it downloads among the video's subtitles ([SubtitleFinder.saveDownloaded]). Main-safe.
 */
internal class SubtitleDownloads(
    private val context: Context,
    private val graph: AppGraph,
    private val finder: SubtitleFinder,
) {
    /** The video the subtitles are for. */
    class Target(
        /** See [com.aakash.novafork.sources.DownloadedSubtitles.videoKey]. */
        val videoKey: String,
        val uri: String,
        val kind: UriKind,
        val fileName: String,
        val entry: VideoWithMeta?,
    )

    private val client: OpenSubtitlesClient get() = OpenSubtitlesAccess.client(graph)

    /** An API key is set (the user's or the built-in one). */
    val hasKey: Boolean get() = OpenSubtitlesAccess.effectiveKey(graph.settings.settings.value).isNotEmpty()

    /** The languages to search in, most wanted first (see [SubtitleDownloadRules.languages]). */
    fun languages(settings: Settings): List<String> =
        SubtitleDownloadRules.languages(settings.subtitleLanguages, settings.preferredSubtitleLanguage, deviceLocales())

    /**
     * Subtitles for [target] in [languages], best first. The movie hash is sent for a file on
     * this device; reading a network file's first and last 64 KiB would cost too much.
     */
    suspend fun search(target: Target, languages: List<String>): List<SubtitleResult> {
        val hash = if (target.kind.isLocal) movieHash(target) else null
        val request = SubtitleDownloadRules.searchRequest(target.entry, target.fileName, languages, hash)
        val results = client.search(request)
        return withContext(Dispatchers.Default) { SubtitleRanking.sort(results, languages) }
    }

    /**
     * What to download for [target] without asking, in [languages] (see
     * [SubtitleDownloadRules.autoPick]). OpenSubtitles isn't asked when no answer could be sure to
     * fit: a video known by its name only, without a movie hash (a file on a share, a link). Nor
     * is it asked again in this process for one whose search by name and hash found nothing timed
     * for it; the next play after the library matched the video does ask.
     */
    suspend fun autoPick(target: Target, languages: List<String>): AutoPick {
        val byName = SubtitleDownloadRules.searchRequest(target.entry, target.fileName, languages, movieHash = null)
        val identified = SubtitleDownloadRules.identifies(byName)
        if (!identified && (!target.kind.isLocal || target.videoKey in unsureByName)) return AutoPick.Later
        val hash = if (target.kind.isLocal) movieHash(target) else null
        val request = byName.copy(movieHash = hash)
        if (!SubtitleDownloadRules.canAutoPick(request)) return AutoPick.Later
        val results = client.search(request)
        val pick = withContext(Dispatchers.Default) { SubtitleDownloadRules.autoPick(request, results, languages) }
        if (!identified && pick == AutoPick.Later) unsureByName += target.videoKey
        return pick
    }

    /** The names of the files downloaded for [videoKey] so far. */
    suspend fun downloadedNames(videoKey: String): List<String> = finder.downloaded(videoKey).map { it.name }

    /**
     * Downloads [result] for [target] and returns the file. One downloaded before is returned
     * as it is, without spending the download quota again.
     */
    suspend fun download(target: Target, result: SubtitleResult): File {
        finder.downloaded(target.videoKey).lastOrNull { SubtitleDownloadRules.isDownloadOf(it.name, result) }?.let { return it }
        val subtitle = client.download(result.fileId)
        val name = SubtitleDownloadRules.fileName(result, subtitle.fileName)
        return finder.saveDownloaded(target.videoKey, name, subtitle.bytes)
            ?: throw OpenSubtitlesException(OpenSubtitlesException.Reason.BAD_RESPONSE, "Couldn't save the subtitle file")
    }

    /** True once an automatic download was settled for [videoKey] (found nothing, or got one). */
    suspend fun autoDownloadSettled(videoKey: String): Boolean = withContext(Dispatchers.IO) {
        File(finder.downloadDirectory(videoKey), AUTO_MARKER).exists()
    }

    /** Remembers that [videoKey]'s automatic download is settled: it is not tried again. */
    suspend fun settleAutoDownload(videoKey: String) {
        withContext(Dispatchers.IO) {
            try {
                val dir = finder.downloadDirectory(videoKey)
                if (dir.isDirectory || dir.mkdirs()) File(dir, AUTO_MARKER).createNewFile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't mark the automatic subtitle download", e)
            }
        }
    }

    /** The OpenSubtitles hash of a file on this device, read through the content resolver; null when it can't be read. */
    private suspend fun movieHash(target: Target): String? = withContext(Dispatchers.IO) {
        try {
            when (target.kind) {
                UriKind.CONTENT -> {
                    val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(target.uri), "r")
                    descriptor?.let { pfd -> ParcelFileDescriptor.AutoCloseInputStream(pfd).use { OpenSubtitlesHash.of(it.channel) } }
                }
                UriKind.FILE -> {
                    val path = if (target.uri.startsWith("/")) target.uri else Uri.parse(target.uri).path
                    path?.let { OpenSubtitlesHash.of(File(it)) }
                }
                UriKind.SMB, UriKind.NETWORK -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A provider that only streams (no seeking), a revoked grant: search without the hash.
            Log.w(TAG, "No movie hash", e)
            null
        }
    }

    private fun deviceLocales(): List<Locale> {
        val locales = LocaleList.getDefault()
        return (0 until locales.size()).mapNotNull { locales.get(it) }
    }

    private companion object {
        const val TAG = "SubtitleDownloads"

        /** In the video's download folder: its automatic download is settled. Hidden, so never loaded as a subtitle. */
        const val AUTO_MARKER = ".auto-download"

        /** Videos searched by name and hash in this process without a result timed for them. */
        val unsureByName: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
