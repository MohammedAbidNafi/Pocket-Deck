package com.abidnafi.pocketdeck.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.abidnafi.pocketdeck.data.db.GameDao
import com.abidnafi.pocketdeck.data.db.PlaySessionDao
import com.abidnafi.pocketdeck.data.db.PlaySessionEntity
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.emulators.EmulatorRegistry
import com.abidnafi.pocketdeck.data.emulators.LaunchSpec
import com.abidnafi.pocketdeck.data.emulators.LaunchTemplates
import com.abidnafi.pocketdeck.data.emulators.SpecResult
import com.abidnafi.pocketdeck.data.model.EmulatorDef
import com.abidnafi.pocketdeck.data.model.PlatformRegistry
import com.abidnafi.pocketdeck.data.apps.InstalledApps
import com.abidnafi.pocketdeck.data.stream.HostRepository
import com.abidnafi.pocketdeck.stream.StreamActivity

sealed interface LaunchResult {
    data object Ok : LaunchResult
    data class Error(val title: String, val message: String) : LaunchResult
}

/** Starts games and keeps per-game play statistics. */
class GameLauncher(
    private val games: GameDao,
    private val sessions: PlaySessionDao,
    private val settings: SettingsRepository,
    private val apps: InstalledApps,
    private val emulators: EmulatorRegistry,
    private val platforms: PlatformRegistry,
    private val notifier: ReturnNotifier,
    private val hosts: HostRepository,
) {
    /**
     * [trackSession]: time the session until the user comes back to PocketDeck. Off for launches
     * from the home-screen widget, where the user returns to their launcher instead.
     */
    suspend fun launch(activity: Activity, game: GameEntity, trackSession: Boolean = true): LaunchResult {
        val result = when (game.type) {
            GameType.APP, GameType.EMULATOR -> launchPackage(activity, game)
            GameType.ROM -> launchRom(activity, game)
            GameType.STREAM -> launchStream(activity, game)
        }
        if (result == LaunchResult.Ok) {
            val now = System.currentTimeMillis()
            games.markLaunched(game.id, now)
            // Streams play inside PocketDeck, so they're always timed until the stream screen closes.
            val timed = trackSession || game.type == GameType.STREAM
            sessions.insert(PlaySessionEntity(gameId = game.id, startedAt = now, from = if (trackSession) "app" else "widget"))
            if (timed) {
                settings.setPendingSession(game.id, now)
                if (game.type != GameType.STREAM && settings.current().returnNotification) notifier.show(game.title)
            }
        }
        return result
    }

    private suspend fun launchStream(activity: Activity, game: GameEntity): LaunchResult {
        val host = game.hostId?.let { hosts.host(it) }
            ?: return LaunchResult.Error("PC not found", "The PC this game was on has been removed. Add it again in Settings → PC streaming.")
        if (game.streamAppId == null) return LaunchResult.Error("Can't stream", "This entry has no app on ${host.name}.")
        if (!host.paired) {
            return LaunchResult.Error("Not paired with ${host.name}", "Pair with it first in Settings → PC streaming.")
        }
        hosts.target(host) ?: return LaunchResult.Error(
            "${host.name} is offline",
            "Turn the PC on and make sure Sunshine is running." +
                if (host.macAddress != null) " You can also wake it from the game's menu or Settings → PC streaming." else "",
        )
        return start(activity, StreamActivity.intent(activity, game.id), game.title)
    }

    suspend fun gameById(id: Long): GameEntity? = games.get(id)

    /** Called whenever this app comes back to the foreground: closes the open play session. */
    suspend fun onReturnedToApp() {
        notifier.hide()
        val (gameId, start) = settings.pendingSession() ?: return
        settings.setPendingSession(null, 0)
        val elapsed = System.currentTimeMillis() - start
        if (elapsed in MIN_SESSION_MS..MAX_SESSION_MS) {
            games.addPlayTime(gameId, elapsed)
            sessions.setDuration(gameId, start, elapsed)
        }
    }

    /** Emulator that would run [game] right now, or null. */
    suspend fun resolveEmulator(game: GameEntity): EmulatorDef? {
        val platformId = game.platformId ?: return null
        val configured = game.emulatorOverride ?: settings.current().defaultEmulators[platformId]
        return emulators.defaultFor(platformId, configured)
    }

    private fun launchPackage(activity: Activity, game: GameEntity): LaunchResult {
        val pkg = game.packageName ?: return LaunchResult.Error("Can't launch", "This entry has no app attached.")
        val intent = apps.launchIntent(pkg)
            ?: return LaunchResult.Error("Not installed", "${game.title} ($pkg) is no longer installed.")
        return start(activity, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), game.title)
    }

    private suspend fun launchRom(activity: Activity, game: GameEntity): LaunchResult {
        val uri = game.romUri ?: return LaunchResult.Error("Missing file", "This ROM has no file attached.")
        val platform = platforms[game.platformId]
        val emulator = resolveEmulator(game)
            ?: return LaunchResult.Error(
                "No emulator for ${platform?.shortName ?: "this system"}",
                "Install an emulator that supports ${platform?.name ?: "this system"} " +
                    "(for example ${emulators.forPlatform(game.platformId.orEmpty()).take(3).joinToString { it.name }.ifEmpty { "RetroArch" }}), " +
                    "or pick one in Settings → Emulators.",
            )

        if (emulator.id == EmulatorDef.SYSTEM_CHOOSER) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uri), "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            return start(activity, Intent.createChooser(view, "Play ${game.title} with…"), game.title)
        }

        val pkg = emulators.installedPackage(emulator)
            ?: return LaunchResult.Error("${emulator.name} isn't installed", "Install it or choose another emulator for this game.")
        val spec = when (val r = LaunchTemplates.build(emulator, pkg, uri, game.romPath, platform?.retroarchCore)) {
            is SpecResult.Error -> return LaunchResult.Error("Can't start ${emulator.name}", r.message)
            is SpecResult.Ok -> r.spec
        }
        val romUri = Uri.parse(spec.grantUri)
        runCatching {
            activity.grantUriPermission(pkg, romUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        return start(activity, spec.toIntent(romUri), "${game.title} in ${emulator.name}", emulator)
    }

    private fun LaunchSpec.toIntent(romUri: Uri): Intent {
        // Explicit receivers: Intent has its own data/extras/flags members that would shadow the spec's.
        val spec = this
        val intent = Intent(spec.action)
        if (spec.className != null) intent.setClassName(spec.packageName, spec.className) else intent.setPackage(spec.packageName)
        val dataUri = spec.data?.let { Uri.parse(it) }
        when {
            dataUri != null && spec.mimeType != null -> intent.setDataAndType(dataUri, spec.mimeType)
            dataUri != null -> intent.setData(dataUri)
        }
        spec.extras.forEach { (k, v) -> intent.putExtra(k, v) }
        intent.clipData = ClipData.newRawUri("rom", romUri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if ("clear_task" in spec.flags) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if ("clear_top" in spec.flags) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if ("no_history" in spec.flags) intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        return intent
    }

    private fun start(activity: Activity, intent: Intent, what: String, emulator: EmulatorDef? = null): LaunchResult =
        try {
            activity.startActivity(intent)
            LaunchResult.Ok
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Error("Couldn't start $what", mismatchHint(emulator) ?: "The app refused to open.")
        } catch (e: SecurityException) {
            LaunchResult.Error("Couldn't start $what", mismatchHint(emulator) ?: "Android blocked the launch: ${e.message}")
        }

    private fun mismatchHint(emulator: EmulatorDef?): String? = emulator?.let {
        "The installed version of ${it.name} doesn't accept the launch settings PocketDeck uses. " +
            "Try another emulator, or create a custom emulator entry in Settings → Emulators."
    }

    private companion object {
        const val MIN_SESSION_MS = 10_000L
        const val MAX_SESSION_MS = 12 * 60 * 60 * 1000L
    }
}
