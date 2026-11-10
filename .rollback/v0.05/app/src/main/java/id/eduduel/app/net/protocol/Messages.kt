package id.eduduel.app.net.protocol

import id.eduduel.app.domain.MatchConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Protokol room EduDuel v2 (JSON UTF-8 dalam frame 4-byte length-prefix big-endian).
 *
 * Topologi bintang: Host adalah pusat room DAN pemain; setiap Tamu hanya punya satu koneksi
 * ke Host (Bluetooth RFCOMM atau TCP di Wi-Fi/hotspot yang sama). Maksimal 4 pemain per room.
 * Host otoritatif: memilih soal, membuka/menutup ronde, menghitung skor, lalu menyiarkannya.
 *
 * Setiap pesan dibungkus [Envelope]: versi protokol, nomor urut (monoton per koneksi),
 * msgId unik (deteksi duplikat), matchId dan roundIndex bila relevan.
 * Payload memakai diskriminator `"type"` (mis. `{"type":"HELLO", ...}`).
 *
 * Catatan keamanan: kunci jawaban TIDAK pernah dikirim sebelum ronde ditutup (ROUND_RESULT).
 */
const val PROTOCOL_VERSION = 2

/** Batas pemain per room (Host + 3 Tamu). */
const val MAX_ROOM_PLAYERS = 4

@Serializable
data class Envelope(
    val v: Int,
    val seq: Long,
    val msgId: String,
    val matchId: String? = null,
    val roundIndex: Int? = null,
    val payload: Payload,
)

@Serializable
sealed interface Payload

/** Alasan penolakan handshake. */
@Serializable
enum class RejectReason { BANK_MISMATCH, PROTOCOL_MISMATCH, ROOM_FULL, MATCH_IN_PROGRESS, WRONG_ROOM_CODE, INVALID_PLAYER, RESUME_REJECTED }

/** Kode error protokol. `fatal = true` berarti koneksi ditutup. */
@Serializable
enum class ErrorCode {
    MALFORMED, PROTOCOL_VERSION, BANK_MISMATCH, INVALID_SETUP, INCONSISTENT_RESULT,
    UNEXPECTED_MESSAGE, TIMEOUT, INTERNAL,
}

// ---------------------------------------------------------------- Handshake & room

/** Dikirim Tamu segera setelah koneksi. Nickname + playerSessionId acak (bukan akun/MAC/IP). */
@Serializable
@SerialName("HELLO")
data class Hello(
    val appVersion: String,
    val bankVersionName: String,
    val bankSha256: String,
    val nickname: String,
    val playerSessionId: String,
    /** Kode room 4 digit yang tampil di HP Host (wajib untuk room Wi-Fi). */
    val roomCode: String? = null,
    /** Diisi saat menyambung ulang di tengah pertandingan. */
    val resumeMatchId: String? = null,
) : Payload

@Serializable
@SerialName("HELLO_ACK")
data class HelloAck(
    val accepted: Boolean,
    val reason: RejectReason? = null,
    val appVersion: String,
    val bankVersionName: String,
    val bankSha256: String,
    val hostSessionId: String,
    val resume: ResumeSnapshot? = null,
) : Payload

/** Pemain sebagaimana disiarkan Host. */
@Serializable
data class WirePlayer(
    val sessionId: String,
    val nickname: String,
    val isHost: Boolean,
    val ready: Boolean,
    val connected: Boolean,
)

@Serializable
enum class WireRoomPhase { LOBBY, IN_MATCH, RESULTS }

/** Keadaan room lengkap; dikirim Host setiap kali ada perubahan (pemain masuk/keluar, siap, nama, aturan). */
@Serializable
@SerialName("ROOM_STATE")
data class RoomStateMessage(
    val players: List<WirePlayer>,
    val config: MatchConfig,
    val phase: WireRoomPhase,
    val maxPlayers: Int = MAX_ROOM_PLAYERS,
) : Payload

/** Ringkasan pertandingan otoritatif dari Host saat Tamu tersambung kembali. */
@Serializable
data class ResumeSnapshot(
    val matchId: String,
    val setup: MatchSetup,
    val results: List<RoundResult>,
)

@Serializable
@SerialName("PLAYER_NAME_UPDATE")
data class PlayerNameUpdate(val nickname: String) : Payload

@Serializable
@SerialName("READY")
data class Ready(val ready: Boolean) : Payload

/** Host membuka room lagi setelah pertandingan selesai (main lagi dengan pemain yang masih tersambung). */
@Serializable
@SerialName("BACK_TO_ROOM")
data class BackToRoom(val reopened: Boolean = true) : Payload

// ---------------------------------------------------------------- Setup pertandingan

@Serializable
@SerialName("MATCH_SETUP")
data class MatchSetup(
    val config: MatchConfig,
    val questionIds: List<String>,
    /** Urutan tampilan opsi per soal, mis. ["C","A","D","B"]. */
    val optionOrders: List<List<String>>,
    /** Peserta pertandingan (snapshot nickname saat mulai). */
    val participants: List<WirePlayer>,
    val bankSha256: String,
    val bankVersionName: String,
) : Payload

@Serializable
@SerialName("BANK_CHECK")
data class BankCheck(
    val ok: Boolean,
    val bankSha256: String,
    val missingIds: List<String> = emptyList(),
    val reason: String? = null,
) : Payload

// ---------------------------------------------------------------- Ronde

@Serializable
@SerialName("QUESTION_PREPARE")
data class QuestionPrepare(val questionId: String) : Payload

@Serializable
@SerialName("QUESTION_READY")
data class QuestionReady(val questionId: String) : Payload

/** Tidak memuat kunci jawaban. */
@Serializable
@SerialName("QUESTION_START")
data class QuestionStart(val questionId: String, val optionOrder: List<String>, val durationMs: Long) : Payload

/** `optionId` = id kanonik opsi (bukan posisi tampilan); null = timeout. */
@Serializable
@SerialName("ANSWER")
data class Answer(val questionId: String, val optionId: String?, val elapsedMs: Long) : Payload

/** Host memberi tahu semua pemain bahwa seseorang sudah mengunci jawaban (tanpa isi jawaban). */
@Serializable
@SerialName("ANSWER_LOCKED")
data class AnswerLocked(val playerSessionId: String) : Payload

@Serializable
data class PlayerRound(
    val optionId: String?,
    val elapsedMs: Long,
    val correct: Boolean,
    val points: Int,
    val timedOut: Boolean,
    /** Pemain tidak tersambung selama ronde ini: 0 poin, bukan kekalahan otomatis lawan. */
    val absent: Boolean = false,
)

@Serializable
data class RoundResult(
    val roundIndex: Int,
    val questionId: String,
    val correctOptionId: String,
    /** playerSessionId -> hasil. Berisi SEMUA peserta. */
    val players: Map<String, PlayerRound>,
)

@Serializable
@SerialName("ROUND_RESULT")
data class RoundResultMessage(val result: RoundResult, val totals: Map<String, Int>) : Payload

@Serializable
@SerialName("NEXT_ROUND")
data class NextRound(val nextRoundIndex: Int) : Payload

@Serializable
enum class EndReason { COMPLETED, CANCELLED_DISCONNECT, CANCELLED_BY_PLAYER, CANCELLED_ERROR }

@Serializable
@SerialName("MATCH_END")
data class MatchEnd(
    val totals: Map<String, Int>,
    val reason: EndReason,
    val roundsPlayed: Int,
) : Payload

// ---------------------------------------------------------------- Kontrol

@Serializable
@SerialName("ERROR")
data class ProtocolError(val code: ErrorCode, val message: String, val fatal: Boolean) : Payload

@Serializable
@SerialName("PING")
data class Ping(val sentAtMs: Long) : Payload

@Serializable
@SerialName("PONG")
data class Pong(val echoSentAtMs: Long) : Payload

@Serializable
enum class DisconnectReason { LEFT, KICKED, HOST_CLOSED }

@Serializable
@SerialName("DISCONNECT")
data class Disconnect(val reason: DisconnectReason, val message: String? = null) : Payload
