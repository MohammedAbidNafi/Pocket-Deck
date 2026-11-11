package com.abidnafi.pocketdeck.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.abidnafi.pocketdeck.data.art.AppVisual
import com.abidnafi.pocketdeck.data.art.LibretroThumbnails
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.db.GameType
import com.abidnafi.pocketdeck.data.model.Platform
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalArtNetwork
import java.io.File

enum class ArtStyle {
    /** Portrait 3:4 tile. */
    COVER,
    /** 16:9 tile; prefers screenshots / TV banners. */
    WIDE,
    /** Full-screen background behind the hero. */
    BACKDROP,
    /** Round / square tiles: the icon (or box art) fills the whole tile, like a launcher icon. */
    FILL,
}

/** Draws the best available artwork for a game, falling back to a generated card. */
@Composable
fun GameArt(game: GameEntity, style: ArtStyle, modifier: Modifier = Modifier) {
    Box(modifier) {
        when {
            game.customArt != null -> AsyncImage(
                model = File(game.customArt),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            game.type == GameType.ROM -> RomArt(game, style)
            else -> AppArt(game, style)
        }
    }
}

@Composable
fun rememberAppVisual(pkg: String?): AppVisual? {
    val icons = LocalAppContainer.current.icons
    val visual by produceState(pkg?.let(icons::cached), pkg) {
        if (pkg != null && value == null) value = icons.load(pkg)
    }
    return visual
}

@Composable
private fun AppArt(game: GameEntity, style: ArtStyle) {
    val visual = rememberAppVisual(game.packageName)
    val primary = visual?.let { Color(it.primaryColor) } ?: Color(0xFF2A3445)
    val dark = visual?.let { Color(it.darkColor) } ?: Color(0xFF141922)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.95f), dark))),
        contentAlignment = Alignment.Center,
    ) {
        if (visual == null) return@Box
        if (style == ArtStyle.FILL) {
            // The icon's own artwork, edge to edge. Older non-adaptive icons can't be unmasked,
            // so they're scaled to fill as much of the tile as they can.
            val full = visual.fullBleed
            if (full != null) {
                Image(full, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Image(visual.icon, game.title, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            }
            return@Box
        }
        if (style != ArtStyle.COVER && visual.banner != null) {
            Image(visual.banner, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            return@Box
        }
        when (style) {
            ArtStyle.BACKDROP -> Image(
                visual.icon, null,
                Modifier
                    .fillMaxSize()
                    .alpha(0.35f)
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier),
                contentScale = ContentScale.Crop,
            )
            else -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Blurred icon fills the card behind a crisp centred icon.
                if (Build.VERSION.SDK_INT >= 31) {
                    Image(visual.icon, null, Modifier.fillMaxSize().blur(28.dp).alpha(0.55f), contentScale = ContentScale.Crop)
                }
                val iconSize = minOf(maxWidth, maxHeight) * 0.56f
                Image(
                    visual.icon, game.title,
                    Modifier
                        .size(iconSize)
                        .shadow(18.dp, RoundedCornerShape(iconSize * 0.22f), clip = false),
                )
            }
        }
    }
}

@Composable
private fun RomArt(game: GameEntity, style: ArtStyle) {
    val platform = LocalAppContainer.current.platforms[game.platformId]
    val network = LocalArtNetwork.current
    val context = LocalContext.current
    val urls = remember(game.fileName, platform?.id, style) {
        val name = game.fileName ?: return@remember emptyList()
        val system = platform?.libretro ?: return@remember emptyList()
        val box = LibretroThumbnails.candidates(system, LibretroThumbnails.Kind.BOXART, name)
        when (style) {
            ArtStyle.COVER, ArtStyle.FILL -> box
            else -> LibretroThumbnails.candidates(system, LibretroThumbnails.Kind.SNAP, name) + box
        }
    }
    var index by remember(urls) { mutableIntStateOf(0) }
    var loaded by remember(urls) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (!loaded) FallbackArt(game.title, platform, style)
        val url = urls.getOrNull(index) ?: return@Box
        val request = remember(url, network) {
            ImageRequest.Builder(context)
                .data(url)
                .networkCachePolicy(if (network) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .build()
        }
        val isBox = url.contains("/Named_Boxarts/")
        if (style == ArtStyle.COVER && loaded && Build.VERSION.SDK_INT >= 31) {
            // Box art keeps its own aspect ratio; fill the card with a blurred copy behind it.
            AsyncImage(request, null, Modifier.fillMaxSize().blur(24.dp).alpha(0.6f), contentScale = ContentScale.Crop)
        }
        AsyncImage(
            model = request,
            contentDescription = game.title,
            contentScale = if (style == ArtStyle.COVER && isBox) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (style == ArtStyle.COVER && isBox) Modifier.padding(6.dp) else Modifier),
            onSuccess = { loaded = true },
            onError = { index++ },
        )
    }
}

/** Generated card in the platform's colour, used until (or instead of) real artwork. */
@Composable
fun FallbackArt(title: String, platform: Platform?, style: ArtStyle) {
    val base = platform?.let { Color(it.argb) } ?: Color(0xFF2A3445)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.9f), Color(0xFF0E1219)))),
    ) {
        if (style == ArtStyle.BACKDROP) return@Box
        platform?.let {
            Text(
                it.shortName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (style == ArtStyle.WIDE) 20.sp else 16.sp,
            lineHeight = if (style == ArtStyle.WIDE) 24.sp else 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(14.dp),
        )
    }
}
