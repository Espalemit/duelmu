package id.eduduel.app.domain

import id.eduduel.app.domain.game.GameSubmission
import kotlinx.serialization.Serializable

data class SoloAnswer(
    val questionId: String,
    val optionId: OptionId?,
    val elapsedMs: Long,
    val timedOut: Boolean,
    val correct: Boolean,
    val score: RoundScore,
    /** Submisi mini-game (null untuk MCQ/timeout). */
    val game: GameSubmission? = null,
    /** Ringkasan hasil evaluasi game. */
    val detail: String? = null,
)

/**
 * State latihan solo yang murni (immutable). Waktu mulai soal disimpan sebagai nilai jam
 * monotonic sehingga recomposition/rotasi tidak me-reset timer; waktu tetap berjalan di background.
 *
 * Soal mini-game diawali tutorial interaksi ([awaitingStart] = true): waktu BELUM berjalan sampai
 * pemain menekan Mulai/Lewati atau tutorial selesai ([SoloEngine.begin]).
 */
data class SoloState(
    val config: MatchConfig,
    val rounds: List<RoundQuestion>,
    val index: Int,
    val startedAtMs: Long,
    val answers: List<SoloAnswer>,
    val finished: Boolean,
    val awaitingStart: Boolean = false,
) {
    val current: RoundQuestion get() = rounds[index]
    val currentAnswer: SoloAnswer? get() = answers.getOrNull(index)
    val isLocked: Boolean get() = currentAnswer != null
    val totalScore: Int get() = answers.sumOf { it.score.total }
    val correctCount: Int get() = answers.count { it.correct }
    val isLast: Boolean get() = index == rounds.lastIndex

    fun remainingMs(now: Long): Long =
        if (awaitingStart) current.question.timeLimitMs
        else ScoringEngine.remainingMs(current.question.timeLimitMs, now - startedAtMs)
}

object SoloEngine {

    fun start(config: MatchConfig, rounds: List<RoundQuestion>, now: Long): SoloState {
        require(rounds.isNotEmpty())
        require(rounds.map { it.question.id }.toSet().size == rounds.size) { "Soal duplikat dalam satu sesi" }
        return SoloState(config, rounds, 0, now, emptyList(), false, awaitingStart = rounds[0].question.isGame)
    }

    /** Tutorial selesai/dilewati: mulai hitung waktu soal game. */
    fun begin(state: SoloState, now: Long): SoloState =
        if (!state.awaitingStart || state.finished) state else state.copy(awaitingStart = false, startedAtMs = now)

    /** Kunci jawaban. Diabaikan bila sudah terkunci (tidak boleh submit ganda) atau waktu habis. */
    fun answer(state: SoloState, option: OptionId, now: Long): SoloState =
        submit(state, ChoiceAnswer(option), now)

    fun answerGame(state: SoloState, submission: GameSubmission?, now: Long): SoloState {
        if (submission == null) return giveUp(state, now)
        return submit(state, GameAnswer(submission), now)
    }

    private fun submit(state: SoloState, answer: RoundAnswer, now: Long): SoloState {
        if (state.finished || state.isLocked || state.awaitingStart) return state
        val q = state.current.question
        if ((answer is GameAnswer) != q.isGame) return state
        val elapsed = (now - state.startedAtMs).coerceAtLeast(0)
        if (elapsed >= q.timeLimitMs) return timeout(state, now)
        val g = Grader.grade(q, answer, elapsed)
        return state.copy(
            answers = state.answers + SoloAnswer(
                q.id, (answer as? ChoiceAnswer)?.optionId, elapsed, false, g.correct, g.score,
                (answer as? GameAnswer)?.submission, g.detail,
            ),
        )
    }

    /** Game gagal/menyerah sebelum waktu habis: 0 poin, tidak menunggu timer. */
    private fun giveUp(state: SoloState, now: Long): SoloState {
        if (state.finished || state.isLocked || state.awaitingStart) return state
        val q = state.current.question
        val elapsed = (now - state.startedAtMs).coerceIn(0, q.timeLimitMs)
        return state.copy(answers = state.answers + SoloAnswer(q.id, null, elapsed, false, false, RoundScore.ZERO, null, "Tidak berhasil"))
    }

    /** Catat timeout (0 poin) bila waktu benar-benar habis dan belum menjawab. */
    fun timeout(state: SoloState, now: Long): SoloState {
        if (state.finished || state.isLocked || state.awaitingStart) return state
        val q = state.current.question
        if (now - state.startedAtMs < q.timeLimitMs) return state
        return state.copy(
            answers = state.answers + SoloAnswer(q.id, null, q.timeLimitMs, true, false, RoundScore.ZERO),
        )
    }

    fun next(state: SoloState, now: Long): SoloState {
        if (!state.isLocked || state.finished) return state
        return if (state.isLast) state.copy(finished = true)
        else state.copy(index = state.index + 1, startedAtMs = now, awaitingStart = state.rounds[state.index + 1].question.isGame)
    }

    // ---- snapshot untuk SavedStateHandle (bertahan dari process death) ----

    @Serializable
    data class Snapshot(
        val config: MatchConfig,
        val questionIds: List<String>,
        val optionOrders: List<List<OptionId>>,
        val index: Int,
        val startedAtMs: Long,
        val answers: List<SnapshotAnswer>,
        val finished: Boolean,
        val seeds: List<Long> = emptyList(),
        val awaitingStart: Boolean = false,
    )

    @Serializable
    data class SnapshotAnswer(
        val optionId: OptionId?,
        val elapsedMs: Long,
        val timedOut: Boolean,
        val game: GameSubmission? = null,
        val gaveUp: Boolean = false,
    )

    fun snapshot(s: SoloState): Snapshot = Snapshot(
        config = s.config,
        questionIds = s.rounds.map { it.question.id },
        optionOrders = s.rounds.map { it.optionOrder },
        index = s.index,
        startedAtMs = s.startedAtMs,
        answers = s.answers.map {
            SnapshotAnswer(it.optionId, it.elapsedMs, it.timedOut, it.game, gaveUp = !it.timedOut && it.optionId == null && it.game == null)
        },
        finished = s.finished,
        seeds = s.rounds.map { it.seed },
        awaitingStart = s.awaitingStart,
    )

    fun restore(snap: Snapshot, bank: QuestionBank): SoloState? {
        val rounds = snap.questionIds.mapIndexed { i, id ->
            val q = bank.byId(id) ?: return null
            val order = snap.optionOrders.getOrNull(i) ?: return null
            runCatching { RoundQuestion(q, order, snap.seeds.getOrNull(i) ?: 0L) }.getOrNull() ?: return null
        }
        if (rounds.isEmpty() || snap.index !in rounds.indices) return null
        val answers = snap.answers.mapIndexed { i, a ->
            val q = rounds.getOrNull(i)?.question ?: return null
            val answer: RoundAnswer? = when {
                a.timedOut || a.gaveUp -> null
                a.game != null -> GameAnswer(a.game)
                a.optionId != null -> ChoiceAnswer(a.optionId)
                else -> null
            }
            val g = Grader.grade(q, answer, a.elapsedMs)
            SoloAnswer(q.id, a.optionId, a.elapsedMs, a.timedOut, g.correct, g.score, a.game, g.detail ?: if (a.gaveUp) "Tidak berhasil" else null)
        }
        return SoloState(snap.config, rounds, snap.index, snap.startedAtMs, answers, snap.finished, snap.awaitingStart)
    }
}
