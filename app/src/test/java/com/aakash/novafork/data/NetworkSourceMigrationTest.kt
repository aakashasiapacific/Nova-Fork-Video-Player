package com.aakash.novafork.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A version 2 database (written from the committed app/schemas/…/2.json, with an SMB share, a
 * watched episode and a playlist) opened by the current [NovaDatabase]: MIGRATION_2_3 adds the
 * network-source columns, Room checks the tables against the entities, and nothing is lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkSourceMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun versionTwoUpgradesWithItsLibraryAndTakesNetworkSources() = runTest {
        createVersion2()

        val database = NovaDatabase.create(context)
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)

            val share = requireNotNull(database.sourceDao().byId(SOURCE_ID))
            assertEquals(SourceType.SMB, share.type)
            assertEquals("NAS", share.name)
            assertEquals(SHARE, share.location)
            assertEquals("alice", share.username)
            assertEquals("secret", share.password)
            assertEquals("WORKGROUP", share.domain)
            assertEquals(SCANNED_AT, share.lastScanAt)
            // The new columns read null on old rows.
            assertNull(share.port)
            assertNull(share.token)
            assertNull(share.userId)
            assertNull(share.serverId)
            assertNull(share.libraryIds)

            val episode = requireNotNull(database.videoDao().byId(EPISODE_ID))
            assertEquals(EPISODE_URI, episode.uri)
            assertEquals(VideoKind.EPISODE, episode.kind)
            assertEquals(MatchState.MANUAL, episode.matchState)
            assertEquals(1_234_000L, episode.positionMs)
            assertEquals(2, episode.playCount)
            assertTrue(episode.watched)
            assertNull(episode.remoteId)
            assertEquals(listOf(EPISODE_ID), database.playlistDao().shownVideos(PLAYLIST_ID).map { it.video.id })
            assertEquals("Mix", database.playlistDao().byId(PLAYLIST_ID)?.name)

            // The new columns are written and read back.
            val serverId = database.sourceDao().insert(
                SourceEntity(
                    type = SourceType.JELLYFIN,
                    name = "Jellyfin",
                    location = "http://jf.local:8096",
                    username = "alice",
                    port = 8096,
                    token = "tok",
                    userId = "u1",
                    serverId = "srv",
                    libraryIds = "movies,shows",
                ),
            )
            database.sourceDao().byId(serverId).let {
                assertEquals(SourceType.JELLYFIN, it?.type)
                assertEquals(8096, it?.port)
                assertEquals("tok", it?.token)
                assertEquals("u1", it?.userId)
                assertEquals("srv", it?.serverId)
                assertEquals("movies,shows", it?.libraryIds)
            }
            database.sourceDao().updateAuth(serverId, token = "new", userId = "u2", serverId = null)
            assertEquals("new", database.sourceDao().byId(serverId)?.token)
            assertNull(database.sourceDao().byId(serverId)?.serverId)
            database.videoDao().update(episode.copy(remoteId = "item-1"))
            assertEquals("item-1", database.videoDao().byId(EPISODE_ID)?.remoteId)
            // Every new type round-trips through its stored name.
            for (type in NetworkSources.TYPES) {
                val id = database.sourceDao().insert(SourceEntity(type = type, name = type.name, location = "x://$type"))
                assertEquals(type, database.sourceDao().byId(id)?.type)
            }
        } finally {
            database.close()
        }
    }

    /** The version 2 database an installed app leaves behind. */
    private fun createVersion2() {
        context.deleteDatabase(DATABASE_NAME)
        val schema = Json.parseToJsonElement(schemaFile(2).readText()).jsonObject.getValue("database").jsonObject
        assertEquals(2, schema.getValue("version").jsonPrimitive.content.toInt())
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            for (element in schema.getValue("entities").jsonArray) {
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace(TABLE_PLACEHOLDER, table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace(TABLE_PLACEHOLDER, table))
                }
            }
            for (query in schema.getValue("setupQueries").jsonArray) db.execSQL(query.jsonPrimitive.content)
            db.execSQL(
                "INSERT INTO sources (id, type, name, location, username, password, domain, enabled, addedAt, " +
                    "lastScanAt, lastError) VALUES ($SOURCE_ID, 'SMB', 'NAS', '$SHARE', 'alice', 'secret', 'WORKGROUP', " +
                    "1, 1000, $SCANNED_AT, NULL)",
            )
            db.execSQL(
                "INSERT INTO videos (id, sourceId, uri, fileName, folder, dateAdded, kind, parsedTitle, season, episode, " +
                    "showKey, matchState, positionMs, watched, playCount, present) VALUES ($EPISODE_ID, $SOURCE_ID, " +
                    "'$EPISODE_URI', 'Severance.S01E02.mkv', 'TV/Severance', 2000, 'EPISODE', 'Severance', 1, 2, " +
                    "'tv:95396', 'MANUAL', 1234000, 1, 2, 1)",
            )
            db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES ($PLAYLIST_ID, 'Mix', 1, 1)")
            db.execSQL("INSERT INTO playlist_items (playlistId, videoId, `position`) VALUES ($PLAYLIST_ID, $EPISODE_ID, 0)")
            db.version = 2
        }
    }

    /** app/schemas, from the module directory (Gradle) or the project root (an IDE run). */
    private fun schemaFile(version: Int): File =
        listOf("schemas", "app/schemas")
            .map { File(it, "com.aakash.novafork.data.db.NovaDatabase/$version.json") }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("No Room schema $version.json below ${File("").absolutePath}")

    private companion object {
        /** NovaDatabase.create's file name. */
        const val DATABASE_NAME = "nova.db"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
        const val SOURCE_ID = 3L
        const val EPISODE_ID = 11L
        const val PLAYLIST_ID = 5L
        const val SHARE = "smb://nas.local/media"
        const val EPISODE_URI = "$SHARE/TV/Severance/Severance.S01E02.mkv"
        const val SCANNED_AT = 1_700_000_000_000L
    }
}
