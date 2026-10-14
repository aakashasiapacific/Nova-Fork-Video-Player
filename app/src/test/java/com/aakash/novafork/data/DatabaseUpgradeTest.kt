package com.aakash.novafork.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.core.library.LibraryKeys
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * An app update keeps the library: a database exactly as the first release created it (built from
 * Room's exported schema, app/schemas/…/1.json) opens with the current [NovaDatabase] through
 * [NovaDatabase.MIGRATIONS] with every row intact. While opening, Room checks the migrated tables
 * against the current entities, so a migration that does not produce them fails here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseUpgradeTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    /** The first release's tables, indices and Room bookkeeping, filled with one watched episode. */
    private fun writeVersion1Database() {
        val schema = Json.parseToJsonElement(schemaFile(1).readText()).jsonObject.getValue("database").jsonObject
        assertEquals(1, schema.getValue("version").jsonPrimitive.content.toInt())
        context.deleteDatabase(DATABASE_NAME)
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (element in schema.getValue("entities").jsonArray) {
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.createSql(table))
                for (index in entity["indices"]?.jsonArray.orEmpty()) {
                    db.execSQL(index.jsonObject.createSql(table))
                }
            }
            for (query in schema.getValue("setupQueries").jsonArray) {
                db.execSQL(query.jsonPrimitive.content)
            }
            db.execSQL(
                "INSERT INTO sources (id, type, name, location, username, password, domain, enabled, addedAt, " +
                    "lastScanAt, lastError) VALUES (1, 'SMB', 'NAS', '$SHARE', 'alice', 'secret', NULL, 1, 1000, " +
                    "$SCANNED_AT, NULL)",
            )
            db.execSQL(
                "INSERT INTO videos (id, sourceId, uri, fileName, folder, sizeBytes, durationMs, width, height, " +
                    "dateAdded, dateModified, kind, parsedTitle, parsedYear, season, episode, episodeEnd, " +
                    "metadataKey, showKey, episodeTitle, episodeOverview, episodeStillPath, episodeAirDate, " +
                    "matchState, matchAttemptAt, localPoster, localBackdrop, localThumb, positionMs, lastPlayedAt, " +
                    "watched, playCount, audioTrackId, subtitleTrackId, present) VALUES (10, 1, '$EPISODE_URI', " +
                    "'Severance.S01E02.mkv', 'TV/Severance', 1500000000, 3300000, 1920, 1080, 2000, NULL, " +
                    "'EPISODE', 'Severance', NULL, 1, 2, NULL, '$SHOW_KEY', '$SHOW_KEY', 'Half Loop', NULL, " +
                    "'/still.jpg', '2022-02-18', 'MANUAL', 3000, NULL, NULL, NULL, 1234000, 4000, 1, 1, 2, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO metadata (`key`, tmdbId, type, title, originalTitle, year, overview, tagline, " +
                    "posterPath, backdropPath, rating, genres, runtimeMin, seasonPostersJson, updatedAt) VALUES " +
                    "('$SHOW_KEY', 95396, 'tv', 'Severance', NULL, 2022, NULL, NULL, '/severance.jpg', NULL, 8.4, " +
                    "'Drama', 50, NULL, 3000)",
            )
            db.execSQL(
                "INSERT INTO match_cache (`query`, metadataKey, checkedAt) VALUES ('$MATCH_QUERY', '$SHOW_KEY', 3000)",
            )
            db.version = 1
        }
    }

    @Test
    fun firstReleasesLibraryOpensWithEverythingKept() = runTest {
        writeVersion1Database()

        val database = NovaDatabase.create(context)
        try {
            val source = requireNotNull(database.sourceDao().byId(1))
            assertEquals(SourceType.SMB, source.type)
            assertEquals("NAS", source.name)
            assertEquals(SHARE, source.location)
            assertEquals("alice", source.username)
            assertEquals("secret", source.password)
            assertTrue(source.enabled)
            assertEquals(SCANNED_AT, source.lastScanAt)

            val episode = requireNotNull(database.videoDao().withMeta(10))
            episode.video.let {
                assertEquals(EPISODE_URI, it.uri)
                assertEquals(1L, it.sourceId)
                assertEquals(VideoKind.EPISODE, it.kind)
                assertEquals(MatchState.MANUAL, it.matchState)
                assertEquals(1, it.season)
                assertEquals(2, it.episode)
                assertEquals(SHOW_KEY, it.showKey)
                assertEquals("Half Loop", it.episodeTitle)
                assertEquals(3_300_000L, it.durationMs)
                assertEquals(1_234_000L, it.positionMs)
                assertEquals(4_000L, it.lastPlayedAt)
                assertTrue(it.watched)
                assertEquals(1, it.playCount)
                assertEquals(2, it.audioTrackId)
                assertNull(it.subtitleTrackId)
                assertTrue(it.present)
            }
            assertEquals("Severance", episode.meta?.title)
            assertEquals(SHOW_KEY, database.matchCacheDao().get(MATCH_QUERY)?.metadataKey)

            // Usable right away; ids go on after the old ones.
            val id = database.videoDao().insert(
                VideoEntity(
                    sourceId = 1,
                    uri = "$SHARE/TV/Severance/Severance.S01E03.mkv",
                    fileName = "Severance.S01E03.mkv",
                    folder = "TV/Severance",
                    parsedTitle = "Severance",
                ),
            )
            assertTrue("New id $id", id > 10)
            assertEquals(2, database.videoDao().bySource(1).size)
        } finally {
            database.close()
        }
    }

    private fun JsonObject.createSql(table: String): String =
        getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    /** Room exports to app/schemas; unit tests run in the module directory. */
    private fun schemaFile(version: Int): File {
        val name = "com.aakash.novafork.data.db.NovaDatabase/$version.json"
        return listOf(File("schemas/$name"), File("app/schemas/$name")).firstOrNull { it.isFile }
            ?: throw AssertionError("Exported Room schema $name not found from ${File("").absolutePath}")
    }

    private companion object {
        /** NovaDatabase.create's file name. */
        const val DATABASE_NAME = "nova.db"
        const val SHARE = "smb://nas.local/media"
        const val EPISODE_URI = "$SHARE/TV/Severance/Severance.S01E02.mkv"
        const val SCANNED_AT = 1_700_000_000_000L
        const val SHOW_KEY = "tv:95396"
        val MATCH_QUERY = LibraryKeys.matchQueryKey("tv", "Severance", null)
    }
}
