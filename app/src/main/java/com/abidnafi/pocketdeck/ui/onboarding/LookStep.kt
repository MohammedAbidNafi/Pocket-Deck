package com.abidnafi.pocketdeck.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abidnafi.pocketdeck.data.TileShape
import com.abidnafi.pocketdeck.data.TileSize
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.components.ArtStyle
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.FilterChip
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.GameArt
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.rememberTileMetrics
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Setup step: accent colour, tile shape and tile size, with a live preview of one of the
 * player's own games on the right. Every choice applies immediately (it's the real setting).
 */
@Composable
internal fun LookStep(onNext: () -> Unit) {
    val settings = LocalSettings.current
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val swatches = remember { Palette.accents.map { FocusRequester() } }
    val continueButton = remember { FocusRequester() }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 36.dp, bottom = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Make it yours", style = MaterialTheme.typography.headlineLarge, color = Palette.Text)
            Text("Changes apply right away. You can change them later in Settings.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)

            Label("Accent colour")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palette.accents.forEachIndexed { i, accent ->
                    Swatch(accent.color, selected = i == settings.accentIndex, focusRequester = swatches[i]) {
                        scope.launch { repo.setAccent(i) }
                    }
                }
            }

            Label("Tile shape")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileShape.entries.forEach { shape ->
                    FilterChip(
                        when (shape) {
                            TileShape.ROUND -> "Round"
                            TileShape.SQUARE -> "Square"
                            TileShape.RECTANGLE -> "Rectangle"
                        },
                        settings.tileShape == shape,
                        { scope.launch { repo.setTileShape(shape) } },
                    )
                }
            }

            Label("Tile size")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileSize.entries.forEach { size ->
                    FilterChip(
                        size.name.lowercase().replaceFirstChar { it.uppercase() },
                        settings.tileSize == size,
                        { scope.launch { repo.setTileSize(size) } },
                        // Nothing is below the size chips: down goes to Continue.
                        modifier = Modifier.focusProperties { down = continueButton },
                    )
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        PreviewPanel(Modifier.weight(1f).fillMaxHeight()) {
            ConsoleButton("Continue", onNext, icon = Icons.AutoMirrored.Rounded.ArrowForward, primary = true, focusRequester = continueButton)
        }
    }
    InitialFocus(null, swatches[settings.accentIndex.coerceIn(0, swatches.lastIndex)])
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Swatch(color: Color, selected: Boolean, focusRequester: FocusRequester, onClick: () -> Unit) {
    FocusCard(onClick = onClick, corner = 22.dp, focusScale = 1.15f, glowColor = color, focusRequester = focusRequester) {
        Box(
            Modifier
                .size(44.dp)
                .background(color, CircleShape)
                .border(3.dp, if (selected) Color.White else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * A small mock of the Home screen using one of the player's own games, drawn with the current
 * accent, tile shape and tile size, so changes show up as they're made.
 */
@Composable
private fun PreviewPanel(modifier: Modifier, action: @Composable () -> Unit) {
    val accent = LocalAccent.current
    val settings = LocalSettings.current
    val metrics = rememberTileMetrics()
    val games by LocalAppContainer.current.library.games.collectAsState(emptyList())
    val sample = remember(games) {
        games.filter { !it.hidden && it.type != GameType.EMULATOR }
            .sortedWith(compareByDescending<GameEntity> { it.lastPlayedAt ?: 0L }.thenBy { it.type != GameType.APP })
            .firstOrNull()
    }

    // Same sizing rules as the real tiles (see GameTile).
    val shape = settings.tileShape
    val (fullW, fullH) = if (shape == TileShape.RECTANGLE) metrics.wideWidth to metrics.wideHeight else metrics.wideHeight to metrics.wideHeight
    // Keep the preview inside the panel on short screens: shrink proportionally if needed.
    val fit = minOf(1f, 104.dp / fullH)
    val w = fullW * fit
    val h = fullH * fit
    val corner: Dp = if (shape == TileShape.ROUND) w / 2 else 14.dp

    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Palette.Surface.copy(alpha = 0.85f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Text("PREVIEW", style = MaterialTheme.typography.labelMedium, color = accent)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Continue playing", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            // Accent-coloured Play button, like on a game's page.
            Row(
                Modifier.background(accent, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
            horizontalAlignment = if (shape == TileShape.ROUND) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            // The tile, drawn as if it were selected: accent glow plus white ring.
            Box(
                Modifier
                    .padding(10.dp)
                    .size(w, h)
                    .drawBehind {
                        val ring = 3.dp.toPx()
                        val r = corner.toPx()
                        for (i in 4 downTo 1) {
                            val grow = ring * i * 1.6f
                            drawRoundRect(
                                accent.copy(alpha = 0.10f * (5 - i) / 4f),
                                topLeft = Offset(-grow, -grow),
                                size = Size(size.width + grow * 2, size.height + grow * 2),
                                cornerRadius = CornerRadius(r + grow),
                                style = Stroke(ring * 1.6f),
                            )
                        }
                        drawRoundRect(
                            Color.White,
                            topLeft = Offset(-ring, -ring),
                            size = Size(size.width + ring * 2, size.height + ring * 2),
                            cornerRadius = CornerRadius(r + ring),
                            style = Stroke(ring),
                        )
                    }
                    .clip(RoundedCornerShape(corner))
                    .background(Palette.SurfaceHigh),
            ) {
                if (sample != null) {
                    GameArt(sample, if (shape == TileShape.RECTANGLE) ArtStyle.WIDE else ArtStyle.FILL, Modifier.fillMaxSize())
                }
            }
            Text(
                sample?.title ?: "Your game",
                style = MaterialTheme.typography.titleSmall, color = Palette.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
    }
}
