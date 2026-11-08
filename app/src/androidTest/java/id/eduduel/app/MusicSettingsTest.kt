package id.eduduel.app

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.eduduel.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicSettingsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun musicSwitchPersistsAcrossActivityRecreation_withoutChangingEffects() {
        val repo = SettingsRepository(rule.activity.applicationContext)
        val original = runBlocking { repo.settings.first() }
        try {
            rule.waitUntil(15_000) { rule.onAllNodesWithText("Latihan Solo").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Pengaturan").performScrollTo().performClick()
            rule.onNodeWithText("Musik latar").performScrollTo().performClick()
            rule.waitUntil(5_000) { runBlocking { repo.settings.first().musicEnabled != original.musicEnabled } }
            assertEquals(original.soundEnabled, runBlocking { repo.settings.first().soundEnabled })
            rule.activityRule.scenario.recreate()
            rule.waitForIdle()
            if (rule.onAllNodesWithText("Musik latar").fetchSemanticsNodes().isEmpty()) {
                rule.onNodeWithText("Pengaturan").performScrollTo().performClick()
            }
            val toggle = rule.onNodeWithText("Musik latar").performScrollTo()
            if (original.musicEnabled) toggle.assertIsOff() else toggle.assertIsOn()
            val fresh = SettingsRepository(rule.activity.applicationContext)
            assertEquals(!original.musicEnabled, runBlocking { fresh.settings.first().musicEnabled })
        } finally {
            runBlocking { repo.setMusic(original.musicEnabled) }
        }
    }
}
