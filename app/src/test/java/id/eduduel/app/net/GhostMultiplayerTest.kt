package id.eduduel.app.net

import id.eduduel.app.TestBank
import id.eduduel.app.core.Ids
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.GhostMode
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.QuestionMode
import id.eduduel.app.domain.Subject
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.AsteroidSubmission
import id.eduduel.app.domain.game.GameType
import id.eduduel.app.net.protocol.AsteroidGhost
import id.eduduel.app.net.protocol.BankCheck
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.FireAction
import id.eduduel.app.net.protocol.GameActionEvent
import id.eduduel.app.net.protocol.GameStateUpdate
import id.eduduel.app.net.protocol.GameSubmit
import id.eduduel.app.net.protocol.Hello
import id.eduduel.app.net.protocol.MatchSetup
import id.eduduel.app.net.protocol.MessageCodec
import id.eduduel.app.net.protocol.PROTOCOL_VERSION
import id.eduduel.app.net.protocol.Payload
import id.eduduel.app.net.protocol.QuestionPrepare
import id.eduduel.app.net.protocol.QuestionReady
import id.eduduel.app.net.protocol.QuestionStart
import id.eduduel.app.net.protocol.Ready
import id.eduduel.app.net.room.GhostHub
import id.eduduel.app.net.room.GhostInterpolator
import id.eduduel.app.net.room.GhostPrivacy
import id.eduduel.app.net.room.RoomGuestSession
import id.eduduel.app.net.room.RoomHostSession
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.RoomSessionCore
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.net.transport.ConnectionEvent
import id.eduduel.app.net.transport.InMemoryConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ghost multiplayer SIMETRIS dengan Host + 3 Tamu SUNGGUHAN (codec frame yang sama dengan TCP/RFCOMM):
 * di layar setiap pemain, ketiga pemain lain tampil sebagai ghost — termasuk Host bagi Tamu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GhostMultiplayerTest {
    private val bank = TestBank.bank
    private fun TestScope.clock() = MonotonicClock { testScheduler.currentTime }
    private fun TestScope.step(ms: Long) { advanceTimeBy(ms); runCurrent() }

    private class Room(val host: RoomHostSession, val guests: List<RoomGuestSession>) {
        val all: List<RoomSessionCore> get() = listOf(host) + guests
        fun id(s: RoomSessionCore) = s.state.value.me.sessionId
    }

    private fun TestScope.newRoom(config: MatchConfig, guests: Int = 3): Room {
        val host = RoomHostSession("Ana (Host)", bank, clock(), backgroundScope, "test", TransportKind.LAN, null, config, seedProvider = { 77L })
        val gs = (1..guests).map { i ->
            val (h, g) = InMemoryConnection.pair("HP Host", "HP $i")
            val guest = RoomGuestSession("Tamu $i", bank, clock(), backgroundScope, "test", TransportKind.LAN)
            host.attach(h); guest.attach(g); runCurrent()
            guest
        }
        return Room(host, gs)
    }

    /** Lobi -> mulai -> tutorial dilewati semua -> QUESTION ronde pertama. */
    private fun TestScope.startGameRound(r: Room) {
        r.guests.forEach { it.setReady(true) }; runCurrent()
        r.host.startMatch(); runCurrent()
        r.all.forEach { s ->
            assertEquals(RoomPhase.PREPARING, s.state.value.phase)
            assertTrue("tutorial game tampil sebelum START", s.state.value.prepare != null)
            assertNull("waktu soal belum berjalan saat tutorial", s.state.value.activeRound)
        }
        r.host.confirmReady(); r.guests.forEach { it.confirmReady() }; runCurrent()
        r.all.forEach { assertEquals(RoomPhase.QUESTION, it.state.value.phase) }
    }

    @Test
    fun fourPlayers_everyScreenShowsTheOtherThreeAsGhosts_hostIncluded() = runTest {
        val r = newRoom(MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME, GhostMode.LIVE))
        startGameRound(r)
        // Semua pemain memainkan SOAL & SEED yang sama.
        val rounds = r.all.map { it.state.value.activeRound!!.round }
        assertEquals(1, rounds.map { it.question.id to it.seed }.toSet().size)

        r.all.forEachIndexed { i, s -> s.publishGhost(AsteroidGhost(x = 0.1f + i * 0.2f, filled = 0, wrong = 0)) }
        step(400)
        val ids = r.all.map(r::id)
        r.all.forEachIndexed { i, s ->
            val seen = s.ghosts.value
            val expected = ids.toSet() - ids[i]
            assertEquals("pemain ${'A' + i} melihat ${expected.size} ghost lain", expected, seen.keys)
            assertFalse("ghost sendiri tidak pernah ada", ids[i] in seen)
            ids.forEachIndexed { j, id ->
                if (j != i) assertEquals(0.1f + j * 0.2f, (seen.getValue(id).latest as AsteroidGhost).x, 1e-6f)
            }
        }
        // Gerakan berikutnya juga tersebar (Host -> semua Tamu, Tamu -> Host -> Tamu lain).
        r.guests[1].publishGhost(AsteroidGhost(x = 0.9f)); step(300)
        listOf(r.host, r.guests[0], r.guests[2]).forEach {
            assertEquals(0.9f, (it.ghosts.value.getValue(r.id(r.guests[1])).latest as AsteroidGhost).x, 1e-6f)
        }
        // Event aksi (tembak) juga di-relay ke semua.
        r.host.publishAction(FireAction(0.4f, token = "2x", correct = true)); runCurrent()
        r.guests.forEach { g -> assertEquals("2x", (g.ghosts.value.getValue(r.id(r.host)).events.last().action as FireAction).token) }
    }

    @Test
    fun ghostsAreIsolated_doNotChangeLocalGameState_andSubmissionsAreReplayedByHost() = runTest {
        val r = newRoom(MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME))
        startGameRound(r)
        val ar = r.host.state.value.activeRound!!
        val spec = ar.round.question.game!!.spec as AsteroidSpec
        r.guests[0].publishAction(FireAction(0.5f, token = spec.answerTokens[0], correct = true)); step(200)
        // Aksi ghost tidak mengunci atau mengubah jawaban pemain lain.
        r.all.drop(1).forEach { assertNull(it.state.value.activeRound!!.myGame) }
        assertNull(r.host.state.value.activeRound!!.myGame)
        step(2_000)
        val ok = AsteroidSubmission(spec.answerTokens.indices.toList())
        val bad = AsteroidSubmission(List(spec.maxWrong + 1) { spec.answerTokens.size })
        r.host.submitGame(ok)
        r.guests[0].submitGame(ok)
        r.guests[1].submitGame(bad)
        r.guests[2].submitGame(null)
        runCurrent()
        assertEquals(RoomPhase.REVEAL, r.host.state.value.phase)
        val res = r.host.state.value.results.single()
        val q = ar.round.question
        assertTrue(res.of(r.id(r.host))!!.correct)
        assertTrue(res.of(r.id(r.guests[0]))!!.correct)
        assertFalse(res.of(r.id(r.guests[1]))!!.correct)
        assertEquals(0, res.of(r.id(r.guests[2]))!!.points)
        val pts = res.of(r.id(r.guests[0]))!!.points
        assertTrue(pts in q.difficulty.basePoints..q.difficulty.basePoints * 3 / 2)
        // Semua HP memverifikasi hasil yang sama (mesin game deterministik).
        r.guests.forEach { assertEquals(res.players, it.state.value.results.single().players) }
    }

    @Test
    fun fairMode_ghostsStillVisible_butLabelsParametersAndSuccessHidden() = runTest {
        val r = newRoom(MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME, GhostMode.FAIR))
        startGameRound(r)
        r.guests[0].publishGhost(AsteroidGhost(x = 0.3f, filled = 1, wrong = 1))
        r.guests[0].publishAction(FireAction(0.3f, token = "2x", correct = true))
        r.host.publishGhost(AsteroidGhost(x = 0.7f, filled = 2, wrong = 0))
        r.guests[1].publishGhost(AsteroidGhost(x = 0.5f, filled = 1))
        r.guests[2].publishGhost(AsteroidGhost(x = 0.6f, wrong = 2))
        step(400)
        val b = r.id(r.guests[0])
        // B tetap terlihat sebagai ghost bagi A, C, D (FAIR tidak menghapus ghost) ...
        for (viewer in listOf(r.host, r.guests[1], r.guests[2])) {
            val t = viewer.ghosts.value.getValue(b)
            val g = t.latest as AsteroidGhost
            assertEquals(0.3f, g.x, 1e-6f)
            // ... tetapi tanpa jumlah kotak terisi/salah dan tanpa label token / indikator berhasil.
            assertNull(g.filled); assertNull(g.wrong)
            val fire = t.events.last().action as FireAction
            assertNull(fire.token); assertNull(fire.correct)
            assertTrue(GhostPrivacy.isFairSafe(g))
        }
        // Host juga tetap tampil sebagai ghost bagi ketiga Tamu, tersensor.
        r.guests.forEach { assertNull((it.ghosts.value.getValue(r.id(r.host)).latest as AsteroidGhost).filled) }
        // Pemain B melihat A, C, D sebagai ghost dalam mode FAIR, semuanya tersensor.
        assertEquals(setOf(r.id(r.host), r.id(r.guests[1]), r.id(r.guests[2])), r.guests[0].ghosts.value.keys)
        assertTrue(r.guests[0].ghosts.value.values.all { GhostPrivacy.isFairSafe(it.latest!!) })
    }

    /** Klien yang dimodifikasi mengirim data LIVE di mode FAIR: Host menyensor sebelum relay. */
    @Test
    fun fairMode_hostRedactsMaliciousClient_andRejectsSpam() = runTest {
        val cfg = MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME, GhostMode.FAIR)
        val r = newRoom(cfg, guests = 1)
        val (hostSide, rawSide) = InMemoryConnection.pair("HP Host", "HP Nakal")
        val rawId = Ids.random()
        var seq = 0L
        var matchId: String? = null
        var round = -1
        suspend fun send(p: Payload) = rawSide.send(Envelope(PROTOCOL_VERSION, seq++, Ids.random(), matchId, round.takeIf { it >= 0 }, p))
        backgroundScope.launch {
            rawSide.events.collect { e ->
                val env = (e as? ConnectionEvent.Received)?.envelope ?: return@collect
                when (val p = env.payload) {
                    is MatchSetup -> { matchId = env.matchId; send(BankCheck(true, bank.bankSha256)) }
                    is QuestionPrepare -> { round = env.roundIndex!!; send(QuestionReady(p.questionId)) }
                    else -> Unit
                }
            }
        }
        r.host.attach(hostSide); runCurrent()
        send(Hello("x", bank.versionName, bank.bankSha256, "Nakal", rawId)); runCurrent()
        send(Ready(true)); r.guests[0].setReady(true); runCurrent()
        r.host.startMatch(); runCurrent()
        r.host.confirmReady(); r.guests[0].confirmReady(); runCurrent()
        assertEquals(RoomPhase.QUESTION, r.host.state.value.phase)
        // klien nakal mengirim isi LIVE (filled/wrong) walau room FAIR
        send(GameStateUpdate(rawId, GameType.ASTEROID, 0, 10, AsteroidGhost(0.5f, filled = 2, wrong = 0))); step(50)
        val seenByGuest = r.guests[0].ghosts.value.getValue(rawId).latest as AsteroidGhost
        assertNull("Host menyensor sebelum relay", seenByGuest.filled)
        assertNull((r.host.ghosts.value.getValue(rawId).latest as AsteroidGhost).filled)
        // spoof playerSessionId pemain lain -> diganti identitas pengirim sebenarnya
        send(GameStateUpdate(r.id(r.guests[0]), GameType.ASTEROID, 1, 20, AsteroidGhost(0.6f))); step(50)
        assertEquals(0.6f, (r.host.ghosts.value.getValue(rawId).latest as AsteroidGhost).x, 1e-6f)
        // angka di luar rentang dunia ditolak (JSON tidak bisa memuat NaN)
        val rejectedBefore = r.host.rejectedGhostMessages
        send(GameStateUpdate(rawId, GameType.ASTEROID, 2, 30, AsteroidGhost(5f))); step(10)
        assertEquals(rejectedBefore + 1, r.host.rejectedGhostMessages)
        // spam 200 update sekaligus -> sebagian besar ditolak rate-limit
        repeat(200) { i -> send(GameStateUpdate(rawId, GameType.ASTEROID, 10 + i, 40L + i, AsteroidGhost(0.1f + (i % 50) / 100f))) }
        runCurrent()
        assertTrue("rate-limit aktif: ${r.host.rejectedGhostMessages}", r.host.rejectedGhostMessages - rejectedBefore > 150)
        // submit game malformed (indeks token di luar rentang) -> 0 poin, bukan crash/benar
        send(GameSubmit(r.host.state.value.activeRound!!.round.question.id, 1_000, AsteroidSubmission(listOf(999)))); runCurrent()
        r.host.submitGame(null); r.guests[0].submitGame(null); runCurrent()
        val res = r.host.state.value.results.single().of(rawId)!!
        assertFalse(res.correct); assertEquals(0, res.points)
    }

    @Test
    fun tutorialTimeout_startsRoundEvenIfSomeoneNeverConfirms_noDeadlock() = runTest {
        val r = newRoom(MatchConfig(Subject.FISIKA, QuestionMode.GAME))
        r.guests.forEach { it.setReady(true) }; runCurrent()
        r.host.startMatch(); runCurrent()
        r.guests[0].confirmReady(); runCurrent()
        assertEquals(RoomPhase.PREPARING, r.host.state.value.phase)
        step(8_000 + 50) // tutorial otomatis selesai di semua HP
        r.all.forEach { assertEquals(RoomPhase.QUESTION, it.state.value.phase) }
        // Tidak ada yang mengunci: Host menutup ronde setelah batas waktu + toleransi (tidak deadlock).
        step(r.host.state.value.activeRound!!.durationMs + 3_000)
        assertEquals(RoomPhase.REVEAL, r.host.state.value.phase)
        assertTrue(r.host.state.value.results.single().players.values.all { it.timedOut && it.points == 0 })
    }

    @Test
    fun fullMixedMatch_allTypesPlayable_totalsConsistentOnEveryDevice() = runTest {
        val r = newRoom(MatchConfig(Subject.BIOLOGI, QuestionMode.MIXED))
        r.guests.forEach { it.setReady(true) }; runCurrent()
        r.host.startMatch(); runCurrent()
        var guard = 0
        while (r.host.state.value.phase != RoomPhase.FINISHED && guard++ < 60) {
            when (r.host.state.value.phase) {
                RoomPhase.PREPARING -> { r.host.confirmReady(); r.guests.forEach { it.confirmReady() }; runCurrent() }
                RoomPhase.QUESTION -> {
                    step(2_000)
                    r.all.forEachIndexed { i, s ->
                        val ar = s.state.value.activeRound ?: return@forEachIndexed
                        val q = ar.round.question
                        when (s) {
                            is RoomHostSession -> if (q.isGame) s.submitGame(if (i % 2 == 0) q.game!!.reference else null) else s.answer(q.correctOptionId!!)
                            is RoomGuestSession -> if (q.isGame) s.submitGame(if (i % 2 == 0) q.game!!.reference else null) else s.answer(q.correctOptionId!!)
                        }
                    }
                    runCurrent()
                }
                RoomPhase.REVEAL -> { r.host.requestNext(); r.guests.forEach { it.requestNext() }; runCurrent() }
                else -> step(100)
            }
        }
        assertEquals(RoomPhase.FINISHED, r.host.state.value.phase)
        val results = r.host.state.value.results
        assertEquals(10, results.size)
        assertEquals(4, results.count { it.round.question.type == id.eduduel.app.domain.QuestionType.CLASSIC_MCQ })
        assertEquals(3, results.count { it.round.question.type == id.eduduel.app.domain.QuestionType.VISUAL_MCQ })
        assertEquals(3, results.count { it.round.question.isGame })
        r.guests.forEach { g ->
            assertEquals(RoomPhase.FINISHED, g.state.value.phase)
            assertEquals(results.map { it.players }, g.state.value.results.map { it.players })
        }
        // pemain genap (Host & Tamu 2) berhasil di semua soal; pemain ganjil gagal di soal game
        val host = r.host.state.value.standings.first { it.player.isHost }
        assertEquals(10, host.correct)
        assertEquals(7, r.host.state.value.standings.first { it.player.nickname == "Tamu 1" }.correct)
    }

    @Test
    fun disconnectDuringGame_othersContinue_ghostRemoved() = runTest {
        val r = newRoom(MatchConfig(Subject.KIMIA, QuestionMode.GAME))
        startGameRound(r)
        r.guests[2].publishGhost(id.eduduel.app.net.protocol.LabGhost(0.2f, 0.3f)); step(300)
        val gone = r.id(r.guests[2])
        assertTrue(gone in r.host.ghosts.value)
        r.guests[2].leave(); runCurrent()
        assertFalse(gone in r.host.ghosts.value)
        r.host.submitGame(null); r.guests[0].submitGame(null); r.guests[1].submitGame(null); runCurrent()
        assertEquals(RoomPhase.REVEAL, r.host.state.value.phase)
        assertTrue(r.host.state.value.results.single().of(gone)!!.absent)
    }

    // ---------------------------------------------------------------- unit: hub & interpolasi

    @Test
    fun ghostHub_dropsOutOfOrder_stale_andOwnUpdates() {
        val hub = GhostHub { "me" }
        hub.reset(3)
        fun u(p: String, seq: Int, x: Float) = GameStateUpdate(p, GameType.ASTEROID, seq, seq * 100L, AsteroidGhost(x))
        assertTrue(hub.accept(3, u("b", 5, 0.5f), 1_000))
        assertFalse("out-of-order", hub.accept(3, u("b", 4, 0.1f), 1_010))
        assertFalse("duplikat", hub.accept(3, u("b", 5, 0.1f), 1_020))
        assertFalse("ronde basi", hub.accept(2, u("c", 9, 0.1f), 1_030))
        assertFalse("ghost sendiri", hub.accept(3, u("me", 1, 0.1f), 1_040))
        assertTrue(hub.accept(3, u("b", 6, 0.7f), 1_100))
        assertEquals(setOf("b"), hub.tracks.value.keys)
        assertEquals(0.7f, (hub.tracks.value.getValue("b").latest as AsteroidGhost).x)
        assertEquals(4, hub.dropped)
    }

    @Test
    fun interpolation_smoothsJitter_150msBehind() {
        val hub = GhostHub { "me" }
        hub.reset(0)
        hub.accept(0, GameStateUpdate("b", GameType.ASTEROID, 0, 0, AsteroidGhost(0.0f)), 1_000)
        hub.accept(0, GameStateUpdate("b", GameType.ASTEROID, 1, 125, AsteroidGhost(1.0f)), 1_200) // datang terlambat (jitter)
        val t = hub.tracks.value.getValue("b")
        val mid = GhostInterpolator.at(t, nowMs = 1_250, delayMs = 150) as AsteroidGhost // render di t=1100 -> tengah
        assertEquals(0.5f, mid.x, 1e-3f)
        assertEquals(1.0f, (GhostInterpolator.at(t, 2_000) as AsteroidGhost).x)
        assertEquals(0.0f, (GhostInterpolator.at(t, 900) as AsteroidGhost).x)
    }
}
