package id.eduduel.app.domain

import kotlin.random.Random

/**
 * Soal terpilih untuk satu ronde. MCQ: urutan tampilan opsi (permutasi A-D). Game: tanpa opsi,
 * dengan [seed] arena/spawn yang SAMA untuk semua pemain (dipilih Host).
 */
data class RoundQuestion(val question: Question, val optionOrder: List<OptionId>, val seed: Long = 0L) {
    init {
        if (question.isGame) {
            require(optionOrder.isEmpty()) { "Soal game tidak memiliki urutan opsi" }
        } else {
            require(optionOrder.size == 4 && optionOrder.toSet() == OptionId.entries.toSet()) {
                "optionOrder harus permutasi A-D"
            }
        }
    }

    /** Opsi sesuai urutan tampilan. Label posisi (A-D) di layar = indeks, id kanonik tetap dipakai untuk jawaban. */
    val displayedOptions: List<AnswerOption> get() = optionOrder.map(question::option)
}

/**
 * Pemilihan soal tanpa pengulangan dengan seed. Host memilih seed acak dan mengirim hasil
 * (ID + urutan opsi + seed game) ke Tamu, sehingga Tamu tidak perlu menjalankan algoritme ini;
 * determinisme tetap dijaga agar mudah diuji dan diaudit.
 */
object QuestionSelector {

    /** Kuota mode Campuran Tipe untuk 10 soal: 4 klasik, 3 bergambar, 3 game (proporsional bila n lain). */
    fun mixedQuota(count: Int): Map<QuestionType, Int> {
        val visual = (count * 3) / 10
        val game = (count * 3) / 10
        return mapOf(QuestionType.CLASSIC_MCQ to count - visual - game, QuestionType.VISUAL_MCQ to visual, QuestionType.MINI_GAME to game)
    }

    fun select(
        pool: List<Question>,
        count: Int,
        seed: Long,
        avoidIds: Set<String> = emptySet(),
        shuffleOptions: Boolean = true,
        stratifyTypes: Boolean = false,
    ): List<RoundQuestion> {
        require(count > 0) { "count harus > 0" }
        require(pool.size >= count) { "Soal tersedia (${pool.size}) kurang dari permintaan ($count)" }
        val random = Random(seed)
        val sorted = pool.distinctBy { it.id }.sortedBy { it.id }
        val chosen = if (stratifyTypes && sorted.map { it.type }.toSet().size > 1) {
            val quota = mixedQuota(count)
            val picked = ArrayList<Question>()
            for (type in QuestionType.entries) {
                val ofType = sorted.filter { it.type == type }
                picked += pickFresh(ofType, quota[type] ?: 0, avoidIds, random)
            }
            // Kekurangan satu jenis (bank kecil) diisi dari sisa pool.
            if (picked.size < count) picked += pickFresh(sorted.filter { it !in picked }, count - picked.size, avoidIds, random)
            picked.shuffled(random)
        } else {
            pickFresh(sorted, count, avoidIds, random)
        }
        return chosen.map { q ->
            if (q.isGame) {
                RoundQuestion(q, emptyList(), random.nextLong())
            } else {
                val order = if (shuffleOptions) OptionId.entries.shuffled(random) else OptionId.entries.toList()
                RoundQuestion(q, order)
            }
        }
    }

    /** Utamakan soal yang belum dipakai (mis. saat "Ulangi Latihan"), lalu sisanya bila kurang. */
    private fun pickFresh(sorted: List<Question>, n: Int, avoidIds: Set<String>, random: Random): List<Question> {
        if (n <= 0) return emptyList()
        val fresh = sorted.filter { it.id !in avoidIds }.shuffled(random)
        val used = sorted.filter { it.id in avoidIds }.shuffled(random)
        return (fresh + used).take(n)
    }

    /** Pemilihan sesuai aturan pertandingan (mode Campuran Tipe = berstrata per jenis soal). */
    fun forConfig(bank: QuestionBank, config: MatchConfig, seed: Long, avoidIds: Set<String> = emptySet()): List<RoundQuestion> {
        val pool = bank.pool(config)
        return select(pool, config.questionCount.coerceAtMost(pool.size), seed, avoidIds, stratifyTypes = config.mode == QuestionMode.MIXED)
    }
}
