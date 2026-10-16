package com.aakash.novafork.player

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last [CAPACITY] things the playback engine did, oldest first, for error reports.
 *
 * libVLC 3.7.2's Java API has no log callback (no log listener on LibVLC or ILibVLC), so the
 * player records its own events instead: how a video was opened, libVLC's player events,
 * surface, lifecycle and picture-in-picture changes, and decoding decisions. File names, hosts
 * and credentials are never recorded, only the kind of source and the file extension.
 */
internal object PlayerDiagnostics {
    private const val CAPACITY = 200

    private val lines = ArrayDeque<String>(CAPACITY)
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Adds one line. Cheap; any thread. */
    fun record(event: String) {
        synchronized(lines) {
            if (lines.size >= CAPACITY) lines.removeFirst()
            lines.addLast("${clock.format(Date())} $event")
        }
    }

    /** The newest [count] lines, oldest first. */
    fun recent(count: Int = CAPACITY): List<String> = synchronized(lines) { lines.takeLast(count) }
}
