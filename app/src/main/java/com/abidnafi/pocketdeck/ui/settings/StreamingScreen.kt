package com.abidnafi.pocketdeck.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.FramePacing
import com.abidnafi.pocketdeck.data.StreamAudio
import com.abidnafi.pocketdeck.data.StreamCodec
import com.abidnafi.pocketdeck.data.StreamResolution
import com.abidnafi.pocketdeck.data.StreamSettings
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.db.StreamHostEntity
import com.abidnafi.pocketdeck.data.stream.HostRepository
import com.abidnafi.pocketdeck.data.stream.HostStatus
import com.abidnafi.pocketdeck.data.stream.PairCode
import com.abidnafi.pocketdeck.data.stream.PairingState
import com.abidnafi.pocketdeck.BuildConfig
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalMessages
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.OverlayScaffold
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.components.TextEntryOverlay
import com.abidnafi.pocketdeck.ui.components.rememberFocusMemory
import com.abidnafi.pocketdeck.ui.components.tryFocus
import com.abidnafi.pocketdeck.ui.gameCount
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.launch

private enum class StreamPicker { RESOLUTION, FPS, BITRATE, CODEC, AUDIO, PACING }

/** Settings → PC streaming: gaming PCs (Sunshine hosts), pairing, and stream quality. */
@Composable
fun StreamingScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val hostsRepo = container.hosts
    val settings = LocalSettings.current
    val stream = settings.stream
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val hosts by hostsRepo.hosts.collectAsState(emptyList())
    val status by hostsRepo.status.collectAsState()
    val syncing by hostsRepo.syncing.collectAsState()
    val discovering by hostsRepo.discovering.collectAsState()
    val games by container.library.games.collectAsState(emptyList())
    val gamesPerHost = remember(games) { games.filter { it.type == GameType.STREAM }.groupingBy { it.hostId }.eachCount() }
    val memory = rememberFocusMemory()
    val addRow = remember { FocusRequester() }
    var selected by remember { mutableStateOf<StreamHostEntity?>(null) }
    var removing by remember { mutableStateOf<StreamHostEntity?>(null) }
    var addingManual by remember { mutableStateOf(false) }
    var pastingLink by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val canScan = remember { canScanPairCode(context) }
    val scanRow = remember { FocusRequester() }
    var picker by remember { mutableStateOf<StreamPicker?>(null) }

    // Look for PCs while this page is open, and check the known ones.
    DisposableEffect(Unit) {
        hostsRepo.startDiscovery()
        hostsRepo.refreshAll(syncGames = false)
        onDispose { hostsRepo.stopDiscovery() }
    }

    fun update(block: (StreamSettings) -> StreamSettings) {
        scope.launch { container.settings.setStream(block(stream)) }
    }

    fun pair(host: StreamHostEntity) {
        scope.launch { hostsRepo.pair(host) }
    }

    SettingsPage(
        "PC streaming",
        "Play games from your gaming PC on this device. Install Sunshine on the PC, then pair it here. " +
            "Its games show up in your library.",
    ) {
        SectionHeader("Gaming PCs")
        if (canScan) {
            SettingRow(
                "Scan PC's code", { nav.navigate(Routes.SCAN_PAIR) }, icon = Icons.Rounded.QrCodeScanner,
                subtitle = "Pair in one step: open PocketDeck on the PC → Devices → Pair a phone",
                focusRequester = scanRow,
            )
        }
        SettingRow(
            "Add PC by address", { addingManual = true }, icon = Icons.Rounded.Add,
            subtitle = "If your PC isn't found automatically, type its IP address",
            focusRequester = addRow,
        )
        if (BuildConfig.DEBUG) {
            SettingRow(
                "Paste pairing link", { pastingLink = true }, icon = Icons.Rounded.Link,
                subtitle = "Debug builds: test pair with one scan without a camera",
            )
        }
        if (discovering) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalAccent.current)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (hosts.isEmpty()) "Looking for PCs running Sunshine on your network…" else "Looking for more PCs…",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                )
            }
        }
        hosts.forEach { h ->
            val st = status[h.id]
            SettingRow(
                title = h.name,
                subtitle = hostSubtitle(h, st, syncing = h.id in syncing),
                value = if (h.paired) gamesPerHost[h.id]?.let { gameCount(it) } else "Pair",
                icon = Icons.Rounded.Computer,
                onClick = { if (!h.paired && st?.online == true) pair(h) else selected = h },
                focusRequester = memory.requester("h/${h.id}"),
                onFocusChange = { if (it) memory.lastKey = "h/${h.id}" },
            )
        }

        SectionHeader("Quality")
        SettingRow("Resolution", { picker = StreamPicker.RESOLUTION }, icon = Icons.Rounded.AspectRatio, value = stream.resolution.label)
        SettingRow("Frame rate", { picker = StreamPicker.FPS }, icon = Icons.Rounded.Speed, value = "${stream.fps} FPS")
        SettingRow(
            "Bitrate", { picker = StreamPicker.BITRATE }, icon = Icons.Rounded.Wifi,
            value = bitrateLabel(stream.bitrateKbps),
            subtitle = "Higher looks better but needs a faster network",
        )
        SettingRow("Video codec", { picker = StreamPicker.CODEC }, icon = Icons.Rounded.Movie, value = stream.codec.label)
        SettingRow(
            "HDR", { update { it.copy(hdr = !it.hdr) } }, icon = Icons.Rounded.HdrOn, toggle = stream.hdr,
            subtitle = "Needs an HDR screen, an HDR-capable decoder and HDR enabled on the PC",
        )
        SettingRow("Audio", { picker = StreamPicker.AUDIO }, icon = Icons.Rounded.SurroundSound, value = stream.audio.label)
        SettingRow("Frame pacing", { picker = StreamPicker.PACING }, icon = Icons.Rounded.Timer, value = stream.framePacing.label)

        SectionHeader("While streaming")
        SettingRow(
            "Show performance stats", { update { it.copy(showStats = !it.showStats) } }, icon = Icons.Rounded.Insights,
            toggle = stream.showStats, subtitle = "Frame rate, latency and decoder in a corner",
        )
        SettingRow(
            "Play sound on the PC too", { update { it.copy(playHostAudio = !it.playHostAudio) } },
            icon = Icons.AutoMirrored.Rounded.VolumeUp, toggle = stream.playHostAudio,
        )
        SettingRow(
            "Optimise game settings", { update { it.copy(optimizeHostSettings = !it.optimizeHostSettings) } },
            icon = Icons.Rounded.Tune, toggle = stream.optimizeHostSettings,
            subtitle = "Let the PC match its resolution to the stream",
        )
        SettingRow(
            "Touchscreen as trackpad", { update { it.copy(touchAsTrackpad = !it.touchAsTrackpad) } },
            icon = Icons.Rounded.TouchApp, toggle = stream.touchAsTrackpad,
            subtitle = if (stream.touchAsTrackpad) "Drag to move the mouse, tap to click" else "Tap where you want to click",
        )
        SettingRow(
            "Quit game when disconnecting", { update { it.copy(quitOnDisconnect = !it.quitOnDisconnect) } },
            icon = Icons.Rounded.StopCircle, toggle = stream.quitOnDisconnect,
            subtitle = "Off: the game keeps running on the PC so you can resume",
        )
        Text(
            "In a game, press Start + Select + LB + RB together (or use Back) to open the stream menu.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
    InitialFocus(memory, if (canScan) scanRow else addRow)

    // ---- Per-host menu ----
    val sel = selected
    val selStatus = sel?.let { status[it.id] }
    SideMenu(
        visible = sel != null,
        title = sel?.name.orEmpty(),
        subtitle = sel?.let { hostSubtitle(it, selStatus, syncing = it.id in syncing) },
        items = buildList {
            val h = sel ?: return@buildList
            if (!h.paired || selStatus?.online == true && !selStatus.hostSaysPaired) {
                add(MenuItem(if (h.paired) "Pair again" else "Pair", Icons.Rounded.Link, subtitle = "Shows a PIN to enter in Sunshine") {
                    selected = null
                    pair(h)
                })
            }
            if (h.paired) {
                add(MenuItem("Refresh games", Icons.Rounded.Refresh, subtitle = "Get the PC's current game list") {
                    selected = null
                    memory.restore()
                    scope.launch {
                        if (!hostsRepo.syncApps(h)) messages.show("Couldn't refresh ${h.name}", "Make sure it's on and Sunshine is running.")
                    }
                })
            }
            if (selStatus?.online == true && selStatus.runningAppId != 0) {
                add(MenuItem("Quit running game", Icons.Rounded.StopCircle, danger = true) {
                    selected = null
                    memory.restore()
                    scope.launch { hostsRepo.quitApp(h) }
                })
            }
            if (h.macAddress != null && selStatus?.online != true) {
                add(MenuItem("Wake up", Icons.Rounded.PowerSettingsNew, subtitle = "Send a Wake-on-LAN signal") {
                    selected = null
                    memory.restore()
                    scope.launch {
                        val error = hostsRepo.wake(h)
                        if (error != null) messages.show("Couldn't wake ${h.name}", error)
                        else messages.show("Wake-up sent", "${h.name} should turn on in a few seconds if Wake-on-LAN is enabled on it.")
                    }
                })
            }
            add(MenuItem("Remove PC", Icons.Rounded.DeleteOutline, danger = true) { removing = h; selected = null })
        },
        onDismiss = { selected = null; memory.restore() },
    )

    com.abidnafi.pocketdeck.ui.components.DialogOverlay(
        visible = removing != null,
        title = "Remove ${removing?.name.orEmpty()}?",
        body = "Its games disappear from PocketDeck and this device is unpaired from it. Nothing changes on the PC.",
        confirmLabel = "Remove",
        dismissLabel = "Cancel",
        danger = true,
        onConfirm = {
            removing?.let { scope.launch { hostsRepo.remove(it) } }
            removing = null
            addRow.tryFocus()
        },
        onDismiss = { removing = null; memory.restore() },
    )

    TextEntryOverlay(
        visible = addingManual,
        title = "PC address",
        initial = "",
        placeholder = "e.g. 192.168.1.20",
        onDismiss = { addingManual = false; addRow.tryFocus() },
        onDone = { text ->
            addingManual = false
            scope.launch {
                when (val r = hostsRepo.addManual(text)) {
                    is HostRepository.AddResult.Ok -> if (!r.host.paired) hostsRepo.pair(r.host)
                    is HostRepository.AddResult.Error -> messages.show("Couldn't add that PC", r.message)
                }
            }
        },
    )

    TextEntryOverlay(
        visible = pastingLink,
        title = "Pairing link",
        initial = "",
        placeholder = "pocketdeck://pair?v=1&…",
        onDismiss = { pastingLink = false; addRow.tryFocus() },
        onDone = { text ->
            pastingLink = false
            when (val r = PairCode.parse(text)) {
                is PairCode.Parsed.Ok -> hostsRepo.startPairFromScan(r.code)
                PairCode.Parsed.NotPairCode -> messages.show("Not a pairing link", "It should start with pocketdeck://pair")
                is PairCode.Parsed.Invalid -> messages.show("Can't use that link", r.message)
            }
        },
    )

    // ---- Quality pickers ----
    fun close() {
        picker = null
    }
    SideMenu(
        visible = picker == StreamPicker.RESOLUTION, startOnChecked = true, title = "Resolution",
        subtitle = "\"Native\" matches this screen exactly",
        items = StreamResolution.entries.map { r ->
            MenuItem(r.label, subtitle = if (r == StreamResolution.NATIVE) "This device's screen" else "${r.width} × ${r.height}", checked = stream.resolution == r) {
                update { it.copy(resolution = r) }; close()
            }
        },
        onDismiss = ::close,
    )
    SideMenu(
        visible = picker == StreamPicker.FPS, startOnChecked = true, title = "Frame rate",
        items = StreamSettings.FPS_OPTIONS.map { f ->
            MenuItem("$f FPS", checked = stream.fps == f) { update { it.copy(fps = f) }; close() }
        },
        onDismiss = ::close,
    )
    SideMenu(
        visible = picker == StreamPicker.BITRATE, startOnChecked = true, title = "Bitrate",
        subtitle = "Automatic picks a good value for the resolution and frame rate",
        items = StreamSettings.BITRATE_OPTIONS.map { b ->
            MenuItem(bitrateLabel(b), checked = stream.bitrateKbps == b) { update { it.copy(bitrateKbps = b) }; close() }
        },
        onDismiss = ::close,
    )
    SideMenu(
        visible = picker == StreamPicker.CODEC, startOnChecked = true, title = "Video codec",
        subtitle = "Automatic uses the best one this device decodes in hardware",
        items = StreamCodec.entries.map { c ->
            MenuItem(c.label, checked = stream.codec == c) { update { it.copy(codec = c) }; close() }
        },
        onDismiss = ::close,
    )
    SideMenu(
        visible = picker == StreamPicker.AUDIO, startOnChecked = true, title = "Audio",
        items = StreamAudio.entries.map { a ->
            MenuItem(a.label, checked = stream.audio == a) { update { it.copy(audio = a) }; close() }
        },
        onDismiss = ::close,
    )
    SideMenu(
        visible = picker == StreamPicker.PACING, startOnChecked = true, title = "Frame pacing",
        items = FramePacing.entries.map { p ->
            MenuItem(
                p.label,
                subtitle = when (p) {
                    FramePacing.LATENCY -> "Fastest response, may stutter a little"
                    FramePacing.BALANCED -> "Good mix of smoothness and latency"
                    FramePacing.CAP_FPS -> "Caps the stream to this screen's refresh rate"
                    FramePacing.SMOOTHNESS -> "Smoothest motion, slightly more latency"
                },
                checked = stream.framePacing == p,
            ) { update { it.copy(framePacing = p) }; close() }
        },
        onDismiss = ::close,
    )
}

/**
 * Pairing progress, shown over any page: the PIN while the user types it into Sunshine (or, with a
 * scanned code, "Pairing with…" while the PC enters it), then the result. [onScanAgain] opens the scanner.
 */
@Composable
fun PairingOverlay(onScanAgain: (() -> Unit)?) {
    val hostsRepo = LocalAppContainer.current.hosts
    val pairing by hostsRepo.pairing.collectAsState()
    val hosts by hostsRepo.hosts.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val state = pairing
    AnimatedVisibility(state !is PairingState.Idle, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
        OverlayScaffold(
            onDismiss = { hostsRepo.cancelPairingUi() },
            hints = listOf(Hint(Btn.CONFIRM, "Select"), Hint(Btn.BACK, "Close")),
        ) {
            val button = remember { FocusRequester() }
            LaunchedEffect(state) { button.tryFocus() }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 620.dp)
                    .background(Palette.Surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is PairingState.Connecting -> {
                        Text("Looking for ${state.hostName}", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalAccent.current)
                            Spacer(Modifier.width(10.dp))
                            Text("Connecting over your network…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        }
                        Spacer(Modifier.height(20.dp))
                        ConsoleButton("Hide", { hostsRepo.cancelPairingUi() }, focusRequester = button)
                    }
                    is PairingState.Waiting -> if (state.pin == null) {
                        Text("Pairing with ${state.hostName}…", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "PocketDeck on the PC confirms the code by itself. Keep the code on the PC's screen until this finishes.",
                            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                        )
                        Spacer(Modifier.height(18.dp))
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = LocalAccent.current)
                        Spacer(Modifier.height(20.dp))
                        ConsoleButton("Hide", { hostsRepo.cancelPairingUi() }, focusRequester = button)
                    } else {
                        Text("Pair with ${state.hostName}", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "On the PC, open Sunshine's web page (https://localhost:47990), go to the PIN tab and enter:",
                            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            state.pin.toCharArray().joinToString(" "),
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp,
                            color = LocalAccent.current,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalAccent.current)
                            Spacer(Modifier.width(10.dp))
                            Text("Waiting for the PIN to be entered…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        }
                        Spacer(Modifier.height(20.dp))
                        ConsoleButton("Hide", { hostsRepo.cancelPairingUi() }, focusRequester = button)
                    }
                    is PairingState.Done -> {
                        Text("Paired with ${state.hostName}", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Its games are being added to your library. Find them in the PC row on Home or the PC filter in the Library.",
                            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                        )
                        Spacer(Modifier.height(20.dp))
                        ConsoleButton("Done", { hostsRepo.cancelPairingUi() }, primary = true, focusRequester = button)
                    }
                    is PairingState.Failed -> {
                        Text("Pairing didn't work", style = MaterialTheme.typography.headlineMedium, color = Palette.Danger)
                        Spacer(Modifier.height(10.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val host = hosts.firstOrNull { it.id == state.hostId }
                            when {
                                // A scanned code's PIN is used up or stale; the PC has to show a new one.
                                state.fromScan && onScanAgain != null -> {
                                    ConsoleButton("Close", { hostsRepo.cancelPairingUi() })
                                    ConsoleButton(
                                        "Scan again", { hostsRepo.cancelPairingUi(); onScanAgain() },
                                        primary = true, focusRequester = button,
                                    )
                                }
                                !state.fromScan && host != null -> {
                                    ConsoleButton("Close", { hostsRepo.cancelPairingUi() })
                                    ConsoleButton("Try again", { scope.launch { hostsRepo.pair(host) } }, primary = true, focusRequester = button)
                                }
                                else -> ConsoleButton("Close", { hostsRepo.cancelPairingUi() }, focusRequester = button)
                            }
                        }
                    }
                    PairingState.Idle -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun hostSubtitle(h: StreamHostEntity, st: HostStatus?, syncing: Boolean): String = buildString {
    append(
        when {
            st == null -> "Checking…"
            !st.online -> "Offline"
            !h.paired -> "Online · not paired, select to pair"
            !st.hostSaysPaired -> "Online · pairing was removed on the PC"
            st.runningAppId != 0 -> "Online · a game is running"
            else -> "Online · paired"
        },
    )
    val addr = st?.address ?: h.addresses.firstOrNull()
    if (addr != null) append(" · ").append(addr)
    if (syncing) append(" · updating games…")
}

private fun bitrateLabel(kbps: Int) = if (kbps == 0) "Automatic" else "${kbps / 1000} Mbps"
