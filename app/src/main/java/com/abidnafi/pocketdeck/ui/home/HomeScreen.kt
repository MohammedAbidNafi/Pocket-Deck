package com.abidnafi.pocketdeck.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.EmptyState
import com.abidnafi.pocketdeck.ui.components.GameArt
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.PivotBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.Shelf
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.rememberTileMetrics
import com.abidnafi.pocketdeck.ui.formatLastPlayed
import com.abidnafi.pocketdeck.ui.formatPlaytime
import com.abidnafi.pocketdeck.ui.rememberGameMenu
import com.abidnafi.pocketdeck.ui.rememberGameStarter
import com.abidnafi.pocketdeck.ui.sourceLabel
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: NavController, topInset: Dp) {
    val container = LocalAppContainer.current
    val all by container.library.games.collectAsState(null)
    val games = all?.filterNot { it.hidden }
    val collections by container.library.collections.collectAsState(emptyList())
    val memberships by container.library.memberships.collectAsState(emptyList())
    val settings = LocalSettings.current
    val shelves = remember(all, collections, memberships, settings.homeRowOrder, settings.hiddenHomeRows) {
        all?.let { homeShelves(it, collections, memberships, container.platforms.platforms.map { p -> p.id to p.name }, settings) }
    }
    val memory = rememberFocusMemory()
    val menu = rememberGameMenu()
    val start = rememberGameStarter()
    val scope = rememberCoroutineScope()
    val metrics = rememberTileMetrics()

    var focusedId by remember { mutableStateOf<Long?>(null) }
    val focused = games?.firstOrNull { it.id == focusedId }
    // Hero/backdrop follow focus with a short delay so fast scrolling doesn't strobe images.
    var heroId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(focusedId, shelves) {
        if (heroId != null) delay(140)
        heroId = focusedId ?: shelves?.firstOrNull()?.games?.firstOrNull()?.id
    }
    val hero = games?.firstOrNull { it.id == heroId }
    // The full-screen background is slower still: it only changes once you've settled on a
    // game for a moment, so moving along a shelf doesn't make the whole screen flicker.
    var backdropId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(heroId) {
        if (backdropId != null) delay(420)
        backdropId = heroId
    }
    val backdrop = games?.firstOrNull { it.id == backdropId }

    ControllerHandler { btn ->
        val g = focused ?: return@ControllerHandler false
        when (btn) {
            Btn.X -> { menu.open(g); true }
            Btn.Y -> { scope.launch { container.library.setFavorite(g.id, !g.favorite) }; true }
            else -> false
        }
    }
    ButtonHints(
        Hint(Btn.CONFIRM, "Play"),
        Hint(Btn.X, "Options"),
        Hint(Btn.Y, if (focused?.favorite == true) "Unfavourite" else "Favourite"),
        Hint(Btn.START, "Menu"),
        Hint(Btn.SELECT, "Search"),
    )

    Box(Modifier.fillMaxSize()) {
        Backdrop(backdrop)
        if (shelves == null) return@Box
        if (shelves.isEmpty()) {
            EmptyLibrary(nav, Modifier.align(Alignment.Center))
            return@Box
        }
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val compact = screenHeight < 480.dp
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topInset))
            HeroInfo(hero, compact, Modifier.padding(horizontal = ScreenPadding))
            // Keep the focused shelf at the top of the list, with its title visible.
            val density = LocalDensity.current
            val pivot = with(density) { 52.dp.toPx() }
            // Blank space under the last shelf: exactly enough for the controller to pivot it to
            // the top, so a touch fling can't run on into empty space.
            var lastShelfPx by remember { mutableIntStateOf(0) }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val bottomSpace = with(density) {
                    if (lastShelfPx == 0) 0.dp else (constraints.maxHeight - pivot - lastShelfPx).coerceAtLeast(0f).toDp()
                }
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember(pivot) { PivotBringIntoViewSpec(pivot) }) {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomSpace),
                ) {
                    items(shelves, key = { it.id }) { shelf ->
                        val isLast = shelf.id == shelves.last().id
                        Shelf(
                            title = shelf.title,
                            games = shelf.games,
                            style = shelf.style,
                            metrics = metrics,
                            keyPrefix = shelf.id,
                            memory = memory,
                            onOpen = start,
                            onFocused = { focusedId = it.id },
                            onMenu = { menu.open(it) },
                            trailing = "${shelf.games.size}".takeIf { shelf.games.size > 6 },
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .then(if (isLast) Modifier.onSizeChanged { lastShelfPx = it.height } else Modifier),
                        )
                    }
                }
            }
            }
        }
        val first = shelves.first()
        InitialFocus(memory, memory.requester("${first.id}/${first.games.first().id}"))
        GameMenuHost(menu, onClosed = { memory.restore() }, onDetails = { nav.navigate(Routes.details(it.id)) })
    }
}

/** Full-bleed artwork of the focused game, faded into the background. */
@Composable
fun Backdrop(game: GameEntity?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        // Long, gentle cross-fade at lower brightness: the colour drifts rather than switches.
        Crossfade(targetState = game, animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "backdrop") { g ->
            if (g != null) GameArt(g, ArtStyle.BACKDROP, Modifier.fillMaxSize().alpha(0.42f))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Palette.Background, 0.55f to Palette.Background.copy(alpha = 0.55f), 1f to Color.Transparent)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Palette.Background.copy(alpha = 0.45f), 0.8f to Palette.Background)),
        )
    }
}

@Composable
private fun HeroInfo(game: GameEntity?, compact: Boolean, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val accent = LocalAccent.current
    Column(modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 18.dp, bottom = if (compact) 4.dp else 12.dp)) {
        if (game == null) return@Column
        val platform = container.platforms[game.platformId]
        Text(
            sourceLabel(game, platform?.name).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .background(accent.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            game.title,
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!compact) {
            Spacer(Modifier.height(4.dp))
            Row {
                Text(formatLastPlayed(game.lastPlayedAt), style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                if (game.playTimeMs > 0) {
                    Spacer(Modifier.width(16.dp))
                    Text("Played ${formatPlaytime(game.playTimeMs)}", style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(nav: NavController, modifier: Modifier) {
    val addApps = remember { androidx.compose.ui.focus.FocusRequester() }
    EmptyState(
        icon = Icons.Rounded.SportsEsports,
        title = "Your library is empty",
        body = "Add Android games you have installed, or point PocketDeck at a folder of ROMs.",
        modifier = modifier,
    ) {
        ConsoleButton("Add Android games", { nav.navigate(Routes.ADD_APP) }, icon = Icons.Rounded.Android, primary = true, focusRequester = addApps)
        ConsoleButton("Add ROM folder", { nav.navigate(Routes.ROM_FOLDERS) }, icon = Icons.Rounded.CreateNewFolder)
    }
    InitialFocus(null, addApps)
}
