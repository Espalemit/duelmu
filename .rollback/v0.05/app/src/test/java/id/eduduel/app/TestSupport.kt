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
    val bank: QuestionBank by lazy { QuestionBankLoader.load(assetFile.readBytes(), expectedSha256 = null) }
}

/** Bank identik isi tetapi hash berbeda: mensimulasikan APK dengan versi bank lain. */
class DifferentVersionBank(private val base: QuestionBank) : QuestionBank {
    override val questions: List<Question> get() = base.questions
    override val bankSha256: String = "0".repeat(64)
    override val versionName: String = "versi-lain"
    override fun byId(id: String): Question? = base.byId(id)
}
