package com.abidnafi.pocketdeck.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GameEntity::class, RomFolderEntity::class, CollectionEntity::class, CollectionGameEntity::class,
        PlaySessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2: play_sessions (per-launch history for the game info page). Existing data is kept.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun romFolders(): RomFolderDao
    abstract fun collections(): CollectionDao
    abstract fun sessions(): PlaySessionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pocketdeck.db").build()
    }
}
