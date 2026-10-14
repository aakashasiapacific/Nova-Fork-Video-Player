package com.aakash.novafork.sources.media

import com.aakash.novafork.AppGraph
import com.aakash.novafork.data.SourceModule

/**
 * Jellyfin, Emby and Plex: their scanners, browsers, playback options and progress reporters, as
 * AppGraph registers them. None is implemented yet, so such a source is saved but not scanned.
 */
internal object MediaServerSources {
    /** [graph] gives the app context and HTTP client; read graph.library lazily, never while creating. */
    @Suppress("UNUSED_PARAMETER")
    fun create(graph: AppGraph): SourceModule = SourceModule()
}
