package com.abidnafi.pocketdeck.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY sortTitle COLLATE NOCASE")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): GameEntity?

    @Query("SELECT * FROM games")
    suspend fun getAll(): List<GameEntity>

    @Query("SELECT * FROM games WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: GameEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Update
    suspend fun updateAll(games: List<GameEntity>)

    @Delete
    suspend fun delete(game: GameEntity)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM games WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    @Query("UPDATE games SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE games SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE games SET customArt = :path WHERE id = :id")
    suspend fun setCustomArt(id: Long, path: String?)

    @Query("UPDATE games SET emulatorOverride = :emulatorId WHERE id = :id")
    suspend fun setEmulatorOverride(id: Long, emulatorId: String?)

    @Query("UPDATE games SET lastPlayedAt = :time, playCount = playCount + 1 WHERE id = :id")
    suspend fun markLaunched(id: Long, time: Long)

    @Query("UPDATE games SET playTimeMs = playTimeMs + :ms WHERE id = :id")
    suspend fun addPlayTime(id: Long, ms: Long)
}

@Dao
interface RomFolderDao {
    @Query("SELECT * FROM rom_folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RomFolderEntity>>

    @Query("SELECT * FROM rom_folders")
    suspend fun getAll(): List<RomFolderEntity>

    @Query("SELECT * FROM rom_folders WHERE id = :id")
    suspend fun get(id: Long): RomFolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: RomFolderEntity): Long

    @Update
    suspend fun update(folder: RomFolderEntity)

    @Delete
    suspend fun delete(folder: RomFolderEntity)
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT c.*, (SELECT COUNT(*) FROM collection_games cg WHERE cg.collectionId = c.id) AS gameCount
           FROM collections c ORDER BY c.name COLLATE NOCASE"""
    )
    fun observeAll(): Flow<List<CollectionWithCount>>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observe(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collection_games ORDER BY addedAt DESC")
    fun observeMemberships(): Flow<List<CollectionGameEntity>>

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    fun observeCollectionsFor(gameId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGame(entry: CollectionGameEntity)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId AND gameId = :gameId")
    suspend fun removeGame(collectionId: Long, gameId: Long)
}

@Dao
interface PlaySessionDao {
    @Insert
    suspend fun insert(session: PlaySessionEntity): Long

    @Query("UPDATE play_sessions SET durationMs = :ms WHERE gameId = :gameId AND startedAt = :startedAt")
    suspend fun setDuration(gameId: Long, startedAt: Long, ms: Long)

    @Query("SELECT * FROM play_sessions WHERE gameId = :gameId ORDER BY startedAt DESC")
    fun observeFor(gameId: Long): Flow<List<PlaySessionEntity>>
}
