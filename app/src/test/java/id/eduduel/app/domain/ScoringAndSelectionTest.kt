package id.eduduel.app.domain

import id.eduduel.app.TestBank
import id.eduduel.app.core.MathText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    @Test fun wrongOrTimeoutScoresZero() {
        assertEquals(0, ScoringEngine.score(Difficulty.SULIT, correct = false, elapsedMs = 1, totalMs = 120_000).total)
        assertEquals(0, ScoringEngine.score(Difficulty.MUDAH, correct = true, elapsedMs = 60_000, totalMs = 60_000).speedBonus)
    }

    @Test fun basePointsPerDifficulty() {
        assertEquals(100, Difficulty.MUDAH.basePoints)
        assertEquals(200, Difficulty.SEDANG.basePoints)
        assertEquals(300, Difficulty.SULIT.basePoints)
        assertEquals(60, Difficulty.MUDAH.timeLimitSeconds)
        assertEquals(90, Difficulty.SEDANG.timeLimitSeconds)
        assertEquals(120, Difficulty.SULIT.timeLimitSeconds)
    }

    @Test fun speedBonusFormulaIsFloorAndClamped() {
        // instan: bonus = base/2
        assertEquals(RoundScore(100, 50), ScoringEngine.score(Difficulty.MUDAH, true, 0, 60_000))
        // separuh waktu: floor(0.5*200*45000/90000) = 50
        assertEquals(RoundScore(200, 50), ScoringEngine.score(Difficulty.SEDANG, true, 45_000, 90_000))
        // floor: 0.5*300*119999/120000 = 149.99875 -> 149
        assertEquals(149, ScoringEngine.score(Difficulty.SULIT, true, 1, 120_000).speedBonus)
        // elapsed negatif / melebihi -> dibatasi
        assertEquals(50, ScoringEngine.score(Difficulty.MUDAH, true, -500, 60_000).speedBonus)
        assertEquals(0, ScoringEngine.score(Difficulty.MUDAH, true, 99_999, 60_000).speedBonus)
        // nilai sembarang = rumus referensi
        for (elapsed in listOf(0L, 1L, 333L, 12_345L, 59_999L)) {
            val expected = kotlin.math.floor(0.5 * 100 * (60_000 - elapsed) / 60_000).toInt()
            assertEquals(expected, ScoringEngine.speedBonus(100, 60_000 - elapsed, 60_000))
        }
    }

    @Test fun outcomeHighestWins_tieIsDraw() {
        assertEquals(MatchOutcome.WIN, ScoringEngine.outcome(10, 9))
        assertEquals(MatchOutcome.LOSE, ScoringEngine.outcome(9, 10))
        assertEquals(MatchOutcome.DRAW, ScoringEngine.outcome(10, 10))
    }
}

class QuestionSelectorTest {
    private val bank = TestBank.bank

    @Test fun selectionIsDeterministicPerSeed_noDuplicates_validPermutations() {
        val pool = bank.pool(MatchConfig(Subject.FISIKA))
        val a = QuestionSelector.select(pool, 15, seed = 7)
        val b = QuestionSelector.select(pool, 15, seed = 7)
        val c = QuestionSelector.select(pool, 15, seed = 8)
        assertEquals(a.map { it.question.id to it.optionOrder }, b.map { it.question.id to it.optionOrder })
        assertNotEquals(a.map { it.question.id }, c.map { it.question.id })
        assertEquals(15, a.map { it.question.id }.toSet().size)
        assertTrue(a.all { it.optionOrder.toSet() == OptionId.entries.toSet() })
    }

    @Test fun avoidIdsPrefersFreshQuestions() {
        val pool = bank.pool(MatchConfig(Subject.KIMIA))
        val first = QuestionSelector.select(pool, 10, 1).map { it.question.id }.toSet()
        val second = QuestionSelector.select(pool, 10, 2, avoidIds = first).map { it.question.id }.toSet()
        assertTrue(first.intersect(second).isEmpty())
    }

    @Test fun everyMatch_isTenRandomQuestionsFromChosenSubjectOnly() {
        for (subject in Subject.entries) {
            val pool = bank.pool(MatchConfig(subject))
            assertEquals(100, pool.size)
            // Jenjang & kesulitan bukan filter: semua tercampur dalam pool mapel.
            assertEquals(GradeBand.entries.toSet(), pool.map { it.gradeBand }.toSet())
            assertEquals(Difficulty.entries.toSet(), pool.map { it.difficulty }.toSet())
            val sets = (1L..5L).map { seed -> QuestionSelector.select(pool, MatchConfig.QUESTIONS_PER_MATCH, seed) }
            sets.forEach { s ->
                assertEquals(10, s.size)
                assertEquals(10, s.map { it.question.id }.toSet().size)
                assertTrue(s.all { it.question.subject == subject })
            }
            assertTrue("seed berbeda harus menghasilkan set soal berbeda", sets.map { s -> s.map { it.question.id }.toSet() }.toSet().size > 1)
        }
        assertEquals(400, bank.pool(MatchConfig(null)).size)
        assertEquals(10, MatchConfig(Subject.FISIKA).questionCount)
    }
}

class SoloEngineTest {
    private val bank = TestBank.bank
    private val rounds = QuestionSelector.select(bank.pool(MatchConfig(Subject.MATEMATIKA)), 5, 3)

    @Test fun answerIsLocked_scoring_andTimeout() {
        var s = SoloEngine.start(MatchConfig.INITIAL, rounds, now = 1_000)
        val q0 = s.current.question
        s = SoloEngine.answer(s, q0.correctOptionId, now = 1_000 + q0.timeLimitMs / 2)
        assertTrue(s.isLocked)
        val locked = s
        s = SoloEngine.answer(s, OptionId.entries.first { it != q0.correctOptionId }, now = 2_000_000)
        assertEquals(locked, s) // submit ganda diabaikan
        assertEquals(q0.difficulty.basePoints + q0.difficulty.basePoints / 4, s.totalScore)

        s = SoloEngine.next(s, now = 100_000)
        val q1 = s.current.question
        // belum habis -> timeout tidak dicatat
        assertEquals(s, SoloEngine.timeout(s, now = 100_000 + q1.timeLimitMs - 1))
        s = SoloEngine.timeout(s, now = 100_000 + q1.timeLimitMs)
        assertTrue(s.currentAnswer!!.timedOut)
        assertEquals(0, s.currentAnswer!!.score.total)
    }

    @Test fun timerSurvivesRecreation_viaSnapshot() {
        var s = SoloEngine.start(MatchConfig.INITIAL, rounds, now = 50_000)
        s = SoloEngine.answer(s, s.current.question.correctOptionId, 51_000)
        s = SoloEngine.next(s, 60_000)
        val restored = SoloEngine.restore(SoloEngine.snapshot(s), bank)!!
        assertEquals(s.index, restored.index)
        assertEquals(s.startedAtMs, restored.startedAtMs) // waktu mulai tidak di-reset
        assertEquals(s.totalScore, restored.totalScore)
        assertEquals(s.rounds.map { it.optionOrder }, restored.rounds.map { it.optionOrder })
        assertEquals(s.remainingMs(70_000), restored.remainingMs(70_000))
    }

    @Test fun finishesAfterLastQuestion() {
        var s = SoloEngine.start(MatchConfig.INITIAL, rounds, 0)
        repeat(5) { i ->
            s = SoloEngine.answer(s, s.current.question.correctOptionId, i * 1_000L)
            s = SoloEngine.next(s, i * 1_000L)
        }
        assertTrue(s.finished)
        assertEquals(5, s.correctCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateQuestionsRejected() {
        SoloEngine.start(MatchConfig(), listOf(rounds[0], rounds[0]), 0)
    }
}

class MathTextTest {
    @Test fun caretToSuperscript() {
        assertEquals("10⁻²", MathText.format("10^(-2)"))
        assertEquals("x²", MathText.format("x^2"))
        assertEquals("3ˣ⁺¹ = 81", MathText.format("3^(x+1) = 81"))
        assertEquals("tanpa pangkat", MathText.format("tanpa pangkat"))
        assertEquals("a^(qq)", MathText.format("a^(qq)")) // tidak bisa -> dibiarkan
    }
}
