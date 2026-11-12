package com.abidnafi.pocketdeck.data.stream

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import com.abidnafi.pocketdeck.data.db.AppDatabase
import com.abidnafi.pocketdeck.data.db.StreamHostEntity
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent
import com.limelight.nvstream.wol.WakeOnLanSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.Inet4Address
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Live, not persisted, state of a host. */
data class HostStatus(
    val online: Boolean,
    /** Address that answered, used for pairing / streaming. */
    val address: String? = null,
    val httpsPort: Int = 0,
    /** The host still knows us (false = re-pair needed even if we have a certificate). */
    val hostSaysPaired: Boolean = false,
    /** App currently running on the host, 0 = none. */
    val runningAppId: Int = 0,
    val error: String? = null,
)

sealed interface PairingState {
    data object Idle : PairingState
    /** Pair with one scan: looking for the PC on the addresses in its code. */
    data class Connecting(val hostName: String) : PairingState
    /** [pin] is shown for the user to type into Sunshine; null when the PC enters it (pair with one scan). */
    data class Waiting(val hostId: Long, val hostName: String, val pin: String?) : PairingState
    data class Done(val hostId: Long, val hostName: String) : PairingState
    /** [hostId] is null when no host was reached. [fromScan]: retry by showing a new code, not by re-pairing. */
    data class Failed(val hostId: Long?, val message: String, val fromScan: Boolean = false) : PairingState
}

/** Everything needed to open a stream to a host. */
class StreamTarget(
    val host: StreamHostEntity,
    val address: String,
    val httpPort: Int,
    val httpsPort: Int,
    val serverCert: X509Certificate,
    val crypto: LimelightCryptoProvider,
) {
    val uniqueId: String get() = UNIQUE_ID
}

/** Upstream Moonlight uses the same client id everywhere so any client can quit games started by another. */
const val UNIQUE_ID = "0123456789ABCDEF"

/**
 * Gaming PCs (Sunshine hosts): discovery on the local network, adding by address, PIN pairing,
 * reachability, and mirroring each paired host's app list into the game library.
 *
 * All protocol work goes through the vendored Moonlight engine (`:moonlight`, `com.limelight.*`);
 * none of its UI is used.
 */
class HostRepository(
    private val context: Context,
    db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val hostsDao = db.streamHosts()
    private val gamesDao = db.games()
    private val writeLock = Mutex()
    private val crypto: LimelightCryptoProvider by lazy { PlatformBinding.getCryptoProvider(context) }
    private val artDir by lazy { File(context.filesDir, "art").apply { mkdirs() } }

    val hosts: Flow<List<StreamHostEntity>> = hostsDao.observeAll()

    private val _status = MutableStateFlow<Map<Long, HostStatus>>(emptyMap())
    val status: StateFlow<Map<Long, HostStatus>> = _status.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private val _syncing = MutableStateFlow<Set<Long>>(emptySet())
    /** Hosts whose app list is being refreshed. */
    val syncing: StateFlow<Set<Long>> = _syncing.asStateFlow()

    // ---- Discovery ------------------------------------------------------------------------------

    private var agent: MdnsDiscoveryAgent? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    /** Looks for hosts advertising GameStream over mDNS; found hosts are added (unpaired). */
    @Synchronized
    fun startDiscovery() {
        if (agent != null) return
        val listener = object : MdnsDiscoveryListener {
            override fun notifyComputerAdded(computer: MdnsComputer) {
                scope.launch(Dispatchers.IO) { onDiscovered(computer) }
            }

            override fun notifyDiscoveryFailure(e: Exception) {
                android.util.Log.w(TAG, "mDNS discovery failed", e)
            }
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("PocketDeck mDNS")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        // Same choice as upstream: NsdManager is complete from Android 14, jmDNS before that.
        agent = (if (Build.VERSION.SDK_INT >= 34) NsdManagerDiscoveryAgent(context, listener) else JmDNSDiscoveryAgent(context, listener))
            .also { it.startDiscovery(DISCOVERY_INTERVAL_MS) }
        _discovering.value = true
    }

    @Synchronized
    fun stopDiscovery() {
        agent?.stopDiscovery()
        agent = null
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
        _discovering.value = false
    }

    private suspend fun onDiscovered(computer: MdnsComputer) {
        val address = (computer.localAddress as? Inet4Address)?.hostAddress
            ?: computer.localAddress?.hostAddress
            ?: computer.ipv6Address?.hostAddress?.let { "[$it]" }
            ?: return
        val port = computer.port.takeIf { it > 0 } ?: StreamHostEntity.DEFAULT_HTTP_PORT
        val details = runCatching { http(address, port, 0, null).getComputerDetails(true) }.getOrNull() ?: return
        upsert(details, localAddress = address, httpPort = port, manual = false)
    }

    // ---- Adding / removing ---------------------------------------------------------------------

    sealed interface AddResult {
        data class Ok(val host: StreamHostEntity) : AddResult
        data class Error(val message: String) : AddResult
    }

    /** Adds a host by IP / host name, optionally with `:port`. */
    suspend fun addManual(input: String): AddResult = withContext(Dispatchers.IO) {
        val (address, port) = parseAddress(input) ?: return@withContext AddResult.Error("\"$input\" isn't a valid address.")
        try {
            val details = http(address, port, 0, null).getComputerDetails(true)
            AddResult.Ok(upsert(details, localAddress = null, httpPort = port, manual = true, manualAddress = address))
        } catch (e: UnknownHostException) {
            AddResult.Error("Couldn't find $address on the network.")
        } catch (e: Exception) {
            AddResult.Error(
                "Couldn't reach a streaming host at $address. Check that Sunshine is running on the PC, " +
                    "that both devices are on the same network, and that the firewall allows it.",
            )
        }
    }

    /** Removes a host and all of its games from the library. */
    suspend fun remove(host: StreamHostEntity) = withContext(Dispatchers.IO) {
        status.value[host.id]?.takeIf { it.online && host.paired }?.let { st ->
            runCatching { http(st.address!!, host.httpPort, st.httpsPort, cert(host)).unpair() }
        }
        writeLock.withLock {
            gamesDao.getByHost(host.id).forEach { g -> g.hostArt?.let { File(it).delete() } }
            gamesDao.deleteByHost(host.id)
            hostsDao.delete(host)
        }
        _status.update { it - host.id }
    }

    private suspend fun upsert(
        details: ComputerDetails,
        localAddress: String?,
        httpPort: Int,
        manual: Boolean,
        manualAddress: String? = null,
    ): StreamHostEntity = writeLock.withLock {
        val now = System.currentTimeMillis()
        val existing = hostsDao.getByUuid(details.uuid)
        val reportedLocal = details.localAddress?.address
        val host = if (existing == null) {
            val fresh = StreamHostEntity(
                uuid = details.uuid,
                name = details.name,
                localAddress = localAddress ?: reportedLocal,
                manualAddress = if (manual) manualAddress else null,
                remoteAddress = details.remoteAddress?.address,
                httpPort = httpPort,
                macAddress = details.macAddress?.takeIf { it != "00:00:00:00:00:00" },
                addedAt = now,
                lastSeenAt = now,
            )
            fresh.copy(id = hostsDao.insert(fresh))
        } else {
            existing.copy(
                name = details.name,
                localAddress = localAddress ?: reportedLocal ?: existing.localAddress,
                manualAddress = if (manual) manualAddress else existing.manualAddress,
                remoteAddress = details.remoteAddress?.address ?: existing.remoteAddress,
                httpPort = httpPort,
                macAddress = details.macAddress?.takeIf { it != "00:00:00:00:00:00" } ?: existing.macAddress,
                lastSeenAt = now,
            ).also { hostsDao.update(it) }
        }
        val address = manualAddress ?: localAddress ?: reportedLocal
        _status.update {
            it + (host.id to HostStatus(
                online = true,
                address = address,
                httpsPort = details.httpsPort,
                hostSaysPaired = details.pairState == PairingManager.PairState.PAIRED,
                runningAppId = details.runningGameId,
            ))
        }
        host
    }

    // ---- Reachability ---------------------------------------------------------------------------

    /** Polls one host on each of its addresses; returns its new status. */
    suspend fun refresh(host: StreamHostEntity): HostStatus = withContext(Dispatchers.IO) {
        val cert = cert(host)
        var lastError: String? = null
        for (address in host.addresses) {
            try {
                val details = http(address, host.httpPort, 0, cert).getComputerDetails(true)
                if (details.uuid != host.uuid) continue // A different PC now has this address.
                val st = HostStatus(
                    online = true,
                    address = address,
                    httpsPort = details.httpsPort,
                    hostSaysPaired = details.pairState == PairingManager.PairState.PAIRED,
                    runningAppId = details.runningGameId,
                )
                _status.update { it + (host.id to st) }
                writeLock.withLock {
                    hostsDao.get(host.id)?.let { h ->
                        hostsDao.update(
                            h.copy(
                                name = details.name,
                                lastSeenAt = System.currentTimeMillis(),
                                macAddress = details.macAddress?.takeIf { it != "00:00:00:00:00:00" } ?: h.macAddress,
                                remoteAddress = details.remoteAddress?.address ?: h.remoteAddress,
                            ),
                        )
                    }
                }
                return@withContext st
            } catch (e: Exception) {
                lastError = e.message
            }
        }
        HostStatus(online = false, error = lastError).also { st -> _status.update { it + (host.id to st) } }
    }

    /** Refreshes every host and re-syncs the games of paired, reachable ones. Best effort. */
    fun refreshAll(syncGames: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            for (host in hostsDao.getAll()) {
                launch {
                    val st = refresh(host)
                    if (syncGames && st.online && host.paired && st.hostSaysPaired) syncApps(host)
                }
            }
        }
    }

    // ---- Pairing --------------------------------------------------------------------------------

    /**
     * Pairs with [host] and pins the host's certificate. Returns true when paired.
     *
     * Without [pin], shows a new PIN through [pairing] that the user types into Sunshine's web UI.
     * With [pin] and [fingerprint] (pair with one scan), the PC enters the PIN itself, and the host's
     * certificate must match [fingerprint] (SHA-256 of its DER, lowercase hex).
     */
    suspend fun pair(host: StreamHostEntity, pin: String? = null, fingerprint: String? = null): Boolean = withContext(Dispatchers.IO) {
        val fromScan = pin != null
        val st = status.value[host.id]?.takeIf { it.online } ?: refresh(host)
        if (!st.online || st.address == null) {
            _pairing.value = PairingState.Failed(host.id, "${host.name} is offline. Turn it on (or wake it) and try again.", fromScan)
            return@withContext false
        }
        try {
            val stored = cert(host)
            val http = http(st.address, host.httpPort, st.httpsPort, stored)
            val storedMatches = fingerprint == null || stored != null && sha256Hex(stored.encoded) == fingerprint
            if (host.paired && storedMatches && http.pairState == PairingManager.PairState.PAIRED) {
                _pairing.value = PairingState.Done(host.id, host.name)
                return@withContext true
            }
            val usedPin = pin ?: PairingManager.generatePinString()
            _pairing.value = PairingState.Waiting(host.id, host.name, if (fromScan) null else usedPin)
            val pm = http.pairingManager
            val result = pm.pair(http.getServerInfo(true), usedPin)
            when (result) {
                PairingManager.PairState.PAIRED -> {
                    val serverCert = pm.pairedCert
                    if (fingerprint != null && sha256Hex(serverCert.encoded) != fingerprint) {
                        // Someone else answered on that address: undo the pairing and keep nothing of it.
                        runCatching { http(st.address, host.httpPort, st.httpsPort, serverCert).unpair() }
                        return@withContext fail(host, "This code doesn't match the PC that answered.", fromScan)
                    }
                    val pem = Base64.encodeToString(serverCert.encoded, Base64.NO_WRAP)
                    val paired = writeLock.withLock {
                        val h = (hostsDao.get(host.id) ?: host).copy(serverCert = pem)
                        hostsDao.update(h)
                        h
                    }
                    _pairing.value = PairingState.Done(host.id, host.name)
                    refresh(paired)
                    syncApps(paired)
                    true
                }
                PairingManager.PairState.PIN_WRONG -> fail(
                    host,
                    // With a scanned code the PC enters the PIN, so a mismatch almost always means a stale code.
                    if (fromScan) "The code expired. Show a new one on the PC." else "The PIN didn't match. Try again and enter the new PIN.",
                    fromScan,
                )
                PairingManager.PairState.ALREADY_IN_PROGRESS -> fail(host, "Another device is already pairing with ${host.name}.", fromScan)
                PairingManager.PairState.FAILED ->
                    fail(
                        host,
                        when {
                            st.runningAppId != 0 -> "${host.name} is busy streaming a game. Quit it on the PC and try again."
                            fromScan -> "Pairing didn't finish. Show a new code on the PC and scan it again."
                            else -> "Pairing failed. Make sure you entered the PIN in Sunshine's web UI (PIN tab)."
                        },
                        fromScan,
                    )
                else -> fail(host, "Pairing failed.", fromScan)
            }
        } catch (e: FileNotFoundException) {
            fail(host, "${host.name} doesn't support pairing (404).", fromScan)
        } catch (e: Exception) {
            fail(host, e.message ?: "Pairing failed.", fromScan)
        }
    }

    private fun fail(host: StreamHostEntity, message: String, fromScan: Boolean = false): Boolean {
        _pairing.value = PairingState.Failed(host.id, message, fromScan)
        return false
    }

    /**
     * Pair with one scan: finds the PC on the addresses in [code], adds it, and pairs using the PIN the
     * PC enters into Sunshine itself. Progress and the result go through [pairing].
     */
    suspend fun pairFromScan(code: PairCode): Boolean = withContext(Dispatchers.IO) {
        _pairing.value = PairingState.Connecting(code.name)
        // Ask every address at once; the first one in the PC's order that answers wins. The probes run
        // in the app scope so a slow, dead address doesn't hold us up once a better one has answered.
        val probes = code.addresses.map { address ->
            scope.async(Dispatchers.IO) {
                runCatching { address to http(address, code.httpPort, 0, null).getComputerDetails(true) }.getOrNull()
            }
        }
        var found: Pair<String, ComputerDetails>? = null
        for (probe in probes) {
            found = probe.await()
            if (found != null) break
        }
        if (found == null) {
            _pairing.value = PairingState.Failed(
                null, "Can't reach ${code.name}. Make sure the phone is on the same Wi-Fi as the PC.", fromScan = true,
            )
            return@withContext false
        }
        val (address, details) = found
        val host = upsert(details, localAddress = null, httpPort = code.httpPort, manual = true, manualAddress = address)
        pair(host, pin = code.pin, fingerprint = code.fingerprint)
    }

    /** Starts [pairFromScan] in the app's scope, so leaving the scanner page doesn't cancel it. */
    fun startPairFromScan(code: PairCode) {
        scope.launch { pairFromScan(code) }
    }

    private val _pairLink = MutableStateFlow<String?>(null)
    /** A `pocketdeck://pair` link the app was opened with (e.g. from the system camera), not handled yet. */
    val pairLink: StateFlow<String?> = _pairLink.asStateFlow()

    fun offerPairLink(link: String) {
        _pairLink.value = link
    }

    fun takePairLink(): String? = _pairLink.value.also { _pairLink.value = null }

    fun cancelPairingUi() {
        _pairing.value = PairingState.Idle
    }

    // ---- Games ----------------------------------------------------------------------------------

    /** Mirrors the host's app list into the library and downloads missing box art. */
    suspend fun syncApps(host: StreamHostEntity): Boolean = withContext(Dispatchers.IO) {
        val target = target(host) ?: return@withContext false
        _syncing.update { it + host.id }
        try {
            val http = http(target)
            val apps = http.appList.map { HostApp(it.appId, it.appName, it.isHdrSupported) }
            writeLock.withLock {
                val existing = gamesDao.getByHost(host.id)
                val diff = StreamAppSync.diff(host.id, host.uuid, existing, apps, System.currentTimeMillis())
                existing.filter { it.id in diff.deleteIds }.forEach { g -> g.hostArt?.let { File(it).delete() } }
                gamesDao.deleteIds(diff.deleteIds)
                gamesDao.insertAll(diff.inserts)
                gamesDao.updateAll(diff.updates)
            }
            for (game in gamesDao.getByHost(host.id)) {
                if (game.hostArt != null && File(game.hostArt).exists()) continue
                val appId = game.streamAppId ?: continue
                val file = File(artDir, "pc_${host.id}_$appId.png")
                val ok = runCatching {
                    http.getBoxArt(NvApp(game.title, appId, false)).use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    file.length() > 0
                }.getOrDefault(false)
                if (ok) gamesDao.setHostArt(game.id, file.absolutePath) else file.delete()
            }
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "App list sync failed for ${host.name}", e)
            false
        } finally {
            _syncing.update { it - host.id }
        }
    }

    // ---- Power / sessions -----------------------------------------------------------------------

    /** Sends Wake-on-LAN magic packets. Returns an error message or null. */
    suspend fun wake(host: StreamHostEntity): String? = withContext(Dispatchers.IO) {
        val mac = host.macAddress ?: return@withContext "PocketDeck doesn't know ${host.name}'s network card yet. Connect to it once while it's on."
        val details = ComputerDetails().apply {
            macAddress = mac
            val port = host.httpPort
            localAddress = host.localAddress?.let { ComputerDetails.AddressTuple(it, port) }
            manualAddress = host.manualAddress?.let { ComputerDetails.AddressTuple(it, port) }
            remoteAddress = host.remoteAddress?.let { ComputerDetails.AddressTuple(it, port) }
        }
        runCatching { WakeOnLanSender.sendWolPacket(details) }.exceptionOrNull()?.let { "Couldn't send the wake-up signal: ${it.message}" }
    }

    /** Quits whatever is running on the host. */
    suspend fun quitApp(host: StreamHostEntity): Boolean = withContext(Dispatchers.IO) {
        val target = target(host) ?: return@withContext false
        val ok = runCatching { http(target).quitApp() }.getOrDefault(false)
        refresh(host)
        ok
    }

    /** Resolves a reachable, paired host for streaming, or null. */
    suspend fun target(host: StreamHostEntity): StreamTarget? = withContext(Dispatchers.IO) {
        val cert = cert(host) ?: return@withContext null
        val st = status.value[host.id]?.takeIf { it.online && it.address != null } ?: refresh(host)
        if (!st.online || st.address == null) return@withContext null
        StreamTarget(host, st.address, host.httpPort, st.httpsPort, cert, crypto)
    }

    suspend fun host(id: Long): StreamHostEntity? = hostsDao.get(id)

    // ---- Helpers --------------------------------------------------------------------------------

    private fun http(address: String, httpPort: Int, httpsPort: Int, cert: X509Certificate?) =
        NvHTTP(ComputerDetails.AddressTuple(address, httpPort), httpsPort, UNIQUE_ID, cert, crypto)

    private fun http(t: StreamTarget) = http(t.address, t.httpPort, t.httpsPort, t.serverCert)

    private fun cert(host: StreamHostEntity): X509Certificate? = host.serverCert?.let { pem ->
        runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(Base64.decode(pem, Base64.DEFAULT))) as X509Certificate
        }.getOrNull()
    }

    companion object {
        private const val TAG = "PocketDeck.Hosts"

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private const val DISCOVERY_INTERVAL_MS = 1500

        /** `host`, `host:port`, `[v6]` or `[v6]:port` → address + HTTP port. */
        fun parseAddress(input: String): Pair<String, Int>? {
            val s = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            if (s.isEmpty() || s.any { it.isWhitespace() }) return null
            val default = StreamHostEntity.DEFAULT_HTTP_PORT
            if (s.startsWith("[")) {
                val end = s.indexOf(']').takeIf { it > 1 } ?: return null
                val host = s.substring(1, end)
                val rest = s.substring(end + 1)
                val port = if (rest.isEmpty()) default else rest.removePrefix(":").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                return host to port
            }
            val colons = s.count { it == ':' }
            return when (colons) {
                0 -> s to default
                1 -> {
                    val port = s.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                    s.substringBefore(':').ifEmpty { return null } to port
                }
                else -> s to default // bare IPv6
            }
        }
    }
}
