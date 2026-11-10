package id.eduduel.app.net.room

import id.eduduel.app.core.Ids
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.data.history.HistoryRepository
import id.eduduel.app.data.history.MatchRecordEntity
import id.eduduel.app.data.history.ParticipantRecord
import id.eduduel.app.data.history.RecordMode
import id.eduduel.app.data.history.RecordOutcome
import id.eduduel.app.data.history.RoundRecord
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.MatchOutcome
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.describe
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.AsteroidSubmission
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.domain.game.LabSpec
import id.eduduel.app.domain.game.MicroscopeSpec
import id.eduduel.app.domain.game.TrajectorySpec
import id.eduduel.app.domain.game.TrajectorySubmission
import id.eduduel.app.net.protocol.AsteroidGhost
import id.eduduel.app.net.protocol.FireAction
import id.eduduel.app.net.protocol.GhostState
import id.eduduel.app.net.protocol.LabGhost
import id.eduduel.app.net.protocol.LabStepAction
import id.eduduel.app.net.protocol.LaunchAction
import id.eduduel.app.net.protocol.MicroGhost
import id.eduduel.app.net.protocol.TapAction
import id.eduduel.app.net.protocol.TrajectoryGhost
import id.eduduel.app.net.transport.AdapterState
import id.eduduel.app.net.transport.BluetoothController
import id.eduduel.app.net.transport.BtDevice
import id.eduduel.app.net.transport.DuelConnection
import id.eduduel.app.net.transport.InMemoryConnection
import id.eduduel.app.net.transport.LanController
import id.eduduel.app.net.transport.LocalAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.sin
import kotlin.random.Random

sealed interface ConnectStatus {
    data object Idle : ConnectStatus
    /** Host: room terbuka dan menerima Tamu. */
    data object Listening : ConnectStatus
    data class Connecting(val target: String) : ConnectStatus
    data object Connected : ConnectStatus
    data class Error(val kind: ConnectError, val detail: String?) : ConnectStatus
}

enum class ConnectError { PERMISSION, BLUETOOTH_OFF, CONNECT_FAILED, LISTEN_FAILED }

/** Sesi aktif: Host atau Tamu. UI memakai [state] dan aksi yang sesuai peran. */
sealed interface ActiveRoom {
    val session: RoomSessionCore
    val state: StateFlow<RoomState> get() = session.state

    data class Host(override val session: RoomHostSession) : ActiveRoom
    data class Guest(override val session: RoomGuestSession) : ActiveRoom

    // Aksi pemain yang sama untuk Host maupun Tamu (Host juga pemain).
    fun answer(option: OptionId) = when (this) {
        is Host -> session.answer(option)
        is Guest -> session.answer(option)
    }

    fun submitGame(submission: GameSubmission?) = when (this) {
        is Host -> session.submitGame(submission)
        is Guest -> session.submitGame(submission)
    }

    fun confirmReady() = when (this) {
        is Host -> session.confirmReady()
        is Guest -> session.confirmReady()
    }

    fun requestNext() = when (this) {
        is Host -> session.requestNext()
        is Guest -> session.requestNext()
    }
}

/** Info room Wi-Fi milik Host untuk ditampilkan di lobi. */
data class LanHostInfo(val port: Int, val addresses: List<LocalAddress>)

/**
 * Pengelola room tingkat aplikasi (bertahan saat navigasi & rotasi).
 * Menghubungkan transport (Wi-Fi/hotspot TCP, Bluetooth RFCOMM, atau in-memory untuk demo) dengan
 * sesi room, menangani Bluetooth dimatikan, dan menyimpan riwayat LOKAL.
 */
class RoomCoordinator(
    private val appScope: CoroutineScope,
    private val bluetooth: BluetoothController,
    private val lan: LanController,
    private val history: HistoryRepository,
    private val clock: MonotonicClock,
    private val appVersion: String,
) {
    private val _room = MutableStateFlow<ActiveRoom?>(null)
    val room: StateFlow<ActiveRoom?> = _room.asStateFlow()

    private val _status = MutableStateFlow<ConnectStatus>(ConnectStatus.Idle)
    val status: StateFlow<ConnectStatus> = _status.asStateFlow()

    private val _lanHost = MutableStateFlow<LanHostInfo?>(null)
    val lanHost: StateFlow<LanHostInfo?> = _lanHost.asStateFlow()

    /** Nickname untuk pertandingan ini (diisi di layar Isi Nama, selalu bisa diubah). */
    var nickname: String = ""
        private set

    val isDemo: Boolean get() = _room.value?.state?.value?.transport == TransportKind.DEMO

    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var lanServer: LanController.LanServer? = null
    private var stopAdvertising: () -> Unit = {}
    private val bots = mutableListOf<RoomGuestSession>()
    private val botJobs = mutableListOf<Job>()
    private var lastConfig: MatchConfig = MatchConfig.INITIAL

    init {
        appScope.launch {
            bluetooth.adapterState.collect { st ->
                if (st == AdapterState.ON || st == AdapterState.TURNING_ON) return@collect
                val r = _room.value ?: return@collect
                if (r.state.value.transport != TransportKind.BLUETOOTH) return@collect
                r.session.transportFailed(RoomFailure.BLUETOOTH_OFF)
                acceptJob?.cancel()
                connectJob?.cancel()
                if (_status.value is ConnectStatus.Connecting || _status.value is ConnectStatus.Listening) {
                    _status.value = ConnectStatus.Error(ConnectError.BLUETOOTH_OFF, null)
                }
            }
        }
    }

    fun setNickname(name: String) {
        nickname = name
        when (val r = _room.value) {
            is ActiveRoom.Host -> r.session.changeNickname(name)
            is ActiveRoom.Guest -> r.session.changeNickname(name)
            null -> Unit
        }
    }

    // ------------------------------------------------------------------ Host

    fun hostRoom(bank: QuestionBank, transport: TransportKind, config: MatchConfig = lastConfig) {
        require(transport != TransportKind.DEMO)
        endSession()
        val code = if (transport == TransportKind.LAN) Ids.roomCode() else null
        val host = newHost(bank, transport, code, config)
        _status.value = ConnectStatus.Listening
        acceptJob = appScope.launch {
            try {
                when (transport) {
                    TransportKind.BLUETOOTH -> bluetooth.acceptConnections().collect { host.attach(it) }
                    TransportKind.LAN -> {
                        val server = lan.openServer()
                        lanServer = server
                        _lanHost.value = LanHostInfo(server.port, lan.localAddresses())
                        stopAdvertising = lan.advertise(nickname, server.port, Ids.random().take(6))
                        server.accepts().collect { host.attach(it) }
                    }
                    TransportKind.DEMO -> Unit
                }
            } catch (e: SecurityException) {
                if (isCurrent(host)) _status.value = ConnectStatus.Error(ConnectError.PERMISSION, e.message)
            } catch (e: IOException) {
                if (isCurrent(host)) _status.value = ConnectStatus.Error(ConnectError.LISTEN_FAILED, e.message)
            }
        }
    }

    /** Perbarui daftar IP (mis. setelah Host menyalakan hotspot). */
    fun refreshLanAddresses() {
        _lanHost.value = _lanHost.value?.copy(addresses = lan.localAddresses())
    }

    private fun isCurrent(s: RoomSessionCore) = _room.value?.session === s

    private fun newHost(bank: QuestionBank, transport: TransportKind, code: String?, config: MatchConfig): RoomHostSession {
        val host = RoomHostSession(
            myNickname = nickname, bank = bank, clock = clock, parentScope = appScope, appVersion = appVersion,
            transport = transport, roomCode = code, initialConfig = config,
            onMatchClosed = { st -> if (st.transport != TransportKind.DEMO) appScope.launch { saveHistory(st, bank) } },
        )
        _room.value = ActiveRoom.Host(host)
        watchConfig(host)
        return host
    }

    private var configJob: Job? = null
    private fun watchConfig(s: RoomSessionCore) {
        configJob?.cancel()
        configJob = appScope.launch { s.state.collect { lastConfig = it.config } }
    }

    // ------------------------------------------------------------------ Tamu

    fun joinBluetooth(bank: QuestionBank, device: BtDevice) =
        join(bank, TransportKind.BLUETOOTH, roomCode = null, label = device.name ?: device.address) { bluetooth.connect(device.address) }

    fun joinLan(bank: QuestionBank, host: String, port: Int, roomCode: String, label: String) =
        join(bank, TransportKind.LAN, roomCode, label) { lan.connect(host, port) }

    private fun join(bank: QuestionBank, transport: TransportKind, roomCode: String?, label: String, connect: suspend () -> DuelConnection) {
        endSession()
        val guest = RoomGuestSession(
            myNickname = nickname, bank = bank, clock = clock, parentScope = appScope, appVersion = appVersion,
            transport = transport, roomCode = roomCode,
            reconnector = { runCatching { connect() }.getOrNull() },
            onMatchClosed = { st -> appScope.launch { saveHistory(st, bank) } },
        )
        _room.value = ActiveRoom.Guest(guest)
        watchConfig(guest)
        _status.value = ConnectStatus.Connecting(label)
        connectJob = appScope.launch {
            try {
                val conn = connect()
                _status.value = ConnectStatus.Connected
                guest.attach(conn)
            } catch (e: SecurityException) {
                _status.value = ConnectStatus.Error(ConnectError.PERMISSION, e.message)
                dropIfCurrent(guest)
            } catch (e: IOException) {
                _status.value = ConnectStatus.Error(ConnectError.CONNECT_FAILED, e.message)
                dropIfCurrent(guest)
            }
        }
    }

    private fun dropIfCurrent(s: RoomSessionCore) {
        s.dispose()
        if (isCurrent(s)) _room.value = null
    }

    // ------------------------------------------------------------------ Demo (tanpa jaringan)

    /**
     * Mode demo: room berisi Host (HP ini) + [botCount] bot. Bot memakai sesi Tamu SUNGGUHAN lewat
     * koneksi in-memory, sehingga protokolnya sama persis dengan Wi-Fi/Bluetooth.
     */
    fun startDemo(bank: QuestionBank, botCount: Int = 3, config: MatchConfig = lastConfig) {
        endSession()
        val host = newHost(bank, TransportKind.DEMO, code = null, config = config)
        BOT_NAMES.take(botCount.coerceIn(1, 3)).forEachIndexed { i, name ->
            val (hostSide, botSide) = InMemoryConnection.pair(labelA = "HP ini", labelB = name)
            val bot = RoomGuestSession(name, bank, clock, appScope, appVersion, TransportKind.DEMO)
            bots += bot
            botJobs += appScope.launch { runBot(bot, accuracy = BOT_ACCURACY[i]) }
            host.attach(hostSide)
            bot.attach(botSide)
        }
        _status.value = ConnectStatus.Connected
    }

    private suspend fun runBot(b: RoomGuestSession, accuracy: Double) {
        var answeredRound = -1
        var nextRound = -1
        var tutorialRound = -1
        b.state.collect { st ->
            when (st.phase) {
                RoomPhase.LOBBY -> if (!st.me.ready && st.players.isNotEmpty()) {
                    delay(Random.nextLong(500, 1_500)); b.setReady(true)
                }
                RoomPhase.PREPARING -> {
                    val prep = st.prepare
                    if (prep != null && !prep.meReady && prep.index != tutorialRound) {
                        tutorialRound = prep.index
                        appScope.launch { delay(Random.nextLong(1_000, 2_500)); b.confirmReady() }
                    }
                }
                RoomPhase.QUESTION -> {
                    val ar = st.activeRound
                    if (ar != null && ar.index != answeredRound) {
                        answeredRound = ar.index
                        val q = ar.round.question
                        val game = q.game
                        if (game == null) {
                            appScope.launch {
                                delay(Random.nextLong(2_500, (ar.durationMs / 3).coerceAtLeast(3_000)))
                                val pick: OptionId = if (Random.nextDouble() < accuracy) q.correctOptionId!! else ar.round.optionOrder.random()
                                b.answer(pick)
                            }
                        } else {
                            // Bot demo juga tampil sebagai GHOST di HP ini (dan di HP lain bila relay), lalu mengirim
                            // submisi referensi (berhasil) atau menyerah sesuai akurasinya.
                            val success = Random.nextDouble() < accuracy
                            val finishAt = Random.nextLong(6_000, (ar.durationMs / 2).coerceAtLeast(8_000))
                            appScope.launch {
                                val phase = Random.nextDouble() * 6.0
                                var t = 0L
                                while (isActive && t < finishAt && b.state.value.activeRound?.index == ar.index) {
                                    b.publishGhost(botGhost(game.spec, t / 1000.0 + phase, done = false))
                                    if (t % 1_800L < 150L) botAction(game.spec, t / 1000.0 + phase)?.let(b::publishAction)
                                    delay(150); t += 150
                                }
                                if (b.state.value.activeRound?.index != ar.index) return@launch
                                if (success && game.reference is TrajectorySubmission) {
                                    val shot = (game.reference as TrajectorySubmission).shots.first()
                                    b.publishAction(LaunchAction(shot.a, shot.b, hit = true))
                                }
                                b.publishGhost(botGhost(game.spec, finishAt / 1000.0 + phase, done = true))
                                delay(300)
                                b.submitGame(if (success) game.reference else null)
                            }
                        }
                    }
                }
                RoomPhase.REVEAL -> if (nextRound != st.results.size) {
                    nextRound = st.results.size
                    appScope.launch { delay(Random.nextLong(1_500, 3_000)); b.requestNext() }
                }
                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------------ Umum

    /** Keluar dari room: kirim DISCONNECT (bila tersambung), tutup socket, batalkan coroutine. */
    fun endSession() {
        acceptJob?.cancel(); acceptJob = null
        connectJob?.cancel(); connectJob = null
        configJob?.cancel()
        stopAdvertising(); stopAdvertising = {}
        lanServer?.close(); lanServer = null
        _lanHost.value = null
        _room.value?.let { r ->
            when (r) {
                is ActiveRoom.Host -> r.session.leave()
                is ActiveRoom.Guest -> r.session.leave()
            }
            appScope.launch { delay(400); r.session.dispose() }
        }
        val oldBots = bots.toList()
        bots.clear()
        botJobs.forEach { it.cancel() }
        botJobs.clear()
        appScope.launch { delay(400); oldBots.forEach { it.dispose() } }
        _room.value = null
        _status.value = ConnectStatus.Idle
    }

    fun clearError() {
        if (_status.value is ConnectStatus.Error) _status.value = ConnectStatus.Idle
    }

    private suspend fun saveHistory(st: RoomState, bank: QuestionBank) {
        val summary = st.lastMatch ?: return
        if (summary.results.isEmpty() && !summary.completed) return
        val meId = st.me.sessionId
        val standings = summary.standings
        val mine = standings.firstOrNull { it.player.sessionId == meId } ?: return
        val outcome = when (summary.outcomeFor(meId)) {
            MatchOutcome.WIN -> RecordOutcome.WIN
            MatchOutcome.LOSE -> RecordOutcome.LOSE
            MatchOutcome.DRAW -> RecordOutcome.DRAW
            null -> RecordOutcome.CANCELLED
        }
        val rounds = summary.results.map { r ->
            val me = r.of(meId)
            RoundRecord(
                index = r.index,
                questionId = r.round.question.id,
                correctOptionId = r.correctOptionId?.name,
                myOptionId = me?.optionId?.name,
                myPoints = me?.points ?: 0,
                myElapsedMs = me?.elapsedMs ?: 0,
                voided = me?.absent == true,
                questionType = r.round.question.type.key,
                correct = me?.correct,
                detail = me?.detail,
            )
        }
        val others = standings.filter { it.player.sessionId != meId }
        history.save(
            MatchRecordEntity(
                id = summary.matchId,
                mode = RecordMode.DUEL,
                playedAtEpochMs = System.currentTimeMillis(),
                myNickname = mine.player.nickname,
                opponentNickname = others.joinToString(", ") { it.player.nickname },
                myRole = st.role.name,
                myScore = mine.total,
                opponentScore = others.maxOfOrNull { it.total },
                outcome = outcome,
                correctCount = mine.correct,
                questionCount = summary.totalRounds,
                configSummary = summary.config.describe(),
                roundsJson = HistoryRepository.encodeRounds(rounds),
                bankVersionName = bank.versionName,
                myRank = if (summary.completed) mine.rank else null,
                playerCount = standings.size,
                participantsJson = HistoryRepository.encodeParticipants(
                    standings.map { ParticipantRecord(it.player.nickname, it.total, it.rank, it.player.sessionId == meId) },
                ),
            ),
        )
    }

    companion object {
        val BOT_NAMES = listOf("Bot Andi 🤖", "Bot Sari 🤖", "Bot Joko 🤖")
        private val BOT_ACCURACY = listOf(0.7, 0.55, 0.4)

        /** Animasi ghost sederhana untuk bot demo (koordinat dunia ternormalisasi). */
        internal fun botGhost(spec: id.eduduel.app.domain.game.GameSpec, t: Double, done: Boolean): GhostState = when (spec) {
            is AsteroidSpec -> AsteroidGhost(x = (0.5 + 0.38 * sin(t * 0.9)).toFloat(), firing = (t % 1.8) < 0.15, done = done)
            is TrajectorySpec -> TrajectoryGhost(aim = (0.5 + 0.25 * sin(t * 0.7)).toFloat(), power = (0.6 + 0.2 * sin(t * 0.4)).toFloat(),
                shots = (t / 4).toInt().coerceAtMost(spec.maxShots), done = done)
            is MicroscopeSpec -> {
                val w = (0.5 + 0.2 * sin(t * 0.3)).toFloat()
                MicroGhost(vx = ((0.5 + 0.3 * sin(t * 0.5)) - w / 2).toFloat().coerceIn(0f, 1f - w), vy = ((0.5 + 0.25 * sin(t * 0.37)) - w / 2).toFloat().coerceIn(0f, 1f - w),
                    vw = w, vh = w, focus = (0.5 + 0.3 * sin(t * 0.8)).toFloat(), cx = (0.5 + 0.3 * sin(t * 0.5)).toFloat(), cy = (0.5 + 0.25 * sin(t * 0.37)).toFloat(), done = done)
            }
            is LabSpec -> LabGhost(tx = (0.5 + 0.35 * sin(t * 0.6)).toFloat(), ty = (0.45 + 0.2 * sin(t * 1.1)).toFloat(),
                holding = if (spec.reagents.isNotEmpty()) ((t / 3).toInt() % spec.reagents.size) else null, pouring = (t % 3) > 2.2, done = done)
        }

        internal fun botAction(spec: id.eduduel.app.domain.game.GameSpec, t: Double) = when (spec) {
            is AsteroidSpec -> FireAction((0.5 + 0.38 * sin(t * 0.9)).toFloat())
            is MicroscopeSpec -> TapAction((0.5 + 0.3 * sin(t * 0.5)).toFloat(), (0.5 + 0.25 * sin(t * 0.37)).toFloat(), hit = false)
            is LabSpec -> if (spec.reagents.isNotEmpty()) LabStepAction(0, (spec.reagents[0].step * 1000).toInt()) else null
            is TrajectorySpec -> null
        }
    }
}
