package id.eduduel.app.net.room

import id.eduduel.app.core.Ids
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.ChoiceAnswer
import id.eduduel.app.domain.GameAnswer
import id.eduduel.app.domain.Grader
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.Question
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.RoundAnswer
import id.eduduel.app.domain.game.GameType
import id.eduduel.app.net.protocol.GameActionEvent
import id.eduduel.app.net.protocol.GameStateUpdate
import id.eduduel.app.net.protocol.GhostAction
import id.eduduel.app.net.protocol.GhostState
import id.eduduel.app.net.protocol.Envelope
import id.eduduel.app.net.protocol.ErrorCode
import id.eduduel.app.net.protocol.PROTOCOL_VERSION
import id.eduduel.app.net.protocol.Payload
import id.eduduel.app.net.protocol.Ping
import id.eduduel.app.net.protocol.PlayerRound
import id.eduduel.app.net.protocol.Pong
import id.eduduel.app.net.protocol.ProtocolError
import id.eduduel.app.net.protocol.WirePlayer
import id.eduduel.app.net.transport.ConnectionEvent
import id.eduduel.app.net.transport.DuelConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Inti bersama sesi Host dan Tamu (transport-agnostic).
 *
 * - Semua event (pesan masuk, aksi pengguna, timer) diproses berurutan oleh SATU coroutine,
 *   sehingga tidak ada race condition antar-thread.
 * - Setiap koneksi ([Link]) punya antrean kirim sendiri: urutan seq terjaga dan koneksi baru
 *   ditutup SETELAH pesan yang sudah diantrekan terkirim.
 * - Waktu memakai jam monotonic yang disuntikkan (elapsedRealtime di aplikasi, waktu virtual di tes).
 */
abstract class RoomSessionCore(
    initial: RoomState,
    protected val bank: QuestionBank,
    protected val clock: MonotonicClock,
    parentScope: CoroutineScope,
    protected val appVersion: String,
    protected val timing: RoomTiming,
) {
    protected val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + parentScope.coroutineContext.minusKey(Job))
    private val events = Channel<Event>(Channel.UNLIMITED)

    protected val _state = MutableStateFlow(initial)
    val state: StateFlow<RoomState> = _state.asStateFlow()
    protected val s: RoomState get() = _state.value

    /** Statistik diagnostik (dipakai tes). */
    @Volatile
    var droppedDuplicates = 0
        protected set

    @Volatile
    var ignoredLateMessages = 0
        protected set

    protected sealed interface Event {
        data class NewConnection(val connection: DuelConnection) : Event
        data class Net(val linkId: Int, val event: ConnectionEvent) : Event
        data class User(val action: Any) : Event
        data class Timer(val key: Any, val token: Long) : Event
        data class TransportFatal(val failure: RoomFailure) : Event
        data class LocalGhost(val state: GhostState) : Event
        data class LocalAction(val action: GhostAction) : Event
    }

    init {
        scope.launch {
            for (e in events) {
                try {
                    dispatch(e)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    onInternalError(t)
                }
            }
        }
    }

    // ============================================================ API publik (thread-safe)

    fun attach(connection: DuelConnection) = post(Event.NewConnection(connection))
    fun transportFailed(failure: RoomFailure) = post(Event.TransportFatal(failure))

    /** Hentikan semua coroutine dan tutup koneksi tanpa mengirim pesan. */
    open fun dispose() {
        links.values.toList().forEach { it.closeNow() }
        scope.cancel()
    }

    protected fun post(e: Event) {
        events.trySend(e)
    }

    protected fun user(action: Any) = post(Event.User(action))

    private suspend fun dispatch(e: Event) {
        when (e) {
            is Event.NewConnection -> onNewConnection(e.connection)
            is Event.Net -> {
                val link = links[e.linkId] ?: return
                onLinkEvent(link, e.event)
            }
            is Event.User -> onUser(e.action)
            is Event.Timer -> if (timerTokens[e.key] == e.token) {
                timers.remove(e.key)
                timerTokens.remove(e.key)
                onTimer(e.key)
            }
            is Event.TransportFatal -> onTransportFatal(e.failure)
            is Event.LocalGhost -> if (ghostAllowed()) pendingGhost = e.state
            is Event.LocalAction -> if (ghostAllowed()) sendLocalAction(e.action)
        }
    }

    // ============================================================ Ghost (semua pemain melihat semua pemain lain)

    /** Ghost pemain LAIN (Host maupun Tamu). Ghost sendiri tidak pernah ada di sini. */
    protected val ghostHub = GhostHub { s.me.sessionId }
    val ghosts: StateFlow<Map<String, GhostTrack>> get() = ghostHub.tracks
    val droppedGhosts: Int get() = ghostHub.dropped

    private var pendingGhost: GhostState? = null
    private var lastSentGhost: GhostState? = null
    private var ghostSeq = 0
    private var actionSeq = 0

    /** Pemain lokal memperbarui state visualnya (dipanggil UI setiap frame/gerakan; di-coalesce). */
    fun publishGhost(state: GhostState) = post(Event.LocalGhost(state))

    /** Event aksi lokal (tembak/luncur/tap/tuang) dikirim segera. */
    fun publishAction(action: GhostAction) = post(Event.LocalAction(action))

    private fun ghostAllowed(): Boolean {
        val ar = s.activeRound ?: return false
        return s.phase == RoomPhase.QUESTION && ar.round.question.isGame
    }

    protected fun ghostIntervalMs(): Long =
        if (s.transport == TransportKind.BLUETOOTH) timing.ghostIntervalBtMs else timing.ghostIntervalLanMs

    /** Dipanggil subkelas saat ronde game dimulai. */
    protected fun beginGhostRound(index: Int) {
        ghostHub.reset(index)
        pendingGhost = null
        lastSentGhost = null
        ghostSeq = 0
        actionSeq = 0
        schedule(GhostTick, ghostIntervalMs())
    }

    protected fun endGhostRound() {
        cancelTimer(GhostTick)
        pendingGhost = null
    }

    protected object GhostTick

    /** Dipanggil subkelas dari onTimer. */
    protected fun handleGhostTick() {
        val ar = s.activeRound ?: return
        if (s.phase != RoomPhase.QUESTION || !ar.round.question.isGame) return
        val g = pendingGhost
        if (g != null && !GhostInterpolator.nearlyEqual(g, lastSentGhost)) {
            lastSentGhost = g
            val type = ar.round.question.game!!.gameType
            val redacted = GhostPrivacy.redact(g, s.config.ghostMode)
            transmitGhost(GameStateUpdate(s.me.sessionId, type, ghostSeq++, clock.nowMs() - ar.startedAtMs, redacted), ar.index)
        }
        schedule(GhostTick, ghostIntervalMs())
    }

    private fun sendLocalAction(action: GhostAction) {
        val ar = s.activeRound ?: return
        val type: GameType = ar.round.question.game?.gameType ?: return
        val redacted = GhostPrivacy.redact(action, s.config.ghostMode)
        transmitAction(GameActionEvent(s.me.sessionId, type, actionSeq++, clock.nowMs() - ar.startedAtMs, redacted), ar.index)
    }

    /** Host: kirim ke semua Tamu peserta. Tamu: kirim ke Host (yang me-relay). */
    protected abstract fun transmitGhost(update: GameStateUpdate, roundIndex: Int)
    protected abstract fun transmitAction(event: GameActionEvent, roundIndex: Int)

    protected abstract fun onNewConnection(conn: DuelConnection)
    protected abstract fun onPayload(link: Link, env: Envelope)
    protected abstract fun onLinkClosed(link: Link, cause: Throwable?)
    protected abstract fun onLinkFatal(link: Link, failure: RoomFailure, detail: String?)
    protected abstract fun onUser(action: Any)
    protected abstract fun onTimer(key: Any)
    protected abstract fun onTransportFatal(failure: RoomFailure)
    protected abstract fun onInternalError(t: Throwable)

    // ============================================================ Link

    private var nextLinkId = 0
    protected val links = LinkedHashMap<Int, Link>()

    /** Satu koneksi point-to-point dengan seq, dedup, ping/watchdog, dan antrean kirim sendiri. */
    protected inner class Link(val conn: DuelConnection) {
        val id: Int = ++nextLinkId
        val peerLabel: String get() = conn.peerLabel
        private var sendSeq = 0L
        private var lastRecvSeq = -1L
        var lastInboundAtMs: Long = clock.nowMs()
            private set
        private val seen = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 512
        }
        var malformed = 0
        var open = true
            private set
        private val outbox = Channel<Envelope?>(Channel.UNLIMITED)

        init {
            links[id] = this
            scope.launch {
                for (env in outbox) {
                    if (env == null) {
                        conn.close(); break
                    }
                    runCatching { conn.send(env) }
                }
            }
            scope.launch { conn.events.collect { events.send(Event.Net(id, it)) } }
            schedule(PingKey(id), timing.pingIntervalMs)
        }

        fun send(payload: Payload, matchId: String? = null, roundIndex: Int? = null) {
            if (!open) return
            outbox.trySend(Envelope(PROTOCOL_VERSION, sendSeq++, Ids.random(), matchId, roundIndex, payload))
        }

        /** Tutup setelah pesan yang sudah diantrekan terkirim. */
        fun close() {
            if (!open) return
            open = false
            outbox.trySend(null)
        }

        fun closeNow() {
            open = false
            outbox.close()
            conn.close()
        }

        /** false bila pesan duplikat/out-of-order (diabaikan). */
        fun accept(env: Envelope): Boolean {
            lastInboundAtMs = clock.nowMs()
            if (seen.containsKey(env.msgId) || env.seq <= lastRecvSeq) return false
            seen[env.msgId] = Unit
            lastRecvSeq = env.seq
            return true
        }
    }

    protected data class PingKey(val linkId: Int)

    private fun onLinkEvent(link: Link, ev: ConnectionEvent) {
        when (ev) {
            is ConnectionEvent.Received -> {
                if (!link.accept(ev.envelope)) {
                    droppedDuplicates++; return
                }
                when (val p = ev.envelope.payload) {
                    is Ping -> link.send(Pong(p.sentAtMs))
                    is Pong -> onPong(link, (clock.nowMs() - p.echoSentAtMs).coerceAtLeast(0))
                    else -> onPayload(link, ev.envelope)
                }
            }
            is ConnectionEvent.Malformed -> {
                link.malformed++
                link.send(ProtocolError(ErrorCode.MALFORMED, ev.reason, fatal = false))
                if (link.malformed > MAX_MALFORMED) {
                    link.send(ProtocolError(ErrorCode.MALFORMED, "Terlalu banyak pesan rusak", fatal = true))
                    link.close()
                    onLinkFatal(link, RoomFailure.PROTOCOL_ERROR, ev.reason)
                }
            }
            is ConnectionEvent.VersionMismatch -> {
                link.send(ProtocolError(ErrorCode.PROTOCOL_VERSION, "Versi protokol $PROTOCOL_VERSION dibutuhkan", fatal = true))
                link.close()
                onLinkFatal(link, RoomFailure.PROTOCOL_MISMATCH, "Versi lawan: ${ev.remoteVersion}")
            }
            is ConnectionEvent.Closed -> {
                links.remove(link.id)
                cancelTimer(PingKey(link.id))
                onLinkClosed(link, ev.cause)
            }
        }
    }

    protected open fun onPong(link: Link, rttMs: Long) {
        _state.update { it.copy(rttMs = rttMs) }
    }

    /** Dipanggil subkelas dari onTimer untuk PingKey. */
    protected fun handlePing(key: PingKey) {
        val link = links[key.linkId] ?: return
        if (!link.open) return
        if (clock.nowMs() - link.lastInboundAtMs > timing.watchdogMs) {
            link.closeNow() // memicu Closed -> onLinkClosed
        } else {
            link.send(Ping(clock.nowMs()))
            schedule(key, timing.pingIntervalMs)
        }
    }

    // ============================================================ Timer

    private val timers = HashMap<Any, Job>()
    private val timerTokens = HashMap<Any, Long>()
    private var timerToken = 0L

    protected fun schedule(key: Any, delayMs: Long) {
        cancelTimer(key)
        val token = ++timerToken
        timerTokens[key] = token
        timers[key] = scope.launch {
            delay(delayMs)
            events.send(Event.Timer(key, token))
        }
    }

    protected fun cancelTimer(key: Any) {
        timers.remove(key)?.cancel()
        timerTokens.remove(key)
    }

    protected fun cancelTimers(predicate: (Any) -> Boolean) {
        timers.keys.filter(predicate).forEach(::cancelTimer)
    }

    // ============================================================ Util

    protected fun notice(n: RoomNotice) {
        _state.update { it.copy(notice = n, noticeId = it.noticeId + 1) }
    }

    /** Hasil pemain dari jawaban lokal (Host maupun Tamu memakai rumus yang SAMA). */
    protected fun localResult(ar: ActiveRound): PlayerRoundResult {
        val q = ar.round.question
        val elapsed = ar.myElapsedMs ?: q.timeLimitMs
        return when {
            ar.myAnswer != null -> scored(q, ChoiceAnswer(ar.myAnswer), elapsed)
            ar.myGame != null -> scored(q, GameAnswer(ar.myGame), elapsed)
            ar.myGaveUp -> gaveUp(elapsed)
            else -> PlayerRoundResult.timeout(q.timeLimitMs)
        }
    }

    /** Penilaian tunggal ([Grader]) untuk MCQ dan game. */
    protected fun scored(q: Question, answer: RoundAnswer, elapsedMs: Long): PlayerRoundResult {
        if (elapsedMs >= q.timeLimitMs) return PlayerRoundResult.timeout(q.timeLimitMs)
        val g = Grader.grade(q, answer, elapsedMs)
        return PlayerRoundResult(
            optionId = (answer as? ChoiceAnswer)?.optionId, elapsedMs = elapsedMs, correct = g.correct, points = g.score.total,
            timedOut = false, game = (answer as? GameAnswer)?.submission, detail = g.detail,
        )
    }

    protected fun gaveUp(elapsedMs: Long) =
        PlayerRoundResult(null, elapsedMs, correct = false, points = 0, timedOut = false, detail = "Tidak berhasil / menyerah")

    companion object {
        const val MAX_MALFORMED = 5
        const val DEFAULT_RTT_MS = 300L
        const val FALLBACK_NAME = "Pemain"
    }
}

// ---------------------------------------------------------------- konversi wire <-> domain

internal fun PlayerRoundResult.toWire() = PlayerRound(optionId?.name, elapsedMs, correct, points, timedOut, absent, game, detail)

internal fun PlayerRound.toDomain() = PlayerRoundResult(OptionId.parse(optionId), elapsedMs, correct, points, timedOut, absent, submission, detail)

internal fun RoomPlayer.toWire() = WirePlayer(sessionId, nickname, isHost, ready, connected)

internal fun WirePlayer.toDomain() = RoomPlayer(sessionId, nickname, isHost, ready, connected)
