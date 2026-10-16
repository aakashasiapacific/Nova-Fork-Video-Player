package com.aakash.novafork.ui

import com.aakash.novafork.data.RemoteEntry

/** One folder of a network source: the source and the path its NetworkBrowser takes ("" = its root). */
internal data class ListingKey(val sourceId: Long, val path: String)

/** A folder of a network source as its NetworkBrowser listed it, for Browse to show live. */
internal data class RemoteListing(
    /** In the browser's order; null until the first listing arrived. */
    val entries: List<RemoteEntry>? = null,
    val loading: Boolean = false,
    /** The last listing failed (entries, if any, are from an earlier one). */
    val error: String? = null,
)
