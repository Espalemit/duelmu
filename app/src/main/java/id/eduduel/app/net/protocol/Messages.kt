package id.eduduel.app.net.protocol

import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.domain.game.GameType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Protokol room EduDuel v3 (JSON UTF-8 dalam frame 4-byte length-prefix big-endian).
 *
 * v3 (Duelmu 0.4): soal bergambar + mini-game. Tambahan: seed ronde game (MATCH_SETUP/QUESTION_START),
 * durasi tutorial (QUESTION_PREPARE), GAME_SUBMIT (masukan game, divalidasi ulang Host),
 * GAME_STATE_UPDATE (ghost visual) dan GAME_ACTION (event aksi) yang di-relay Host ke SEMUA peserta lain,
 * serta hasil ronde game (submisi + ringkasan) di ROUND_RESULT. Klien v2 ditolak (versi protokol).
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
const val PROTOCOL_VERSION = 3

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
    /** Urutan tampilan opsi per soal, mis. ["C","A","D","B"]; kosong untuk soal game. */
    val optionOrders: List<List<String>>,
    /** Peserta pertandingan (snapshot nickname saat mulai). */
    val participants: List<WirePlayer>,
    val bankSha256: String,
    val bankVersionName: String,
    /** Seed arena/spawn per soal (sama untuk semua pemain); 0 untuk soal MCQ. */
    val seeds: List<Long> = emptyList(),
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
data class QuestionPrepare(
    val questionId: String,
    /** Durasi tutorial interaksi game sebelum START (0 = MCQ). Waktu menjawab BELUM berjalan. */
    val tutorialMs: Long = 0,
) : Payload

@Serializable
@SerialName("QUESTION_READY")
data class QuestionReady(val questionId: String) : Payload

/** Tidak memuat kunci jawaban. */
@Serializable
@SerialName("QUESTION_START")
data class QuestionStart(val questionId: String, val optionOrder: List<String>, val durationMs: Long, val seed: Long = 0) : Payload

/** `optionId` = id kanonik opsi (bukan posisi tampilan); null = timeout. */
@Serializable
@SerialName("ANSWER")
data class Answer(val questionId: String, val optionId: String?, val elapsedMs: Long) : Payload

/** Host memberi tahu semua pemain bahwa seseorang sudah mengunci jawaban (tanpa isi jawaban). */
@Serializable
@SerialName("ANSWER_LOCKED")
data class AnswerLocked(val playerSessionId: String) : Payload

/**
 * Jawaban soal game: MASUKAN pemain (bukan flag benar/salah). Host memutar ulang dengan mesin
 * deterministik + seed yang sama. `submission == null` = menyerah/gagal sebelum waktu habis.
 */
@Serializable
@SerialName("GAME_SUBMIT")
data class GameSubmit(val questionId: String, val elapsedMs: Long, val submission: GameSubmission?) : Payload

/**
 * Snapshot visual ghost (5-10/detik). Koordinat DUNIA ternormalisasi 0..1, bukan piksel layar.
 * TIDAK pernah memuat kunci jawaban. Pada mode FAIR, label/parameter/indikator berhasil dihapus
 * oleh pengirim DAN oleh Host sebelum relay.
 */
@Serializable
@SerialName("GAME_STATE_UPDATE")
data class GameStateUpdate(
    val playerSessionId: String,
    val gameType: GameType,
    /** Nomor urut per pemain per ronde; snapshot terlambat/out-of-order dibuang. */
    val seq: Int,
    /** Waktu sejak ronde dimulai menurut jam monotonic pengirim. */
    val tMs: Long,
    val ghost: GhostState,
) : Payload

/** Event aksi diskrit (tembak, luncur, tap, tuang) untuk animasi ghost. */
@Serializable
@SerialName("GAME_ACTION")
data class GameActionEvent(
    val playerSessionId: String,
    val gameType: GameType,
    val seq: Int,
    val tMs: Long,
    val action: GhostAction,
) : Payload

/** State visual minimal per mesin game (bukan frame render). */
@Serializable
sealed interface GhostState {
    /** Pemain sudah mengunci/menyelesaikan ronde (ghost tampil idle). */
    val done: Boolean
}

@Serializable
@SerialName("asteroid")
data class AsteroidGhost(
    val x: Float,
    val firing: Boolean = false,
    /** Slot terisi (LIVE saja). */
    val filled: Int? = null,
    val wrong: Int? = null,
    override val done: Boolean = false,
) : GhostState

@Serializable
@SerialName("trajectory")
data class TrajectoryGhost(
    /** Arah bidikan 0..1 (0° .. 90°) — LIVE saja. */
    val aim: Float? = null,
    /** Kekuatan/kelajuan relatif 0..1 — LIVE saja. */
    val power: Float? = null,
    val shots: Int = 0,
    override val done: Boolean = false,
) : GhostState

@Serializable
@SerialName("microscope")
data class MicroGhost(
    /** Bingkai area pengamatan (dunia ternormalisasi). */
    val vx: Float,
    val vy: Float,
    val vw: Float,
    val vh: Float,
    val focus: Float? = null,
    val cx: Float? = null,
    val cy: Float? = null,
    override val done: Boolean = false,
) : GhostState

@Serializable
@SerialName("lab")
data class LabGhost(
    /** Posisi alat yang dipegang (meja lab ternormalisasi). */
    val tx: Float,
    val ty: Float,
    val holding: Int? = null,
    val pouring: Boolean = false,
    /** Tingkat isi bejana 0..1 — LIVE saja. */
    val fill: Float? = null,
    override val done: Boolean = false,
) : GhostState

@Serializable
sealed interface GhostAction

@Serializable
@SerialName("fire")
data class FireAction(val x: Float, val token: String? = null, val correct: Boolean? = null) : GhostAction

@Serializable
@SerialName("launch")
data class LaunchAction(val a: Int? = null, val b: Int? = null, val hit: Boolean? = null) : GhostAction

@Serializable
@SerialName("tap")
data class TapAction(val x: Float? = null, val y: Float? = null, val hit: Boolean? = null) : GhostAction

@Serializable
@SerialName("lab_step")
data class LabStepAction(val reagent: Int? = null, val amt: Int? = null) : GhostAction

@Serializable
data class PlayerRound(
    val optionId: String?,
    val elapsedMs: Long,
    val correct: Boolean,
    val points: Int,
    val timedOut: Boolean,
    /** Pemain tidak tersambung selama ronde ini: 0 poin, bukan kekalahan otomatis lawan. */
    val absent: Boolean = false,
    /** Soal game: masukan pemain (dikirim SETELAH ronde ditutup agar semua HP bisa memverifikasi). */
    val submission: GameSubmission? = null,
    /** Ringkasan hasil game yang transparan (mis. "Slot terisi 2/2, salah 1"). */
    val detail: String? = null,
)

@Serializable
data class RoundResult(
    val roundIndex: Int,
    val questionId: String,
    /** Kunci MCQ; null untuk soal game (penilaian lewat mesin game). */
    val correctOptionId: String? = null,
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
