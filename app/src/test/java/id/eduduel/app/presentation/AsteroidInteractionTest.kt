package id.eduduel.app.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import id.eduduel.app.TestBank
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.domain.game.AsteroidEngine
import id.eduduel.app.domain.game.AsteroidSpec
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.presentation.components.GameEffectsHost
import id.eduduel.app.presentation.game.AsteroidGame
import id.eduduel.app.presentation.game.GameEnv
import id.eduduel.app.presentation.game.GameCallbacks
import kotlin.math.abs
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
class AsteroidInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shipCollisionLosesExactlyOneLife_andFlashesEvenWhileRockWouldStillOverlap() {
        val q = TestBank.bank.questions.first { it.game?.spec is AsteroidSpec }
        val spec = (q.game!!.spec as AsteroidSpec).copy(fallSpeed = 1.0, spawnInterval = 10.0, maxWrong = 2)
        val seed = (1L..100L).first { abs(AsteroidEngine.spawns(spec, it, 1).first().x - 0.5) > 0.25 }
        val spawn = AsteroidEngine.spawns(spec, seed, 1).first()
        var clockMs = 0L
        val submissions = mutableListOf<GameSubmission?>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                GameEffectsHost {
                    Column {
                        AsteroidGame(spec, GameEnv(RoundQuestion(q, emptyList(), seed), 0, 60_000,
                            MonotonicClock { clockMs }, false, Color.Blue), GameCallbacks(onSubmit = { submissions += it }))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithContentDescription("Arena asteroid.", substring = true).performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.9f), Offset(width * spawn.x.toFloat(), height * 0.9f), 300)
        }
        rule.runOnIdle { clockMs = (980 / spawn.speedFactor).toLong() }
        rule.mainClock.advanceTimeBy(96)
        rule.onNodeWithText("penalti 1/2", substring = true).assertIsDisplayed()
        rule.onNodeWithText("-1").assertIsDisplayed()
        rule.runOnIdle { assertTrue(submissions.isEmpty()); clockMs += 50 }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("penalti 1/2", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("mini_game_damage").assertDoesNotExist()
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
    }
}
