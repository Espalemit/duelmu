package id.eduduel.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import id.eduduel.app.data.bank.QuestionBankLoader
import id.eduduel.app.domain.game.MicroscopeSpec
import id.eduduel.app.domain.game.MicroSubmission
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise real navigation, answer locking and the new result screen; export actual UI captures. */
@RunWith(AndroidJUnit4::class)
class VisualExperienceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(text: String) {
        rule.waitUntil(15_000) {
            rule.mainClock.advanceTimeByFrame()
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
        rule.mainClock.autoAdvance = false
        waitText("Latihan Solo")
        capture("01-beranda")
        rule.onNodeWithText("Latihan Solo").performScrollTo().performClick()
        rule.onNodeWithText("Biologi").performScrollTo().performClick()
        capture("02-pilih-mapel")
        rule.onNodeWithText("Mulai Latihan").performScrollTo().performClick()
        repeat(10) { index ->
            waitText("Soal ${index + 1} dari 10")
            val choices = rule.onAllNodesWithContentDescription("Pilihan A:", substring = true).fetchSemanticsNodes()
            if (choices.isEmpty()) {
                completeBiologyGame()
            } else {
                val answer = rule.onNodeWithContentDescription("Pilihan A:", substring = true)
                answer.performScrollTo().performClick()
                rule.mainClock.advanceTimeBy(32)
                answer.assertIsNotEnabled()
            }
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

    /** Solve the single Micro Hunter round as a player, using its verified reference coordinates. */
    private fun completeBiologyGame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bank = QuestionBankLoader.load(context.assets.open("questions.json").use { it.readBytes() }, BuildConfig.BANK_SHA256)
        val question = bank.questions.first { q -> q.game?.spec is MicroscopeSpec &&
            rule.onAllNodesWithText(q.stem).fetchSemanticsNodes().isNotEmpty() }
        val spec = question.game!!.spec as MicroscopeSpec
        val target = (question.game!!.reference as MicroSubmission).taps.last()
        rule.onNodeWithText("Saya siap — Mulai").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(64)
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(spec.focusTarget.toFloat()) }
        rule.mainClock.advanceTimeBy(32)
        fun control(name: String) {
            rule.onNodeWithContentDescription(name).performScrollTo().performClick()
            rule.mainClock.advanceTimeBy(32)
        }
        var zoom = spec.minZoom.toFloat()
        while (zoom < spec.minTapZoom) {
            control("Perbesar")
            zoom = (zoom * 1.25f).coerceAtMost(spec.maxZoom.toFloat())
        }
        val step = spec.worldW.toFloat() / zoom * 0.15f
        var cx = (spec.worldW / 2).toFloat(); var cy = (spec.worldH / 2).toFloat()
        val nx = ((target.x - cx) / step).roundToInt()
        repeat(abs(nx)) {
            control(if (nx > 0) "Geser kanan" else "Geser kiri")
            cx = (cx + if (nx > 0) step else -step).coerceIn(0f, spec.worldW.toFloat())
        }
        val ny = ((target.y - cy) / step).roundToInt()
        repeat(abs(ny)) {
            control(if (ny > 0) "Geser bawah" else "Geser atas")
            cy = (cy + if (ny > 0) step else -step).coerceIn(0f, spec.worldH.toFloat())
        }
        val visible = spec.worldW.toFloat() / zoom
        rule.onNodeWithContentDescription("Lensa mikroskop.", substring = true).performScrollTo().performTouchInput {
            click(Offset((0.5f + (target.x - cx) / visible) * width, (0.5f + (target.y - cy) / visible) * height))
        }
        rule.mainClock.advanceTimeBy(64)
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
