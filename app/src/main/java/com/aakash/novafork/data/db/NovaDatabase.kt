package com.aakash.novafork.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 is the baseline; Room exports each version's schema to app/schemas (keep those JSON
 * files in the repository). A schema change bumps [version] and appends a Migration to
 * [MIGRATIONS] that carries the old data over.
 *
 * Version 2 adds playlists (playlists, playlist_items).
 */
@Database(
    entities = [
        SourceEntity::class,
        VideoEntity::class,
        MetadataEntity::class,
        MatchCacheEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun videoDao(): VideoDao
    abstract fun metadataDao(): MetadataDao
    abstract fun matchCacheDao(): MatchCacheDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        /**
         * Adds the playlist tables; nothing existing changes. The statements are the ones Room
         * generates for [PlaylistEntity] and [PlaylistItemEntity] (see app/schemas/…/2.json): Room
         * compares the result with those entities after migrating and refuses a mismatch.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`playlistId` INTEGER NOT NULL, `videoId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`videoId`) REFERENCES `videos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_videoId` ON `playlist_items` (`videoId`)")
            }
        }

        /**
         * Upgrades between schema versions, oldest first (`Migration(1, 2)`, `Migration(2, 3)`, …).
         * Watch history, resume points and manual matches only survive an app update through these:
         * with a version missing here Room refuses to open the database rather than wipe it.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context): NovaDatabase =
            Room.databaseBuilder(context.applicationContext, NovaDatabase::class.java, "nova.db")
                .addMigrations(*MIGRATIONS)
                // Only an older app finding a newer database (a downgrade) starts over empty.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
