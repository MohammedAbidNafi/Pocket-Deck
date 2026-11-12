package com.abidnafi.pocketdeck.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.GameMenuHost
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ButtonGlyph
import com.abidnafi.pocketdeck.ui.components.EmptyState
import com.abidnafi.pocketdeck.ui.components.FilterChip
import com.abidnafi.pocketdeck.ui.components.GameTile
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MarginBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.rememberTileMetrics
import com.abidnafi.pocketdeck.ui.rememberGameMenu
import com.abidnafi.pocketdeck.ui.rememberGameStarter
import com.abidnafi.pocketdeck.ui.theme.Palette
import com.abidnafi.pocketdeck.data.GameSort
import kotlinx.coroutines.launch

private data class LibraryFilter(val id: String, val label: String, val matches: (GameEntity) -> Boolean)

/** Everything in one grid with platform / source filters. L2/R2 cycle filters, Select-chip changes sort. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(nav: NavController, topInset: Dp) {
    val container = LocalAppContainer.current
    val all by container.library.games.collectAsState(null)
    val games = all ?: return
    val metrics = rememberTileMetrics()
    val memory = rememberFocusMemory()
    val menu = rememberGameMenu()
    val start = rememberGameStarter()
    val scope = rememberCoroutineScope()

    val filters = remember(games) {
        val visible = games.filterNot { it.hidden }
        buildList {
            add(LibraryFilter("all", "All") { !it.hidden })
            if (visible.any { it.favorite }) add(LibraryFilter("fav", "Favourites") { !it.hidden && it.favorite })
            if (visible.any { it.type == GameType.APP }) add(LibraryFilter("android", "Android") { !it.hidden && it.type == GameType.APP })
            if (visible.any { it.type == GameType.STREAM }) add(LibraryFilter("pc", "PC") { !it.hidden && it.type == GameType.STREAM })
            container.platforms.platforms.forEach { p ->
                if (visible.any { it.platformId == p.id }) add(LibraryFilter(p.id, p.shortName) { !it.hidden && it.platformId == p.id })
            }
            if (visible.any { it.type == GameType.EMULATOR }) add(LibraryFilter("emu", "Emulators") { !it.hidden && it.type == GameType.EMULATOR })
            if (games.any { it.hidden }) add(LibraryFilter("hidden", "Hidden") { it.hidden })
        }
    }
    var filterId by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf(GameSort.NAME) }
    val filter = filters.firstOrNull { it.id == filterId } ?: filters.first()
    val shown = remember(games, filter, sort) { sort.apply(games.filter(filter.matches)) }
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val focused = shown.firstOrNull { it.id == focusedId }

    fun cycleFilter(step: Int) {
        val i = filters.indexOf(filter)
        filterId = filters[(i + step + filters.size) % filters.size].id
    }

    ControllerHandler { btn ->
        when (btn) {
            Btn.L2 -> { cycleFilter(-1); true }
            Btn.R2 -> { cycleFilter(1); true }
            Btn.X -> { focused?.let(menu::open); focused != null }
            Btn.Y -> { focused?.let { g -> scope.launch { container.library.setFavorite(g.id, !g.favorite) } }; focused != null }
            else -> false
        }
    }
    ButtonHints(
        Hint(Btn.CONFIRM, "Play"),
        Hint(Btn.X, "Options"),
        Hint(Btn.Y, "Favourite"),
        Hint(Btn.L2, "Prev filter"),
        Hint(Btn.R2, "Next filter"),
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = topInset)) {
            Row(Modifier.fillMaxWidth().padding(start = ScreenPadding - 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonGlyph(Btn.L2, 22.dp)
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filters, key = { it.id }) { f ->
                        FilterChip(
                            label = f.label,
                            selected = f.id == filter.id,
                            count = games.count(f.matches),
                            onClick = { filterId = f.id },
                        )
                    }
                    item(key = "sort") {
                        FilterChip(label = "Sort: ${sort.label}", selected = false, onClick = {
                            sort = sort.next()
                        })
                    }
                }
                ButtonGlyph(Btn.R2, 22.dp)
                Spacer(Modifier.width(ScreenPadding - 12.dp))
            }

            if (shown.isEmpty()) {
                EmptyState(Icons.Rounded.SearchOff, "Nothing here", "No games match this filter.", Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            val margin = with(LocalDensity.current) { 28.dp.toPx() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(metrics.coverWidth),
                    state = rememberLazyGridState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shown, key = { it.id }) { game ->
                        GameTile(
                            game = game,
                            style = ArtStyle.COVER,
                            metrics = metrics,
                            onClick = { start(game) },
                            onFocused = { focusedId = game.id },
                            onMenu = { menu.open(game) },
                            memory = memory,
                            memoryKey = "lib/${game.id}",
                        )
                    }
                }
            }
            InitialFocus(memory, memory.requester("lib/${shown.first().id}"), key = filter.id)
        }
        Text(
            com.abidnafi.pocketdeck.ui.gameCount(shown.size),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextDim,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = ScreenPadding, bottom = 16.dp),
        )
        GameMenuHost(menu, onClosed = { memory.restore() }, onDetails = { nav.navigate(Routes.details(it.id)) })
    }
}
