package com.abidnafi.pocketdeck.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.abidnafi.pocketdeck.data.apps.InstalledApp
import com.abidnafi.pocketdeck.data.apps.InstalledApps
import com.abidnafi.pocketdeck.data.db.AppDatabase
import com.abidnafi.pocketdeck.data.db.CollectionEntity
import com.abidnafi.pocketdeck.data.db.CollectionGameEntity
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameSource
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.db.RomFolderEntity
import com.abidnafi.pocketdeck.data.emulators.EmulatorRegistry
import com.abidnafi.pocketdeck.data.roms.RomNames
import com.abidnafi.pocketdeck.data.roms.RomScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ScanState(val running: Boolean = false, val folderName: String? = null, val found: Int = 0)

/** Single entry point for reading and changing the game library. */
class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val apps: InstalledApps,
    private val emulators: EmulatorRegistry,
    private val scanner: RomScanner,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val gamesDao = db.games()
    private val foldersDao = db.romFolders()
    private val collectionsDao = db.collections()
    private val syncLock = Mutex()
    private val scanLock = Mutex()

    val games = gamesDao.observeAll()
    val folders = foldersDao.observeAll()
    val collections = collectionsDao.observeAll()
    val memberships = collectionsDao.observeMemberships()

    private val _scan = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scan.asStateFlow()

    fun game(id: Long) = gamesDao.observe(id)
    fun collection(id: Long) = collectionsDao.observe(id)
    fun collectionsFor(gameId: Long) = collectionsDao.observeCollectionsFor(gameId)

    // ---- Android apps -------------------------------------------------------------------------

    fun installedApps(): List<InstalledApp> = apps.query()

    /** Adds newly installed games/emulators, drops uninstalled ones, refreshes labels. */
    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        syncLock.withLock {
            val autoDetect = settings.current().autoDetectGames
            val installed = apps.query()
            val installedByPkg = installed.associateBy { it.packageName }
            val emulatorPackages = emulators.allPackages()
            val existing = gamesDao.getAll()
            val existingKeys = existing.map { it.key }.toSet()
            val now = System.currentTimeMillis()

            val toInsert = installed.mapNotNull { app ->
                when {
                    app.packageName in emulatorPackages && GameEntity.emulatorKey(app.packageName) !in existingKeys ->
                        newAppEntity(app, GameType.EMULATOR, auto = true, now)
                    app.packageName !in emulatorPackages && autoDetect && app.isGame &&
                        GameEntity.appKey(app.packageName) !in existingKeys ->
                        newAppEntity(app, GameType.APP, auto = true, now)
                    else -> null
                }
            }
            gamesDao.insertAll(toInsert)

            // Only app/emulator rows are backed by an installed package (ROMs and PC games are not).
            val packaged = existing.filter { it.type == GameType.APP || it.type == GameType.EMULATOR }
            gamesDao.deleteIds(packaged.filter { it.packageName !in installedByPkg }.map { it.id })
            gamesDao.updateAll(
                packaged.mapNotNull { g ->
                    val label = installedByPkg[g.packageName]?.label ?: return@mapNotNull null
                    if (label == g.title) null else g.copy(title = label, sortTitle = RomNames.sortTitle(label))
                },
            )
        }
    }

    suspend fun addApp(app: InstalledApp) {
        val type = if (app.packageName in emulators.allPackages()) GameType.EMULATOR else GameType.APP
        val key = if (type == GameType.EMULATOR) GameEntity.emulatorKey(app.packageName) else GameEntity.appKey(app.packageName)
        val existing = gamesDao.getAll().firstOrNull { it.key == key }
        if (existing != null) {
            gamesDao.setHidden(existing.id, false)
        } else {
            gamesDao.insert(newAppEntity(app, type, auto = false, System.currentTimeMillis()))
        }
    }

    private fun newAppEntity(app: InstalledApp, type: GameType, auto: Boolean, now: Long) = GameEntity(
        key = if (type == GameType.EMULATOR) GameEntity.emulatorKey(app.packageName) else GameEntity.appKey(app.packageName),
        type = type,
        title = app.label,
        sortTitle = RomNames.sortTitle(app.label),
        packageName = app.packageName,
        source = when {
            !auto -> GameSource.MANUAL
            app.fromPlayStore -> GameSource.PLAY_STORE
            else -> GameSource.SIDELOADED
        },
        autoDetected = auto,
        addedAt = now,
    )

    // ---- Per-game actions ---------------------------------------------------------------------

    suspend fun setFavorite(id: Long, favorite: Boolean) = gamesDao.setFavorite(id, favorite)
    suspend fun setHidden(id: Long, hidden: Boolean) = gamesDao.setHidden(id, hidden)
    suspend fun setEmulatorOverride(id: Long, emulatorId: String?) = gamesDao.setEmulatorOverride(id, emulatorId)

    /**
     * Removes a game from the library. Auto-detected apps and scanned ROMs would come straight back
     * on the next sync, so those are hidden instead.
     */
    suspend fun remove(game: GameEntity) {
        // PC games are auto-detected too: they'd return on the next app-list sync.
        if (game.autoDetected || game.type == GameType.ROM || game.type == GameType.STREAM) gamesDao.setHidden(game.id, true) else gamesDao.delete(game)
    }

    /** Copies a picked image into app storage so it survives the source being deleted. */
    suspend fun setCustomArt(game: GameEntity, image: Uri?) = withContext(Dispatchers.IO) {
        game.customArt?.let { File(it).delete() }
        if (image == null) {
            gamesDao.setCustomArt(game.id, null)
            return@withContext
        }
        val dir = File(context.filesDir, "art").apply { mkdirs() }
        val file = File(dir, "custom_${game.id}_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(image)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        gamesDao.setCustomArt(game.id, file.absolutePath)
    }

    // ---- ROM folders --------------------------------------------------------------------------

    suspend fun addRomFolder(treeUri: Uri, platformId: String?): Long {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            .onFailure { context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "ROMs"
        val id = foldersDao.insert(RomFolderEntity(treeUri = treeUri.toString(), name = name, platformId = platformId))
        val folder = foldersDao.get(id) ?: foldersDao.getAll().first { it.treeUri == treeUri.toString() }
        rescan(folder.id)
        return folder.id
    }

    suspend fun setFolderPlatform(folder: RomFolderEntity, platformId: String?) {
        foldersDao.update(folder.copy(platformId = platformId))
        rescan(folder.id)
    }

    suspend fun removeRomFolder(folder: RomFolderEntity) {
        gamesDao.deleteByFolder(folder.id)
        foldersDao.delete(folder)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** Rescans one folder (or every folder when [folderId] is null) in the app scope. */
    fun rescan(folderId: Long? = null) {
        scope.launch {
            scanLock.withLock {
                val targets = foldersDao.getAll().filter { folderId == null || it.id == folderId }
                for (folder in targets) scanFolder(folder)
                _scan.value = ScanState()
            }
        }
    }

    private suspend fun scanFolder(folder: RomFolderEntity) {
        _scan.value = ScanState(running = true, folderName = folder.name)
        val found = runCatching {
            scanner.scan(Uri.parse(folder.treeUri), folder.platformId) { n -> _scan.update { it.copy(found = n) } }
        }.getOrElse { return }

        val existing = gamesDao.getByFolder(folder.id).associateBy { it.key }
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        val inserts = mutableListOf<GameEntity>()
        val updates = mutableListOf<GameEntity>()
        for (rom in found) {
            val key = GameEntity.romKey(rom.documentUri)
            if (!seen.add(key)) continue
            val old = existing[key]
            if (old == null) {
                inserts += GameEntity(
                    key = key, type = GameType.ROM, title = rom.title, sortTitle = RomNames.sortTitle(rom.title),
                    platformId = rom.platformId, romUri = rom.documentUri, romPath = rom.path, fileName = rom.fileName,
                    folderId = folder.id, source = GameSource.ROM_SCAN, autoDetected = true, addedAt = now,
                )
            } else if (old.platformId != rom.platformId || old.romPath != rom.path || old.fileName != rom.fileName) {
                updates += old.copy(platformId = rom.platformId, romPath = rom.path, fileName = rom.fileName)
            }
        }
        gamesDao.insertAll(inserts)
        gamesDao.updateAll(updates)
        gamesDao.deleteIds(existing.values.filter { it.key !in seen }.map { it.id })
        foldersDao.update(folder.copy(lastScanAt = now, gameCount = seen.size))
    }

    // ---- Collections --------------------------------------------------------------------------

    suspend fun createCollection(name: String): Long =
        collectionsDao.insert(CollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renameCollection(id: Long, name: String) = collectionsDao.rename(id, name.trim())
    suspend fun deleteCollection(id: Long) = collectionsDao.delete(id)

    suspend fun setInCollection(collectionId: Long, gameId: Long, member: Boolean) {
        if (member) collectionsDao.addGame(CollectionGameEntity(collectionId, gameId, System.currentTimeMillis()))
        else collectionsDao.removeGame(collectionId, gameId)
    }
}
