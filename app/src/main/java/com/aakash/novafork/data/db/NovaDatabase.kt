package com.aakash.novafork.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Version 1 is the baseline; Room exports each version's schema to app/schemas (keep those JSON
 * files in the repository). A schema change bumps [version] and appends a Migration to
 * [MIGRATIONS] that carries the old data over.
 */
@Database(
    entities = [SourceEntity::class, VideoEntity::class, MetadataEntity::class, MatchCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun videoDao(): VideoDao
    abstract fun metadataDao(): MetadataDao
    abstract fun matchCacheDao(): MatchCacheDao

    companion object {
        /**
         * Upgrades between schema versions, oldest first (`Migration(1, 2)`, `Migration(2, 3)`, …).
         * Watch history, resume points and manual matches only survive an app update through these:
         * with a version missing here Room refuses to open the database rather than wipe it.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun create(context: Context): NovaDatabase =
            Room.databaseBuilder(context.applicationContext, NovaDatabase::class.java, "nova.db")
                .addMigrations(*MIGRATIONS)
                // Only an older app finding a newer database (a downgrade) starts over empty.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
