package id.eduduel.app.domain

data class RoundScore(val basePoints: Int, val speedBonus: Int) {
    val total: Int get() = basePoints + speedBonus

    companion object {
        val ZERO = RoundScore(0, 0)
    }
}

/**
 * Formula skor deterministik (identik di kedua perangkat):
 * - salah / timeout -> 0
 * - benar -> basePoints + floor(0.5 * basePoints * remainingMs / totalMs), bonus dibatasi [0, basePoints/2]
 * Seluruh perhitungan memakai aritmetika bilangan bulat (Long) agar tidak ada selisih floating point.
 */
object ScoringEngine {

    fun remainingMs(totalMs: Long, elapsedMs: Long): Long =
        (totalMs - elapsedMs.coerceAtLeast(0)).coerceIn(0, totalMs)

    fun speedBonus(basePoints: Int, remainingMs: Long, totalMs: Long): Int {
        require(totalMs > 0) { "totalMs harus > 0" }
        val rem = remainingMs.coerceIn(0, totalMs)
        // floor(0.5 * base * rem / total) == (base * rem) / (2 * total) untuk bilangan non-negatif
        val bonus = (basePoints.toLong() * rem) / (2L * totalMs)
        return bonus.toInt().coerceIn(0, basePoints / 2)
    }

    fun score(difficulty: Difficulty, correct: Boolean, elapsedMs: Long, totalMs: Long): RoundScore {
        if (!correct) return RoundScore.ZERO
        val base = difficulty.basePoints
        return RoundScore(base, speedBonus(base, remainingMs(totalMs, elapsedMs), totalMs))
    }

    fun score(question: Question, answer: OptionId?, elapsedMs: Long): RoundScore =
        score(question.difficulty, answer != null && answer == question.correctOptionId, elapsedMs, question.timeLimitMs)

    /** Pemenang = skor total tertinggi; sama = SERI (tidak ada tie-breaker karangan). */
    fun outcome(myTotal: Int, opponentTotal: Int): MatchOutcome = when {
        myTotal > opponentTotal -> MatchOutcome.WIN
        myTotal < opponentTotal -> MatchOutcome.LOSE
        else -> MatchOutcome.DRAW
    }
}
