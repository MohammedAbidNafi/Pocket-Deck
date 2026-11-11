package com.abidnafi.pocketdeck.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import com.abidnafi.pocketdeck.input.UiFeedback
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.abidnafi.pocketdeck.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Same colour as the system splash backdrop (`@color/splash_bg`), so the hand-off is seamless. */
val SplashBackground = Color(0xFF0B0820)

private val RingColors = listOf(Color(0xFFB84DFF), Color(0xFF6A5CFF), Color(0xFF4DB0FF))

/**
 * Animated logo shown on cold start: the phone pops in, the neon ring sweeps around it,
 * the controller grips snap on from both sides and the Android head bounces up.
 * Built from the same vector parts as the launcher icon, so it ends on exactly the icon.
 * Tap to skip; skipped entirely when system animations are off.
 */
@Composable
fun StartupIntro(feedback: UiFeedback, onFinished: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val finish by rememberUpdatedState(onFinished)
    val phone = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val grips = remember { Animatable(0f) }
    val droid = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val animScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (animScale == 0f) {
            finish()
            return@LaunchedEffect
        }
        // Let the app underneath do its heavy first layout while the screen still shows the
        // plain splash colour, so the animation itself gets smooth frames. Also wait (briefly)
        // for the chime to be ready so sound and motion line up.
        repeat(3) { withFrameNanos { } }
        withTimeoutOrNull(450) { feedback.awaitLoaded(UiFeedback.Sound.INTRO) }
        delay(60)
        feedback.play(UiFeedback.Sound.INTRO)
        coroutineScope {
            launch { phone.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f)) }
            launch { delay(120); ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            launch {
                delay(360)
                launch { delay(90); feedback.tap(view) }
                grips.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 340f))
            }
            launch {
                delay(760)
                launch { delay(70); feedback.tap(view) }
                droid.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 380f))
            }
            launch {
                delay(820)
                glow.animateTo(1f, tween(260))
                glow.animateTo(0.4f, tween(500))
            }
        }
        delay(200)
        finish()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .pointerInput(Unit) { detectTapGestures { finish() } },
        contentAlignment = Alignment.Center,
    ) {
        val logo = min(min(maxWidth, maxHeight) * 0.62f, 320.dp)
        Box(Modifier.size(logo)) {
            // Soft halo that brightens as the parts land.
            Canvas(Modifier.fillMaxSize()) {
                val strength = 0.12f * ring.value + 0.3f * glow.value
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF6A5CFF).copy(alpha = strength), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.42f,
                    ),
                    radius = size.minDimension * 0.42f,
                )
            }
            Canvas(Modifier.fillMaxSize()) { drawRing(ring.value) }

            val slide = logo.value * 0.38f
            Image(
                painterResource(R.drawable.logo_grip_left), null,
                Modifier.fillMaxSize().graphicsLayer {
                    val g = grips.value
                    translationX = -(1f - g) * slide * density
                    rotationZ = -(1f - g) * 14f
                    alpha = g.coerceIn(0f, 1f)
                },
            )
            Image(
                painterResource(R.drawable.logo_grip_right), null,
                Modifier.fillMaxSize().graphicsLayer {
                    val g = grips.value
                    translationX = (1f - g) * slide * density
                    rotationZ = (1f - g) * 14f
                    alpha = g.coerceIn(0f, 1f)
                },
            )
            Image(
                painterResource(R.drawable.logo_phone), null,
                Modifier.fillMaxSize().graphicsLayer {
                    val p = phone.value
                    scaleX = 0.55f + 0.45f * p
                    scaleY = 0.55f + 0.45f * p
                    alpha = p.coerceIn(0f, 1f)
                },
            )
            Image(
                painterResource(R.drawable.logo_droid), null,
                Modifier.fillMaxSize().graphicsLayer {
                    val d = droid.value
                    // Grow out of the head's flat base, like popping up from behind the screen edge.
                    transformOrigin = TransformOrigin(0.5f, 56.5f / 108f)
                    scaleX = d
                    scaleY = d
                    translationY = (1f - d) * size.height * 0.03f
                    alpha = d.coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** The finished logo (no animation), drawn from the same vector parts as the launcher icon. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(modifier) {
        listOf(R.drawable.logo_ring, R.drawable.logo_grip_left, R.drawable.logo_grip_right, R.drawable.logo_phone, R.drawable.logo_droid)
            .forEach { Image(painterResource(it), null, Modifier.fillMaxSize()) }
    }
}

/** The logo ring on the 108-unit icon grid, swept clockwise from the top by [progress]. */
private fun DrawScope.drawRing(progress: Float) {
    if (progress <= 0f) return
    val u = size.width / 108f
    val radius = 21.35f * u
    val topLeft = Offset(54f * u - radius, 54f * u - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val brush = Brush.verticalGradient(RingColors, startY = 31f * u, endY = 77f * u)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    fun arcs() {
        drawArc(brush, -90f, sweep, false, topLeft, arcSize, alpha = 0.22f, style = Stroke(6.5f * u, cap = StrokeCap.Round))
        drawArc(brush, -90f, sweep, false, topLeft, arcSize, style = Stroke(3.1f * u, cap = StrokeCap.Round))
    }
    // Same horizontal cut as the icon: the ring is hidden where the grips cross it.
    clipRect(bottom = 41.3f * u) { arcs() }
    clipRect(top = 65.8f * u) { arcs() }
}
