package id.eduduel.app.domain

import id.eduduel.app.domain.game.GameSpec
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.domain.game.GameType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Subject(val label: String, val idPrefix: String) {
    FISIKA("Fisika", "FIS"),
    BIOLOGI("Biologi", "BIO"),
    KIMIA("Kimia", "KIM"),
    MATEMATIKA("Matematika", "MAT");

    companion object {
        fun fromLabel(label: String): Subject? = entries.firstOrNull { it.label == label }
    }
}

@Serializable
enum class GradeBand { SMP, SMA }

/** Durasi & poin dasar per kesulitan sesuai spesifikasi: 60/90/120 detik, 100/200/300 poin. */
@Serializable
enum class Difficulty(val key: String, val label: String, val timeLimitSeconds: Int, val basePoints: Int) {
    MUDAH("mudah", "Mudah", 60, 100),
    SEDANG("sedang", "Sedang", 90, 200),
    SULIT("sulit", "Sulit", 120, 300);

    companion object {
        fun fromKey(key: String): Difficulty? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
enum class OptionId { A, B, C, D;

    companion object {
        fun parse(value: String?): OptionId? = entries.firstOrNull { it.name == value }
    }
}

data class AnswerOption(val id: OptionId, val text: String)

/** Diskriminator jenis soal di bank (`questionType`). */
@Serializable
enum class QuestionType(val key: String, val label: String) {
    @SerialName("classic_mcq") CLASSIC_MCQ("classic_mcq", "Kuis Klasik"),
    @SerialName("visual_mcq") VISUAL_MCQ("visual_mcq", "Kuis Bergambar"),
    @SerialName("mini_game") MINI_GAME("mini_game", "Mini-Game");

    companion object {
        fun fromKey(key: String): QuestionType? = entries.firstOrNull { it.key == key }
    }
}

/** Isi soal: pilihan ganda A-D (klasik/bergambar) ATAU konfigurasi mini-game. */
sealed interface QuestionBody

data class McqBody(
    val options: List<AnswerOption>,
    val correctOptionId: OptionId,
    /** Path asset relatif terhadap `assets/` (mis. "images/FIS-SMP-051.png"); hanya soal bergambar. */
    val imageAsset: String? = null,
) : QuestionBody

data class GameBody(
    val spec: GameSpec,
    /** Ringkasan solusi yang ditampilkan SETELAH ronde ditutup. */
    val solutionText: String,
    /** Submisi referensi yang terbukti berhasil (dipakai tes & bot demo). Tidak pernah dikirim lewat jaringan. */
    val reference: GameSubmission,
) : QuestionBody {
    val gameType: GameType get() = spec.type
}

/** Model soal tipe-kuat. `explanation` boleh null: UI tidak pernah mengarang pembahasan. */
data class Question(
    val id: String,
    val subject: Subject,
    val gradeBand: GradeBand,
    val topic: String,
    val difficulty: Difficulty,
    val timeLimitSeconds: Int,
    val stem: String,
    val explanation: String?,
    val type: QuestionType,
    val body: QuestionBody,
    /** Catatan editorial (mis. perlu tinjauan guru). Kosong = tidak ada catatan. */
    val editorialFlags: List<String> = emptyList(),
) {
    init {
        require((type == QuestionType.MINI_GAME) == (body is GameBody)) { "$id: questionType tidak cocok dengan isi" }
        require(type != QuestionType.VISUAL_MCQ || (body as McqBody).imageAsset != null) { "$id: soal bergambar tanpa gambar" }
        require(type != QuestionType.CLASSIC_MCQ || (body as McqBody).imageAsset == null) { "$id: soal klasik tidak boleh bergambar" }
    }

    val timeLimitMs: Long get() = timeLimitSeconds * 1000L
    val mcq: McqBody? get() = body as? McqBody
    val game: GameBody? get() = body as? GameBody
    val isGame: Boolean get() = body is GameBody

    /** Opsi A-D; kosong untuk soal game. */
    val options: List<AnswerOption> get() = mcq?.options.orEmpty()

    /** Kunci MCQ; null untuk soal game (penilaian memakai mesin game, bukan opsi A-D). */
    val correctOptionId: OptionId? get() = mcq?.correctOptionId
    val imageAsset: String? get() = mcq?.imageAsset

    fun option(id: OptionId): AnswerOption = options.first { it.id == id }

    companion object {
        /** Konstruktor ringkas soal pilihan ganda (dipakai preview/tes). */
        fun mcq(
            id: String, subject: Subject, gradeBand: GradeBand, topic: String, difficulty: Difficulty, stem: String,
            options: List<AnswerOption>, correct: OptionId, explanation: String?, imageAsset: String? = null,
        ) = Question(
            id, subject, gradeBand, topic, difficulty, difficulty.timeLimitSeconds, stem, explanation,
            if (imageAsset == null) QuestionType.CLASSIC_MCQ else QuestionType.VISUAL_MCQ,
            McqBody(options, correct, imageAsset),
        )
    }
}

/** Mode pertandingan: jenis soal yang diambil. Default KLASIK = perilaku lama (400 soal). */
@Serializable
enum class QuestionMode(val label: String, val types: Set<QuestionType>) {
    CLASSIC("Kuis Klasik", setOf(QuestionType.CLASSIC_MCQ)),
    VISUAL("Kuis Bergambar", setOf(QuestionType.VISUAL_MCQ)),
    GAME("Mini-Game", setOf(QuestionType.MINI_GAME)),
    MIXED("Campuran Tipe", QuestionType.entries.toSet()),
}

/**
 * Ghost di room multiplayer. LIVE (default): gerak & aksi lawan terlihat real-time, termasuk
 * label/parameter (pemain bisa meniru strategi). FAIR: ghost tetap terlihat, tetapi label asteroid,
 * titik sentuh, parameter eksperimen, dan indikator berhasil disembunyikan sampai ronde ditutup.
 */
@Serializable
enum class GhostMode { LIVE, FAIR }

/**
 * Aturan pertandingan/latihan: mapel + mode jenis soal (+ mode ghost untuk room).
 * `subject == null` berarti **Campuran** (semua mapel). Jenjang & kesulitan BUKAN filter.
 * Setiap pertandingan selalu berisi [QUESTIONS_PER_MATCH] soal acak.
 */
@Serializable
data class MatchConfig(
    val subject: Subject? = null,
    val mode: QuestionMode = QuestionMode.CLASSIC,
    val ghostMode: GhostMode = GhostMode.LIVE,
) {
    fun matches(q: Question): Boolean = (subject == null || q.subject == subject) && q.type in mode.types
    val isMixed: Boolean get() = subject == null
    val questionCount: Int get() = QUESTIONS_PER_MATCH

    companion object {
        const val QUESTIONS_PER_MATCH = 10

        /** Pilihan awal di UI. Default konstruktor tetap `null` agar JSON tanpa field `subject` = Campuran. */
        val INITIAL = MatchConfig(Subject.FISIKA)
    }
}

interface QuestionBank {
    val questions: List<Question>
    val bankSha256: String
    val versionName: String
    fun byId(id: String): Question?
    fun pool(config: MatchConfig): List<Question> = questions.filter(config::matches)
}

enum class MatchOutcome { WIN, LOSE, DRAW }

/** Ringkasan aturan untuk ditampilkan dan disimpan di riwayat. */
fun MatchConfig.describe(): String = "${subject?.label ?: "Campuran"} · ${mode.label} · $questionCount soal"
