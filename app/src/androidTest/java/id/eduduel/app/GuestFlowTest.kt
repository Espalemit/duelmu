package id.eduduel.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Alur Guest-only: buka aplikasi -> langsung Beranda (tanpa login) -> Main Bareng ->
 * validasi nama -> nama bebas -> room demo 4 pemain (3 bot, tanpa jaringan) -> ubah nama sebelum
 * mulai -> pilih mapel Matematika -> mulai -> soal 1 dari 10 bermapel Matematika.
 */
@RunWith(AndroidJUnit4::class)
class GuestFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(text: String, timeoutMs: Long = 10_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun opensStraightToHome_withoutLogin_roomOfFour_tenQuestionsFromChosenSubject() {
        waitText("Latihan Solo")
        rule.onNodeWithText("Main Bareng").assertIsDisplayed()
        rule.onAllNodesWithText("Login", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Daftar", substring = true).assertCountEquals(0)

        rule.onNodeWithText("Main Bareng").performClick()
        waitText("Isi Nama untuk Pertandingan Ini")
        rule.onNode(hasText("Nama kamu")).performTextReplacement(" ")
        rule.onNodeWithText("Lanjutkan").performClick()
        rule.onNodeWithText("Nama tidak boleh kosong.").assertIsDisplayed()

        rule.onNode(hasText("Nama kamu")).performTextReplacement("Uji 🚀")
        rule.onNodeWithText("Lanjutkan").performClick()
        waitText("Kamu bermain sebagai")
        rule.onNodeWithText("Uji 🚀").assertIsDisplayed()
        rule.onNodeWithText("Buat Room").assertIsDisplayed()
        rule.onNodeWithText("Gabung Room").assertIsDisplayed()

        rule.onNodeWithText("Coba dengan 3 Bot (demo)").performScrollTo().performClick()
        waitText("Pemain (4/4)")
        listOf("Bot Andi", "Bot Sari", "Bot Joko").forEach { waitText(it) }
        rule.onNodeWithText("Uji 🚀").assertIsDisplayed()

        rule.onNodeWithText("Ubah Nama").performScrollTo().performClick()
        waitText("Ubah nama untuk pertandingan ini")
        rule.onNode(hasSetTextAction() and hasText("Uji 🚀")).performTextReplacement("Uji Dua")
        rule.onNodeWithText("Simpan").performClick()
        waitText("Uji Dua")
        rule.onNodeWithContentDescription("Uji Dua, Kamu", substring = true).assertIsDisplayed()

        // Hanya pilihan mapel; tidak ada pilihan jenjang/kesulitan/jumlah soal.
        listOf("Jenjang", "SMP + SMA", "Tingkat kesulitan", "Jumlah soal", "Semua topik").forEach {
            rule.onAllNodesWithText(it).assertCountEquals(0)
        }
        rule.onNodeWithText("Matematika").performScrollTo().performClick()
        waitText("10 soal diambil acak dari 100 soal Matematika")

        // Tekan Mulai begitu aktif (bot SIAP ulang setelah mapel diganti); ulangi bila klik sintetis jatuh
        // tepat saat status SIAP berubah.
        try {
            rule.waitUntil(30_000) {
                if (rule.onAllNodesWithText("Soal 1 dari 10", substring = true).fetchSemanticsNodes().isNotEmpty()) return@waitUntil true
                runCatching { rule.onNodeWithText("Mulai Pertandingan").performScrollTo().assertIsEnabled().performClick() }
                false
            }
        } catch (e: Throwable) {
            rule.onRoot().printToLog("EDUDUEL_TREE")
            throw e
        }
        rule.onAllNodesWithText("Matematika · ", substring = true).fetchSemanticsNodes().let { check(it.isNotEmpty()) }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(n: Int) {
    val actual = fetchSemanticsNodes().size
    check(actual == n) { "Diharapkan $n node, ditemukan $actual" }
}
