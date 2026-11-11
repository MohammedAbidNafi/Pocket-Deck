package com.abidnafi.pocketdeck.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.abidnafi.pocketdeck.input.UiFeedback
import kotlinx.coroutines.launch
import com.abidnafi.pocketdeck.ui.LocalFeedback
import com.abidnafi.pocketdeck.ui.theme.LocalAccent

/**
 * The basic controller-focusable surface: grows, lifts above neighbours and gets an accent
 * glow ring when focused. Confirm (A) / Enter / tap triggers [onClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    focusRequester: FocusRequester? = null,
    corner: Dp = 14.dp,
    focusScale: Float = 1.07f,
    glowColor: Color = LocalAccent.current,
    clickSound: UiFeedback.Sound = UiFeedback.Sound.SELECT,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) focusScale else 1f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "focusScale",
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(170), label = "focusGlow")
    val feedback = LocalFeedback.current
    val view = LocalView.current
    val ringWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val shape = RoundedCornerShape(corner)

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (glow > 0f) {
                    // Soft halo built from expanding, fading strokes, then a crisp ring.
                    for (i in 4 downTo 1) {
                        val grow = ringWidth * i * 1.6f
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.10f * glow * (5 - i) / 4f),
                            topLeft = Offset(-grow, -grow),
                            size = Size(size.width + grow * 2, size.height + grow * 2),
                            cornerRadius = CornerRadius(cornerPx + grow),
                            style = Stroke(ringWidth * 1.6f),
                        )
                    }
                    val inset = -ringWidth / 2 - 1f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f * glow),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(cornerPx + ringWidth / 2),
                        style = Stroke(ringWidth),
                    )
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (focused) feedback.play(UiFeedback.Sound.MOVE, view)
                    onFocusChange(focused)
                }
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
            ) {
                feedback.play(clickSound, view)
                onClick()
            },
    ) { content(focused) }
}

/**
 * Remembers which item had focus so it can be focused again after a menu closes or the user
 * comes back to the screen. Keys must be stable strings (e.g. "shelf:recent/42").
 */
class FocusMemory(initial: String?) {
    var lastKey: String? = initial
    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * Restores focus once a closing menu / dialog has animated away. Overlays hold on to focus
     * until they're gone, so restoring straight away can land somewhere else (e.g. the top bar).
     */
    fun restoreSoon(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            kotlinx.coroutines.delay(200)
            restore()
        }
    }

    fun restore(): Boolean {
        val key = lastKey ?: return false
        val r = requesters[key] ?: return false
        return runCatching { r.requestFocus() }.getOrDefault(false)
    }

    companion object {
        val saver = Saver<FocusMemory, String>(save = { it.lastKey.orEmpty() }, restore = { FocusMemory(it.ifEmpty { null }) })
    }
}

@Composable
fun rememberFocusMemory(): FocusMemory = rememberSaveable(saver = FocusMemory.saver) { FocusMemory(null) }

/**
 * Lets a parent veto [InitialFocus] in its children, e.g. the main shell while the user is
 * moving along the top bar with L1/R1 (a freshly shown tab must not pull focus down).
 */
val LocalInitialFocusAllowed = staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * On first composition, refocus the remembered item, or [fallback] if there is none.
 * Retries for a short while: lazy lists and the window itself may not be ready on the first frames.
 */
@Composable
fun InitialFocus(memory: FocusMemory?, fallback: FocusRequester?, key: Any? = Unit) {
    val allowed = LocalInitialFocusAllowed.current
    val view = LocalView.current
    LaunchedEffect(key) {
        withFrameNanos { }
        repeat(45) {
            withFrameNanos { }
            if (!allowed()) return@LaunchedEffect
            // In touch mode Android refuses focus requests, so nothing would be highlighted
            // until the first D-pad press. Leave touch mode, controller-first.
            if (!view.hasFocus()) view.requestFocusFromTouch()
            if (memory?.restore() == true) return@LaunchedEffect
            if (fallback != null && runCatching { fallback.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)

/** Keeps the focused child at a fixed distance from the start of a scroll container. */
@OptIn(ExperimentalFoundationApi::class)
class PivotBringIntoViewSpec(private val pivotPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - pivotPx
}

/**
 * Like [MarginBringIntoViewSpec], but snaps fully to the start/end of the content whenever the
 * focused item would still be completely visible there. So moving back up to the first item
 * also reveals any title or header above it, and moving to the last item shows anything below.
 *
 * [scrolled] = how far the content is scrolled from its very top (null if unknown, e.g. a lazy
 * list scrolled past its first item); [remaining] = how far it can still scroll down (null if unknown).
 */
@OptIn(ExperimentalFoundationApi::class)
class EdgeSnapBringIntoViewSpec(
    private val marginPx: Float,
    private val scrolled: () -> Float?,
    private val remaining: () -> Float? = { null },
) : BringIntoViewSpec {
    private val fallback = MarginBringIntoViewSpec(marginPx)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val s = scrolled()
        // Item fits on screen with the content at its very top: go all the way up.
        if (s != null && s > 0f && s + offset + size + marginPx <= containerSize) return -s
        val r = remaining()
        // Item still fits with the content scrolled to its very end: go all the way down.
        if (r != null && r > 0f && offset - r >= marginPx && offset + size > containerSize - marginPx) return r
        return fallback.calculateScrollDistance(offset, size, containerSize)
    }
}

/** Scrolls only as much as needed to keep the focused child (plus a margin for its glow) visible. */
@OptIn(ExperimentalFoundationApi::class)
class MarginBringIntoViewSpec(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = marginPx
        val trailing = containerSize - marginPx
        val end = offset + size
        return when {
            size > trailing - leading -> offset - leading
            offset < leading -> offset - leading
            end > trailing -> end - trailing
            else -> 0f
        }
    }
}
