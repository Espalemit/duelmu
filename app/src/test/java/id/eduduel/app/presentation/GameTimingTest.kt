package id.eduduel.app.presentation

import id.eduduel.app.presentation.game.NEVER_MS
import id.eduduel.app.presentation.game.msSince
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresi 0.4.0: stempel waktu awal Long.MIN_VALUE membuat `now - lastShotAt` overflow negatif,
 * sehingga tembakan asteroid selalu ditolak (cooldown) dan laser tergambar terus di tengah arena.
 */
class GameTimingTest {
    @Test fun neverHappened_isInfinitelyLongAgo_forAnyClockValue() {
        for (now in listOf(0L, 1L, 5_000L, 123_456_789L, Long.MAX_VALUE / 2)) {
            val since = msSince(now, NEVER_MS)
            assertTrue("cooldown tembak 300 ms harus lolos saat belum pernah menembak (now=$now)", since >= 300)
            assertTrue("laser 220 ms tidak boleh tampil sebelum menembak (now=$now)", !(since < 220))
        }
    }

    @Test fun normalElapsed() {
        assertEquals(250L, msSince(10_250, 10_000))
        assertTrue(msSince(10_100, 10_000) < 300) // tembakan beruntun < 300 ms tetap ditahan
        assertTrue(msSince(10_400, 10_000) >= 300)
    }
}
