package id.eduduel.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise real navigation, answer locking and the new result screen; export actual UI captures. */
@RunWith(AndroidJUnit4::class)
class VisualExperienceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(text: String) {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "ui-preview").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun soloCompletesTenRounds_answersLock_andResultAppears() {
        waitText("Latihan Solo")
        capture("01-beranda")
        rule.onNodeWithText("Latihan Solo").performScrollTo().performClick()
        rule.onNodeWithText("Biologi").performScrollTo().performClick()
        capture("02-pilih-mapel")
        rule.onNodeWithText("Mulai Latihan").performScrollTo().performClick()
        repeat(10) { index ->
            waitText("Soal ${index + 1} dari 10")
            val answer = rule.onNodeWithContentDescription("Pilihan A:", substring = true)
            answer.performScrollTo().performClick()
            answer.assertIsNotEnabled()
            if (index == 0) capture("03-jawaban")
            rule.onNodeWithText(if (index == 9) "Lihat hasil" else "Soal berikutnya")
                .performScrollTo().performClick()
        }
        waitText("Latihan tuntas!")
        rule.onNodeWithText("Latihan tuntas!").performScrollTo().assertIsDisplayed()
        capture("04-hasil-latihan")
        rule.onNodeWithText("Kembali ke Beranda").performScrollTo().performClick()
        waitText("Mau main apa hari ini?")
    }

    @Test
    fun largestText_keepsSettingsAndPlayControlsReachable() {
        waitText("Latihan Solo")
        rule.onNodeWithText("Pengaturan").performScrollTo().performClick()
        rule.onNodeWithText("Sangat besar").performScrollTo().performClick()
        capture("05-pengaturan-teks-besar")
        rule.onNodeWithContentDescription("Kembali").performClick()
        rule.onNodeWithText("Latihan Solo").performScrollTo().performClick()
        rule.onNodeWithText("Matematika").performScrollTo().performClick()
        rule.onNodeWithText("Mulai Latihan").performScrollTo().assertIsDisplayed()
        capture("06-mapel-teks-besar")
        rule.onNodeWithContentDescription("Kembali").performClick()
        rule.onNodeWithText("Pengaturan").performScrollTo().performClick()
        rule.onNodeWithText("Normal").performScrollTo().performClick()
    }
}
