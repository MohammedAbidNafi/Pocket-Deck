package com.abidnafi.pocketdeck.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.abidnafi.pocketdeck.data.stream.PairCode
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ButtonHints
import com.abidnafi.pocketdeck.input.Hint
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.components.ConsoleButton
import com.abidnafi.pocketdeck.ui.components.InitialFocus
import com.abidnafi.pocketdeck.ui.theme.LocalAccent
import com.abidnafi.pocketdeck.ui.theme.Palette
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/** Whether this device has any camera to scan a pairing code with (many TVs and handhelds don't). */
fun canScanPairCode(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/**
 * Pair with one scan: reads the QR code PocketDeck on the PC shows, then hands it to
 * [com.abidnafi.pocketdeck.data.stream.HostRepository.startPairFromScan] and goes back. Progress
 * and the result show in the pairing overlay.
 */
@Composable
fun ScanPairScreen(nav: NavController) {
    val context = LocalContext.current
    val hostsRepo = LocalAppContainer.current.hosts
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var asked by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(false) }
    val button = remember { FocusRequester() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    // Back from the app's settings page with the camera now allowed.
    LifecycleResumeEffect(Unit) {
        granted = hasPermission()
        onPauseOrDispose { }
    }
    // A wrong code's message fades after a moment so the next scan can speak.
    LaunchedEffect(problem) {
        if (problem != null) {
            delay(3500)
            problem = null
        }
    }

    fun onScanned(text: String) {
        if (handled) return
        when (val r = PairCode.parse(text)) {
            is PairCode.Parsed.Ok -> {
                handled = true
                hostsRepo.startPairFromScan(r.code)
                nav.popBackStack()
            }
            PairCode.Parsed.NotPairCode -> problem = "That isn't a PocketDeck pairing code."
            is PairCode.Parsed.Invalid -> problem = r.message
        }
    }

    ButtonHints(Hint(Btn.CONFIRM, "Select"), Hint(Btn.BACK, "Back"))
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted && cameraError == null) {
            QrCameraPreview(onScanned = ::onScanned, onError = { cameraError = it }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(3.dp, LocalAccent.current, RoundedCornerShape(28.dp)),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                .widthIn(max = 640.dp)
                .background(Palette.Surface.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Scan your PC's code", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    cameraError != null -> cameraError!!
                    !granted && asked -> "PocketDeck needs the camera to read the code on your PC. Nothing is recorded or saved."
                    !granted -> "Allow the camera to scan the code."
                    else -> "On the PC, open PocketDeck → Devices → Pair a phone, then point this camera at the code."
                },
                style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, textAlign = TextAlign.Center,
            )
            problem?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, textAlign = TextAlign.Center)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!granted && asked) {
                ConsoleButton(
                    "Allow camera",
                    {
                        // After "Don't ask again" the system dialog no longer shows; the app's settings page does.
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    icon = Icons.Rounded.CameraAlt, primary = true, focusRequester = button,
                )
            }
            ConsoleButton(
                "Pair with a PIN instead", { nav.popBackStack() },
                icon = if (granted) Icons.Rounded.Pin else Icons.AutoMirrored.Rounded.ArrowBack,
                focusRequester = if (!granted && asked) null else button,
            )
        }
    }
    InitialFocus(null, button, key = granted to asked)
}

/** Back camera preview that reports every QR code it reads (on the main thread). */
@Composable
private fun QrCameraPreview(onScanned: (String) -> Unit, onError: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestScanned by rememberUpdatedState(onScanned)
    val latestError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val p = runCatching { future.get() }.getOrNull()
            if (p == null) {
                latestError("The camera couldn't be started.")
                return@addListener
            }
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .build()
            val decoder = QrDecoder()
            analysis.setAnalyzer(analysisExecutor) { image ->
                val text = try {
                    decoder.decode(image)
                } finally {
                    image.close()
                }
                if (text != null) main.execute { if (!disposed) latestScanned(text) }
            }
            val selector = when {
                runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
                runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }
            if (selector == null) {
                latestError("This device has no camera PocketDeck can use. Pair with a PIN instead.")
                return@addListener
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }.onFailure { latestError("The camera couldn't be started. Another app may be using it.") }
        }, main)
        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Decodes QR codes from the camera's luminance (Y) plane with ZXing. Not thread-safe; one per analyzer. */
private class QrDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )
    private var buffer = ByteArray(0)

    fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        if (rowStride < width || plane.pixelStride != 1) return null
        val size = rowStride * height
        if (buffer.size < size) buffer = ByteArray(size)
        val data = plane.buffer.duplicate().apply { rewind() }
        // The last row may be shorter than the stride; the rest of the buffer just stays unused.
        data.get(buffer, 0, minOf(data.remaining(), size))
        val source = PlanarYUVLuminanceSource(buffer, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
