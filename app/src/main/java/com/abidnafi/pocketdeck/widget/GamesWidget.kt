package com.abidnafi.pocketdeck.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.abidnafi.pocketdeck.AppContainer
import com.abidnafi.pocketdeck.MainActivity
import com.abidnafi.pocketdeck.PocketDeckApp
import com.abidnafi.pocketdeck.R
import com.abidnafi.pocketdeck.data.TileShape
import com.abidnafi.pocketdeck.data.art.LibretroThumbnails
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

internal val SourceKey = stringPreferencesKey("source")
private val RefreshKey = longPreferencesKey("refresh")
private val DeltaKey = ActionParameters.Key<Int>("delta")

private const val ICON_PX = 144
private const val MAX_GAMES = 24

/** One game as the widget draws it. */
private class WidgetGame(val id: Long, val title: String, val icon: Bitmap?, val color: Int, val badge: String?)

private class WidgetData(val source: WidgetSource, val games: List<WidgetGame>, val accent: Color, val round: Boolean)

/**
 * Home-screen widget: a grid of games from one source (last played, recently added, favourites,
 * all games or any collection). Tap a game to play it; the arrows switch source; tapping the
 * title opens the picker. Fills whatever size the widget is resized to.
 */
class GamesWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PocketDeckApp).container
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val prefs = currentState<Preferences>()
            val sourceId = prefs[SourceKey] ?: DEFAULT_WIDGET_SOURCE
            val refresh = prefs[RefreshKey] ?: 0L
            val data by produceState<WidgetData?>(null, sourceId, refresh) { value = load(context, container, sourceId) }
            WidgetContent(context, appWidgetId, data)
        }
    }

    companion object {
        /** Redraws every placed widget with fresh library data (after games change, a launch, etc.). */
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val widget = GamesWidget()
            manager.getGlanceIds(GamesWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[RefreshKey] = System.currentTimeMillis() }
                widget.update(context, id)
            }
        }

        /** Shows [sourceId] on one widget. */
        suspend fun setSource(context: Context, appWidgetId: Int, sourceId: String) {
            val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, id) { it[SourceKey] = sourceId }
            GamesWidget().update(context, id)
        }
    }
}

class GamesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GamesWidget()
}

/** The ‹ › arrows: move to the previous / next source. */
class CycleSourceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as PocketDeckApp).container
        val ids = loadWidgetSources(container).map { it.id }
        if (ids.isEmpty()) return
        val delta = parameters[DeltaKey] ?: 1
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = ids.indexOf(prefs[SourceKey] ?: DEFAULT_WIDGET_SOURCE).coerceAtLeast(0)
            prefs[SourceKey] = ids[(current + delta + ids.size) % ids.size]
        }
        GamesWidget().update(context, glanceId)
    }
}

private suspend fun load(context: Context, container: AppContainer, sourceId: String): WidgetData = withContext(Dispatchers.IO) {
    val settings = container.settings.settings.first()
    val sources = loadWidgetSources(container)
    val source = sources.firstOrNull { it.id == sourceId } ?: sources.first()
    val round = settings.tileShape == TileShape.ROUND
    val games = source.games.take(MAX_GAMES).map { game ->
        val platform = container.platforms[game.platformId]
        WidgetGame(
            id = game.id,
            title = game.title,
            icon = artFor(context, container, game, settings.onlineArtwork)?.let { roundOff(it, round) },
            color = platform?.argb?.toInt() ?: 0xFF2A3445.toInt(),
            badge = platform?.shortName,
        )
    }
    WidgetData(source, games, Palette.accents.getOrElse(settings.accentIndex) { Palette.accents[0] }.color, round)
}

/** Icon for Android games (full-bleed when possible), box art for ROMs (from cache, or downloaded if allowed). */
private suspend fun artFor(context: Context, container: AppContainer, game: GameEntity, online: Boolean): Bitmap? {
    game.customArt?.let { path ->
        return runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
    }
    if (game.type == GameType.STREAM) {
        return game.hostArt?.let { path -> runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    if (game.type != GameType.ROM) {
        val visual = game.packageName?.let { container.icons.load(it) } ?: return null
        return (visual.fullBleed ?: visual.icon).asAndroidBitmap()
    }
    val system = container.platforms[game.platformId]?.libretro ?: return null
    val name = game.fileName ?: return null
    val loader = SingletonImageLoader.get(context)
    for (url in LibretroThumbnails.candidates(system, LibretroThumbnails.Kind.BOXART, name)) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(ICON_PX * 2)
            .allowHardware(false)
            .networkCachePolicy(if (online) coil3.request.CachePolicy.ENABLED else coil3.request.CachePolicy.DISABLED)
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) return result.image.toBitmap()
    }
    return null
}

/** Square-crops and rounds the art (circle for round tiles), so it looks right on every Android version. */
private fun roundOff(src: Bitmap, circle: Boolean): Bitmap {
    val side = min(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, ICON_PX, ICON_PX, true)
    val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    val rect = RectF(0f, 0f, ICON_PX.toFloat(), ICON_PX.toFloat())
    val radius = if (circle) ICON_PX / 2f else ICON_PX * 0.24f
    Canvas(out).drawRoundRect(rect, radius, radius, paint)
    return out
}

private val White = ColorProvider(Color.White)
private val Dim = ColorProvider(Color(0xFF9AA4B4))

@Composable
private fun WidgetContent(context: Context, appWidgetId: Int, data: WidgetData?) {
    val size = LocalSize.current
    val accent = data?.accent ?: Palette.accents[0].color
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(26.dp)
            .background(ColorProvider(Color(0xF00A0D13)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(context, appWidgetId, data?.source?.title ?: "PocketDeck", accent)
        Spacer(GlanceModifier.height(8.dp))
        when {
            data == null -> Unit
            data.games.isEmpty() -> EmptyState(context, data.source)
            else -> Grid(context, data, size.width, size.height)
        }
    }
}

@Composable
private fun Header(context: Context, appWidgetId: Int, title: String, accent: Color) {
    val openApp = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val pickSource = Intent(context, WidgetSetupActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setData(Uri.parse("pocketdeck://widget/$appWidgetId"))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.mipmap.ic_launcher), "Open PocketDeck",
            GlanceModifier.size(26.dp).clickable(actionStartActivity(openApp)),
        )
        Spacer(GlanceModifier.width(8.dp))
        // Tap the title to pick what this widget shows.
        Text(
            title,
            style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(pickSource)),
        )
        ArrowButton("‹", -1, accent)
        Spacer(GlanceModifier.width(6.dp))
        ArrowButton("›", 1, accent)
    }
}

@Composable
private fun ArrowButton(label: String, delta: Int, accent: Color) {
    Box(
        GlanceModifier
            .size(30.dp)
            .cornerRadius(15.dp)
            .background(ColorProvider(accent.copy(alpha = 0.28f)))
            .clickable(actionRunCallback<CycleSourceAction>(actionParametersOf(DeltaKey to delta))),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun EmptyState(context: Context, source: WidgetSource) {
    val openApp = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val message = when (source.id) {
        "recent" -> "Games you play will show up here."
        "favorites" -> "Press Y on a game in PocketDeck to favourite it."
        else -> "Nothing here yet."
    }
    Column(
        GlanceModifier.fillMaxSize().clickable(actionStartActivity(openApp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = TextStyle(color = Dim, fontSize = 13.sp))
        Text("Tap to open PocketDeck", style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

/** As many columns and rows of games as fit the widget's current size. */
@Composable
private fun Grid(context: Context, data: WidgetData, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val cellWidth = 72.dp
    val cellHeight = 86.dp
    val columns = max(1, ((width - 24.dp) / cellWidth).toInt())
    val rows = max(1, ((height - 64.dp) / cellHeight).toInt())
    val shown = data.games.take(columns * rows).chunked(columns)
    Column(GlanceModifier.fillMaxSize()) {
        shown.forEach { line ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                line.forEach { game -> GameCell(context, game, data.round) }
                // Keep spacing even when the last row is short.
                repeat(columns - line.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.GameCell(context: Context, game: WidgetGame, round: Boolean) {
    val launch = Intent(context, WidgetLaunchActivity::class.java)
        .setData(Uri.parse("pocketdeck://game/${game.id}"))
        .putExtra(WidgetLaunchActivity.EXTRA_GAME_ID, game.id)
    Column(
        GlanceModifier.defaultWeight().clickable(actionStartActivity(launch)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (game.icon != null) {
            Image(ImageProvider(game.icon), game.title, GlanceModifier.size(54.dp))
        } else {
            // No artwork yet: a tile in the system's colour with its short name.
            Box(
                GlanceModifier
                    .size(54.dp)
                    .cornerRadius(if (round) 27.dp else 13.dp)
                    .background(ColorProvider(Color(game.color))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    game.badge ?: game.title.take(2),
                    style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(game.title, style = TextStyle(color = White, fontSize = 11.sp), maxLines = 1)
    }
}
