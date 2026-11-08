package id.eduduel.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.eduduel.app.data.bank.BankState
import id.eduduel.app.data.bank.QuestionBankLoader
import id.eduduel.app.presentation.home.HomeContent
import id.eduduel.app.presentation.theme.EduDuelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeMotionTest {
    @get:Rule val rule = createComposeRule()

    private val ready by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BankState.Ready(QuestionBankLoader.load(context.assets.open("questions.json").use { it.readBytes() }, BuildConfig.BANK_SHA256))
    }

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    @Test fun heroActuallyMoves_whileMenuHitAreasStayStableAndClickable() {
        rule.mainClock.autoAdvance = false
        var clicks = 0
        rule.setContent { EduDuelTheme { HomeContent(ready, {}, { clicks++ }, {}, {}) } }
        rule.mainClock.advanceTimeBy(1_200)
        val hero = rule.onNodeWithTag("home_hero")
        val first = hero.captureToImage().pixels()
        val bounds = rule.onNodeWithText("Main Bareng").fetchSemanticsNode().boundsInRoot
        rule.mainClock.advanceTimeBy(2_400)
        val second = hero.captureToImage().pixels()
        assertFalse("Decorations must visibly move between frames", first.contentEquals(second))
        assertEquals(bounds, rule.onNodeWithText("Main Bareng").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("Main Bareng").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun disabledMotion_isStaticAndImmediatelyUsable() {
        rule.mainClock.autoAdvance = false
        var clicks = 0
        rule.setContent { EduDuelTheme { HomeContent(ready, { clicks++ }, {}, {}, {}, motionEnabled = false) } }
        rule.onNodeWithText("Latihan Solo").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("home_hero").performScrollTo()
        val first = rule.onNodeWithTag("home_hero").captureToImage().pixels()
        rule.mainClock.advanceTimeBy(2_400)
        val second = rule.onNodeWithTag("home_hero").captureToImage().pixels()
        assertTrue("Disabled motion must keep the hero static", first.contentEquals(second))
        rule.onNodeWithText("Latihan Solo").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }
}
