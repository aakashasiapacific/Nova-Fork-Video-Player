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
 * Version 3 adds what network and media-server sources need: sources.port, token, userId,
 * serverId and libraryIds, and videos.remoteId.
 * Version 4 adds music (songs, playlist_songs) and the watchlist.
 * Version 5 adds metadata.textlessPosterPath, castNames and directors, and artwork_choices.
 */
@Database(
    entities = [
        SourceEntity::class,
        VideoEntity::class,
        MetadataEntity::class,
        MatchCacheEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SongEntity::class,
        PlaylistSongEntity::class,
        WatchlistEntity::class,
        ArtworkChoiceEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun videoDao(): VideoDao
    abstract fun metadataDao(): MetadataDao
    abstract fun matchCacheDao(): MatchCacheDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao
    abstract fun playlistSongDao(): PlaylistSongDao
    abstract fun watchlistDao(): WatchlistDao

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
         * Adds the network-source columns; every one is nullable without a default, so existing rows
         * read null (no port, token, user, server or library choice; no remote id). Room compares
         * the tables with [SourceEntity] and [VideoEntity] afterwards, and SQLite appends added
         * columns last, where the entities declare them too.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sources` ADD COLUMN `port` INTEGER")
                db.execSQL("ALTER TABLE `sources` ADD COLUMN `token` TEXT")
                db.execSQL("ALTER TABLE `sources` ADD COLUMN `userId` TEXT")
                db.execSQL("ALTER TABLE `sources` ADD COLUMN `serverId` TEXT")
                db.execSQL("ALTER TABLE `sources` ADD COLUMN `libraryIds` TEXT")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `remoteId` TEXT")
            }
        }

        /**
         * Adds the music and watchlist tables; nothing existing changes. The statements are the
         * ones Room generates for [SongEntity], [PlaylistSongEntity] and [WatchlistEntity] (see
         * app/schemas/…/4.json), which Room checks the migrated tables against.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `songs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sourceId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`folder` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT, `albumArtist` TEXT, " +
                        "`album` TEXT, `albumKey` TEXT NOT NULL, `artistKey` TEXT NOT NULL, `trackNumber` INTEGER, " +
                        "`discNumber` INTEGER, `year` INTEGER, `genre` TEXT, `durationMs` INTEGER, " +
                        "`sizeBytes` INTEGER, `dateAdded` INTEGER NOT NULL, `dateModified` INTEGER, " +
                        "`coverUri` TEXT, `lyricsUri` TEXT, `playCount` INTEGER NOT NULL, " +
                        "`lastPlayedAt` INTEGER, `present` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_songs_uri` ON `songs` (`uri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_sourceId` ON `songs` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_albumKey` ON `songs` (`albumKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artistKey` ON `songs` (`artistKey`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_songs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watchlist` (`itemKey` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemKey`))",
                )
            }
        }

        /**
         * Adds the metadata columns for the tall hero and search (nullable: old rows read null and
         * are fetched again) and the table of artwork the user picked.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `metadata` ADD COLUMN `textlessPosterPath` TEXT")
                db.execSQL("ALTER TABLE `metadata` ADD COLUMN `castNames` TEXT")
                db.execSQL("ALTER TABLE `metadata` ADD COLUMN `directors` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `artwork_choices` (`metadataKey` TEXT NOT NULL, `posterPath` TEXT, " +
                        "`backdropPath` TEXT, PRIMARY KEY(`metadataKey`))",
                )
            }
        }

        /**
         * Upgrades between schema versions, oldest first (`Migration(1, 2)`, `Migration(2, 3)`, …).
         * Watch history, resume points and manual matches only survive an app update through these:
         * with a version missing here Room refuses to open the database rather than wipe it.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun create(context: Context): NovaDatabase =
            Room.databaseBuilder(context.applicationContext, NovaDatabase::class.java, "nova.db")
                .addMigrations(*MIGRATIONS)
                // Only an older app finding a newer database (a downgrade) starts over empty.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
