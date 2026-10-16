package com.aakash.novafork.data

import android.content.Context
import android.os.SystemClock
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aakash.novafork.BuildConfig
import com.aakash.novafork.sources.SmbFetcher
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object Network {
    private const val HTTP_CACHE_BYTES = 50L * 1024 * 1024
    private const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024

    /** Shared client for TMDB and artwork: 15 s timeouts, disk cache, app User-Agent, secure DNS. */
    fun createOkHttp(context: Context, settings: SettingsStore): OkHttpClient {
        val cacheDir = context.applicationContext.cacheDir
        val userAgent = "NovaFork/" + BuildConfig.VERSION_NAME
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cache(Cache(File(cacheDir, "http"), HTTP_CACHE_BYTES))
            .addInterceptor(
                Interceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                },
            )
            .dns(SecureDns(File(cacheDir, "doh")) { settings.settings.value.secureDns })
            .build()
    }

    /** Coil's loader: video frames for local files, smb:// artwork, 20% memory and 250 MB disk cache. */
    fun createImageLoader(context: Context, okHttp: OkHttpClient, smb: SmbBrowser): ImageLoader {
        val appContext = context.applicationContext
        return ImageLoader.Builder(appContext)
            // Coil keeps images in its own disk cache; without this they would be stored twice.
            .okHttpClient { okHttp.newBuilder().cache(null).build() }
            .components {
                add(VideoFrameDecoder.Factory())
                add(SmbFetcher.Factory(smb))
            }
            .memoryCache { MemoryCache.Builder(appContext).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            // TMDB images never change for a path; keep them even when headers say otherwise.
            .respectCacheHeaders(false)
            .build()
    }
}

/**
 * Resolves names over DNS-over-HTTPS (Cloudflare, then Google) while [enabled]: some ISPs block
 * TMDB through their DNS. Anything going wrong falls back to the system resolver.
 */
private class SecureDns(
    private val cacheDir: File,
    private val enabled: () -> Boolean,
) : Dns {
    private val resolvers: List<Dns> by lazy {
        // Plain client for the resolvers themselves; it caches answers for their TTL.
        val bootstrap = OkHttpClient.Builder()
            .cache(Cache(cacheDir, DOH_CACHE_BYTES))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        listOf(
            dnsOverHttps(bootstrap, "https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
            dnsOverHttps(bootstrap, "https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        )
    }

    @Volatile
    private var bypassUntil = 0L

    override fun lookup(hostname: String): List<InetAddress> {
        if (enabled() && SystemClock.elapsedRealtime() >= bypassUntil) {
            var unreachable = 0
            for (resolver in resolvers) {
                try {
                    val addresses = resolver.lookup(hostname)
                    if (addresses.isNotEmpty()) return addresses
                } catch (e: Exception) {
                    // DnsOverHttps reports network failures as an UnknownHostException with a cause;
                    // a plain one means the name does not exist (or is a LAN name it won't resolve).
                    if (e !is UnknownHostException || e.cause != null) unreachable++
                }
            }
            // Both resolvers blocked (some networks do): don't pay their timeouts on every lookup.
            if (unreachable == resolvers.size) bypassUntil = SystemClock.elapsedRealtime() + BYPASS_MS
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun dnsOverHttps(client: OkHttpClient, url: String, vararg bootstrapHosts: String): Dns =
        DnsOverHttps.Builder()
            .client(client)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapHosts.map { InetAddress.getByName(it) })
            .build()

    private companion object {
        const val DOH_CACHE_BYTES = 1L * 1024 * 1024
        const val BYPASS_MS = 60_000L
    }
}
