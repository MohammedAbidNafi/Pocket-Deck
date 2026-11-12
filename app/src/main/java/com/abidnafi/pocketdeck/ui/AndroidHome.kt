package com.abidnafi.pocketdeck.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.abidnafi.pocketdeck.data.OtherLauncher
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.SideMenu
import kotlinx.coroutines.launch

/**
 * "Switch to Android home" while PocketDeck is the home screen: opens the user's regular launcher.
 * With several launchers and no saved choice, [open] shows a picker (draw it with [AndroidHomePicker]).
 */
class AndroidHomeSwitcher internal constructor(
    private val resolve: () -> OtherLauncher?,
    private val list: () -> List<OtherLauncher>,
    internal val launch: (OtherLauncher) -> Unit,
    internal val remember: (OtherLauncher) -> Unit,
    private val onNone: () -> Unit,
) {
    /** Non-null while the picker is open. */
    internal var choices by mutableStateOf<List<OtherLauncher>?>(null)
    /** Whether picking also opens the launcher (false when just choosing it in Settings). */
    internal var openAfterPick = true

    /** Opens the saved / only launcher, or asks which one. */
    fun open() {
        val target = resolve()
        if (target != null) launch(target) else showPicker(open = true)
    }

    /** Lets the user choose which launcher to use, without opening it. */
    fun choose() = showPicker(open = false)

    private fun showPicker(open: Boolean) {
        val all = list()
        if (all.isEmpty()) {
            onNone()
        } else {
            openAfterPick = open
            choices = all
        }
    }
}

@Composable
fun rememberAndroidHomeSwitcher(): AndroidHomeSwitcher {
    val container = LocalAppContainer.current
    val preferred = LocalSettings.current.androidLauncher
    val messages = LocalMessages.current
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    return remember(preferred, activity) {
        AndroidHomeSwitcher(
            resolve = { container.homeMode.preferredLauncher(preferred) },
            list = { container.homeMode.otherLaunchers() },
            launch = { l ->
                val ok = activity != null && container.homeMode.open(activity, l)
                if (!ok) messages.show("Couldn't open ${l.label}", "Pick another launcher in Settings → Home screen.")
            },
            remember = { l -> scope.launch { container.settings.setAndroidLauncher(l.packageName) } },
            onNone = { messages.show("No other launcher", "Android didn't report another home app on this device.") },
        )
    }
}

/** The launcher picker for [switcher]. Picking one remembers it (and opens it, from "Switch to Android home"). */
@Composable
fun AndroidHomePicker(switcher: AndroidHomeSwitcher) {
    val preferred = LocalSettings.current.androidLauncher
    val choices = switcher.choices
    SideMenu(
        visible = choices != null,
        title = "Android home screen",
        subtitle = "The launcher to switch to. Press Home to come back to PocketDeck.",
        startOnChecked = true,
        items = choices.orEmpty().map { l ->
            MenuItem(l.label, Icons.Rounded.Home, subtitle = l.packageName, checked = l.packageName == preferred) {
                switcher.choices = null
                switcher.remember(l)
                if (switcher.openAfterPick) switcher.launch(l)
            }
        },
        onDismiss = { switcher.choices = null },
    )
}
