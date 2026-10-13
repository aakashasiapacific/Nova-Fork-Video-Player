package com.aakash.novafork

import android.app.Application
import android.util.Log
import coil.ImageLoader
import com.aakash.novafork.core.tmdb.TmdbClient
import com.aakash.novafork.core.tmdb.TmdbCredentials
import com.aakash.novafork.data.LibraryRepository
import com.aakash.novafork.data.LibraryRepositoryImpl
import com.aakash.novafork.data.Network
import com.aakash.novafork.data.PrefsSettingsStore
import com.aakash.novafork.data.SettingsStore
import com.aakash.novafork.data.SmbBrowser
import com.aakash.novafork.data.SourceScanner
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.sources.DeviceScanner
import com.aakash.novafork.sources.FolderScanner
import com.aakash.novafork.sources.SmbBrowserImpl
import com.aakash.novafork.sources.SmbScanner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Hand-wired dependencies, one per process (see NovaApp). */
class AppGraph(val app: Application) {
    /**
     * Lives as long as the process: scans, metadata lookups and saves run here. A failure in one
     * of them is logged and the rest keeps going; it never takes the process down.
     */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Background work failed", error)
            CrashReporter.log("Background work failed", error)
        },
    )

    val settings: SettingsStore by lazy { PrefsSettingsStore(app) }

    val database: NovaDatabase by lazy { NovaDatabase.create(app) }

    val http: OkHttpClient by lazy { Network.createOkHttp(app, settings) }

    val tmdb: TmdbClient by lazy {
        TmdbClient(
            http = http,
            // The user's key when set, else the one built into the APK (see PrefsSettingsStore).
            credentials = { settings.effectiveTmdbKey.takeIf { it.isNotBlank() }?.let { TmdbCredentials(it) } },
            baseUrl = { settings.settings.value.tmdbHost.baseUrl },
            language = { settings.settings.value.tmdbLanguage },
        )
    }

    val smb: SmbBrowser by lazy {
        SmbBrowserImpl(credentialsFor = { host, share -> library.smbCredentials(host, share) })
    }

    val scanners: Map<SourceType, SourceScanner> by lazy {
        mapOf(
            SourceType.DEVICE to DeviceScanner(app),
            SourceType.FOLDER to FolderScanner(app),
            SourceType.SMB to SmbScanner(smb),
        )
    }

    val library: LibraryRepository by lazy {
        LibraryRepositoryImpl(
            context = app,
            database = database,
            scanners = scanners,
            tmdb = tmdb,
            settings = settings,
            scope = appScope,
        )
    }

    val imageLoader: ImageLoader by lazy { Network.createImageLoader(app, http, smb) }

    private companion object {
        const val TAG = "AppGraph"
    }
}
