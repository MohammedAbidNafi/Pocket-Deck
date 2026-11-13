package com.abidnafi.pocketdeck.data.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * The PC's virtual display: PocketDeck for PC switches on a display at this phone's resolution and
 * refresh rate before the stream starts, and off again afterwards. Contract:
 * `Pocket-Deck-PC/docs/virtual-display.md` (the source of truth; change it there first).
 *
 * Same trust as Moonlight's own requests: the PC must present the certificate pinned at pairing,
 * and we present our paired client certificate. A PC without the feature simply doesn't answer
 * on the port, and the stream goes ahead on its normal screen.
 */
class PcDisplay(private val target: StreamTarget) {

    sealed interface Result {
        /** On, at the phone's mode. [note] says what didn't go to plan, if anything. */
        data class Ready(val note: String?) : Result
        /** This PC doesn't have it (older PocketDeck, or not switched on in its settings). */
        data object Unavailable : Result
        data class Failed(val message: String) : Result
    }

    private val client: OkHttpClient by lazy {
        val crypto = target.crypto
        val keyManager = object : X509KeyManager {
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = "Limelight-RSA"
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(crypto.clientCertificate)
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun getPrivateKey(alias: String?): PrivateKey = crypto.clientPrivateKey
        }
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                throw CertificateException("Not a server")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain == null || chain.isEmpty() || chain[0] != target.serverCert) {
                    throw CertificateException("This isn't the PC this phone paired with")
                }
            }
        }
        val tls = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trustManager)
            // The certificate is pinned above, so the address it's reached at doesn't matter.
            .hostnameVerifier { _, _ -> true }
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PREPARE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun url(path: String): HttpUrl =
        HttpUrl.Builder().scheme("https").host(target.address).port(target.httpPort + PORT_OFFSET)
            .encodedPath(path).build()

    private fun post(path: String, json: String, readTimeoutMs: Long): Pair<Int, String> {
        val request = Request.Builder().url(url(path)).post(json.toRequestBody(JSON)).build()
        val call = client.newBuilder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build().newCall(request)
        call.execute().use { return it.code to (it.body.string()) }
    }

    /** Asks for the display at this mode and waits until it's in place (a few seconds at most). */
    suspend fun prepare(width: Int, height: Int, fps: Int): Result = withContext(Dispatchers.IO) {
        try {
            val (code, body) = post(PATH_PREPARE, """{"width":$width,"height":$height,"fps":$fps}""", PREPARE_TIMEOUT_MS)
            parseReply(code, body)
        } catch (e: IOException) {
            // Nothing listening (refused or timed out) is the normal answer from a PC without it.
            Result.Unavailable
        }
    }

    /** Renews the PC's lease on the display. Call every [KEEPALIVE_INTERVAL_MS] while streaming. */
    suspend fun keepAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            post(PATH_KEEPALIVE, "{}", SHORT_TIMEOUT_MS).first == 200
        } catch (e: IOException) {
            false
        }
    }

    /** Hands the display back so the PC's own screen returns. Fire and forget. */
    fun release() {
        Thread {
            try {
                post(PATH_RELEASE, "{}", SHORT_TIMEOUT_MS)
            } catch (_: IOException) {
                // The PC also switches it off by itself once the lease runs out.
            }
        }.start()
    }

    companion object {
        /** Sunshine's base port + 30 (47989 → 48019). */
        const val PORT_OFFSET = 30
        const val PATH_PREPARE = "/pocketdeck/v1/display/prepare"
        const val PATH_KEEPALIVE = "/pocketdeck/v1/display/keepalive"
        const val PATH_RELEASE = "/pocketdeck/v1/display/release"

        /** The PC's lease is 90 s; renewing every 20 s survives a few lost requests. */
        const val KEEPALIVE_INTERVAL_MS = 20_000L

        /** How long to wait before saying "Turning on…", so a quick answer shows no message at all. */
        const val SHOW_PROGRESS_AFTER_MS = 400L

        private const val CONNECT_TIMEOUT_MS = 2_000L
        private const val PREPARE_TIMEOUT_MS = 45_000L
        private const val SHORT_TIMEOUT_MS = 5_000L
        private val JSON = "application/json".toMediaType()
        private val parser = Json { ignoreUnknownKeys = true }

        /** What a prepare answer means. Public for tests. */
        fun parseReply(code: Int, body: String): Result {
            val json = runCatching { parser.parseToJsonElement(body) as? JsonObject }.getOrNull()
            fun text(key: String) = json?.get(key)?.jsonPrimitive?.contentOrNull
            val state = text("state")
            return when {
                code == 200 && state == "ready" -> Result.Ready(text("note"))
                code == 503 || state == "unavailable" -> Result.Unavailable
                code == 404 -> Result.Unavailable
                else -> Result.Failed(text("message") ?: "The PC answered with HTTP $code.")
            }
        }
    }
}
