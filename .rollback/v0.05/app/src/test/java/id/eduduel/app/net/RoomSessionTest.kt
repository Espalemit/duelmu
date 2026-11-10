package id.eduduel.app.net

import id.eduduel.app.DifferentVersionBank
import id.eduduel.app.TestBank
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.MatchOutcome
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.Subject
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.EndReason
import id.eduduel.app.net.protocol.MessageCodec
import id.eduduel.app.net.protocol.PROTOCOL_VERSION
import id.eduduel.app.net.protocol.Ready
import id.eduduel.app.net.room.ActiveRound
import id.eduduel.app.net.room.RoomFailure
import id.eduduel.app.net.room.RoomGuestSession
import id.eduduel.app.net.room.RoomHostSession
import id.eduduel.app.net.room.RoomPhase
import id.eduduel.app.net.room.RoomSessionCore
import id.eduduel.app.net.room.RoomState
import id.eduduel.app.net.room.TransportKind
import id.eduduel.app.net.transport.DuelConnection
import id.eduduel.app.net.transport.InMemoryConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sesi Host + Tamu SUNGGUHAN yang saling bicara lewat encoder/decoder frame yang sama dengan
 * RFCOMM/TCP (InMemoryConnection), dengan waktu virtual. Tidak ada mock protokol.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSessionTest {

    private val bank = TestBank.bank
    private val math = MatchConfig(Subject.MATEMATIKA)

    private class Room(
        val host: RoomHostSession,
        val guests: MutableList<RoomGuestSession>,
        val hostSides: MutableList<InMemoryConnection>,
        val guestSides: MutableList<InMemoryConnection>,
        val closed: MutableList<RoomState>,
    ) {
        val all: List<RoomSessionCore> get() = listOf(host) + guests
    }

    private fun TestScope.clock() = MonotonicClock { testScheduler.currentTime }

    private fun TestScope.step(ms: Long) {
        advanceTimeBy(ms); runCurrent()
    }

    private var seed = 41L

    private fun TestScope.newRoom(
        names: List<String> = listOf("Budi", "Ani", "Sari", "Joko"),
        config: MatchConfig = math,
        roomCode: String? = null,
    ): Room {
        val closed = mutableListOf<RoomState>()
        val host = RoomHostSession(
            names.first(), bank, clock(), backgroundScope, "test", TransportKind.LAN, roomCode, config,
            seedProvider = { ++seed }, onMatchClosed = { closed += it },
        )
        val room = Room(host, mutableListOf(), mutableListOf(), mutableListOf(), closed)
        names.drop(1).forEach { addGuest(room, it, code = roomCode) }
        runCurrent()
        return room
    }

    private fun TestScope.addGuest(
        room: Room,
        name: String,
        guestBank: QuestionBank = bank,
        code: String? = null,
        reconnect: Boolean = false,
    ): RoomGuestSession {
        val label = "HP $name"
        val (hostSide, guestSide) = InMemoryConnection.pair(labelA = "HP Host", labelB = label)
        val reconnector: (suspend () -> DuelConnection?)? = if (reconnect) {
            {
                delay(1_000) // menyambung ulang butuh waktu, seperti di perangkat nyata
                val (h, g) = InMemoryConnection.pair(labelA = "HP Host", labelB = label)
                room.host.attach(h)
                g
            }
        } else null
        val g = RoomGuestSession(name, guestBank, clock(), backgroundScope, "test", TransportKind.LAN, code,
            reconnector = reconnector, onMatchClosed = { room.closed += it })
        room.guests += g; room.hostSides += hostSide; room.guestSides += guestSide
        room.host.attach(hostSide)
        g.attach(guestSide)
        runCurrent()
        return g
    }

    private fun TestScope.allReadyAndStart(room: Room) {
        room.guests.forEach { it.setReady(true) }
        runCurrent()
        room.host.startMatch()
        runCurrent()
    }

    private fun correctOf(ar: ActiveRound) = ar.round.question.correctOptionId
    private fun wrongOf(ar: ActiveRound) = OptionId.entries.first { it != ar.round.question.correctOptionId }

    /** Mainkan satu ronde. [pick] null = tidak menjawab (timeout). Lalu semua menekan Lanjut. */
    private fun TestScope.playRound(room: Room, pick: (Int, ActiveRound) -> OptionId?) {
        assertTrue(room.all.all { it.state.value.phase == RoomPhase.QUESTION || !it.state.value.connected && it !== room.host })
        step(3_000)
        room.all.forEachIndexed { i, s ->
            val ar = s.state.value.activeRound ?: return@forEachIndexed
            if (s.state.value.phase != RoomPhase.QUESTION) return@forEachIndexed
            val opt = pick(i, ar) ?: return@forEachIndexed
            when (s) {
                is RoomHostSession -> s.answer(opt)
                is RoomGuestSession -> s.answer(opt)
            }
        }
        runCurrent()
        // Tunggu timeout/batas toleransi Host secara bertahap (jangan melewati masa REVEAL 6 dtk).
        var waited = 0L
        while (room.host.state.value.phase == RoomPhase.QUESTION && waited < 200_000) { step(500); waited += 500 }
        assertEquals(RoomPhase.REVEAL, room.host.state.value.phase)
        room.host.requestNext()
        room.guests.forEach { it.requestNext() }
        runCurrent()
    }

    // ------------------------------------------------------------------ Room & lobi

    @Test
    fun fourPlayers_joinRoom_everyDeviceSeesAllNicknames_withRandomSessionIds() = runTest {
        val r = newRoom(listOf("Budi 🚀", "Ani", "Sari", "Joko"))
        val names = listOf("Budi 🚀", "Ani", "Sari", "Joko")
        r.all.forEach { s ->
            val st = s.state.value
            assertEquals(RoomPhase.LOBBY, st.phase)
            assertEquals(names, st.players.map { it.nickname })
            assertTrue(st.players.first().isHost)
            assertTrue(st.isFull)
        }
        val ids = r.all.map { it.state.value.me.sessionId }
        assertEquals(4, ids.toSet().size)
        assertEquals(ids, r.host.state.value.players.map { it.sessionId })
        assertEquals("HP Host", r.guests[0].state.value.hostLabel)
    }

    @Test
    fun fifthPlayer_isRejected_roomFull() = runTest {
        val r = newRoom()
        val fifth = addGuest(r, "Lima")
        assertEquals(RoomPhase.FAILED, fifth.state.value.phase)
        assertEquals(RoomFailure.ROOM_FULL, fifth.state.value.failure)
        assertEquals(4, r.host.state.value.players.size)
    }

    @Test
    fun wrongRoomCode_andDifferentBank_areRejected_withoutAffectingRoom() = runTest {
        val r = newRoom(listOf("Budi", "Ani"), roomCode = "1234")
        assertEquals(2, r.host.state.value.players.size)
        val wrong = addGuest(r, "Salah", code = "9999")
        assertEquals(RoomFailure.WRONG_ROOM_CODE, wrong.state.value.failure)
        val otherBank = addGuest(r, "Versi", guestBank = DifferentVersionBank(bank), code = "1234")
        assertEquals(RoomFailure.BANK_MISMATCH, otherBank.state.value.failure)
        assertEquals(listOf("Budi", "Ani"), r.host.state.value.players.map { it.nickname })
    }

    @Test
    fun identicalNicknames_areAllowed_andDistinguishedBySessionId() = runTest {
        val r = newRoom(listOf("Sama", "Sama", "Sama"))
        val players = r.host.state.value.players
        assertEquals(listOf("Sama", "Sama", "Sama"), players.map { it.nickname })
        assertEquals(3, players.map { it.sessionId }.toSet().size)
    }

    @Test
    fun start_requiresAtLeastOneGuest_andAllGuestsReady() = runTest {
        val alone = newRoom(listOf("Sendiri"))
        alone.host.startMatch(); runCurrent()
        assertEquals(RoomPhase.LOBBY, alone.host.state.value.phase)

        val r = newRoom()
        r.guests[0].setReady(true); r.guests[1].setReady(true); runCurrent()
        assertFalse(r.host.state.value.canStart)
        r.host.startMatch(); runCurrent()
        assertEquals(RoomPhase.LOBBY, r.host.state.value.phase)
        // Semua Tamu melihat status SIAP pemain lain.
        assertEquals(listOf(true, true, true, false), r.guests[2].state.value.players.map { it.ready })
        r.guests[2].setReady(true); runCurrent()
        assertTrue(r.host.state.value.canStart)
        r.host.startMatch(); runCurrent()
        assertTrue(r.all.all { it.state.value.inMatch })
    }

    @Test
    fun renameBeforeReady_propagatesToEveryone_lockedAfterReady() = runTest {
        val r = newRoom()
        r.guests[0].changeNickname("Ani Baru"); runCurrent()
        r.all.forEach { assertEquals("Ani Baru", it.state.value.players[1].nickname) }
        r.guests[0].setReady(true); runCurrent()
        r.guests[0].changeNickname("Tidak Boleh"); runCurrent()
        assertEquals("Ani Baru", r.host.state.value.players[1].nickname)
        r.host.changeNickname("Budi Host"); runCurrent()
        r.guests.forEach { assertEquals("Budi Host", it.state.value.players[0].nickname) }
    }

    @Test
    fun subjectChange_resetsReady_andIsVisibleToGuests() = runTest {
        val r = newRoom()
        r.guests.forEach { it.setReady(true) }; runCurrent()
        assertTrue(r.host.state.value.canStart)
        r.host.updateConfig(MatchConfig(Subject.BIOLOGI)); runCurrent()
        assertFalse(r.host.state.value.canStart)
        r.guests.forEach {
            assertEquals(MatchConfig(Subject.BIOLOGI), it.state.value.config)
            assertFalse(it.state.value.me.ready)
        }
        r.host.updateConfig(MatchConfig(null)); runCurrent() // Campuran
        r.guests.forEach { assertNull(it.state.value.config.subject) }
    }

    @Test
    fun kick_removesGuestFromRoom() = runTest {
        val r = newRoom()
        val target = r.guests[1]
        r.host.kick(target.state.value.me.sessionId); runCurrent()
        assertEquals(RoomFailure.KICKED, target.state.value.failure)
        assertEquals(listOf("Budi", "Ani", "Joko"), r.host.state.value.players.map { it.nickname })
        assertEquals(listOf("Budi", "Ani", "Joko"), r.guests[0].state.value.players.map { it.nickname })
    }

    @Test
    fun joiningWhileMatchInProgress_isRejected() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        allReadyAndStart(r)
        val late = addGuest(r, "Telat")
        assertEquals(RoomFailure.MATCH_IN_PROGRESS, late.state.value.failure)
    }

    // ------------------------------------------------------------------ Pertandingan

    @Test
    fun fullMatch_fourPlayers_tenRandomQuestionsFromSubject_identicalEverywhere_consistentTotals() = runTest {
        val r = newRoom()
        allReadyAndStart(r)
        runCurrent()
        assertEquals(RoomPhase.QUESTION, r.host.state.value.phase)
        repeat(10) {
            playRound(r) { i, ar ->
                when (i) {
                    0 -> correctOf(ar) // Host selalu benar
                    1 -> wrongOf(ar)   // Ani selalu salah
                    2 -> correctOf(ar) // Sari selalu benar
                    else -> null       // Joko tidak menjawab -> timeout
                }
            }
        }
        r.all.forEach { assertEquals(RoomPhase.FINISHED, it.state.value.phase) }
        val hostResults = r.host.state.value.lastMatch!!.results
        assertEquals(10, hostResults.size)
        assertEquals(10, hostResults.map { it.round.question.id }.toSet().size)
        assertTrue(hostResults.all { it.round.question.subject == Subject.MATEMATIKA })
        // Soal, urutan opsi, dan skor identik di keempat HP.
        r.guests.forEach { g ->
            val gr = g.state.value.lastMatch!!.results
            assertEquals(hostResults.map { it.round.question.id to it.round.optionOrder }, gr.map { it.round.question.id to it.round.optionOrder })
            assertEquals(hostResults.map { it.players }, gr.map { it.players })
        }
        val standings = r.host.state.value.lastMatch!!.standings
        val byName = standings.associateBy { it.player.nickname }
        assertEquals(0, byName.getValue("Ani").total)
        assertEquals(0, byName.getValue("Joko").total)
        assertEquals(10, byName.getValue("Budi").correct)
        assertTrue(byName.getValue("Budi").total > 0)
        assertTrue(byName.getValue("Sari").total > 0)
        // Ani & Joko seri 0 -> berbagi peringkat yang sama.
        assertEquals(byName.getValue("Ani").rank, byName.getValue("Joko").rank)
        assertEquals(MatchOutcome.LOSE, r.guests[0].state.value.lastMatch!!.outcomeFor(r.guests[0].state.value.me.sessionId))
        assertEquals(4, r.closed.size) // setiap HP menyimpan riwayatnya sendiri
    }

    @Test
    fun scoringUsesFormula_forEachPlayer() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        allReadyAndStart(r)
        val ar = r.host.state.value.activeRound!!
        step(10_000)
        r.host.answer(correctOf(ar)); runCurrent()
        step(5_000)
        r.guests[0].answer(correctOf(ar)); runCurrent()
        val res = r.host.state.value.results.single()
        val q = ar.round.question
        val base = q.difficulty.basePoints
        val total = q.timeLimitMs
        assertEquals(base + (base * (total - 10_000)) / (2 * total), res.of(r.host.state.value.me.sessionId)!!.points.toLong())
        assertEquals(base + (base * (total - 15_000)) / (2 * total), res.of(r.guests[0].state.value.me.sessionId)!!.points.toLong())
        assertEquals(res.players, r.guests[0].state.value.results.single().players)
    }

    @Test
    fun allPlayersTimeOut_roundScoresZeroForEveryone() = runTest {
        val r = newRoom(listOf("Budi", "Ani", "Sari"))
        allReadyAndStart(r)
        val ar = r.host.state.value.activeRound!!
        step(ar.durationMs + 3_000)
        val res = r.host.state.value.results.single()
        assertTrue(res.players.values.all { it.points == 0 && it.timedOut })
        assertEquals(RoomPhase.REVEAL, r.guests[1].state.value.phase)
    }

    @Test
    fun answerIsLocked_secondAnswerIgnored() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        allReadyAndStart(r)
        val ar = r.guests[0].state.value.activeRound!!
        r.guests[0].answer(wrongOf(ar)); runCurrent()
        r.guests[0].answer(correctOf(ar)); runCurrent()
        assertEquals(wrongOf(ar), r.guests[0].state.value.activeRound!!.myAnswer)
        r.host.answer(correctOf(ar)); runCurrent()
        assertEquals(0, r.host.state.value.results.single().of(r.guests[0].state.value.me.sessionId)!!.points)
    }

    @Test
    fun guestDisconnects_othersContinue_absentRoundsZero_noAutomaticWin() = runTest {
        val r = newRoom(listOf("Budi", "Ani", "Sari"))
        allReadyAndStart(r)
        playRound(r) { _, ar -> correctOf(ar) }
        // Sari putus tanpa reconnect.
        r.guestSides[1].simulateDrop(); runCurrent()
        assertEquals(RoomPhase.CANCELLED, r.guests[1].state.value.phase)
        assertFalse(r.host.state.value.players.first { it.nickname == "Sari" }.connected)
        // Pertandingan tetap berjalan untuk Budi & Ani.
        repeat(9) { playRound(r) { _, ar -> correctOf(ar) } }
        assertEquals(RoomPhase.FINISHED, r.host.state.value.phase)
        assertEquals(RoomPhase.FINISHED, r.guests[0].state.value.phase)
        val sariId = r.guests[1].state.value.me.sessionId
        val results = r.host.state.value.lastMatch!!.results
        assertTrue(results.drop(1).all { it.of(sariId)!!.absent && it.of(sariId)!!.points == 0 })
        // Sari tidak mendapat kemenangan otomatis; pemain yang putus tidak membuat pemain lain menang otomatis.
        val sari = r.host.state.value.lastMatch!!.standings.first { it.player.sessionId == sariId }
        assertEquals(3, sari.rank)
        // HP Sari sendiri mencatat pertandingan sebagai dibatalkan (tanpa pemenang).
        assertNull(r.guests[1].state.value.lastMatch!!.outcomeFor(sariId))
    }

    @Test
    fun guestReconnectsWithinWindow_rejoinsFromNextRound() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        val sari = addGuest(r, "Sari", reconnect = true)
        allReadyAndStart(r)
        playRound(r) { _, ar -> correctOf(ar) }
        // Putus saat soal ke-2 aktif.
        assertEquals(RoomPhase.QUESTION, sari.state.value.phase)
        r.guestSides[1].simulateDrop(); runCurrent()
        assertEquals(RoomPhase.RECONNECTING, sari.state.value.phase)
        step(1_500)
        assertEquals(RoomPhase.PREPARING, sari.state.value.phase) // tersambung, menunggu soal berikutnya
        assertTrue(r.host.state.value.players.first { it.nickname == "Sari" }.connected)
        // Ronde 2 tetap jalan untuk Budi & Ani; Sari absen di ronde ini.
        val ar = r.host.state.value.activeRound!!
        r.host.answer(correctOf(ar)); r.guests[0].answer(correctOf(ar)); runCurrent()
        assertEquals(RoomPhase.REVEAL, r.host.state.value.phase)
        val sariId = sari.state.value.me.sessionId
        assertTrue(r.host.state.value.results[1].of(sariId)!!.absent)
        assertEquals(2, sari.state.value.results.size) // hasil ronde yang terlewat tetap tercatat
        r.host.requestNext(); r.guests[0].requestNext(); runCurrent()
        // Ronde 3: Sari ikut lagi.
        assertEquals(RoomPhase.QUESTION, sari.state.value.phase)
        repeat(8) { playRound(r) { _, a -> correctOf(a) } }
        r.all.forEach { assertEquals(RoomPhase.FINISHED, it.state.value.phase) }
        assertEquals(r.host.state.value.lastMatch!!.results.map { it.players }, sari.state.value.lastMatch!!.results.map { it.players })
    }

    @Test
    fun allGuestsLeave_matchCancelledWithoutWinner() = runTest {
        val r = newRoom(listOf("Budi", "Ani", "Sari"))
        allReadyAndStart(r)
        r.guests.forEach { it.leave() }
        runCurrent()
        val st = r.host.state.value
        assertEquals(RoomPhase.CANCELLED, st.phase)
        assertEquals(RoomFailure.NOT_ENOUGH_PLAYERS, st.failure)
        assertNull(st.lastMatch!!.outcomeFor(st.me.sessionId))
    }

    @Test
    fun hostLeaves_everyGuestCancelled() = runTest {
        val r = newRoom()
        allReadyAndStart(r)
        r.host.leave(); runCurrent()
        r.guests.forEach {
            assertEquals(RoomPhase.CANCELLED, it.state.value.phase)
            assertEquals(EndReason.CANCELLED_BY_PLAYER, it.state.value.endReason)
        }
    }

    @Test
    fun hostLeavesLobby_guestsSeeHostLeft() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        r.host.leave(); runCurrent()
        assertEquals(RoomFailure.HOST_LEFT, r.guests[0].state.value.failure)
    }

    @Test
    fun backToRoom_afterMatch_guestsReturnToLobby_andSecondMatchUsesNewQuestions() = runTest {
        val r = newRoom(listOf("Budi", "Ani", "Sari"))
        allReadyAndStart(r)
        repeat(10) { playRound(r) { _, ar -> correctOf(ar) } }
        val first = r.host.state.value.lastMatch!!.results.map { it.round.question.id }
        r.host.backToRoom(); runCurrent()
        r.all.forEach {
            assertEquals(RoomPhase.LOBBY, it.state.value.phase)
            assertTrue(it.state.value.lastMatch != null) // hasil lama tetap bisa dilihat
        }
        r.guests.forEach { assertFalse(it.state.value.me.ready) }
        r.guests[0].changeNickname("Ani 2"); runCurrent()
        allReadyAndStart(r)
        assertTrue(r.host.state.value.inMatch)
        assertEquals("Ani 2", r.host.state.value.participants[1].nickname)
        repeat(10) { playRound(r) { _, ar -> correctOf(ar) } }
        val second = r.host.state.value.lastMatch!!.results.map { it.round.question.id }
        assertNotEquals(first, second)
        assertEquals("Ani 2", r.guests[0].state.value.lastMatch!!.participants[1].nickname)
    }

    // ------------------------------------------------------------------ Ketahanan protokol

    @Test
    fun duplicateAndOutOfOrderMessages_areIgnored_andMalformedFrameDoesNotCrash() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        val env = Envelope(PROTOCOL_VERSION, 999, "dup-1", payload = Ready(true))
        r.hostSides[0].deliverBytes(MessageCodec.encode(env)); runCurrent()
        assertTrue(r.host.state.value.players[1].ready)
        val before = r.host.droppedDuplicates
        r.hostSides[0].deliverBytes(MessageCodec.encode(env)) // msgId sama
        r.hostSides[0].deliverBytes(MessageCodec.encode(env.copy(msgId = "old", seq = 5, payload = Ready(false)))) // seq lama
        runCurrent()
        assertEquals(before + 2, r.host.droppedDuplicates)
        assertTrue(r.host.state.value.players[1].ready)
        r.hostSides[0].deliverBytes(id.eduduel.app.net.protocol.FrameCodec.encode("{rusak".toByteArray())); runCurrent()
        assertEquals(RoomPhase.LOBBY, r.host.state.value.phase)
        assertEquals(2, r.host.state.value.players.size)
    }

    @Test
    fun lateAnswerAfterRoundClosed_isIgnored() = runTest {
        val r = newRoom(listOf("Budi", "Ani"))
        allReadyAndStart(r)
        val ar = r.host.state.value.activeRound!!
        r.host.answer(correctOf(ar)); r.guests[0].answer(correctOf(ar)); runCurrent()
        val before = r.host.ignoredLateMessages
        val late = Envelope(PROTOCOL_VERSION, 10_000, "late", r.host.state.value.matchId, 0,
            id.eduduel.app.net.protocol.Answer(ar.round.question.id, wrongOf(ar).name, 1))
        r.hostSides[0].deliverBytes(MessageCodec.encode(late)); runCurrent()
        assertEquals(before + 1, r.host.ignoredLateMessages)
        assertTrue(r.host.state.value.results.single().of(r.guests[0].state.value.me.sessionId)!!.correct)
    }
}
