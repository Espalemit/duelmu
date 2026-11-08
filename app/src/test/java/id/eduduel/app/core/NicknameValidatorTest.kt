package id.eduduel.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NicknameValidatorTest {

    private fun valid(s: String) = (NicknameValidator.validate(s) as NicknameValidation.Valid).value
    private fun error(s: String) = (NicknameValidator.validate(s) as NicknameValidation.Invalid).error

    @Test fun acceptsNormalNames() {
        assertEquals("Budi", valid("Budi"))
        assertEquals("Ani S.", valid("  Ani   S.  ")) // trim + rapatkan spasi
        assertEquals("ab", valid("ab"))
    }

    @Test fun rejectsEmptyAndWhitespaceOnly() {
        assertEquals(NicknameError.EMPTY, error(""))
        assertEquals(NicknameError.EMPTY, error("     "))
        assertEquals(NicknameError.EMPTY, error("　  ")) // spasi Unicode
    }

    @Test fun rejectsTooShortAndTooLong() {
        assertEquals(NicknameError.TOO_SHORT, error("A"))
        assertEquals(NicknameError.TOO_SHORT, error(" A "))
        assertEquals("a".repeat(20), valid("a".repeat(20)))
        assertEquals(NicknameError.TOO_LONG, error("a".repeat(21)))
    }

    @Test fun rejectsControlNewlineAndBidiCharacters() {
        assertEquals(NicknameError.INVALID_CHARACTERS, error("Bu\ndi"))
        assertEquals(NicknameError.INVALID_CHARACTERS, error("Bu\u0007di"))
        assertEquals(NicknameError.INVALID_CHARACTERS, error("Bu\tdi"))
        assertEquals(NicknameError.INVALID_CHARACTERS, error("Budi‮"))
        assertEquals(NicknameError.INVALID_CHARACTERS, error("Budi "))
    }

    @Test fun countsEmojiAsSingleGraphemes() {
        // Emoji keluarga (ZWJ) = 1 grapheme, bendera = 1, kulit = 1
        val family = "👨‍👩‍👧‍👦"
        assertEquals(1, NicknameValidator.graphemeCount(family))
        assertEquals(1, NicknameValidator.graphemeCount("🇮🇩"))
        assertEquals(1, NicknameValidator.graphemeCount("👍🏽"))
        assertEquals("Ani $family", valid("Ani $family"))
        assertEquals(NicknameError.TOO_SHORT, error("🚀"))
        assertEquals("🚀🚀", valid("🚀🚀"))
        assertEquals("🚀".repeat(20), valid("🚀".repeat(20)))
        assertEquals(NicknameError.TOO_LONG, error("🚀".repeat(21)))
    }

    @Test fun truncationNeverSplitsSurrogatePairs() {
        val input = "🚀".repeat(25)
        val t = NicknameValidator.truncateGraphemes(input, 20)
        assertEquals(20, NicknameValidator.graphemeCount(t))
        assertEquals(40, t.length) // 20 x surrogate pair
        assertTrue(t.all { !Character.isSurrogate(it) } || t.chunked(2).all { Character.isSurrogatePair(it[0], it[1]) })
        val mixed = "a" + "👨‍👩‍👧‍👦".repeat(21)
        val t2 = NicknameValidator.truncateGraphemes(mixed, 20)
        assertEquals(20, NicknameValidator.graphemeCount(t2))
        assertTrue(t2.endsWith("👨‍👩‍👧‍👦"))
    }

    @Test fun sanitizeInputRemovesNewlines() {
        assertEquals("Bu di", NicknameValidator.sanitizeInput("Bu\ndi"))
    }

    @Test fun remoteNamesAreValidatedDefensively() {
        assertEquals("Ani", NicknameValidator.sanitizeRemote(" Ani "))
        assertNull(NicknameValidator.sanitizeRemote(null))
        assertNull(NicknameValidator.sanitizeRemote(""))
        assertNull(NicknameValidator.sanitizeRemote("x".repeat(1000)))
        assertNull(NicknameValidator.sanitizeRemote("a\u0000b"))
    }

    @Test fun supportsNonLatinScripts() {
        assertEquals("ສະບາຍດີ", valid("ສະບາຍດີ"))
        assertEquals("محمد", valid("محمد"))
        assertEquals("小明", valid("小明"))
    }
}
