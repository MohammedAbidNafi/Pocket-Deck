package com.abidnafi.pocketdeck.ui.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abidnafi.pocketdeck.data.GlyphStyle
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.input.LocalController
import com.abidnafi.pocketdeck.input.LocalHints
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.theme.Palette

private enum class Face { BOTTOM, RIGHT, LEFT, TOP }

private data class GlyphLook(val text: String, val color: Color, val round: Boolean)

private fun faceLook(style: GlyphStyle, face: Face): GlyphLook = when (style) {
    GlyphStyle.XBOX -> when (face) {
        Face.BOTTOM -> GlyphLook("A", Color(0xFF6CC644), true)
        Face.RIGHT -> GlyphLook("B", Color(0xFFE5484D), true)
        Face.LEFT -> GlyphLook("X", Color(0xFF3B8CF0), true)
        Face.TOP -> GlyphLook("Y", Color(0xFFF2B630), true)
    }
    GlyphStyle.PLAYSTATION -> when (face) {
        Face.BOTTOM -> GlyphLook("✕", Color(0xFF8AB4F8), true)
        Face.RIGHT -> GlyphLook("○", Color(0xFFF28B96), true)
        Face.LEFT -> GlyphLook("□", Color(0xFFE59CE5), true)
        Face.TOP -> GlyphLook("△", Color(0xFF6FD9B5), true)
    }
    GlyphStyle.NINTENDO -> when (face) {
        Face.BOTTOM -> GlyphLook("B", Color.White, true)
        Face.RIGHT -> GlyphLook("A", Color.White, true)
        Face.LEFT -> GlyphLook("Y", Color.White, true)
        Face.TOP -> GlyphLook("X", Color.White, true)
    }
}

private fun look(style: GlyphStyle, swapAB: Boolean, btn: Btn): GlyphLook {
    val pill = Color(0xFFD6DCE6)
    return when (btn) {
        Btn.CONFIRM -> faceLook(style, if (swapAB) Face.RIGHT else Face.BOTTOM)
        Btn.BACK -> faceLook(style, if (swapAB) Face.BOTTOM else Face.RIGHT)
        Btn.X -> faceLook(style, Face.LEFT)
        Btn.Y -> faceLook(style, Face.TOP)
        Btn.L1 -> GlyphLook(when (style) { GlyphStyle.XBOX -> "LB"; GlyphStyle.PLAYSTATION -> "L1"; GlyphStyle.NINTENDO -> "L" }, pill, false)
        Btn.R1 -> GlyphLook(when (style) { GlyphStyle.XBOX -> "RB"; GlyphStyle.PLAYSTATION -> "R1"; GlyphStyle.NINTENDO -> "R" }, pill, false)
        Btn.L2 -> GlyphLook(when (style) { GlyphStyle.XBOX -> "LT"; GlyphStyle.PLAYSTATION -> "L2"; GlyphStyle.NINTENDO -> "ZL" }, pill, false)
        Btn.R2 -> GlyphLook(when (style) { GlyphStyle.XBOX -> "RT"; GlyphStyle.PLAYSTATION -> "R2"; GlyphStyle.NINTENDO -> "ZR" }, pill, false)
        Btn.START -> GlyphLook(when (style) { GlyphStyle.XBOX -> "☰"; GlyphStyle.PLAYSTATION -> "OPT"; GlyphStyle.NINTENDO -> "+" }, pill, true)
        Btn.SELECT -> GlyphLook(when (style) { GlyphStyle.XBOX -> "⧉"; GlyphStyle.PLAYSTATION -> "SHR"; GlyphStyle.NINTENDO -> "−" }, pill, true)
    }
}

/** The four face buttons of [style] laid out in their diamond, as on the controller. */
@Composable
fun FaceButtonsPreview(style: GlyphStyle, size: Dp = 22.dp) {
    @Composable
    fun face(f: Face) {
        val l = faceLook(style, f)
        Box(
            Modifier.size(size).background(Color(0xFF2B3342), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(l.text, color = l.color, fontSize = (size.value * 0.56f).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
    Box(Modifier.size(size * 3)) {
        Box(Modifier.align(Alignment.TopCenter)) { face(Face.TOP) }
        Box(Modifier.align(Alignment.CenterStart)) { face(Face.LEFT) }
        Box(Modifier.align(Alignment.CenterEnd)) { face(Face.RIGHT) }
        Box(Modifier.align(Alignment.BottomCenter)) { face(Face.BOTTOM) }
    }
}

/** A controller button glyph matching the user's chosen controller family. */
@Composable
fun ButtonGlyph(btn: Btn, size: Dp = 22.dp) {
    val settings = LocalSettings.current
    val l = look(settings.glyphStyle, settings.swapAB, btn)
    val shape = if (l.round && l.text.length == 1) CircleShape else RoundedCornerShape(size / 2)
    Box(
        Modifier
            .height(size)
            .defaultMinSize(minWidth = size)
            .background(Color(0xFF2B3342), shape)
            .padding(horizontal = if (l.text.length > 1) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            l.text,
            color = l.color,
            fontSize = (size.value * if (l.text.length > 1) 0.44f else 0.56f).sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Bottom bar listing what each button does on the current screen. Tapping a hint presses it. */
@Composable
fun HintBar(modifier: Modifier = Modifier) {
    val hints = LocalHints.current.active
    val dispatcher = LocalController.current
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Background.copy(alpha = 0.92f))))
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { hint -> HintItem(hint) {
            when (hint.btn) {
                Btn.BACK -> back?.onBackPressed()
                Btn.CONFIRM -> Unit
                else -> dispatcher.dispatch(hint.btn)
            }
        } }
    }
}

@Composable
private fun HintItem(hint: Hint, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 22.dp)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonGlyph(hint.btn)
        Spacer(Modifier.width(8.dp))
        Text(hint.label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

@Composable
fun GlyphLabel(btn: Btn, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ButtonGlyph(btn, 20.dp)
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
    }
}
