package com.abidnafi.pocketdeck

import android.content.Context
import com.abidnafi.pocketdeck.data.GameLauncher
import com.abidnafi.pocketdeck.data.LibraryRepository
import com.abidnafi.pocketdeck.data.SettingsRepository
import com.abidnafi.pocketdeck.data.apps.InstalledApps
import com.abidnafi.pocketdeck.data.art.AppIcons
import com.abidnafi.pocketdeck.data.db.AppDatabase
import com.abidnafi.pocketdeck.data.emulators.EmulatorRegistry
import com.abidnafi.pocketdeck.data.model.EmulatorDef
import com.abidnafi.pocketdeck.data.model.Platform
import com.abidnafi.pocketdeck.data.model.PlatformRegistry
import com.abidnafi.pocketdeck.input.UiFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** Hand-rolled dependency container; one per process, owned by [PocketDeckApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val platforms = PlatformRegistry(json.decodeFromString<List<Platform>>(asset("systems.json")))
    val db = AppDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val installedApps = InstalledApps(appContext)
    val emulators = EmulatorRegistry(json.decodeFromString<List<EmulatorDef>>(asset("emulators.json")), installedApps)
    val icons = AppIcons(appContext)
    val feedback = UiFeedback(appContext)

    val library = LibraryRepository(
        context = appContext,
        db = db,
        apps = installedApps,
        emulators = emulators,
        scanner = com.abidnafi.pocketdeck.data.roms.RomScanner(appContext, platforms),
        settings = settings,
        scope = appScope,
    )
    val returnNotifier = com.abidnafi.pocketdeck.data.ReturnNotifier(appContext)
    /** Gaming PCs for streaming (Sunshine hosts). */
    val hosts = com.abidnafi.pocketdeck.data.stream.HostRepository(appContext, db, appScope)
    /** Optional "PocketDeck as home screen" mode. */
    val homeMode = com.abidnafi.pocketdeck.data.HomeMode(appContext)
    val launcher = GameLauncher(db.games(), db.sessions(), settings, installedApps, emulators, platforms, returnNotifier, hosts)

    private fun asset(name: String): String =
        appContext.assets.open(name).bufferedReader().use { it.readText() }
}
