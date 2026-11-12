package com.abidnafi.pocketdeck

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ControllerDispatcher
import com.abidnafi.pocketdeck.input.HintRegistry
import com.abidnafi.pocketdeck.input.StickNavigator
import com.abidnafi.pocketdeck.input.UiFeedback
import com.abidnafi.pocketdeck.ui.PocketDeckRoot
import com.abidnafi.pocketdeck.ui.StartupIntro
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as PocketDeckApp).container }
    private val controller = ControllerDispatcher()
    private val hints = HintRegistry()
    private val stick = StickNavigator { code -> injectKey(code) }

    /** Mirrors the "swap A/B" setting so key handling stays synchronous. */
    @Volatile private var swapAB = false

    /** Started by Android as the home screen (via the HomeEntry alias), not as a normal app. */
    private var isHomeInstance = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        isHomeInstance = intent?.hasCategory(android.content.Intent.CATEGORY_HOME) == true
        hideSystemBars()
        lifecycleScope.launch {
            container.settings.settings.collect {
                swapAB = it.swapAB
                container.feedback.soundsEnabled = it.uiSounds
                container.feedback.hapticsEnabled = it.haptics
            }
        }
        val coldStart = savedInstanceState == null
        setContent {
            // Animated logo only on a real launch, not after rotation / process restore.
            var intro by rememberSaveable { mutableStateOf(coldStart) }
            Box(Modifier.fillMaxSize()) {
                PocketDeckRoot(container, controller, hints)
                AnimatedVisibility(
                    visible = intro,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(380)) + scaleOut(tween(380), targetScale = 1.12f),
                ) {
                    StartupIntro(container.feedback, onFinished = { intro = false })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.homeMode.refresh()
        // When PocketDeck becomes the home app, Android starts a separate home copy of it. Hand this
        // ordinary copy over to that one so there's only ever one PocketDeck (setup resumes there).
        if (!isHomeInstance && container.homeMode.state.value.isDefault) {
            startActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finishAndRemoveTask()
            return
        }
        hideSystemBars()
        lifecycleScope.launch {
            container.launcher.onReturnedToApp()
            container.library.syncInstalledApps()
        }
        // Gaming PCs: who's online, and pick up games added on the PC. Best effort, in the background.
        container.hosts.refreshAll()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Home pressed while PocketDeck (as the home screen) is already open: back to the Home tab.
        if (intent.hasCategory(android.content.Intent.CATEGORY_HOME)) container.homeMode.onHomePressed()
    }

    override fun onPause() {
        super.onPause()
        stick.reset()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Face buttons become the keys Compose already understands for click / back.
        val confirmKey = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        val backKey = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        when (code) {
            confirmKey -> {
                val down = event.action == KeyEvent.ACTION_DOWN
                if (down && event.repeatCount == 0) {
                    controller.confirmHeld = true
                    controller.swallowConfirm = false
                }
                // A long-press opened a menu: ignore the rest of this press (repeats + release).
                if (controller.swallowConfirm) {
                    if (!down) {
                        controller.confirmHeld = false
                        controller.swallowConfirm = false
                    }
                    return true
                }
                if (!down) controller.confirmHeld = false
                return super.dispatchKeyEvent(remap(event, KeyEvent.KEYCODE_DPAD_CENTER))
            }
            backKey, KeyEvent.KEYCODE_ESCAPE -> {
                // Go straight to the back dispatcher: a remapped BACK key event is ignored by
                // the system's predictive-back handling on recent Android versions.
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    container.feedback.play(UiFeedback.Sound.BACK, window.decorView)
                    onBackPressedDispatcher.onBackPressed()
                }
                return true
            }
        }
        val btn = buttonFor(code) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (!controller.dispatch(btn)) return super.dispatchKeyEvent(event)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        stick.onMotion(event) || super.dispatchGenericMotionEvent(event)

    private fun buttonFor(code: Int): Btn? = when (code) {
        KeyEvent.KEYCODE_BUTTON_X -> Btn.X
        KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_PAGE_UP -> Btn.L1
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_PAGE_DOWN -> Btn.R1
        KeyEvent.KEYCODE_BUTTON_L2 -> Btn.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> Btn.R2
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE -> Btn.START
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SEARCH -> Btn.SELECT
        // Keyboard shortcuts, handy on an emulator or a handheld with a keyboard.
        KeyEvent.KEYCODE_F1 -> Btn.X
        KeyEvent.KEYCODE_F2 -> Btn.Y
        KeyEvent.KEYCODE_F3 -> Btn.START
        KeyEvent.KEYCODE_F4 -> Btn.SELECT
        else -> null
    }

    private fun remap(event: KeyEvent, newCode: Int) = KeyEvent(
        event.downTime, event.eventTime, event.action, newCode, event.repeatCount,
        event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
    )

    private fun injectKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }
}
