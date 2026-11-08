package id.eduduel.app.presentation

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.presentation.components.Feedback
import id.eduduel.app.presentation.components.GameSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test fun defaultSettingsEnableSounds() {
        assertTrue(AppSettings().soundEnabled)
    }

    @Test fun correctWrongAndStartUseDistinctEffects() {
        val sounds = mutableListOf<GameSound>()
        val feedback = Feedback({}, { sounds += it }, AppSettings(hapticsEnabled = false))
        feedback.matchStart()
        feedback.result(true)
        feedback.result(false)
        feedback.tap()
        assertEquals(listOf(GameSound.MATCH_START, GameSound.CORRECT, GameSound.WRONG, GameSound.TAP), sounds)
    }

    @Test fun mutedSettingsKeepHapticsWithoutPlayingAnySound() {
        val sounds = mutableListOf<GameSound>()
        val haptics = mutableListOf<HapticFeedbackType>()
        val feedback = Feedback({ haptics += it }, { sounds += it }, AppSettings(soundEnabled = false))
        feedback.matchStart()
        feedback.result(true)
        feedback.result(false)
        feedback.tap()
        assertTrue(sounds.isEmpty())
        assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Reject, HapticFeedbackType.TextHandleMove), haptics)
    }

    @Test fun disablingHapticsDoesNotMuteSounds() {
        val sounds = mutableListOf<GameSound>()
        val feedback = Feedback({ error("Haptics disabled") }, { sounds += it }, AppSettings(hapticsEnabled = false))
        feedback.result(true)
        feedback.result(false)
        feedback.tap()
        assertEquals(3, sounds.size)
    }

    @Test fun unavailableAudioDoesNotCrashGame() {
        val feedback = Feedback({}, { error("Audio device unavailable") }, AppSettings(hapticsEnabled = false))
        feedback.matchStart()
        feedback.result(true)
        feedback.result(false)
        feedback.tap()
    }
}
