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
        val pool = bank.pool(MatchConfig(Subject.FISIKA, QuestionMode.CLASSIC))
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
            assertEquals(140, pool.size)
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
        assertEquals(560, bank.pool(MatchConfig(null)).size)
        assertEquals(10, MatchConfig(Subject.FISIKA).questionCount)
    }
}

class SoloEngineTest {
    private val bank = TestBank.bank
    private val rounds = QuestionSelector.select(bank.pool(MatchConfig(Subject.MATEMATIKA, QuestionMode.CLASSIC)), 5, 3)

    @Test fun answerIsLocked_scoring_andTimeout() {
        var s = SoloEngine.start(MatchConfig.INITIAL, rounds, now = 1_000)
        val q0 = s.current.question
        s = SoloEngine.answer(s, q0.correctOptionId!!, now = 1_000 + q0.timeLimitMs / 2)
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
        s = SoloEngine.answer(s, s.current.question.correctOptionId!!, 51_000)
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
            s = SoloEngine.answer(s, s.current.question.correctOptionId!!, i * 1_000L)
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

/** Mode Klasik / Bergambar / Mini-Game / Campuran pada 560 soal. */
class ModeSelectionTest {
    private val bank = TestBank.bank

    @Test fun defaultModeIsMixed() {
        assertEquals(QuestionMode.MIXED, MatchConfig(Subject.FISIKA).mode)
        assertEquals(GhostMode.LIVE, MatchConfig().ghostMode)
        assertEquals(560, bank.pool(MatchConfig()).size)
        assertEquals(QuestionType.entries.toSet(), bank.pool(MatchConfig()).map { it.type }.toSet())
    }

    @Test fun poolSizesPerMode() {
        for (s in Subject.entries) {
            assertEquals(100, bank.pool(MatchConfig(s, QuestionMode.CLASSIC)).size)
            assertEquals(20, bank.pool(MatchConfig(s, QuestionMode.VISUAL)).size)
            assertEquals(20, bank.pool(MatchConfig(s, QuestionMode.GAME)).size)
            assertEquals(140, bank.pool(MatchConfig(s, QuestionMode.MIXED)).size)
            // Mini-game mapel ini selalu mengarah ke gameplay khasnya.
            assertEquals(setOf(id.eduduel.app.domain.game.GameType.forSubject(s)), bank.pool(MatchConfig(s, QuestionMode.GAME)).map { it.game!!.gameType }.toSet())
        }
        assertEquals(80, bank.pool(MatchConfig(null, QuestionMode.VISUAL)).size)
        assertEquals(80, bank.pool(MatchConfig(null, QuestionMode.GAME)).size)
        assertEquals(560, bank.pool(MatchConfig(null, QuestionMode.MIXED)).size)
    }

    @Test fun gameRounds_haveSeed_andNoOptionOrder_mcqRoundsKeepPermutation() {
        val rounds = QuestionSelector.select(bank.pool(MatchConfig(Subject.FISIKA, QuestionMode.GAME)), 10, seed = 9)
        assertEquals(10, rounds.size)
        assertTrue(rounds.all { it.question.isGame && it.optionOrder.isEmpty() })
        assertTrue(rounds.map { it.seed }.toSet().size > 1)
        assertEquals(rounds, QuestionSelector.select(bank.pool(MatchConfig(Subject.FISIKA, QuestionMode.GAME)), 10, seed = 9))
        val vis = QuestionSelector.select(bank.pool(MatchConfig(Subject.BIOLOGI, QuestionMode.VISUAL)), 10, seed = 9)
        assertTrue(vis.all { it.question.imageAsset != null && it.optionOrder.toSet() == OptionId.entries.toSet() })
    }

    @Test fun mixedMode_isStratified_6classic_3visual_1game() {
        assertEquals(mapOf(QuestionType.CLASSIC_MCQ to 6, QuestionType.VISUAL_MCQ to 3, QuestionType.MINI_GAME to 1), QuestionSelector.mixedQuota(10))
        for (seed in 1L..20L) for (subject in Subject.entries + listOf(null)) for (mode in QuestionMode.entries) {
            val rounds = QuestionSelector.forConfig(bank, MatchConfig(subject, mode), seed)
            val byType = rounds.groupingBy { it.question.type }.eachCount()
            assertEquals(6, byType[QuestionType.CLASSIC_MCQ])
            assertEquals(3, byType[QuestionType.VISUAL_MCQ])
            assertEquals(1, byType[QuestionType.MINI_GAME])
            assertEquals(10, rounds.map { it.question.id }.toSet().size)
            assertTrue(subject == null || rounds.all { it.question.subject == subject })
        }
    }

    @Test fun describeIncludesMode() {
        assertEquals("Fisika · Mini-Game · 10 soal", MatchConfig(Subject.FISIKA, QuestionMode.GAME).describe())
    }
}

/** Solo dengan soal game: tutorial menahan timer, skor game = rumus kuis, snapshot bertahan. */
class SoloGameTest {
    private val bank = TestBank.bank
    private val rounds = QuestionSelector.select(bank.pool(MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME)), 10, seed = 5)

    @Test fun tutorialHoldsTimer_thenScoringUsesSameFormula() {
        var s = SoloEngine.start(MatchConfig(Subject.MATEMATIKA, QuestionMode.GAME), rounds, now = 0)
        assertTrue(s.awaitingStart)
        val q = s.current.question
        // selama tutorial: tidak bisa menjawab dan tidak timeout walau lama
        assertEquals(s, SoloEngine.timeout(s, now = 10_000_000))
        assertEquals(s, SoloEngine.answerGame(s, q.game!!.reference, now = 5))
        assertEquals(q.timeLimitMs, s.remainingMs(99_999_999))
        s = SoloEngine.begin(s, now = 50_000)
        assertFalse(s.awaitingStart)
        s = SoloEngine.answerGame(s, q.game!!.reference, now = 50_000 + q.timeLimitMs / 2)
        assertTrue(s.currentAnswer!!.correct)
        assertEquals(q.difficulty.basePoints + q.difficulty.basePoints / 4, s.totalScore)
        assertTrue(s.currentAnswer!!.detail!!.isNotBlank())
        // soal berikutnya juga game -> kembali ke tutorial
        s = SoloEngine.next(s, now = 200_000)
        assertTrue(s.awaitingStart)
        // menyerah = 0 poin, tetap terkunci
        s = SoloEngine.begin(s, 200_000)
        s = SoloEngine.answerGame(s, null, 201_000)
        assertTrue(s.isLocked)
        assertEquals(0, s.currentAnswer!!.score.total)
        // snapshot memulihkan submisi & skor
        val restored = SoloEngine.restore(SoloEngine.snapshot(s), bank)!!
        assertEquals(s.totalScore, restored.totalScore)
        assertEquals(s.rounds.map { it.seed }, restored.rounds.map { it.seed })
        assertEquals(s.answers.map { it.correct }, restored.answers.map { it.correct })
    }

    @Test fun mcqAnswerCannotBeUsedOnGameQuestion() {
        var s = SoloEngine.begin(SoloEngine.start(MatchConfig(), rounds, 0), 0)
        s = SoloEngine.answer(s, OptionId.A, 1_000)
        assertFalse(s.isLocked)
    }
}
