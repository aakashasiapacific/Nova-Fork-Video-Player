package com.aakash.novafork.data

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.core.tmdb.TmdbCredentials
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowEnvironment
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [LibraryRepositoryImpl] over an in-memory Room database, fake scanners and a fake TMDB.
 *
 * With the default [scope] (cancelled before the repository is built) none of the repository's
 * background work ever starts: metadata passes, the device-source sync, the reactions to settings
 * and the scan that addFolder / saveSmbServer launch. The database then changes only through the
 * calls a test makes, so assertions never race a lookup. Pass [LiveScope.scope] to run that work.
 * [scanTimeoutMs] replaces the time one scan may take before it is given up.
 */
internal class RepositoryFixture(
    scope: CoroutineScope = inertScope(),
    initialSettings: Settings = Settings(scanDeviceStorage = false),
    scanTimeoutMs: Long? = null,
) {
    val context: Context = ApplicationProvider.getApplicationContext<Context>()
    val database: NovaDatabase = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val device = FakeScanner()
    val folders = FakeScanner()
    val shares = FakeScanner()
    val tmdb = FakeTmdb()
    val settings = FakeSettingsStore(initialSettings)
    val repository = LibraryRepositoryImpl(
        context = context,
        database = database,
        scanners = mapOf(SourceType.DEVICE to device, SourceType.FOLDER to folders, SourceType.SMB to shares),
        tmdb = tmdb.client(),
        settings = settings,
        scope = scope,
        scanTimeoutMs = scanTimeoutMs?.let { limit -> { _: SourceType -> limit } } ?: ::defaultScanTimeoutMs,
    )

    suspend fun videoAt(uri: String): VideoEntity =
        requireNotNull(database.videoDao().byUri(uri)) { "No video at $uri" }

    suspend fun videosOf(sourceId: Long): List<VideoEntity> = database.videoDao().bySource(sourceId)

    /** Rewrites a row, e.g. into the state automatic matching would have left it in. */
    suspend fun edit(uri: String, change: (VideoEntity) -> VideoEntity) {
        database.videoDao().update(change(videoAt(uri)))
    }

    fun close() {
        database.close()
    }
}

internal fun inertScope(): CoroutineScope = CoroutineScope(Job().apply { cancel() })

/**
 * Runs the repository's background work for real, on Dispatchers.IO. [close] cancels that work,
 * waits for it to end and fails the test if any of it threw.
 */
internal class LiveScope {
    private val job = SupervisorJob()
    private val escaped: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

    val scope: CoroutineScope = CoroutineScope(job + Dispatchers.IO + CoroutineExceptionHandler { _, e -> escaped += e })

    fun close() {
        runBlocking { job.cancelAndJoin() }
        val failures = synchronized(escaped) { escaped.toList() }
        assertTrue("Background work failed: $failures", failures.isEmpty())
    }
}

/**
 * Polls [condition] until it holds, failing after [timeoutMs]. For tests on real time
 * (runBlocking) that wait for background work; runTest's virtual clock would never let it run.
 */
internal suspend fun awaitUntil(what: String, timeoutMs: Long = AWAIT_TIMEOUT_MS, condition: suspend () -> Boolean) {
    val met = withTimeoutOrNull(timeoutMs) {
        while (!condition()) delay(AWAIT_POLL_MS)
        true
    }
    if (met == null) throw AssertionError("Timed out after $timeoutMs ms waiting for $what")
}

private const val AWAIT_TIMEOUT_MS = 20_000L
private const val AWAIT_POLL_MS = 25L

/** What granting the video permission does: device storage becomes scannable (Android 13+). */
internal fun grantVideoAccess() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
}

/** Android 14's "Allow limited access": only the videos the user picked are visible. */
internal fun grantLimitedAccess() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}

/**
 * "All files access" (Android 11+) turned on or off for the app: the MANAGE_EXTERNAL_STORAGE
 * app-op that Environment.isExternalStorageManager() reads (for the first external storage
 * directory, which Robolectric only has once one is added).
 */
internal fun setAllFilesAccess(allowed: Boolean) {
    val app = ApplicationProvider.getApplicationContext<Application>()
    ShadowEnvironment.addExternalDir("emulated-0")
    val appOps = requireNotNull(app.getSystemService(AppOpsManager::class.java))
    shadowOf(appOps).setMode(
        OPSTR_MANAGE_EXTERNAL_STORAGE,
        app.applicationInfo.uid,
        app.packageName,
        if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED,
    )
}

/** AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE, which the public SDK does not expose. */
private const val OPSTR_MANAGE_EXTERNAL_STORAGE = "android:manage_external_storage"

/** One file as a scanner reports it. [folder] is the display path, root → leaf. */
internal fun scannedVideo(
    uri: String,
    fileName: String,
    folder: String,
    sizeBytes: Long? = 700_000_000L,
    durationMs: Long? = null,
    dateAdded: Long = 1_000L,
): ScannedVideo = ScannedVideo(
    uri = uri,
    fileName = fileName,
    folder = folder,
    parentFolders = folder.split('/').filter { it.isNotEmpty() }.asReversed(),
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    dateAdded = dateAdded,
)

/** Returns the files [serve]d for a source's location; while [failure] is set every scan throws it. */
internal class FakeScanner : SourceScanner {
    private val files = ConcurrentHashMap<String, List<ScannedVideo>>()
    private val scans = AtomicInteger()
    private val scansByLocation = ConcurrentHashMap<String, AtomicInteger>()
    private val gates = ConcurrentHashMap<String, Gate>()

    @Volatile
    var failure: Exception? = null

    /** Scans started so far, of every source. */
    val scanCount: Int get() = scans.get()

    fun scanCount(location: String): Int = scansByLocation[location]?.get() ?: 0

    fun serve(location: String, vararg videos: ScannedVideo) {
        files[location] = videos.toList()
    }

    /** Scans of [location] wait from now on until the returned gate is opened. */
    fun hold(location: String): Gate = Gate().also { gates[location] = it }

    /** Holds a scan in progress, so a test can act while a refresh is running. */
    class Gate {
        private val released = CompletableDeferred<Unit>()

        /** Completes once a scan is waiting at the gate. */
        val entered = CompletableDeferred<Unit>()

        fun open() {
            released.complete(Unit)
        }

        suspend fun pass() {
            entered.complete(Unit)
            released.await()
        }
    }

    override suspend fun scan(source: SourceEntity, onProgress: (found: Int) -> Unit): List<ScannedVideo> {
        scans.incrementAndGet()
        scansByLocation.getOrPut(source.location) { AtomicInteger() }.incrementAndGet()
        gates[source.location]?.pass()
        failure?.let { throw it }
        val found = files[source.location].orEmpty()
        onProgress(found.size)
        return found
    }
}

internal class FakeSettingsStore(initial: Settings) : SettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun update(transform: (Settings) -> Settings) {
        state.update(transform)
    }

    override val effectiveTmdbKey: String get() = state.value.tmdbApiKey.trim()

    override val hasBuiltInTmdbKey: Boolean get() = false
}

/**
 * TMDB answering from canned JSON, keyed by request path without the base URL ("tv/95396",
 * "tv/95396/season/1", "search/tv"); anything else is TMDB's 404. An application interceptor
 * answers before OkHttp opens a connection, so nothing goes over the network, and the client is
 * cleartext-only so no TLS setup runs either.
 */
internal class FakeTmdb : Interceptor {
    private class Route(val code: Int, val json: String)

    private val routes = ConcurrentHashMap<String, Route>()
    private val log: MutableList<String> = Collections.synchronizedList(ArrayList())

    /** Paths requested so far, oldest first. */
    val requests: List<String> get() = synchronized(log) { log.toList() }

    fun requestCount(path: String): Int = requests.count { it == path }

    /** Answers [path] with [json] and HTTP [code] from now on. */
    fun route(path: String, json: String, code: Int = 200) {
        routes[path] = Route(code, json)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.removePrefix("/3/")
        log.add(path)
        val route = routes[path] ?: Route(404, NOT_FOUND)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(route.code)
            .message(if (route.code == 200) "OK" else "Error")
            .body(route.json.toResponseBody(JSON))
            .build()
    }

    /** No retry waits and no rate limit, so a failure shows at once and nothing sleeps. */
    fun client(): TmdbClient = TmdbClient(
        http = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
            .addInterceptor(this)
            .build(),
        credentials = { TmdbCredentials("0123456789abcdef0123456789abcdef") },
        baseUrl = { "http://tmdb.test/3/" },
        language = { "en-US" },
        retryDelaysMs = emptyList(),
        rateLimitRequests = 0,
    )

    private companion object {
        val JSON = "application/json".toMediaType()
        const val NOT_FOUND = """{"status_code":34,"status_message":"The resource you requested could not be found.","success":false}"""
    }
}

/** TMDB responses for the shows and movies the tests use. */
internal object TmdbJson {
    const val SEVERANCE_ID = 95396
    const val FRIEREN_ID = 209867
    const val INCEPTION_ID = 27205

    val severance = """
        {"id":95396,"name":"Severance","original_name":"Severance","first_air_date":"2022-02-17",
         "overview":"Mark leads a team of office workers.","poster_path":"/severance.jpg",
         "backdrop_path":"/severance-bg.jpg","vote_average":8.4,"genres":[{"id":18,"name":"Drama"}],
         "episode_run_time":[50],"number_of_seasons":2,
         "seasons":[{"season_number":1,"episode_count":9,"poster_path":"/severance-s1.jpg"},
                    {"season_number":2,"episode_count":10,"poster_path":"/severance-s2.jpg"}]}
    """.trimIndent()

    /** Absolute anime numbering: 28 + 10 episodes, and two specials that must not count. */
    val frieren = """
        {"id":209867,"name":"Frieren","original_name":"Sousou no Frieren","first_air_date":"2023-09-29",
         "overview":"An elf mage outlives her party.","poster_path":"/frieren.jpg","vote_average":8.8,
         "genres":[{"id":16,"name":"Animation"}],"number_of_seasons":2,
         "seasons":[{"season_number":0,"episode_count":2,"name":"Specials"},
                    {"season_number":1,"episode_count":28},
                    {"season_number":2,"episode_count":10}]}
    """.trimIndent()

    val frierenSearch = """
        {"page":1,"total_results":1,"total_pages":1,
         "results":[{"id":209867,"name":"Frieren","original_name":"Sousou no Frieren",
                     "first_air_date":"2023-09-29","popularity":120.5,"origin_country":["JP"]}]}
    """.trimIndent()

    val inception = """
        {"id":27205,"title":"Inception","original_title":"Inception","release_date":"2010-07-15",
         "overview":"A thief who steals corporate secrets.","tagline":"Your mind is the scene of the crime.",
         "poster_path":"/inception.jpg","backdrop_path":"/inception-bg.jpg","runtime":148,
         "vote_average":8.4,"genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"}]}
    """.trimIndent()

    val inceptionSearch = """
        {"page":1,"total_results":1,"total_pages":1,
         "results":[{"id":27205,"title":"Inception","original_title":"Inception",
                     "release_date":"2010-07-15","popularity":80.0}]}
    """.trimIndent()

    const val NO_RESULTS = """{"page":1,"results":[],"total_results":0,"total_pages":0}"""

    /** TMDB's answer (HTTP 401) to a wrong API key. */
    const val INVALID_KEY = """{"status_code":7,"status_message":"Invalid API key: You must be granted a valid key.","success":false}"""

    /** What TMDB (or a proxy in front of it) answers with HTTP 503 while it is down. */
    const val UNAVAILABLE = """{"status_code":9,"status_message":"Service offline - try again later.","success":false}"""

    /** A season with episodes 1..[episodes], each named by [name] and with a still "/still-S-E.jpg". */
    fun season(season: Int, episodes: Int, name: (Int) -> String): String =
        (1..episodes).joinToString(separator = ",", prefix = """{"season_number":$season,"episodes":[""", postfix = "]}") { n ->
            """{"id":${season * 1000 + n},"episode_number":$n,"season_number":$season,"name":"${name(n)}",""" +
                """"overview":"Episode $n.","still_path":"/still-$season-$n.jpg","air_date":"2023-01-01"}"""
        }
}
