package id.eduduel.app.domain

import kotlin.random.Random

/** Soal terpilih untuk satu ronde beserta urutan tampilan opsi (posisi A-D di layar). */
data class RoundQuestion(val question: Question, val optionOrder: List<OptionId>) {
    init {
        require(optionOrder.size == 4 && optionOrder.toSet() == OptionId.entries.toSet()) {
            "optionOrder harus permutasi A-D"
        }
    }

    /** Opsi sesuai urutan tampilan. Label posisi (A-D) di layar = indeks, id kanonik tetap dipakai untuk jawaban. */
    val displayedOptions: List<AnswerOption> get() = optionOrder.map(question::option)
}

/**
 * Pemilihan soal tanpa pengulangan dengan seed. Host memilih seed acak dan mengirim hasil
 * (ID + urutan opsi) ke Join, sehingga Join tidak perlu menjalankan algoritme ini; determinisme
 * tetap dijaga agar mudah diuji dan diaudit.
 */
object QuestionSelector {

    fun select(
        pool: List<Question>,
        count: Int,
        seed: Long,
        avoidIds: Set<String> = emptySet(),
        shuffleOptions: Boolean = true,
    ): List<RoundQuestion> {
        require(count > 0) { "count harus > 0" }
        require(pool.size >= count) { "Soal tersedia (${pool.size}) kurang dari permintaan ($count)" }
        val random = Random(seed)
        val sorted = pool.distinctBy { it.id }.sortedBy { it.id }
        // Utamakan soal yang belum dipakai (mis. saat "Ulangi Latihan"), lalu sisanya bila kurang.
        val fresh = sorted.filter { it.id !in avoidIds }.shuffled(random)
        val used = sorted.filter { it.id in avoidIds }.shuffled(random)
        val chosen = (fresh + used).take(count)
        return chosen.map { q ->
            val order = if (shuffleOptions) OptionId.entries.shuffled(random) else OptionId.entries.toList()
            RoundQuestion(q, order)
        }
    }
}
