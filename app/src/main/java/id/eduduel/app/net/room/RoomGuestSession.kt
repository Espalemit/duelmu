package id.eduduel.app.net.room

import id.eduduel.app.core.Ids
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.core.NicknameValidator
import id.eduduel.app.domain.ChoiceAnswer
import id.eduduel.app.domain.GameAnswer
import id.eduduel.app.domain.Grader
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.RoundAnswer
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.net.protocol.Answer
import id.eduduel.app.net.protocol.AnswerLocked
import id.eduduel.app.net.protocol.BackToRoom
import id.eduduel.app.net.protocol.BankCheck
import id.eduduel.app.net.protocol.Disconnect
import id.eduduel.app.net.protocol.DisconnectReason
import id.eduduel.app.net.protocol.EndReason
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.ErrorCode
import id.eduduel.app.net.protocol.GameActionEvent
import id.eduduel.app.net.protocol.GameStateUpdate
import id.eduduel.app.net.protocol.GameSubmit
import id.eduduel.app.net.protocol.Hello
import id.eduduel.app.net.protocol.HelloAck
import id.eduduel.app.net.protocol.MatchEnd
import id.eduduel.app.net.protocol.MatchSetup
import id.eduduel.app.net.protocol.NextRound
import id.eduduel.app.net.protocol.PlayerNameUpdate
import id.eduduel.app.net.protocol.PlayerRound
import id.eduduel.app.net.protocol.ProtocolError
import id.eduduel.app.net.protocol.QuestionPrepare
import id.eduduel.app.net.protocol.QuestionReady
import id.eduduel.app.net.protocol.QuestionStart
import id.eduduel.app.net.protocol.Ready
import id.eduduel.app.net.protocol.RejectReason
import id.eduduel.app.net.protocol.RoomStateMessage
import id.eduduel.app.net.protocol.RoundResult
import id.eduduel.app.net.protocol.RoundResultMessage
import id.eduduel.app.net.protocol.WireRoomPhase
import id.eduduel.app.net.transport.DuelConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tamu room: satu koneksi ke Host. Tidak menghitung apa pun secara otoritatif, tetapi
 * memverifikasi bank soal, setup pertandingan, dan konsistensi skor setiap ronde.
 */
class RoomGuestSession(
    myNickname: String,
    bank: QuestionBank,
    clock: MonotonicClock,
    parentScope: CoroutineScope,
    appVersion: String,
    transport: TransportKind,
    private val roomCode: String? = null,
    timing: RoomTiming = RoomTiming(),
    /** Reconnect best-effort ke Host yang sama. null = tanpa reconnect. */
    private val reconnector: (suspend () -> DuelConnection?)? = null,
    private val onMatchClosed: (RoomState) -> Unit = {},
) : RoomSessionCore(
    RoomState(
        role = RoomRole.GUEST,
        transport = transport,
        me = RoomPlayer(Ids.random(), myNickname, isHost = false),
    ),
    bank, clock, parentScope, appVersion, timing,
) {
    private var link: Link? = null
    private var hostSessionId: String? = null
    private var rounds: List<RoundQuestion> = emptyList()
    private var currentIndex = -1
    private var reconnectJob: Job? = null
    private var matchClosedNotified = false
    /** QUESTION_READY yang ditahan sampai tutorial game selesai/dilewati. */
    private var pendingReady: Pair<Int, String>? = null

    // ============================================================ API publik

    fun changeNickname(name: String) = user(Action.ChangeName(name))
    fun setReady(ready: Boolean) = user(Action.SetReady(ready))
    fun answer(option: OptionId) = user(Action.SubmitAnswer(option))
    /** Kunci jawaban soal game (masukan pemain). null = gagal/menyerah sebelum waktu habis. */
    fun submitGame(submission: GameSubmission?) = user(Action.SubmitGame(submission))
    /** Tutorial game selesai/dilewati: kirim QUESTION_READY. */
    fun confirmReady() = user(Action.ConfirmReady)
    fun requestNext() = user(Action.Next)
    fun leave() = user(Action.Leave)

    override fun dispose() {
        reconnectJob?.cancel()
        super.dispose()
    }

    private sealed interface Action {
        data class ChangeName(val name: String) : Action
        data class SetReady(val ready: Boolean) : Action
        data class SubmitAnswer(val option: OptionId) : Action
        data class SubmitGame(val submission: GameSubmission?) : Action
        data object ConfirmReady : Action
        data object Next : Action
        data object Leave : Action
    }

    private enum class T { HANDSHAKE, TUTORIAL, LOCAL_TIMEOUT, RECONNECT_DEADLINE }

    // ============================================================ Koneksi

    override fun onNewConnection(conn: DuelConnection) {
        if (s.phase in setOf(RoomPhase.FAILED, RoomPhase.CANCELLED)) {
            conn.close(); return
        }
        link?.takeIf { it.conn !== conn }?.closeNow()
        val l = Link(conn)
        link = l
        _state.update { it.copy(connected = true, hostLabel = conn.peerLabel) }
        val resume = s.phase == RoomPhase.RECONNECTING
        if (!resume) _state.update { it.copy(phase = RoomPhase.HANDSHAKE) }
        l.send(
            Hello(
                appVersion = appVersion,
                bankVersionName = bank.versionName,
                bankSha256 = bank.bankSha256,
                nickname = s.me.nickname,
                playerSessionId = s.me.sessionId,
                roomCode = roomCode,
                resumeMatchId = if (resume) s.matchId else null,
            ),
        )
        if (!resume) schedule(T.HANDSHAKE, timing.guestHandshakeTimeoutMs)
    }

    override fun onLinkClosed(link: Link, cause: Throwable?) {
        if (link !== this.link) return
        this.link = null
        _state.update { it.copy(connected = false) }
        when {
            s.phase in setOf(RoomPhase.FAILED, RoomPhase.CANCELLED) -> Unit
            s.phase == RoomPhase.RECONNECTING -> startReconnectAttempts()
            s.inMatch && reconnector != null -> enterReconnecting()
            s.inMatch -> closeMatch(EndReason.CANCELLED_DISCONNECT, RoomFailure.CONNECTION_LOST)
            s.phase == RoomPhase.FINISHED -> Unit
            else -> fail(RoomFailure.CONNECTION_LOST, cause?.message)
        }
    }

    override fun onLinkFatal(link: Link, failure: RoomFailure, detail: String?) {
        if (s.inMatch) closeMatch(EndReason.CANCELLED_ERROR, failure) else fail(failure, detail)
    }

    private fun enterReconnecting() {
        cancelTimer(T.LOCAL_TIMEOUT)
        cancelTimer(T.TUTORIAL)
        pendingReady = null
        endGhostRound()
        _state.update {
            it.copy(phase = RoomPhase.RECONNECTING, reconnectDeadlineMs = clock.nowMs() + timing.reconnectWindowMs, activeRound = null, prepare = null)
        }
        schedule(T.RECONNECT_DEADLINE, timing.reconnectWindowMs)
        startReconnectAttempts()
    }

    private fun startReconnectAttempts() {
        val rc = reconnector ?: return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (isActive) {
                val conn = runCatching { rc() }.getOrNull()
                if (conn != null) {
                    attach(conn); return@launch
                }
                delay(timing.reconnectRetryMs)
            }
        }
    }

    // ============================================================ Pesan masuk

    override fun onPayload(link: Link, env: Envelope) {
        if (link !== this.link) return
        when (val p = env.payload) {
            is HelloAck -> onHelloAck(p)
            is RoomStateMessage -> onRoomState(p)
            is BackToRoom -> if (s.phase in setOf(RoomPhase.FINISHED, RoomPhase.CANCELLED) && s.connected) {
                rounds = emptyList()
                currentIndex = -1
                _state.update {
                    it.copy(
                        phase = RoomPhase.LOBBY, matchId = null, participants = emptyList(), totalRounds = 0, activeRound = null,
                        results = emptyList(), endReason = null, failure = null, revealUntilMs = null, meNextReady = false, prepare = null,
                    )
                }
            }
            is MatchSetup -> onMatchSetup(link, env, p)
            is QuestionPrepare -> {
                val idx = env.roundIndex ?: -1
                if (env.matchId == s.matchId && s.phase in setOf(RoomPhase.STARTING, RoomPhase.REVEAL, RoomPhase.PREPARING) &&
                    idx in rounds.indices && rounds[idx].question.id == p.questionId && s.results.none { it.index >= idx }
                ) {
                    currentIndex = idx
                    val rq = rounds[idx]
                    // Tutorial hanya untuk soal game; dibatasi agar Host yang aneh tidak bisa menahan Tamu.
                    val tutorialMs = if (rq.question.isGame) p.tutorialMs.coerceIn(0, MAX_TUTORIAL_MS) else 0L
                    val firstOfType = rq.question.isGame && rounds.take(idx).none { it.question.game?.gameType == rq.question.game?.gameType }
                    val prepare = if (tutorialMs > 0) PrepareInfo(idx, rq, clock.nowMs() + tutorialMs, meReady = false, firstOfType = firstOfType) else null
                    _state.update { it.copy(phase = RoomPhase.PREPARING, activeRound = null, prepare = prepare, meNextReady = false, revealUntilMs = null) }
                    if (tutorialMs > 0) {
                        pendingReady = idx to p.questionId
                        schedule(T.TUTORIAL, tutorialMs)
                    } else {
                        link.send(QuestionReady(p.questionId), s.matchId, idx)
                    }
                } else ignoredLateMessages++
            }
            is QuestionStart -> {
                val idx = env.roundIndex ?: -1
                val rq = rounds.getOrNull(idx)
                if (s.phase == RoomPhase.PREPARING && env.matchId == s.matchId && idx == currentIndex && rq != null &&
                    rq.question.id == p.questionId && p.optionOrder == rq.optionOrder.map { it.name } &&
                    p.durationMs == rq.question.timeLimitMs && p.seed == rq.seed
                ) {
                    cancelTimer(T.TUTORIAL)
                    pendingReady = null
                    _state.update { it.copy(phase = RoomPhase.QUESTION, prepare = null, activeRound = ActiveRound(idx, rq, clock.nowMs(), p.durationMs)) }
                    if (rq.question.isGame) beginGhostRound(idx)
                    schedule(T.LOCAL_TIMEOUT, p.durationMs)
                } else if (s.phase == RoomPhase.PREPARING && env.matchId == s.matchId && idx == currentIndex) {
                    rejectSetup("QUESTION_START tidak cocok dengan MATCH_SETUP")
                } else ignoredLateMessages++
            }
            is AnswerLocked -> {
                val ar = s.activeRound
                if (s.phase == RoomPhase.QUESTION && ar != null && env.roundIndex == ar.index &&
                    s.participants.any { it.sessionId == p.playerSessionId }
                ) {
                    _state.update { it.copy(activeRound = ar.copy(lockedPlayers = ar.lockedPlayers + p.playerSessionId)) }
                } else ignoredLateMessages++
            }
            is RoundResultMessage -> onRoundResult(env, p)
            is GameStateUpdate -> {
                val ar = s.activeRound
                if (s.phase == RoomPhase.QUESTION && ar != null && env.matchId == s.matchId && env.roundIndex == ar.index &&
                    ar.round.question.game?.gameType == p.gameType && s.participants.any { it.sessionId == p.playerSessionId } &&
                    GhostValidator.valid(p.gameType, p.ghost)
                ) {
                    // Pertahanan berlapis: sensor FAIR juga diterapkan di sisi penerima.
                    ghostHub.accept(ar.index, p.copy(ghost = GhostPrivacy.redact(p.ghost, s.config.ghostMode)), clock.nowMs())
                } else ignoredLateMessages++
            }
            is GameActionEvent -> {
                val ar = s.activeRound
                if (s.phase == RoomPhase.QUESTION && ar != null && env.matchId == s.matchId && env.roundIndex == ar.index &&
                    ar.round.question.game?.gameType == p.gameType && s.participants.any { it.sessionId == p.playerSessionId } &&
                    GhostValidator.valid(p.gameType, p.action)
                ) {
                    ghostHub.acceptAction(ar.index, p.copy(action = GhostPrivacy.redact(p.action, s.config.ghostMode)), clock.nowMs())
                } else ignoredLateMessages++
            }
            is MatchEnd -> if (env.matchId == s.matchId && s.inMatch) {
                val local = s.participants.associate { it.sessionId to s.totalOf(it.sessionId) }
                if (p.reason == EndReason.COMPLETED && p.totals != local) {
                    link.send(ProtocolError(ErrorCode.INCONSISTENT_RESULT, "Total akhir tidak cocok", fatal = true), s.matchId)
                    closeMatch(EndReason.CANCELLED_ERROR, RoomFailure.INCONSISTENT_RESULT)
                } else {
                    closeMatch(p.reason, if (p.reason == EndReason.COMPLETED) null else RoomFailure.HOST_LEFT)
                }
            } else ignoredLateMessages++
            is Disconnect -> {
                link.close()
                this.link = null
                _state.update { it.copy(connected = false) }
                val f = if (p.reason == DisconnectReason.KICKED) RoomFailure.KICKED else RoomFailure.HOST_LEFT
                when {
                    s.inMatch -> closeMatch(EndReason.CANCELLED_BY_PLAYER, f)
                    // Pertandingan sudah ditutup (MATCH_END diterima lebih dulu): pertahankan hasilnya.
                    s.phase == RoomPhase.FINISHED || s.phase == RoomPhase.CANCELLED -> Unit
                    else -> fail(f, p.message)
                }
            }
            is ProtocolError -> if (p.fatal) {
                val f = when (p.code) {
                    ErrorCode.BANK_MISMATCH -> RoomFailure.BANK_MISMATCH
                    ErrorCode.PROTOCOL_VERSION -> RoomFailure.PROTOCOL_MISMATCH
                    ErrorCode.INVALID_SETUP -> RoomFailure.INVALID_SETUP
                    ErrorCode.INCONSISTENT_RESULT -> RoomFailure.INCONSISTENT_RESULT
                    else -> RoomFailure.PROTOCOL_ERROR
                }
                if (s.inMatch) closeMatch(EndReason.CANCELLED_ERROR, f) else fail(f, p.message)
                link.close()
            }
            else -> ignoredLateMessages++
        }
    }

    private fun onHelloAck(p: HelloAck) {
        if (s.phase == RoomPhase.RECONNECTING) {
            val snap = p.resume
            if (!p.accepted || snap == null || snap.matchId != s.matchId) {
                closeMatch(EndReason.CANCELLED_DISCONNECT, RoomFailure.RESUME_REJECTED)
                link?.close()
                return
            }
            val results = snap.results.mapNotNull { r -> rounds.getOrNull(r.roundIndex)?.let { r.toSummary(it) } }
            cancelTimer(T.RECONNECT_DEADLINE)
            reconnectJob?.cancel()
            currentIndex = results.maxOfOrNull { it.index } ?: -1
            // Ikut lagi mulai soal berikutnya yang dibuka Host.
            _state.update {
                it.copy(phase = RoomPhase.PREPARING, results = results, reconnectDeadlineMs = null, activeRound = null, revealUntilMs = null)
            }
            notice(RoomNotice.Resumed)
            return
        }
        if (s.phase != RoomPhase.HANDSHAKE) return
        cancelTimer(T.HANDSHAKE)
        if (!p.accepted) {
            val f = when (p.reason) {
                RejectReason.BANK_MISMATCH -> RoomFailure.BANK_MISMATCH
                RejectReason.PROTOCOL_MISMATCH -> RoomFailure.PROTOCOL_MISMATCH
                RejectReason.ROOM_FULL -> RoomFailure.ROOM_FULL
                RejectReason.MATCH_IN_PROGRESS -> RoomFailure.MATCH_IN_PROGRESS
                RejectReason.WRONG_ROOM_CODE -> RoomFailure.WRONG_ROOM_CODE
                RejectReason.RESUME_REJECTED -> RoomFailure.RESUME_REJECTED
                RejectReason.INVALID_PLAYER, null -> RoomFailure.PROTOCOL_ERROR
            }
            abort(f, p.bankVersionName)
            return
        }
        if (p.bankSha256 != bank.bankSha256) {
            link?.send(ProtocolError(ErrorCode.BANK_MISMATCH, "Bank soal berbeda", fatal = true))
            abort(RoomFailure.BANK_MISMATCH, p.bankVersionName)
            return
        }
        hostSessionId = p.hostSessionId
        _state.update { it.copy(phase = RoomPhase.LOBBY) }
    }

    private fun onRoomState(p: RoomStateMessage) {
        if (s.phase == RoomPhase.HANDSHAKE || s.phase == RoomPhase.FAILED) return
        val incoming = p.players.map { w ->
            val clean = NicknameValidator.sanitizeRemote(w.nickname) ?: FALLBACK_NAME
            w.toDomain().copy(nickname = clean)
        }
        val old = s.players
        if (old.isNotEmpty()) {
            incoming.forEach { n ->
                val o = old.firstOrNull { it.sessionId == n.sessionId }
                when {
                    n.sessionId == s.me.sessionId -> Unit
                    o == null -> notice(RoomNotice.PlayerJoined(n.nickname))
                    o.nickname != n.nickname -> notice(RoomNotice.PlayerRenamed(o.nickname, n.nickname))
                    o.connected && !n.connected -> notice(RoomNotice.PlayerDisconnected(n.nickname))
                    !o.connected && n.connected -> notice(RoomNotice.PlayerReconnected(n.nickname))
                    else -> Unit
                }
            }
            old.filter { o -> o.sessionId != s.me.sessionId && incoming.none { it.sessionId == o.sessionId } }
                .forEach { notice(RoomNotice.PlayerLeft(it.nickname)) }
        }
        val configChanged = s.phase == RoomPhase.LOBBY && old.isNotEmpty() && p.config != s.config
        if (configChanged) notice(RoomNotice.ConfigChanged)
        val meWire = incoming.firstOrNull { it.sessionId == s.me.sessionId }
        _state.update {
            it.copy(
                players = incoming,
                me = meWire?.copy(nickname = it.me.nickname) ?: it.me,
                config = if (p.phase == WireRoomPhase.LOBBY) p.config else it.config,
            )
        }
    }

    private fun onMatchSetup(link: Link, env: Envelope, p: MatchSetup) {
        if (s.phase != RoomPhase.LOBBY) { ignoredLateMessages++; return }
        val matchId = env.matchId
        val missing = p.questionIds.filter { bank.byId(it) == null }
        val problem: String? = when {
            matchId.isNullOrBlank() -> "matchId kosong"
            p.bankSha256 != bank.bankSha256 -> "bank soal berbeda (${p.bankVersionName})"
            missing.isNotEmpty() -> "ID soal tidak dikenal"
            p.questionIds.size != p.config.questionCount -> "jumlah soal bukan ${p.config.questionCount}"
            p.questionIds.toSet().size != p.questionIds.size -> "soal duplikat"
            p.optionOrders.size != p.questionIds.size -> "urutan opsi tidak lengkap"
            p.seeds.size != p.questionIds.size -> "seed ronde tidak lengkap"
            p.questionIds.indices.any { i ->
                val q = bank.byId(p.questionIds[i])!!
                val o = p.optionOrders[i]
                if (q.isGame) o.isNotEmpty() else (o.size != 4 || o.mapNotNull(OptionId::parse).toSet() != OptionId.entries.toSet())
            } -> "urutan opsi bukan permutasi A-D (atau soal game memuat opsi)"
            p.questionIds.any { id -> !p.config.matches(bank.byId(id)!!) } -> "soal tidak sesuai mapel"
            p.participants.none { it.sessionId == s.me.sessionId } -> "pemain ini bukan peserta"
            p.participants.size !in 2..id.eduduel.app.net.protocol.MAX_ROOM_PLAYERS -> "jumlah peserta tidak valid"
            else -> null
        }
        if (problem != null) {
            link.send(BankCheck(false, bank.bankSha256, missing, problem), matchId)
            fail(if (p.bankSha256 != bank.bankSha256 || missing.isNotEmpty()) RoomFailure.BANK_MISMATCH else RoomFailure.INVALID_SETUP, problem)
            link.close()
            return
        }
        applySetup(p)
        matchClosedNotified = false
        _state.update {
            it.copy(
                phase = RoomPhase.STARTING, matchId = matchId, results = emptyList(), activeRound = null,
                endReason = null, failure = null, revealUntilMs = null, meNextReady = false,
            )
        }
        link.send(BankCheck(true, bank.bankSha256), matchId)
    }

    private fun applySetup(p: MatchSetup) {
        rounds = p.questionIds.mapIndexed { i, id -> RoundQuestion(bank.byId(id)!!, p.optionOrders[i].map { OptionId.valueOf(it) }, p.seeds[i]) }
        currentIndex = -1
        val participants = p.participants.map { w ->
            w.toDomain().copy(nickname = NicknameValidator.sanitizeRemote(w.nickname) ?: FALLBACK_NAME, ready = false)
        }
        _state.update { it.copy(config = p.config, totalRounds = rounds.size, participants = participants) }
    }

    private fun rejectSetup(reason: String) {
        link?.send(ProtocolError(ErrorCode.INVALID_SETUP, reason, fatal = true), s.matchId)
        closeMatch(EndReason.CANCELLED_ERROR, RoomFailure.INVALID_SETUP)
        link?.close()
    }

    private fun onRoundResult(env: Envelope, p: RoundResultMessage) {
        val r = p.result
        if (env.matchId != s.matchId || !s.inMatch || s.results.any { it.index >= r.roundIndex }) {
            ignoredLateMessages++; return
        }
        val rq = rounds.getOrNull(r.roundIndex) ?: run { ignoredLateMessages++; return }
        val q = rq.question
        // Validasi konsistensi: kunci, keberhasilan game, & poin harus sama dengan perhitungan lokal
        // dari bank identik (mesin game deterministik -> semua HP menghitung hasil yang sama).
        fun answerOf(pr: PlayerRound): RoundAnswer? = when {
            pr.timedOut || pr.absent -> null
            q.isGame -> pr.submission?.let(::GameAnswer)
            else -> OptionId.parse(pr.optionId)?.let(::ChoiceAnswer)
        }
        fun matches(pr: PlayerRound): Boolean {
            if (q.isGame && pr.optionId != null) return false
            if (!q.isGame && pr.submission != null) return false
            val g = Grader.grade(q, answerOf(pr), pr.elapsedMs)
            return g.score.total == pr.points && g.correct == pr.correct
        }
        val ids = s.participants.map { it.sessionId }.toSet()
        val consistent = r.questionId == q.id && r.correctOptionId == q.correctOptionId?.name && r.players.keys == ids &&
            r.players.values.all(::matches)
        val results = s.results + r.toSummary(rq)
        val localTotals = ids.associateWith { id -> results.sumOf { it.of(id)?.points ?: 0 } }
        // Jawaban saya sendiri harus tercatat sama. Host boleh mencatat timeout bila jawaban tiba setelah
        // batas toleransi latensi (bukan kecurangan), tetapi tidak boleh mencatat pilihan yang berbeda.
        val mine = r.players[s.me.sessionId]
        val ar = s.activeRound
        val myConsistent = ar == null || ar.index != r.roundIndex || mine == null || mine.absent ||
            (if (q.isGame) mine.submission == null || mine.submission == ar.myGame else mine.optionId == null || mine.optionId == ar.myAnswer?.name)
        if (!consistent || !myConsistent || p.totals != localTotals) {
            link?.send(ProtocolError(ErrorCode.INCONSISTENT_RESULT, "Hasil ronde ${r.roundIndex + 1} tidak konsisten", fatal = true), s.matchId)
            closeMatch(EndReason.CANCELLED_ERROR, RoomFailure.INCONSISTENT_RESULT)
            link?.close()
            return
        }
        val active = r.roundIndex == currentIndex && s.phase in setOf(RoomPhase.QUESTION, RoomPhase.PREPARING) && ar?.index == r.roundIndex
        endGhostRound()
        if (active) {
            cancelTimer(T.LOCAL_TIMEOUT)
            _state.update {
                it.copy(
                    phase = RoomPhase.REVEAL, results = results, revealUntilMs = clock.nowMs() + timing.revealMs, meNextReady = false,
                    activeRound = ar?.copy(lockedPlayers = ar.lockedPlayers + r.players.keys),
                )
            }
        } else {
            // Ronde yang terlewat (absen/menyambung ulang): catat saja, tetap menunggu soal berikutnya.
            if (r.roundIndex > currentIndex) currentIndex = r.roundIndex
            _state.update { it.copy(results = results) }
        }
    }

    // ============================================================ Aksi pengguna

    override fun onUser(action: Any) {
        when (val a = action as Action) {
            is Action.ChangeName -> {
                val clean = NicknameValidator.sanitizeRemote(a.name) ?: return
                // Ganti nama hanya sebelum koneksi atau di lobi sebelum SIAP; tidak saat pertandingan.
                val allowed = s.phase == RoomPhase.IDLE || s.phase == RoomPhase.HANDSHAKE || (s.phase == RoomPhase.LOBBY && !s.me.ready)
                if (allowed && clean != s.me.nickname) {
                    _state.update { it.copy(me = it.me.copy(nickname = clean)) }
                    if (s.phase == RoomPhase.LOBBY) link?.send(PlayerNameUpdate(clean))
                }
            }
            is Action.SetReady -> if (s.phase == RoomPhase.LOBBY && s.me.ready != a.ready) {
                // Optimistis; Host mengonfirmasi lewat ROOM_STATE.
                _state.update { st -> st.copy(me = st.me.copy(ready = a.ready), players = st.players.map { if (it.sessionId == st.me.sessionId) it.copy(ready = a.ready) else it }) }
                link?.send(Ready(a.ready))
            }
            is Action.SubmitGame -> {
                val ar = s.activeRound ?: return
                if (s.phase != RoomPhase.QUESTION || ar.isLocked || !ar.round.question.isGame) return
                val elapsed = (clock.nowMs() - ar.startedAtMs).coerceAtLeast(0)
                if (elapsed >= ar.durationMs) return
                _state.update {
                    it.copy(activeRound = ar.copy(myGame = a.submission, myGaveUp = a.submission == null, myElapsedMs = elapsed,
                        lockedPlayers = ar.lockedPlayers + it.me.sessionId))
                }
                cancelTimer(T.LOCAL_TIMEOUT)
                link?.send(GameSubmit(ar.round.question.id, elapsed, a.submission), s.matchId, ar.index)
            }
            Action.ConfirmReady -> sendPendingReady()
            is Action.SubmitAnswer -> {
                val ar = s.activeRound ?: return
                if (s.phase != RoomPhase.QUESTION || ar.isLocked || ar.round.question.isGame) return // terkunci: tidak boleh submit ganda
                val elapsed = (clock.nowMs() - ar.startedAtMs).coerceAtLeast(0)
                if (elapsed >= ar.durationMs) return
                _state.update { it.copy(activeRound = ar.copy(myAnswer = a.option, myElapsedMs = elapsed, lockedPlayers = ar.lockedPlayers + it.me.sessionId)) }
                cancelTimer(T.LOCAL_TIMEOUT)
                link?.send(Answer(ar.round.question.id, a.option.name, elapsed), s.matchId, ar.index)
            }
            Action.Next -> if (s.phase == RoomPhase.REVEAL && !s.meNextReady) {
                _state.update { it.copy(meNextReady = true) }
                link?.send(NextRound(currentIndex + 1), s.matchId, currentIndex)
            }
            Action.Leave -> {
                link?.send(Disconnect(DisconnectReason.LEFT))
                if (s.inMatch) closeMatch(EndReason.CANCELLED_BY_PLAYER, null)
                else if (s.phase != RoomPhase.FINISHED) _state.update { it.copy(phase = RoomPhase.CANCELLED) }
                link?.close()
                link = null
                reconnectJob?.cancel()
                T.entries.forEach(::cancelTimer)
            }
        }
    }

    // ============================================================ Timer

    override fun onTimer(key: Any) {
        when (key) {
            is PingKey -> handlePing(key)
            T.HANDSHAKE -> if (s.phase == RoomPhase.HANDSHAKE) abort(RoomFailure.HANDSHAKE_TIMEOUT, null)
            T.TUTORIAL -> sendPendingReady()
            T.LOCAL_TIMEOUT -> {
                val ar = s.activeRound
                if (s.phase == RoomPhase.QUESTION && ar != null && !ar.isLocked) {
                    _state.update { it.copy(activeRound = ar.copy(myTimedOut = true, lockedPlayers = ar.lockedPlayers + it.me.sessionId)) }
                    if (ar.round.question.isGame) link?.send(GameSubmit(ar.round.question.id, ar.durationMs, null), s.matchId, ar.index)
                    else link?.send(Answer(ar.round.question.id, null, ar.durationMs), s.matchId, ar.index)
                }
            }
            GhostTick -> handleGhostTick()
            T.RECONNECT_DEADLINE -> if (s.phase == RoomPhase.RECONNECTING) {
                reconnectJob?.cancel()
                closeMatch(EndReason.CANCELLED_DISCONNECT, RoomFailure.RECONNECT_FAILED)
            }
        }
    }

    override fun onTransportFatal(failure: RoomFailure) {
        reconnectJob?.cancel()
        if (s.inMatch) closeMatch(EndReason.CANCELLED_ERROR, failure) else fail(failure, null)
        link?.closeNow()
        link = null
    }

    override fun onInternalError(t: Throwable) {
        if (s.inMatch) closeMatch(EndReason.CANCELLED_ERROR, RoomFailure.PROTOCOL_ERROR) else fail(RoomFailure.PROTOCOL_ERROR, t.message)
    }

    private fun sendPendingReady() {
        val (idx, qid) = pendingReady ?: return
        if (s.phase != RoomPhase.PREPARING || idx != currentIndex) return
        pendingReady = null
        cancelTimer(T.TUTORIAL)
        _state.update { it.copy(prepare = it.prepare?.copy(meReady = true)) }
        link?.send(QuestionReady(qid), s.matchId, idx)
    }

    override fun transmitGhost(update: GameStateUpdate, roundIndex: Int) {
        link?.send(update, s.matchId, roundIndex)
    }

    override fun transmitAction(event: GameActionEvent, roundIndex: Int) {
        link?.send(event, s.matchId, roundIndex)
    }

    // ============================================================ Penutupan

    private fun closeMatch(reason: EndReason, failure: RoomFailure?) {
        T.entries.forEach(::cancelTimer)
        endGhostRound()
        ghostHub.clear()
        pendingReady = null
        reconnectJob?.cancel()
        val summary = s.matchId?.let { MatchSummary(it, s.config, s.participants, s.results, s.totalRounds, reason, failure) }
        _state.update {
            it.copy(
                phase = if (reason == EndReason.COMPLETED) RoomPhase.FINISHED else RoomPhase.CANCELLED,
                endReason = reason, failure = failure, reconnectDeadlineMs = null, revealUntilMs = null,
                lastMatch = summary ?: it.lastMatch, me = it.me.copy(ready = false),
            )
        }
        if (!matchClosedNotified && s.matchId != null) {
            matchClosedNotified = true
            runCatching { onMatchClosed(s) }
        }
    }

    companion object {
        /** Batas atas tutorial yang diminta Host (pertahanan terhadap Host yang menahan pemain). */
        const val MAX_TUTORIAL_MS = 15_000L
    }

    private fun fail(failure: RoomFailure, detail: String?) {
        T.entries.forEach(::cancelTimer)
        reconnectJob?.cancel()
        _state.update { it.copy(phase = RoomPhase.FAILED, failure = failure, failureDetail = detail) }
    }

    private fun abort(failure: RoomFailure, detail: String?) {
        fail(failure, detail)
        link?.close()
        link = null
    }
}

internal fun RoundResult.toSummary(rq: RoundQuestion): RoundSummary = RoundSummary(
    index = roundIndex,
    round = rq,
    correctOptionId = OptionId.parse(correctOptionId) ?: rq.question.correctOptionId,
    players = players.mapValues { it.value.toDomain() },
)
