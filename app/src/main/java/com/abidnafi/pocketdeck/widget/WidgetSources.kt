package com.abidnafi.pocketdeck.widget

import com.abidnafi.pocketdeck.AppContainer
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.ui.home.allHomeRows
import kotlinx.coroutines.flow.first

/** Something a home-screen widget can show: a list of games under a title. */
data class WidgetSource(val id: String, val title: String, val subtitle: String, val games: List<GameEntity>)

/** Default for a newly placed widget. */
const val DEFAULT_WIDGET_SOURCE = "recent"

/**
 * Everything a widget can show, in picker order: last played, recently added, favourites,
 * all games, every collection, then the system / Android rows from Home. Uses the same rows as
 * the Home screen, so a widget always matches what the app shows.
 */
suspend fun loadWidgetSources(container: AppContainer): List<WidgetSource> {
    val games = container.library.games.first()
    val collections = container.library.collections.first()
    val memberships = container.library.memberships.first()
    val rows = allHomeRows(games, collections, memberships, container.platforms.platforms.map { it.id to it.name })
    val byId = rows.associateBy { it.id }
    val visible = games.filterNot { it.hidden }

    val list = mutableListOf<WidgetSource>()
    fun add(id: String, title: String, subtitle: String, list2: List<GameEntity> = byId[id]?.games.orEmpty()) {
        list += WidgetSource(id, title, subtitle, list2)
    }
    add("recent", "Last played", "The games you played most recently")
    add("added", "Recently added", "Newest games in your library")
    add("favorites", "Favourites", "Games you starred")
    add("all", "All games", "Your whole library, A to Z", visible)
    collections.forEach { c ->
        val id = "collection:${c.collection.id}"
        add(id, c.collection.name, "Collection")
    }
    val taken = list.map { it.id }.toSet()
    rows.filter { it.id !in taken }.forEach { add(it.id, it.title, it.kind, it.games) }
    return list
}
