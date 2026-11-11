package com.abidnafi.pocketdeck.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.R
import com.abidnafi.pocketdeck.shortcuts.ControllerShortcutService
import com.abidnafi.pocketdeck.shortcuts.PocketDeckTileService
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalMessages
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import kotlinx.coroutines.launch

/** Ways to get back to PocketDeck from inside a game: notification, Quick Settings tile, controller shortcut. */
@Composable
fun ReturnShortcutsScreen(nav: NavController) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val memory = rememberFocusMemory()
    val first = remember { FocusRequester() }

    var shortcutOn by remember { mutableStateOf(ControllerShortcutService.isEnabled(context)) }
    var explainShortcut by remember { mutableStateOf(false) }
    // The accessibility switch lives in system settings; re-check whenever we come back.
    LifecycleResumeEffect(settings.returnNotification) {
        shortcutOn = ControllerShortcutService.isEnabled(context)
        if (settings.returnNotification && !container.returnNotifier.canPost()) {
            scope.launch { container.settings.setReturnNotification(false) }
        }
        onPauseOrDispose { }
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { container.settings.setReturnNotification(true) }
        } else {
            messages.show(
                "Notifications are off",
                "Allow notifications for PocketDeck in Android Settings → Apps → PocketDeck → Notifications, then turn this on again.",
            )
        }
    }

    fun toggleNotification() {
        when {
            settings.returnNotification -> scope.launch { container.settings.setReturnNotification(false) }
            container.returnNotifier.canPost() -> scope.launch { container.settings.setReturnNotification(true) }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun addTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sbm = context.getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(context, PocketDeckTileService::class.java),
                context.getString(R.string.tile_label),
                Icon.createWithResource(context, R.drawable.ic_notification),
                context.mainExecutor,
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        messages.show("Tile already added", "Pull down from the top of the screen and tap PocketDeck to come back.")
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> Unit
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
                    else -> messages.show("Add the tile by hand", TILE_HOW_TO)
                }
            }
        } else {
            messages.show("Add the PocketDeck tile", TILE_HOW_TO)
        }
    }

    fun openAccessibilitySettings() {
        explainShortcut = false
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            messages.show("Can't open Accessibility settings", "Open Android Settings → Accessibility → PocketDeck controller shortcut.")
        }
    }

    SettingsPage("Getting back", subtitle = "Ways to return to PocketDeck while you're in a game") {
        SectionHeader("While playing")
        SettingRow(
            "Back to PocketDeck notification", ::toggleNotification, icon = Icons.Rounded.Notifications,
            subtitle = "While a game runs, pull down the notification shade and tap it to come back",
            toggle = settings.returnNotification, focusRequester = first,
        )
        val tile = memory.requester("tile")
        SettingRow(
            "Quick Settings tile", ::addTile, icon = Icons.Rounded.SpaceDashboard,
            subtitle = "Adds a PocketDeck button to the pull-down panel next to Wi-Fi and Bluetooth",
            value = "Add", focusRequester = tile, onFocusChange = { if (it) memory.lastKey = "tile" },
        )

        SectionHeader("Controller")
        val shortcut = memory.requester("shortcut")
        SettingRow(
            "Controller shortcut", { explainShortcut = true }, icon = Icons.Rounded.Gamepad,
            subtitle = "Hold Select + Start (View + Menu on Xbox) for a moment to open PocketDeck from any game",
            toggle = shortcutOn, focusRequester = shortcut, onFocusChange = { if (it) memory.lastKey = "shortcut" },
        )
    }
    InitialFocus(memory, first)

    DialogOverlay(
        visible = explainShortcut,
        title = if (shortcutOn) "Turn off the controller shortcut" else "Turn on the controller shortcut",
        body = if (shortcutOn) {
            "In Accessibility settings, open \"PocketDeck controller shortcut\" and switch it off."
        } else {
            "Android only lets an accessibility service see controller buttons while another app is open. " +
                "In Accessibility settings, open \"PocketDeck controller shortcut\" and switch it on.\n\n" +
                "It only looks at controller buttons and passes them on to the game; it doesn't read the screen or send anything anywhere.\n\n" +
                "If the switch is greyed out (apps not installed from Play), go to Settings → Apps → PocketDeck, " +
                "tap ⋮ → Allow restricted settings, then try again."
        },
        confirmLabel = "Open settings",
        dismissLabel = "Cancel",
        onConfirm = ::openAccessibilitySettings,
        onDismiss = { explainShortcut = false },
    )
}

private const val TILE_HOW_TO =
    "Pull down twice from the top of the screen, tap the pencil (Edit), and drag the PocketDeck tile into your tiles."
