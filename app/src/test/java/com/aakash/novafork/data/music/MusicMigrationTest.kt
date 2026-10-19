package com.aakash.novafork.data.music

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.PlaylistEntity
import com.aakash.novafork.data.db.PlaylistSongEntity
import com.aakash.novafork.data.db.SongEntity
import com.aakash.novafork.data.db.WatchlistEntity
import kotlinx.coroutines.flow.first
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
 * A version 3 database (written from the committed app/schemas/…/3.json, with a folder, a movie
 * and a video playlist) opened by the current [NovaDatabase]: MIGRATION_3_4 adds the music and
 * watchlist tables, Room checks them against the entities, and the library is untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun versionThreeUpgradesAndTakesMusic() = runTest {
        createVersion3()

        val database = NovaDatabase.create(context)
        try {
            assertEquals(6, database.openHelper.writableDatabase.version)
            assertEquals(60_000L, database.videoDao().byId(MOVIE_ID)?.positionMs)
            assertEquals(listOf("Movies night"), database.playlistDao().observeWithCounts().first().map { it.name })

            val songs = database.songDao()
            songs.insertAll(
                listOf(
                    SongEntity(
                        sourceId = SOURCE_ID, uri = "$TREE/document/song", fileName = "01 Song.flac", folder = "Music",
                        title = "Song", albumKey = "a:band|album", artistKey = "band",
                    ),
                ),
            )
            val song = songs.bySource(SOURCE_ID).single()
            val mix = database.playlistDao().insert(PlaylistEntity(name = "Mix", createdAt = 2L, updatedAt = 2L))
            database.playlistSongDao().insertItems(listOf(PlaylistSongEntity(playlistId = mix, songId = song.id, position = 0)))
            database.watchlistDao().add(WatchlistEntity(itemKey = "v:$MOVIE_ID", addedAt = 3L))

            // Each tab lists its own playlists.
            assertEquals(listOf("Mix"), database.playlistSongDao().observePlaylists().first().map { it.name })
            assertEquals(listOf("Movies night"), database.playlistDao().observeWithCounts().first().map { it.name })
            assertEquals(listOf("v:$MOVIE_ID"), database.watchlistDao().observe().first().map { it.itemKey })

            // The migrated table has its foreign keys: a song that goes takes its playlist entry along.
            database.sourceDao().delete(SOURCE_ID)
            songs.deleteOrphans()
            assertTrue(database.playlistSongDao().items(mix).isEmpty())
        } finally {
            database.close()
        }
    }

    /** The version 3 database an installed app leaves behind. */
    private fun createVersion3() {
        context.deleteDatabase(DATABASE_NAME)
        val schema = Json.parseToJsonElement(schemaFile(3).readText()).jsonObject.getValue("database").jsonObject
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
                    "VALUES ($SOURCE_ID, 'FOLDER', 'Media', '$TREE', 1, 0)",
            )
            db.execSQL(
                "INSERT INTO videos (id, sourceId, uri, fileName, folder, dateAdded, kind, parsedTitle, matchState, " +
                    "positionMs, watched, playCount, present) VALUES ($MOVIE_ID, $SOURCE_ID, '$TREE/document/movie', " +
                    "'Inception (2010).mkv', 'Movies', 0, 'MOVIE', 'Inception', 'PENDING', 60000, 0, 0, 1)",
            )
            db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Movies night', 1, 1)")
            db.execSQL("INSERT INTO playlist_items (playlistId, videoId, position) VALUES (1, $MOVIE_ID, 0)")
            db.version = 3
        }
    }

    /** app/schemas, from the module directory (Gradle) or the project root (an IDE run). */
    private fun schemaFile(version: Int): File =
        listOf("schemas", "app/schemas")
            .map { File(it, "com.aakash.novafork.data.db.NovaDatabase/$version.json") }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("No Room schema $version.json below ${File("").absolutePath}")

    private companion object {
        const val DATABASE_NAME = "nova.db"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
        const val SOURCE_ID = 1L
        const val MOVIE_ID = 7L
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AMedia"
    }
}
