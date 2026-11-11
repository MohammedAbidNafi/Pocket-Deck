package com.abidnafi.pocketdeck.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.data.TileShape
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.FilterChip
import com.abidnafi.pocketdeck.ui.components.GameTile
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MarginBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.rememberTileMetrics
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Pick the games in a collection: every game in the library with a tick on the ones already in
 * it. A adds / removes, Y switches between all games and only the ticked ones, Start (or the
 * Done button) finishes. Opens straight after creating a collection, and from a collection's page.
 * [justCreated]: Done goes on to the new collection's page instead of back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionAddGamesScreen(collectionId: Long, justCreated: Boolean, nav: NavController) {
    val library = LocalAppContainer.current.library
    val platforms = LocalAppContainer.current.platforms
    val collection by remember(collectionId) { library.collection(collectionId) }.collectAsState(null)
    val memberships by library.memberships.collectAsState(emptyList())
    val all by library.games.collectAsState(emptyList())
    val selected = remember(memberships) { memberships.filter { it.collectionId == collectionId }.map { it.gameId }.toSet() }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    val games = remember(all, selected, onlySelected) {
        all.filter { !it.hidden && it.type != GameType.EMULATOR && (!onlySelected || it.id in selected) }
    }
    val scope = rememberCoroutineScope()
    val metrics = rememberTileMetrics()
    val memory = rememberFocusMemory()
    val done = remember { FocusRequester() }
    var focusedId by remember { mutableStateOf<Long?>(null) }

    fun toggle(id: Long) {
        scope.launch { library.setInCollection(collectionId, id, id !in selected) }
    }

    fun finish() {
        if (justCreated) {
            nav.navigate(Routes.collection(collectionId)) { popUpTo(Routes.COLLECTION_ADD) { inclusive = true } }
        } else {
            nav.popBackStack()
        }
    }

    BackHandler(onBack = ::finish)
    ControllerHandler { btn ->
        when (btn) {
            Btn.Y -> { onlySelected = !onlySelected; true }
            Btn.START -> { finish(); true }
            else -> false
        }
    }
    val focusedIn = focusedId?.let { it in selected }
    ButtonHints(
        Hint(Btn.CONFIRM, if (focusedIn == true) "Remove" else "Add"),
        Hint(Btn.Y, if (onlySelected) "Show all games" else "Show added"),
        Hint(Btn.START, "Done"),
    )

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Row(Modifier.padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Add games to ${collection?.name.orEmpty()}",
                    style = MaterialTheme.typography.headlineMedium, color = Palette.Text, maxLines = 1,
                )
                Text(
                    "${selected.size} ${if (selected.size == 1) "game" else "games"} added · press A on a game to add or remove it",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                )
            }
            Spacer(Modifier.width(16.dp))
            FilterChip("All games", !onlySelected, { onlySelected = false })
            Spacer(Modifier.width(8.dp))
            FilterChip("Added", onlySelected, { onlySelected = true }, count = selected.size)
            Spacer(Modifier.width(16.dp))
            ConsoleButton("Done", ::finish, icon = Icons.Rounded.Check, primary = true, focusRequester = done)
        }

        val margin = with(LocalDensity.current) { 28.dp.toPx() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
            LazyVerticalGrid(
                columns = GridCells.FixedSize(metrics.coverWidth),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (games.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (onlySelected) "Nothing added yet. Press Y to see all your games." else "Your library is empty.",
                            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, modifier = Modifier.padding(top = 40.dp),
                        )
                    }
                }
                items(games, key = { it.id }) { game ->
                    Box {
                        GameTile(
                            game, ArtStyle.COVER, metrics,
                            onClick = { toggle(game.id) },
                            onFocused = { focusedId = game.id },
                            onMenu = { toggle(game.id) },
                            memory = memory,
                            memoryKey = "add/${game.id}",
                        )
                        // Round tiles: the badge sits in the empty corner beside the circle.
                        val round = LocalSettings.current.tileShape == TileShape.ROUND
                        SelectionBadge(
                            game.id in selected,
                            Modifier.align(if (round) Alignment.TopEnd else Alignment.TopStart).padding(if (round) 0.dp else 8.dp),
                        )
                    }
                }
            }
        }
    }
    // A new (empty) collection starts on the first game; an existing one on Done.
    // Keyed on the library having loaded, so a new collection lands on the first game, not Done.
    val firstGame = games.firstOrNull()
    InitialFocus(memory, if (justCreated && firstGame != null) memory.requester("add/${firstGame.id}") else done, key = firstGame != null)
}

/** Tick in the tile's corner: filled in the accent colour when the game is in the collection. */
@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier
            .size(30.dp)
            .background(if (selected) accent else Color.Black.copy(alpha = 0.45f), CircleShape)
            .border(2.dp, Color.White.copy(alpha = if (selected) 1f else 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, "Added", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
