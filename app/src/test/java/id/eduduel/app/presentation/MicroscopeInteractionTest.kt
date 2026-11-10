package id.eduduel.app.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import id.eduduel.app.TestBank
import id.eduduel.app.core.MonotonicClock
import id.eduduel.app.domain.RoundQuestion
import id.eduduel.app.domain.game.GameSubmission
import id.eduduel.app.domain.game.MicroSubmission
import id.eduduel.app.domain.game.MicroscopeSpec
import id.eduduel.app.domain.game.Pt
import id.eduduel.app.domain.game.SceneRegion
import id.eduduel.app.presentation.game.GameCallbacks
import id.eduduel.app.presentation.game.GameEnv
import id.eduduel.app.presentation.game.MicroscopeGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MicroscopeInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val submissions = mutableListOf<GameSubmission?>()
    private val question = TestBank.bank.questions.first { it.game?.spec is MicroscopeSpec }
    private val spec = (question.game!!.spec as MicroscopeSpec).copy(
        worldW = 1000.0, worldH = 1000.0, shapes = emptyList(), targetName = "inti sel",
        regions = listOf(SceneRegion("nucleus", "inti sel", listOf(listOf(
            Pt(400.0, 400.0), Pt(600.0, 400.0), Pt(600.0, 600.0), Pt(400.0, 600.0),
        )), target = true)),
        focusTarget = 0.5, focusTolerance = 0.5,
        minZoom = 1.0, minTapZoom = 1.0, maxZoom = 4.0,
    )

    private fun show(locked: Boolean = false, sceneSpec: MicroscopeSpec = spec) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                    MicroscopeGame(sceneSpec, GameEnv(
                        round = RoundQuestion(question, emptyList()), startedAtMs = 0,
                        durationMs = 60_000, clock = MonotonicClock { 1000L },
                        locked = locked, myColor = Color.Blue,
                    ), GameCallbacks(onSubmit = { submissions += it }))
                }
            }
        }
        rule.mainClock.advanceTimeBy(64)
    }

    private fun lens(): androidx.compose.ui.test.SemanticsNodeInteraction {
        rule.mainClock.advanceTimeBy(32)
        return rule.onNodeWithContentDescription("Lensa mikroskop.", substring = true)
    }

    @Test fun tapOnTargetSubmitsAnswerOnce() {
        show()
        lens().performTouchInput { click(center) }
        rule.runOnIdle {
            assertEquals(1, submissions.size)
            val tap = (submissions.single() as MicroSubmission).taps.single()
            assertEquals(500, tap.x)
            assertEquals(500, tap.y)
        }
        lens().performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, submissions.size) }
    }

    @Test fun wrongTapShowsMessageBelowLensAndAllowsRetry() {
        show()
        lens().performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        rule.mainClock.advanceTimeBy(32)
        val message = rule.onNodeWithText("Bukan inti sel.").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = lens().fetchSemanticsNode().boundsInRoot
        assertTrue("Feedback must be outside the clipped lens", message.top >= viewport.bottom)
        val image = File("build/reports/game-artwork/microscope-feedback.png")
        image.parentFile!!.mkdirs()
        image.outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        lens().performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(2, (submissions.single() as MicroSubmission).taps.size) }
    }

    @Test fun panDoesNotSubmitAnAnswerAndNextTapUsesMovedViewport() {
        show()
        lens().performTouchInput { swipe(center, center + Offset(width * 0.15f, 0f), durationMillis = 400) }
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
        // The center of the specimen has moved right with the pan.
        lens().performTouchInput { click(center + Offset(width * 0.15f, 0f)) }
        rule.runOnIdle { assertEquals(1, submissions.size) }
    }

    @Test fun lockedLensIgnoresTouches() {
        show(locked = true)
        lens().performTouchInput { click(center) }
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
    }

    @Test fun pinchZoomsWithoutAnsweringThenTapStillWorks() {
        show()
        lens().performTouchInput {
            pinch(
                start0 = center - Offset(width * 0.12f, 0f), start1 = center + Offset(width * 0.12f, 0f),
                end0 = center - Offset(width * 0.30f, 0f), end1 = center + Offset(width * 0.30f, 0f),
                durationMillis = 400,
            )
        }
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
        lens().performTouchInput { click(center) }
        rule.runOnIdle {
            val tap = (submissions.single() as MicroSubmission).taps.single()
            assertTrue("Pinching outward must zoom in", tap.zoom > 100)
        }
    }

    @Test fun blurryTapDoesNotCountAndTapUsesUpdatedFocus() {
        show(sceneSpec = spec.copy(focusTolerance = 0.01))
        lens().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("Masih kabur", substring = true).assertIsDisplayed()
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        lens().performTouchInput { click(center) }
        rule.runOnIdle {
            val tap = (submissions.single() as MicroSubmission).taps.single()
            assertEquals(500, tap.focus)
        }
    }

    @Test fun lowZoomTapDoesNotCountAndTapUsesUpdatedZoom() {
        show(sceneSpec = spec.copy(minTapZoom = 1.5))
        lens().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("Perbesar minimal", substring = true).assertIsDisplayed()
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
        repeat(2) { rule.onNodeWithContentDescription("Perbesar").performClick() }
        lens().performTouchInput { click(center) }
        rule.runOnIdle {
            val tap = (submissions.single() as MicroSubmission).taps.single()
            assertTrue(tap.zoom >= 150)
        }
    }
}
