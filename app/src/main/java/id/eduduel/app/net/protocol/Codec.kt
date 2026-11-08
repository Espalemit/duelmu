package id.eduduel.app.net.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

/** Batas ukuran satu frame. MATCH_SETUP 15 soal hanya ~2 KB; batas ini mencegah alokasi berlebih. */
const val MAX_FRAME_BYTES = 64 * 1024
const val LENGTH_PREFIX_BYTES = 4

class FrameTooLargeException(val declaredLength: Int) :
    Exception("Frame $declaredLength byte melebihi batas $MAX_FRAME_BYTES")

class InvalidFrameException(message: String) : Exception(message)

/**
 * Framing: [panjang 4 byte big-endian][payload UTF-8 JSON].
 * Tidak pernah mengasumsikan satu `read()` = satu pesan.
 */
object FrameCodec {
    fun encode(payload: ByteArray): ByteArray {
        require(payload.size in 1..MAX_FRAME_BYTES) { "Ukuran payload ${payload.size} di luar batas" }
        val out = ByteArray(LENGTH_PREFIX_BYTES + payload.size)
        val n = payload.size
        out[0] = (n ushr 24).toByte()
        out[1] = (n ushr 16).toByte()
        out[2] = (n ushr 8).toByte()
        out[3] = n.toByte()
        payload.copyInto(out, LENGTH_PREFIX_BYTES)
        return out
    }
}

/**
 * Decoder inkremental: menerima potongan byte sembarang (paket terpotong, beberapa frame dalam
 * satu read) dan mengembalikan frame yang lengkap. Setelah [FrameTooLargeException] atau
 * [InvalidFrameException], stream dianggap tidak sinkron dan koneksi harus ditutup.
 */
class FrameDecoder(private val maxFrameBytes: Int = MAX_FRAME_BYTES) {
    private val header = ByteArray(LENGTH_PREFIX_BYTES)
    private var headerFilled = 0
    private var body: ByteArray? = null
    private var bodyFilled = 0

    fun feed(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size): List<ByteArray> {
        val frames = ArrayList<ByteArray>(1)
        var i = offset
        val end = offset + length
        while (i < end) {
            val b = body
            if (b == null) {
                header[headerFilled++] = chunk[i++]
                if (headerFilled == LENGTH_PREFIX_BYTES) {
                    val len = ((header[0].toInt() and 0xFF) shl 24) or
                        ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                    headerFilled = 0
                    if (len <= 0) throw InvalidFrameException("Panjang frame tidak valid: $len")
                    if (len > maxFrameBytes) throw FrameTooLargeException(len)
                    body = ByteArray(len)
                    bodyFilled = 0
                }
            } else {
                val n = minOf(b.size - bodyFilled, end - i)
                System.arraycopy(chunk, i, b, bodyFilled, n)
                bodyFilled += n
                i += n
                if (bodyFilled == b.size) {
                    frames += b
                    body = null
                    bodyFilled = 0
                }
            }
        }
        return frames
    }

    /** Byte yang sedang tertahan (untuk diagnostik/tes). */
    val pendingBytes: Int get() = headerFilled + bodyFilled
}

sealed interface DecodeResult {
    data class Ok(val envelope: Envelope) : DecodeResult
    data class VersionMismatch(val remoteVersion: Int?) : DecodeResult
    data class Malformed(val reason: String) : DecodeResult
}

/** Encoder/decoder JSON yang defensif. */
object MessageCodec {
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(envelope: Envelope): ByteArray =
        FrameCodec.encode(json.encodeToString(Envelope.serializer(), envelope).toByteArray(Charsets.UTF_8))

    fun decode(frame: ByteArray): DecodeResult {
        val text = try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(frame)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            return DecodeResult.Malformed("UTF-8 tidak valid")
        }
        val obj: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return DecodeResult.Malformed("JSON rusak")
        }
        val version = try {
            obj["v"]?.jsonPrimitive?.int
        } catch (e: Exception) {
            null
        }
        if (version != PROTOCOL_VERSION) return DecodeResult.VersionMismatch(version)
        return try {
            DecodeResult.Ok(json.decodeFromJsonElement(Envelope.serializer(), obj))
        } catch (e: Exception) {
            DecodeResult.Malformed("Payload tidak dikenal/tidak valid: ${e.message?.take(120)}")
        }
    }

    /** Utilitas tes: gabungkan beberapa frame menjadi satu aliran byte. */
    fun concat(vararg frames: ByteArray): ByteArray =
        ByteArrayOutputStream().apply { frames.forEach { write(it) } }.toByteArray()
}
