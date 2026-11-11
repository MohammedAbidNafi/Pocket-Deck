package com.abidnafi.pocketdeck.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.apps.InstalledApp
import com.abidnafi.pocketdeck.data.emulators.EmulatorRegistry
import com.abidnafi.pocketdeck.data.model.EmulatorDef
import com.abidnafi.pocketdeck.data.model.Platform
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.TextEntryOverlay
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.tryFocus
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a custom emulator receives the ROM. */
private enum class RomDelivery(val label: String, val description: String) {
    DATA_URI("Open file (content URI)", "Intent data = {rom_uri}. Works with most modern emulators."),
    DATA_PATH("Open file (file path)", "Intent data = file://{rom_path}. Needs a real path on storage."),
    EXTRA_URI("Extra with URI", "Puts {rom_uri} in an extra you name."),
    EXTRA_PATH("Extra with path", "Puts {rom_path} in an extra you name."),
}

private data class Draft(
    val id: String,
    val name: String = "",
    val packageName: String? = null,
    val platforms: Set<String> = emptySet(),
    val activity: String = "",
    val action: String = "android.intent.action.VIEW",
    val delivery: RomDelivery = RomDelivery.DATA_URI,
    val extraKey: String = "",
    val isNew: Boolean = true,
) {
    val valid get() = name.isNotBlank() && packageName != null && platforms.isNotEmpty() &&
        (delivery == RomDelivery.DATA_URI || delivery == RomDelivery.DATA_PATH || extraKey.isNotBlank())

    fun toDef() = EmulatorDef(
        id = id,
        name = name.trim(),
        packages = listOfNotNull(packageName),
        platforms = platforms.toList(),
        activity = activity.trim().ifEmpty { null },
        action = action.trim().ifEmpty { "android.intent.action.VIEW" },
        data = when (delivery) {
            RomDelivery.DATA_URI -> "{rom_uri}"
            RomDelivery.DATA_PATH -> "file://{rom_path}"
            else -> null
        },
        extras = when (delivery) {
            RomDelivery.EXTRA_URI -> mapOf(extraKey.trim() to "{rom_uri}")
            RomDelivery.EXTRA_PATH -> mapOf(extraKey.trim() to "{rom_path}")
            else -> emptyMap()
        },
        flags = listOf("clear_top"),
        needsFilePath = delivery == RomDelivery.DATA_PATH || delivery == RomDelivery.EXTRA_PATH,
        custom = true,
    )

    companion object {
        fun from(def: EmulatorDef): Draft {
            val extra = def.extras.entries.firstOrNull()
            val delivery = when {
                def.data?.contains("{rom_path}") == true -> RomDelivery.DATA_PATH
                def.data != null -> RomDelivery.DATA_URI
                extra?.value?.contains("{rom_path}") == true -> RomDelivery.EXTRA_PATH
                extra != null -> RomDelivery.EXTRA_URI
                else -> RomDelivery.DATA_URI
            }
            return Draft(
                id = def.id, name = def.name, packageName = def.packages.firstOrNull(), platforms = def.platforms.toSet(),
                activity = def.activity.orEmpty(), action = def.action ?: "android.intent.action.VIEW",
                delivery = delivery, extraKey = extra?.key.orEmpty(), isNew = false,
            )
        }
    }
}

private enum class Field { NAME, ACTIVITY, ACTION, EXTRA }
private enum class Picker { PACKAGE, PLATFORMS, DELIVERY }

@Composable
fun EmulatorsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val repo = container.settings
    val registry = container.emulators
    val games by container.library.games.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val memory = rememberFocusMemory()
    val first = remember { FocusRequester() }

    // Built-ins from the registry, custom ones straight from settings so edits show immediately.
    val emulators = remember(settings.customEmulators) { settings.customEmulators + registry.all.filterNot { it.custom } }
    val usedPlatforms = remember(games) { games.mapNotNull { it.platformId }.toSet() }
    val platforms = container.platforms.platforms
    val shownPlatforms = platforms.filter { it.id in usedPlatforms }.ifEmpty { platforms }

    var choosingFor by remember { mutableStateOf<Platform?>(null) }
    var customMenu by remember { mutableStateOf<EmulatorDef?>(null) }
    var deleting by remember { mutableStateOf<EmulatorDef?>(null) }
    var draft by remember { mutableStateOf<Draft?>(null) }

    fun candidates(platformId: String) =
        emulators.filter { it.supports(platformId) }.sortedByDescending { registry.isInstalled(it) }

    if (draft != null) {
        CustomEmulatorEditor(
            draft = draft!!,
            onChange = { draft = it },
            onSave = { d -> scope.launch { repo.saveCustomEmulator(d.toDef()) }; draft = null },
            onCancel = { draft = null },
        )
        return
    }

    SettingsPage(
        "Emulators",
        "Choose which emulator opens each system. You can still override it per game from the game's Options menu.",
    ) {
        SectionHeader("Default emulator per system")
        shownPlatforms.forEachIndexed { i, p ->
            val def = registry.defaultFor(p.id, settings.defaultEmulators[p.id])
            val count = games.count { it.platformId == p.id }
            SettingRow(
                title = p.name,
                subtitle = if (count > 0) "$count games" else null,
                value = def?.name ?: "None installed",
                icon = Icons.Rounded.SportsEsports,
                onClick = { choosingFor = p },
                focusRequester = if (i == 0) first else memory.requester("p/${p.id}"),
                onFocusChange = { if (it && i > 0) memory.lastKey = "p/${p.id}" },
            )
        }

        SectionHeader("Custom emulators")
        Text(
            "For emulators PocketDeck doesn't know yet: pick the app, the systems it runs and how it expects the ROM.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
        )
        SettingRow("Add custom emulator", {
            draft = Draft(id = "custom_${System.currentTimeMillis()}")
        }, icon = Icons.Rounded.Add)
        settings.customEmulators.forEach { def ->
            SettingRow(
                title = def.name,
                subtitle = def.platforms.mapNotNull { container.platforms[it]?.shortName }.joinToString(),
                value = if (registry.isInstalled(def)) null else "Not installed",
                icon = Icons.Rounded.Tune,
                onClick = { customMenu = def },
                focusRequester = memory.requester("c/${def.id}"),
                onFocusChange = { if (it) memory.lastKey = "c/${def.id}" },
            )
        }

        SectionHeader("Known emulators")
        registry.all.filterNot { it.custom || it.standaloneOnly }.forEach { def ->
            val installed = registry.isInstalled(def)
            SettingRow(
                title = def.name,
                subtitle = def.note ?: def.platforms.mapNotNull { container.platforms[it]?.shortName }.joinToString(),
                value = if (installed) "Installed" else "Not installed",
                onClick = {},
            )
        }
    }
    InitialFocus(memory, first)

    val target = choosingFor
    SideMenu(
        visible = target != null,
        startOnChecked = true,
        title = target?.name.orEmpty(),
        subtitle = "Default emulator",
        items = if (target == null) emptyList() else buildList {
            val configured = settings.defaultEmulators[target.id]
            add(MenuItem("Automatic", subtitle = "First installed emulator", checked = configured == null) {
                scope.launch { repo.setDefaultEmulator(target.id, null) }; choosingFor = null; memory.restore()
            })
            candidates(target.id).forEach { def ->
                val installed = registry.isInstalled(def)
                add(MenuItem(def.name, subtitle = if (installed) def.note else "Not installed", enabled = installed, checked = configured == def.id) {
                    scope.launch { repo.setDefaultEmulator(target.id, def.id) }; choosingFor = null; memory.restore()
                })
            }
            val chooser = EmulatorRegistry.systemChooser
            add(MenuItem(chooser.name, checked = configured == chooser.id) {
                scope.launch { repo.setDefaultEmulator(target.id, chooser.id) }; choosingFor = null; memory.restore()
            })
        },
        onDismiss = { choosingFor = null; memory.restore() },
    )
    val custom = customMenu
    SideMenu(
        visible = custom != null,
        title = custom?.name.orEmpty(),
        items = listOf(
            MenuItem("Edit", Icons.Rounded.Edit) { draft = custom?.let(Draft::from); customMenu = null },
            MenuItem("Delete", Icons.Rounded.DeleteOutline, danger = true) { deleting = custom; customMenu = null },
        ),
        onDismiss = { customMenu = null; memory.restore() },
    )
    DialogOverlay(
        visible = deleting != null,
        title = "Delete \"${deleting?.name.orEmpty()}\"?",
        body = "Systems and games using it go back to their automatic emulator.",
        confirmLabel = "Delete",
        dismissLabel = "Cancel",
        danger = true,
        onConfirm = {
            deleting?.let { d ->
                scope.launch {
                    repo.deleteCustomEmulator(d.id)
                    settings.defaultEmulators.filterValues { it == d.id }.keys.forEach { repo.setDefaultEmulator(it, null) }
                    games.filter { it.emulatorOverride == d.id }.forEach { container.library.setEmulatorOverride(it.id, null) }
                }
            }
            deleting = null
            first.tryFocus()
        },
        onDismiss = { deleting = null; memory.restore() },
    )
}

@Composable
private fun CustomEmulatorEditor(draft: Draft, onChange: (Draft) -> Unit, onSave: (Draft) -> Unit, onCancel: () -> Unit) {
    val container = LocalAppContainer.current
    val apps by produceState<List<InstalledApp>>(emptyList()) {
        value = withContext(Dispatchers.IO) { container.installedApps.query() }
    }
    val first = remember { FocusRequester() }
    val memory = rememberFocusMemory()
    var editing by remember { mutableStateOf<Field?>(null) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    val appLabel = draft.packageName?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }

    androidx.activity.compose.BackHandler(enabled = editing == null && picker == null, onBack = onCancel)

    fun row(key: String) = memory.requester(key)
    fun focus(key: String): (Boolean) -> Unit = { if (it) memory.lastKey = key }

    SettingsPage(if (draft.isNew) "New custom emulator" else "Edit ${draft.name}") {
        SettingRow("Name", { editing = Field.NAME }, icon = Icons.Rounded.Badge, value = draft.name.ifBlank { "Required" },
            focusRequester = first, onFocusChange = focus("name"))
        SettingRow("App", { picker = Picker.PACKAGE }, icon = Icons.Rounded.Apps, value = appLabel ?: "Required",
            subtitle = draft.packageName, focusRequester = row("app"), onFocusChange = focus("app"))
        SettingRow(
            "Systems", { picker = Picker.PLATFORMS }, icon = Icons.Rounded.SportsEsports,
            value = if (draft.platforms.isEmpty()) "Required" else "${draft.platforms.size}",
            subtitle = draft.platforms.mapNotNull { container.platforms[it]?.shortName }.joinToString().ifEmpty { null },
            focusRequester = row("systems"), onFocusChange = focus("systems"),
        )
        SectionHeader("Launch")
        SettingRow("ROM delivery", { picker = Picker.DELIVERY }, icon = Icons.Rounded.Link, value = draft.delivery.label,
            subtitle = draft.delivery.description, focusRequester = row("delivery"), onFocusChange = focus("delivery"))
        if (draft.delivery == RomDelivery.EXTRA_URI || draft.delivery == RomDelivery.EXTRA_PATH) {
            SettingRow("Extra name", { editing = Field.EXTRA }, icon = Icons.Rounded.Code, value = draft.extraKey.ifBlank { "Required" },
                focusRequester = row("extra"), onFocusChange = focus("extra"))
        }
        SettingRow("Activity", { editing = Field.ACTIVITY }, icon = Icons.Rounded.Code,
            value = draft.activity.ifBlank { "App default" }, subtitle = "Fully-qualified class, optional",
            focusRequester = row("activity"), onFocusChange = focus("activity"))
        SettingRow("Intent action", { editing = Field.ACTION }, icon = Icons.Rounded.Code, value = draft.action.substringAfterLast('.'),
            subtitle = draft.action, focusRequester = row("action"), onFocusChange = focus("action"))

        SettingRow(
            "Save", { if (draft.valid) onSave(draft) }, icon = Icons.Rounded.Save,
            subtitle = if (draft.valid) null else "Fill in the required fields first",
            focusRequester = row("save"), onFocusChange = focus("save"),
        )
        SettingRow("Cancel", onCancel, focusRequester = row("cancel"), onFocusChange = focus("cancel"))
    }
    InitialFocus(null, first)

    val field = editing
    TextEntryOverlay(
        visible = field != null,
        title = when (field) {
            Field.NAME -> "Emulator name"
            Field.ACTIVITY -> "Activity class"
            Field.ACTION -> "Intent action"
            Field.EXTRA -> "Extra name"
            null -> ""
        },
        initial = when (field) {
            Field.NAME -> draft.name
            Field.ACTIVITY -> draft.activity
            Field.ACTION -> draft.action
            Field.EXTRA -> draft.extraKey
            null -> ""
        },
        onDone = { text ->
            onChange(
                when (field) {
                    Field.NAME -> draft.copy(name = text)
                    Field.ACTIVITY -> draft.copy(activity = text.replace(" ", ""))
                    Field.ACTION -> draft.copy(action = text.replace(" ", ""))
                    Field.EXTRA -> draft.copy(extraKey = text.replace(" ", ""))
                    null -> draft
                },
            )
            editing = null
            memory.restore()
        },
        onDismiss = { editing = null; memory.restore() },
    )
    SideMenu(
        visible = picker == Picker.PACKAGE,
        title = "Which app?",
        items = apps.map { app ->
            MenuItem(app.label, subtitle = app.packageName, checked = app.packageName == draft.packageName) {
                onChange(draft.copy(packageName = app.packageName, name = draft.name.ifBlank { app.label }))
                picker = null; memory.restore()
            }
        },
        onDismiss = { picker = null; memory.restore() },
    )
    SideMenu(
        visible = picker == Picker.PLATFORMS,
        title = "Systems it runs",
        subtitle = "Select all that apply, then go back",
        items = container.platforms.platforms.map { p ->
            val on = p.id in draft.platforms
            MenuItem(p.name, checked = on) {
                onChange(draft.copy(platforms = if (on) draft.platforms - p.id else draft.platforms + p.id))
            }
        },
        onDismiss = { picker = null; memory.restore() },
    )
    SideMenu(
        visible = picker == Picker.DELIVERY,
        startOnChecked = true,
        title = "How the ROM is passed",
        items = RomDelivery.entries.map { d ->
            MenuItem(d.label, subtitle = d.description, checked = d == draft.delivery) {
                onChange(draft.copy(delivery = d)); picker = null; memory.restore()
            }
        },
        onDismiss = { picker = null; memory.restore() },
    )
}
