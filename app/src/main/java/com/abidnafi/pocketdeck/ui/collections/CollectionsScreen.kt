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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.AutoCollection
import com.abidnafi.pocketdeck.data.AutoCollectionHost
import com.abidnafi.pocketdeck.data.AutoCollections
import com.abidnafi.pocketdeck.data.CollectionSort
import com.abidnafi.pocketdeck.data.db.CollectionWithCount
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.GameMenuHost
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalMessages
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.EmptyState
import com.abidnafi.pocketdeck.ui.components.FilterChip
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

/** Auto collections for the current library (empty when the user turned them off). */
@Composable
fun rememberAutoCollections(includeHidden: Boolean = false): List<AutoCollection> {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val games by container.library.games.collectAsState(emptyList())
    val hosts by container.hosts.hosts.collectAsState(emptyList())
    val platforms = remember { container.platforms.platforms.map { it.id to it.name } }
    return remember(games, hosts, settings.autoCollections, settings.hiddenAutoCollections, includeHidden) {
        if (!settings.autoCollections) {
            emptyList()
        } else {
            AutoCollections.build(games, platforms, hosts.filter { it.paired }.map { AutoCollectionHost(it.id, it.name) })
                .filter { includeHidden || it.id !in settings.hiddenAutoCollections }
        }
    }
}

/** Collections tab: a sort chip, the user's own collections, then the auto collections. Y cycles the sort. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionsScreen(nav: NavController, topInset: Dp) {
    val container = LocalAppContainer.current
    val library = container.library
    val settings = LocalSettings.current
    val collections by library.collections.collectAsState(null)
    val memberships by library.memberships.collectAsState(emptyList())
    val games by library.games.collectAsState(emptyList())
    val autos = rememberAutoCollections()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val memory = rememberFocusMemory()
    val metrics = rememberTileMetrics()
    var creating by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<CollectionWithCount?>(null) }
    var focusedAuto by remember { mutableStateOf<AutoCollection?>(null) }
    var menuFor by remember { mutableStateOf<CollectionWithCount?>(null) }
    var autoMenuFor by remember { mutableStateOf<AutoCollection?>(null) }
    var renaming by remember { mutableStateOf<CollectionWithCount?>(null) }
    var deleting by remember { mutableStateOf<CollectionWithCount?>(null) }
    val list = collections ?: return
    val sort = settings.collectionSort

    val gamesById = remember(games) { games.associateBy { it.id } }
    // Memberships arrive newest first, so covers show the latest additions.
    val members = remember(memberships, gamesById) {
        memberships.groupBy { it.collectionId }.mapValues { (_, m) -> m.mapNotNull { gamesById[it.gameId] } }
    }
    val sortedUser = remember(list, members, sort) {
        when (sort) {
            CollectionSort.NAME -> list.sortedBy { it.collection.name.lowercase() }
            CollectionSort.RECENT -> list.sortedByDescending { c ->
                members[c.collection.id].orEmpty().maxOfOrNull { it.lastPlayedAt ?: 0L } ?: 0L
            }
            CollectionSort.SIZE -> list.sortedByDescending { it.gameCount }
            CollectionSort.NEWEST -> list.sortedByDescending { it.collection.createdAt }
        }
    }
    val sortedAuto = remember(autos, sort) {
        when (sort) {
            CollectionSort.NAME -> autos.sortedBy { it.name.lowercase() }
            CollectionSort.RECENT -> autos.sortedByDescending { a -> a.games.maxOfOrNull { it.lastPlayedAt ?: 0L } ?: 0L }
            CollectionSort.SIZE -> autos.sortedByDescending { it.games.size }
            CollectionSort.NEWEST -> autos // Built-in order: favourites, Android, PC, each system…
        }
    }

    fun cycleSort() {
        scope.launch { container.settings.setCollectionSort(sort.next()) }
    }

    ControllerHandler { btn ->
        when {
            btn == Btn.Y -> { cycleSort(); true }
            btn == Btn.X && focused != null -> { menuFor = focused; true }
            btn == Btn.X && focusedAuto != null -> { autoMenuFor = focusedAuto; true }
            else -> false
        }
    }
    ButtonHints(Hint(Btn.CONFIRM, "Open"), Hint(Btn.X, "Options"), Hint(Btn.Y, "Sort"), Hint(Btn.START, "Menu"))

    val cardWidth = metrics.wideWidth
    Box(Modifier.fillMaxSize()) {
        val margin = with(LocalDensity.current) { 28.dp.toPx() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
            LazyVerticalGrid(
                columns = GridCells.FixedSize(cardWidth),
                modifier = Modifier.fillMaxSize().padding(top = topInset),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 14.dp, bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(label = "Sort: ${sort.label}", selected = false, onClick = ::cycleSort, focusRequester = memory.requester("sort"))
                    }
                }
                item(key = "new") {
                    NewCollectionCard(cardWidth, metrics.wideHeight, memory.requester("new")) { creating = true }
                }
                items(sortedUser, key = { it.collection.id }) { c ->
                    CollectionCard(
                        c.collection.name, c.gameCount, members[c.collection.id].orEmpty().take(4), cardWidth, metrics.wideHeight,
                        onOpen = { nav.navigate(Routes.collection(c.collection.id)) },
                        onFocus = { focused = c; focusedAuto = null; memory.lastKey = "c/${c.collection.id}" },
                        onMenu = { menuFor = c },
                        focusRequester = memory.requester("c/${c.collection.id}"),
                    )
                }
                if (sortedAuto.isNotEmpty()) {
                    item(key = "auto-header", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Auto collections", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                            Text("Kept up to date for you from your library", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        }
                    }
                    items(sortedAuto, key = { it.id }) { a ->
                        CollectionCard(
                            a.name, a.games.size, a.games.take(4), cardWidth, metrics.wideHeight,
                            onOpen = { nav.navigate(Routes.autoCollection(a.id)) },
                            onFocus = { focusedAuto = a; focused = null; memory.lastKey = a.id },
                            onMenu = { autoMenuFor = a },
                            focusRequester = memory.requester(a.id),
                            badge = "AUTO",
                        )
                    }
                }
            }
        }
        if (list.isEmpty() && sortedAuto.isEmpty()) {
            Text(
                "Collections let you group games your way — \"Couch co-op\", \"RPGs\", \"Finish this year\"…",
                style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = ScreenPadding, bottom = 72.dp),
            )
        }
        val firstKey = sortedUser.firstOrNull()?.let { "c/${it.collection.id}" } ?: sortedAuto.firstOrNull()?.id ?: "new"
        InitialFocus(memory, memory.requester(firstKey))

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
        val autoTarget = autoMenuFor
        SideMenu(
            visible = autoTarget != null,
            title = autoTarget?.name.orEmpty(),
            subtitle = autoTarget?.let { "Auto collection · ${it.games.size} ${if (it.games.size == 1) "game" else "games"}" },
            items = listOf(
                MenuItem("Save as my collection", Icons.Rounded.ContentCopy, subtitle = "An editable copy you can add to or trim") {
                    autoMenuFor = null
                    val a = autoTarget ?: return@MenuItem
                    scope.launch {
                        val id = library.createCollection(a.name)
                        a.games.forEach { library.setInCollection(id, it.id, true) }
                        messages.show(
                            "Saved \"${a.name}\"",
                            "It's now one of your collections, so you can add or remove games. Unlike the auto collection, it won't update by itself.",
                        )
                    }
                },
                MenuItem("Hide this collection", Icons.Rounded.VisibilityOff, subtitle = "Bring it back in Settings → Library") {
                    autoMenuFor = null
                    autoTarget?.let { a -> scope.launch { container.settings.setAutoCollectionHidden(a.id, true) } }
                    memory.lastKey = "new"
                    memory.restoreSoon(scope)
                },
                MenuItem("Turn off auto collections", Icons.Rounded.AutoAwesome, danger = true, subtitle = "Turn them back on in Settings → Library") {
                    autoMenuFor = null
                    scope.launch { container.settings.setAutoCollections(false) }
                    memory.lastKey = "new"
                    memory.restoreSoon(scope)
                },
            ),
            onDismiss = { autoMenuFor = null; memory.restoreSoon(scope) },
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
private fun NewCollectionCard(width: Dp, height: Dp, focusRequester: FocusRequester, onClick: () -> Unit) {
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
    name: String,
    count: Int,
    covers: List<GameEntity>,
    width: Dp,
    height: Dp,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
    onMenu: () -> Unit,
    focusRequester: FocusRequester,
    badge: String? = null,
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
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count ${if (count == 1) "game" else "games"}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/** Grid of the games in one of the user's collections. */
@Composable
fun CollectionDetailScreen(collectionId: Long, nav: NavController) {
    val library = LocalAppContainer.current.library
    val collection by remember(collectionId) { library.collection(collectionId) }.collectAsState(null)
    val memberships by library.memberships.collectAsState(emptyList())
    val all by library.games.collectAsState(emptyList())
    // When each game joined this collection, for "Recently added".
    val joined = remember(memberships, collectionId) {
        memberships.filter { it.collectionId == collectionId }.associate { it.gameId to it.addedAt }
    }
    val games = remember(joined, all) { all.filter { it.id in joined } }
    CollectionGames(
        title = collection?.name.orEmpty(),
        games = games,
        sortKey = "c:$collectionId",
        addedAt = { joined[it.id] ?: it.addedAt },
        nav = nav,
        onAddGames = { nav.navigate(Routes.collectionAdd(collectionId)) },
    )
}

/** Grid of the games in an auto collection. Read-only: it fills itself. */
@Composable
fun AutoCollectionDetailScreen(autoId: String, nav: NavController) {
    val auto = rememberAutoCollections(includeHidden = true).firstOrNull { it.id == autoId }
    CollectionGames(
        title = auto?.name.orEmpty(),
        subtitle = auto?.subtitle,
        games = auto?.games.orEmpty(),
        sortKey = autoId,
        addedAt = { it.addedAt },
        nav = nav,
        onAddGames = null,
    )
}

/** Shared collection page: header with sort chip, then the games. Select cycles the sort. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionGames(
    title: String,
    games: List<GameEntity>,
    sortKey: String,
    addedAt: (GameEntity) -> Long,
    nav: NavController,
    onAddGames: (() -> Unit)?,
    subtitle: String? = null,
) {
    val container = LocalAppContainer.current
    val library = container.library
    val settings = LocalSettings.current
    val sort = settings.gameSortFor(sortKey)
    val shown = remember(games, sort) { sort.apply(games, addedAt) }
    val metrics = rememberTileMetrics()
    val memory = rememberFocusMemory()
    val menu = rememberGameMenu()
    val start = rememberGameStarter()
    val scope = rememberCoroutineScope()
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val focused = shown.firstOrNull { it.id == focusedId }
    val addButton = remember { FocusRequester() }

    fun cycleSort() {
        scope.launch { container.settings.setCollectionGameSort(sortKey, sort.next()) }
    }

    BackHandler { nav.popBackStack() }
    ControllerHandler { btn ->
        if (btn == Btn.START && onAddGames != null) { onAddGames(); return@ControllerHandler true }
        if (btn == Btn.SELECT && games.size > 1) { cycleSort(); return@ControllerHandler true }
        val g = focused ?: return@ControllerHandler false
        when (btn) {
            Btn.X -> { menu.open(g); true }
            Btn.Y -> { scope.launch { library.setFavorite(g.id, !g.favorite) }; true }
            else -> false
        }
    }
    val hints = buildList {
        add(Hint(Btn.CONFIRM, "Play"))
        add(Hint(Btn.X, "Options"))
        add(Hint(Btn.Y, "Favourite"))
        if (games.size > 1) add(Hint(Btn.SELECT, "Sort"))
        if (onAddGames != null) add(Hint(Btn.START, "Add games"))
        add(Hint(Btn.BACK, "Back"))
    }
    ButtonHints(*hints.toTypedArray())

    Box(Modifier.fillMaxSize()) {
        Backdrop(focused ?: shown.firstOrNull())
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            Row(Modifier.padding(horizontal = ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.displayMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull("${games.size} ${if (games.size == 1) "game" else "games"}", subtitle).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                    )
                }
                if (games.size > 1) {
                    FilterChip(label = "Sort: ${sort.label}", selected = false, onClick = ::cycleSort)
                    Spacer(Modifier.width(12.dp))
                }
                if (onAddGames != null) {
                    ConsoleButton("Add games", onAddGames, icon = Icons.Rounded.Add, primary = games.isEmpty(), focusRequester = addButton)
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Rounded.CollectionsBookmark, "No games yet",
                    if (onAddGames != null) "Use Add games above, or press X on any game and choose \"Add to collection\"."
                    else "Games show up here as soon as your library has some that fit.",
                    Modifier.align(Alignment.CenterHorizontally),
                )
                if (onAddGames != null) InitialFocus(null, addButton)
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
                    items(shown, key = { it.id }) { game ->
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
            InitialFocus(memory, memory.requester("cg/${shown.first().id}"))
        }
        GameMenuHost(menu, onClosed = { memory.restoreSoon(scope) }, onDetails = { nav.navigate(Routes.details(it.id)) })
    }
}
