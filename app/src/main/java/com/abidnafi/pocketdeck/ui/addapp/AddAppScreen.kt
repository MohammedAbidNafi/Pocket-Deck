package com.abidnafi.pocketdeck.ui.addapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.apps.InstalledApp
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MarginBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.rememberAppVisual
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pick any installed app (sideloaded games, apps Android doesn't flag as games) to add to the library. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddAppScreen(nav: NavController) {
    val library = LocalAppContainer.current.library
    val apps by produceState<List<InstalledApp>?>(null) { value = withContext(Dispatchers.IO) { library.installedApps() } }
    val games by library.games.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val inLibrary = remember(games) {
        games.filter { it.type != GameType.ROM && !it.hidden }.mapNotNull { it.packageName }.toSet()
    }
    val first = remember { FocusRequester() }
    val gamesChip = remember { FocusRequester() }
    var showAll by rememberSaveable { mutableStateOf(false) }
    // Games only by default; apps already in the library stay visible so they can be removed.
    val list = remember(apps, showAll, inLibrary) {
        apps?.filter { showAll || it.isGame || it.packageName in inLibrary }
    }

    fun done() {
        nav.popBackStack()
    }

    // Handle back here rather than relying on the NavHost, so B / the Done hint always leave this screen.
    BackHandler(onBack = ::done)
    ControllerHandler { btn ->
        if (btn == Btn.Y) { showAll = !showAll; true } else false
    }
    ButtonHints(
        Hint(Btn.CONFIRM, "Add / remove"),
        Hint(Btn.Y, if (showAll) "Games only" else "All apps"),
        Hint(Btn.BACK, "Done"),
    )

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Text("Add apps to your library", style = MaterialTheme.typography.displayMedium, color = Palette.Text, modifier = Modifier.padding(horizontal = ScreenPadding))
        Text(
            "Games from the Play Store are added automatically. Pick anything else you want to launch from here.",
            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
        Row(
            Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioChip("Games", selected = !showAll, focusRequester = gamesChip) { showAll = false }
            RadioChip("All apps", selected = showAll) { showAll = true }
            Spacer(Modifier.width(6.dp))
            Text("${inLibrary.size} in library", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
        }
        if (list == null) return@Column
        val margin = with(LocalDensity.current) { 24.dp.toPx() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(margin) { MarginBringIntoViewSpec(margin) }) {
            LazyVerticalGrid(
                columns = GridCells.FixedSize(132.dp),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (list.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "No installed apps are marked as games. Choose \"All apps\" to pick from everything.",
                            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                        )
                    }
                }
                items(list, key = { it.packageName }) { app ->
                    val added = app.packageName in inLibrary
                    AppCard(app, added, if (app == list.first()) first else null) {
                        scope.launch {
                            if (added) {
                                games.filter { it.packageName == app.packageName && it.type != GameType.ROM }.forEach { library.remove(it) }
                            } else {
                                library.addApp(app)
                            }
                        }
                    }
                }
                item(key = "done", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        ConsoleButton("Done", ::done, icon = Icons.Rounded.Check, primary = true)
                    }
                }
            }
        }
        InitialFocus(null, if (list.isEmpty()) gamesChip else first)
    }
}

/** One option of a two-way choice, drawn as a pill with a radio dot. */
@Composable
private fun RadioChip(label: String, selected: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val accent = LocalAccent.current
    FocusCard(onClick = onClick, focusRequester = focusRequester, corner = 22.dp, focusScale = 1.05f) { focused ->
        Row(
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        selected -> accent.copy(alpha = 0.28f)
                        else -> Palette.Surface
                    },
                )
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (focused) Palette.Background else Palette.Text
            Box(
                Modifier
                    .size(18.dp)
                    .border(2.dp, if (selected) accent else ink.copy(alpha = 0.6f), CircleShape)
                    .padding(4.dp)
                    .background(if (selected) accent else Color.Transparent, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = ink)
        }
    }
}

@Composable
private fun AppCard(app: InstalledApp, added: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val visual = rememberAppVisual(app.packageName)
    val accent = LocalAccent.current
    FocusCard(onClick = onClick, focusRequester = focusRequester, focusScale = 1.06f, modifier = Modifier.size(132.dp, 150.dp)) { focused ->
        Column(
            Modifier
                .fillMaxSize()
                .background(if (focused) Palette.SurfaceHigher else Palette.Surface)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp)) {
                visual?.let { Image(it.icon, null, Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                app.label, style = MaterialTheme.typography.titleSmall, color = Palette.Text,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (added) {
            Icon(
                Icons.Rounded.CheckCircle, "In library", tint = accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Palette.Background, CircleShape).size(24.dp),
            )
        }
    }
}
