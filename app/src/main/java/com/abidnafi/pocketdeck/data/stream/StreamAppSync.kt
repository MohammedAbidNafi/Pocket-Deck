package com.abidnafi.pocketdeck.data.stream

import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameSource
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.roms.RomNames

/** One entry of a host's app list. */
data class HostApp(val id: Int, val name: String, val hdrSupported: Boolean = false)

/** What to change in the library after fetching a host's app list. Pure Kotlin so it is unit-testable. */
data class StreamAppDiff(
    val inserts: List<GameEntity>,
    val updates: List<GameEntity>,
    val deleteIds: List<Long>,
)

object StreamAppSync {
    /**
     * Matches [apps] against the [existing] library rows of one host by key. New apps are added,
     * renamed apps are updated (user data such as favourites and play time is kept), and apps the
     * host no longer lists are removed.
     */
    fun diff(hostId: Long, hostUuid: String, existing: List<GameEntity>, apps: List<HostApp>, now: Long): StreamAppDiff {
        val byKey = existing.associateBy { it.key }
        val seen = HashSet<String>()
        val inserts = mutableListOf<GameEntity>()
        val updates = mutableListOf<GameEntity>()
        for (app in apps) {
            val key = GameEntity.streamKey(hostUuid, app.id)
            if (!seen.add(key)) continue
            val title = app.name.trim().ifEmpty { "App ${app.id}" }
            val old = byKey[key]
            when {
                old == null -> inserts += GameEntity(
                    key = key,
                    type = GameType.STREAM,
                    title = title,
                    sortTitle = RomNames.sortTitle(title),
                    source = GameSource.PC_HOST,
                    autoDetected = true,
                    addedAt = now,
                    hostId = hostId,
                    streamAppId = app.id,
                )
                old.title != title || old.hostId != hostId ->
                    updates += old.copy(title = title, sortTitle = RomNames.sortTitle(title), hostId = hostId)
            }
        }
        val deleteIds = existing.filter { it.key !in seen }.map { it.id }
        return StreamAppDiff(inserts, updates, deleteIds)
    }
}
