package com.abidnafi.pocketdeck.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GameEntity::class, RomFolderEntity::class, CollectionEntity::class, CollectionGameEntity::class,
        PlaySessionEntity::class, StreamHostEntity::class,
    ],
    version = 3,
    exportSchema = true,
    // v2: play_sessions (per-launch history for the game info page).
    // v3: stream_hosts + streaming columns on games (PC streaming). Existing data is kept.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun romFolders(): RomFolderDao
    abstract fun collections(): CollectionDao
    abstract fun sessions(): PlaySessionDao
    abstract fun streamHosts(): StreamHostDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pocketdeck.db").build()
    }
}
