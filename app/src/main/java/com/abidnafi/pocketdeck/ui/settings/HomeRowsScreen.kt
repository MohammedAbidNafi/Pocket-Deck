package com.abidnafi.pocketdeck.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalMessages
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.SectionHeader
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.home.ShelfModel
import com.abidnafi.pocketdeck.ui.home.allHomeRows
import com.abidnafi.pocketdeck.ui.home.orderHomeRows
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Reorder and show/hide the rows on the Home screen. Controller: A picks a row up, the D-pad
 * moves it, A (or B) drops it; Y shows/hides. Touch: the arrow and eye buttons on each row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRowsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val repo = container.settings
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val games by container.library.games.collectAsState(emptyList())
    val collections by container.library.collections.collectAsState(emptyList())
    val memberships by container.library.memberships.collectAsState(emptyList())
    val rows = remember(games, collections, memberships) {
        allHomeRows(games, collections, memberships, container.platforms.platforms.map { it.id to it.name })
    }
    val byId = remember(rows) { rows.associateBy { it.id } }

    // Working order, kept locally so moves feel instant; saved after every change.
    val order = remember { mutableStateListOf<String>() }
    LaunchedEffect(rows.map { it.id }.toSet()) {
        val ids = orderHomeRows(rows, settings.homeRowOrder).map { it.id }
        order.clear()
        order.addAll(ids)
    }

    var moving by remember { mutableStateOf<String?>(null) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    fun save() {
        val snapshot = order.toList()
        scope.launch { repo.setHomeRowOrder(snapshot) }
    }

    fun move(id: String, delta: Int) {
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.removeAt(from)
        order.add(to, id)
        save()
    }

    fun toggleHidden(id: String) {
        val hidden = id in settings.hiddenHomeRows
        if (!hidden && order.count { it !in settings.hiddenHomeRows } <= 1) {
            messages.show("Keep one row", "Home needs at least one row to show.")
            return
        }
        scope.launch { repo.setHomeRowHidden(id, !hidden) }
    }

    BackHandler(enabled = moving != null) { moving = null }
    ControllerHandler { btn ->
        when (btn) {
            Btn.Y -> {
                val id = focusedId ?: return@ControllerHandler false
                toggleHidden(id); true
            }
            else -> false
        }
    }

    SettingsPage("Home rows", "Pick the order of the rows on Home, and hide the ones you don't want.") {
        if (moving != null) {
            ButtonHints(Hint(Btn.CONFIRM, "Drop"), Hint(Btn.BACK, "Drop"))
        } else {
            ButtonHints(Hint(Btn.CONFIRM, "Move"), Hint(Btn.Y, "Show / hide"), Hint(Btn.BACK, "Back"))
        }
        if (order.isEmpty()) {
            Text("Add some games first, then come back to arrange their rows.", style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
        }
        order.forEachIndexed { index, id ->
            val row = byId[id] ?: return@forEachIndexed
            key(id) {
                RowItem(
                    row = row,
                    position = index,
                    count = order.size,
                    hidden = id in settings.hiddenHomeRows,
                    moving = moving == id,
                    focusRequester = if (index == 0) first else null,
                    onFocus = { focusedId = id },
                    onClick = { moving = if (moving == id) null else id },
                    onMove = { move(id, it) },
                    onToggleHidden = { toggleHidden(id) },
                )
            }
        }

        SectionHeader("Reset")
        SettingRow(
            "Reset to default", {
                moving = null
                scope.launch { repo.resetHomeRows() }
                order.clear()
                order.addAll(rows.map { it.id })
            },
            icon = Icons.Rounded.RestartAlt,
            subtitle = "Default order with every row shown",
        )
    }
    InitialFocus(null, first, key = order.isNotEmpty())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowItem(
    row: ShelfModel,
    position: Int,
    count: Int,
    hidden: Boolean,
    moving: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onToggleHidden: () -> Unit,
) {
    val accent = LocalAccent.current
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // Slide smoothly to a new position: jump back to where the row was drawn before the
    // reorder, then spring to its new place (the row it swapped with does the same).
    var height by remember { mutableIntStateOf(0) }
    var lastPosition by remember { mutableIntStateOf(position) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(position) {
        val delta = lastPosition - position
        lastPosition = position
        if (delta != 0 && height > 0) {
            slide.snapTo(slide.value + delta * height)
            slide.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 520f))
        }
    }
    // Keep the row in view while it travels up and down the list.
    LaunchedEffect(position, moving) { if (moving) bring.bringIntoView() }
    val lift by animateFloatAsState(if (moving) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")

    FocusCard(
        onClick = onClick,
        focusRequester = focusRequester,
        onFocusChange = { if (it) onFocus() },
        corner = 18.dp,
        focusScale = if (moving) 1.04f else 1.02f,
        modifier = Modifier
            .onSizeChanged { height = it.height }
            .zIndex(if (moving) 2f else if (slide.isRunning) 1f else 0f)
            .graphicsLayer {
                translationY = slide.value
                shadowElevation = lift * 18.dp.toPx()
                shape = RoundedCornerShape(18.dp)
                clip = false
            }
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bringIntoViewRequester(bring)
            .onPreviewKeyEvent { e ->
                if (!moving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { onMove(-1); scope.launch { bring.bringIntoView() }; true }
                    Key.DirectionDown -> { onMove(1); scope.launch { bring.bringIntoView() }; true }
                    Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            },
    ) { focused ->
        val bg by animateColorAsState(
            when {
                moving -> accent.copy(alpha = 0.35f)
                focused -> Palette.SurfaceHigher
                else -> Palette.Surface
            },
            tween(150), label = "rowBg",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.DragIndicator, null, tint = if (moving) Color.White else Palette.TextFaint)
            Spacer(Modifier.width(10.dp))
            Text(
                "${position + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = if (moving) Color.White else accent,
                modifier = Modifier.width(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    row.title, style = MaterialTheme.typography.titleMedium,
                    color = if (hidden) Palette.TextFaint else Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        moving -> "Move with the D-pad, press again to drop"
                        hidden -> "Hidden · ${row.kind} · ${com.abidnafi.pocketdeck.ui.gameCount(row.games.size)}"
                        else -> "${row.kind} · ${com.abidnafi.pocketdeck.ui.gameCount(row.games.size)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                )
            }
            TapIcon(Icons.Rounded.KeyboardArrowUp, enabled = position > 0) { onMove(-1) }
            TapIcon(Icons.Rounded.KeyboardArrowDown, enabled = position < count - 1) { onMove(1) }
            Spacer(Modifier.width(4.dp))
            TapIcon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, enabled = true, highlight = !hidden, onTap = onToggleHidden)
        }
    }
}

/** Small round touch button. Not focusable, so controller navigation moves row by row. */
@Composable
private fun TapIcon(icon: ImageVector, enabled: Boolean, highlight: Boolean = false, onTap: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .size(38.dp)
            .background(if (highlight) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f), CircleShape)
            .pointerInput(enabled) { detectTapGestures { if (enabled) onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = when {
                !enabled -> Palette.TextFaint.copy(alpha = 0.4f)
                highlight -> accent
                else -> Palette.Text
            },
            modifier = Modifier.size(22.dp),
        )
    }
}
