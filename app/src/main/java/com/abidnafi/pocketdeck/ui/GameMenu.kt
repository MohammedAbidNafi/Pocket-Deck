package com.abidnafi.pocketdeck.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.emulators.EmulatorRegistry
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.TextEntryOverlay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Which game the context menu (X button / long-press) is open for. */
class GameMenuState {
    var gameId by mutableStateOf<Long?>(null)
        private set
    internal var page by mutableStateOf(Page.MAIN)

    fun open(game: GameEntity) {
        page = Page.MAIN
        gameId = game.id
    }

    fun close() {
        gameId = null
    }

    internal enum class Page { MAIN, COLLECTIONS, NEW_COLLECTION, EMULATOR }
}

@Composable
fun rememberGameMenu() = remember { GameMenuState() }

/**
 * Context menu for one game: play, favourite, collections, emulator, artwork, hide/remove.
 * [onClosed] lets the caller put focus back on the tile.
 */
@Composable
fun GameMenuHost(
    state: GameMenuState,
    onClosed: () -> Unit,
    onDetails: ((GameEntity) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val library = container.library
    val scope = rememberCoroutineScope()
    val start = rememberGameStarter()
    val id = state.gameId
    val game by remember(id) { id?.let(library::game) ?: flowOf(null) }.collectAsState(null)
    val collections by library.collections.collectAsState(emptyList())
    val memberOf by remember(id) { id?.let(library::collectionsFor) ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val settings = LocalSettings.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val g = game
        if (uri != null && g != null) scope.launch { library.setCustomArt(g, uri) }
    }

    fun close() {
        state.close()
        // Let the menu animate away (it holds focus until then) before the caller refocuses.
        scope.launch {
            kotlinx.coroutines.delay(200)
            onClosed()
        }
    }

    val g = game
    val visible = id != null && g != null

    val mainItems = if (g == null) emptyList() else buildList {
        add(MenuItem("Play", Icons.Rounded.PlayArrow) { close(); start(g) })
        if (onDetails != null) add(MenuItem("Game info", Icons.Rounded.Info, subtitle = "Play time, sessions and details") { state.close(); onDetails(g) })
        add(
            MenuItem(
                if (g.favorite) "Remove from favourites" else "Add to favourites",
                if (g.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            ) { scope.launch { library.setFavorite(g.id, !g.favorite) } },
        )
        add(MenuItem("Add to collection…", Icons.AutoMirrored.Rounded.PlaylistAdd) { state.page = GameMenuState.Page.COLLECTIONS })
        if (g.type == GameType.ROM) {
            val override = container.emulators[g.emulatorOverride]
            add(
                MenuItem("Emulator", Icons.Rounded.SportsEsports, subtitle = override?.name ?: "System default") {
                    state.page = GameMenuState.Page.EMULATOR
                },
            )
        }
        add(
            MenuItem(
                if (g.customArt != null) "Change artwork…" else "Set custom artwork…",
                Icons.Rounded.Image,
            ) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        if (g.customArt != null) add(MenuItem("Reset artwork", Icons.Rounded.Image) { scope.launch { library.setCustomArt(g, null) } })
        add(
            MenuItem(
                if (g.hidden) "Show in library" else "Hide from library",
                if (g.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            ) { scope.launch { library.setHidden(g.id, !g.hidden) }; close() },
        )
        if (!g.autoDetected && g.type != GameType.ROM) {
            add(MenuItem("Remove from library", Icons.Rounded.DeleteOutline, danger = true) { scope.launch { library.remove(g) }; close() })
        }
    }

    val collectionItems = buildList {
        add(MenuItem("New collection…", Icons.Rounded.Add) { state.page = GameMenuState.Page.NEW_COLLECTION })
        collections.forEach { c ->
            val member = c.collection.id in memberOf
            add(MenuItem(c.collection.name, subtitle = gameCount(c.gameCount), checked = member) {
                if (g != null) scope.launch { library.setInCollection(c.collection.id, g.id, !member) }
            })
        }
    }

    val pid = g?.platformId
    val emulatorItems = if (g == null || pid == null) emptyList() else buildList {
        val platformDefault = container.emulators.defaultFor(pid, settings.defaultEmulators[pid])
        add(MenuItem("System default", subtitle = platformDefault?.name ?: "None installed", checked = g.emulatorOverride == null) {
            scope.launch { library.setEmulatorOverride(g.id, null) }; state.page = GameMenuState.Page.MAIN
        })
        container.emulators.forPlatform(pid).forEach { def ->
            val installed = container.emulators.isInstalled(def)
            add(MenuItem(def.name, subtitle = if (installed) null else "Not installed", enabled = installed, checked = g.emulatorOverride == def.id) {
                scope.launch { library.setEmulatorOverride(g.id, def.id) }; state.page = GameMenuState.Page.MAIN
            })
        }
        val chooser = EmulatorRegistry.systemChooser
        add(MenuItem(chooser.name, checked = g.emulatorOverride == chooser.id) {
            scope.launch { library.setEmulatorOverride(g.id, chooser.id) }; state.page = GameMenuState.Page.MAIN
        })
    }

    val page = state.page
    SideMenu(
        visible = visible && page == GameMenuState.Page.MAIN,
        title = g?.title.orEmpty(),
        subtitle = g?.let { gameSubtitle(it, container.platforms[it.platformId]?.name) },
        items = mainItems,
        onDismiss = ::close,
    )
    SideMenu(
        visible = visible && page == GameMenuState.Page.COLLECTIONS,
        title = "Add to collection",
        items = collectionItems,
        onDismiss = { state.page = GameMenuState.Page.MAIN },
    )
    SideMenu(
        visible = visible && page == GameMenuState.Page.EMULATOR,
        startOnChecked = true,
        title = "Play with",
        items = emulatorItems,
        onDismiss = { state.page = GameMenuState.Page.MAIN },
    )
    TextEntryOverlay(
        visible = visible && page == GameMenuState.Page.NEW_COLLECTION,
        title = "New collection",
        initial = "",
        placeholder = "Collection name",
        onDone = { name ->
            if (name.isNotBlank() && g != null) scope.launch {
                val cid = library.createCollection(name)
                library.setInCollection(cid, g.id, true)
            }
            state.page = GameMenuState.Page.COLLECTIONS
        },
        onDismiss = { state.page = GameMenuState.Page.COLLECTIONS },
    )
}
