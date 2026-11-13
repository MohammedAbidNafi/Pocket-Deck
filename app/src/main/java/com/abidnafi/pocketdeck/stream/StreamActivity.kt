package com.abidnafi.pocketdeck.stream

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.abidnafi.pocketdeck.PocketDeckApp
import com.abidnafi.pocketdeck.data.FramePacing
import com.abidnafi.pocketdeck.data.StreamAudio
import com.abidnafi.pocketdeck.data.StreamCodec
import com.abidnafi.pocketdeck.data.StreamResolution
import com.abidnafi.pocketdeck.data.StreamSettings
import com.abidnafi.pocketdeck.data.db.GameEntity
import com.abidnafi.pocketdeck.data.stream.PcDisplay
import com.abidnafi.pocketdeck.data.stream.StreamTarget
import com.abidnafi.pocketdeck.input.Btn
import com.abidnafi.pocketdeck.input.ControllerDispatcher
import com.abidnafi.pocketdeck.input.HintRegistry
import com.abidnafi.pocketdeck.input.LocalController
import com.abidnafi.pocketdeck.input.LocalHints
import com.abidnafi.pocketdeck.input.UiFeedback
import com.abidnafi.pocketdeck.ui.LocalAppContainer
import com.abidnafi.pocketdeck.ui.LocalFeedback
import com.abidnafi.pocketdeck.ui.LocalSettings
import com.abidnafi.pocketdeck.ui.theme.Palette
import com.abidnafi.pocketdeck.ui.theme.PocketDeckTheme
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.capture.InputCaptureManager
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.binding.input.evdev.EvdevListener
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.binding.video.CrashListener
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.GameGestures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Plays a PC game inside PocketDeck. The video, audio and input pipeline is the vendored Moonlight
 * engine (session logic ported from upstream's `Game` activity); everything on screen is PocketDeck's.
 *
 * Controls: the controller goes to the PC. Start + Select + LB + RB (or the system Back gesture)
 * opens PocketDeck's in-stream menu.
 */
class StreamActivity : ComponentActivity(), SurfaceHolder.Callback, NvConnectionListener, GameGestures, PerfOverlayListener, EvdevListener {

    companion object {
        private const val EXTRA_GAME = "game_id"
        private const val REFERENCE_WIDTH = 1280
        private const val REFERENCE_HEIGHT = 720

        fun intent(context: Context, gameId: Long): Intent =
            Intent(context, StreamActivity::class.java).putExtra(EXTRA_GAME, gameId)
    }

    private val container by lazy { (application as PocketDeckApp).container }
    private val ui = StreamUiState()
    private val overlayController = ControllerDispatcher()
    private val overlayHints = HintRegistry()

    private lateinit var surface: StreamSurfaceView
    private lateinit var prefConfig: PreferenceConfiguration
    private var game: GameEntity? = null
    private var target: StreamTarget? = null
    private var stream: StreamSettings = StreamSettings()

    private var conn: NvConnection? = null
    private var decoder: MediaCodecDecoderRenderer? = null
    private var controllerHandler: ControllerHandler? = null
    private val keyboardTranslator = KeyboardTranslator()
    private var inputCapture: InputCaptureProvider? = null
    private val touchContexts = arrayOfNulls<TouchContext>(2)

    private var surfaceReady = false
    private var attemptedConnection = false
    @Volatile private var connected = false
    private var connecting = false
    @Volatile private var reportedEnd = false
    private var lastButtonState = 0
    private var desiredRefreshRate = 60f
    private var swapAB = false

    /** The PC's virtual display, while this stream holds it. */
    private var pcDisplay: PcDisplay? = null
    private var keepAlive: Job? = null

    // ---- Lifecycle ------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        surface = StreamSurfaceView(this).apply {
            holder.addCallback(this@StreamActivity)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { v, e -> handleTouch(v, e) }
            if (Build.VERSION.SDK_INT >= 26) {
                setOnCapturedPointerListener { v, e -> handleMotion(v, e) }
            }
        }
        val overlay = ComposeView(this).apply {
            setContent {
                val settings = container.settings.settings.collectAsStateValue()
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalController provides overlayController,
                    LocalHints provides overlayHints,
                    LocalFeedback provides container.feedback,
                    LocalSettings provides settings,
                ) {
                    PocketDeckTheme(Palette.accents.getOrElse(settings.accentIndex) { Palette.accents[0] }.color) {
                        StreamOverlay(ui, actions)
                    }
                }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            },
        )

        // System Back (gesture / nav bar / phone key) while streaming opens the menu.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    ui.confirmQuit -> ui.confirmQuit = false
                    ui.menuOpen -> resumeStream()
                    ui.error != null -> finish()
                    ui.connecting != null || ui.otherAppRunning != null -> finish()
                    else -> openMenu()
                }
            }
        })

        lifecycleScope.launch { prepare(intent.getLongExtra(EXTRA_GAME, -1)) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
        inputCapture?.onWindowFocusChanged(hasFocus)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen ends the stream (the game keeps running on the PC).
        if (conn != null) {
            reportedEnd = true
            stopConnection()
            if (!isFinishing) finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVirtualDisplay()
        controllerHandler?.destroy()
        inputCapture?.destroy()
        (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(keyboardTranslator)
        // Re-sync now: the host's "running app" changed.
        game?.hostId?.let { container.hosts.refreshAll(syncGames = false) }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- Preparing the session ------------------------------------------------------------------

    private suspend fun prepare(gameId: Long) {
        val g = container.launcher.gameById(gameId) ?: return fail("Game not found", "This game is no longer in your library.")
        game = g
        ui.title = g.title
        val settings = container.settings.current()
        stream = settings.stream
        swapAB = settings.swapAB
        container.feedback.soundsEnabled = settings.uiSounds
        container.feedback.hapticsEnabled = settings.haptics
        val host = g.hostId?.let { container.hosts.host(it) } ?: return fail("PC not found", "The PC for this game has been removed.")
        ui.hostName = host.name
        ui.connecting = "Connecting to ${host.name}…"
        val t = container.hosts.target(host)
            ?: return fail("Can't reach ${host.name}", "Make sure the PC is on, Sunshine is running, and you're on the same network.")
        target = t
        val appId = g.streamAppId ?: return fail("Can't stream", "This game has no app on ${host.name}.")

        // Something else running on the PC? Ask before closing it.
        val running = container.hosts.status.value[host.id]?.runningAppId ?: 0
        if (running != 0 && running != appId) {
            val otherName = container.db.games().getByHost(host.id).firstOrNull { it.streamAppId == running }?.title ?: "Another game"
            val choice = CompletableDeferred<Boolean>()
            pendingOtherAppChoice = choice
            ui.connecting = null
            ui.otherAppRunning = otherName
            if (!choice.await()) return
            ui.otherAppRunning = null
            ui.connecting = "Closing $otherName…"
            if (!container.hosts.quitApp(host)) {
                return fail("Couldn't quit $otherName", "Close it on the PC, then try again. Only the device that started a game can quit it with some hosts.")
            }
        }
        prepareVirtualDisplay(t)
        ui.connecting = "Starting ${g.title}…"
        setUpConnection(t, g, appId)
        maybeStart()
    }

    /**
     * Has the PC switch on a virtual display at this phone's resolution before the stream starts,
     * so Sunshine captures it rather than the PC's own screen. Says so on screen only when it takes
     * a moment. A PC without the feature doesn't answer and nothing changes.
     */
    private suspend fun prepareVirtualDisplay(t: StreamTarget) {
        val display = PcDisplay(t)
        val (width, height) = streamSize(stream.resolution)
        val slow = lifecycleScope.launch {
            delay(PcDisplay.SHOW_PROGRESS_AFTER_MS)
            ui.connecting = "Turning on the virtual display…"
        }
        val result = display.prepare(width, height, stream.fps)
        slow.cancel()
        when (result) {
            is PcDisplay.Result.Ready -> {
                pcDisplay = display
                keepAlive = lifecycleScope.launch {
                    while (isActive) {
                        delay(PcDisplay.KEEPALIVE_INTERVAL_MS)
                        display.keepAlive()
                    }
                }
            }
            PcDisplay.Result.Unavailable -> {}
            is PcDisplay.Result.Failed ->
                ui.showNotice("Couldn't turn on the virtual display, so you'll see the PC's own screen. ${result.message}")
        }
    }

    /** Hands the virtual display back; the PC's own screen returns. */
    private fun releaseVirtualDisplay() {
        keepAlive?.cancel()
        keepAlive = null
        pcDisplay?.release()
        pcDisplay = null
    }

    private var pendingOtherAppChoice: CompletableDeferred<Boolean>? = null

    private fun setUpConnection(t: StreamTarget, g: GameEntity, appId: Int) {
        val s = stream
        val (width, height) = streamSize(s.resolution)
        prefConfig = buildPreferences(s, width, height)
        ui.showStats = s.showStats

        MediaCodecHelper.initialize(this, GlRenderer.get(this))

        var hdr = s.hdr && displaySupportsHdr10()
        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val crashPrefs = getSharedPreferences("StreamDecoderCrashes", MODE_PRIVATE)
        val renderer = MediaCodecDecoderRenderer(
            this, prefConfig,
            CrashListener { crashPrefs.edit().putInt("CrashCount", crashPrefs.getInt("CrashCount", 0) + 1).commit() },
            crashPrefs.getInt("CrashCount", 0),
            connMgr.isActiveNetworkMetered,
            hdr,
            GlRenderer.get(this),
            this,
        )
        decoder = renderer
        if (hdr && !renderer.isHevcMain10Hdr10Supported && !renderer.isAv1Main10Supported) {
            hdr = false
            ui.showNotice("This device's video decoder can't play HDR. Streaming in SDR.")
        }
        if (s.codec == StreamCodec.HEVC && !renderer.isHevcSupported) ui.showNotice("No HEVC decoder on this device, using H.264")
        if (s.codec == StreamCodec.AV1 && !renderer.isAv1Supported) ui.showNotice("No AV1 decoder on this device, using another codec")

        var formats = MoonBridge.VIDEO_FORMAT_H264
        if (renderer.isHevcSupported) {
            formats = formats or MoonBridge.VIDEO_FORMAT_H265
            if (hdr && renderer.isHevcMain10Hdr10Supported) formats = formats or MoonBridge.VIDEO_FORMAT_H265_MAIN10
        }
        if (renderer.isAv1Supported) {
            formats = formats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (hdr && renderer.isAv1Main10Supported) formats = formats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
        }

        val displayRefresh = prepareDisplay(width, height, s.fps)
        var frameRate = s.fps
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            val rounded = displayRefresh.roundToInt()
            if (s.fps >= rounded) {
                if (s.fps > rounded + 3 || rounded <= 49) prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
                else frameRate = rounded - 1
            }
        }

        val config = StreamConfiguration.Builder()
            .setResolution(width, height)
            .setLaunchRefreshRate(s.fps)
            .setRefreshRate(frameRate)
            .setApp(NvApp(g.title, appId, hdr))
            .setBitrate(prefConfig.bitrate)
            .setEnableSops(s.optimizeHostSettings)
            .enableLocalAudioPlayback(s.playHostAudio)
            .setMaxPacketSize(1392)
            .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
            .setSupportedVideoFormats(formats)
            .setAttachedGamepadMask(ControllerHandler.getAttachedControllerMask(this).toInt())
            .setClientRefreshRateX100((displayRefresh * 100).toInt())
            .setAudioConfiguration(prefConfig.audioConfiguration)
            .setColorSpace(renderer.preferredColorSpace)
            .setColorRange(renderer.preferredColorRange)
            .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
            .build()

        val c = NvConnection(
            applicationContext,
            ComputerDetails.AddressTuple(t.address, t.httpPort),
            t.httpsPort, t.uniqueId, config, t.crypto, t.serverCert,
        )
        conn = c
        controllerHandler = ControllerHandler(this, c, this, prefConfig)
        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(keyboardTranslator, null)
        for (i in touchContexts.indices) {
            touchContexts[i] = if (s.touchAsTrackpad) {
                RelativeTouchContext(c, i, REFERENCE_WIDTH, REFERENCE_HEIGHT, surface, prefConfig)
            } else {
                AbsoluteTouchContext(c, i, surface)
            }
        }
        inputCapture = InputCaptureManager.getInputCaptureProvider(this, surface, this)
        surface.aspect = width.toDouble() / height
    }

    /** Moonlight's config object, filled from PocketDeck's settings (upstream defaults elsewhere). */
    private fun buildPreferences(s: StreamSettings, width: Int, height: Int) = PreferenceConfiguration().apply {
        this.width = width
        this.height = height
        fps = s.fps
        bitrate = if (s.bitrateKbps > 0) s.bitrateKbps else PreferenceConfiguration.getDefaultBitrate("${width}x$height", s.fps.toString())
        videoFormat = when (s.codec) {
            StreamCodec.AUTO -> PreferenceConfiguration.FormatOption.AUTO
            StreamCodec.H264 -> PreferenceConfiguration.FormatOption.FORCE_H264
            StreamCodec.HEVC -> PreferenceConfiguration.FormatOption.FORCE_HEVC
            StreamCodec.AV1 -> PreferenceConfiguration.FormatOption.FORCE_AV1
        }
        audioConfiguration = when (s.audio) {
            StreamAudio.STEREO -> MoonBridge.AUDIO_CONFIGURATION_STEREO
            StreamAudio.SURROUND_51 -> MoonBridge.AUDIO_CONFIGURATION_51_SURROUND
            StreamAudio.SURROUND_71 -> MoonBridge.AUDIO_CONFIGURATION_71_SURROUND
        }
        framePacing = when (s.framePacing) {
            FramePacing.LATENCY -> PreferenceConfiguration.FRAME_PACING_MIN_LATENCY
            FramePacing.BALANCED -> PreferenceConfiguration.FRAME_PACING_BALANCED
            FramePacing.CAP_FPS -> PreferenceConfiguration.FRAME_PACING_CAP_FPS
            FramePacing.SMOOTHNESS -> PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS
        }
        enableHdr = s.hdr
        enableSops = s.optimizeHostSettings
        playHostAudio = s.playHostAudio
        enablePerfOverlay = true // stats are always computed; PocketDeck decides whether to show them
        touchscreenTrackpad = s.touchAsTrackpad
        // Upstream defaults for everything PocketDeck doesn't expose.
        deadzonePercentage = 7
        multiController = true
        usbDriver = false
        bindAllUsb = false
        flipFaceButtons = false
        onscreenController = false
        onlyL3R3 = false
        showGuideButton = true
        mouseEmulation = true
        analogStickForScrolling = PreferenceConfiguration.AnalogStickForScrolling.RIGHT
        mouseNavButtons = false
        unlockFps = false
        vibrateOsc = false
        vibrateFallbackToDevice = false
        vibrateFallbackToDeviceStrength = 100
        absoluteMouseMode = false
        enableAudioFx = false
        reduceRefreshRate = false
        fullRange = false
        gamepadMotionSensors = true
        gamepadTouchpadAsMouse = false
        gamepadMotionSensorsFallbackToDevice = false
        stretchVideo = false
        disableWarnings = false
        enablePip = false
        enableLatencyToast = false
        language = "default"
    }

    private fun streamSize(res: StreamResolution): Pair<Int, Int> {
        if (res != StreamResolution.NATIVE) return res.width to res.height
        val mode = displayCompat().mode
        val w = max(mode.physicalWidth, mode.physicalHeight)
        val h = min(mode.physicalWidth, mode.physicalHeight)
        // Even dimensions keep every decoder happy.
        return (w and 1.inv()) to (h and 1.inv())
    }

    @Suppress("DEPRECATION")
    private fun displayCompat(): Display = if (Build.VERSION.SDK_INT >= 30) display!! else windowManager.defaultDisplay

    private fun displaySupportsHdr10(): Boolean =
        displayCompat().hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) == true

    /**
     * Picks the display mode that best fits the stream's frame rate without changing resolution
     * (a simpler version of upstream's prepareDisplayForRendering). Returns the refresh rate.
     */
    private fun prepareDisplay(width: Int, height: Int, fps: Int): Float {
        val display = displayCompat()
        val current = display.mode
        fun good(rate: Float) = rate >= fps - 3 && (rate.roundToInt() % fps <= 3 || fps % rate.roundToInt() == 0)
        var best = current
        for (m in display.supportedModes) {
            if (m.physicalWidth != current.physicalWidth || m.physicalHeight != current.physicalHeight) continue
            val bestGood = good(best.refreshRate)
            val mGood = good(m.refreshRate)
            best = when {
                mGood && !bestGood -> m
                mGood == bestGood && m.refreshRate > best.refreshRate && (mGood || m.refreshRate <= fps + 3) -> m
                else -> best
            }
        }
        if (best.modeId != current.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
        desiredRefreshRate = best.refreshRate
        return best.refreshRate
    }

    private fun maybeStart() {
        val c = conn ?: return
        val d = decoder ?: return
        if (!surfaceReady || attemptedConnection) return
        attemptedConnection = true
        connecting = true
        d.setRenderTarget(surface.holder)
        c.start(AndroidAudioRenderer(this, false), d, this)
    }

    private fun stopConnection() {
        if (connecting || connected) {
            connecting = false
            connected = false
            controllerHandler?.stop()
            val c = conn
            // Stopping does network I/O; keep it off the main thread like upstream.
            Thread { c?.stop() }.start()
        }
    }

    private fun fail(title: String, message: String) {
        // The stream is over; don't keep the PC's screen dark behind the error.
        releaseVirtualDisplay()
        ui.connecting = null
        ui.menuOpen = false
        ui.error = title to message
    }

    // ---- Overlay actions ------------------------------------------------------------------------

    private val actions = StreamActions(
        onResume = { resumeStream() },
        onDisconnect = {
            if (stream.quitOnDisconnect) quitOnHostAndFinish() else {
                reportedEnd = true
                stopConnection()
                finish()
            }
        },
        onQuitGame = { quitOnHostAndFinish() },
        onToggleStats = {
            ui.showStats = !ui.showStats
            lifecycleScope.launch { container.settings.setStream(stream.copy(showStats = ui.showStats)) }
        },
        onKeyboard = { resumeStream(); toggleKeyboard() },
        onCloseError = { finish() },
        onQuitOtherAndStart = { pendingOtherAppChoice?.complete(true) },
        onCancel = {
            pendingOtherAppChoice?.complete(false)
            reportedEnd = true
            stopConnection()
            finish()
        },
    )

    private fun quitOnHostAndFinish() {
        reportedEnd = true
        stopConnection()
        val host = target?.host
        lifecycleScope.launch {
            if (host != null) container.hosts.quitApp(host)
            finish()
        }
    }

    private fun openMenu() {
        if (!connected) return
        ui.menuOpen = true
        inputCapture?.disableCapture()
        inputCapture?.showCursor()
        container.feedback.play(UiFeedback.Sound.SELECT, window.decorView)
    }

    private fun resumeStream() {
        ui.confirmQuit = false
        ui.menuOpen = false
        inputCapture?.enableCapture()
        hideSystemBars()
        surface.requestFocus()
    }

    // ---- GameGestures (engine → UI) -------------------------------------------------------------

    override fun toggleKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        surface.requestFocus()
        @Suppress("DEPRECATION")
        imm.toggleSoftInput(0, 0)
    }

    override fun showNotice(message: String) = runOnUiThread { ui.showNotice(message) }

    override fun onQuitCombo() = runOnUiThread { openMenu() }

    override fun onPerfUpdate(text: String) = runOnUiThread { ui.stats = text }

    // ---- NvConnectionListener -------------------------------------------------------------------

    override fun stageStarting(stage: String) = runOnUiThread {
        if (ui.connecting != null) ui.connecting = "Starting $stage…"
    }

    override fun stageComplete(stage: String) {}

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        val ports = if (portFlags != 0) MoonBridge.stringifyPortFlags(portFlags, "\n") else null
        runOnUiThread {
            if (reportedEnd) return@runOnUiThread
            reportedEnd = true
            val body = buildString {
                append("Starting $stage failed (error $errorCode).")
                if (stage.contains("video") && surface.holder.surface.isValid) {
                    append("\n\nThis device's video decoder couldn't start. Try H.264 or a lower resolution in Settings → PC streaming.")
                }
                if (ports != null) append("\n\nCheck that your firewall and router allow these ports:\n$ports")
            }
            stopConnection()
            fail("Couldn't start the stream", body)
        }
    }

    override fun connectionStarted() = runOnUiThread {
        connecting = false
        connected = true
        ui.connecting = null
        surface.postDelayed({ if (!ui.menuOpen) inputCapture?.enableCapture() }, 500)
        hideSystemBars()
        surface.requestFocus()
    }

    override fun connectionTerminated(errorCode: Int) {
        val portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode)
        val ports = if (portFlags != 0) MoonBridge.stringifyPortFlags(portFlags, "\n") else null
        runOnUiThread {
            controllerHandler?.stop()
            inputCapture?.disableCapture()
            if (reportedEnd) return@runOnUiThread
            reportedEnd = true
            stopConnection()
            if (errorCode == MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                finish()
                return@runOnUiThread
            }
            val message = when (errorCode) {
                MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC ->
                    "No video arrived from the PC. Check the firewall on the PC and your router (UDP ports)."
                MoonBridge.ML_ERROR_NO_VIDEO_FRAME ->
                    "The PC isn't sending video frames. Try restarting the game or Sunshine."
                MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION, MoonBridge.ML_ERROR_PROTECTED_CONTENT ->
                    "The PC ended the stream right away. The game may have closed, or it shows protected content."
                MoonBridge.ML_ERROR_FRAME_CONVERSION ->
                    "The PC couldn't convert the video. Try a different codec or resolution."
                else -> "The connection to the PC was lost (error ${if (abs(errorCode) > 1000) Integer.toHexString(errorCode) else errorCode})."
            } + (ports?.let { "\n\nCheck these ports:\n$it" } ?: "")
            fail("Stream ended", message)
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) = runOnUiThread {
        ui.poorConnection = connectionStatus == MoonBridge.CONN_STATUS_POOR
    }

    override fun displayMessage(message: String) = runOnUiThread { ui.showNotice(message) }

    override fun displayTransientMessage(message: String) = runOnUiThread { ui.showNotice(message) }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        controllerHandler?.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor)
    }

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        controllerHandler?.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger)
    }

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        decoder?.setHdrMode(enabled, hdrMetadata)
    }

    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
        controllerHandler?.handleSetMotionEventState(controllerNumber, motionType, reportRateHz)
    }

    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        controllerHandler?.handleSetControllerLED(controllerNumber, r, g, b)
    }

    // ---- Surface ----------------------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        val rate = if (desiredRefreshRate < stream.fps) stream.fps.toFloat() else desiredRefreshRate
        when {
            Build.VERSION.SDK_INT >= 31 ->
                holder.surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS)
            Build.VERSION.SDK_INT >= 30 ->
                holder.surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = true
        maybeStart()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        if (attemptedConnection) {
            decoder?.prepareForStop()
            if (connected) stopConnection()
        }
    }

    // ---- Input ------------------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ui.capturesInput) return dispatchOverlayKey(event)
        val handled = when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(event)
            KeyEvent.ACTION_UP -> handleKeyUp(event)
            KeyEvent.ACTION_MULTIPLE -> handleKeyMultiple(event)
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    /** PocketDeck's own overlay is open: drive it like the rest of the app (A = select, B = back). */
    private fun dispatchOverlayKey(event: KeyEvent): Boolean {
        val confirmKey = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        val backKey = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        return when (event.keyCode) {
            confirmKey -> super.dispatchKeyEvent(
                KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
            )
            backKey, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    container.feedback.play(UiFeedback.Sound.BACK, window.decorView)
                    onBackPressedDispatcher.onBackPressed()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE -> {
                if (event.action == KeyEvent.ACTION_UP && ui.menuOpen) resumeStream()
                true
            }
            else -> {
                val btn = when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_X -> Btn.X
                    KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y
                    else -> null
                }
                if (btn != null) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) overlayController.dispatch(btn)
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
        }
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
        val c = conn ?: return false
        if (event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY != 0) return false
        // Some devices turn a right mouse click into BACK.
        if (isMouseSource(event.source) && event.keyCode == KeyEvent.KEYCODE_BACK) {
            c.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
            return true
        }
        if (ControllerHandler.isGameControllerDevice(event.device) && controllerHandler?.handleButtonDown(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false // system back → menu
        val translated = keyboardTranslator.translate(event.keyCode, event.deviceId)
        if (translated.toInt() == 0) {
            val unicode = event.unicodeChar
            if (unicode and KeyCharacterMap.COMBINING_ACCENT == 0 && unicode and KeyCharacterMap.COMBINING_ACCENT_MASK != 0) {
                c.sendUtf8Text(unicode.toChar().toString())
                return true
            }
            return false
        }
        if (event.repeatCount > 0) return true
        c.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN, modifiers(event), keyFlags(event))
        return true
    }

    private fun handleKeyUp(event: KeyEvent): Boolean {
        val c = conn ?: return false
        if (event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY != 0) return false
        if (isMouseSource(event.source) && event.keyCode == KeyEvent.KEYCODE_BACK) {
            c.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
            return true
        }
        if (ControllerHandler.isGameControllerDevice(event.device) && controllerHandler?.handleButtonUp(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        val translated = keyboardTranslator.translate(event.keyCode, event.deviceId)
        if (translated.toInt() == 0) {
            val unicode = event.unicodeChar
            return unicode and KeyCharacterMap.COMBINING_ACCENT == 0 && unicode and KeyCharacterMap.COMBINING_ACCENT_MASK != 0
        }
        c.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, modifiers(event), keyFlags(event))
        return true
    }

    private fun handleKeyMultiple(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN || event.characters == null) return false
        conn?.sendUtf8Text(event.characters)
        return true
    }

    private fun keyFlags(event: KeyEvent): Byte =
        if (keyboardTranslator.hasNormalizedMapping(event.keyCode, event.deviceId)) 0 else MoonBridge.SS_KBE_FLAG_NON_NORMALIZED

    private fun modifiers(event: KeyEvent): Byte {
        var m = 0
        if (event.isShiftPressed || event.keyCode == KeyEvent.KEYCODE_PLUS) m = m or KeyboardPacket.MODIFIER_SHIFT.toInt()
        if (event.isCtrlPressed) m = m or KeyboardPacket.MODIFIER_CTRL.toInt()
        if (event.isAltPressed) m = m or KeyboardPacket.MODIFIER_ALT.toInt()
        if (event.isMetaPressed) m = m or KeyboardPacket.MODIFIER_META.toInt()
        return m.toByte()
    }

    private fun isMouseSource(source: Int) = source == InputDevice.SOURCE_MOUSE || source == InputDevice.SOURCE_MOUSE_RELATIVE

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ui.capturesInput) return super.dispatchGenericMotionEvent(event)
        return handleMotion(surface, event) || super.dispatchGenericMotionEvent(event)
    }

    /** Controllers, mice and trackpads. Mirrors upstream's motion routing. */
    private fun handleMotion(view: View, event: MotionEvent): Boolean {
        val c = conn ?: return false
        if (!connected || ui.capturesInput) return false
        val source = event.source
        val deviceSources = event.device?.sources ?: 0
        if (source and InputDevice.SOURCE_CLASS_JOYSTICK != 0) {
            return controllerHandler?.handleMotionEvent(event) == true
        }
        if (deviceSources and InputDevice.SOURCE_CLASS_JOYSTICK != 0 && controllerHandler?.tryHandleTouchpadEvent(event) == true) {
            return true
        }
        val pointerLike = source and InputDevice.SOURCE_CLASS_POINTER != 0 ||
            source and InputDevice.SOURCE_CLASS_POSITION != 0 || source == InputDevice.SOURCE_MOUSE_RELATIVE
        if (!pointerLike) return false
        val mouseLike = source == InputDevice.SOURCE_MOUSE || source and InputDevice.SOURCE_CLASS_POSITION != 0 ||
            source == InputDevice.SOURCE_MOUSE_RELATIVE ||
            (event.pointerCount >= 1 && event.getToolType(0).let {
                it == MotionEvent.TOOL_TYPE_MOUSE || it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER
            })
        if (!mouseLike) return false

        val capture = inputCapture
        if (capture != null && capture.eventHasRelativeMouseAxes(event)) {
            val dx = capture.getRelativeAxisX(event).toInt()
            val dy = capture.getRelativeAxisY(event).toInt()
            if (dx != 0 || dy != 0) c.sendMouseMove(dx.toShort(), dy.toShort())
        } else if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_MOVE) {
            c.sendMousePosition(
                event.x.coerceIn(0f, view.width.toFloat()).toInt().toShort(),
                event.y.coerceIn(0f, view.height.toFloat()).toInt().toShort(),
                view.width.toShort(), view.height.toShort(),
            )
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            c.sendMouseHighResScroll((event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).toInt().toShort())
            c.sendMouseHighResHScroll((event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120).toInt().toShort())
        }
        val buttons = event.buttonState
        val changed = buttons xor lastButtonState
        fun button(mask: Int, id: Byte) {
            if (changed and mask == 0) return
            if (buttons and mask != 0) c.sendMouseButtonDown(id) else c.sendMouseButtonUp(id)
        }
        button(MotionEvent.BUTTON_PRIMARY, MouseButtonPacket.BUTTON_LEFT)
        button(MotionEvent.BUTTON_SECONDARY, MouseButtonPacket.BUTTON_RIGHT)
        button(MotionEvent.BUTTON_TERTIARY, MouseButtonPacket.BUTTON_MIDDLE)
        button(MotionEvent.BUTTON_BACK, MouseButtonPacket.BUTTON_X1)
        button(MotionEvent.BUTTON_FORWARD, MouseButtonPacket.BUTTON_X2)
        // Stylus contact acts as a left click.
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) c.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
            if (event.actionMasked == MotionEvent.ACTION_UP) c.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
        }
        lastButtonState = buttons
        return true
    }

    /** Fingers on the screen: trackpad-style mouse (or direct pointing). */
    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        if (handleMotion(view, event)) return true
        if (!connected || ui.capturesInput) return false
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) return false
        val time = SystemClock.uptimeMillis()
        val index = event.actionIndex
        val ctx = touchContexts.getOrNull(event.getPointerId(index))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                touchContexts.forEach { it?.setPointerCount(event.pointerCount) }
                ctx?.touchDownEvent(event.getX(index).toInt(), event.getY(index).toInt(), time, true)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    touchContexts.getOrNull(event.getPointerId(i))?.touchMoveEvent(event.getX(i).toInt(), event.getY(i).toInt(), time)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                ctx?.touchUpEvent(event.getX(index).toInt(), event.getY(index).toInt(), time)
                touchContexts.forEach { it?.setPointerCount(event.pointerCount - 1) }
            }
            MotionEvent.ACTION_CANCEL -> touchContexts.forEach { it?.cancelTouch() }
        }
        return true
    }

    // ---- EvdevListener (only used by root capture providers, kept for completeness) ---------------

    override fun mouseMove(deltaX: Int, deltaY: Int) {
        conn?.sendMouseMove(deltaX.toShort(), deltaY.toShort())
    }

    override fun mouseButtonEvent(buttonId: Int, down: Boolean) {
        val id = when (buttonId) {
            EvdevListener.BUTTON_LEFT -> MouseButtonPacket.BUTTON_LEFT
            EvdevListener.BUTTON_MIDDLE -> MouseButtonPacket.BUTTON_MIDDLE
            EvdevListener.BUTTON_RIGHT -> MouseButtonPacket.BUTTON_RIGHT
            EvdevListener.BUTTON_X1 -> MouseButtonPacket.BUTTON_X1
            EvdevListener.BUTTON_X2 -> MouseButtonPacket.BUTTON_X2
            else -> return
        }
        if (down) conn?.sendMouseButtonDown(id) else conn?.sendMouseButtonUp(id)
    }

    override fun mouseVScroll(amount: Byte) {
        conn?.sendMouseScroll(amount)
    }

    override fun mouseHScroll(amount: Byte) {
        conn?.sendMouseHScroll(amount)
    }

    override fun keyboardEvent(buttonDown: Boolean, keyCode: Short) {
        val translated = keyboardTranslator.translate(keyCode.toInt(), -1)
        if (translated.toInt() != 0) {
            conn?.sendKeyboardInput(translated, if (buttonDown) KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP, 0, 0)
        }
    }
}

/** collectAsState with the repository's default Settings while the first value loads. */
@androidx.compose.runtime.Composable
private fun kotlinx.coroutines.flow.Flow<com.abidnafi.pocketdeck.data.Settings>.collectAsStateValue(): com.abidnafi.pocketdeck.data.Settings {
    val state = androidx.compose.runtime.produceState(com.abidnafi.pocketdeck.data.Settings(), this) { collect { value = it } }
    return state.value
}
