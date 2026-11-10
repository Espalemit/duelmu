package id.eduduel.app.data

import id.eduduel.app.TestBank
import id.eduduel.app.core.Hashing
import id.eduduel.app.data.bank.BankLoadException
import id.eduduel.app.data.bank.QuestionBankLoader
import id.eduduel.app.domain.Difficulty
import id.eduduel.app.domain.GradeBand
import id.eduduel.app.domain.OptionId
import id.eduduel.app.domain.QuestionType
import id.eduduel.app.domain.Subject
import id.eduduel.app.domain.game.GameEngines
import id.eduduel.app.domain.game.GameType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Validasi dataset 560 soal yang dipaketkan di APK (asset hasil tools/build_question_asset.py). */
class QuestionBankDatasetTest {
    private val bank = TestBank.bank
    private val classic = bank.questions.filter { it.type == QuestionType.CLASSIC_MCQ }
    private val visual = bank.questions.filter { it.type == QuestionType.VISUAL_MCQ }
    private val games = bank.questions.filter { it.type == QuestionType.MINI_GAME }

    @Test fun exactly560Questions_140PerSubject_70PerGrade_400_80_80() {
        assertEquals(560, bank.questions.size)
        assertEquals(400, classic.size)
        assertEquals(80, visual.size)
        assertEquals(80, games.size)
        for (s in Subject.entries) {
            assertEquals(s.label, 140, bank.questions.count { it.subject == s })
            assertEquals(s.label, 100, classic.count { it.subject == s })
            assertEquals(s.label, 20, visual.count { it.subject == s })
            assertEquals(s.label, 20, games.count { it.subject == s })
            for (g in GradeBand.entries) {
                assertEquals("${s.label} $g", 70, bank.questions.count { it.subject == s && it.gradeBand == g })
                assertEquals("${s.label} $g klasik", 50, classic.count { it.subject == s && it.gradeBand == g })
                assertEquals("${s.label} $g visual", 10, visual.count { it.subject == s && it.gradeBand == g })
                assertEquals("${s.label} $g game", 10, games.count { it.subject == s && it.gradeBand == g })
            }
        }
    }

    @Test fun uniqueIds_consistentWithSubjectGradeAndTypeRange() {
        assertEquals(560, bank.questions.map { it.id }.toSet().size)
        for (q in bank.questions) {
            assertTrue(q.id, q.id.startsWith("${q.subject.idPrefix}-${q.gradeBand.name}-"))
            assertEquals(q, bank.byId(q.id))
            val n = q.id.takeLast(3).toInt()
            val range = when (q.type) {
                QuestionType.CLASSIC_MCQ -> 1..50
                QuestionType.VISUAL_MCQ -> 51..60
                QuestionType.MINI_GAME -> 61..70
            }
            assertTrue("${q.id} di luar rentang ${q.type}", n in range)
        }
    }

    /** 400 soal lama tidak berubah: sidik jari kanonik == asset v1 (2026.11.07-r1) di git. */
    @Test fun classic400_identicalToOriginalV1Asset() {
        val canon = classic.sortedBy { it.id }.joinToString("\n") { q ->
            listOf(
                q.id, q.subject.label, q.gradeBand.name, q.topic, q.difficulty.key, q.timeLimitSeconds.toString(), q.stem,
                q.options.joinToString(";") { "${it.id.name}=${it.text}" }, q.correctOptionId!!.name, q.explanation ?: "",
            ).joinToString("|")
        }
        assertEquals("0ed470c23bd08f3c9b9d7c20b71b81c6b6c989dc83654d7359f2808f37ce14d3", Hashing.sha256Hex(canon))
    }

    @Test fun mcq_fourUniqueOptions_exactlyOneCorrect() {
        for (q in classic + visual) {
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

    @Test fun newBatches_difficultyDistribution_6_8_6_perSubject() {
        for (batch in listOf(visual, games)) for (s in Subject.entries) {
            val ofSubject = batch.filter { it.subject == s }
            assertEquals("${s.label} ${batch.first().type}", listOf(6, 8, 6), Difficulty.entries.map { d -> ofSubject.count { it.difficulty == d } })
        }
    }

    @Test fun explanationsOptional_neverBlank_andPresentForCalculations() {
        assertTrue(bank.questions.all { q -> q.explanation?.isNotBlank() ?: true })
        assertEquals(150, classic.count { it.explanation != null })
        assertTrue(classic.any { it.explanation == null })
        // Setiap soal baru (bergambar & game) WAJIB berpembahasan.
        assertTrue((visual + games).all { it.explanation != null })
    }

    @Test fun mathIsRenderedWithUnicode_noCaretLeft() {
        val texts = (classic + visual).flatMap { q -> listOf(q.stem, q.explanation.orEmpty()) + q.options.map { it.text } }
        assertTrue(texts.filter { '^' in it }.toString(), texts.none { '^' in it })
        assertTrue(bank.byId("KIM-SMP-046")!!.stem.contains("10⁻²"))
        assertTrue(bank.byId("FIS-SMA-031")!!.explanation!!.contains("4²"))
        assertTrue(bank.byId("BIO-SMA-045")!!.stem.contains("p² + 2pq + q²"))
        assertTrue(bank.byId("KIM-SMP-057")!!.explanation!!.contains("ZnCl₂"))
    }

    /** 80 soal bergambar: ID/kunci/opsi == sumber ZIP (content/visual), gambar valid & berpasangan. */
    @Test fun visual80_matchSourcePack_andImagesDecodeWithCorrectPairing() {
        val src = Json.parseToJsonElement(TestBank.file("content/visual/questions_extra_80.json").readText()).jsonObject["questions"]!!.jsonArray
        assertEquals(80, src.size)
        val assets = TestBank.assetFile.parentFile!!
        for (e in src) {
            val o = e.jsonObject
            val id = o["id"]!!.jsonPrimitive.content
            val q = bank.byId(id)
            assertNotNull(id, q)
            q!!
            assertEquals(id, QuestionType.VISUAL_MCQ, q.type)
            assertEquals(id, o["correctOptionId"]!!.jsonPrimitive.content, q.correctOptionId!!.name)
            assertEquals(id, o["difficulty"]!!.jsonPrimitive.content, q.difficulty.key)
            assertEquals(id, o["timeLimitSeconds"]!!.jsonPrimitive.int, q.timeLimitSeconds)
            assertEquals(id, "images/$id.png", q.imageAsset)
            // Gambar di APK identik byte-per-byte dengan gambar sumber untuk ID yang SAMA (tidak tertukar).
            val apkPng = File(assets, q.imageAsset!!)
            val srcPng = TestBank.file("content/visual/${q.imageAsset}")
            assertTrue(id, apkPng.readBytes().contentEquals(srcPng.readBytes()))
            val img = ImageIO.read(apkPng)
            assertNotNull("$id tidak dapat didekode", img)
            assertEquals(id, 1250, img.width)
            assertEquals(id, 600, img.height)
        }
        // Tidak ada dua soal yang memakai gambar identik.
        assertEquals(80, visual.map { Hashing.sha256Hex(File(assets, it.imageAsset!!).readBytes()) }.toSet().size)
    }

    @Test fun assetManifestVerification_detectsMissingOrCorruptImages() {
        val bytes = TestBank.assetFile.readBytes()
        val dir = TestBank.assetFile.parentFile!!
        QuestionBankLoader.load(bytes, null) { File(dir, it).takeIf(File::exists)?.readBytes() } // utuh: ok
        val missing = runCatching { QuestionBankLoader.load(bytes, null) { p -> if (p.endsWith("FIS-SMP-051.png")) null else File(dir, p).readBytes() } }
        assertTrue(missing.exceptionOrNull() is BankLoadException)
        assertTrue(missing.exceptionOrNull()!!.message!!.contains("hilang"))
        val corrupt = runCatching {
            QuestionBankLoader.load(bytes, null) { p -> File(dir, p).readBytes().also { if (p.endsWith("MAT-SMA-060.png")) it[100] = (it[100] + 1).toByte() } }
        }
        assertTrue(corrupt.exceptionOrNull()!!.message!!.contains("rusak"))
    }

    /** 80 soal game: dapat diparse, jenis game sesuai mapel, solusi referensi menyelesaikan game. */
    @Test fun game80_parse_typeMatchesSubject_referenceSolves() {
        for (q in games) {
            val g = q.game!!
            assertEquals(q.id, GameType.forSubject(q.subject), g.gameType)
            assertTrue(q.id, GameEngines.isWellFormed(g.spec, g.reference))
            assertTrue(q.id, GameEngines.evaluate(g.spec, g.reference).success)
            assertTrue(q.id, g.solutionText.isNotBlank())
            assertTrue(q.id, q.options.isEmpty() && q.correctOptionId == null)
        }
        // Instruksi tidak boleh duplikat persis dengan soal mana pun.
        val stems = bank.questions.map { it.stem.lowercase().replace(Regex("\\s+"), " ").trim() }
        assertEquals(stems.size, stems.toSet().size)
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

    /** ignoreUnknownKeys TIDAK aktif: field asing (mis. konfigurasi dari versi lain) membuat pemuatan gagal. */
    @Test fun strictDecoder_unknownFieldIsRejected_notSilentlyIgnored() {
        val text = TestBank.assetFile.readText().replaceFirst("\"imageAsset\":", "\"imageAssetV9\":\"x\",\"imageAsset\":")
        val r = runCatching { QuestionBankLoader.load(text.toByteArray(), null) }
        assertTrue(r.exceptionOrNull() is BankLoadException)
        // Schema 1 lama (400 soal) tetap bisa dimuat (kompatibilitas mundur loader).
        val v1 = """{"schemaVersion":1,"bankVersionName":"x","sourceFile":"a","sourceSha256":"b","questionCount":1,"questions":[""" +
            """{"id":"FIS-SMP-001","subject":"Fisika","gradeBand":"SMP","topic":"t","difficulty":"mudah","timeLimitSeconds":60,"stem":"s",""" +
            """"options":[{"id":"A","text":"1"},{"id":"B","text":"2"},{"id":"C","text":"3"},{"id":"D","text":"4"}],"correctOptionId":"A","correctAnswer":"1","explanation":null}]}"""
        assertEquals(1, QuestionBankLoader.load(v1.toByteArray(), null).questions.size)
        val v1Game = v1.replace("\"id\":\"FIS-SMP-001\",", "\"id\":\"FIS-SMP-001\",\"questionType\":\"mini_game\",")
        assertFalse(runCatching { QuestionBankLoader.load(v1Game.toByteArray(), null) }.isSuccess)
    }

    @Test fun gradeAndDifficultyAreLabelsOnly_timeAndPointsStillFollowDifficulty() {
        assertTrue(bank.questions.all { it.timeLimitSeconds == it.difficulty.timeLimitSeconds })
        assertTrue(bank.questions.all { it.topic.isNotBlank() })
        assertEquals(Difficulty.entries.toSet(), bank.questions.map { it.difficulty }.toSet())
    }
}
