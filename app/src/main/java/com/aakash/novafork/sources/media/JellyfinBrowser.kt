package com.aakash.novafork.sources.media

import com.aakash.novafork.core.jellyfin.JellyfinClient
import com.aakash.novafork.core.jellyfin.JellyfinDates
import com.aakash.novafork.core.jellyfin.JellyfinFlavor
import com.aakash.novafork.core.jellyfin.JellyfinItem
import com.aakash.novafork.core.jellyfin.JellyfinQuery
import com.aakash.novafork.data.NetworkBrowser
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Browses a Jellyfin or Emby server: "" lists the libraries, an item id what is in it (series,
 * seasons, movies, episodes). [RemoteEntry.path] of a folder is its item id; a video's
 * [RemoteEntry.playUri] is the URI a scan stores it under.
 */
internal class JellyfinBrowser(private val connector: JellyfinConnector) : NetworkBrowser {
    override suspend fun list(source: SourceEntity, path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val parent = path.trim().trim('/')
        connector.withAuth(source) { client, auth ->
            if (parent.isEmpty()) {
                JellyfinItems.chosenLibraries(client.libraries(auth), source).map { library ->
                    RemoteEntry(
                        name = library.name?.trim().orEmpty().ifEmpty { library.id },
                        path = library.id,
                        isDirectory = true,
                        imageUrl = JellyfinItems.posterUrl(client, library, MediaServerArt.THUMB_WIDTH),
                    )
                }
            } else {
                val query = JellyfinQuery(parentId = parent, recursive = false, sortBy = CHILD_ORDER)
                client.items(auth, query).mapNotNull { entry(client, it) }
            }
        }
    }

    override suspend fun test(source: SourceEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = connector.client(source)
            val info = client.publicInfo()
            wrongFlavor(client.flavor, info.productName)?.let { return@withContext Result.failure(IOException(it)) }
            connector.withAuth(source) { signedIn, auth -> signedIn.libraries(auth) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't connect to the server", e))
        }
    }

    private fun entry(client: JellyfinClient, item: JellyfinItem): RemoteEntry? = when {
        item.isMissing -> null
        item.isFolder -> RemoteEntry(
            name = JellyfinItems.displayName(item),
            path = item.id,
            isDirectory = true,
            imageUrl = JellyfinItems.posterUrl(client, item, MediaServerArt.THUMB_WIDTH),
        )
        JellyfinItems.isVideo(item) -> RemoteEntry(
            name = JellyfinItems.displayName(item),
            path = item.id,
            isDirectory = false,
            sizeBytes = item.mediaSources.firstOrNull()?.size?.takeIf { it > 0 },
            modifiedAt = JellyfinDates.epochMillis(item.dateCreated),
            playUri = JellyfinItems.playUri(client, item),
            imageUrl = JellyfinItems.posterUrl(client, item, MediaServerArt.THUMB_WIDTH),
        )
        else -> null
    }

    /** A server of the other kind answered: say which type to add it as. */
    private fun wrongFlavor(flavor: JellyfinFlavor, productName: String?): String? {
        val product = productName.orEmpty()
        return when {
            flavor == JellyfinFlavor.JELLYFIN && product.contains("Emby", ignoreCase = true) -> "This is an Emby server. Add it as Emby."
            flavor == JellyfinFlavor.EMBY && product.contains("Jellyfin", ignoreCase = true) -> "This is a Jellyfin server. Add it as Jellyfin."
            else -> null
        }
    }

    private companion object {
        /** Seasons and episodes by number, everything else by name. */
        val CHILD_ORDER = listOf("ParentIndexNumber", "IndexNumber", "SortName")
    }
}
