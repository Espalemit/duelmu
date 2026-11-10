package id.eduduel.app.net

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.eduduel.app.TestBank
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.net.room.RoomGuestSession
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.net.transport.LanController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Uji asap MANUAL terhadap aplikasi sungguhan di HP/emulator yang menjadi Host room Wi-Fi.
 * Dilewati kecuali variabel lingkungan diisi, mis.:
 *
 *   adb forward tcp:47830 tcp:47830
 *   EDUDUEL_LAN_TARGET=127.0.0.1:47830 EDUDUEL_LAN_CODE=1234 gradlew :app:testDebugUnitTest --tests "*LanDeviceSmokeTest*"
 *
 * Tamu JVM ini masuk room, menekan SIAP, menunggu Host menekan Mulai, lalu menjawab 2 soal.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LanDeviceSmokeTest {
    @Test fun joinRealDeviceRoom() = runBlocking {
        val target = System.getenv("EDUDUEL_LAN_TARGET")
        val code = System.getenv("EDUDUEL_LAN_CODE")
        assumeTrue("EDUDUEL_LAN_TARGET tidak diisi", !target.isNullOrBlank() && !code.isNullOrBlank())
        val (host, port) = LanController.parseAddress(target!!) ?: error("alamat tidak valid: $target")
        val lan = LanController(ApplicationProvider.getApplicationContext())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val guest = RoomGuestSession("Tamu JVM", TestBank.bank, MonotonicClock { System.nanoTime() / 1_000_000 }, scope, "jvm",
            TransportKind.LAN, roomCode = code)
        try {
            guest.attach(lan.connect(host, port))
            val lobby = withTimeout(15_000) { guest.state.first { it.phase == RoomPhase.LOBBY && it.players.isNotEmpty() } }
            println("SMOKE lobby players=${lobby.players.map { it.nickname }} subject=${lobby.config.subject}")
            guest.setReady(true)
            println("SMOKE ready; waiting for host to press Mulai")
            repeat(2) { round ->
                val q = withTimeout(180_000) { guest.state.first { it.phase == RoomPhase.QUESTION && it.activeRound?.index == round } }
                val ar = q.activeRound!!
                println("SMOKE round ${round + 1}: ${ar.round.question.id} ${ar.round.question.subject} ${ar.round.question.difficulty} order=${ar.round.optionOrder}")
                delay(2_000)
                guest.answer(ar.round.question.correctOptionId!!)
                val rev = withTimeout(200_000) { guest.state.first { it.phase == RoomPhase.REVEAL && it.results.size == round + 1 } }
                println("SMOKE result ${round + 1}: " + rev.results.last().players.map { (id, r) -> "${rev.nameOf(id)}=${r.points}" })
                guest.requestNext()
            }
            guest.leave()
            delay(500)
        } finally {
            scope.cancel()
        }
    }
}
