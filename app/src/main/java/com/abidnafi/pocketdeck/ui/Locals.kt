package com.abidnafi.pocketdeck.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.abidnafi.pocketdeck.AppContainer
import com.abidnafi.pocketdeck.data.LaunchResult
import com.abidnafi.pocketdeck.data.Settings
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.input.UiFeedback
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalSettings = staticCompositionLocalOf { Settings() }
val LocalFeedback = staticCompositionLocalOf<UiFeedback> { error("UiFeedback not provided") }
val LocalMessages = staticCompositionLocalOf { MessageCenter() }
/** Whether artwork may be fetched from the network right now. */
val LocalArtNetwork = staticCompositionLocalOf { true }

data class Message(val title: String, val body: String)

/** App-wide modal message (e.g. launch errors), drawn by the root. */
class MessageCenter {
    var current by mutableStateOf<Message?>(null)
        private set

    fun show(title: String, body: String) {
        current = Message(title, body)
    }

    fun dismiss() {
        current = null
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Returns a function that launches a game and reports failures in the message overlay. */
@Composable
fun rememberGameStarter(): (GameEntity) -> Unit {
    val container = LocalAppContainer.current
    val messages = LocalMessages.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { game ->
        val activity = context.findActivity()
        if (activity != null) {
            scope.launch {
                val result = container.launcher.launch(activity, game)
                if (result is LaunchResult.Error) messages.show(result.title, result.message)
            }
        }
    }
}
