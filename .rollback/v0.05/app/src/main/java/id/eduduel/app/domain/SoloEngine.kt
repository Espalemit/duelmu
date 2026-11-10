package id.eduduel.app.domain

import kotlinx.serialization.Serializable

data class SoloAnswer(
    val questionId: String,
    val optionId: OptionId?,
    val elapsedMs: Long,
    val timedOut: Boolean,
    val correct: Boolean,
    val score: RoundScore,
)

/**
 * State latihan solo yang murni (immutable). Waktu mulai soal disimpan sebagai nilai jam
 * monotonic sehingga recomposition/rotasi tidak me-reset timer; waktu tetap berjalan di background.
 */
data class SoloState(
    val config: MatchConfig,
    val rounds: List<RoundQuestion>,
    val index: Int,
    val startedAtMs: Long,
    val answers: List<SoloAnswer>,
    val finished: Boolean,
) {
    val current: RoundQuestion get() = rounds[index]
    val currentAnswer: SoloAnswer? get() = answers.getOrNull(index)
    val isLocked: Boolean get() = currentAnswer != null
    val totalScore: Int get() = answers.sumOf { it.score.total }
    val correctCount: Int get() = answers.count { it.correct }
    val isLast: Boolean get() = index == rounds.lastIndex

    fun remainingMs(now: Long): Long =
        ScoringEngine.remainingMs(current.question.timeLimitMs, now - startedAtMs)
}

object SoloEngine {

    fun start(config: MatchConfig, rounds: List<RoundQuestion>, now: Long): SoloState {
        require(rounds.isNotEmpty())
        require(rounds.map { it.question.id }.toSet().size == rounds.size) { "Soal duplikat dalam satu sesi" }
        return SoloState(config, rounds, 0, now, emptyList(), false)
    }

    /** Kunci jawaban. Diabaikan bila sudah terkunci (tidak boleh submit ganda) atau waktu habis. */
    fun answer(state: SoloState, option: OptionId, now: Long): SoloState {
        if (state.finished || state.isLocked) return state
        val q = state.current.question
        val elapsed = (now - state.startedAtMs).coerceAtLeast(0)
        if (elapsed >= q.timeLimitMs) return timeout(state, now)
        val correct = option == q.correctOptionId
        val score = ScoringEngine.score(q.difficulty, correct, elapsed, q.timeLimitMs)
        return state.copy(answers = state.answers + SoloAnswer(q.id, option, elapsed, false, correct, score))
    }

    /** Catat timeout (0 poin) bila waktu benar-benar habis dan belum menjawab. */
    fun timeout(state: SoloState, now: Long): SoloState {
        if (state.finished || state.isLocked) return state
        val q = state.current.question
        if (now - state.startedAtMs < q.timeLimitMs) return state
        return state.copy(
            answers = state.answers + SoloAnswer(q.id, null, q.timeLimitMs, true, false, RoundScore.ZERO),
        )
    }

    fun next(state: SoloState, now: Long): SoloState {
        if (!state.isLocked || state.finished) return state
        return if (state.isLast) state.copy(finished = true)
        else state.copy(index = state.index + 1, startedAtMs = now)
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
    )

    @Serializable
    data class SnapshotAnswer(val optionId: OptionId?, val elapsedMs: Long, val timedOut: Boolean)

    fun snapshot(s: SoloState): Snapshot = Snapshot(
        config = s.config,
        questionIds = s.rounds.map { it.question.id },
        optionOrders = s.rounds.map { it.optionOrder },
        index = s.index,
        startedAtMs = s.startedAtMs,
        answers = s.answers.map { SnapshotAnswer(it.optionId, it.elapsedMs, it.timedOut) },
        finished = s.finished,
    )

    fun restore(snap: Snapshot, bank: QuestionBank): SoloState? {
        val rounds = snap.questionIds.mapIndexed { i, id ->
            val q = bank.byId(id) ?: return null
            RoundQuestion(q, snap.optionOrders.getOrNull(i) ?: return null)
        }
        if (rounds.isEmpty() || snap.index !in rounds.indices) return null
        val answers = snap.answers.mapIndexed { i, a ->
            val q = rounds.getOrNull(i)?.question ?: return null
            val correct = !a.timedOut && a.optionId == q.correctOptionId
            SoloAnswer(q.id, a.optionId, a.elapsedMs, a.timedOut, correct,
                ScoringEngine.score(q.difficulty, correct, a.elapsedMs, q.timeLimitMs))
        }
        return SoloState(snap.config, rounds, snap.index, snap.startedAtMs, answers, snap.finished)
    }
}
