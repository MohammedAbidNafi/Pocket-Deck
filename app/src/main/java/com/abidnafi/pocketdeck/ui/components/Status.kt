package com.abidnafi.pocketdeck.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.InputDevice
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.delay
import java.util.Date

/** Current time, refreshed every few seconds, in the device's 12/24h format. */
@Composable
fun rememberClock(pattern: Boolean = false): String {
    val context = LocalContext.current
    val time by produceState("") {
        while (true) {
            value = if (pattern) DateFormat.getLongDateFormat(context).format(Date())
            else DateFormat.getTimeFormat(context).format(Date())
            delay(5_000)
        }
    }
    return time
}

data class BatteryState(val percent: Int, val charging: Boolean, val present: Boolean)

@Composable
fun rememberBattery(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(100, false, false)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                state = BatteryState(
                    percent = if (level >= 0 && scale > 0) level * 100 / scale else 100,
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, level >= 0),
                )
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Names of connected game controllers, updated as they connect / disconnect. */
@Composable
fun rememberControllers(): List<String> {
    val context = LocalContext.current
    var names by remember { mutableStateOf(controllerNames()) }
    DisposableEffect(context) {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { names = controllerNames() }
            override fun onInputDeviceRemoved(deviceId: Int) { names = controllerNames() }
            override fun onInputDeviceChanged(deviceId: Int) { names = controllerNames() }
        }
        im.registerInputDeviceListener(listener, null)
        onDispose { im.unregisterInputDeviceListener(listener) }
    }
    return names
}

private fun controllerNames(): List<String> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
    val d = InputDevice.getDevice(id) ?: return@mapNotNull null
    val gamepad = d.supportsSource(InputDevice.SOURCE_GAMEPAD) || d.supportsSource(InputDevice.SOURCE_JOYSTICK)
    if (gamepad && !d.isVirtual) d.name else null
}.distinct()

/** Clock, controller and battery indicators for the top bar. */
@Composable
fun StatusIndicators(showClock: Boolean, modifier: Modifier = Modifier) {
    val battery = rememberBattery()
    val controllers = rememberControllers()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (controllers.isNotEmpty()) {
            Icon(Icons.Rounded.SportsEsports, "${controllers.size} controller(s)", tint = Palette.Text, modifier = Modifier.size(22.dp))
            if (controllers.size > 1) Text("${controllers.size}", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            Spacer(Modifier.width(16.dp))
        }
        if (battery.present) {
            val icon = when {
                battery.charging -> Icons.Rounded.BatteryChargingFull
                battery.percent > 85 -> Icons.Rounded.BatteryFull
                battery.percent > 55 -> Icons.Rounded.Battery6Bar
                battery.percent > 25 -> Icons.Rounded.Battery4Bar
                else -> Icons.Rounded.Battery2Bar
            }
            Icon(icon, null, tint = if (battery.percent <= 15 && !battery.charging) Palette.Danger else Palette.Text, modifier = Modifier.size(22.dp))
            Text("${battery.percent}%", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            Spacer(Modifier.width(16.dp))
        }
        if (showClock) Text(rememberClock(), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    }
}
