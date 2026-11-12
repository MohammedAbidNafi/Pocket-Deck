package com.abidnafi.pocketdeck.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.abidnafi.pocketdeck.ui.rememberAndroidHomeSwitcher
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.AndroidHomePicker
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.zIndex
import com.abidnafi.pocketdeck.input.LocalHints
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.focus.FocusRequester
import com.abidnafi.pocketdeck.ui.components.LocalInitialFocusAllowed
import com.abidnafi.pocketdeck.ui.components.tryFocus
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Priority
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.collections.CollectionsScreen
import com.abidnafi.pocketdeck.ui.components.ButtonGlyph
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.IconPill
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.StatusIndicators
import com.abidnafi.pocketdeck.ui.components.rememberBattery
import com.abidnafi.pocketdeck.ui.components.rememberClock
import com.abidnafi.pocketdeck.ui.components.rememberControllers
import com.abidnafi.pocketdeck.ui.findActivity
import com.abidnafi.pocketdeck.ui.home.HomeScreen
import com.abidnafi.pocketdeck.ui.library.LibraryScreen
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette

enum class Tab(val label: String) { HOME("Home"), LIBRARY("Library"), COLLECTIONS("Collections") }

val TopBarHeight: Dp = 64.dp

/** Tabbed Big-Picture shell: L1/R1 switch tabs, Start opens the quick menu, Select opens search. */
@Composable
fun MainShell(nav: NavController) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var quickMenu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val activity = LocalContext.current.findActivity()
    val container = LocalAppContainer.current
    val homeState by container.homeMode.state.collectAsState()
    val androidHome = rememberAndroidHomeSwitcher()
    // Home screen mode: pressing Home while PocketDeck is open goes back to the Home tab.
    val homePresses by container.homeMode.homePresses.collectAsState()
    var seenHomePresses by rememberSaveable { mutableIntStateOf(homePresses) }
    LaunchedEffect(homePresses) {
        if (homePresses != seenHomePresses) {
            seenHomePresses = homePresses
            tab = Tab.HOME
            quickMenu = false
            confirmExit = false
        }
    }

    // Top bar: the three tabs, then Search and Settings. L1/R1 walk the whole bar with a
    // focus pill; landing on a tab also switches to it.
    // Tabs, Search, Settings, and (as the home screen) Android home.
    val barItems = remember { List(Tab.entries.size + 3) { FocusRequester() } }
    val barCount = Tab.entries.size + if (homeState.isDefault) 3 else 2
    var barFocus by remember { mutableStateOf<Int?>(null) }

    fun stepBar(delta: Int) {
        val from = barFocus ?: tab.ordinal
        val to = (from.coerceAtMost(barCount - 1) + delta + barCount) % barCount
        if (to < Tab.entries.size) tab = Tab.entries[to]
        barFocus = to
        barItems[to].tryFocus()
    }

    ControllerHandler(Priority.SHELL) { btn ->
        when (btn) {
            Btn.L1 -> { stepBar(-1); true }
            Btn.R1 -> { stepBar(1); true }
            Btn.START -> { quickMenu = true; true }
            Btn.SELECT -> { nav.navigate(Routes.SEARCH); true }
            else -> false
        }
    }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    // As the home screen there's nothing to go back to, so Back on Home does nothing.
    BackHandler(enabled = tab == Tab.HOME) { if (!homeState.isDefault) confirmExit = true }

    // Menus opened from inside a tab (e.g. a game's options) must cover the top bar too.
    val overlayOpen = LocalHints.current.overlayOpen
    Box(Modifier.fillMaxSize()) {
        // A tab shown while the user is on the top bar keeps focus up there.
        CompositionLocalProvider(LocalInitialFocusAllowed provides { barFocus == null }) {
        AnimatedContent(
            modifier = Modifier.zIndex(if (overlayOpen) 2f else 0f),
            targetState = tab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
            label = "tab",
        ) { t ->
            holder.SaveableStateProvider(t.name) {
                when (t) {
                    Tab.HOME -> HomeScreen(nav, TopBarHeight)
                    Tab.LIBRARY -> LibraryScreen(nav, TopBarHeight)
                    Tab.COLLECTIONS -> CollectionsScreen(nav, TopBarHeight)
                }
            }
        }
        }
        TopBar(
            tab = tab,
            items = barItems,
            onItemFocus = { i, f -> if (f) barFocus = i else if (barFocus == i) barFocus = null },
            onTab = { tab = it },
            onSearch = { nav.navigate(Routes.SEARCH) },
            onSettings = { nav.navigate(Routes.SETTINGS) },
            onAndroidHome = if (homeState.isDefault) ({ androidHome.open() }) else null,
        )
        Box(Modifier.fillMaxSize().zIndex(3f)) {
            QuickMenu(
                visible = quickMenu,
                onDismiss = { quickMenu = false },
                onTab = { tab = it; quickMenu = false },
                onNavigate = { quickMenu = false; nav.navigate(it) },
                onExit = { quickMenu = false; confirmExit = true },
                isHome = homeState.isDefault,
                onAndroidHome = { quickMenu = false; androidHome.open() },
            )
            AndroidHomePicker(androidHome)
            DialogOverlay(
                visible = confirmExit,
                title = "Exit PocketDeck?",
                body = "You can come back any time from your home screen.",
                confirmLabel = "Exit",
                dismissLabel = "Stay",
                onConfirm = { activity?.finish() },
                onDismiss = { confirmExit = false },
            )
        }
    }
}

@Composable
private fun TopBar(
    tab: Tab,
    items: List<FocusRequester>,
    onItemFocus: (Int, Boolean) -> Unit,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAndroidHome: (() -> Unit)?,
) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(Brush.verticalGradient(listOf(Palette.Background.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = ScreenPadding - 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonGlyph(Btn.L1, 24.dp)
        Spacer(Modifier.width(8.dp))
        Tab.entries.forEach { t ->
            val selected = t == tab
            FocusCard(
                onClick = { onTab(t) },
                corner = 20.dp,
                focusScale = 1.08f,
                focusRequester = items[t.ordinal],
                onFocusChange = { onItemFocus(t.ordinal, it) },
                modifier = Modifier.padding(horizontal = 2.dp),
            ) { focused ->
                val bg by animateColorAsState(
                    when {
                        focused -> Color.White
                        selected -> accent.copy(alpha = 0.28f)
                        else -> Color.Transparent
                    },
                    tween(180), label = "tabPill",
                )
                Text(
                    t.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        focused -> Palette.Background
                        selected -> Palette.Text
                        else -> Palette.TextDim
                    },
                    modifier = Modifier
                        .background(bg)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        ButtonGlyph(Btn.R1, 24.dp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val search = Tab.entries.size
            IconPill(
                Icons.Rounded.Search, "Search", onSearch,
                focusRequester = items[search], onFocusChange = { onItemFocus(search, it) },
            )
            IconPill(
                Icons.Rounded.Settings, "Settings", onSettings,
                focusRequester = items[search + 1], onFocusChange = { onItemFocus(search + 1, it) },
            )
            // Only while PocketDeck is the home screen: one press to the regular Android launcher.
            if (onAndroidHome != null) {
                IconPill(
                    Icons.Rounded.PhoneAndroid, "Switch to Android home", onAndroidHome,
                    focusRequester = items[search + 2], onFocusChange = { onItemFocus(search + 2, it) },
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusIndicators(LocalSettings.current.showClock)
        }
    }
}

@Composable
private fun QuickMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTab: (Tab) -> Unit,
    onNavigate: (String) -> Unit,
    onExit: () -> Unit,
    isHome: Boolean,
    onAndroidHome: () -> Unit,
) {
    SideMenu(
        visible = visible,
        title = "Quick menu",
        onDismiss = onDismiss,
        header = { QuickMenuHeader() },
        items = listOf(
            MenuItem("Home", Icons.Rounded.Home) { onTab(Tab.HOME) },
            MenuItem("Library", Icons.Rounded.Apps) { onTab(Tab.LIBRARY) },
            MenuItem("Collections", Icons.Rounded.CollectionsBookmark) { onTab(Tab.COLLECTIONS) },
            MenuItem("Search", Icons.Rounded.Search) { onNavigate(Routes.SEARCH) },
            MenuItem("ROM folders", Icons.Rounded.Folder) { onNavigate(Routes.ROM_FOLDERS) },
            MenuItem("Settings", Icons.Rounded.Settings) { onNavigate(Routes.SETTINGS) },
            // As the home screen, "exit" means going to the regular Android launcher.
            if (isHome) {
                MenuItem("Switch to Android home", Icons.Rounded.PhoneAndroid, subtitle = "Press Home to come back", onClick = onAndroidHome)
            } else {
                MenuItem("Exit PocketDeck", Icons.AutoMirrored.Rounded.ExitToApp, danger = true, onClick = onExit)
            },
        ),
    )
}

@Composable
private fun QuickMenuHeader() {
    val battery = rememberBattery()
    val controllers = rememberControllers()
    // One compact status line (the clock is already in the top bar).
    val parts = buildList {
        if (battery.present) add("Battery ${battery.percent}%" + if (battery.charging) " · Charging" else "")
        add(if (controllers.isEmpty()) "No controller connected" else controllers.joinToString())
    }
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
