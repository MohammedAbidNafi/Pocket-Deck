package com.abidnafi.pocketdeck.ui.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Crop169
import androidx.compose.material.icons.rounded.CropSquare
import com.abidnafi.pocketdeck.data.TileShape
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette as PaletteIcon
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import com.abidnafi.pocketdeck.ui.components.EdgeSnapBringIntoViewSpec
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.SingletonImageLoader
import com.abidnafi.pocketdeck.BuildConfig
import com.abidnafi.pocketdeck.data.GlyphStyle
import com.abidnafi.pocketdeck.data.TileSize
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalMessages
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.tryFocus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared layout for settings pages: big title and a centred scrolling column. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPage(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ButtonHints(Hint(Btn.CONFIRM, "Select"), Hint(Btn.BACK, "Back"))
    // Controller focus scrolling stops short of the edges, leaving room above the row and
    // clear of the hint bar below, just like scrolling by touch.
    val margin = with(LocalDensity.current) { 72.dp.toPx() }
    val scroll = rememberScrollState()
    // Going back to the first row also brings the page title back; the last row shows what's below it.
    val spec = remember(margin, scroll) {
        EdgeSnapBringIntoViewSpec(margin, scrolled = { scroll.value.toFloat() }, remaining = { (scroll.maxValue - scroll.value).toFloat() })
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 860.dp)
                    .verticalScroll(scroll)
                    .padding(horizontal = ScreenPadding)
                    .padding(top = 28.dp, bottom = 96.dp),
            ) {
                Text(title, style = MaterialTheme.typography.displayMedium, color = Palette.Text)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Folder picker for ROM directories; shows a message on devices without a document picker (some TVs). */
@Composable
fun rememberRomFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val messages = LocalMessages.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onPicked) }
    return {
        try {
            launcher.launch(null)
        } catch (e: ActivityNotFoundException) {
            messages.show("No folder picker", "This device has no system folder picker, so ROM folders can't be added here.")
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val repo = container.settings
    val folders by container.library.folders.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val messages = LocalMessages.current
    val memory = rememberFocusMemory()
    val first = remember { FocusRequester() }
    val accentRow = remember { FocusRequester() }
    val shapeRow = remember { FocusRequester() }
    var picker by remember { mutableStateOf<SettingsPicker?>(null) }

    fun set(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    SettingsPage("Settings") {
        SectionHeader("Library")
        SettingRow(
            "ROM folders", { nav.navigate(Routes.ROM_FOLDERS) }, icon = Icons.Rounded.Folder,
            value = if (folders.isEmpty()) "None" else "${folders.size}",
            subtitle = "Folders PocketDeck scans for emulator games", focusRequester = first,
        )
        SettingRow("Rescan everything", { container.library.rescan(); set { container.library.syncInstalledApps() } }, icon = Icons.Rounded.Refresh, subtitle = "Look for new ROMs and installed games")
        SettingRow("Add apps to library", { nav.navigate(Routes.ADD_APP) }, icon = Icons.Rounded.Apps, subtitle = "Sideloaded games or any app you want here")
        SettingRow(
            "Detect installed games", { set { repo.setAutoDetect(!settings.autoDetectGames) } }, icon = Icons.Rounded.AutoAwesome,
            subtitle = "Add apps Android marks as games automatically", toggle = settings.autoDetectGames,
        )
        SettingRow(
            "Auto collections", { set { repo.setAutoCollections(!settings.autoCollections) } }, icon = Icons.Rounded.CollectionsBookmark,
            subtitle = "Collections that fill themselves: Android, PC, each system, favourites, not played yet",
            toggle = settings.autoCollections,
        )
        if (settings.autoCollections && settings.hiddenAutoCollections.isNotEmpty()) {
            SettingRow(
                "Show hidden auto collections", { set { repo.showAllAutoCollections() } }, icon = Icons.Rounded.Visibility,
                value = "${settings.hiddenAutoCollections.size} hidden",
            )
        }

        SectionHeader("Emulators")
        SettingRow("Emulators", { nav.navigate(Routes.EMULATORS) }, icon = Icons.Rounded.SportsEsports, subtitle = "Default emulator per system, custom launch settings")

        SectionHeader("PC streaming")
        val pcHosts by container.hosts.hosts.collectAsState(emptyList())
        SettingRow(
            "Gaming PCs", { nav.navigate(Routes.STREAMING) }, icon = Icons.Rounded.Computer,
            value = when (val paired = pcHosts.count { it.paired }) {
                0 -> if (pcHosts.isEmpty()) "None" else "Not paired"
                else -> "$paired paired"
            },
            subtitle = "Stream games from a PC running Sunshine, plus stream quality",
        )

        SectionHeader("Controller")
        SettingRow(
            "Button prompts", {
                set { repo.setGlyphStyle(GlyphStyle.entries[(settings.glyphStyle.ordinal + 1) % GlyphStyle.entries.size]) }
            }, icon = Icons.Rounded.Gamepad,
            value = when (settings.glyphStyle) {
                GlyphStyle.XBOX -> "Xbox"
                GlyphStyle.PLAYSTATION -> "PlayStation"
                GlyphStyle.NINTENDO -> "Nintendo"
            },
        )
        SettingRow(
            "Swap confirm and back", { set { repo.setSwapAB(!settings.swapAB) } }, icon = Icons.Rounded.SwapHoriz,
            subtitle = "Use the right face button to confirm (Nintendo layout)", toggle = settings.swapAB,
        )
        SettingRow("UI sounds", { set { repo.setUiSounds(!settings.uiSounds) } }, icon = Icons.AutoMirrored.Rounded.VolumeUp, toggle = settings.uiSounds)
        SettingRow("Vibration", { set { repo.setHaptics(!settings.haptics) } }, icon = Icons.Rounded.Vibration, toggle = settings.haptics)
        SettingRow(
            "Getting back to PocketDeck", { nav.navigate(Routes.RETURN_SHORTCUTS) },
            icon = Icons.AutoMirrored.Rounded.KeyboardReturn,
            subtitle = "Notification, Quick Settings tile and controller shortcut",
        )

        SectionHeader("Appearance")
        SettingRow(
            "Accent colour", { picker = SettingsPicker.ACCENT },
            icon = Icons.Rounded.PaletteIcon,
            value = Palette.accents.getOrNull(settings.accentIndex)?.name,
            focusRequester = accentRow,
        )
        SettingRow(
            "Tile size", { set { repo.setTileSize(TileSize.entries[(settings.tileSize.ordinal + 1) % TileSize.entries.size]) } },
            icon = Icons.Rounded.GridView,
            value = settings.tileSize.name.lowercase().replaceFirstChar { it.uppercase() },
        )
        SettingRow(
            "Tile shape", { picker = SettingsPicker.TILE_SHAPE },
            icon = when (settings.tileShape) {
                TileShape.ROUND -> Icons.Rounded.Circle
                TileShape.SQUARE -> Icons.Rounded.CropSquare
                TileShape.RECTANGLE -> Icons.Rounded.Crop169
            },
            value = tileShapeName(settings.tileShape),
            focusRequester = shapeRow,
        )
        SettingRow(
            "Home rows", { nav.navigate(Routes.HOME_ROWS) }, icon = Icons.Rounded.ViewAgenda,
            subtitle = "Order and show or hide rows like Favourites and your collections",
            value = if (settings.hiddenHomeRows.isEmpty()) null else "${settings.hiddenHomeRows.size} hidden",
        )
        SettingRow("Show clock", { set { repo.setShowClock(!settings.showClock) } }, icon = Icons.Rounded.Schedule, toggle = settings.showClock)

        SectionHeader("Artwork")
        SettingRow(
            "Download box art", { set { repo.setOnlineArtwork(!settings.onlineArtwork) } }, icon = Icons.Rounded.Image,
            subtitle = "Fetch ROM covers from the free libretro thumbnail server", toggle = settings.onlineArtwork,
        )
        SettingRow(
            "Only on Wi-Fi", { set { repo.setWifiOnlyArtwork(!settings.wifiOnlyArtwork) } }, icon = Icons.Rounded.Wifi,
            subtitle = "Skip downloads on metered connections", toggle = settings.wifiOnlyArtwork,
        )
        SettingRow("Clear artwork cache", {
            set {
                withContext(Dispatchers.IO) {
                    SingletonImageLoader.get(context).let { loader ->
                        loader.diskCache?.clear()
                        loader.memoryCache?.clear()
                    }
                }
                messages.show("Artwork cache cleared", "Covers will be downloaded again as you browse.")
            }
        }, icon = Icons.Rounded.CleaningServices)

        SectionHeader("About")
        SettingRow(
            "About & credits", { nav.navigate(Routes.ABOUT) }, icon = Icons.Rounded.Info,
            subtitle = "PocketDeck ${BuildConfig.VERSION_NAME} · made by $DEVELOPER_NAME · open-source licences",
        )
        SettingRow(
            "Run setup again", {
                set { repo.setOnboardingDone(false) }
                nav.navigate(Routes.ONBOARDING) { popUpTo(Routes.MAIN) { inclusive = true } }
            }, icon = Icons.Rounded.Refresh,
            subtitle = "Pick button style and add games again",
        )
    }
    InitialFocus(memory, first)

    fun closePicker() {
        picker = null
    }
    // Put focus back on the row that opened the menu once it has animated away (it traps focus until then).
    var lastPicker by remember { mutableStateOf<SettingsPicker?>(null) }
    LaunchedEffect(picker) {
        val open = picker
        if (open != null) lastPicker = open
        else lastPicker?.let {
            kotlinx.coroutines.delay(180)
            when (it) {
                SettingsPicker.ACCENT -> accentRow.tryFocus()
                SettingsPicker.TILE_SHAPE -> shapeRow.tryFocus()
            }
        }
    }
    SideMenu(
        visible = picker == SettingsPicker.ACCENT,
        startOnChecked = true,
        title = "Accent colour",
        subtitle = "Used for highlights, buttons and the glow around selected items",
        items = Palette.accents.mapIndexed { i, accent ->
            MenuItem(accent.name, swatch = accent.color, checked = i == settings.accentIndex) {
                set { repo.setAccent(i) }
                closePicker()
            }
        },
        onDismiss = ::closePicker,
    )
    SideMenu(
        visible = picker == SettingsPicker.TILE_SHAPE,
        startOnChecked = true,
        title = "Tile shape",
        subtitle = "How game tiles look on Home, in the Library and in Search",
        items = TileShape.entries.map { shape ->
            MenuItem(
                tileShapeName(shape),
                icon = when (shape) {
                    TileShape.ROUND -> Icons.Rounded.Circle
                    TileShape.SQUARE -> Icons.Rounded.CropSquare
                    TileShape.RECTANGLE -> Icons.Rounded.Crop169
                },
                subtitle = when (shape) {
                    TileShape.ROUND -> "Circles, like app icons"
                    TileShape.SQUARE -> "Even squares in every row"
                    TileShape.RECTANGLE -> "Wide tiles and box-art covers (default)"
                },
                checked = settings.tileShape == shape,
            ) {
                set { repo.setTileShape(shape) }
                closePicker()
            }
        },
        onDismiss = ::closePicker,
    )
}

private enum class SettingsPicker { ACCENT, TILE_SHAPE }

private fun tileShapeName(shape: TileShape) = when (shape) {
    TileShape.ROUND -> "Round"
    TileShape.SQUARE -> "Square"
    TileShape.RECTANGLE -> "Rectangle"
}
