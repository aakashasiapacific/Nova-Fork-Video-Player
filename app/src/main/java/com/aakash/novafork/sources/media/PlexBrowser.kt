package com.aakash.novafork.sources.media

import com.aakash.novafork.core.plex.PlexMetadata
import com.aakash.novafork.core.plex.PlexServerClient
import com.aakash.novafork.data.NetworkBrowser
import com.aakash.novafork.data.RemoteEntry
import com.aakash.novafork.data.db.SourceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Browses a Plex server: "" lists the movie and TV sections, a folder's path is the key the server
 * gave it ("/library/sections/1/all", "/library/metadata/7/children"). A video's
 * [RemoteEntry.playUri] is the URI a scan stores it under.
 */
internal class PlexBrowser(private val connector: PlexConnector) : NetworkBrowser {
    override suspend fun list(source: SourceEntity, path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val key = path.trim()
        connector.withServer(source) { client, _ ->
            if (key.isEmpty()) {
                PlexItems.chosenSections(client.sections(), source).map { section ->
                    RemoteEntry(
                        name = section.title?.trim().orEmpty().ifEmpty { section.key },
                        path = "/library/sections/${section.key}/all",
                        isDirectory = true,
                    )
                }
            } else {
                client.children(key).metadata.mapNotNull { entry(client, it) }
            }
        }
    }

    override suspend fun test(source: SourceEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            connector.client(source, null).identity()
            connector.withServer(source) { client, _ -> client.sections() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IOException(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't connect to the server", e))
        }
    }

    private fun entry(client: PlexServerClient, item: PlexMetadata): RemoteEntry? {
        val image = client.imageUrl(item.thumb, MediaServerArt.THUMB_WIDTH, MediaServerArt.THUMB_HEIGHT)
        val part = item.part
        val key = item.key?.trim().orEmpty()
        return when {
            part != null -> RemoteEntry(
                name = PlexItems.displayName(item),
                path = key.ifEmpty { item.ratingKey },
                isDirectory = false,
                sizeBytes = part.size?.takeIf { it > 0 },
                modifiedAt = item.updatedAt?.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
                playUri = PlexItems.playUri(client, item),
                imageUrl = image,
            )
            // Shows, seasons and collections open with their key.
            key.startsWith("/library/") -> RemoteEntry(
                name = PlexItems.displayName(item),
                path = key,
                isDirectory = true,
                imageUrl = image,
            )
            else -> null
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
