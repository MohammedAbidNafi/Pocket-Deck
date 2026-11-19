package com.abidnafi.pocketdeck.data.stream

import android.content.Context
import android.net.wifi.WifiManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The Rust tunnel (`rust/pocketdeck-tunnel-jni`, built on `Pocket-Deck-PC/crates/remote`). */
internal object RemoteTunnelNative {
    init {
        System.loadLibrary("pocketdeck_tunnel_jni")
    }

    /** Gives the native side the app context (iroh needs it on Android). Once, before anything else. */
    @JvmStatic external fun init(context: Context)
    @JvmStatic external fun generateSecret(): ByteArray
    @JvmStatic external fun endpointId(secret: ByteArray): String
    /** Blocks until the tunnel is up (direct path, local ports open), or throws [IOException]. */
    @JvmStatic external fun start(secret: ByteArray, relayUrl: String?, pcId: String): Long
    @JvmStatic external fun ports(handle: Long): IntArray
    @JvmStatic external fun status(handle: Long): IntArray
    @JvmStatic external fun stop(handle: Long)
}

/** Why the tunnel didn't come up. [kind] is `no-direct-path`, `connect`, `hello`, `bind`, `config` or `datagrams`. */
class TunnelException(val kind: String, message: String) : IOException(message) {
    val noDirectPath: Boolean get() = kind == KIND_NO_DIRECT_PATH

    companion object {
        const val KIND_NO_DIRECT_PATH = "no-direct-path"

        /** The native side throws `IOException("<kind>\n<message>")`. */
        fun from(e: IOException): TunnelException {
            val text = e.message.orEmpty()
            val kind = text.substringBefore('\n', "connect")
            return TunnelException(kind, text.substringAfter('\n'))
        }
    }
}

/** The PC's ports, which are also the ports the tunnel listens on at 127.0.0.1. */
data class TunnelPorts(
    val https: Int, val http: Int, val rtsp: Int, val display: Int,
    val video: Int, val control: Int, val audio: Int,
)

enum class TunnelLink { OFF, CONNECTING, LAN, INTERNET, RELAYED, NO_DIRECT_PATH }

data class TunnelStatus(val link: TunnelLink, val rttMs: Int?, val closed: Boolean) {
    /** For the stream's chip: "LAN · 3 ms", "Internet · 34 ms". */
    val label: String?
        get() = when (link) {
            TunnelLink.LAN -> "LAN"
            TunnelLink.INTERNET -> "Internet"
            else -> null
        }?.let { name -> if (rttMs != null) "$name · $rttMs ms" else name }

    companion object {
        val OFF = TunnelStatus(TunnelLink.OFF, null, true)
    }
}

/**
 * Play from anywhere, phone side: at most one tunnel at a time (its local ports are fixed), to the
 * PC whose Sunshine it stands in for at `127.0.0.1`. Opened on demand, kept while a stream
 * [hold]s it, and closed after [IDLE_MS] without use. Contract: `Pocket-Deck-PC/docs/remote-play.md`.
 */
class RemoteTunnel(private val context: Context, private val scope: CoroutineScope) {
    private val opening = Mutex()
    private val lock = Any()
    private var handle = 0L
    private var pcId: String? = null
    private var ports: TunnelPorts? = null
    private var lastUse = 0L
    private var holds = 0
    private var idleWatch: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // Every native call goes through the key first, so this is where the native side gets set up.
    private val secret: ByteArray by lazy {
        RemoteTunnelNative.init(context.applicationContext)
        TunnelKey.load(context)
    }

    /** This phone's endpoint id, which the PC learns at registration. */
    val phoneId: String by lazy { RemoteTunnelNative.endpointId(secret) }

    /** Opens (or reuses) the tunnel to [pcId]. Throws [TunnelException]. */
    suspend fun open(pcId: String, relayUrl: String?): TunnelPorts = opening.withLock {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                lastUse = System.currentTimeMillis()
                val p = ports
                if (handle != 0L && this@RemoteTunnel.pcId == pcId && p != null && !statusLocked().closed) return@withContext p
                closeLocked()
            }
            acquireMulticast()
            val h = try {
                RemoteTunnelNative.start(secret, relayUrl, pcId)
            } catch (e: IOException) {
                releaseMulticast()
                throw TunnelException.from(e)
            }
            val a = RemoteTunnelNative.ports(h)
            val p = TunnelPorts(a[0], a[1], a[2], a[3], a[4], a[5], a[6])
            synchronized(lock) {
                handle = h
                this@RemoteTunnel.pcId = pcId
                ports = p
                lastUse = System.currentTimeMillis()
            }
            watchIdle()
            p
        }
    }

    fun status(): TunnelStatus = synchronized(lock) { statusLocked() }

    private fun statusLocked(): TunnelStatus {
        if (handle == 0L) return TunnelStatus.OFF
        val s = RemoteTunnelNative.status(handle)
        val link = TunnelLink.entries.getOrElse(s[0]) { TunnelLink.OFF }
        return TunnelStatus(link, s[1].takeIf { it >= 0 }, s[2] != 0)
    }

    /** Keeps the tunnel open (a stream is using it) until [release]. */
    fun hold() = synchronized(lock) { holds++; lastUse = System.currentTimeMillis() }

    fun release() = synchronized(lock) {
        holds = (holds - 1).coerceAtLeast(0)
        lastUse = System.currentTimeMillis()
    }

    fun stop() {
        synchronized(lock) { closeLocked() }
    }

    private fun closeLocked() {
        if (handle != 0L) {
            RemoteTunnelNative.stop(handle)
            handle = 0L
            pcId = null
            ports = null
            releaseMulticast()
        }
    }

    private fun watchIdle() {
        if (idleWatch?.isActive == true) return
        idleWatch = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000)
                synchronized(lock) {
                    if (handle != 0L && holds == 0 && System.currentTimeMillis() - lastUse > IDLE_MS) closeLocked()
                    if (handle == 0L) return@launch
                }
            }
        }
    }

    /** Android drops multicast without this, and the tunnel finds the PC at home over mDNS. */
    private fun acquireMulticast() {
        if (multicastLock != null) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("PocketDeck tunnel")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseMulticast() {
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
    }

    companion object {
        const val IDLE_MS = 60_000L
        const val LOCALHOST = "127.0.0.1"
    }
}

/** The phone's tunnel key: made once, kept encrypted with a key that never leaves Android Keystore. */
private object TunnelKey {
    private const val ALIAS = "pocketdeck-tunnel"
    private const val FILE = "tunnel-key.bin"
    private const val IV_BYTES = 12

    fun load(context: Context): ByteArray {
        val file = File(context.filesDir, FILE)
        if (file.exists()) {
            runCatching { decrypt(file.readBytes()) }.getOrNull()?.takeIf { it.size == 32 }?.let { return it }
        }
        val secret = RemoteTunnelNative.generateSecret()
        file.writeBytes(encrypt(secret))
        return secret
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }
}
