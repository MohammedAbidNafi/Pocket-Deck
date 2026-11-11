package com.abidnafi.pocketdeck.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.abidnafi.pocketdeck.input.LocalController
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.input.Priority
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette

/**
 * Modal layer drawn inside the activity window (not a Dialog), so controller remapping keeps
 * working. Traps focus, eats controller buttons and closes on Back.
 */
@Composable
fun OverlayScaffold(
    onDismiss: () -> Unit,
    hints: List<Hint> = listOf(Hint(Btn.CONFIRM, "Select"), Hint(Btn.BACK, "Close")),
    onButton: (Btn) -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    // If a held A (long-press) opened this, the rest of that press must not click inside it.
    val controller = LocalController.current
    remember { controller.swallowHeldConfirm() }
    ControllerHandler(Priority.OVERLAY) { btn -> onButton(btn); true }
    ButtonHints(*hints.toTypedArray(), priority = Priority.OVERLAY)
    // An overlay always takes focus for itself, even when the screen behind it is holding back
    // its own initial focus (e.g. the user is on the top bar).
    CompositionLocalProvider(LocalInitialFocusAllowed provides { true }) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        content = content,
    )
    }
}

data class MenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val subtitle: String? = null,
    val checked: Boolean? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    /** Shows a colour swatch instead of an icon (e.g. the accent colour picker). */
    val swatch: Color? = null,
    val onClick: () -> Unit,
)

/** Floating, rounded side sheet with a list of actions; slides in with a little bounce. */
@Composable
fun SideMenu(
    visible: Boolean,
    title: String,
    items: List<MenuItem>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    /** Single-choice menus: open with the ticked item selected instead of the first one. */
    startOnChecked: Boolean = false,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
        OverlayScaffold(onDismiss) {
            val first = remember { FocusRequester() }
            InitialFocus(null, first)
            val accent = LocalAccent.current
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .animateEnterExit(
                        // One smooth motion with a slight settle (a bouncier scale made the contents wobble).
                        enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 2 } +
                            scaleIn(spring(dampingRatio = 0.85f, stiffness = 420f), initialScale = 0.94f, transformOrigin = TransformOrigin(1f, 0.5f)),
                        exit = slideOutHorizontally(tween(160)) { it / 3 } + scaleOut(tween(160), targetScale = 0.94f),
                    )
                    .fillMaxHeight()
                    // Bottom gap keeps the sheet clear of the button hint bar.
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 52.dp)
                    .widthIn(min = 340.dp, max = 420.dp)
                    .shadow(24.dp, BubbleShape, ambientColor = accent, spotColor = accent)
                    .clip(BubbleShape)
                    .background(Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface)))
                    .border(1.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.5f), Palette.Outline)), BubbleShape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    // The list adds its own inner padding (room for the focus glow), so keep this small.
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                // Header and title scroll together with the items, so long menus get the full height.
                val start = remember { if (startOnChecked) items.indexOfFirst { it.checked == true }.coerceAtLeast(0) else 0 }
                MenuList(items, first, start) {
                    Column(Modifier.padding(horizontal = 2.dp)) {
                        header?.invoke()
                        Text(
                            title, style = MaterialTheme.typography.headlineMedium, color = Palette.Text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        }
                    }
                }
            }
        }
    }
}

private val BubbleShape = RoundedCornerShape(30.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuList(items: List<MenuItem>, first: FocusRequester, start: Int, top: @Composable () -> Unit) {
    val accent = LocalAccent.current
    // Ignore clicks for a moment after opening, so the press that opened the menu can't pick an item.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        armed = true
    }
    val opened = remember { System.currentTimeMillis() }
    // Starting further down a long list: make sure that item is laid out so it can take focus
    // (index + 1 because the title/header is the list's first entry).
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (start > 3) start + 1 - 2 else 0)
    // Focused items stay clear of the faded edges instead of hugging them.
    val margin = with(LocalDensity.current) { 36.dp.toPx() }
    // Back at the first item, the list returns fully to the top so the title/header shows again.
    val spec = remember(margin, state) {
        EdgeSnapBringIntoViewSpec(
            margin,
            scrolled = { if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else null },
        )
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Room on every side for the focused item's grow + glow, so nothing gets sliced off.
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        modifier = Modifier.fadingEdges(top = state.canScrollBackward, bottom = state.canScrollForward),
    ) {
        item(key = "top") { top() }
        itemsIndexed(items) { i, item ->
            // Items pop in one after another when the menu opens; ones that scroll into view
            // later just appear (replaying the pop-in mid-scroll looked glitchy).
            // The first item gets focus straight away, so it is fully there from the start;
            // the rest fade in just behind it. (No per-item sliding: the panel already slides,
            // and two motions at once made the focused item stutter.)
            val appear = remember { Animatable(if (i == start || System.currentTimeMillis() - opened > 500) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (appear.value >= 1f) return@LaunchedEffect
                delay(25L * i.coerceAtMost(8))
                appear.animateTo(1f, tween(180))
            }
            FocusCard(
                onClick = { if (armed && item.enabled) item.onClick() },
                focusRequester = if (i == start) first else null,
                corner = 22.dp,
                focusScale = 1.03f,
                glowColor = if (item.danger) Palette.Danger else accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) },
            ) { focused ->
                val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.05f), tween(150), label = "menuBg")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (item.danger) Palette.Danger else accent
                    val fg = when {
                        !item.enabled -> Palette.TextFaint
                        focused -> Palette.Background
                        item.danger -> Palette.Danger
                        else -> Palette.Text
                    }
                    if (item.swatch != null) {
                        // Colour choice: the bubble is the colour itself, ticked when chosen.
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(item.swatch, CircleShape)
                                .border(2.dp, if (focused) Palette.Background.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.checked == true) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    } else Box(
                        Modifier
                            .size(38.dp)
                            .background(if (focused) tint else tint.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon ?: if (item.checked == true) Icons.Rounded.Check else Icons.Rounded.Circle,
                            null,
                            tint = if (focused) Color.White else if (item.enabled) tint else Palette.TextFaint,
                            modifier = Modifier.size(if (item.icon == null && item.checked != true) 10.dp else 21.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.titleMedium, color = fg)
                        if (item.subtitle != null) {
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Items without their own icon already show the tick in the bubble.
                    if (item.checked == true && item.icon != null) {
                        Box(
                            Modifier.size(24.dp).background(if (focused) accent else accent.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
    }
}

/** Softly fades content out at the top/bottom edge (only where there is more to scroll). */
private fun Modifier.fadingEdges(top: Boolean, bottom: Boolean, length: Dp = 28.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val f = (length.toPx() / size.height).coerceIn(0f, 0.5f)
            drawRect(
                Brush.verticalGradient(
                    0f to if (top) Color.Transparent else Color.Black,
                    f to Color.Black,
                    1f - f to Color.Black,
                    1f to if (bottom) Color.Transparent else Color.Black,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Centred card with a message and up to two buttons. */
@Composable
fun DialogOverlay(
    visible: Boolean,
    title: String,
    body: String,
    confirmLabel: String = "OK",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    danger: Boolean = false,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
        OverlayScaffold(onDismiss) {
            val confirm = remember { FocusRequester() }
            InitialFocus(null, confirm)
            val bodyScroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val step = with(LocalDensity.current) { 72.dp.toPx() }
            Column(
                Modifier
                    .align(Alignment.Center)
                    // Stay clear of the screen edges and the hint bar, however long the text is.
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 56.dp)
                    .widthIn(max = 680.dp)
                    .background(Palette.Surface, RoundedCornerShape(24.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    // Long text: up/down on the D-pad scrolls it (the buttons keep focus).
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val delta = when (e.key) {
                            Key.DirectionDown -> step
                            Key.DirectionUp -> -step
                            else -> return@onPreviewKeyEvent false
                        }
                        if (bodyScroll.maxValue == 0) return@onPreviewKeyEvent false
                        scope.launch { bodyScroll.animateScrollBy(delta) }
                        true
                    }
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = if (danger) Palette.Danger else Palette.Text)
                Spacer(Modifier.height(10.dp))
                // The body takes whatever height is left and scrolls; title and buttons never get cut.
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextDim,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fadingEdges(top = bodyScroll.canScrollBackward, bottom = bodyScroll.canScrollForward, length = 20.dp)
                        .verticalScroll(bodyScroll),
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    if (dismissLabel != null) ConsoleButton(dismissLabel, onDismiss)
                    ConsoleButton(confirmLabel, onConfirm, primary = true, focusRequester = confirm)
                }
            }
        }
    }
}
