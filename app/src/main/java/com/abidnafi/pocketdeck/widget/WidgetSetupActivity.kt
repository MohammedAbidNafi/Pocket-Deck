package com.abidnafi.pocketdeck.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import com.abidnafi.pocketdeck.PocketDeckApp
import com.abidnafi.pocketdeck.ui.theme.Palette
import com.abidnafi.pocketdeck.ui.theme.PocketDeckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Chooses what a home-screen widget shows. Opens when the widget is placed, when its title is
 * tapped, and from the launcher's "reconfigure" option.
 */
class WidgetSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out of first-time setup cancels placing the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = (application as PocketDeckApp).container
        setContent {
            val accentIndex by produceState(0) { value = container.settings.settings.first().accentIndex }
            val sources by produceState<List<WidgetSource>?>(null) { value = loadWidgetSources(container) }
            val current by produceState<String?>(null) {
                value = runCatching {
                    val id = GlanceAppWidgetManager(this@WidgetSetupActivity).getGlanceIdBy(appWidgetId)
                    getAppWidgetState(this@WidgetSetupActivity, PreferencesGlanceStateDefinition, id)
                        .let { (it as Preferences)[SourceKey] }
                }.getOrNull() ?: DEFAULT_WIDGET_SOURCE
            }
            PocketDeckTheme(Palette.accents.getOrElse(accentIndex) { Palette.accents[0] }.color) {
                Picker(sources, current) { source -> choose(appWidgetId, source.id) }
            }
        }
    }

    private fun choose(appWidgetId: Int, sourceId: String) {
        lifecycleScope.launch {
            GamesWidget.setSource(applicationContext, appWidgetId, sourceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun Picker(sources: List<WidgetSource>?, current: String?, onPick: (WidgetSource) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.padding(top = 20.dp))
        Text("What should this widget show?", style = MaterialTheme.typography.headlineMedium, color = Palette.Text, fontWeight = FontWeight.Bold)
        Text(
            "You can change it any time: tap the widget's title, or use the arrows on it.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
        )
        Spacer(Modifier.padding(top = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(sources.orEmpty(), key = { it.id }) { source ->
                val selected = source.id == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) accent.copy(alpha = 0.22f) else Palette.Surface)
                        .clickable { onPick(source) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(iconFor(source.id), null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(source.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            "${source.subtitle} · ${source.games.size} ${if (source.games.size == 1) "game" else "games"}",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                        )
                    }
                    if (selected) Icon(Icons.Rounded.Check, "Current", tint = accent)
                }
            }
        }
    }
}

private fun iconFor(id: String): ImageVector = when {
    id == "recent" -> Icons.Rounded.History
    id == "added" -> Icons.Rounded.NewReleases
    id == "favorites" -> Icons.Rounded.Star
    id == "all" -> Icons.Rounded.Apps
    id.startsWith("collection:") -> Icons.Rounded.CollectionsBookmark
    id == "emulators" -> Icons.Rounded.SportsEsports
    else -> Icons.AutoMirrored.Rounded.PlaylistPlay
}
