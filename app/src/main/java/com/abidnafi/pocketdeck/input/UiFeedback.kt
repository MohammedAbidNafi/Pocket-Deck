package com.abidnafi.pocketdeck.input

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Console-style UI sounds and haptics. The sounds are synthesised into small WAV files on
 * first use, so the APK ships no audio assets.
 */
class UiFeedback(private val context: Context) {
    enum class Sound { MOVE, SELECT, BACK, TOGGLE, INTRO }

    var soundsEnabled = true
    var hapticsEnabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = java.util.concurrent.ConcurrentHashMap<Sound, Int>()
    private val loaded = MutableStateFlow<Set<Int>>(emptySet())
    private var lastMove = 0L

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.update { it + sampleId }
        }
        Thread {
            // Startup chime first: it is needed straight away on a cold start.
            ids[Sound.INTRO] = loadIntro(File(context.cacheDir, "ui_sounds").apply { mkdirs() })
            val dir = File(context.cacheDir, "ui_sounds").apply { mkdirs() }
            ids[Sound.MOVE] = load(dir, "move", listOf(1760.0), 0.028, 0.18)
            ids[Sound.SELECT] = load(dir, "select", listOf(880.0, 1320.0), 0.09, 0.22)
            ids[Sound.BACK] = load(dir, "back", listOf(990.0, 660.0), 0.08, 0.2)
            ids[Sound.TOGGLE] = load(dir, "toggle", listOf(1320.0), 0.05, 0.2)
        }.start()
    }

    /** Suspends until [sound] has been decoded and can play without a gap. */
    suspend fun awaitLoaded(sound: Sound) {
        loaded.first { set -> ids[sound]?.let { it in set } == true }
    }

    /** A light physical tap (no sound), for moments like the logo parts snapping together. */
    fun tap(view: View) {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun play(sound: Sound, view: View? = null) {
        if (sound == Sound.MOVE) {
            val now = System.currentTimeMillis()
            if (now - lastMove < 35) return // stick repeat can fire faster than the sound
            lastMove = now
        }
        if (soundsEnabled) ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
        if (hapticsEnabled && view != null) {
            view.performHapticFeedback(
                if (sound == Sound.MOVE) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }
    }

    /** Writes a short sine blip (tones played back to back) as 16-bit mono WAV and loads it. */
    private fun load(dir: File, name: String, tones: List<Double>, seconds: Double, volume: Double): Int {
        val file = File(dir, "$name.wav")
        if (!file.exists()) {
            val rate = 44_100
            val perTone = (rate * seconds / tones.size).toInt()
            val samples = ShortArray(perTone * tones.size)
            tones.forEachIndexed { t, freq ->
                for (i in 0 until perTone) {
                    val time = i.toDouble() / rate
                    val attack = (i / (rate * 0.002)).coerceAtMost(1.0)
                    val decay = exp(-time * 6.0 / (seconds / tones.size))
                    samples[t * perTone + i] = (sin(2 * PI * freq * time) * attack * decay * volume * Short.MAX_VALUE).toInt().toShort()
                }
            }
            writeWav(file, samples, rate)
        }
        return pool.load(file.absolutePath, 1)
    }

    /** Synthesises (once) and loads the startup sound. */
    private fun loadIntro(dir: File): Int {
        val file = File(dir, "intro_epic_v2.wav")
        if (!file.exists()) {
            val rate = 44_100
            val mix = epicIntro(rate)
            val samples = ShortArray(mix.size) { (mix[it] * Short.MAX_VALUE).toInt().toShort() }
            writeWav(file, samples, rate)
        }
        return pool.load(file.absolutePath, 1)
    }

    /**
     * Cinematic startup sound timed to the logo animation (all times in seconds):
     *  - 0.00  a dark rising swell of filtered noise and low drone as the phone appears,
     *  - 0.40  the grips snap on: a huge sub "boom" that drops in pitch, plus a punchy thump,
     *  - 0.78  the Android head pops up: a wide brass-like C minor-to-major power chord stab,
     *  then everything rings out through a reverb for an "arena" tail.
     * Low notes are driven through saturation so their harmonics still read as bass on phone speakers.
     */
    private fun epicIntro(rate: Int): DoubleArray {
        val length = 3.2
        val n = (rate * length).toInt()
        val dry = DoubleArray(n)
        val rnd = java.util.Random(7)
        fun at(seconds: Double) = (seconds * rate).toInt()

        // 1. Rising swell: low drone (C1/C2) + noise through a one-pole low-pass that opens up.
        run {
            var lp = 0.0
            val end = at(0.45)
            for (i in 0 until end) {
                val t = i.toDouble() / rate
                val p = t / 0.45
                val cutoff = 0.004 + 0.05 * p * p
                lp += cutoff * (rnd.nextGaussian() - lp)
                val drone = sin(2 * PI * 32.7 * t) + 0.6 * sin(2 * PI * 65.4 * t)
                val env = p * p
                dry[i] += (lp * 2.2 + drone * 0.35) * env * 0.55
            }
        }

        // 2. Sub boom on the grips: sine sweeping 120 Hz -> 34 Hz, long decay, plus a noise thump.
        run {
            val start = at(0.40)
            var phase = 0.0
            for (i in 0 until at(1.9)) {
                val t = i.toDouble() / rate
                val freq = 34.0 + 86.0 * exp(-t * 9.0)
                phase += 2 * PI * freq / rate
                val env = (t / 0.004).coerceAtMost(1.0) * exp(-t * 1.7)
                val thump = if (t < 0.06) rnd.nextGaussian() * exp(-t * 70.0) * 0.8 else 0.0
                val idx = start + i
                if (idx < n) dry[idx] += (tanh(sin(phase) * 2.4) * 1.1 + thump) * env
            }
        }

        // 3. Brass-like chord stab on the Android head: C2 G2 C3 G3 C4 E4, sawtooth-ish voices
        //    (first 10 harmonics) with a slight detune for width and a swelling filter envelope.
        run {
            val start = at(0.78)
            val chord = doubleArrayOf(65.41, 98.0, 130.81, 196.0, 261.63, 329.63)
            val gains = doubleArrayOf(0.9, 0.6, 0.55, 0.4, 0.3, 0.22)
            for (i in 0 until at(2.3)) {
                val t = i.toDouble() / rate
                val attack = (t / 0.03).coerceAtMost(1.0)
                val env = attack * (0.35 + 0.65 * exp(-t * 3.2)) * exp(-t * 0.9)
                // Brightness opens quickly then settles, like a brass section swelling.
                val bright = 3.0 + 7.0 * exp(-t * 4.0)
                var v = 0.0
                for ((k, f) in chord.withIndex()) {
                    for (detune in doubleArrayOf(0.997, 1.003)) {
                        var h = 1
                        while (h <= 10) {
                            val rolloff = exp(-(h - 1) / bright)
                            v += sin(2 * PI * f * detune * h * t) * rolloff / h * gains[k] * 0.5
                            h++
                        }
                    }
                }
                val idx = start + i
                if (idx < n) dry[idx] += tanh(v * 0.55) * env * 0.9
            }
            // A soft high shimmer on top (C6 + G6) to finish the hit.
            for (i in 0 until at(1.6)) {
                val t = i.toDouble() / rate
                val env = (t / 0.08).coerceAtMost(1.0) * exp(-t * 2.2)
                val idx = start + at(0.05) + i
                if (idx < n) dry[idx] += (sin(2 * PI * 1046.5 * t) + 0.7 * sin(2 * PI * 1568.0 * t)) * env * 0.05
            }
        }

        // 4. Reverb: four damped feedback combs in parallel (Schroeder style) for a big-room tail.
        val wet = DoubleArray(n)
        for (delayMs in doubleArrayOf(29.7, 37.1, 41.1, 43.7)) {
            val d = (delayMs / 1000 * rate).toInt()
            val buf = DoubleArray(n)
            var damp = 0.0
            for (i in 0 until n) {
                val back = if (i >= d) buf[i - d] else 0.0
                damp = damp * 0.4 + back * 0.6
                buf[i] = dry[i] + damp * 0.84
                wet[i] += buf[i] * 0.25
            }
        }

        // Mix, master saturation, normalise and fade the very end.
        val out = DoubleArray(n) { i -> tanh((dry[i] + wet[i] * 0.45) * 1.3) }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        for (i in 0 until n) {
            val tail = ((n - i) / (rate * 0.4)).coerceAtMost(1.0)
            out[i] = out[i] / peak * 0.95 * tail
        }
        return out
    }

    private fun writeWav(file: File, samples: ShortArray, rate: Int) {
        val dataSize = samples.size * 2
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun int(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun short(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            f.writeBytes("RIFF"); int(36 + dataSize); f.writeBytes("WAVE")
            f.writeBytes("fmt "); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
            f.writeBytes("data"); int(dataSize)
            val bytes = ByteArray(dataSize)
            samples.forEachIndexed { i, s ->
                bytes[i * 2] = s.toInt().toByte()
                bytes[i * 2 + 1] = (s.toInt() shr 8).toByte()
            }
            f.write(bytes)
        }
    }
}
