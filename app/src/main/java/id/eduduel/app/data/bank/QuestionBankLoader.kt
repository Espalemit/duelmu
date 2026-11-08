package id.eduduel.app.data.bank

import android.content.Context
import id.eduduel.app.BuildConfig
import id.eduduel.app.core.Hashing
import id.eduduel.app.domain.AnswerOption
import id.eduduel.app.domain.Difficulty
import id.eduduel.app.domain.GradeBand
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.Question
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.Subject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BankFileDto(
    val schemaVersion: Int,
    val bankVersionName: String,
    val questionCount: Int,
    val questions: List<QuestionDto>,
)

@Serializable
private data class QuestionDto(
    val id: String,
    val subject: String,
    val gradeBand: String,
    val topic: String,
    val difficulty: String,
    val timeLimitSeconds: Int,
    val stem: String,
    val options: List<OptionDto>,
    val correctOptionId: String,
    val correctAnswer: String? = null,
    val explanation: String? = null,
)

@Serializable
private data class OptionDto(val id: String, val text: String)

class BankLoadException(message: String) : Exception(message)

private class InMemoryQuestionBank(
    override val questions: List<Question>,
    override val bankSha256: String,
    override val versionName: String,
) : QuestionBank {
    private val index = questions.associateBy { it.id }
    override fun byId(id: String): Question? = index[id]
}

/**
 * Memuat dan memvalidasi `questions.json`. Gagal keras bila ada data tidak valid, karena
 * bank soal yang rusak tidak boleh dipakai bertanding.
 */
object QuestionBankLoader {
    const val SUPPORTED_SCHEMA = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun load(bytes: ByteArray, expectedSha256: String?): QuestionBank {
        val sha = Hashing.sha256Hex(bytes)
        if (expectedSha256 != null && !sha.equals(expectedSha256, ignoreCase = true)) {
            throw BankLoadException("Integritas bank soal gagal: hash $sha != $expectedSha256")
        }
        val dto = try {
            json.decodeFromString(BankFileDto.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw BankLoadException("Format questions.json tidak valid: ${e.message}")
        }
        if (dto.schemaVersion != SUPPORTED_SCHEMA) {
            throw BankLoadException("schemaVersion ${dto.schemaVersion} tidak didukung")
        }
        val questions = dto.questions.map(::toDomain)
        if (questions.size != dto.questionCount) throw BankLoadException("questionCount tidak cocok")
        if (questions.map { it.id }.toSet().size != questions.size) throw BankLoadException("ID soal duplikat")
        return InMemoryQuestionBank(questions, sha, dto.bankVersionName)
    }

    private fun toDomain(d: QuestionDto): Question {
        fun fail(msg: String): Nothing = throw BankLoadException("${d.id}: $msg")
        val subject = Subject.fromLabel(d.subject) ?: fail("subject tidak dikenal ${d.subject}")
        val grade = runCatching { GradeBand.valueOf(d.gradeBand) }.getOrNull() ?: fail("gradeBand ${d.gradeBand}")
        val difficulty = Difficulty.fromKey(d.difficulty) ?: fail("difficulty ${d.difficulty}")
        if (d.timeLimitSeconds != difficulty.timeLimitSeconds) fail("timeLimitSeconds tidak sesuai kesulitan")
        if (d.stem.isBlank()) fail("pertanyaan kosong")
        val options = d.options.map { o ->
            AnswerOption(OptionId.parse(o.id) ?: fail("opsi ${o.id}"), o.text.trim().ifEmpty { fail("opsi kosong") })
        }
        if (options.map { it.id } != OptionId.entries) fail("opsi harus A,B,C,D")
        if (options.map { it.text.lowercase() }.toSet().size != 4) fail("teks opsi tidak unik")
        val correct = OptionId.parse(d.correctOptionId) ?: fail("correctOptionId ${d.correctOptionId}")
        if (d.correctAnswer != null && options.first { it.id == correct }.text != d.correctAnswer.trim()) {
            fail("correctAnswer tidak sama dengan teks opsi $correct")
        }
        return Question(
            id = d.id,
            subject = subject,
            gradeBand = grade,
            topic = d.topic,
            difficulty = difficulty,
            timeLimitSeconds = d.timeLimitSeconds,
            stem = d.stem,
            options = options,
            correctOptionId = correct,
            explanation = d.explanation?.takeIf { it.isNotBlank() },
        )
    }
}

sealed interface BankState {
    data object Loading : BankState
    data class Ready(val bank: QuestionBank) : BankState
    data class Error(val message: String) : BankState
}

/** Memuat bank soal dari asset lokal di IO dispatcher. Tidak ada unduhan jaringan. */
class QuestionBankRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<BankState>(BankState.Loading)
    val state: StateFlow<BankState> = _state.asStateFlow()

    val bankOrNull: QuestionBank? get() = (state.value as? BankState.Ready)?.bank

    init {
        reload()
    }

    fun reload() {
        _state.value = BankState.Loading
        scope.launch(io) {
            _state.value = try {
                val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
                BankState.Ready(QuestionBankLoader.load(bytes, BuildConfig.BANK_SHA256))
            } catch (e: Exception) {
                BankState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    companion object {
        const val ASSET_NAME = "questions.json"
    }
}
