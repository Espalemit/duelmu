package id.eduduel.app.net.transport

import id.eduduel.app.net.protocol.DecodeResult
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.FrameDecoder
import id.eduduel.app.net.protocol.MessageCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ConnectionEvent {
    data class Received(val envelope: Envelope) : ConnectionEvent
    /** Frame utuh tetapi isinya tidak valid (JSON rusak/tipe tak dikenal). Koneksi tetap terbuka. */
    data class Malformed(val reason: String) : ConnectionEvent
    data class VersionMismatch(val remoteVersion: Int?) : ConnectionEvent
    /** Koneksi berakhir (EOF, IOException, frame terlalu besar, atau ditutup lokal). */
    data class Closed(val cause: Throwable?, val local: Boolean) : ConnectionEvent
}

/** Koneksi duel point-to-point yang tidak bergantung transport (RFCOMM, in-memory untuk tes/demo). */
interface DuelConnection {
    /** Aliran event; hanya boleh dikumpulkan oleh SATU kolektor. Selesai setelah [ConnectionEvent.Closed]. */
    val events: Flow<ConnectionEvent>
    val peerLabel: String
    suspend fun send(envelope: Envelope)
    fun close()
}

/**
 * Koneksi di atas sepasang stream blocking (mis. BluetoothSocket). Pembacaan berjalan pada
 * IO dispatcher, tidak pernah di main thread. Menutup koneksi menutup socket sehingga
 * `read()` yang blocking ikut berhenti.
 */
class StreamDuelConnection(
    private val input: InputStream,
    private val output: OutputStream,
    override val peerLabel: String,
    private val onClose: () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DuelConnection {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val channel = Channel<ConnectionEvent>(Channel.UNLIMITED)
    private val writeLock = Mutex()
    private val closed = AtomicBoolean(false)
    private val reader: Job

    override val events: Flow<ConnectionEvent> = channel.receiveAsFlow()

    init {
        reader = scope.launch {
            val decoder = FrameDecoder()
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val n = input.read(buffer)
                    if (n < 0) throw IOException("Stream berakhir (EOF)")
                    if (n == 0) continue
                    for (frame in decoder.feed(buffer, 0, n)) {
                        channel.trySend(
                            when (val r = MessageCodec.decode(frame)) {
                                is DecodeResult.Ok -> ConnectionEvent.Received(r.envelope)
                                is DecodeResult.Malformed -> ConnectionEvent.Malformed(r.reason)
                                is DecodeResult.VersionMismatch -> ConnectionEvent.VersionMismatch(r.remoteVersion)
                            },
                        )
                    }
                }
            } catch (t: Throwable) {
                finish(t, local = closed.get())
            }
        }
    }

    override suspend fun send(envelope: Envelope) {
        if (closed.get()) throw IOException("Koneksi sudah ditutup")
        val bytes = MessageCodec.encode(envelope)
        withContext(io) {
            writeLock.withLock {
                try {
                    output.write(bytes)
                    output.flush()
                } catch (e: IOException) {
                    finish(e, local = false)
                    throw e
                }
            }
        }
    }

    override fun close() {
        finish(null, local = true)
    }

    private fun finish(cause: Throwable?, local: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { onClose() }
        channel.trySend(ConnectionEvent.Closed(cause, local))
        channel.close()
        scope.cancel()
    }
}

/**
 * Pasangan koneksi in-memory yang melewati encoder/decoder frame yang sama dengan RFCOMM.
 * Dipakai untuk unit test dan Mode Demo (lawan simulasi) tanpa Bluetooth.
 */
class InMemoryConnection private constructor(override val peerLabel: String) : DuelConnection {
    private val channel = Channel<ConnectionEvent>(Channel.UNLIMITED)
    private val decoder = FrameDecoder()
    private lateinit var peer: InMemoryConnection
    private val closed = AtomicBoolean(false)

    /** Tes dapat menyuntikkan byte mentah (mis. JSON rusak) ke sisi ini. */
    var dropOutgoing: Boolean = false

    override val events: Flow<ConnectionEvent> = channel.receiveAsFlow()

    override suspend fun send(envelope: Envelope) {
        if (closed.get()) throw IOException("Koneksi sudah ditutup")
        if (dropOutgoing) return
        peer.deliverBytes(MessageCodec.encode(envelope))
    }

    @Synchronized
    fun deliverBytes(bytes: ByteArray) {
        if (closed.get()) return
        try {
            for (frame in decoder.feed(bytes)) {
                channel.trySend(
                    when (val r = MessageCodec.decode(frame)) {
                        is DecodeResult.Ok -> ConnectionEvent.Received(r.envelope)
                        is DecodeResult.Malformed -> ConnectionEvent.Malformed(r.reason)
                        is DecodeResult.VersionMismatch -> ConnectionEvent.VersionMismatch(r.remoteVersion)
                    },
                )
            }
        } catch (e: Exception) {
            finish(e, local = false)
        }
    }

    override fun close() {
        if (finish(null, local = true)) peer.finish(IOException("Lawan menutup koneksi"), local = false)
    }

    /** Simulasi putus koneksi tiba-tiba di kedua sisi (tes disconnect). */
    fun simulateDrop() {
        finish(IOException("Simulasi putus"), local = false)
        peer.finish(IOException("Simulasi putus"), local = false)
    }

    private fun finish(cause: Throwable?, local: Boolean): Boolean {
        if (!closed.compareAndSet(false, true)) return false
        channel.trySend(ConnectionEvent.Closed(cause, local))
        channel.close()
        return true
    }

    companion object {
        fun pair(labelA: String = "Perangkat A", labelB: String = "Perangkat B"): Pair<InMemoryConnection, InMemoryConnection> {
            // a.peerLabel = nama perangkat lawan yang terlihat dari sisi a
            val a = InMemoryConnection(labelB)
            val b = InMemoryConnection(labelA)
            a.peer = b
            b.peer = a
            return a to b
        }
    }
}
