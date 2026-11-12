package com.abidnafi.pocketdeck.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abidnafi.pocketdeck.data.TileShape
import com.abidnafi.pocketdeck.data.TileSize
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.theme.Palette

/** Tile dimensions scaled to the screen height so shelves fit phones, handhelds and TVs. */
data class TileMetrics(val coverWidth: Dp, val wideWidth: Dp) {
    val coverHeight: Dp get() = coverWidth * 4f / 3f
    val wideHeight: Dp get() = wideWidth * 9f / 16f
}

@Composable
fun rememberTileMetrics(): TileMetrics {
    val size = LocalSettings.current.tileSize
    val height = LocalConfiguration.current.screenHeightDp
    return remember(size, height) {
        val base = when (size) {
            TileSize.SMALL -> 124f
            TileSize.MEDIUM -> 150f
            TileSize.LARGE -> 182f
        }
        val scale = (height / 760f).coerceIn(0.6f, 1.5f)
        TileMetrics(coverWidth = (base * scale).dp, wideWidth = (base * scale * 2.05f).dp)
    }
}

val ScreenPadding = 48.dp

/**
 * One game tile. The title appears under the tile while it is focused (Switch-style),
 * so unfocused shelves stay clean.
 */
@Composable
fun GameTile(
    game: GameEntity,
    style: ArtStyle,
    metrics: TileMetrics,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    memory: FocusMemory? = null,
    memoryKey: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val titleAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "titleAlpha")
    val shape = LocalSettings.current.tileShape
    // Rectangle: wide rows are 16:9, box-art rows are 3:4. Square / round: one side, sized so
    // the tile still fits the same grid cells (box-art width) or row height (wide rows).
    val width: Dp
    val height: Dp
    if (shape == TileShape.RECTANGLE) {
        width = if (style == ArtStyle.WIDE) metrics.wideWidth else metrics.coverWidth
        height = if (style == ArtStyle.WIDE) metrics.wideHeight else metrics.coverHeight
    } else {
        val side = if (style == ArtStyle.WIDE) metrics.wideHeight else metrics.coverWidth
        width = side
        height = side
    }
    val round = shape == TileShape.ROUND
    val platform = LocalAppContainer.current.platforms[game.platformId]

    Column(modifier.width(width), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        FocusCard(
            onClick = onClick,
            onLongClick = onMenu,
            corner = if (round) width / 2 else 14.dp,
            focusRequester = memoryKey?.let { memory?.requester(it) },
            onFocusChange = {
                focused = it
                if (it) {
                    memoryKey?.let { k -> memory?.lastKey = k }
                    onFocused()
                }
            },
            modifier = Modifier.size(width, height),
        ) {
            // Round and square tiles are filled edge to edge by the icon / box art.
            GameArt(
                game,
                if (shape == TileShape.RECTANGLE) style else ArtStyle.FILL,
                Modifier.fillMaxSize().background(Palette.SurfaceHigh),
            )
            if (game.favorite) {
                Icon(
                    Icons.Rounded.Star, "Favourite", tint = Palette.Favorite,
                    // Corners of a circle are cut away, so badges move to the middle edges there.
                    modifier = Modifier
                        .align(if (round) Alignment.TopCenter else Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
            val badge = when {
                game.customArt != null -> null
                game.type == GameType.ROM -> platform?.shortName
                game.type == GameType.STREAM -> "PC"
                else -> null
            }
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(if (round) Alignment.BottomCenter else Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            game.title,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(titleAlpha),
        )
    }
}

/** A titled horizontal row of tiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Shelf(
    title: String,
    games: List<GameEntity>,
    style: ArtStyle,
    metrics: TileMetrics,
    keyPrefix: String,
    memory: FocusMemory,
    onOpen: (GameEntity) -> Unit,
    onFocused: (GameEntity) -> Unit,
    onMenu: (GameEntity) -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val margin = with(LocalDensity.current) { (ScreenPadding + 8.dp).toPx() }
    val spec = remember(margin) { MarginBringIntoViewSpec(margin) }
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Text(trailing, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(if (style == ArtStyle.WIDE) 20.dp else 16.dp),
            ) {
                items(games, key = { it.id }) { game ->
                    GameTile(
                        game = game,
                        style = style,
                        metrics = metrics,
                        onClick = { onOpen(game) },
                        onFocused = { onFocused(game) },
                        onMenu = { onMenu(game) },
                        memory = memory,
                        memoryKey = "$keyPrefix/${game.id}",
                    )
                }
            }
        }
    }
}
