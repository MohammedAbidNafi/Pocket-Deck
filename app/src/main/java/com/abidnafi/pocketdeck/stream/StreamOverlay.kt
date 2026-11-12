package com.abidnafi.pocketdeck.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.input.Priority
import com.abidnafi.pocketdeck.ui.components.DialogOverlay
import com.abidnafi.pocketdeck.ui.components.HintBar
import com.abidnafi.pocketdeck.ui.components.MenuItem
import com.abidnafi.pocketdeck.ui.components.SideMenu
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.delay

/** Everything PocketDeck draws over the video. Plain Compose state, written from the activity. */
class StreamUiState {
    var title by mutableStateOf("")
    var hostName by mutableStateOf("")
    /** Non-null while connecting: the current step. */
    var connecting by mutableStateOf<String?>("Getting ready…")
    var error by mutableStateOf<Pair<String, String>?>(null)
    /** Another app is running on the PC: its name, until the user decides. */
    var otherAppRunning by mutableStateOf<String?>(null)
    var menuOpen by mutableStateOf(false)
    var confirmQuit by mutableStateOf(false)
    var stats by mutableStateOf<String?>(null)
    var showStats by mutableStateOf(false)
    var poorConnection by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var noticeId by mutableStateOf(0)

    /** True when PocketDeck's UI, not the PC, should get the controller. */
    val capturesInput: Boolean get() = connecting != null || error != null || otherAppRunning != null || menuOpen || confirmQuit

    fun showNotice(text: String) {
        notice = text
        noticeId++
    }
}

class StreamActions(
    val onResume: () -> Unit,
    val onDisconnect: () -> Unit,
    val onQuitGame: () -> Unit,
    val onToggleStats: () -> Unit,
    val onKeyboard: () -> Unit,
    val onCloseError: () -> Unit,
    val onQuitOtherAndStart: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
fun StreamOverlay(ui: StreamUiState, actions: StreamActions) {
    Box(Modifier.fillMaxSize()) {
        // Performance stats (top left), same numbers upstream Moonlight shows, drawn by PocketDeck.
        val stats = ui.stats
        if (ui.showStats && stats != null && ui.connecting == null) {
            Text(
                stats,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            ui.poorConnection && ui.connecting == null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Pill("Weak connection", Palette.Danger, Modifier.padding(16.dp))
        }

        // Transient notices (replaces the engine's toasts).
        var visibleNotice by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
        LaunchedEffect(ui.noticeId) {
            val text = ui.notice ?: return@LaunchedEffect
            visibleNotice = text
            delay(3000)
            visibleNotice = null
        }
        AnimatedVisibility(
            visibleNotice != null,
            enter = fadeIn(), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Pill(visibleNotice.orEmpty(), Palette.SurfaceHigher, Modifier.padding(bottom = 32.dp))
        }

        ui.connecting?.let { stage -> ConnectingCard(ui.title, ui.hostName, stage, actions.onCancel) }

        SideMenu(
            visible = ui.menuOpen && !ui.confirmQuit,
            title = ui.title,
            subtitle = "Streaming from ${ui.hostName}",
            items = listOf(
                MenuItem("Resume", Icons.Rounded.PlayArrow, onClick = actions.onResume),
                MenuItem("Keyboard", Icons.Rounded.Keyboard, subtitle = "Type on the PC", onClick = actions.onKeyboard),
                MenuItem(
                    if (ui.showStats) "Hide stats" else "Show stats",
                    Icons.Rounded.Insights,
                    subtitle = "Frame rate, latency and decoder",
                    onClick = actions.onToggleStats,
                ),
                MenuItem(
                    "Disconnect",
                    Icons.AutoMirrored.Rounded.ExitToApp,
                    subtitle = "The game keeps running on the PC",
                    onClick = actions.onDisconnect,
                ),
                MenuItem("Quit game", Icons.Rounded.StopCircle, subtitle = "Closes it on the PC", danger = true) { ui.confirmQuit = true },
            ),
            onDismiss = actions.onResume,
        )

        DialogOverlay(
            visible = ui.confirmQuit,
            title = "Quit ${ui.title}?",
            body = "The game is closed on ${ui.hostName}. Unsaved progress is lost.",
            confirmLabel = "Quit game",
            danger = true,
            onConfirm = actions.onQuitGame,
            onDismiss = { ui.confirmQuit = false },
            dismissLabel = "Cancel",
        )

        DialogOverlay(
            visible = ui.otherAppRunning != null,
            title = "${ui.otherAppRunning} is running",
            body = "${ui.hostName} is already running ${ui.otherAppRunning}. Quit it and start ${ui.title}?",
            confirmLabel = "Quit and start",
            danger = true,
            onConfirm = actions.onQuitOtherAndStart,
            onDismiss = actions.onCancel,
            dismissLabel = "Cancel",
        )

        val error = ui.error
        DialogOverlay(
            visible = error != null,
            title = error?.first.orEmpty(),
            body = error?.second.orEmpty(),
            onConfirm = actions.onCloseError,
            onDismiss = actions.onCloseError,
        )

        if (ui.capturesInput) HintBar(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ConnectingCard(title: String, hostName: String, stage: String, onCancel: () -> Unit) {
    ButtonHints(Hint(Btn.BACK, "Cancel"), priority = Priority.SCREEN)
    androidx.activity.compose.BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .background(Palette.Surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 36.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = LocalAccent.current, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
            if (hostName.isNotEmpty()) {
                Text("on $hostName", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
            Spacer(Modifier.height(14.dp))
            Text(stage, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Text(
                    "In game, press Start + Select + LB + RB together for the menu",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(color.copy(alpha = 0.92f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
