package com.abidnafi.pocketdeck.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abidnafi.pocketdeck.PocketDeckApp
import com.abidnafi.pocketdeck.data.LaunchResult
import kotlinx.coroutines.launch

/**
 * Invisible stop between a widget tap and the game: starts the game the same way the app does
 * (emulator settings, URI permissions…) and closes straight away. Runs in its own empty task so
 * PocketDeck itself doesn't pop up first.
 */
class WidgetLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PocketDeckApp
        val container = app.container
        val id = intent.getLongExtra(EXTRA_GAME_ID, -1L)
        lifecycleScope.launch {
            val game = container.launcher.gameById(id)
            val result = if (game == null) {
                LaunchResult.Error("Game not found", "It may have been removed from PocketDeck.")
            } else {
                container.launcher.launch(this@WidgetLaunchActivity, game, trackSession = false)
            }
            if (result is LaunchResult.Error) {
                Toast.makeText(app, "${result.title}. ${result.message}", Toast.LENGTH_LONG).show()
            }
            // "Last played" changed: redraw widgets (outlives this activity).
            container.appScope.launch { GamesWidget.refreshAll(app) }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }
}
