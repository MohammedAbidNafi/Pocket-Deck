package com.abidnafi.pocketdeck.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.db.RomFolderEntity
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.tryFocus
import com.abidnafi.pocketdeck.ui.formatLastPlayed
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun RomFoldersScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val library = container.library
    val folders by library.folders.collectAsState(emptyList())
    val scan by library.scanState.collectAsState()
    val scope = rememberCoroutineScope()
    val memory = rememberFocusMemory()
    val add = remember { FocusRequester() }
    var selected by remember { mutableStateOf<RomFolderEntity?>(null) }
    var pickingPlatform by remember { mutableStateOf<RomFolderEntity?>(null) }
    var removing by remember { mutableStateOf<RomFolderEntity?>(null) }
    val pick = rememberRomFolderPicker { uri -> scope.launch { library.addRomFolder(uri, null) } }

    SettingsPage(
        "ROM folders",
        "Pick the folder that holds your ROMs. Sub-folders named after systems (snes, psx, gba, ps2…) are detected automatically, " +
            "or assign one system to a whole folder.",
    ) {
        if (scan.running) ScanBanner(scan.folderName, scan.found)
        SettingRow("Add folder", pick, icon = Icons.Rounded.CreateNewFolder, focusRequester = add)
        if (folders.isNotEmpty()) SectionHeader("Folders")
        folders.forEach { f ->
            val platform = container.platforms[f.platformId]
            SettingRow(
                title = f.name,
                subtitle = buildString {
                    append(platform?.name ?: "Systems detected automatically")
                    append(" · ")
                    append(if (f.lastScanAt == null) "Not scanned yet" else "Scanned ${formatLastPlayed(f.lastScanAt).lowercase()}")
                },
                value = com.abidnafi.pocketdeck.ui.gameCount(f.gameCount),
                icon = Icons.Rounded.Folder,
                onClick = { selected = f },
                focusRequester = memory.requester("f/${f.id}"),
                onFocusChange = { if (it) memory.lastKey = "f/${f.id}" },
            )
        }
    }
    InitialFocus(memory, add)

    val sel = selected
    SideMenu(
        visible = sel != null,
        title = sel?.name.orEmpty(),
        items = listOf(
            MenuItem("Rescan", Icons.Rounded.Refresh) { sel?.let { library.rescan(it.id) }; selected = null; memory.restore() },
            MenuItem("System", Icons.Rounded.SportsEsports, subtitle = container.platforms[sel?.platformId]?.name ?: "Detect automatically") {
                pickingPlatform = sel; selected = null
            },
            MenuItem("Remove folder", Icons.Rounded.DeleteOutline, danger = true) { removing = sel; selected = null },
        ),
        onDismiss = { selected = null; memory.restore() },
    )
    val target = pickingPlatform
    SideMenu(
        visible = target != null,
        title = "System for this folder",
        items = buildList {
            add(MenuItem("Detect automatically", subtitle = "From sub-folder names and file types", checked = target?.platformId == null) {
                target?.let { scope.launch { library.setFolderPlatform(it, null) } }; pickingPlatform = null; memory.restore()
            })
            container.platforms.platforms.forEach { p ->
                add(MenuItem(p.name, checked = target?.platformId == p.id) {
                    target?.let { scope.launch { library.setFolderPlatform(it, p.id) } }; pickingPlatform = null; memory.restore()
                })
            }
        },
        onDismiss = { pickingPlatform = null; memory.restore() },
    )
    DialogOverlay(
        visible = removing != null,
        title = "Remove \"${removing?.name.orEmpty()}\"?",
        body = "Its games disappear from PocketDeck. Your files are not touched.",
        confirmLabel = "Remove",
        dismissLabel = "Cancel",
        danger = true,
        onConfirm = {
            removing?.let { scope.launch { library.removeRomFolder(it) } }
            removing = null
            add.tryFocus()
        },
        onDismiss = { removing = null; memory.restore() },
    )
}

@Composable
private fun ScanBanner(folder: String?, found: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(LocalAccent.current.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp, color = LocalAccent.current)
        Spacer(Modifier.width(14.dp))
        Text("Scanning ${folder.orEmpty()}… $found games found", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    }
}

