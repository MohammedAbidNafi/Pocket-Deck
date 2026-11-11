package com.abidnafi.pocketdeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abidnafi.pocketdeck.input.UiFeedback
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette

/** Pill button. [primary] buttons are filled with the accent colour. */
@Composable
fun ConsoleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val accent = LocalAccent.current
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocusChange = onFocusChange,
        corner = 26.dp,
        focusScale = 1.05f,
    ) { focused ->
        val bg = when {
            primary -> accent
            focused -> Color.White
            else -> Palette.SurfaceHigher.copy(alpha = 0.85f)
        }
        val fg = when {
            primary -> Color.White
            focused -> Palette.Background
            else -> Palette.Text
        }
        Row(
            Modifier
                .background(bg)
                .height(52.dp)
                .padding(horizontal = if (icon != null) 22.dp else 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(label, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1)
        }
    }
}

/** Round icon-only button used in the top bar. */
@Composable
fun IconPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    FocusCard(
        onClick = onClick, modifier = modifier, corner = 20.dp, focusScale = 1.1f,
        focusRequester = focusRequester, onFocusChange = onFocusChange,
    ) { focused ->
        Box(
            Modifier
                .size(40.dp)
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = if (focused) Palette.Background else Palette.Text, modifier = Modifier.size(22.dp))
        }
    }
}

/** Selectable filter chip (library platform tabs, etc.). */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    count: Int? = null,
) {
    val accent = LocalAccent.current
    FocusCard(onClick = onClick, modifier = modifier, corner = 18.dp, focusScale = 1.06f, focusRequester = focusRequester) { focused ->
        Row(
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        selected -> accent
                        else -> Color.White.copy(alpha = 0.07f)
                    },
                )
                .height(36.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (focused) Palette.Background else Palette.Text
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text("$count", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.6f))
            }
        }
    }
}

/** A full-width settings / list row with an optional trailing value. */
@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    toggle: Boolean? = null,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    danger: Boolean = false,
) {
    val accent = LocalAccent.current
    FocusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        corner = 12.dp,
        focusScale = 1.015f,
        focusRequester = focusRequester,
        onFocusChange = onFocusChange,
        clickSound = if (toggle != null) UiFeedback.Sound.TOGGLE else UiFeedback.Sound.SELECT,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Palette.SurfaceHigher else Palette.Surface.copy(alpha = 0.7f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (danger) Palette.Danger else if (focused) accent else Palette.TextDim, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) Palette.Danger else Palette.Text)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = if (focused) Palette.Text else Palette.TextDim, maxLines = 1)
            }
            if (toggle != null) {
                Spacer(Modifier.width(12.dp))
                Switch(toggle, accent)
            }
        }
    }
}

@Composable
private fun Switch(on: Boolean, accent: Color) {
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .background(if (on) accent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(13.dp))
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(10.dp)))
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.TextDim,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
    )
}

/** Big friendly placeholder for empty screens. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Palette.TextFaint, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
    }
}

/** Remembers a boolean that flips when this composable gains/loses focus (for focus-dependent styling). */
@Composable
fun rememberFocusFlag(): Pair<Boolean, (Boolean) -> Unit> {
    var flag by remember { mutableStateOf(false) }
    return flag to { v: Boolean -> flag = v }
}
