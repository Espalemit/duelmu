package id.eduduel.app.core

import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Sumber waktu MONOTONIC untuk timer ronde. Memakai `SystemClock.elapsedRealtime()` yang terus
 * berjalan saat aplikasi di background/layar mati dan tidak terpengaruh perubahan jam dinding.
 */
fun interface MonotonicClock {
    fun nowMs(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}

object Ids {
    private val secureRandom = SecureRandom()

    /** ID acak (UUID v4) untuk matchId/playerSessionId/msgId. Bukan MAC, bukan akun. */
    fun random(): String = UUID.randomUUID().toString()

    fun randomSeed(): Long = secureRandom.nextLong()

    /** Kode room 4 digit yang ditampilkan Host dan diketik Tamu (room Wi-Fi). */
    fun roomCode(): String = "%04d".format(secureRandom.nextInt(10_000))
}

object Hashing {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))
}

/**
 * Fallback rendering matematika sederhana tanpa jaringan. Asset sudah dinormalisasi ke
 * superscript/subskrip Unicode oleh tools/build_question_asset.py; fungsi ini menangani sisa
 * notasi caret (`10^(-2)`, `x^2`) bila bank soal diperbarui tanpa normalisasi.
 */
object MathText {
    private val superscript = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
        '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'x' to 'ˣ', 'i' to 'ⁱ', '=' to '⁼',
    )
    private val groupPattern = Regex("""\^\(([^()]{1,12})\)""")
    private val simplePattern = Regex("""\^(-?[0-9]{1,4}|[nxi])""")

    private fun toSuperscript(s: String): String? {
        val sb = StringBuilder()
        for (c in s) {
            if (c == ' ') continue
            sb.append(superscript[c] ?: return null)
        }
        return sb.toString()
    }

    fun format(input: String): String {
        if ('^' !in input) return input
        var out = groupPattern.replace(input) { m -> toSuperscript(m.groupValues[1]) ?: m.value }
        out = simplePattern.replace(out) { m -> toSuperscript(m.groupValues[1]) ?: m.value }
        return out
    }
}
