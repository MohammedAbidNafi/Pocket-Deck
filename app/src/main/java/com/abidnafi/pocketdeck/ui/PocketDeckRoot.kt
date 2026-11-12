package com.abidnafi.pocketdeck.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abidnafi.pocketdeck.AppContainer
import com.abidnafi.pocketdeck.data.Settings
import com.abidnafi.pocketdeck.input.ControllerDispatcher
import com.abidnafi.pocketdeck.input.HintRegistry
import com.abidnafi.pocketdeck.input.LocalController
import com.abidnafi.pocketdeck.input.LocalHints
import com.abidnafi.pocketdeck.ui.addapp.AddAppScreen
import com.abidnafi.pocketdeck.ui.collections.CollectionDetailScreen
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.HintBar
import com.abidnafi.pocketdeck.ui.details.GameDetailsScreen
import com.abidnafi.pocketdeck.ui.onboarding.OnboardingScreen
import com.abidnafi.pocketdeck.ui.search.SearchScreen
import com.abidnafi.pocketdeck.ui.settings.EmulatorsScreen
import com.abidnafi.pocketdeck.ui.settings.RomFoldersScreen
import com.abidnafi.pocketdeck.ui.settings.SettingsScreen
import com.abidnafi.pocketdeck.ui.shell.MainShell
import com.abidnafi.pocketdeck.ui.theme.PocketDeckTheme
import com.abidnafi.pocketdeck.ui.theme.Palette

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val DETAILS = "details/{id}"
    const val COLLECTION = "collection/{id}"
    const val COLLECTION_ADD = "collection/{id}/add?new={new}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ROM_FOLDERS = "settings/roms"
    const val EMULATORS = "settings/emulators"
    const val ADD_APP = "settings/add-app"
    const val ABOUT = "settings/about"
    const val RETURN_SHORTCUTS = "settings/return"
    const val HOME_ROWS = "settings/home-rows"
    const val STREAMING = "settings/streaming"
    const val AUTO_COLLECTION = "auto-collection/{key}"

    fun details(id: Long) = "details/$id"
    fun collection(id: Long) = "collection/$id"
    fun autoCollection(key: String) = "auto-collection/${android.net.Uri.encode(key)}"
    fun collectionAdd(id: Long, justCreated: Boolean = false) = "collection/$id/add?new=$justCreated"
}

@Composable
fun PocketDeckRoot(container: AppContainer, controller: ControllerDispatcher, hints: HintRegistry) {
    val settings by container.settings.settings.collectAsState(initial = null)
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Palette.Background))
        return
    }
    val messages = remember { MessageCenter() }
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalController provides controller,
        LocalHints provides hints,
        LocalSettings provides s,
        LocalFeedback provides container.feedback,
        LocalMessages provides messages,
        LocalArtNetwork provides rememberArtNetworkAllowed(s),
    ) {
        PocketDeckTheme(Palette.accents.getOrElse(s.accentIndex) { Palette.accents[0] }.color) {
            Box(Modifier.fillMaxSize().background(Palette.Background)) {
                val nav = rememberNavController()
                // Home screen mode: pressing Home from any page returns to the main screen.
                LaunchedEffect(Unit) {
                    var seen = container.homeMode.homePresses.value
                    container.homeMode.homePresses.collect { n ->
                        if (n != seen) {
                            seen = n
                            runCatching { nav.popBackStack(Routes.MAIN, inclusive = false) }
                        }
                    }
                }
                val start = remember { if (s.onboardingDone) Routes.MAIN else Routes.ONBOARDING }
                NavHost(
                    navController = nav,
                    startDestination = start,
                    enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 1.03f) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.03f) },
                ) {
                    // Setup ends in a flash of light; the glow fades slowly while Home zooms in out of it.
                    composable(
                        Routes.ONBOARDING,
                        exitTransition = {
                            if (targetState.destination.route == Routes.MAIN) fadeOut(tween(900, delayMillis = 150)) else fadeOut(tween(160))
                        },
                    ) { OnboardingScreen(nav) }
                    composable(
                        Routes.MAIN,
                        enterTransition = {
                            if (initialState.destination.route == Routes.ONBOARDING) {
                                fadeIn(tween(500)) + scaleIn(tween(1100, easing = FastOutSlowInEasing), initialScale = 1.35f)
                            } else {
                                fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 1.03f)
                            }
                        },
                    ) { MainShell(nav) }
                    composable(Routes.DETAILS, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        GameDetailsScreen(it.arguments?.getLong("id") ?: 0L, nav)
                    }
                    composable(Routes.COLLECTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        CollectionDetailScreen(it.arguments?.getLong("id") ?: 0L, nav)
                    }
                    composable(
                        Routes.COLLECTION_ADD,
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("new") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) {
                        com.abidnafi.pocketdeck.ui.collections.CollectionAddGamesScreen(
                            it.arguments?.getLong("id") ?: 0L, it.arguments?.getBoolean("new") ?: false, nav,
                        )
                    }
                    composable(Routes.SEARCH) { SearchScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable(Routes.ROM_FOLDERS) { RomFoldersScreen(nav) }
                    composable(Routes.EMULATORS) { EmulatorsScreen(nav) }
                    composable(Routes.ADD_APP) { AddAppScreen(nav) }
                    composable(Routes.ABOUT) { com.abidnafi.pocketdeck.ui.settings.AboutScreen(nav) }
                    composable(Routes.HOME_ROWS) { com.abidnafi.pocketdeck.ui.settings.HomeRowsScreen(nav) }
                    composable(Routes.RETURN_SHORTCUTS) { com.abidnafi.pocketdeck.ui.settings.ReturnShortcutsScreen(nav) }
                    composable(Routes.STREAMING) { com.abidnafi.pocketdeck.ui.settings.StreamingScreen(nav) }
                    composable(Routes.AUTO_COLLECTION) {
                        com.abidnafi.pocketdeck.ui.collections.AutoCollectionDetailScreen(it.arguments?.getString("key").orEmpty(), nav)
                    }
                }

                HintBar(Modifier.align(Alignment.BottomCenter))

                val message = messages.current
                DialogOverlay(
                    visible = message != null,
                    title = message?.title.orEmpty(),
                    body = message?.body.orEmpty(),
                    onConfirm = messages::dismiss,
                    onDismiss = messages::dismiss,
                )
            }
        }
    }
    LaunchedEffect(Unit) { container.library.syncInstalledApps() }
}

/** Online artwork switch + "Wi-Fi only" (unmetered) check, tracked live. */
@Composable
private fun rememberArtNetworkAllowed(settings: Settings): Boolean {
    val context = LocalContext.current
    var unmetered by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        unmetered = !cm.isActiveNetworkMetered
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return settings.onlineArtwork && (!settings.wifiOnlyArtwork || unmetered)
}
