package id.eduduel.app.net.room

import id.eduduel.app.core.Ids
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.core.NicknameValidator
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.QuestionSelector
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.net.protocol.Answer
import id.eduduel.app.net.protocol.AnswerLocked
import id.eduduel.app.net.protocol.BackToRoom
import id.eduduel.app.net.protocol.BankCheck
import id.eduduel.app.net.protocol.Disconnect
import id.eduduel.app.net.protocol.DisconnectReason
import id.eduduel.app.net.protocol.EndReason
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.ErrorCode
import id.eduduel.app.net.protocol.Hello
import id.eduduel.app.net.protocol.HelloAck
import id.eduduel.app.net.protocol.MAX_ROOM_PLAYERS
import id.eduduel.app.net.protocol.MatchEnd
import id.eduduel.app.net.protocol.MatchSetup
import id.eduduel.app.net.protocol.NextRound
import id.eduduel.app.net.protocol.Payload
import id.eduduel.app.net.protocol.PlayerNameUpdate
import id.eduduel.app.net.protocol.ProtocolError
import id.eduduel.app.net.protocol.QuestionPrepare
import id.eduduel.app.net.protocol.QuestionReady
import id.eduduel.app.net.protocol.QuestionStart
import id.eduduel.app.net.protocol.Ready
import id.eduduel.app.net.protocol.RejectReason
import id.eduduel.app.net.protocol.ResumeSnapshot
import id.eduduel.app.net.protocol.RoomStateMessage
import id.eduduel.app.net.protocol.RoundResult
import id.eduduel.app.net.protocol.RoundResultMessage
import id.eduduel.app.net.protocol.WireRoomPhase
import id.eduduel.app.net.transport.DuelConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update

/**
 * Host room: pusat topologi bintang DAN pemain.
 *
 * - Menerima maksimal 3 Tamu (total [MAX_ROOM_PLAYERS]); Tamu ke-4 ditolak ROOM_FULL.
 * - Lobi: menyiarkan ROOM_STATE setiap ada perubahan; Mulai hanya bila semua Tamu SIAP.
 * - Otoritatif: memilih 10 soal acak dari mapel terpilih (seed acak), membuka/menutup ronde,
 *   menghitung skor semua pemain, menyiarkan hasil. Tamu memverifikasi konsistensi.
 * - Tamu yang putus di tengah pertandingan punya jendela reconnect; selama absen ia mendapat
 *   0 poin per ronde. Pertandingan berlanjut untuk pemain lain; TIDAK ada kemenangan otomatis.
 */
class RoomHostSession(
    myNickname: String,
    bank: QuestionBank,
    clock: MonotonicClock,
    parentScope: CoroutineScope,
    appVersion: String,
    transport: TransportKind,
    /** Kode room yang wajib dikirim Tamu (room Wi-Fi). null = tidak dicek (Bluetooth/demo). */
    private val roomCode: String? = null,
    initialConfig: MatchConfig = MatchConfig.INITIAL,
    timing: RoomTiming = RoomTiming(),
    private val seedProvider: () -> Long = Ids::randomSeed,
    private val onMatchClosed: (RoomState) -> Unit = {},
) : RoomSessionCore(
    RoomState(
        role = RoomRole.HOST,
        transport = transport,
        me = RoomPlayer(Ids.random(), myNickname, isHost = true, ready = true),
        phase = RoomPhase.LOBBY,
        roomCode = roomCode,
        config = initialConfig,
    ),
    bank, clock, parentScope, appVersion, timing,
) {
    private class Guest(val sessionId: String, var nickname: String, var link: Link?) {
        var ready = false
        /** Keluar permanen dari pertandingan berjalan (jendela reconnect habis / keluar sendiri). */
        var left = false
    }

    /** Koneksi yang belum mengirim HELLO. */
    private val pending = HashMap<Int, Link>()
    private val guests = LinkedHashMap<String, Guest>()

    // ---- pertandingan ----
    private var rounds: List<RoundQuestion> = emptyList()
    private var setup: MatchSetup? = null
    private var participantIds: List<String> = emptyList()
    private var currentIndex = -1
    private var roundStartedAt = 0L
    private val answers = HashMap<String, PlayerRoundResult>()
    private val absentThisRound = HashSet<String>()
    private val awaitingBankCheck = HashSet<String>()
    private val awaitingPrepare = HashSet<String>()
    /** Tamu yang menerima hasil ronde ini (yang ditunggu tombol Lanjut). */
    private val revealAudience = HashSet<String>()
    private val nextReady = HashSet<String>()
    private var matchClosedNotified = false
    /** Host menutup room (keluar) atau transport mati: tidak menerima koneksi lagi. */
    private var closedByHost = false

    init {
        publishRoom()
    }

    // ============================================================ API publik

    fun updateConfig(config: MatchConfig) = user(Action.UpdateConfig(config))
    fun changeNickname(name: String) = user(Action.ChangeName(name))
    fun kick(sessionId: String) = user(Action.Kick(sessionId))
    fun startMatch() = user(Action.Start)
    fun answer(option: OptionId) = user(Action.SubmitAnswer(option))
    fun requestNext() = user(Action.Next)
    fun backToRoom() = user(Action.BackToRoom)
    fun leave() = user(Action.Leave)

    private sealed interface Action {
        data class UpdateConfig(val config: MatchConfig) : Action
        data class ChangeName(val name: String) : Action
        data class Kick(val sessionId: String) : Action
        data object Start : Action
        data class SubmitAnswer(val option: OptionId) : Action
        data object Next : Action
        data object BackToRoom : Action
        data object Leave : Action
    }

    private enum class T { BANK_CHECK, PREPARE, LOCAL_TIMEOUT, ROUND_DEADLINE, REVEAL }
    private data class HandshakeKey(val linkId: Int)
    private data class ReconnectKey(val sessionId: String)

    // ============================================================ Koneksi

    override fun onNewConnection(conn: DuelConnection) {
        if (s.phase in setOf(RoomPhase.FAILED) || closedByHost) {
            conn.close(); return
        }
        val link = Link(conn)
        pending[link.id] = link
        schedule(HandshakeKey(link.id), timing.handshakeTimeoutMs)
    }

    private fun guestOf(link: Link): Guest? = guests.values.firstOrNull { it.link === link }

    override fun onLinkClosed(link: Link, cause: Throwable?) {
        if (pending.remove(link.id) != null) {
            cancelTimer(HandshakeKey(link.id)); return
        }
        val g = guestOf(link) ?: return
        g.link = null
        onGuestGone(g, voluntary = false)
    }

    override fun onLinkFatal(link: Link, failure: RoomFailure, detail: String?) {
        // Masalah satu Tamu (pesan rusak, versi lain) tidak membatalkan room; link sudah ditutup.
    }

    private fun connectedGuests(): List<Guest> = guests.values.filter { it.link != null }

    private fun onGuestGone(g: Guest, voluntary: Boolean) {
        val inMatch = s.inMatch && g.sessionId in participantIds
        if (!inMatch) {
            guests.remove(g.sessionId)
            notice(RoomNotice.PlayerLeft(g.nickname))
            publishRoom()
            return
        }
        // Di tengah pertandingan: tetap peserta (0 poin selama absen), boleh menyambung ulang.
        if (s.phase == RoomPhase.PREPARING || s.phase == RoomPhase.QUESTION) absentThisRound += g.sessionId
        awaitingBankCheck -= g.sessionId
        awaitingPrepare -= g.sessionId
        revealAudience -= g.sessionId
        if (voluntary) {
            g.left = true
            notice(RoomNotice.PlayerLeft(g.nickname))
        } else {
            schedule(ReconnectKey(g.sessionId), timing.reconnectWindowMs)
            notice(RoomNotice.PlayerDisconnected(g.nickname))
        }
        publishRoom()
        if (!checkEnoughPlayers()) return
        progressAfterChange()
    }

    /** Batalkan pertandingan (tanpa pemenang) bila tidak ada Tamu yang tersisa atau bisa kembali. */
    private fun checkEnoughPlayers(): Boolean {
        if (!s.inMatch) return true
        val anyone = participantIds.any { id ->
            val g = guests[id] ?: return@any false
            g.link != null || (!g.left)
        }
        if (!anyone) {
            closeMatch(EndReason.CANCELLED_DISCONNECT, RoomFailure.NOT_ENOUGH_PLAYERS)
            return false
        }
        return true
    }

    /** Lanjutkan fase yang sedang menunggu pemain yang baru saja hilang. */
    private fun progressAfterChange() {
        when (s.phase) {
            RoomPhase.STARTING -> if (awaitingBankCheck.isEmpty()) beginRounds()
            RoomPhase.PREPARING -> if (awaitingPrepare.isEmpty()) startQuestion()
            RoomPhase.QUESTION -> maybeCloseRound()
            RoomPhase.REVEAL -> maybeAdvance()
            else -> Unit
        }
    }

    // ============================================================ Pesan masuk

    override fun onPayload(link: Link, env: Envelope) {
        val p = env.payload
        if (pending.containsKey(link.id)) {
            if (p is Hello) onHello(link, p) else ignoredLateMessages++
            return
        }
        val g = guestOf(link) ?: run { ignoredLateMessages++; return }
        when (p) {
            is Disconnect -> {
                link.close()
                g.link = null
                onGuestGone(g, voluntary = true)
            }
            is ProtocolError -> if (p.fatal) {
                link.close()
            }
            is PlayerNameUpdate -> onGuestName(g, p.nickname)
            is Ready -> if (s.phase == RoomPhase.LOBBY && g.ready != p.ready) {
                g.ready = p.ready
                publishRoom()
            }
            is BankCheck -> if (s.phase == RoomPhase.STARTING && env.matchId == s.matchId && g.sessionId in awaitingBankCheck) {
                awaitingBankCheck -= g.sessionId
                if (!p.ok || p.bankSha256 != bank.bankSha256) {
                    link.send(ProtocolError(ErrorCode.BANK_MISMATCH, p.reason ?: "Bank soal berbeda", fatal = true), s.matchId)
                    link.close()
                    g.link = null
                    g.left = true
                    publishRoom()
                    if (!checkEnoughPlayers()) return
                }
                if (awaitingBankCheck.isEmpty()) beginRounds()
            } else ignoredLateMessages++
            is QuestionReady -> if (s.phase == RoomPhase.PREPARING && env.matchId == s.matchId && env.roundIndex == currentIndex &&
                p.questionId == rounds[currentIndex].question.id
            ) {
                awaitingPrepare -= g.sessionId
                if (awaitingPrepare.isEmpty()) startQuestion()
            } else ignoredLateMessages++
            is Answer -> onGuestAnswer(g, env, p)
            is NextRound -> if (s.phase == RoomPhase.REVEAL && env.matchId == s.matchId && p.nextRoundIndex == currentIndex + 1) {
                nextReady += g.sessionId
                maybeAdvance()
            } else ignoredLateMessages++
            else -> ignoredLateMessages++
        }
    }

    private fun ack(accepted: Boolean, reason: RejectReason? = null, resume: ResumeSnapshot? = null) = HelloAck(
        accepted = accepted, reason = reason, appVersion = appVersion, bankVersionName = bank.versionName,
        bankSha256 = bank.bankSha256, hostSessionId = s.me.sessionId, resume = resume,
    )

    private fun reject(link: Link, reason: RejectReason) {
        pending.remove(link.id)
        cancelTimer(HandshakeKey(link.id))
        link.send(ack(false, reason))
        link.close()
    }

    private fun onHello(link: Link, p: Hello) {
        val sid = p.playerSessionId
        when {
            p.bankSha256 != bank.bankSha256 -> return reject(link, RejectReason.BANK_MISMATCH)
            sid.length !in 8..64 || sid == s.me.sessionId -> return reject(link, RejectReason.INVALID_PLAYER)
            roomCode != null && p.roomCode != roomCode -> return reject(link, RejectReason.WRONG_ROOM_CODE)
        }
        // ---- menyambung ulang di tengah pertandingan ----
        if (p.resumeMatchId != null) {
            val g = guests[sid]
            if (!s.inMatch || p.resumeMatchId != s.matchId || g == null || g.link != null || g.left) {
                return reject(link, RejectReason.RESUME_REJECTED)
            }
            pending.remove(link.id)
            cancelTimer(HandshakeKey(link.id))
            cancelTimer(ReconnectKey(sid))
            g.link = link
            // Ikut lagi mulai ronde berikutnya; ronde yang sedang berjalan dihitung absen.
            if (s.phase == RoomPhase.PREPARING || s.phase == RoomPhase.QUESTION) absentThisRound += sid
            link.send(ack(true, resume = ResumeSnapshot(s.matchId!!, setup!!, s.results.map { it.toWire() })))
            notice(RoomNotice.PlayerReconnected(g.nickname))
            publishRoom()
            return
        }
        when {
            s.phase != RoomPhase.LOBBY -> return reject(link, RejectReason.MATCH_IN_PROGRESS)
            guests.containsKey(sid) -> return reject(link, RejectReason.INVALID_PLAYER)
            guests.size + 1 >= MAX_ROOM_PLAYERS -> return reject(link, RejectReason.ROOM_FULL)
        }
        pending.remove(link.id)
        cancelTimer(HandshakeKey(link.id))
        val nick = NicknameValidator.sanitizeRemote(p.nickname) ?: "$FALLBACK_NAME ${guests.size + 2}"
        guests[sid] = Guest(sid, nick, link)
        link.send(ack(true))
        notice(RoomNotice.PlayerJoined(nick))
        publishRoom()
    }

    private fun onGuestName(g: Guest, raw: String) {
        if (s.phase != RoomPhase.LOBBY || g.ready) { ignoredLateMessages++; return }
        val clean = NicknameValidator.sanitizeRemote(raw) ?: return
        if (clean != g.nickname) {
            val old = g.nickname
            g.nickname = clean
            notice(RoomNotice.PlayerRenamed(old, clean))
            publishRoom()
        }
    }

    /** Siarkan keadaan room ke semua Tamu dan perbarui state lokal. */
    private fun publishRoom() {
        val me = s.me
        val players = listOf(me.copy(ready = true, connected = true)) + guests.values.filter { !it.left || s.inMatch }.map {
            RoomPlayer(it.sessionId, it.nickname, isHost = false, ready = it.ready, connected = it.link != null)
        }
        _state.update { it.copy(players = players, me = players.first()) }
        val phase = when {
            s.inMatch -> WireRoomPhase.IN_MATCH
            s.phase == RoomPhase.LOBBY -> WireRoomPhase.LOBBY
            else -> WireRoomPhase.RESULTS
        }
        val msg = RoomStateMessage(players.map { it.toWire() }, s.config, phase)
        connectedGuests().forEach { it.link!!.send(msg) }
    }

    // ============================================================ Pertandingan

    private fun participantsConnected(): List<String> =
        participantIds.filter { id -> id != s.me.sessionId && guests[id]?.link != null && id !in absentThisRound }

    private fun sendToParticipants(payload: Payload, roundIndex: Int? = null) {
        participantIds.forEach { id -> guests[id]?.link?.send(payload, s.matchId, roundIndex) }
    }

    private fun start() {
        val st = s
        if (!st.canStart) return
        val pool = bank.pool(st.config)
        if (pool.size < st.config.questionCount) return
        rounds = QuestionSelector.select(pool, st.config.questionCount, seedProvider())
        val matchId = Ids.random()
        val participants = st.players.filter { it.connected }.map { it.copy(ready = false) }
        participantIds = participants.map { it.sessionId }
        currentIndex = -1
        matchClosedNotified = false
        answers.clear(); absentThisRound.clear(); nextReady.clear(); revealAudience.clear()
        val ms = MatchSetup(
            config = st.config,
            questionIds = rounds.map { it.question.id },
            optionOrders = rounds.map { r -> r.optionOrder.map { it.name } },
            participants = participants.map { it.toWire() },
            bankSha256 = bank.bankSha256,
            bankVersionName = bank.versionName,
        )
        setup = ms
        _state.update {
            it.copy(
                phase = RoomPhase.STARTING, matchId = matchId, participants = participants, totalRounds = rounds.size,
                results = emptyList(), activeRound = null, endReason = null, failure = null, revealUntilMs = null, meNextReady = false,
            )
        }
        awaitingBankCheck.clear()
        awaitingBankCheck += participantsConnected()
        sendToParticipants(ms)
        publishRoom()
        schedule(T.BANK_CHECK, timing.bankCheckTimeoutMs)
    }

    private fun beginRounds() {
        cancelTimer(T.BANK_CHECK)
        startRound(0)
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) { endMatch(); return }
        currentIndex = index
        answers.clear()
        absentThisRound.clear()
        nextReady.clear()
        _state.update { it.copy(phase = RoomPhase.PREPARING, activeRound = null, meNextReady = false, revealUntilMs = null) }
        awaitingPrepare.clear()
        awaitingPrepare += participantsConnected()
        sendToParticipants(QuestionPrepare(rounds[index].question.id), index)
        if (awaitingPrepare.isEmpty()) startQuestion() else schedule(T.PREPARE, timing.prepareTimeoutMs)
    }

    private fun startQuestion() {
        if (s.phase != RoomPhase.PREPARING) return
        cancelTimer(T.PREPARE)
        // Tamu yang belum siap tetap menerima soal; bila terlambat, waktu yang diamati Host tetap berlaku.
        val rq = rounds[currentIndex]
        val duration = rq.question.timeLimitMs
        sendToParticipants(QuestionStart(rq.question.id, rq.optionOrder.map { it.name }, duration), currentIndex)
        roundStartedAt = clock.nowMs()
        _state.update { it.copy(phase = RoomPhase.QUESTION, activeRound = ActiveRound(currentIndex, rq, roundStartedAt, duration)) }
        schedule(T.LOCAL_TIMEOUT, duration)
        schedule(T.ROUND_DEADLINE, duration + timing.answerGraceMs)
    }

    private fun onGuestAnswer(g: Guest, env: Envelope, p: Answer) {
        val ar = s.activeRound
        if (s.phase != RoomPhase.QUESTION || ar == null || env.matchId != s.matchId || env.roundIndex != currentIndex ||
            p.questionId != ar.round.question.id || g.sessionId in answers || g.sessionId in absentThisRound ||
            g.sessionId !in participantIds
        ) {
            ignoredLateMessages++; return
        }
        val q = ar.round.question
        val option = OptionId.parse(p.optionId)
        val observed = clock.nowMs() - roundStartedAt
        val rtt = s.rttMs ?: DEFAULT_RTT_MS
        // Klaim waktu Tamu tidak boleh jauh lebih cepat dari yang mungkin secara fisik.
        val minPlausible = (observed - rtt - timing.elapsedToleranceMs).coerceIn(0, q.timeLimitMs)
        val elapsed = p.elapsedMs.coerceIn(0, q.timeLimitMs).coerceAtLeast(minPlausible)
        answers[g.sessionId] = if (option == null || elapsed >= q.timeLimitMs) PlayerRoundResult.timeout(q.timeLimitMs)
        else scored(q, option, elapsed)
        markLocked(g.sessionId)
        maybeCloseRound()
    }

    private fun markLocked(sessionId: String) {
        val ar = s.activeRound ?: return
        _state.update { it.copy(activeRound = ar.copy(lockedPlayers = ar.lockedPlayers + sessionId)) }
        sendToParticipants(AnswerLocked(sessionId), currentIndex)
    }

    private fun submitAnswer(option: OptionId) {
        val ar = s.activeRound ?: return
        if (s.phase != RoomPhase.QUESTION || ar.isLocked) return // terkunci: tidak boleh submit ganda
        val elapsed = (clock.nowMs() - ar.startedAtMs).coerceAtLeast(0)
        if (elapsed >= ar.durationMs) return // LOCAL_TIMEOUT akan mencatat timeout
        _state.update { it.copy(activeRound = ar.copy(myAnswer = option, myElapsedMs = elapsed)) }
        cancelTimer(T.LOCAL_TIMEOUT)
        markLocked(s.me.sessionId)
        maybeCloseRound()
    }

    private fun maybeCloseRound() {
        val ar = s.activeRound ?: return
        if (s.phase != RoomPhase.QUESTION) return
        if (ar.isLocked && participantsConnected().all { it in answers }) closeRound()
    }

    private fun closeRound() {
        val ar = s.activeRound ?: return
        if (s.phase != RoomPhase.QUESTION) return
        cancelTimer(T.LOCAL_TIMEOUT)
        cancelTimer(T.ROUND_DEADLINE)
        val q = ar.round.question
        val players = participantIds.associateWith { id ->
            when {
                id == s.me.sessionId -> localResult(ar)
                id in answers -> answers.getValue(id)
                id in absentThisRound || guests[id]?.link == null -> PlayerRoundResult.ABSENT
                else -> PlayerRoundResult.timeout(q.timeLimitMs)
            }
        }
        val summary = RoundSummary(currentIndex, ar.round, q.correctOptionId, players)
        val results = s.results + summary
        val totals = participantIds.associateWith { id -> results.sumOf { it.of(id)?.points ?: 0 } }
        val msg = RoundResultMessage(summary.toWire(), totals)
        revealAudience.clear()
        // Tamu yang absen di ronde ini (mis. baru tersambung ulang) tidak melihat layar hasil ronde -> tidak ditunggu.
        revealAudience += participantIds.filter { id -> id != s.me.sessionId && guests[id]?.link != null && id !in absentThisRound }
        participantIds.forEach { id -> guests[id]?.link?.send(msg, s.matchId, currentIndex) }
        _state.update {
            it.copy(
                phase = RoomPhase.REVEAL, results = results, revealUntilMs = clock.nowMs() + timing.revealMs,
                meNextReady = false, activeRound = ar.copy(lockedPlayers = ar.lockedPlayers + players.keys),
            )
        }
        schedule(T.REVEAL, timing.revealMs)
    }

    private fun maybeAdvance() {
        if (s.phase != RoomPhase.REVEAL) return
        if (s.meNextReady && revealAudience.all { it in nextReady }) advance()
    }

    private fun advance() {
        if (s.phase != RoomPhase.REVEAL) return
        cancelTimer(T.REVEAL)
        startRound(currentIndex + 1)
    }

    private fun endMatch() {
        val totals = participantIds.associateWith { id -> s.results.sumOf { it.of(id)?.points ?: 0 } }
        sendToParticipants(MatchEnd(totals, EndReason.COMPLETED, s.results.size))
        closeMatch(EndReason.COMPLETED, null)
    }

    private fun closeMatch(reason: EndReason, failure: RoomFailure?) {
        T.entries.forEach(::cancelTimer)
        cancelTimers { it is ReconnectKey }
        if (reason != EndReason.COMPLETED) {
            sendToParticipants(MatchEnd(participantIds.associateWith { s.totalOf(it) }, reason, s.results.size))
        }
        val summary = s.matchId?.let { MatchSummary(it, s.config, s.participants, s.results, s.totalRounds, reason, failure) }
        _state.update {
            it.copy(
                phase = if (reason == EndReason.COMPLETED) RoomPhase.FINISHED else RoomPhase.CANCELLED,
                endReason = reason, failure = failure, revealUntilMs = null, lastMatch = summary ?: it.lastMatch,
            )
        }
        publishRoom()
        if (!matchClosedNotified && s.matchId != null) {
            matchClosedNotified = true
            runCatching { onMatchClosed(s) }
        }
    }

    /** Buka room lagi: Tamu yang masih tersambung kembali ke lobi (SIAP direset). */
    private fun reopenRoom() {
        if (s.phase !in setOf(RoomPhase.FINISHED, RoomPhase.CANCELLED) || closedByHost) return
        guests.values.removeAll { it.link == null || it.left }
        guests.values.forEach { it.ready = false }
        participantIds = emptyList()
        rounds = emptyList()
        setup = null
        _state.update {
            it.copy(
                phase = RoomPhase.LOBBY, matchId = null, participants = emptyList(), totalRounds = 0, activeRound = null,
                results = emptyList(), endReason = null, failure = null, revealUntilMs = null, meNextReady = false,
            )
        }
        connectedGuests().forEach { it.link!!.send(BackToRoom()) }
        publishRoom()
    }

    // ============================================================ Aksi pengguna

    override fun onUser(action: Any) {
        when (val a = action as Action) {
            is Action.UpdateConfig -> if (s.phase == RoomPhase.LOBBY && a.config != s.config) {
                guests.values.forEach { it.ready = false }
                // Satu pembaruan atomik: UI tidak boleh sempat melihat mapel baru dengan status SIAP lama
                // (tombol Mulai tampak aktif lalu ditolak).
                _state.update { st -> st.copy(config = a.config, players = st.players.map { p -> if (p.isHost) p else p.copy(ready = false) }) }
                publishRoom()
            }
            is Action.ChangeName -> {
                val clean = NicknameValidator.sanitizeRemote(a.name) ?: return
                if (s.phase == RoomPhase.LOBBY && clean != s.me.nickname) {
                    _state.update { it.copy(me = it.me.copy(nickname = clean)) }
                    publishRoom()
                }
            }
            is Action.Kick -> if (!s.inMatch) {
                val g = guests[a.sessionId] ?: return
                g.link?.send(Disconnect(DisconnectReason.KICKED))
                g.link?.close()
                guests.remove(a.sessionId)
                notice(RoomNotice.PlayerLeft(g.nickname))
                publishRoom()
            }
            Action.Start -> start()
            is Action.SubmitAnswer -> submitAnswer(a.option)
            Action.Next -> if (s.phase == RoomPhase.REVEAL && !s.meNextReady) {
                _state.update { it.copy(meNextReady = true) }
                maybeAdvance()
            }
            Action.BackToRoom -> reopenRoom()
            Action.Leave -> {
                closedByHost = true
                if (s.inMatch) closeMatch(EndReason.CANCELLED_BY_PLAYER, null)
                (pending.values + guests.values.mapNotNull { it.link }).forEach {
                    it.send(Disconnect(DisconnectReason.HOST_CLOSED))
                    it.close()
                }
                pending.clear()
                guests.clear()
                T.entries.forEach(::cancelTimer)
                if (s.phase != RoomPhase.FINISHED) _state.update { it.copy(phase = RoomPhase.CANCELLED) }
            }
        }
    }

    // ============================================================ Timer

    override fun onTimer(key: Any) {
        when (key) {
            is PingKey -> handlePing(key)
            is HandshakeKey -> pending.remove(key.linkId)?.close()
            is ReconnectKey -> {
                val g = guests[key.sessionId] ?: return
                if (g.link == null && s.inMatch) {
                    g.left = true
                    notice(RoomNotice.PlayerLeft(g.nickname))
                    publishRoom()
                    if (checkEnoughPlayers()) progressAfterChange()
                }
            }
            T.BANK_CHECK -> if (s.phase == RoomPhase.STARTING) {
                // Tamu yang tidak merespons dikeluarkan dari pertandingan.
                awaitingBankCheck.toList().forEach { id ->
                    guests[id]?.let { g -> g.link?.close(); g.link = null; g.left = true }
                }
                awaitingBankCheck.clear()
                publishRoom()
                if (checkEnoughPlayers()) beginRounds()
            }
            T.PREPARE -> startQuestion()
            T.LOCAL_TIMEOUT -> {
                val ar = s.activeRound
                if (s.phase == RoomPhase.QUESTION && ar != null && !ar.isLocked) {
                    _state.update { it.copy(activeRound = ar.copy(myTimedOut = true)) }
                    markLocked(s.me.sessionId)
                    maybeCloseRound()
                }
            }
            T.ROUND_DEADLINE -> if (s.phase == RoomPhase.QUESTION) {
                val ar = s.activeRound
                if (ar != null && !ar.isLocked) _state.update { it.copy(activeRound = ar.copy(myTimedOut = true)) }
                closeRound()
            }
            T.REVEAL -> advance()
        }
    }

    override fun onTransportFatal(failure: RoomFailure) {
        // Transport Host mati (mis. Bluetooth dimatikan): semua koneksi hilang.
        closedByHost = true
        if (s.inMatch) closeMatch(EndReason.CANCELLED_ERROR, failure)
        (pending.values + guests.values.mapNotNull { it.link }).forEach { it.closeNow() }
        pending.clear()
        guests.clear()
        _state.update { it.copy(phase = RoomPhase.FAILED, failure = failure) }
    }

    override fun onInternalError(t: Throwable) {
        if (s.inMatch) closeMatch(EndReason.CANCELLED_ERROR, RoomFailure.PROTOCOL_ERROR)
        _state.update { it.copy(failureDetail = t.message) }
    }
}

internal fun RoundSummary.toWire(): RoundResult = RoundResult(
    roundIndex = index,
    questionId = round.question.id,
    correctOptionId = correctOptionId.name,
    players = players.mapValues { it.value.toWire() },
)
