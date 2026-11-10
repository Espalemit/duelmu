package id.eduduel.app.data

import id.eduduel.app.TestBank
import id.eduduel.app.core.Hashing
import id.eduduel.app.data.bank.BankLoadException
import id.eduduel.app.data.bank.QuestionBankLoader
import id.eduduel.app.domain.Difficulty
import id.eduduel.app.domain.GradeBand
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validasi dataset yang dipaketkan di APK (asset hasil tools/build_question_asset.py). */
class QuestionBankDatasetTest {
    private val bank = TestBank.bank

    @Test fun exactly400Questions_100PerSubject_50Per_Grade() {
        assertEquals(400, bank.questions.size)
        for (s in Subject.entries) {
            assertEquals(s.label, 100, bank.questions.count { it.subject == s })
            for (g in GradeBand.entries) {
                assertEquals("${s.label} $g", 50, bank.questions.count { it.subject == s && it.gradeBand == g })
            }
        }
    }

    @Test fun uniqueIds_consistentWithSubjectAndGrade() {
        assertEquals(400, bank.questions.map { it.id }.toSet().size)
        for (q in bank.questions) {
            assertTrue(q.id, q.id.startsWith("${q.subject.idPrefix}-${q.gradeBand.name}-"))
            assertEquals(q, bank.byId(q.id))
        }
    }

    @Test fun fourUniqueOptions_exactlyOneCorrect() {
        for (q in bank.questions) {
            assertEquals(q.id, OptionId.entries, q.options.map { it.id })
            assertEquals(q.id, 4, q.options.map { it.text.lowercase() }.toSet().size)
            assertTrue(q.id, q.options.all { it.text.isNotBlank() })
            assertEquals(q.id, 1, q.options.count { it.id == q.correctOptionId })
            assertTrue(q.id, q.stem.isNotBlank())
        }
    }

    @Test fun timeLimitsMatchDifficulty_60_90_120() {
        for (q in bank.questions) {
            val expected = when (q.difficulty) {
                Difficulty.MUDAH -> 60
                Difficulty.SEDANG -> 90
                Difficulty.SULIT -> 120
            }
            assertEquals(q.id, expected, q.timeLimitSeconds)
        }
    }

    @Test fun explanationsOptional_neverBlank_andPresentForCalculations() {
        assertTrue(bank.questions.all { q -> q.explanation?.isNotBlank() ?: true })
        assertEquals(150, bank.questions.count { it.explanation != null })
        assertTrue(bank.questions.any { it.explanation == null })
    }

    @Test fun mathIsRenderedWithUnicode_noCaretLeft() {
        val texts = bank.questions.flatMap { q -> listOf(q.stem, q.explanation.orEmpty()) + q.options.map { it.text } }
        assertTrue(texts.none { '^' in it })
        assertTrue(bank.byId("KIM-SMP-046")!!.stem.contains("10⁻²"))
        assertTrue(bank.byId("FIS-SMA-031")!!.explanation!!.contains("4²"))
        assertTrue(bank.byId("BIO-SMA-045")!!.stem.contains("p² + 2pq + q²"))
    }

    @Test fun integrityCheckRejectsTamperedBank() {
        val bytes = TestBank.assetFile.readBytes()
        val sha = Hashing.sha256Hex(bytes)
        QuestionBankLoader.load(bytes, sha) // ok
        val tampered = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        try {
            QuestionBankLoader.load(tampered, sha)
            throw AssertionError("seharusnya gagal")
        } catch (e: BankLoadException) {
            assertTrue(e.message!!.contains("Integritas"))
        }
    }

    @Test fun gradeAndDifficultyAreLabelsOnly_timeAndPointsStillFollowDifficulty() {
        // Jenjang & kesulitan tidak lagi memfilter pertandingan, tetapi tetap menentukan durasi & poin soal.
        assertTrue(bank.questions.all { it.timeLimitSeconds == it.difficulty.timeLimitSeconds })
        assertTrue(bank.questions.all { it.topic.isNotBlank() })
        assertEquals(Difficulty.entries.toSet(), bank.questions.map { it.difficulty }.toSet())
    }
}
