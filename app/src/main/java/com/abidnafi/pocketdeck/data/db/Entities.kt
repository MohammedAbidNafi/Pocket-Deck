package com.abidnafi.pocketdeck.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** [STREAM]: a game on a paired PC, played by streaming it from that host (Sunshine / GameStream). */
enum class GameType { APP, ROM, EMULATOR, STREAM }

enum class GameSource { PLAY_STORE, SIDELOADED, MANUAL, ROM_SCAN, PC_HOST }

@Entity(
    tableName = "games",
    indices = [Index(value = ["key"], unique = true), Index("folderId"), Index("packageName"), Index("hostId")],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identity: `app:<pkg>`, `emu:<pkg>`, `rom:<document uri>` or `pc:<host uuid>:<app id>`. */
    val key: String,
    val type: GameType,
    val title: String,
    val sortTitle: String,
    val packageName: String? = null,
    val platformId: String? = null,
    val romUri: String? = null,
    val romPath: String? = null,
    val fileName: String? = null,
    val folderId: Long? = null,
    val source: GameSource,
    val autoDetected: Boolean = false,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    /** Absolute path to a user-picked image in app storage. */
    val customArt: String? = null,
    val emulatorOverride: String? = null,
    val addedAt: Long,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val playTimeMs: Long = 0,
    /** STREAM only: the [StreamHostEntity] this game lives on. */
    val hostId: Long? = null,
    /** STREAM only: the host's id for the app. */
    val streamAppId: Int? = null,
    /** STREAM only: box art downloaded from the host. */
    val hostArt: String? = null,
) {
    companion object {
        fun appKey(pkg: String) = "app:$pkg"
        fun emulatorKey(pkg: String) = "emu:$pkg"
        fun romKey(uri: String) = "rom:$uri"
        fun streamKey(hostUuid: String, appId: Int) = "pc:$hostUuid:$appId"
    }
}

/**
 * A gaming PC running Sunshine (or another GameStream host). Addresses are host names or IPs
 * without the port; [httpPort] applies to all of them.
 */
@Entity(tableName = "stream_hosts", indices = [Index(value = ["uuid"], unique = true)])
data class StreamHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    /** Found by mDNS on the local network. */
    val localAddress: String? = null,
    /** Typed in by the user. Tried first. */
    val manualAddress: String? = null,
    /** Public address reported by the host, for streaming away from home. */
    val remoteAddress: String? = null,
    val httpPort: Int = DEFAULT_HTTP_PORT,
    val macAddress: String? = null,
    /** Base64 DER of the certificate pinned when pairing; null = not paired. */
    val serverCert: String? = null,
    val addedAt: Long,
    val lastSeenAt: Long? = null,
) {
    val paired: Boolean get() = serverCert != null

    /** Addresses in the order they should be tried. */
    val addresses: List<String> get() = listOfNotNull(manualAddress, localAddress, remoteAddress).distinct()

    companion object {
        const val DEFAULT_HTTP_PORT = 47989
    }
}

@Entity(tableName = "rom_folders", indices = [Index(value = ["treeUri"], unique = true)])
data class RomFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val name: String,
    /** Null = detect per sub-folder / extension. */
    val platformId: String? = null,
    val lastScanAt: Long? = null,
    val gameCount: Int = 0,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_games",
    primaryKeys = ["collectionId", "gameId"],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(GameEntity::class, ["id"], ["gameId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("gameId")],
)
data class CollectionGameEntity(
    val collectionId: Long,
    val gameId: Long,
    val addedAt: Long,
)

data class CollectionWithCount(
    @Embedded val collection: CollectionEntity,
    val gameCount: Int,
)

/**
 * One launch of a game. [durationMs] is filled in when the player comes back to PocketDeck
 * (0 = not timed, e.g. launched from the home-screen widget). [from] is "app" or "widget".
 */
@Entity(
    tableName = "play_sessions",
    foreignKeys = [ForeignKey(GameEntity::class, ["id"], ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId")],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val startedAt: Long,
    val durationMs: Long = 0,
    val from: String = "app",
)
