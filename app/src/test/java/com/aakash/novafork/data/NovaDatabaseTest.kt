package com.aakash.novafork.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aakash.novafork.data.db.MatchState
import com.aakash.novafork.data.db.NovaDatabase
import com.aakash.novafork.data.db.SourceEntity
import com.aakash.novafork.data.db.SourceType
import com.aakash.novafork.data.db.VideoEntity
import com.aakash.novafork.data.db.VideoKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [NovaDatabase]'s schema versions and how the on-disk database survives app updates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NovaDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrationsLeadFromTheFirstVersionToTheCurrentOne() {
        val database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val version = try {
            database.openHelper.writableDatabase.version
        } finally {
            database.close()
        }

        var reached = 1
        for (migration in NovaDatabase.MIGRATIONS) {
            assertEquals("Migrations are listed oldest first, without gaps", reached, migration.startVersion)
            assertTrue("Migration ${migration.startVersion} → ${migration.endVersion}", migration.endVersion > migration.startVersion)
            reached = migration.endVersion
        }
        // Without a path to the current version an updated app could not open its old database.
        assertEquals("NovaDatabase.MIGRATIONS must reach schema version $version", version, reached)
    }

    @Test
    fun reopeningKeepsTheLibrary() = runTest {
        val first = NovaDatabase.create(context)
        val sourceId = try {
            val id = first.sourceDao().insert(SourceEntity(type = SourceType.FOLDER, name = "Movies", location = TREE))
            val videoId = first.videoDao().insert(movie(id))
            first.videoDao().updatePosition(videoId, 60_000, 1_000L)
            id
        } finally {
            first.close()
        }

        val second = NovaDatabase.create(context)
        try {
            assertEquals("Movies", second.sourceDao().byId(sourceId)?.name)
            assertEquals(60_000L, second.videoDao().byUri(MOVIE_URI)?.positionMs)
        } finally {
            second.close()
        }
    }

    @Test
    fun databaseOfANewerAppVersionStartsOverInsteadOfFailing() = runTest {
        val first = NovaDatabase.create(context)
        try {
            val id = first.sourceDao().insert(SourceEntity(type = SourceType.FOLDER, name = "Movies", location = TREE))
            first.videoDao().insert(movie(id))
        } finally {
            first.close()
        }
        // What a later version of the app leaves behind before this one is installed over it.
        SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = it.version + 1
        }

        val second = NovaDatabase.create(context)
        try {
            assertTrue(second.sourceDao().all().isEmpty())
            assertNull(second.videoDao().byUri(MOVIE_URI))
            // Usable again right away.
            second.sourceDao().insert(SourceEntity(type = SourceType.FOLDER, name = "Movies", location = TREE))
            assertEquals(1, second.sourceDao().all().size)
        } finally {
            second.close()
        }
    }

    private fun movie(sourceId: Long) = VideoEntity(
        sourceId = sourceId,
        uri = MOVIE_URI,
        fileName = "Inception (2010).mkv",
        folder = "Movies",
        kind = VideoKind.MOVIE,
        parsedTitle = "Inception",
        parsedYear = 2010,
        matchState = MatchState.PENDING,
    )

    private companion object {
        /** NovaDatabase.create's file name. */
        const val DATABASE_NAME = "nova.db"
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AVideos"
        const val MOVIE_URI = "$TREE/document/inception"
    }
}
