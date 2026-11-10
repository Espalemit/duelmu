package id.eduduel.app.domain

import id.eduduel.app.TestBank
import id.eduduel.app.domain.game.AsteroidEngine
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.AsteroidSubmission
import id.eduduel.app.domain.game.GameEngines
import id.eduduel.app.domain.game.GameType
import id.eduduel.app.domain.game.LabAction
import id.eduduel.app.domain.game.LabEngine
import id.eduduel.app.domain.game.LabModule
import id.eduduel.app.domain.game.LabSpec
import id.eduduel.app.domain.game.LabSubmission
import id.eduduel.app.domain.game.MicroSubmission
import id.eduduel.app.domain.game.MicroTap
import id.eduduel.app.domain.game.MicroscopeEngine
import id.eduduel.app.domain.game.MicroscopeSpec
import id.eduduel.app.domain.game.ShotInput
import id.eduduel.app.domain.game.TrajectoryEngine
import id.eduduel.app.domain.game.TrajectorySpec
import id.eduduel.app.domain.game.TrajectorySubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Setiap mesin game: menang, kalah, timeout, reset/replay, dan sifat khas per mata pelajaran. */
class GameEngineTest {
    private val games = TestBank.bank.questions.filter { it.isGame }
    private fun q(id: String) = TestBank.bank.byId(id)!!
    private inline fun <reified T> specOf(id: String): T = q(id).game!!.spec as T

    @Test fun everyGame_win_lose_timeout_and_deterministicReplay() {
        assertEquals(80, games.size)
        for (g in games) {
            val body = g.game!!
            // menang: solusi referensi
            val win = Grader.grade(g, GameAnswer(body.reference), elapsedMs = 1_000)
            assertTrue(g.id, win.correct)
            assertEquals(g.id, ScoringEngine.score(g.difficulty, true, 1_000, g.timeLimitMs), win.score)
            // timeout: solusi benar sekalipun = 0 bila waktu habis
            assertEquals(g.id, RoundScore.ZERO, Grader.grade(g, GameAnswer(body.reference), g.timeLimitMs).score)
            // kalah: menyerah / tanpa masukan
            assertFalse(g.id, Grader.grade(g, null, 1_000).correct)
            // replay deterministik & tanpa state (reset = mulai dari nol setiap evaluasi)
            assertEquals(g.id, GameEngines.evaluate(body.spec, body.reference), GameEngines.evaluate(body.spec, body.reference))
            // poin game tidak pernah melebihi poin MCQ dengan kesulitan sama
            assertTrue(g.id, win.score.total <= g.difficulty.basePoints * 3 / 2)
        }
    }

    // ---------------------------------------------------------------- Matematika: Asteroid Solver

    @Test fun asteroid_tokenSequenceMustBeInOrder_distractorsCostLives() {
        for (g in games.filter { it.game!!.gameType == GameType.ASTEROID }) {
            val s = g.game!!.spec as AsteroidSpec
            val n = s.answerTokens.size
            assertTrue(g.id, AsteroidEngine.evaluate(s, AsteroidSubmission((0 until n).toList())).success)
            // urutan terbalik = salah (kecuali token kebetulan sama)
            if (n >= 2 && s.answerTokens.first() != s.answerTokens.last()) {
                assertFalse(g.id, AsteroidEngine.progress(s, (0 until n).reversed().toList()).success)
            }
            // pengecoh: tepat maxWrong kesalahan masih boleh, maxWrong+1 kalah
            val d = n // indeks pengecoh pertama
            val okWithMistakes = List(s.maxWrong) { d } + (0 until n)
            assertTrue(g.id, AsteroidEngine.evaluate(s, AsteroidSubmission(okWithMistakes)).success)
            val tooMany = List(s.maxWrong + 1) { d } + (0 until n)
            val p = AsteroidEngine.progress(s, tooMany)
            assertTrue(g.id, p.failed && !p.success)
            // pengecoh tidak pernah sama dengan jawaban
            assertTrue(g.id, s.distractorTokens.none { it in s.answerTokens })
        }
    }

    @Test fun asteroid_spawnsAreDeterministicPerSeed_andContainEveryRequiredToken() {
        val s = specOf<AsteroidSpec>("MAT-SMA-061")
        val a = AsteroidEngine.spawns(s, seed = 42, count = 30)
        assertEquals(a, AsteroidEngine.spawns(s, 42, 30))
        assertNotEquals(a.map { it.x }, AsteroidEngine.spawns(s, 43, 30).map { it.x })
        val cycle = s.answerTokens.size * 2 + s.distractorTokens.size
        val firstCycle = a.take(cycle).map { it.token }.toSet()
        assertTrue(s.answerTokens.indices.all { it in firstCycle })
        assertTrue(a.all { it.x in 0.05..0.95 })
        // contoh dari spesifikasi: f(x) = x² + 7x -> 2x lalu 7
        assertEquals(listOf("2x", "7"), s.answerTokens)
    }

    // ---------------------------------------------------------------- Fisika: Trajectory Lab

    @Test fun trajectory_referenceHits_radiusTolerance_andComplementaryAnglesBothAccepted() {
        val s = specOf<TrajectorySpec>("FIS-SMA-062") // v = 30 m/s, sasaran 77,94 m
        fun shotAt(deg: Int) = ShotInput(((deg - s.a.min) / s.a.step).toInt(), 0)
        assertTrue(TrajectoryEngine.simulate(s, shotAt(30)).hit)
        assertTrue("sudut berkomplemen 60° juga sah", TrajectoryEngine.simulate(s, shotAt(60)).hit)
        assertFalse(TrajectoryEngine.simulate(s, shotAt(45)).hit)
        // toleransi: sasaran digeser 2r -> tembakan yang sama meleset
        val moved = s.copy(target = s.target.copy(x = s.target.x + 2.5 * s.target.r))
        assertFalse(TrajectoryEngine.simulate(moved, shotAt(30)).hit)
        // jangkauan analitik R = v² sin2θ / g cocok dengan simulasi diskrit (dalam radius sasaran)
        val r = 30.0 * 30.0 * Math.sin(Math.toRadians(60.0)) / 10.0
        assertTrue(abs(r - s.target.x) < s.target.r)
    }

    @Test fun trajectory_obstacleBlocksLowArc_maxShotsAndRangeValidation() {
        val s = specOf<TrajectorySpec>("FIS-SMP-064") // tembok 12 m: 30° terhalang, 60° lolos
        fun shotAt(deg: Int) = ShotInput(((deg - s.a.min) / s.a.step).toInt(), 0)
        assertEquals(TrajectoryEngine.End.OBSTACLE, TrajectoryEngine.simulate(s, shotAt(30)).end)
        assertTrue(TrajectoryEngine.simulate(s, shotAt(60)).hit)
        val misses = List(s.maxShots) { shotAt(30) }
        assertFalse(TrajectoryEngine.evaluate(s, TrajectorySubmission(misses)).success)
        assertTrue(TrajectoryEngine.evaluate(s, TrajectorySubmission(misses.dropLast(1) + shotAt(60))).success)
        // lebih dari maxShots / parameter di luar rentang -> ditolak (tidak crash)
        assertFalse(GameEngines.evaluate(s, TrajectorySubmission(List(s.maxShots + 1) { shotAt(60) })).success)
        assertFalse(GameEngines.evaluate(s, TrajectorySubmission(listOf(ShotInput(9_999, 0)))).success)
    }

    @Test fun trajectory_everyConfigHasSolution_butIsNotTrivial() {
        for (g in games.filter { it.game!!.gameType == GameType.TRAJECTORY }) {
            val s = g.game!!.spec as TrajectorySpec
            var hits = 0
            for (a in 0 until s.a.count) for (b in 0 until s.b.count) if (TrajectoryEngine.simulate(s, ShotInput(a, b)).hit) hits++
            val total = s.a.count * s.b.count
            assertTrue(g.id, hits > 0)
            assertTrue("${g.id}: $hits/$total kombinasi kena", total <= 20 || hits.toDouble() / total <= 0.15)
        }
    }

    // ---------------------------------------------------------------- Biologi: Micro Hunter

    @Test fun microscope_hotspot_focusAndZoomRules_wrongTapsLimited() {
        for (g in games.filter { it.game!!.gameType == GameType.MICROSCOPE }) {
            val s = g.game!!.spec as MicroscopeSpec
            val ref = (g.game!!.reference as MicroSubmission).taps.first()
            assertEquals(g.id, MicroscopeEngine.TapVerdict.HIT, MicroscopeEngine.judge(s, ref, false).verdict)
            // kabur -> tidak dihitung
            val blurry = ref.copy(focus = if (s.focusTarget > 0.5) 0 else 1000)
            assertEquals(g.id, MicroscopeEngine.TapVerdict.BLURRY, MicroscopeEngine.judge(s, blurry, false).verdict)
            // perbesaran kurang -> tidak dihitung
            if (s.minTapZoom > 1.0) {
                assertEquals(g.id, MicroscopeEngine.TapVerdict.ZOOM_TOO_LOW, MicroscopeEngine.judge(s, ref.copy(zoom = 100), false).verdict)
            }
            // tap di pojok dunia (latar) = salah; maxWrong+1 kali = kalah walau lalu benar
            val corner = ref.copy(x = 2, y = 2)
            assertEquals(g.id, MicroscopeEngine.TapVerdict.WRONG, MicroscopeEngine.judge(s, corner, false).verdict)
            assertTrue(g.id, MicroscopeEngine.evaluate(s, MicroSubmission(List(s.maxWrong) { corner } + ref)).success)
            assertFalse(g.id, MicroscopeEngine.evaluate(s, MicroSubmission(List(s.maxWrong + 1) { corner } + ref)).success)
            // tap kabur tidak menghabiskan kesempatan
            assertTrue(g.id, MicroscopeEngine.evaluate(s, MicroSubmission(List(10) { blurry } + ref)).success)
            // target tidak memenuhi preparat (tidak bisa ditebak dengan tap acak)
            assertTrue(g.id, s.regions.count { it.target } >= 1 && s.regions.any { !it.target })
        }
    }

    @Test fun microscope_assistEnlargesTolerance_andWorldCoordinatesAreResolutionIndependent() {
        val s = specOf<MicroscopeSpec>("BIO-SMA-070") // nodus Ranvier: objek kecil
        val node = s.regions.first { it.target }.polygons.first()
        val maxX = node.maxOf { it.x }
        val midY = node.map { it.y }.average()
        val justOutside = MicroTap((maxX + s.tapTolerance + 3).toInt(), midY.toInt(), (s.minTapZoom * 100).toInt(), (s.focusTarget * 1000).toInt())
        assertFalse(MicroscopeEngine.judge(s, justOutside, assist = false).verdict == MicroscopeEngine.TapVerdict.HIT)
        assertEquals(MicroscopeEngine.TapVerdict.HIT, MicroscopeEngine.judge(s, justOutside, assist = true).verdict)
        // titik layar relatif yang sama -> titik dunia yang sama pada HP 1080 px dan 720 px
        val a = MicroscopeEngine.screenToWorld(0.3 * 1080, 0.6 * 1080, 1080.0, 1080.0, 420.0, 300.0, 2.5, s.worldW)
        val b = MicroscopeEngine.screenToWorld(0.3 * 720, 0.6 * 720, 720.0, 720.0, 420.0, 300.0, 2.5, s.worldW)
        assertEquals(a.x, b.x, 1e-9)
        assertEquals(a.y, b.y, 1e-9)
        val back = MicroscopeEngine.worldToScreen(a.x, a.y, 720.0, 720.0, 420.0, 300.0, 2.5, s.worldW)
        assertEquals(0.3 * 720, back.x, 1e-9)
    }

    @Test fun microscope_illustrationsAreDistinct() {
        val scenes = games.filter { it.game!!.gameType == GameType.MICROSCOPE }.map { (it.game!!.spec as MicroscopeSpec).shapes }
        assertEquals(20, scenes.size)
        assertEquals("setiap soal memakai ilustrasi berbeda", 20, scenes.toSet().size)
    }

    // ---------------------------------------------------------------- Kimia: Reaction Lab

    @Test fun lab_referencePasses_offByOneStepFails_resetAndLimits() {
        for (g in games.filter { it.game!!.gameType == GameType.LAB }) {
            val s = g.game!!.spec as LabSpec
            val ref = g.game!!.reference as LabSubmission
            assertTrue(g.id, LabEngine.evaluate(s, ref).success)
            // RESET membuang aksi sebelumnya: sampah + RESET + referensi tetap berhasil
            val junk = if (s.module == LabModule.SEPARATION) LabAction(0) else LabAction(0, (s.reagents[0].step * 1000).toInt())
            assertTrue(g.id, LabEngine.evaluate(s, LabSubmission(listOf(junk, LabAction(LabAction.RESET)) + ref.actions)).success)
            // terlalu banyak RESET -> masukan ditolak
            val resets = List(s.maxResets + 1) { LabAction(LabAction.RESET) }
            assertFalse(g.id, GameEngines.evaluate(s, LabSubmission(resets + ref.actions)).success)
            if (s.module != LabModule.SEPARATION) {
                // bukan kelipatan langkah reagen -> ditolak
                val first = ref.actions.first()
                assertFalse(g.id, GameEngines.evaluate(s, LabSubmission(listOf(first.copy(amt = first.amt + 1)) + ref.actions.drop(1))).success)
            } else {
                // urutan dibalik salah (untuk pemisahan bertahap)
                if (ref.actions.size > 1) assertFalse(g.id, LabEngine.evaluate(s, LabSubmission(ref.actions.reversed())).success)
            }
        }
        // satu langkah buret lebih/kurang pada titrasi ±0,1 mL masih toleransi; dua langkah tidak
        val t = specOf<LabSpec>("KIM-SMA-063")
        assertTrue(LabEngine.evaluate(t, LabSubmission(listOf(LabAction(0, 25_100)))).success)
        assertFalse(LabEngine.evaluate(t, LabSubmission(listOf(LabAction(0, 25_300)))).success)
    }

    @Test fun lab_chemistryIsCorrect_pH_stoichiometry_massBalance() {
        // pH titik ekuivalen asam kuat-basa kuat = 7,00; sebelum ekuivalen asam
        val n = specOf<LabSpec>("KIM-SMP-067")
        val eq = LabEngine.replay(n, listOf(LabAction(0, 20_000)))
        assertEquals(7.0, LabEngine.ph(eq)!!, 1e-6)
        assertTrue(LabEngine.ph(LabEngine.replay(n, listOf(LabAction(0, 19_500))))!! < 3.5)
        assertTrue(LabEngine.ph(LabEngine.replay(n, listOf(LabAction(0, 20_500))))!! > 10.5)
        // pH 2: V = 40,9 mL (perhitungan manual)
        val p2 = specOf<LabSpec>("KIM-SMA-069")
        assertEquals(2.0, LabEngine.ph(LabEngine.replay(p2, listOf(LabAction(0, 40_900))))!!, 0.01)
        // Mg + 2HCl: kekekalan massa (massa pereaksi yang bereaksi = massa produk)
        val mg = specOf<LabSpec>("KIM-SMA-067")
        val before = LabEngine.replay(mg, listOf(LabAction(0, 1_200)))
        val after = LabEngine.react(mg, before)
        val M = mapOf("Mg" to 24.0, "HCl" to 36.5, "MgCl2" to 95.0, "H2" to 2.0)
        fun mass(st: LabEngine.LabState) = M.entries.sumOf { (sp, mm) -> st.amount(sp) * mm }
        assertEquals(mass(before), mass(after), 1e-9)
        assertEquals(0.05, after.amount("H2"), 1e-9)
        // N₂ + 3H₂ -> 2NH₃ dengan H₂ pembatas
        val nh3 = specOf<LabSpec>("KIM-SMA-066")
        val r = LabEngine.react(nh3, LabEngine.replay(nh3, listOf(LabAction(0, 4_500))))
        assertEquals(3.0, r.amount("NH3"), 1e-9)
        assertEquals(0.5, r.amount("N2"), 1e-9)
        assertEquals(0.0, r.amount("H2"), 1e-9)
        // pengenceran M1V1 = M2V2
        val dil = specOf<LabSpec>("KIM-SMA-062")
        val st = LabEngine.replay(dil, listOf(LabAction(0, 25_000), LabAction(1, 75_000)))
        assertEquals(0.5, LabEngine.concentration(st, "HCl", "M")!!, 1e-9)
        // bejana tidak bisa melebihi kapasitas
        assertFalse(GameEngines.evaluate(dil, LabSubmission(listOf(LabAction(1, 150_000), LabAction(1, 150_000)))).success)
    }
}
