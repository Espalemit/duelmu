package id.eduduel.app.net.room

import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.MatchOutcome
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.domain.ScoringEngine
import id.eduduel.app.net.protocol.EndReason

/** Peran KONEKSI di room. Identitas aplikasi semua pemain tetap Guest (tanpa akun). */
enum class RoomRole { HOST, GUEST }

/** Jalur koneksi room. Semuanya lokal/offline; tidak ada server internet. */
enum class TransportKind { LAN, BLUETOOTH, DEMO }

enum class RoomPhase {
    IDLE,
    HANDSHAKE,
    LOBBY,
    /** MATCH_SETUP dikirim/diterima, menunggu BANK_CHECK. */
    STARTING,
    PREPARING,
    QUESTION,
    REVEAL,
    FINISHED,
    /** Tamu: koneksi ke Host terputus di tengah pertandingan, mencoba menyambung ulang. */
    RECONNECTING,
    CANCELLED,
    FAILED,
}

enum class RoomFailure {
    BANK_MISMATCH,
    PROTOCOL_MISMATCH,
    ROOM_FULL,
    MATCH_IN_PROGRESS,
    WRONG_ROOM_CODE,
    INVALID_SETUP,
    INCONSISTENT_RESULT,
    HANDSHAKE_TIMEOUT,
    CONNECTION_LOST,
    HOST_LEFT,
    KICKED,
    BLUETOOTH_OFF,
    PROTOCOL_ERROR,
    RECONNECT_FAILED,
    RESUME_REJECTED,
    NOT_ENOUGH_PLAYERS,
}

/** Identitas pemain HANYA untuk room ini: nickname bebas + playerSessionId acak. */
data class RoomPlayer(
    val sessionId: String,
    val nickname: String,
    val isHost: Boolean,
    val ready: Boolean = false,
    val connected: Boolean = true,
)

data class PlayerRoundResult(
    val optionId: OptionId?,
    val elapsedMs: Long,
    val correct: Boolean,
    val points: Int,
    val timedOut: Boolean,
    /** Tidak tersambung selama ronde ini (0 poin). */
    val absent: Boolean = false,
) {
    companion object {
        fun timeout(limitMs: Long) = PlayerRoundResult(null, limitMs, correct = false, points = 0, timedOut = true)
        val ABSENT = PlayerRoundResult(null, 0, correct = false, points = 0, timedOut = false, absent = true)
    }
}

data class ActiveRound(
    val index: Int,
    val round: RoundQuestion,
    /** Waktu mulai lokal (jam monotonic perangkat ini). */
    val startedAtMs: Long,
    val durationMs: Long,
    val myAnswer: OptionId? = null,
    val myElapsedMs: Long? = null,
    val myTimedOut: Boolean = false,
    /** playerSessionId yang sudah mengunci jawaban (termasuk diri sendiri). */
    val lockedPlayers: Set<String> = emptySet(),
) {
    val isLocked: Boolean get() = myAnswer != null || myTimedOut
    fun remainingMs(now: Long): Long = ScoringEngine.remainingMs(durationMs, now - startedAtMs)
}

data class RoundSummary(
    val index: Int,
    val round: RoundQuestion,
    val correctOptionId: OptionId,
    /** playerSessionId -> hasil untuk SEMUA peserta. */
    val players: Map<String, PlayerRoundResult>,
) {
    fun of(sessionId: String): PlayerRoundResult? = players[sessionId]
}

/** Peringkat: skor tertinggi = 1; skor sama berbagi peringkat (tanpa tie-breaker karangan). */
data class Standing(val player: RoomPlayer, val total: Int, val correct: Int, val rank: Int)

fun computeStandings(participants: List<RoomPlayer>, results: List<RoundSummary>): List<Standing> {
    val totals = participants.associate { p -> p.sessionId to results.sumOf { it.of(p.sessionId)?.points ?: 0 } }
    return participants
        .map { p ->
            val total = totals.getValue(p.sessionId)
            Standing(
                player = p,
                total = total,
                correct = results.count { it.of(p.sessionId)?.correct == true },
                rank = 1 + totals.values.count { it > total },
            )
        }
        .sortedWith(compareBy<Standing> { it.rank }.thenBy { it.player.nickname })
}

/** Hasil akhir dari sudut pandang [meId]. Juara bersama = SERI. */
fun outcomeFor(meId: String, standings: List<Standing>): MatchOutcome? {
    val me = standings.firstOrNull { it.player.sessionId == meId } ?: return null
    return when {
        me.rank > 1 -> MatchOutcome.LOSE
        standings.count { it.rank == 1 } > 1 -> MatchOutcome.DRAW
        else -> MatchOutcome.WIN
    }
}

/** Ringkasan pertandingan yang sudah ditutup; tetap ada saat room kembali ke lobi. */
data class MatchSummary(
    val matchId: String,
    val config: MatchConfig,
    val participants: List<RoomPlayer>,
    val results: List<RoundSummary>,
    val totalRounds: Int,
    val endReason: EndReason,
    val failure: RoomFailure?,
) {
    val standings: List<Standing> get() = computeStandings(participants, results)
    val completed: Boolean get() = endReason == EndReason.COMPLETED
    fun outcomeFor(meId: String): MatchOutcome? = if (completed) outcomeFor(meId, standings) else null
}

data class RoomState(
    val role: RoomRole,
    val transport: TransportKind,
    val me: RoomPlayer,
    val phase: RoomPhase = RoomPhase.IDLE,
    /** Semua pemain di room (Host pertama), termasuk diri sendiri. */
    val players: List<RoomPlayer> = emptyList(),
    /** Kode room 4 digit (room Wi-Fi). Tamu harus memasukkannya. */
    val roomCode: String? = null,
    /** Nama perangkat/alamat Host yang terlihat oleh Tamu. */
    val hostLabel: String? = null,
    val config: MatchConfig = MatchConfig.INITIAL,
    val connected: Boolean = false,
    val matchId: String? = null,
    val participants: List<RoomPlayer> = emptyList(),
    val totalRounds: Int = 0,
    val activeRound: ActiveRound? = null,
    val results: List<RoundSummary> = emptyList(),
    val revealUntilMs: Long? = null,
    val meNextReady: Boolean = false,
    val endReason: EndReason? = null,
    val failure: RoomFailure? = null,
    val failureDetail: String? = null,
    val reconnectDeadlineMs: Long? = null,
    val rttMs: Long? = null,
    /** Pertandingan terakhir (untuk layar hasil, juga setelah room dibuka lagi). */
    val lastMatch: MatchSummary? = null,
    /** Pemberitahuan sekali tampil; [noticeId] naik setiap ada pemberitahuan baru. */
    val notice: RoomNotice? = null,
    val noticeId: Long = 0,
) {
    val inMatch: Boolean
        get() = phase in IN_MATCH_PHASES

    val guests: List<RoomPlayer> get() = players.filter { !it.isHost }

    val canEditName: Boolean get() = phase == RoomPhase.LOBBY && (role == RoomRole.HOST || !me.ready)

    /** Host boleh memulai: minimal 2 pemain dan semua Tamu sudah SIAP. */
    val canStart: Boolean
        get() = role == RoomRole.HOST && phase == RoomPhase.LOBBY && guests.isNotEmpty() && guests.all { it.ready && it.connected }

    val isFull: Boolean get() = players.size >= id.eduduel.app.net.protocol.MAX_ROOM_PLAYERS

    fun totalOf(sessionId: String): Int = results.sumOf { it.of(sessionId)?.points ?: 0 }

    val standings: List<Standing> get() = computeStandings(participants.map { p -> players.firstOrNull { it.sessionId == p.sessionId }?.let { p.copy(connected = it.connected) } ?: p.copy(connected = false) }, results)

    fun nameOf(sessionId: String): String =
        participants.firstOrNull { it.sessionId == sessionId }?.nickname
            ?: players.firstOrNull { it.sessionId == sessionId }?.nickname
            ?: "—"

    companion object {
        val IN_MATCH_PHASES = setOf(RoomPhase.STARTING, RoomPhase.PREPARING, RoomPhase.QUESTION, RoomPhase.REVEAL, RoomPhase.RECONNECTING)
    }
}

sealed interface RoomNotice {
    data class PlayerJoined(val name: String) : RoomNotice
    data class PlayerLeft(val name: String) : RoomNotice
    data class PlayerDisconnected(val name: String) : RoomNotice
    data class PlayerReconnected(val name: String) : RoomNotice
    data class PlayerRenamed(val oldName: String, val newName: String) : RoomNotice
    data class NameInvalid(val fallback: String) : RoomNotice
    data object ConfigChanged : RoomNotice
    data object Resumed : RoomNotice
}

/** Parameter waktu protokol. Latensi Bluetooth/Wi-Fi membuat sinkronisasi milidetik tidak dijamin. */
data class RoomTiming(
    val handshakeTimeoutMs: Long = 20_000,
    val guestHandshakeTimeoutMs: Long = 30_000,
    val bankCheckTimeoutMs: Long = 15_000,
    val prepareTimeoutMs: Long = 8_000,
    /** Toleransi Host menunggu jawaban Tamu setelah durasi habis (kompensasi latensi). */
    val answerGraceMs: Long = 2_500,
    val revealMs: Long = 6_000,
    val pingIntervalMs: Long = 2_500,
    /** Tanpa pesan masuk selama ini -> koneksi dianggap hilang (socket bisa diam tanpa error). */
    val watchdogMs: Long = 12_000,
    val reconnectWindowMs: Long = 30_000,
    val reconnectRetryMs: Long = 2_000,
    /** Toleransi klaim waktu jawab Tamu terhadap waktu yang diamati Host. */
    val elapsedToleranceMs: Long = 1_000,
)
