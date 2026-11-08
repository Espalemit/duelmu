package id.eduduel.app.net.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.BindException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

/** Room yang ditemukan di jaringan lokal lewat NSD (mDNS/DNS-SD). */
data class LanRoom(val serviceName: String, val hostNickname: String, val host: String, val port: Int)

/** Alamat IPv4 lokal perangkat ini (untuk ditampilkan ke Tamu sebagai cadangan "masukkan IP"). */
data class LocalAddress(val ip: String, val interfaceName: String) {
    /** Antarmuka hotspot umumnya bernama ap0/swlan0/wlan1; Wi-Fi biasa wlan0. */
    val looksLikeHotspot: Boolean get() = interfaceName.startsWith("ap") || interfaceName.startsWith("swlan")
}

/**
 * Room lewat Wi-Fi yang sama atau hotspot salah satu HP. Tidak butuh internet: hanya socket TCP
 * di jaringan lokal (karena itu aplikasi memegang izin INTERNET, satu-satunya cara Android
 * mengizinkan socket). Tidak ada koneksi ke server mana pun.
 *
 * - Host: [openServer] membuka ServerSocket (port tetap [DEFAULT_PORT] bila bebas) dan
 *   mengiklankan room via NSD tipe [SERVICE_TYPE].
 * - Tamu: [discover] mencari room; bila jaringan memblokir mDNS (sebagian router/hotspot),
 *   Tamu dapat memasukkan IP Host secara manual lalu [connect].
 * - Data di jaringan lokal TIDAK dienkripsi (isinya hanya nickname, jawaban, skor); kode room 4 digit
 *   mencegah orang lain di jaringan yang sama ikut masuk tanpa izin.
 */
class LanController(context: Context) {
    private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)

    // ------------------------------------------------------------------ Host

    class LanServer internal constructor(private val server: ServerSocket) {
        val port: Int get() = server.localPort

        /** Koneksi masuk berulang sampai [close]. */
        fun accepts(): Flow<DuelConnection> = channelFlow {
            launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val socket = server.accept()
                        send(socket.toConnection())
                    }
                } catch (e: IOException) {
                    if (isActive && !server.isClosed) close(e)
                }
            }
            awaitClose { }
        }.flowOn(Dispatchers.IO)

        fun close() {
            runCatching { server.close() }
        }
    }

    suspend fun openServer(): LanServer = withContext(Dispatchers.IO) {
        val server = ServerSocket().apply { reuseAddress = true }
        try {
            server.bind(InetSocketAddress(DEFAULT_PORT))
        } catch (e: BindException) {
            server.bind(InetSocketAddress(0)) // port tetap dipakai aplikasi lain: pakai port acak
        }
        LanServer(server)
    }

    /**
     * Iklankan room di jaringan lokal. Nama layanan acak (bukan nama/ID perangkat); nickname Host
     * dikirim sebagai atribut agar Tamu mengenali room. Kode room TIDAK diiklankan.
     * Mengembalikan fungsi untuk berhenti mengiklankan.
     */
    fun advertise(hostNickname: String, port: Int, tag: String): () -> Unit {
        val manager = nsd ?: return {}
        val info = NsdServiceInfo().apply {
            serviceName = "Duelmu-$tag"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(ATTR_NICKNAME, hostNickname.take(40))
            setAttribute(ATTR_VERSION, id.eduduel.app.net.protocol.PROTOCOL_VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
        return { runCatching { manager.unregisterService(listener) } }
    }

    /** Alamat IPv4 lokal yang aktif (Wi-Fi, hotspot). Tidak butuh izin tambahan. */
    fun localAddresses(): List<LocalAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { ni ->
                ni.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { LocalAddress(it.hostAddress ?: "", ni.name) }
            }
            .filter { it.ip.isNotBlank() && !it.interfaceName.startsWith("rmnet") }
            .sortedBy { if (it.interfaceName.startsWith("wlan")) 0 else 1 }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ Tamu

    private sealed interface DiscoveryEvent {
        data class Found(val info: NsdServiceInfo) : DiscoveryEvent
        data class Lost(val serviceName: String) : DiscoveryEvent
    }

    /** Daftar room yang terlihat; diperbarui terus sampai kolektor berhenti. */
    fun discover(): Flow<List<LanRoom>> = callbackFlow {
        val manager = nsd ?: run { close(IOException("NSD tidak tersedia")); return@callbackFlow }
        val queue = Channel<DiscoveryEvent>(Channel.UNLIMITED)
        val rooms = LinkedHashMap<String, LanRoom>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IOException("Pencarian room gagal ($errorCode)"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName.startsWith("Duelmu-")) queue.trySend(DiscoveryEvent.Found(serviceInfo))
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                queue.trySend(DiscoveryEvent.Lost(serviceInfo.serviceName))
            }
        }
        trySend(emptyList())
        launch {
            // Resolve satu per satu: Android lama menolak resolve paralel (FAILURE_ALREADY_ACTIVE).
            for (ev in queue) {
                when (ev) {
                    is DiscoveryEvent.Found -> resolve(manager, ev.info)?.let { rooms[it.serviceName] = it }
                    is DiscoveryEvent.Lost -> rooms.remove(ev.serviceName)
                }
                send(rooms.values.toList())
            }
        }
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { close(it) }
        awaitClose {
            queue.close()
            runCatching { manager.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION") // resolveService masih berfungsi di API 34+; API baru butuh executor & callback per layanan.
    private suspend fun resolve(manager: NsdManager, info: NsdServiceInfo): LanRoom? = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        serviceInfo.hostAddresses.firstOrNull { it is Inet4Address } ?: serviceInfo.hostAddresses.firstOrNull()
                    } else {
                        serviceInfo.host
                    }
                    val nick = serviceInfo.attributes[ATTR_NICKNAME]?.toString(Charsets.UTF_8)
                    val room = host?.hostAddress?.let { LanRoom(serviceInfo.serviceName, nick ?: "Room Duelmu", it, serviceInfo.port) }
                    if (cont.isActive) cont.resume(room)
                }
            })
        }
    }

    /** Tamu: sambung ke Host lewat TCP. */
    suspend fun connect(host: String, port: Int = DEFAULT_PORT): DuelConnection = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
        socket.toConnection()
    }

    companion object {
        const val SERVICE_TYPE = "_eduduel._tcp."
        const val DEFAULT_PORT = 47_830
        private const val ATTR_NICKNAME = "n"
        private const val ATTR_VERSION = "v"
        private const val RESOLVE_TIMEOUT_MS = 6_000L
        private const val CONNECT_TIMEOUT_MS = 6_000

        /** Parse "192.168.1.5" atau "192.168.1.5:47830". null bila bukan IPv4 yang valid. */
        fun parseAddress(input: String): Pair<String, Int>? {
            val t = input.trim()
            val parts = t.split(":")
            if (parts.size > 2) return null
            val ip = parts[0]
            val octets = ip.split(".")
            if (octets.size != 4 || octets.any { o -> o.isEmpty() || o.length > 3 || !o.all(Char::isDigit) || o.toInt() > 255 }) return null
            val port = if (parts.size == 2) parts[1].toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null else DEFAULT_PORT
            return ip to port
        }
    }
}

private fun Socket.toConnection(): DuelConnection {
    tcpNoDelay = true
    keepAlive = true
    val socket = this
    val label = inetAddress?.hostAddress ?: "Wi-Fi"
    return StreamDuelConnection(getInputStream(), getOutputStream(), label, onClose = { runCatching { socket.close() } })
}
