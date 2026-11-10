package id.eduduel.app.data.bank

import android.content.Context
import id.eduduel.app.BuildConfig
import id.eduduel.app.core.Hashing
import id.eduduel.app.domain.AnswerOption
import id.eduduel.app.domain.Difficulty
import id.eduduel.app.domain.GameBody
import id.eduduel.app.domain.GradeBand
import id.eduduel.app.domain.McqBody
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.Question
import id.eduduel.app.domain.QuestionBank
import id.eduduel.app.domain.QuestionType
import id.eduduel.app.domain.Subject
import id.eduduel.app.domain.game.GameEngines
import id.eduduel.app.domain.game.GameSpec
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.domain.game.GameType
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
    // schema 1
    val sourceFile: String? = null,
    val sourceSha256: String? = null,
    // schema 2
    val sources: List<SourceDto> = emptyList(),
    val assetManifest: List<AssetDto> = emptyList(),
)

@Serializable
private data class SourceDto(val file: String, val sha256: String, val questions: Int)

@Serializable
private data class AssetDto(val path: String, val sha256: String, val bytes: Long)

@Serializable
private data class QuestionDto(
    val id: String,
    val questionType: String = QuestionType.CLASSIC_MCQ.key,
    val subject: String,
    val gradeBand: String,
    val topic: String,
    val difficulty: String,
    val timeLimitSeconds: Int,
    val stem: String,
    val options: List<OptionDto>? = null,
    val correctOptionId: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val imageAsset: String? = null,
    val game: GameSpec? = null,
    val solution: GameSubmission? = null,
    val solutionText: String? = null,
    val editorialFlags: List<String> = emptyList(),
)

@Serializable
private data class OptionDto(val id: String, val text: String)

class BankLoadException(message: String) : Exception(message)

private class InMemoryQuestionBank(
    override val questions: List<Question>,
    override val bankSha256: String,
    override val versionName: String,
    val assetPaths: List<String>,
) : QuestionBank {
    private val index = questions.associateBy { it.id }
    override fun byId(id: String): Question? = index[id]
}

/**
 * Memuat dan memvalidasi `questions.json`. Gagal keras bila ada data tidak valid, karena
 * bank soal yang rusak tidak boleh dipakai bertanding.
 *
 * Decoder KETAT (`ignoreUnknownKeys = false`): field baru yang tidak dikenal (mis. gambar atau
 * konfigurasi game dari versi lain) membuat pemuatan gagal, bukan diam-diam diabaikan.
 * Schema 1 (400 soal klasik) tetap didukung; schema 2 menambah soal bergambar & mini-game.
 */
object QuestionBankLoader {
    val SUPPORTED_SCHEMAS = setOf(1, 2)
    const val SUPPORTED_SCHEMA = 2

    /** Json untuk bank soal. Diskriminator polimorfik = `kind` (game, target lab, solusi). */
    val bankJson = Json {
        ignoreUnknownKeys = false
        classDiscriminator = "kind"
    }

    /**
     * @param assetReader pembaca asset (path relatif `assets/`); bila diberikan, setiap entri
     *   `assetManifest` dicek keberadaan & SHA-256-nya (gambar hilang/rusak -> gagal sebelum match).
     */
    fun load(bytes: ByteArray, expectedSha256: String?, assetReader: ((String) -> ByteArray?)? = null): QuestionBank {
        val sha = Hashing.sha256Hex(bytes)
        if (expectedSha256 != null && !sha.equals(expectedSha256, ignoreCase = true)) {
            throw BankLoadException("Integritas bank soal gagal: hash $sha != $expectedSha256")
        }
        val dto = try {
            bankJson.decodeFromString(BankFileDto.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw BankLoadException("Format questions.json tidak valid: ${e.message?.take(300)}")
        }
        if (dto.schemaVersion !in SUPPORTED_SCHEMAS) {
            throw BankLoadException("schemaVersion ${dto.schemaVersion} tidak didukung (didukung: $SUPPORTED_SCHEMAS)")
        }
        val questions = dto.questions.map { toDomain(it, dto.schemaVersion) }
        if (questions.size != dto.questionCount) throw BankLoadException("questionCount tidak cocok")
        if (questions.map { it.id }.toSet().size != questions.size) throw BankLoadException("ID soal duplikat")

        // Manifest asset: setiap gambar soal harus terdaftar, dan setiap entri harus utuh.
        val manifest = dto.assetManifest.associateBy { it.path }
        if (manifest.size != dto.assetManifest.size) throw BankLoadException("assetManifest duplikat")
        questions.mapNotNull { it.imageAsset }.forEach { path ->
            if (path !in manifest) throw BankLoadException("Gambar $path tidak terdaftar di assetManifest")
        }
        if (assetReader != null) {
            val problems = dto.assetManifest.mapNotNull { a ->
                val raw = try {
                    assetReader(a.path)
                } catch (e: Exception) {
                    null
                }
                when {
                    raw == null -> "${a.path} hilang"
                    raw.size.toLong() != a.bytes || Hashing.sha256Hex(raw) != a.sha256 -> "${a.path} rusak/berbeda"
                    else -> null
                }
            }
            if (problems.isNotEmpty()) {
                throw BankLoadException("Asset gambar soal tidak valid (${problems.size}): ${problems.take(5).joinToString()}")
            }
        }
        return InMemoryQuestionBank(questions, sha, dto.bankVersionName, dto.assetManifest.map { it.path })
    }

    private fun toDomain(d: QuestionDto, schema: Int): Question {
        fun fail(msg: String): Nothing = throw BankLoadException("${d.id}: $msg")
        val subject = Subject.fromLabel(d.subject) ?: fail("subject tidak dikenal ${d.subject}")
        val grade = runCatching { GradeBand.valueOf(d.gradeBand) }.getOrNull() ?: fail("gradeBand ${d.gradeBand}")
        val difficulty = Difficulty.fromKey(d.difficulty) ?: fail("difficulty ${d.difficulty}")
        if (d.timeLimitSeconds != difficulty.timeLimitSeconds) fail("timeLimitSeconds tidak sesuai kesulitan")
        if (d.stem.isBlank()) fail("pertanyaan kosong")
        val type = QuestionType.fromKey(d.questionType) ?: fail("questionType ${d.questionType}")
        if (schema == 1 && type != QuestionType.CLASSIC_MCQ) fail("schema 1 hanya mendukung soal klasik")
        val body = when (type) {
            QuestionType.CLASSIC_MCQ, QuestionType.VISUAL_MCQ -> {
                if (d.game != null || d.solution != null || d.solutionText != null) fail("soal MCQ tidak boleh memuat konfigurasi game")
                val options = (d.options ?: fail("opsi tidak ada")).map { o ->
                    AnswerOption(OptionId.parse(o.id) ?: fail("opsi ${o.id}"), o.text.trim().ifEmpty { fail("opsi kosong") })
                }
                if (options.map { it.id } != OptionId.entries) fail("opsi harus A,B,C,D")
                if (options.map { it.text.lowercase() }.toSet().size != 4) fail("teks opsi tidak unik")
                val correct = OptionId.parse(d.correctOptionId) ?: fail("correctOptionId ${d.correctOptionId}")
                if (d.correctAnswer != null && options.first { it.id == correct }.text != d.correctAnswer.trim()) {
                    fail("correctAnswer tidak sama dengan teks opsi $correct")
                }
                val image = d.imageAsset
                if (type == QuestionType.VISUAL_MCQ && (image == null || !image.matches(IMAGE_PATH))) fail("imageAsset tidak valid: $image")
                if (type == QuestionType.CLASSIC_MCQ && image != null) fail("soal klasik tidak boleh bergambar")
                McqBody(options, correct, image)
            }
            QuestionType.MINI_GAME -> {
                if (d.options != null || d.correctOptionId != null || d.imageAsset != null) fail("soal game tidak memakai opsi A-D/gambar")
                val spec = d.game ?: fail("konfigurasi game tidak ada")
                val solution = d.solution ?: fail("solusi referensi tidak ada")
                if (spec.type.subject != subject) fail("jenis game ${spec.type} tidak sesuai mapel ${subject.label}")
                if (solution.type != spec.type) fail("jenis solusi tidak sesuai game")
                if (!GameEngines.isWellFormed(spec, solution) || !GameEngines.evaluate(spec, solution).success) {
                    fail("solusi referensi tidak menyelesaikan game")
                }
                GameBody(spec, d.solutionText?.takeIf { it.isNotBlank() } ?: fail("solutionText kosong"), solution)
            }
        }
        return Question(
            id = d.id,
            subject = subject,
            gradeBand = grade,
            topic = d.topic,
            difficulty = difficulty,
            timeLimitSeconds = d.timeLimitSeconds,
            stem = d.stem,
            explanation = d.explanation?.takeIf { it.isNotBlank() },
            type = type,
            body = body,
            editorialFlags = d.editorialFlags,
        )
    }

    private val IMAGE_PATH = Regex("^images/[A-Z]{3}-(SMP|SMA)-\\d{3}\\.png$")

    /** Jenis game per mapel (dipakai validasi & UI). */
    fun gameTypeOf(subject: Subject): GameType = GameType.forSubject(subject)
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
                val bank = QuestionBankLoader.load(bytes, BuildConfig.BANK_SHA256) { path ->
                    context.assets.open(path).use { it.readBytes() }
                }
                BankState.Ready(bank)
            } catch (e: Exception) {
                BankState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    companion object {
        const val ASSET_NAME = "questions.json"
    }
}
