package id.eduduel.app.domain

import id.eduduel.app.domain.game.GameEngines
import id.eduduel.app.domain.game.GameSubmission

/** Jawaban satu ronde: pilihan A-D atau submisi mini-game. */
sealed interface RoundAnswer

data class ChoiceAnswer(val optionId: OptionId) : RoundAnswer

data class GameAnswer(val submission: GameSubmission) : RoundAnswer

data class Grade(val correct: Boolean, val score: RoundScore, val detail: String?)

/**
 * Penilaian SATU-SATUNYA untuk semua jenis soal (Solo, Host, verifikasi Tamu).
 * Rumus skor sama untuk MCQ dan game: benar/berhasil = poin dasar kesulitan + bonus cepat
 * (maks. 50%); salah/gagal/timeout = 0. Skor game karenanya tidak pernah melebihi skor MCQ
 * dengan kesulitan yang sama.
 */
object Grader {
    fun grade(q: Question, answer: RoundAnswer?, elapsedMs: Long): Grade {
        if (answer == null || elapsedMs >= q.timeLimitMs) return Grade(false, RoundScore.ZERO, null)
        return when (val body = q.body) {
            is McqBody -> {
                val correct = (answer as? ChoiceAnswer)?.optionId == body.correctOptionId
                Grade(correct, ScoringEngine.score(q.difficulty, correct, elapsedMs, q.timeLimitMs), null)
            }
            is GameBody -> {
                val sub = (answer as? GameAnswer)?.submission
                val outcome = GameEngines.evaluate(body.spec, sub)
                Grade(outcome.success, ScoringEngine.score(q.difficulty, outcome.success, elapsedMs, q.timeLimitMs), outcome.detail)
            }
        }
    }
}
