package com.abidnafi.pocketdeck.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Logical controller buttons. Confirm/Back are resolved from the physical A/B buttons
 * (respecting the swap setting) and normally reach the UI as DPAD_CENTER / BACK key events;
 * they appear here so they can be shown in the hint bar.
 */
enum class Btn { CONFIRM, BACK, X, Y, L1, R1, L2, R2, START, SELECT }

object Priority {
    const val SHELL = 0
    const val SCREEN = 10
    const val OVERLAY = 100
}

/** Routes non-navigation buttons to the most specific active handler. */
class ControllerDispatcher {
    private class Entry(val priority: Int, val order: Long, val handler: (Btn) -> Boolean)

    private val entries = mutableListOf<Entry>()
    private var counter = 0L

    /** True while the physical confirm (A) button is held; kept up to date by the activity. */
    @Volatile var confirmHeld = false

    /** Set when a menu opened under a held confirm press: the rest of that press is ignored. */
    @Volatile var swallowConfirm = false

    /** Call when a menu or overlay opens, so a long-press that opened it can't also click inside it. */
    fun swallowHeldConfirm() {
        if (confirmHeld) swallowConfirm = true
    }

    fun register(priority: Int, handler: (Btn) -> Boolean): Any =
        Entry(priority, counter++, handler).also { entries += it }

    fun unregister(token: Any) {
        entries.remove(token)
    }

    fun dispatch(btn: Btn): Boolean =
        entries.sortedWith(compareByDescending<Entry> { it.priority }.thenByDescending { it.order })
            .any { it.handler(btn) }
}

val LocalController = staticCompositionLocalOf { ControllerDispatcher() }

/** Handles controller buttons while in composition. Return true to consume. */
@Composable
fun ControllerHandler(priority: Int = Priority.SCREEN, enabled: Boolean = true, onButton: (Btn) -> Boolean) {
    val dispatcher = LocalController.current
    val current by rememberUpdatedState(onButton)
    DisposableEffect(dispatcher, priority, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val token = dispatcher.register(priority) { current(it) }
        onDispose { dispatcher.unregister(token) }
    }
}

data class Hint(val btn: Btn, val label: String)

/** Stack of button hints; the bottom bar shows the highest-priority set. */
class HintRegistry {
    class Entry(val priority: Int, val order: Long) {
        var hints by mutableStateOf<List<Hint>>(emptyList())
    }

    private val entries = mutableStateListOf<Entry>()
    private var counter = 0L

    val active: List<Hint>
        get() = entries.maxWithOrNull(compareBy<Entry> { it.priority }.thenBy { it.order })?.hints.orEmpty()

    /** True while a modal overlay (menu, dialog, keyboard) is showing anywhere. */
    val overlayOpen: Boolean
        get() = entries.any { it.priority >= Priority.OVERLAY }

    fun add(priority: Int): Entry = Entry(priority, counter++).also { entries += it }
    fun remove(entry: Entry) {
        entries.remove(entry)
    }
}

val LocalHints = staticCompositionLocalOf { HintRegistry() }

@Composable
fun ButtonHints(vararg hints: Hint, priority: Int = Priority.SCREEN, enabled: Boolean = true) {
    val registry = LocalHints.current
    val entry = remember(registry, priority, enabled) { if (enabled) registry.add(priority) else null }
    val list = hints.toList()
    SideEffect { if (entry != null && entry.hints != list) entry.hints = list }
    DisposableEffect(entry) { onDispose { entry?.let(registry::remove) } }
}
