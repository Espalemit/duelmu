package id.eduduel.app.core

import java.text.BreakIterator

/** Kesalahan validasi nickname; dipetakan ke pesan berbahasa Indonesia di lapisan UI. */
enum class NicknameError { EMPTY, TOO_SHORT, TOO_LONG, INVALID_CHARACTERS }

sealed interface NicknameValidation {
    data class Valid(val value: String) : NicknameValidation
    data class Invalid(val error: NicknameError) : NicknameValidation
}

/**
 * Aturan nickname Guest untuk duel:
 * - 2-20 grapheme (karakter yang terlihat pengguna) setelah `trim`. Emoji gabungan (ZWJ),
 *   modifier warna kulit, dan bendera dihitung sebagai satu karakter dan TIDAK pernah dipotong
 *   di tengah surrogate pair.
 * - Ditolak: hanya spasi, karakter kontrol (termasuk newline/tab), pemisah baris/paragraf,
 *   kontrol arah bidi (rawan spoofing), private-use, dan code point yang belum ditetapkan.
 * - Spasi berurutan di tengah dirapatkan menjadi satu spasi.
 * Nama identik DIIZINKAN; identitas teknis memakai playerSessionId, bukan nama.
 */
object NicknameValidator {
    const val MIN_GRAPHEMES = 2
    const val MAX_GRAPHEMES = 20

    /** Batas byte UTF-8 untuk nickname yang diterima dari jaringan (20 grapheme emoji kompleks masih muat). */
    const val MAX_WIRE_BYTES = 256

    private val BIDI_CONTROLS = setOf(
        0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069,
    )

    /** Karakter format yang sah di dalam emoji/aksara (ZWNJ, ZWJ). */
    private val ALLOWED_FORMAT = setOf(0x200C, 0x200D)

    fun graphemeCount(text: String): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var count = 0
        while (it.next() != BreakIterator.DONE) count++
        return count
    }

    /** Potong ke maksimal [max] grapheme pada batas grapheme (aman untuk emoji). */
    fun truncateGraphemes(text: String, max: Int = MAX_GRAPHEMES): String {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var count = 0
        var end = 0
        while (true) {
            val next = it.next()
            if (next == BreakIterator.DONE) return text
            count++
            if (count > max) return text.substring(0, end)
            end = next
        }
    }

    /** Untuk `onValueChange` TextField: buang newline/tab dan batasi panjang secara aman. */
    fun sanitizeInput(raw: String): String {
        val noBreaks = raw.replace(Regex("[\\r\\n\\t\\u2028\\u2029]"), " ")
        // Izinkan sedikit spasi di ujung saat mengetik; batas grapheme tetap dijaga.
        return truncateGraphemes(noBreaks, MAX_GRAPHEMES + 2)
    }

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(raw: String): NicknameValidation {
        // Periksa karakter terlarang (tab/newline/kontrol) SEBELUM spasi dirapatkan.
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return NicknameValidation.Invalid(NicknameError.EMPTY)
        if (!hasOnlyAllowedCodePoints(raw)) return NicknameValidation.Invalid(NicknameError.INVALID_CHARACTERS)
        val value = normalize(trimmed)
        if (!hasVisibleCharacter(value)) return NicknameValidation.Invalid(NicknameError.EMPTY)
        val count = graphemeCount(value)
        if (count < MIN_GRAPHEMES) return NicknameValidation.Invalid(NicknameError.TOO_SHORT)
        if (count > MAX_GRAPHEMES) return NicknameValidation.Invalid(NicknameError.TOO_LONG)
        return NicknameValidation.Valid(value)
    }

    /**
     * Validasi nickname dari lawan (jaringan). Mengembalikan nilai bersih atau `null`
     * bila tidak valid; pemanggil memakai fallback seperti "Pemain Host".
     */
    fun sanitizeRemote(raw: String?): String? {
        if (raw == null) return null
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_WIRE_BYTES) return null
        return (validate(raw) as? NicknameValidation.Valid)?.value
    }

    private fun hasOnlyAllowedCodePoints(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            // surrogate tunggal (tidak berpasangan) -> Character.SURROGATE
            when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE -> return false
                Character.FORMAT -> if (cp !in ALLOWED_FORMAT) return false
            }
            if (cp in BIDI_CONTROLS) return false
            i += Character.charCount(cp)
        }
        return true
    }

    private fun hasVisibleCharacter(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val type = Character.getType(cp).toByte()
            val invisible = Character.isWhitespace(cp) || Character.isSpaceChar(cp) ||
                type == Character.FORMAT || type == Character.NON_SPACING_MARK ||
                type == Character.ENCLOSING_MARK || cp == 0xFE0F || cp == 0xFE0E
            if (!invisible) return true
            i += Character.charCount(cp)
        }
        return false
    }
}
