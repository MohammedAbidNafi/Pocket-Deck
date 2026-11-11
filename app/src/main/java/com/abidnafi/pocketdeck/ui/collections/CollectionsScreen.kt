package com.abidnafi.pocketdeck.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.db.CollectionWithCount
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.GameMenuHost
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.EmptyState
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.GameArt
import com.abidnafi.pocketdeck.ui.components.GameTile
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MarginBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.TextEntryOverlay
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.rememberTileMetrics
import com.abidnafi.pocketdeck.ui.home.Backdrop
import com.abidnafi.pocketdeck.ui.rememberGameMenu
import com.abidnafi.pocketdeck.ui.rememberGameStarter
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionsScreen(nav: NavController, topInset: Dp) {
    val library = LocalAppContainer.current.library
    val collections by library.collections.collectAsState(null)
    val memberships by library.memberships.collectAsState(emptyList())
    val games by library.games.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val memory = rememberFocusMemory()
    val metrics = rememberTileMetrics()
    var creating by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<CollectionWithCount?>(null) }
    var menuFor by remember { mutableStateOf<CollectionWithCount?>(null) }
    var renaming by remember { mutableStateOf<CollectionWithCount?>(null) }
    var deleting by remember { mutableStateOf<CollectionWithCount?>(null) }
    val list = collections ?: return

    val gamesById = remember(games) { games.associateBy { it.id } }
    val covers = remember(memberships, gamesById) {
        memberships.groupBy { it.collectionId }.mapValues { (_, m) -> m.mapNotNull { gamesById[it.gameId] }.take(4) }
    }

    ControllerHandler { btn ->
        if (btn == Btn.X && focused != null) { menuFor = focused; true } else false
    }
    ButtonHints(Hint(Btn.CONFIRM, "Open"), Hint(Btn.X, "Options"), Hint(Btn.START, "Menu"))

    val cardWidth = metrics.wideWidth
    Box(Modifier.fillMaxSize()) {
        val margin = with(LocalDensity.current) { 28.dp.toPx() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
            LazyVerticalGrid(
                columns = GridCells.FixedSize(cardWidth),
                modifier = Modifier.fillMaxSize().padding(top = topInset),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 20.dp, bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "new") {
                    NewCollectionCard(cardWidth, metrics.wideHeight, memory.requester("new")) { creating = true }
                }
                items(list, key = { it.collection.id }) { c ->
                    CollectionCard(
                        c, covers[c.collection.id].orEmpty(), cardWidth, metrics.wideHeight,
                        onOpen = { nav.navigate(Routes.collection(c.collection.id)) },
                        onFocus = { focused = c; memory.lastKey = "c/${c.collection.id}" },
                        onMenu = { menuFor = c },
                        focusRequester = memory.requester("c/${c.collection.id}"),
                    )
                }
            }
        }
        if (list.isEmpty()) {
            Text(
                "Collections let you group games your way — \"Couch co-op\", \"RPGs\", \"Finish this year\"…",
                style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = ScreenPadding, bottom = 72.dp),
            )
        }
        InitialFocus(memory, memory.requester(list.firstOrNull()?.let { "c/${it.collection.id}" } ?: "new"))

        val menuTarget = menuFor
        SideMenu(
            visible = menuTarget != null,
            title = menuTarget?.collection?.name.orEmpty(),
            subtitle = menuTarget?.let { "${it.gameCount} ${if (it.gameCount == 1) "game" else "games"}" },
            items = listOf(
                MenuItem("Add or remove games", Icons.Rounded.Add) {
                    menuFor = null
                    menuTarget?.let { nav.navigate(Routes.collectionAdd(it.collection.id)) }
                },
                MenuItem("Rename", Icons.Rounded.Edit) { renaming = menuTarget; menuFor = null },
                MenuItem("Delete collection", Icons.Rounded.DeleteOutline, danger = true) { deleting = menuTarget; menuFor = null },
            ),
            onDismiss = { menuFor = null; memory.restoreSoon(scope) },
        )
        TextEntryOverlay(
            visible = creating,
            title = "New collection",
            initial = "",
            placeholder = "Collection name",
            onDone = { name ->
                creating = false
                if (name.isNotBlank()) {
                    scope.launch {
                        val id = library.createCollection(name)
                        nav.navigate(Routes.collectionAdd(id, justCreated = true))
                    }
                } else {
                    memory.restoreSoon(scope)
                }
            },
            onDismiss = { creating = false; memory.restoreSoon(scope) },
        )
        TextEntryOverlay(
            visible = renaming != null,
            title = "Rename collection",
            initial = renaming?.collection?.name.orEmpty(),
            onDone = { name ->
                val c = renaming
                renaming = null
                if (c != null && name.isNotBlank()) scope.launch { library.renameCollection(c.collection.id, name) }
                memory.restoreSoon(scope)
            },
            onDismiss = { renaming = null; memory.restoreSoon(scope) },
        )
        DialogOverlay(
            visible = deleting != null,
            title = "Delete \"${deleting?.collection?.name.orEmpty()}\"?",
            body = "The games stay in your library; only the collection is removed.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            danger = true,
            onConfirm = {
                deleting?.let { c -> scope.launch { library.deleteCollection(c.collection.id) } }
                deleting = null
                memory.lastKey = "new"
                memory.restoreSoon(scope)
            },
            onDismiss = { deleting = null; memory.restoreSoon(scope) },
        )
    }
}

@Composable
private fun NewCollectionCard(width: Dp, height: Dp, focusRequester: androidx.compose.ui.focus.FocusRequester, onClick: () -> Unit) {
    val accent = LocalAccent.current
    FocusCard(onClick = onClick, focusRequester = focusRequester, modifier = Modifier.size(width, height)) { focused ->
        Column(
            Modifier.fillMaxSize().background(if (focused) accent.copy(alpha = 0.3f) else Palette.Surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Palette.Text, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(6.dp))
            Text("New collection", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        }
    }
}

@Composable
private fun CollectionCard(
    c: CollectionWithCount,
    covers: List<GameEntity>,
    width: Dp,
    height: Dp,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
    onMenu: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
) {
    FocusCard(
        onClick = onOpen,
        onLongClick = onMenu,
        onFocusChange = { if (it) onFocus() },
        focusRequester = focusRequester,
        modifier = Modifier.size(width, height),
    ) {
        Row(Modifier.fillMaxSize().background(Palette.SurfaceHigh)) {
            if (covers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CollectionsBookmark, null, tint = Palette.TextFaint, modifier = Modifier.size(48.dp))
                }
            } else {
                covers.forEach { g -> GameArt(g, ArtStyle.COVER, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(c.collection.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${c.gameCount} ${if (c.gameCount == 1) "game" else "games"}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/** Grid of the games in one collection. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionDetailScreen(collectionId: Long, nav: NavController) {
    val library = LocalAppContainer.current.library
    val collection by remember(collectionId) { library.collection(collectionId) }.collectAsState(null)
    val memberships by library.memberships.collectAsState(emptyList())
    val all by library.games.collectAsState(emptyList())
    val games = remember(memberships, all) {
        val ids = memberships.filter { it.collectionId == collectionId }.map { it.gameId }.toSet()
        all.filter { it.id in ids }
    }
    val metrics = rememberTileMetrics()
    val memory = rememberFocusMemory()
    val menu = rememberGameMenu()
    val start = rememberGameStarter()
    val scope = rememberCoroutineScope()
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val focused = games.firstOrNull { it.id == focusedId }

    val addButton = remember { androidx.compose.ui.focus.FocusRequester() }
    fun addGames() = nav.navigate(Routes.collectionAdd(collectionId))

    BackHandler { nav.popBackStack() }
    ControllerHandler { btn ->
        if (btn == Btn.START) { addGames(); return@ControllerHandler true }
        val g = focused ?: return@ControllerHandler false
        when (btn) {
            Btn.X -> { menu.open(g); true }
            Btn.Y -> { scope.launch { library.setFavorite(g.id, !g.favorite) }; true }
            else -> false
        }
    }
    ButtonHints(Hint(Btn.CONFIRM, "Play"), Hint(Btn.X, "Options"), Hint(Btn.Y, "Favourite"), Hint(Btn.START, "Add games"), Hint(Btn.BACK, "Back"))

    Box(Modifier.fillMaxSize()) {
        Backdrop(focused ?: games.firstOrNull())
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            Row(Modifier.padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(collection?.name.orEmpty(), style = MaterialTheme.typography.displayMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${games.size} ${if (games.size == 1) "game" else "games"}", style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                }
                com.abidnafi.pocketdeck.ui.components.ConsoleButton("Add games", ::addGames, icon = Icons.Rounded.Add, primary = games.isEmpty(), focusRequester = addButton)
            }
            if (games.isEmpty()) {
                EmptyState(
                    Icons.Rounded.CollectionsBookmark, "No games yet",
                    "Use Add games above, or press X on any game and choose \"Add to collection\".",
                    Modifier.align(Alignment.CenterHorizontally),
                )
                InitialFocus(null, addButton)
                return@Column
            }
            val margin = with(LocalDensity.current) { 28.dp.toPx() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(metrics.coverWidth),
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(games, key = { it.id }) { game ->
                        GameTile(
                            game, ArtStyle.COVER, metrics,
                            onClick = { start(game) },
                            onFocused = { focusedId = game.id },
                            onMenu = { menu.open(game) },
                            memory = memory,
                            memoryKey = "cg/${game.id}",
                        )
                    }
                }
            }
            InitialFocus(memory, memory.requester("cg/${games.first().id}"))
        }
        GameMenuHost(menu, onClosed = { memory.restoreSoon(scope) }, onDetails = { nav.navigate(Routes.details(it.id)) })
    }
}
