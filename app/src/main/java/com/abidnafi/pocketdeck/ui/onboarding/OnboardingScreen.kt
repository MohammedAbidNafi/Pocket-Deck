package com.abidnafi.pocketdeck.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.GlyphStyle
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.ControllerHandler
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.input.Priority
import com.abidnafi.pocketdeck.ui.AppLogo
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalFeedback
import com.abidnafi.pocketdeck.input.UiFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.CubicBezierEasing
import android.view.HapticFeedbackConstants
import kotlin.math.hypot
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.Routes
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.FaceButtonsPreview
import com.abidnafi.pocketdeck.ui.components.FocusCard
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.components.ScreenPadding
import com.abidnafi.pocketdeck.ui.components.SettingRow
import com.abidnafi.pocketdeck.ui.settings.rememberRomFolderPicker
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEPS = 4

/** First-run setup: welcome, controller style, look (colour / tile shape / size), add games, then a short "setting up" hand-off. */
@Composable
fun OnboardingScreen(nav: NavController) {
    val container = LocalAppContainer.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var finishing by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = step > 0 || finishing) { if (!finishing) step-- }
    ButtonHints(Hint(Btn.CONFIRM, "Select"), Hint(Btn.BACK, if (step > 0) "Previous" else "Exit"))

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(LocalAccent.current.copy(alpha = 0.22f), Palette.Background), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 * dir }) togetherWith fadeOut(tween(140))
            },
            label = "onboarding",
        ) { s ->
            key(s) {
                when (s) {
                    0 -> Welcome { step = 1 }
                    1 -> ControllerStep { step = 2 }
                    2 -> LookStep { step = 3 }
                    else -> GamesStep(nav) { finishing = true }
                }
            }
        }
        StepDots(step, Modifier.align(Alignment.TopCenter).padding(top = 18.dp))

        AnimatedVisibility(finishing, enter = fadeIn(tween(350)), exit = fadeOut(tween(200))) {
            SettingUp(
                work = { container.library.syncInstalledApps() },
                onDone = {
                    container.settings.setOnboardingDone(true)
                    nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
    }
}

@Composable
private fun Page(title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenPadding, end = ScreenPadding, top = 44.dp, bottom = 64.dp),
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
        Spacer(Modifier.height(22.dp))
        content()
    }
}

/** Logo that gently floats, with a soft breathing glow behind it. */
@Composable
private fun FloatingLogo(size: androidx.compose.ui.unit.Dp) {
    val accent = LocalAccent.current
    val t = rememberInfiniteTransition(label = "float")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strength = 0.26f + 0.08f * bob
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = strength), Color.Transparent), radius = this.size.minDimension * 0.5f),
                radius = this.size.minDimension * 0.5f,
            )
        }
        AppLogo(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = bob * 6.dp.toPx() },
        )
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val next = remember { FocusRequester() }
    Row(
        Modifier
            .widthIn(max = 980.dp)
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingLogo(210.dp)
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text("WELCOME TO", style = MaterialTheme.typography.labelLarge, color = LocalAccent.current, fontWeight = FontWeight.Bold)
            Text("PocketDeck", style = MaterialTheme.typography.displayMedium, color = Palette.Text, fontWeight = FontWeight.Bold)
            Text(
                "Your games, front and centre. Built for playing with a controller.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextDim,
            )
            Spacer(Modifier.height(18.dp))
            Feature(Icons.Rounded.Android, "Every Android game you have installed")
            Feature(Icons.Rounded.SportsEsports, "Retro ROMs, launched straight into your emulators")
            Feature(Icons.Rounded.Gamepad, "Move with the D-pad, pick with a button. Touch works too")
            Spacer(Modifier.height(20.dp))
            ConsoleButton("Get started", onNext, icon = Icons.AutoMirrored.Rounded.ArrowForward, primary = true, focusRequester = next)
        }
    }
    InitialFocus(null, next)
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    val accent = LocalAccent.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
    }
}

@Composable
private fun ControllerStep(onNext: () -> Unit) {
    val settings = LocalSettings.current
    val repo = LocalAppContainer.current.settings
    val scope = rememberCoroutineScope()
    val requesters = remember { GlyphStyle.entries.associateWith { FocusRequester() } }
    var picked by remember { mutableStateOf<GlyphStyle?>(null) }

    fun pick(style: GlyphStyle) {
        if (picked != null) return
        picked = style
        scope.launch {
            repo.setGlyphStyle(style)
            delay(320) // let the tick show before moving on
            onNext()
        }
    }

    Page("Which controller do you use?", "Pick one and button prompts will match it. You can change this later in Settings.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlyphStyle.entries.forEach { style ->
                val (name, letters) = when (style) {
                    GlyphStyle.XBOX -> "Xbox" to "A  B  X  Y"
                    GlyphStyle.PLAYSTATION -> "PlayStation" to "✕  ○  □  △"
                    GlyphStyle.NINTENDO -> "Nintendo" to "B  A  Y  X"
                }
                ControllerCard(
                    name = name,
                    letters = letters,
                    style = style,
                    selected = (picked ?: settings.glyphStyle) == style,
                    onClick = { pick(style) },
                    focusRequester = requesters.getValue(style),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingRow(
            "Swap confirm and back", { scope.launch { repo.setSwapAB(!settings.swapAB) } }, icon = Icons.Rounded.SwapHoriz,
            subtitle = "Confirm with the right face button, like on Nintendo consoles", toggle = settings.swapAB,
        )
    }
    InitialFocus(null, requesters.getValue(settings.glyphStyle))
}

@Composable
private fun ControllerCard(
    name: String,
    letters: String,
    style: GlyphStyle,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    FocusCard(onClick = onClick, modifier = modifier, corner = 22.dp, focusScale = 1.05f, focusRequester = focusRequester) { focused ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Palette.SurfaceHigher else Palette.Surface.copy(alpha = 0.85f))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                FaceButtonsPreview(style, 24.dp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Box(Modifier.size(20.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            }
            Text(letters, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
    }
}

@Composable
private fun GamesStep(nav: NavController, onFinish: () -> Unit) {
    val library = LocalAppContainer.current.library
    val folders by library.folders.collectAsState(emptyList())
    val games by library.games.collectAsState(emptyList())
    val scan by library.scanState.collectAsState()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val pick = rememberRomFolderPicker { uri -> scope.launch { library.addRomFolder(uri, null) } }
    Page(
        "Add your games",
        "Installed Android games are found automatically. Add a folder of ROMs to bring in your emulated games — " +
            "sub-folders named after systems (snes, gba, psx…) are sorted for you.",
    ) {
        SettingRow(
            "Add ROM folder", pick, icon = Icons.Rounded.CreateNewFolder, focusRequester = first,
            value = if (folders.isEmpty()) null else "${folders.size} added",
            subtitle = if (scan.running) "Scanning… ${scan.found} found" else folders.joinToString { it.name }.ifEmpty { null },
        )
        Spacer(Modifier.height(6.dp))
        SettingRow("Pick other apps", { nav.navigate(Routes.ADD_APP) }, icon = Icons.Rounded.Android, subtitle = "Sideloaded games, streaming apps…")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConsoleButton("Done", onFinish, icon = Icons.Rounded.Check, primary = true)
            Spacer(Modifier.width(18.dp))
            Text("${games.count { !it.hidden }} games in your library", style = MaterialTheme.typography.titleMedium, color = Palette.TextDim)
        }
    }
    InitialFocus(null, first)
}

/**
 * Full-screen "Setting up your dashboard" moment shown after the last step: the logo pulses
 * inside a spinning ring while the library sync finishes, then hands off to the main screen.
 */
@Composable
private fun SettingUp(work: suspend () -> Unit, onDone: suspend () -> Unit) {
    val accent = LocalAccent.current
    val progress = remember { Animatable(0f) }
    var line by remember { mutableIntStateOf(0) }
    val lines = listOf("Finding your games…", "Getting the artwork ready…", "Almost there…", "Ready!")
    val warp = remember { Animatable(0f) }
    val feedback = LocalFeedback.current
    val view = LocalView.current

    // Swallow every button while this runs.
    ControllerHandler(Priority.OVERLAY) { true }
    ButtonHints(priority = Priority.OVERLAY)

    LaunchedEffect(Unit) {
        val job = launch { runCatching { work() } }
        val ticker = launch {
            while (true) {
                delay(700)
                line = (line + 1).coerceAtMost(lines.lastIndex - 1)
            }
        }
        progress.animateTo(0.85f, tween(1500, easing = FastOutSlowInEasing))
        job.join()
        progress.animateTo(1f, tween(300))
        ticker.cancel()
        line = lines.lastIndex
        delay(450)
        // The big moment: boom, rumble, and the logo rushes at the screen in a flood of light.
        feedback.play(UiFeedback.Sound.INTRO)
        if (feedback.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        warp.animateTo(1f, tween(820, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)))
        onDone()
    }

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    val pulse by spin.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(170.dp)
                    .graphicsLayer {
                        // Warp: the logo and ring zoom toward the viewer and spin up.
                        val w = warp.value
                        val s = 1f + w * w * 9f
                        scaleX = s
                        scaleY = s
                        rotationZ = w * 25f
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 4.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)),
                        radius = size.minDimension / 2,
                    )
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, accent, Color.White)),
                        startAngle = angle * (1f + warp.value * 3f), sweepAngle = 270f + warp.value * 90f, useCenter = false,
                        topLeft = Offset(w, w), size = Size(size.width - 2 * w, size.height - 2 * w),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                AppLogo(Modifier.size(118.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
            }
            Column(
                Modifier.graphicsLayer {
                    // Text and progress fall away first.
                    val w = warp.value
                    alpha = (1f - w * 3f).coerceIn(0f, 1f)
                    translationY = w * 60.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(22.dp))
                Text("Setting up your dashboard", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                Spacer(Modifier.height(6.dp))
                // A fixed-width row centres the line; inside it, each line fills the width and is
                // centred as text, so lines of any length sit right under the title.
                AnimatedContent(
                    targetState = line,
                    // Old line slides up and out as the new one rises in; never clipped.
                    transitionSpec = {
                        ((fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it }))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(420.dp),
                    label = "line",
                ) { i ->
                    Text(
                        lines[i],
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (i == lines.lastIndex) accent else Palette.TextDim,
                        fontWeight = if (i == lines.lastIndex) FontWeight.Bold else null,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .width(260.dp)
                        .height(6.dp)
                        .background(Palette.SurfaceHigher, RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.value)
                            .height(6.dp)
                            .background(Brush.horizontalGradient(listOf(accent, Color.White)), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        // Flood of light bursting out from the logo, ending in a bright accent glow.
        Canvas(Modifier.fillMaxSize()) { drawBurst(warp.value, accent) }
    }
}

private fun DrawScope.drawBurst(w: Float, accent: Color) {
    if (w <= 0f) return
    val diag = hypot(size.width, size.height)
    val radius = (diag * 0.75f * w).coerceAtLeast(1f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.95f * w),
            0.35f to accent.copy(alpha = 0.9f * w),
            1f to accent.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
        radius = radius,
    )
    // Light rays streaking outward.
    val rays = 18
    for (i in 0 until rays) {
        val a = Math.toRadians(i * 360.0 / rays + w * 40.0)
        val inner = radius * 0.15f
        val outer = radius * (0.7f + 0.3f * ((i * 37) % 10) / 10f)
        drawLine(
            Color.White.copy(alpha = 0.35f * w),
            start = center + Offset((kotlin.math.cos(a) * inner).toFloat(), (kotlin.math.sin(a) * inner).toFloat()),
            end = center + Offset((kotlin.math.cos(a) * outer).toFloat(), (kotlin.math.sin(a) * outer).toFloat()),
            strokeWidth = 3.dp.toPx() * (1f + w * 3f),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun StepDots(step: Int, modifier: Modifier) {
    val accent = LocalAccent.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                    .background(if (i == step) accent else Palette.TextFaint, CircleShape),
            )
        }
    }
}
