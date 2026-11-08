package id.eduduel.app.net.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Perangkat Bluetooth yang ditampilkan di UI (nama + status pairing). MAC tidak dikirim ke lawan. */
data class BtDevice(val address: String, val name: String?, val bonded: Boolean)

enum class AdapterState { UNSUPPORTED, OFF, TURNING_ON, ON, TURNING_OFF }

/** Kebutuhan izin untuk sebuah aksi Bluetooth; berbeda antara Android 8-11 dan 12+. */
enum class BtAction { HOST, CONNECT_PAIRED, SCAN }

/**
 * Pembungkus Android Bluetooth Classic RFCOMM.
 *
 * - Host: `listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)` lalu `accept()` berulang di IO
 *   dispatcher (satu socket per Tamu, topologi bintang); server socket ditutup saat room ditutup.
 * - Tamu: `createRfcommSocketToServiceRecord(APP_UUID)` lalu `connect()` di IO dispatcher.
 * - Selalu memakai socket SECURE (pairing + enkripsi). Socket insecure tidak dipakai.
 */
class BluetoothController(private val context: Context) {

    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _adapterState = MutableStateFlow(readState())
    val adapterState: StateFlow<AdapterState> = _adapterState.asStateFlow()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) _adapterState.value = readState()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, stateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    val isSupported: Boolean get() = adapter != null

    fun refreshState() {
        _adapterState.value = readState()
    }

    private fun readState(): AdapterState {
        val a = manager?.adapter ?: return AdapterState.UNSUPPORTED
        return when (a.state) {
            BluetoothAdapter.STATE_ON -> AdapterState.ON
            BluetoothAdapter.STATE_TURNING_ON -> AdapterState.TURNING_ON
            BluetoothAdapter.STATE_TURNING_OFF -> AdapterState.TURNING_OFF
            else -> AdapterState.OFF
        }
    }

    // ------------------------------------------------------------------ Izin

    /**
     * Izin runtime per versi Android:
     * - Android 12+ (API 31+): BLUETOOTH_CONNECT (koneksi/daftar perangkat berpasangan),
     *   BLUETOOTH_SCAN (pemindaian, `neverForLocation`), BLUETOOTH_ADVERTISE (Host discoverable).
     * - Android 8-11: BLUETOOTH/BLUETOOTH_ADMIN adalah izin install-time; lokasi HANYA untuk
     *   pemindaian (discovery). Host & koneksi ke perangkat berpasangan tidak butuh lokasi.
     */
    fun requiredPermissions(action: BtAction): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (action) {
                BtAction.HOST -> listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                BtAction.CONNECT_PAIRED -> listOf(Manifest.permission.BLUETOOTH_CONNECT)
                BtAction.SCAN -> listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            when (action) {
                BtAction.HOST, BtAction.CONNECT_PAIRED -> emptyList()
                BtAction.SCAN -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

    fun missingPermissions(action: BtAction): List<String> =
        requiredPermissions(action).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun hasPermissions(action: BtAction): Boolean = missingPermissions(action).isEmpty()

    private fun canConnect(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun canScan(): Boolean = hasPermissions(BtAction.SCAN)

    // ------------------------------------------------------------------ Perangkat

    @SuppressLint("MissingPermission") // dicek oleh canConnect()
    fun bondedDevices(): List<BtDevice> {
        val a = adapter ?: return emptyList()
        if (!canConnect()) return emptyList()
        return try {
            a.bondedDevices.orEmpty()
                .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE } // hanya Classic / Dual
                .map { BtDevice(it.address, it.name, bonded = true) }
                .sortedBy { it.name ?: it.address }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Nama Bluetooth perangkat ini (ditampilkan agar lawan mengenali Host). */
    @SuppressLint("MissingPermission")
    fun localName(): String? = if (canConnect()) runCatching { adapter?.name }.getOrNull() else null

    /**
     * Discovery perangkat sekitar. Flow berakhir saat discovery selesai (~12 detik) atau
     * dibatalkan; discovery dihentikan saat kolektor berhenti.
     */
    @SuppressLint("MissingPermission")
    fun discover(): Flow<BtDevice> = callbackFlow {
        val a = adapter
        if (a == null || !canScan()) {
            close(SecurityException("Izin pemindaian belum diberikan"))
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        if (d != null) {
                            val name = runCatching { d.name }.getOrNull()
                            val bonded = runCatching { d.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                            trySend(BtDevice(d.address, name, bonded))
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        try {
            if (a.isDiscovering) a.cancelDiscovery()
            if (!a.startDiscovery()) close(IOException("Gagal memulai pemindaian"))
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { if (canScan()) a.cancelDiscovery() }
        }
    }

    // ------------------------------------------------------------------ RFCOMM

    /**
     * Host room: buka SATU server socket RFCOMM secure dan terima koneksi berulang kali (maksimal
     * 3 Tamu dibatasi oleh sesi). Server socket ditutup saat kolektor berhenti (room ditutup).
     */
    @SuppressLint("MissingPermission")
    fun acceptConnections(): Flow<DuelConnection> = channelFlow {
        val a = adapter ?: throw IOException("Bluetooth tidak tersedia")
        if (!canConnect()) throw SecurityException("Izin BLUETOOTH_CONNECT belum diberikan")
        val server: BluetoothServerSocket = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
        launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val socket: BluetoothSocket = server.accept()
                    send(socket.toConnection())
                }
            } catch (e: IOException) {
                if (isActive) close(e)
            }
        }
        awaitClose { runCatching { server.close() } }
    }.flowOn(Dispatchers.IO)

    /** Tamu: hubungkan ke Host. Bila belum berpasangan, Android menampilkan dialog pairing ke pengguna. */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): DuelConnection = withContext(Dispatchers.IO) {
        val a = adapter ?: throw IOException("Bluetooth tidak tersedia")
        if (!canConnect()) throw SecurityException("Izin BLUETOOTH_CONNECT belum diberikan")
        // Discovery memperlambat koneksi; hentikan bila izin memungkinkan.
        if (canScan()) runCatching { a.cancelDiscovery() }
        val device = a.getRemoteDevice(address)
        val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { socket.close() } }
            try {
                socket.connect()
                cont.resume(Unit)
            } catch (e: IOException) {
                runCatching { socket.close() }
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
        socket.toConnection()
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothSocket.toConnection(): DuelConnection {
        val label = runCatching { remoteDevice?.name }.getOrNull() ?: "Perangkat Bluetooth"
        val socket = this
        return StreamDuelConnection(inputStream, outputStream, label, onClose = { runCatching { socket.close() } })
    }

    fun dispose() {
        runCatching { context.unregisterReceiver(stateReceiver) }
    }

    companion object {
        /** UUID khusus aplikasi Duelmu (bukan UUID layanan sistem seperti SPP). */
        val APP_UUID: UUID = UUID.fromString("6f1c3a52-8d1e-4c7b-9a35-2e0d5b7c9e41")
        const val SERVICE_NAME = "Duelmu Room RFCOMM"
        const val DISCOVERABLE_SECONDS = 120
    }
}
