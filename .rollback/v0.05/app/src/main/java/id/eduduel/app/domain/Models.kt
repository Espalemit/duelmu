package id.eduduel.app.domain

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

/** Model soal tipe-kuat. `explanation` boleh null: UI tidak pernah mengarang pembahasan. */
data class Question(
    val id: String,
    val subject: Subject,
    val gradeBand: GradeBand,
    val topic: String,
    val difficulty: Difficulty,
    val timeLimitSeconds: Int,
    val stem: String,
    val options: List<AnswerOption>,
    val correctOptionId: OptionId,
    val explanation: String?,
) {
    val timeLimitMs: Long get() = timeLimitSeconds * 1000L
    fun option(id: OptionId): AnswerOption = options.first { it.id == id }
}

/**
 * Aturan pertandingan/latihan: HANYA mapel. `subject == null` berarti **Campuran** (semua mapel).
 * Jenjang (SMP/SMA) dan tingkat kesulitan BUKAN filter; keduanya hanya label pada tiap soal
 * (dan tetap menentukan durasi 60/90/120 dtk serta poin dasar 100/200/300 soal tersebut).
 * Setiap pertandingan selalu berisi [QUESTIONS_PER_MATCH] soal acak dari mapel yang dipilih.
 */
@Serializable
data class MatchConfig(val subject: Subject? = null) {
    fun matches(q: Question): Boolean = subject == null || q.subject == subject
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
fun MatchConfig.describe(): String = "${subject?.label ?: "Campuran"} · $questionCount soal"
