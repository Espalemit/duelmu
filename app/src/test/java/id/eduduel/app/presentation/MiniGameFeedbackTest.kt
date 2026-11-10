package id.eduduel.app.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import id.eduduel.app.TestBank
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.domain.game.GameEngines
import id.eduduel.app.domain.game.GameType
import id.eduduel.app.presentation.components.Feedback
import id.eduduel.app.presentation.components.GameFeedbackEffects
import id.eduduel.app.presentation.components.GameSound
import id.eduduel.app.presentation.components.GameVisualEffects
import id.eduduel.app.presentation.components.LocalGameVisualEffects
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MiniGameFeedbackTest {
    @get:Rule val rule = createComposeRule()

    private data class Result(val index: Int? = null, val correct: Boolean? = null, val miniGame: Boolean = true)

    @Test fun allFourMiniGamesCelebrateOnceAndRestorationDoesNotReplay() {
        val sounds = mutableListOf<GameSound>()
        val feedback = Feedback({}, { sounds += it }, AppSettings(hapticsEnabled = false))
        val visuals = GameVisualEffects()
        val result = mutableStateOf(Result())
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val r = result.value
            CompositionLocalProvider(LocalGameVisualEffects provides visuals) {
                GameFeedbackEffects("session", false, r.index, r.correct, feedback, r.miniGame)
            }
        }
        GameType.entries.forEachIndexed { index, type ->
            val question = TestBank.bank.questions.first { it.game?.gameType == type }
            val game = question.game!!
            val success = GameEngines.evaluate(game.spec, game.reference).success
            rule.runOnIdle { result.value = Result(index, success, question.isGame) }
            rule.runOnIdle { assertEquals(List(index + 1) { GameSound.CELEBRATION }, sounds) }
        }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(4, sounds.size); assertEquals(4, visuals.celebration) }
        rule.runOnIdle { result.value = Result(4, false) }
        rule.runOnIdle { assertEquals(GameSound.WRONG, sounds.last()); assertEquals(5, sounds.size) }
        rule.runOnIdle { result.value = Result(5, true, miniGame = false) }
        rule.runOnIdle { assertEquals(GameSound.CORRECT, sounds.last()); assertEquals(6, sounds.size); assertEquals(4, visuals.celebration) }
    }
}
