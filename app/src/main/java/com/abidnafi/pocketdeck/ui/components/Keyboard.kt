package com.abidnafi.pocketdeck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette

private const val NUMBERS = "1234567890"
private const val SYMBOLS = "!@#\$%&*()?"
private val letterRows = listOf("qwertyuiop", "asdfghjkl'", "zxcvbnm")

enum class ShiftMode { OFF, ONCE, LOCK }

/** Shift state, hoisted so screens can also toggle it from a controller button (L1). */
class KeyboardState {
    var shift by mutableStateOf(ShiftMode.OFF)

    /** Off -> one capital -> caps lock -> off. */
    fun toggleShift() {
        shift = when (shift) {
            ShiftMode.OFF -> ShiftMode.ONCE
            ShiftMode.ONCE -> ShiftMode.LOCK
            ShiftMode.LOCK -> ShiftMode.OFF
        }
    }

    internal fun afterChar() {
        if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
    }
}

@Composable
fun rememberKeyboardState() = remember { KeyboardState() }

/**
 * Controller-friendly on-screen keyboard: number row (symbols with Shift), QWERTY letters,
 * Shift, backspace, space and done. X = backspace, Y = space, L1 = Shift, Start = done are
 * wired by the caller through [OverlayScaffold] / screen handlers.
 */
@Composable
fun ConsoleKeyboard(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    firstKey: FocusRequester? = null,
    doneLabel: String = "Done",
    keySize: Dp = 46.dp,
    state: KeyboardState = rememberKeyboardState(),
) {
    val gap = (keySize.value * 0.17f).dp
    fun span(units: Float): Dp = keySize * units + gap * (units - 1f)
    val shifted = state.shift != ShiftMode.OFF
    fun type(c: Char) {
        onChar(c)
        state.afterChar()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        // Number row; Shift turns it into symbols.
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            (if (shifted) SYMBOLS else NUMBERS).forEach { c ->
                KeyCap(c.toString(), width = keySize, height = keySize) { type(c) }
            }
        }
        letterRows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (r == 2) {
                    KeyCap(
                        label = null,
                        icon = if (state.shift == ShiftMode.LOCK) Icons.Rounded.KeyboardCapslock else Icons.Rounded.ArrowUpward,
                        width = span(1.5f), height = keySize,
                        highlighted = shifted,
                        onClick = state::toggleShift,
                    )
                }
                row.forEachIndexed { i, c ->
                    val ch = if (shifted) c.uppercaseChar() else c
                    KeyCap(
                        label = ch.toString(),
                        width = keySize,
                        height = keySize,
                        focusRequester = if (r == 0 && i == 0) firstKey else null,
                    ) { type(ch) }
                }
                if (r == 2) {
                    KeyCap(null, Icons.AutoMirrored.Rounded.Backspace, width = span(1.5f), height = keySize, onClick = onBackspace)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            listOf('-', '.').forEach { c -> KeyCap(c.toString(), width = keySize, height = keySize) { type(c) } }
            KeyCap(null, Icons.Rounded.SpaceBar, width = span(4f), height = keySize, onClick = onSpace)
            listOf(':', ',').forEach { c -> KeyCap(c.toString(), width = keySize, height = keySize) { type(c) } }
            KeyCap(doneLabel, Icons.Rounded.Check, width = span(2f), height = keySize, accent = true, onClick = onDone)
        }
    }
}

/** Largest key size (capped at [max]) that fits the five keyboard rows into [height]. */
fun keySizeFor(height: Dp, max: Dp = 46.dp): Dp =
    // 5 rows + 4 gaps of 0.17 key each = 5.68 keys.
    (height / 5.68f).coerceIn(30.dp, max)

@Composable
private fun KeyCap(
    label: String?,
    icon: ImageVector? = null,
    width: Dp,
    height: Dp,
    accent: Boolean = false,
    highlighted: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val accentColor = LocalAccent.current
    FocusCard(onClick = onClick, corner = 10.dp, focusScale = 1.12f, focusRequester = focusRequester) { focused ->
        Box(
            Modifier
                .width(width)
                .height(height)
                .background(
                    when {
                        focused -> Color.White
                        accent -> accentColor
                        highlighted -> accentColor.copy(alpha = 0.55f)
                        else -> Palette.SurfaceHigher
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val fg = if (focused) Palette.Background else Palette.Text
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                // Narrow keys with a label (e.g. Done on small keyboards) drop the icon so the word fits on one line.
                val showIcon = icon != null && (label == null || width >= 100.dp)
                if (showIcon) Icon(icon!!, null, tint = fg)
                if (showIcon && label != null) Spacer(Modifier.padding(3.dp))
                if (label != null) Text(label, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Modal text entry with the on-screen keyboard (used for naming collections, custom emulators…). */
@Composable
fun TextEntryOverlay(
    visible: Boolean,
    title: String,
    initial: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
        var text by rememberSaveable(initial) { mutableStateOf(initial) }
        val keyboard = rememberKeyboardState()
        OverlayScaffold(
            onDismiss = onDismiss,
            hints = listOf(Hint(Btn.CONFIRM, "Type"), Hint(Btn.X, "Delete"), Hint(Btn.Y, "Space"), Hint(Btn.L1, "Shift"), Hint(Btn.START, "Done"), Hint(Btn.BACK, "Cancel")),
            onButton = { btn ->
                when (btn) {
                    Btn.L1 -> keyboard.toggleShift()
                    Btn.X -> text = text.dropLast(1)
                    Btn.Y -> text += " "
                    Btn.START -> onDone(text)
                    else -> Unit
                }
                true
            },
        ) {
            val first = remember { FocusRequester() }
            InitialFocus(null, first)
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Palette.Surface, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(24.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                Spacer(Modifier.height(14.dp))
                TextDisplay(text, placeholder, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                ConsoleKeyboard(
                    onChar = { text += it },
                    onBackspace = { text = text.dropLast(1) },
                    onSpace = { text += " " },
                    onDone = { onDone(text) },
                    firstKey = first,
                    keySize = keySizeFor(LocalConfiguration.current.screenHeightDp.dp - 230.dp, 42.dp),
                    state = keyboard,
                )
            }
        }
    }
}

/** The "text field" above the keyboard, with a blinking-free caret. */
@Composable
fun TextDisplay(text: String, placeholder: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    // Gently blinking caret, like a real text field.
    val blink = rememberInfiniteTransition(label = "caret")
    val caretAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 500; 0f at 520; 0f at 980 }),
        label = "caretAlpha",
    )
    val caret = @Composable { Box(Modifier.height(26.dp).width(2.dp).alpha(caretAlpha).background(accent)) }
    Row(
        modifier
            .background(Palette.Background, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (text.isEmpty()) {
            // Nothing typed: the caret waits at the start, in front of the hint text.
            caret()
            Spacer(Modifier.width(4.dp))
            Text(placeholder, style = MaterialTheme.typography.titleLarge, color = Palette.TextFaint, maxLines = 1)
        } else {
            Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1)
            Spacer(Modifier.width(2.dp))
            caret()
        }
    }
}
