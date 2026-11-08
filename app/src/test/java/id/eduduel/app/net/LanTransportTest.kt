package id.eduduel.app.net

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.eduduel.app.TestBank
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.Subject
import id.eduduel.app.net.room.RoomFailure
import id.eduduel.app.net.room.RoomGuestSession
import id.eduduel.app.net.room.RoomHostSession
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.net.transport.LanController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Jalur Wi-Fi/hotspot memakai socket TCP SUNGGUHAN (loopback 127.0.0.1) dan kode transport yang
 * sama dengan aplikasi: ServerSocket Host, connect Tamu, framing, handshake + kode room, ROOM_STATE,
 * sampai soal pertama dimulai di kedua sisi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LanTransportTest {
    private val bank = TestBank.bank
    private val clock = MonotonicClock { System.nanoTime() / 1_000_000 }

    @Test fun hostAndGuest_overRealTcpSockets_joinRoom_andStartMatch() = runBlocking {
        val lan = LanController(ApplicationProvider.getApplicationContext())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = lan.openServer()
        try {
            val host = RoomHostSession("Budi", bank, clock, scope, "test", TransportKind.LAN, roomCode = "2468",
                initialConfig = MatchConfig(Subject.KIMIA))
            scope.launch { server.accepts().collect { host.attach(it) } }

            // Kode salah ditolak lewat jaringan nyata.
            val intruder = RoomGuestSession("Iseng", bank, clock, scope, "test", TransportKind.LAN, roomCode = "0000")
            intruder.attach(lan.connect("127.0.0.1", server.port))
            withTimeout(10_000) { intruder.state.first { it.phase == RoomPhase.FAILED } }
            assertEquals(RoomFailure.WRONG_ROOM_CODE, intruder.state.value.failure)

            val guest = RoomGuestSession("Ani", bank, clock, scope, "test", TransportKind.LAN, roomCode = "2468")
            guest.attach(lan.connect("127.0.0.1", server.port))
            withTimeout(10_000) { guest.state.first { it.phase == RoomPhase.LOBBY && it.players.size == 2 } }
            assertEquals(listOf("Budi", "Ani"), guest.state.value.players.map { it.nickname })
            assertEquals(MatchConfig(Subject.KIMIA), guest.state.value.config)
            assertEquals("127.0.0.1", guest.state.value.hostLabel)

            guest.setReady(true)
            withTimeout(10_000) { host.state.first { it.canStart } }
            host.startMatch()
            val g = withTimeout(10_000) { guest.state.first { it.phase == RoomPhase.QUESTION } }
            val h = host.state.first { it.phase == RoomPhase.QUESTION }
            assertEquals(h.activeRound!!.round.question.id, g.activeRound!!.round.question.id)
            assertEquals(h.activeRound!!.round.optionOrder, g.activeRound!!.round.optionOrder)
            assertTrue(g.activeRound!!.round.question.subject == Subject.KIMIA)

            guest.leave()
            host.leave()
        } finally {
            server.close()
            scope.cancel()
        }
    }

    @Test fun parseManualAddress() {
        assertEquals("192.168.43.1" to LanController.DEFAULT_PORT, LanController.parseAddress(" 192.168.43.1 "))
        assertEquals("10.0.0.5" to 5000, LanController.parseAddress("10.0.0.5:5000"))
        assertNull(LanController.parseAddress("192.168.1"))
        assertNull(LanController.parseAddress("192.168.1.300"))
        assertNull(LanController.parseAddress("192.168.1.3:99999"))
        assertNull(LanController.parseAddress("host.local"))
    }
}
