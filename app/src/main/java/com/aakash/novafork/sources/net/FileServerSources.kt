package com.aakash.novafork.sources.net

import com.aakash.novafork.AppGraph
import com.aakash.novafork.data.SourceModule

/**
 * FTP, SFTP, NFS, UPnP and WebDAV: their scanners, browsers and playback options, as AppGraph
 * registers them. None is implemented yet, so such a source is saved but not scanned.
 */
internal object FileServerSources {
    /** [graph] gives the app context and HTTP client; read graph.library lazily, never while creating. */
    @Suppress("UNUSED_PARAMETER")
    fun create(graph: AppGraph): SourceModule = SourceModule()
}
