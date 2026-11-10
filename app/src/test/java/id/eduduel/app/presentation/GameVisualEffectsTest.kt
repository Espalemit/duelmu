package id.eduduel.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import id.eduduel.app.presentation.components.GameEffectsHost
import id.eduduel.app.presentation.components.GameVisualEffects
import id.eduduel.app.presentation.components.SubjectPicker
import id.eduduel.app.domain.MatchConfig
import id.eduduel.app.domain.QuestionMode
import id.eduduel.app.domain.Subject
import id.eduduel.app.domain.QuestionSelector
import id.eduduel.app.domain.QuestionType
import id.eduduel.app.TestBank
import id.eduduel.app.presentation.navigation.configFromRouteJson
import id.eduduel.app.presentation.navigation.toRouteJson
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameVisualEffectsTest {
    @get:Rule val rule = createComposeRule(object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    private fun capture(name: String) {
        val file = File("build/reports/game-effects/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun confettiLastsTwoSeconds_andDamageHalfASecond_withoutBlockingControls() {
        val effects = GameVisualEffects()
        var clicks = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                GameEffectsHost(effects) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Button(onClick = { clicks++ }) { Text("Lanjut") }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnIdle { effects.celebrate() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(350)
        capture("confetti-both-sides")
        rule.onNodeWithTag("mini_game_confetti").assertIsDisplayed()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        fun colorful(left: Boolean): Int = (if (left) 0 until bitmap.width / 2 else bitmap.width / 2 until bitmap.width).sumOf { x ->
            (bitmap.height / 4 until bitmap.height).count { y ->
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel); val g = android.graphics.Color.green(pixel); val b = android.graphics.Color.blue(pixel)
                maxOf(r, g, b) - minOf(r, g, b) > 50
            }
        }
        assertTrue("Confetti must enter from both edges: left=${colorful(true)}, right=${colorful(false)}", colorful(true) > 100 && colorful(false) > 100)
        rule.onNodeWithText("Lanjut").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, clicks); effects.loseLife() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("mini_game_damage").assertIsDisplayed()
        rule.onNodeWithText("-1").assertIsDisplayed()
        capture("life-lost")
        rule.mainClock.advanceTimeBy(450)
        rule.onNodeWithTag("mini_game_damage").assertDoesNotExist()
        rule.onNodeWithText("-1").assertDoesNotExist()
        rule.onNodeWithTag("mini_game_confetti").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("mini_game_confetti").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("mini_game_confetti").assertDoesNotExist()
    }

    @Test fun quizTypePickerIsGone_andRestoredLegacySelectionsUseSixThreeOne() {
        rule.setContent { MaterialTheme { SubjectPicker(TestBank.bank, MatchConfig.INITIAL, {}) } }
        listOf("Jenis soal", "Kuis Klasik", "Kuis Bergambar", "Mini-Game", "Campuran Tipe").forEach {
            rule.onNodeWithText(it).assertDoesNotExist()
        }
        for (subject in Subject.entries + listOf(null)) for (oldMode in QuestionMode.entries) {
            val config = configFromRouteJson(MatchConfig(subject, oldMode).toRouteJson())
            assertEquals(QuestionMode.MIXED, config.mode)
            val rounds = QuestionSelector.forConfig(TestBank.bank, config, 42)
            assertEquals(mapOf(QuestionType.CLASSIC_MCQ to 6, QuestionType.VISUAL_MCQ to 3, QuestionType.MINI_GAME to 1),
                rounds.groupingBy { it.question.type }.eachCount())
            assertTrue(subject == null || rounds.all { it.question.subject == subject })
        }
    }

    @Test fun simultaneousLifeLossesEachGetTheirOwnHalfSecondAnimation() {
        val effects = GameVisualEffects()
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { GameEffectsHost(effects) { Box(Modifier.fillMaxSize()) } } }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnIdle { effects.loseLife(); effects.loseLife() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(650)
        rule.onNodeWithText("-1").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(450)
        rule.onNodeWithTag("mini_game_damage").assertDoesNotExist()
    }
}
