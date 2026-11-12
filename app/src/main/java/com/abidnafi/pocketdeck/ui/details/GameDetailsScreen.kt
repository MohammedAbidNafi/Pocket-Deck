package com.abidnafi.pocketdeck.ui.details

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.db.PlaySessionEntity
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.GameMenuHost
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.EdgeSnapBringIntoViewSpec
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.GameArt
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.tryFocus
import com.abidnafi.pocketdeck.ui.formatLastPlayed
import com.abidnafi.pocketdeck.ui.formatPlaytime
import com.abidnafi.pocketdeck.ui.home.Backdrop
import com.abidnafi.pocketdeck.ui.rememberGameMenu
import com.abidnafi.pocketdeck.ui.rememberGameStarter
import com.abidnafi.pocketdeck.ui.sourceLabel
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Game info: the game's page with Play / favourite / options, then everything PocketDeck knows
 * about it: play time and launches, session stats, a 14-day activity chart, recent sessions,
 * and details about the app or ROM file. Opened from "Game info" in a game's menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameDetailsScreen(gameId: Long, nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val game by remember(gameId) { container.library.game(gameId) }.collectAsState(null)
    val sessions by remember(gameId) { container.db.sessions().observeFor(gameId) }.collectAsState(emptyList())
    val allGames by container.library.games.collectAsState(emptyList())
    val collections by container.library.collections.collectAsState(emptyList())
    val memberOf by remember(gameId) { container.library.collectionsFor(gameId) }.collectAsState(emptyList())
    val start = rememberGameStarter()
    val menu = rememberGameMenu()
    val scope = rememberCoroutineScope()
    val play = remember { FocusRequester() }
    // Leave the page if the game is removed while it is open.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(game) {
        if (game != null) seen = true else if (seen) nav.popBackStack()
    }
    val g = game ?: return

    val platform = container.platforms[g.platformId]
    val hosts by container.hosts.hosts.collectAsState(emptyList())
    // PC games show their host's name where ROMs show their system.
    val placeName = if (g.type == GameType.STREAM) hosts.firstOrNull { it.id == g.hostId }?.name else platform?.name
    val emulatorName by produceState<String?>(null, g.emulatorOverride, g.platformId) {
        value = container.launcher.resolveEmulator(g)?.name
    }
    val about by produceState<List<Pair<String, String>>>(emptyList(), g, emulatorName, memberOf, collections) {
        value = aboutRows(context, g, placeName, emulatorName, collections.filter { it.collection.id in memberOf }.map { it.collection.name })
    }
    val stats = remember(g, sessions, allGames) { computeStats(g, sessions, allGames) }

    ControllerHandler { btn ->
        when (btn) {
            Btn.X -> { menu.open(g); true }
            Btn.Y -> { scope.launch { container.library.setFavorite(g.id, !g.favorite) }; true }
            else -> false
        }
    }
    ButtonHints(Hint(Btn.CONFIRM, "Select"), Hint(Btn.X, "Options"), Hint(Btn.Y, "Favourite"), Hint(Btn.BACK, "Back"))

    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val coverW = (screenH * 0.36f).coerceIn(130.dp, 300.dp)
    val scroll = rememberScrollState()
    val margin = with(LocalDensity.current) { 64.dp.toPx() }
    val spec = remember(margin, scroll) {
        EdgeSnapBringIntoViewSpec(margin, scrolled = { scroll.value.toFloat() }, remaining = { (scroll.maxValue - scroll.value).toFloat() })
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(g)
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp, bottom = 96.dp),
            ) {
                // --- Header: art, title, actions, headline numbers ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameArt(
                        g, ArtStyle.COVER,
                        Modifier
                            .size(coverW, coverW * 4f / 3f)
                            .shadow(30.dp, RoundedCornerShape(18.dp))
                            .background(Palette.SurfaceHigh, RoundedCornerShape(18.dp)),
                    )
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            sourceLabel(g, placeName).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(LocalAccent.current.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(g.title, style = MaterialTheme.typography.displayMedium, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${formatLastPlayed(g.lastPlayedAt)} · ${if (g.playTimeMs > 0) formatPlaytime(g.playTimeMs) + " played" else "No play time yet"}",
                            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ConsoleButton("Play", { start(g) }, icon = Icons.Rounded.PlayArrow, primary = true, focusRequester = play)
                            ConsoleButton(
                                if (g.favorite) "Favourite" else "Add to favourites",
                                { scope.launch { container.library.setFavorite(g.id, !g.favorite) } },
                                icon = if (g.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            )
                            ConsoleButton("More", { menu.open(g) }, icon = Icons.Rounded.MoreHoriz)
                        }
                    }
                }

                // --- Play stats ---
                Section("Your stats") {
                    StatGrid(
                        listOf(
                            "Total play time" to (if (g.playTimeMs > 0) formatPlaytime(g.playTimeMs) else "—"),
                            "Times launched" to "${g.playCount}",
                            "Last played" to formatLastPlayed(g.lastPlayedAt),
                            "First played" to (stats.firstPlayed?.let(::formatDate) ?: "—"),
                            "Average session" to (stats.averageMs?.let(::formatPlaytime) ?: "—"),
                            "Longest session" to (stats.longestMs?.let(::formatPlaytime) ?: "—"),
                            "Past 7 days" to (if (stats.last7Ms > 0) formatPlaytime(stats.last7Ms) else "—"),
                            "Days played" to (if (stats.daysPlayed > 0) "${stats.daysPlayed}" else "—"),
                            "Play streak" to when (stats.streak) {
                                0 -> "—"
                                1 -> "1 day"
                                else -> "${stats.streak} days in a row"
                            },
                            "Usually plays" to (stats.timeOfDay ?: "—"),
                            "Rank in library" to (stats.rank?.let { "#$it of ${stats.rankedCount}" } ?: "—"),
                            "Share of play time" to (stats.sharePercent?.let { "$it%" } ?: "—"),
                        ),
                    )
                    if (sessions.isEmpty() && g.playCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Session history starts from this version, so some numbers only count newer sessions.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint,
                        )
                    }
                }

                // --- Activity chart ---
                Section("Last 14 days") {
                    ActivityChart(stats.lastDays)
                }

                // --- Session history ---
                Section("Recent sessions") {
                    if (sessions.isEmpty()) {
                        Text("No sessions yet. Play it from PocketDeck and each session shows up here.", style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
                    } else {
                        sessions.take(12).forEach { SessionRow(it) }
                        if (sessions.size > 12) {
                            Text("+ ${sessions.size - 12} older sessions", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }

                // --- About ---
                Section(when (g.type) { GameType.ROM -> "About this ROM"; GameType.STREAM -> "About this PC game"; else -> "About this app" }) {
                    about.forEach { (label, value) -> InfoRow(label, value) }
                }
            }
        }
        InitialFocus(null, play)
        GameMenuHost(menu, onClosed = { play.tryFocus() })
    }
}

/** A titled block. The whole block takes focus so the D-pad can scroll the page section by section. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(26.dp))
    FocusCard(onClick = {}, corner = 22.dp, focusScale = 1.01f, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface.copy(alpha = 0.82f))
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun StatGrid(stats: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> Stat(label, value, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Bars of play time per day, oldest left; today highlighted. */
@Composable
private fun ActivityChart(days: List<Pair<LocalDate, Long>>) {
    val accent = LocalAccent.current
    val max = days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    if (days.all { it.second == 0L }) {
        Text("No timed play in the last two weeks.", style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
        return
    }
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.58f
            days.forEachIndexed { i, (_, ms) ->
                val h = if (ms == 0L) 3.dp.toPx() else (size.height * ms / max).coerceAtLeast(4.dp.toPx())
                val today = i == days.lastIndex
                drawRoundRect(
                    color = if (ms == 0L) Color.White.copy(alpha = 0.08f) else if (today) Color.White else accent,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 3),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, (date, _) ->
                Text(
                    if (i == days.lastIndex) "Today" else date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == days.lastIndex) Palette.Text else Palette.TextFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Most in a day: ${formatPlaytime(max)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

@Composable
private fun SessionRow(session: PlaySessionEntity) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (session.from == "widget") Icons.Rounded.Widgets else Icons.Rounded.SportsEsports, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(session.startedAt), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(
                if (session.from == "widget") "Started from the home-screen widget" else "Started from PocketDeck",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
            )
        }
        Text(
            if (session.durationMs > 0) formatPlaytime(session.durationMs) else "Not timed",
            style = MaterialTheme.typography.titleSmall,
            color = if (session.durationMs > 0) Palette.Text else Palette.TextFaint,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.widthIn(min = 170.dp, max = 220.dp))
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
    }
}

// ---- Numbers -------------------------------------------------------------------------------

private class GameStats(
    val firstPlayed: Long?,
    val averageMs: Long?,
    val longestMs: Long?,
    val last7Ms: Long,
    val daysPlayed: Int,
    val streak: Int,
    val timeOfDay: String?,
    val rank: Int?,
    val rankedCount: Int,
    val sharePercent: Int?,
    val lastDays: List<Pair<LocalDate, Long>>,
)

private fun computeStats(game: GameEntity, sessions: List<PlaySessionEntity>, all: List<GameEntity>): GameStats {
    val zone = ZoneId.systemDefault()
    fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val now = System.currentTimeMillis()
    val today = LocalDate.now(zone)
    val timed = sessions.filter { it.durationMs > 0 }

    val playDays = sessions.map { day(it.startedAt) }.toSet()
    // Consecutive days with a session, ending today (or yesterday, so the streak isn't lost before you play today).
    var streak = 0
    var cursor = if (today in playDays) today else today.minusDays(1)
    while (cursor in playDays) {
        streak++
        cursor = cursor.minusDays(1)
    }

    val perDay = timed.groupBy { day(it.startedAt) }.mapValues { (_, list) -> list.sumOf { it.durationMs } }
    val lastDays = (13 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (perDay[it] ?: 0L) }

    val timeOfDay = sessions.takeIf { it.size >= 2 }?.groupBy {
        when (Instant.ofEpochMilli(it.startedAt).atZone(zone).hour) {
            in 5..11 -> "In the morning"
            in 12..16 -> "In the afternoon"
            in 17..21 -> "In the evening"
            else -> "Late at night"
        }
    }?.maxByOrNull { it.value.size }?.key

    val ranked = all.filter { !it.hidden && it.playTimeMs > 0 }.sortedByDescending { it.playTimeMs }
    val rank = ranked.indexOfFirst { it.id == game.id }.takeIf { it >= 0 }?.plus(1)
    val total = ranked.sumOf { it.playTimeMs }
    val share = if (total > 0 && game.playTimeMs > 0) ((game.playTimeMs * 100.0 / total).toInt()).coerceAtLeast(1) else null

    return GameStats(
        firstPlayed = sessions.minOfOrNull { it.startedAt },
        averageMs = timed.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.durationMs } / list.size },
        longestMs = timed.maxOfOrNull { it.durationMs },
        last7Ms = timed.filter { now - it.startedAt <= TimeUnit.DAYS.toMillis(7) }.sumOf { it.durationMs },
        daysPlayed = playDays.size,
        streak = streak,
        timeOfDay = timeOfDay,
        rank = rank,
        rankedCount = ranked.size,
        sharePercent = share,
        lastDays = lastDays,
    )
}

private fun formatDate(t: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
private fun formatDateTime(t: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

/** Facts about the app or ROM file behind the game. */
private suspend fun aboutRows(
    context: Context,
    game: GameEntity,
    platformName: String?,
    emulatorName: String?,
    collections: List<String>,
): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    buildList {
        add("Type" to sourceLabel(game, platformName))
        when (game.type) {
            GameType.ROM -> {
                platformName?.let { add("System" to it) }
                add("Emulator" to (emulatorName ?: "None installed"))
                game.fileName?.let { add("File" to it) }
                val location = game.romPath ?: game.romUri
                location?.let { add("Location" to it) }
                game.romUri?.let { uri ->
                    runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri))?.length() }.getOrNull()
                        ?.takeIf { it > 0 }?.let { add("File size" to Formatter.formatShortFileSize(context, it)) }
                }
            }
            GameType.STREAM -> {
                platformName?.let { add("PC" to it) }
                add("Streams with" to "Moonlight engine · GameStream protocol")
            }
            else -> game.packageName?.let { pkg ->
                add("Package" to pkg)
                val pm = context.packageManager
                runCatching {
                    val info = pm.getPackageInfo(pkg, 0)
                    info.versionName?.let { add("Version" to it) }
                    add("Installed" to formatDate(info.firstInstallTime))
                    add("Last updated" to formatDate(info.lastUpdateTime))
                    val installer = if (Build.VERSION.SDK_INT >= 30) {
                        pm.getInstallSourceInfo(pkg).installingPackageName
                    } else {
                        @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
                    }
                    val installerName = installer?.let { p ->
                        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrNull() ?: p
                    }
                    add("Installed from" to (installerName ?: "Unknown (sideloaded)"))
                }.onFailure { if (it is PackageManager.NameNotFoundException) add("Status" to "Not installed any more") }
            }
        }
        add("Added to PocketDeck" to formatDate(game.addedAt))
        add("Collections" to collections.ifEmpty { listOf("None") }.joinToString())
        add("Favourite" to if (game.favorite) "Yes" else "No")
        if (game.hidden) add("Hidden" to "Yes, it's hidden from your library")
    }
}
