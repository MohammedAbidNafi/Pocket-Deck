package com.abidnafi.pocketdeck.shortcuts

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.abidnafi.pocketdeck.data.ReturnNotifier

/**
 * Optional "controller shortcut": holding Select + Start (View + Menu on Xbox pads) for a moment
 * opens PocketDeck from inside any game. Only watches controller buttons; the presses still reach
 * the game, nothing is consumed or recorded. Off until the user enables it in Accessibility settings.
 */
class ControllerShortcutService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val held = HashSet<Int>()
    private val fire = Runnable {
        held.clear()
        runCatching { startActivity(ReturnNotifier.launchIntent(this)) }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_BUTTON_SELECT && code != KeyEvent.KEYCODE_BUTTON_START) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (held.add(code) && held.size == 2) {
                handler.removeCallbacks(fire)
                handler.postDelayed(fire, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                held.remove(code)
                handler.removeCallbacks(fire)
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        held.clear()
        handler.removeCallbacks(fire)
    }

    override fun onDestroy() {
        handler.removeCallbacks(fire)
        super.onDestroy()
    }

    companion object {
        const val HOLD_MS = 700L

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ControllerShortcutService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
