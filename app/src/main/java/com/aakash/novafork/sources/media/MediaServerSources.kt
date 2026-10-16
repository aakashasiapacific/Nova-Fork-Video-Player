package com.aakash.novafork.sources.media

import com.aakash.novafork.AppGraph
import com.aakash.novafork.data.SourceModule
import com.aakash.novafork.data.db.SourceType

/**
 * Jellyfin, Emby and Plex: their scanners, browsers, playback options and progress reporters, as
 * AppGraph registers them. Jellyfin and Emby share one implementation (core JellyfinClient with
 * the server's flavour); one reporter serves all three, since one video plays at a time.
 */
internal object MediaServerSources {
    /** [graph] gives the app context and HTTP client; graph.library is read lazily, never while creating. */
    fun create(graph: AppGraph): SourceModule {
        val env = MediaServerEnvironment(graph)
        val jellyfin = JellyfinConnector(env)
        val plex = PlexConnector(env)
        val jellyfinScanner = JellyfinScanner(jellyfin)
        val jellyfinBrowser = JellyfinBrowser(jellyfin)
        val jellyfinPlayback = JellyfinPlayback(jellyfin)
        val plexPlayback = PlexPlayback(plex)
        val reporter = MediaServerReporter(
            servers = mapOf(
                SourceType.JELLYFIN to jellyfinPlayback,
                SourceType.EMBY to jellyfinPlayback,
                SourceType.PLEX to plexPlayback,
            ),
            scope = graph.appScope,
        )
        return SourceModule(
            scanners = mapOf(
                SourceType.JELLYFIN to jellyfinScanner,
                SourceType.EMBY to jellyfinScanner,
                SourceType.PLEX to PlexScanner(plex),
            ),
            browsers = mapOf(
                SourceType.JELLYFIN to jellyfinBrowser,
                SourceType.EMBY to jellyfinBrowser,
                SourceType.PLEX to PlexBrowser(plex),
            ),
            playbackOptions = mapOf(
                SourceType.JELLYFIN to jellyfinPlayback,
                SourceType.EMBY to jellyfinPlayback,
                SourceType.PLEX to plexPlayback,
            ),
            progressReporters = mapOf(
                SourceType.JELLYFIN to reporter,
                SourceType.EMBY to reporter,
                SourceType.PLEX to reporter,
            ),
        )
    }

    /** Plex sign-in without a pasted token (a code to enter at plex.tv/link, or a password), for the add-server screen. */
    fun plexSignIn(graph: AppGraph): PlexSignIn = PlexSignIn(MediaServerEnvironment(graph))
}
