package com.abidnafi.pocketdeck.ui.home

import com.abidnafi.pocketdeck.data.Settings
import com.abidnafi.pocketdeck.data.db.CollectionGameEntity
import com.abidnafi.pocketdeck.data.db.CollectionWithCount
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.ui.components.ArtStyle

/** One home shelf. [id] is stable and is what the row order / hidden settings refer to. */
data class ShelfModel(val id: String, val title: String, val games: List<GameEntity>, val style: ArtStyle, val kind: String)

/**
 * Every possible home row in its default order: Continue playing, Recently added, Favourites,
 * one row per collection, Android games, PC games, one row per system, Emulators. Empty rows are left out.
 */
fun allHomeRows(
    games: List<GameEntity>,
    collections: List<CollectionWithCount>,
    memberships: List<CollectionGameEntity>,
    platformOrder: List<Pair<String, String>>,
): List<ShelfModel> = buildList {
    val visible = games.filterNot { it.hidden }
    val recent = visible.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }.take(12)
    if (recent.isNotEmpty()) add(ShelfModel("recent", "Continue playing", recent, ArtStyle.WIDE, "Recently played"))
    val added = visible.filter { it.type != GameType.EMULATOR }.sortedByDescending { it.addedAt }.take(12)
    if (added.isNotEmpty()) add(ShelfModel("added", "Recently added", added, ArtStyle.WIDE, "Newest in your library"))
    val favorites = visible.filter { it.favorite }
    if (favorites.isNotEmpty()) add(ShelfModel("favorites", "Favourites", favorites, ArtStyle.COVER, "Favourites"))

    val byId = visible.associateBy { it.id }
    val byCollection = memberships.groupBy { it.collectionId }
    for (c in collections) {
        // Memberships arrive newest first, so a collection row shows its latest additions first.
        val list = byCollection[c.collection.id].orEmpty().mapNotNull { byId[it.gameId] }
        if (list.isNotEmpty()) add(ShelfModel("collection:${c.collection.id}", c.collection.name, list, ArtStyle.COVER, "Collection"))
    }

    val apps = visible.filter { it.type == GameType.APP }
    if (apps.isNotEmpty()) add(ShelfModel("android", "Android games", apps, ArtStyle.COVER, "Android"))
    val pcGames = visible.filter { it.type == GameType.STREAM }
    if (pcGames.isNotEmpty()) add(ShelfModel("pc", "PC games", pcGames, ArtStyle.COVER, "Streamed from your PC"))
    val roms = visible.filter { it.type == GameType.ROM }.groupBy { it.platformId }
    for ((id, name) in platformOrder) {
        val list = roms[id] ?: continue
        add(ShelfModel("platform:$id", name, list, ArtStyle.COVER, "System"))
    }
    val emulators = visible.filter { it.type == GameType.EMULATOR }
    if (emulators.isNotEmpty()) add(ShelfModel("emulators", "Emulators", emulators, ArtStyle.COVER, "Emulator apps"))
}

/**
 * Applies the user's saved order. Rows the user has never placed (e.g. a new collection) slot in
 * right after the row that precedes them in the default order.
 */
fun orderHomeRows(rows: List<ShelfModel>, savedOrder: List<String>): List<ShelfModel> {
    if (savedOrder.isEmpty()) return rows
    val byId = rows.associateBy { it.id }
    val result = savedOrder.mapNotNull { byId[it] }.toMutableList()
    rows.forEachIndexed { i, row ->
        if (row.id in savedOrder) return@forEachIndexed
        val before = rows.getOrNull(i - 1)?.id
        val at = result.indexOfFirst { it.id == before }
        result.add(if (at < 0) minOf(i, result.size) else at + 1, row)
    }
    return result
}

/** The rows the home screen actually shows. */
fun homeShelves(
    games: List<GameEntity>,
    collections: List<CollectionWithCount>,
    memberships: List<CollectionGameEntity>,
    platformOrder: List<Pair<String, String>>,
    settings: Settings,
): List<ShelfModel> =
    orderHomeRows(allHomeRows(games, collections, memberships, platformOrder), settings.homeRowOrder)
        .filterNot { it.id in settings.hiddenHomeRows }
