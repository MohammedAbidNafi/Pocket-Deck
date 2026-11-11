package com.abidnafi.pocketdeck.data.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppVisual(
    val icon: ImageBitmap,
    /** Android TV banner (16:9) when the app ships one. */
    val banner: ImageBitmap?,
    /** Vibrant + dark colours pulled from the icon, as ARGB. */
    val primaryColor: Int,
    val darkColor: Int,
    /** The icon's artwork unmasked and edge to edge (adaptive icons only), for filled tiles. */
    val fullBleed: ImageBitmap? = null,
)

/** Loads app icons, TV banners and their palette colours, with an in-memory cache. */
class AppIcons(private val context: Context) {
    private val cache = LruCache<String, AppVisual>(200)

    fun cached(pkg: String): AppVisual? = cache.get(pkg)

    suspend fun load(pkg: String): AppVisual? {
        cache.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            runCatching {
                val drawable = pm.getApplicationIcon(pkg)
                val icon = drawable.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                val fullBleed = (drawable as? AdaptiveIconDrawable)?.let(::renderFullBleed)
                val banner = runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
                    ?.let { it.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888) }
                val palette = Palette.from(icon).maximumColorCount(16).generate()
                val fallback = 0xFF2A3445.toInt()
                val primary = palette.getVibrantColor(palette.getDominantColor(fallback))
                val dark = palette.getDarkMutedColor(palette.getDarkVibrantColor(darken(primary)))
                AppVisual(icon.asImageBitmap(), banner?.asImageBitmap(), primary, dark, fullBleed?.asImageBitmap())
            }.getOrNull()?.also { cache.put(pkg, it) }
        }
    }

    /**
     * The icon's own artwork without the system's mask, as a square: the background and
     * foreground layers drawn so the launcher-visible area (the middle 72 of the 108-unit
     * canvas) fills the bitmap. Lets tiles of any shape be filled edge to edge.
     */
    private fun renderFullBleed(icon: AdaptiveIconDrawable): Bitmap {
        val bitmap = Bitmap.createBitmap(FULL_PX, FULL_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pad = (FULL_PX * 0.25f).toInt() // 108 units shown at 72 -> 18 units each side
        listOfNotNull(icon.background, icon.foreground).forEach { layer ->
            val copy = layer.constantState?.newDrawable()?.mutate() ?: layer
            copy.setBounds(-pad, -pad, FULL_PX + pad, FULL_PX + pad)
            copy.draw(canvas)
        }
        return bitmap
    }

    private fun darken(argb: Int): Int {
        val r = (argb shr 16 and 0xFF) / 3
        val g = (argb shr 8 and 0xFF) / 3
        val b = (argb and 0xFF) / 3
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private companion object {
        const val ICON_PX = 192
        const val FULL_PX = 384
        const val BANNER_W = 640
        const val BANNER_H = 360
    }
}
