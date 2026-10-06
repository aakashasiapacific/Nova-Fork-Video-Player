package com.aakash.novafork.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, VideoEntity::class, MetadataEntity::class, MatchCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun videoDao(): VideoDao
    abstract fun metadataDao(): MetadataDao
    abstract fun matchCacheDao(): MatchCacheDao

    companion object {
        fun create(context: Context): NovaDatabase =
            Room.databaseBuilder(context.applicationContext, NovaDatabase::class.java, "nova.db")
                // The library is a cache of what the sources contain; a rescan rebuilds it.
                .fallbackToDestructiveMigration()
                .build()
    }
}
