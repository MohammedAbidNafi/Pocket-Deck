package com.abidnafi.pocketdeck.data

import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType

/**
 * A collection PocketDeck keeps up to date by itself (Android games, PC games, one per ROM system…).
 * Nothing is stored: it's worked out from the library every time, so it's always current.
 * [id] is stable and is what the "hidden" and sort settings refer to.
 */
data class AutoCollection(val id: String, val name: String, val subtitle: String, val games: List<GameEntity>)

/** One PC that games can be streamed from. */
data class AutoCollectionHost(val id: Long, val name: String)

object AutoCollections {
    const val ANDROID = "auto:android"
    const val PC = "auto:pc"
    const val EMULATORS = "auto:emulators"
    const val FAVOURITES = "auto:favourites"
    const val UNPLAYED = "auto:unplayed"
    fun platformId(platform: String) = "auto:platform:$platform"
    fun hostId(host: Long) = "auto:pc:$host"

    /**
     * Every auto collection that has games, in display order: Favourites, Android, PC (one per PC
     * when there are several), one per ROM system in [platforms] order, Emulators, Not played yet.
     * Hidden games are left out.
     */
    fun build(
        games: List<GameEntity>,
        platforms: List<Pair<String, String>>,
        hosts: List<AutoCollectionHost>,
    ): List<AutoCollection> = buildList {
        val visible = games.filterNot { it.hidden }
        fun add(id: String, name: String, subtitle: String, list: List<GameEntity>) {
            if (list.isNotEmpty()) add(AutoCollection(id, name, subtitle, list))
        }
        add(FAVOURITES, "Favourites", "Games you starred", visible.filter { it.favorite })
        add(ANDROID, "Android games", "Installed on this device", visible.filter { it.type == GameType.APP })

        val pc = visible.filter { it.type == GameType.STREAM }
        val byHost = pc.groupBy { it.hostId }
        if (byHost.size > 1) {
            for (h in hosts) add(hostId(h.id), h.name, "Streamed from this PC", byHost[h.id].orEmpty())
        } else {
            add(PC, "PC games", hosts.singleOrNull()?.let { "Streamed from ${it.name}" } ?: "Streamed from your PC", pc)
        }

        val roms = visible.filter { it.type == GameType.ROM }.groupBy { it.platformId }
        for ((id, name) in platforms) add(platformId(id), name, "ROMs", roms[id].orEmpty())

        add(EMULATORS, "Emulators", "Emulator apps", visible.filter { it.type == GameType.EMULATOR })
        add(UNPLAYED, "Not played yet", "Waiting for their first session", visible.filter { it.type != GameType.EMULATOR && it.playCount == 0 })
    }
}

/** How games are ordered inside a list. [addedAt] lets a collection sort by when a game joined it. */
enum class GameSort(val label: String) {
    NAME("A–Z"), RECENT("Recently played"), PLAYTIME("Most played"), ADDED("Recently added");

    fun apply(list: List<GameEntity>, addedAt: (GameEntity) -> Long = { it.addedAt }): List<GameEntity> = when (this) {
        NAME -> list.sortedBy { it.sortTitle }
        RECENT -> list.sortedWith(compareByDescending<GameEntity> { it.lastPlayedAt ?: 0L }.thenBy { it.sortTitle })
        PLAYTIME -> list.sortedWith(compareByDescending<GameEntity> { it.playTimeMs }.thenBy { it.sortTitle })
        ADDED -> list.sortedWith(compareByDescending<GameEntity> { addedAt(it) }.thenBy { it.sortTitle })
    }

    fun next(): GameSort = entries[(ordinal + 1) % entries.size]
}

/** How the Collections tab orders collections. */
enum class CollectionSort(val label: String) {
    NAME("A–Z"), RECENT("Recently played"), SIZE("Most games"), NEWEST("Newest");

    fun next(): CollectionSort = entries[(ordinal + 1) % entries.size]
}
