package com.aakash.novafork.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.PlaylistEntity
import com.aakash.novafork.data.db.PlaylistItemEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * An installed app's database, written from the committed schema of version 1, opened by the
 * current [NovaDatabase]: Room runs [NovaDatabase.MIGRATIONS] and checks the result against the
 * entities, so a migration that differs from them fails here rather than on people's phones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaylistMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun versionOneUpgradesWithItsLibraryAndGetsPlaylists() = runTest {
        createVersion1()

        val database = NovaDatabase.create(context)
        try {
            val movie = requireNotNull(database.videoDao().byUri(MOVIE_URI))
            assertEquals(MOVIE_ID, movie.id)
            assertEquals(60_000L, movie.positionMs)
            assertEquals("Movies", database.sourceDao().byId(SOURCE_ID)?.name)
            // Through every later migration too, up to the current schema.
            assertEquals(6, database.openHelper.writableDatabase.version)

            val playlists = database.playlistDao()
            val playlistId = playlists.insert(PlaylistEntity(name = "Mix", createdAt = 1L, updatedAt = 1L))
            playlists.insertItems(listOf(PlaylistItemEntity(playlistId = playlistId, videoId = movie.id, position = 0)))
            assertEquals(listOf(movie.id), playlists.shownVideos(playlistId).map { it.video.id })

            // The migrated table has its foreign keys: deleting the video takes the item along.
            database.videoDao().deleteBySource(SOURCE_ID)
            assertTrue(playlists.items(playlistId).isEmpty())
        } finally {
            database.close()
        }
    }

    /** The version 1 database an installed app leaves behind, with one folder and one movie. */
    private fun createVersion1() {
        context.deleteDatabase(DATABASE_NAME)
        val schema = Json.parseToJsonElement(schemaFile(1).readText()).jsonObject.getValue("database").jsonObject
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
                "INSERT INTO sources (id, type, name, location, enabled, addedAt) " +
                    "VALUES ($SOURCE_ID, 'FOLDER', 'Movies', '$TREE', 1, 0)",
            )
            db.execSQL(
                "INSERT INTO videos (id, sourceId, uri, fileName, folder, dateAdded, kind, parsedTitle, matchState, " +
                    "positionMs, watched, playCount, present) VALUES ($MOVIE_ID, $SOURCE_ID, '$MOVIE_URI', " +
                    "'Inception (2010).mkv', 'Movies', 0, 'MOVIE', 'Inception', 'PENDING', 60000, 0, 0, 1)",
            )
            db.version = 1
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
        const val SOURCE_ID = 1L
        const val MOVIE_ID = 7L
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AMovies"
        const val MOVIE_URI = "$TREE/document/inception"
    }
}
