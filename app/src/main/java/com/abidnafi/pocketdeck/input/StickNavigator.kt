package com.abidnafi.pocketdeck.input

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the left stick and the hat (D-pad reported as axes) into D-pad key presses,
 * with an initial delay and accelerating repeat, like a console menu.
 */
class StickNavigator(private val press: (keyCode: Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var direction = 0
    private var repeats = 0

    private val repeat = object : Runnable {
        override fun run() {
            if (direction == 0) return
            press(direction)
            repeats++
            handler.postDelayed(this, max(MIN_REPEAT_MS, REPEAT_MS - repeats * 8L))
        }
    }

    fun onMotion(event: MotionEvent): Boolean {
        val joystick = event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!joystick || event.actionMasked != MotionEvent.ACTION_MOVE) return false

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val useHat = abs(hatX) > 0.5f || abs(hatY) > 0.5f
        val x = if (useHat) hatX else event.getAxisValue(MotionEvent.AXIS_X)
        val y = if (useHat) hatY else event.getAxisValue(MotionEvent.AXIS_Y)

        // Hysteresis: a held direction stays active until the stick is mostly released.
        val threshold = if (direction != 0) RELEASE else PRESS
        val next = when {
            abs(x) < threshold && abs(y) < threshold -> 0
            abs(x) >= abs(y) -> if (x > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            else -> if (y > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        }
        if (next != direction) {
            direction = next
            handler.removeCallbacks(repeat)
            if (next != 0) {
                repeats = 0
                press(next)
                handler.postDelayed(repeat, INITIAL_DELAY_MS)
            }
        }
        return true
    }

    fun reset() {
        direction = 0
        handler.removeCallbacks(repeat)
    }

    private companion object {
        const val PRESS = 0.55f
        const val RELEASE = 0.35f
        const val INITIAL_DELAY_MS = 360L
        const val REPEAT_MS = 130L
        const val MIN_REPEAT_MS = 55L
    }
}
