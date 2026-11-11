package com.abidnafi.pocketdeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0A0D13)
    val Surface = Color(0xFF141922)
    val SurfaceHigh = Color(0xFF1D2430)
    val SurfaceHigher = Color(0xFF283141)
    val Outline = Color(0x22FFFFFF)
    val Text = Color(0xFFEFF3F8)
    val TextDim = Color(0xFF9AA4B4)
    val TextFaint = Color(0xFF5E6878)
    val Danger = Color(0xFFFF5A5F)
    val Favorite = Color(0xFFFFC542)

    data class Accent(val name: String, val color: Color)

    val accents = listOf(
        Accent("Steam blue", Color(0xFF1A9FFF)),
        Accent("Neon red", Color(0xFFFF3D57)),
        Accent("Electric purple", Color(0xFF9B6BFF)),
        Accent("Mint", Color(0xFF2EE6A6)),
        Accent("Amber", Color(0xFFFFB020)),
        Accent("Pink", Color(0xFFFF5FC8)),
    )
}

val LocalAccent = staticCompositionLocalOf { Palette.accents[0].color }

private val Display = FontFamily.SansSerif

private val ConsoleTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = (-1).sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = (-0.5).sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

@Composable
fun PocketDeckTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Surface,
        onSurface = Palette.Text,
        surfaceVariant = Palette.SurfaceHigh,
        onSurfaceVariant = Palette.TextDim,
        outline = Palette.Outline,
        error = Palette.Danger,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = ConsoleTypography, content = content)
    }
}
