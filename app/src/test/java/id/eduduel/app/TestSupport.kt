package id.eduduel.app

import id.eduduel.app.data.bank.QuestionBankLoader
import id.eduduel.app.domain.Question
import id.eduduel.app.domain.QuestionBank
import java.io.File

/** Memuat asset bank soal asli (src/main/assets/questions.json) untuk unit test JVM. */
object TestBank {
    val assetFile: File by lazy {
        listOf(File("src/main/assets/questions.json"), File("app/src/main/assets/questions.json"))
            .first { it.exists() }
    }

    /** Akar repository (tes berjalan dari app/ atau dari akar). */
    val root: File by lazy { assetFile.absoluteFile.parentFile!!.parentFile!!.parentFile!!.parentFile!!.parentFile!! }

    fun file(relative: String): File = File(root, relative)

    val bank: QuestionBank by lazy {
        val dir = assetFile.parentFile!!
        QuestionBankLoader.load(assetFile.readBytes(), expectedSha256 = null) { File(dir, it).readBytes() }
    }
}

/** Isolate engine/MCQ transport tests; production always uses the complete mixed bank. */
fun QuestionBank.onlyTypes(vararg types: id.eduduel.app.domain.QuestionType): QuestionBank {
    val base = this
    return object : QuestionBank {
        override val questions = base.questions.filter { it.type in types }
        override val bankSha256 get() = base.bankSha256
        override val versionName get() = base.versionName
        override fun byId(id: String) = questions.firstOrNull { it.id == id }
    }
}

/** Bank identik isi tetapi hash berbeda: mensimulasikan APK dengan versi bank lain. */
class DifferentVersionBank(private val base: QuestionBank) : QuestionBank {
    override val questions: List<Question> get() = base.questions
    override val bankSha256: String = "0".repeat(64)
    override val versionName: String = "versi-lain"
    override fun byId(id: String): Question? = base.byId(id)
}
