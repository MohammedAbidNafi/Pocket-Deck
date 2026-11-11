package com.abidnafi.pocketdeck.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.GameMenuHost
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ConsoleKeyboard
import com.abidnafi.pocketdeck.ui.components.keySizeFor
import com.abidnafi.pocketdeck.ui.components.rememberKeyboardState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import com.abidnafi.pocketdeck.ui.components.GameTile
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MarginBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.TextDisplay
import com.abidnafi.pocketdeck.ui.components.TileMetrics
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.rememberTileMetrics
import com.abidnafi.pocketdeck.ui.components.tryFocus
import com.abidnafi.pocketdeck.ui.rememberGameMenu
import com.abidnafi.pocketdeck.ui.rememberGameStarter
import com.abidnafi.pocketdeck.ui.theme.Palette

/** Search with the on-screen keyboard on the left and live results on the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val games by container.library.games.collectAsState(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(games, query) { search(games, query) }
    val firstKey = remember { FocusRequester() }
    val memory = rememberFocusMemory()
    val menu = rememberGameMenu()
    val start = rememberGameStarter()
    val base = rememberTileMetrics()
    val metrics = remember(base) { TileMetrics(base.coverWidth * 0.8f, base.wideWidth * 0.8f) }
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val keyboard = rememberKeyboardState()

    ControllerHandler { btn ->
        when (btn) {
            Btn.X -> {
                val g = results.firstOrNull { it.id == focusedId }
                if (g != null) menu.open(g) else query = query.dropLast(1)
                true
            }
            Btn.Y -> { query += " "; true }
            Btn.L1 -> { keyboard.toggleShift(); true }
            Btn.SELECT -> { query = ""; firstKey.tryFocus(); true }
            else -> false
        }
    }
    ButtonHints(Hint(Btn.CONFIRM, "Select"), Hint(Btn.X, "Delete / Options"), Hint(Btn.Y, "Space"), Hint(Btn.L1, "Shift"), Hint(Btn.SELECT, "Clear"), Hint(Btn.BACK, "Back"))

    // Fit all five keyboard rows above the hint bar, whatever the screen height.
    val keySize = keySizeFor(LocalConfiguration.current.screenHeightDp.dp - 150.dp, 46.dp)
    val keyboardWidth = keySize * 10 + keySize * 0.17f * 9
    Row(Modifier.fillMaxSize().padding(start = ScreenPadding, top = 20.dp)) {
        Column {
            Row(Modifier.width(keyboardWidth), verticalAlignment = Alignment.CenterVertically) {
                Text("Search", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                Spacer(Modifier.width(16.dp))
                TextDisplay(query, "Type a game name…", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            ConsoleKeyboard(
                state = keyboard,
                onChar = { query += it },
                onBackspace = { query = query.dropLast(1) },
                onSpace = { query += " " },
                onDone = {
                    val first = results.firstOrNull()
                    if (!memory.restore() && first != null) memory.requester("s/${first.id}").tryFocus()
                },
                firstKey = firstKey,
                doneLabel = "Results",
                keySize = keySize,
            )
        }
        Spacer(Modifier.width(32.dp))
        Box(Modifier.weight(1f)) {
            if (results.isEmpty()) {
                Text(
                    if (query.isBlank()) "Start typing to search ${games.size} games." else "No games match \"$query\".",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                    modifier = Modifier.padding(top = 120.dp),
                )
            }
            val margin = with(LocalDensity.current) { 24.dp.toPx() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(metrics.coverWidth),
                    contentPadding = PaddingValues(top = 84.dp, end = ScreenPadding, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(results, key = { it.id }) { game ->
                        GameTile(
                            game, ArtStyle.COVER, metrics,
                            onClick = { start(game) },
                            onFocused = { focusedId = game.id },
                            onMenu = { menu.open(game) },
                            memory = memory,
                            memoryKey = "s/${game.id}",
                        )
                    }
                }
            }
        }
    }
    InitialFocus(null, firstKey)
    GameMenuHost(menu, onClosed = { memory.restore() }, onDetails = { nav.navigate(Routes.details(it.id)) })
}

/** Matches every word of the query against the title, ignoring punctuation; prefix matches rank first. */
private fun search(games: List<GameEntity>, query: String): List<GameEntity> {
    val words = query.lowercase().split(' ').map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return games.asSequence()
        .filterNot { it.hidden }
        .map { it to it.title.lowercase().filter { c -> c.isLetterOrDigit() || c == ' ' } }
        .filter { (_, t) -> words.all { it in t.replace(" ", "") } }
        .sortedWith(compareByDescending<Pair<GameEntity, String>> { (_, t) -> t.startsWith(words.first()) }.thenBy { it.first.sortTitle })
        .map { it.first }
        .take(60)
        .toList()
}
