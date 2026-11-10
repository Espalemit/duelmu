package id.eduduel.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.media.AudioAttributes
import android.media.SoundPool
import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.presentation.components.Feedback
import id.eduduel.app.presentation.components.GameFeedbackEffects
import id.eduduel.app.presentation.components.GameSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue

@RunWith(AndroidJUnit4::class)
class GameFeedbackEffectsTest {
    @get:Rule val rule = createComposeRule()

    private data class Event(val session: String?, val started: Boolean, val index: Int? = null, val correct: Boolean? = null)

    @Test fun allBundledSoundClipsDecodeAndStartPlaybackOnDevice() {
        val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
        try {
            val ready = CountDownLatch(5)
            val failures = ConcurrentLinkedQueue<Int>()
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status != 0) failures.add(status)
                ready.countDown()
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clips = listOf(R.raw.sound_tap, R.raw.sound_correct, R.raw.sound_wrong, R.raw.sound_match_start, R.raw.sound_celebration)
                .map { pool.load(context, it, 1) }
            assertTrue("All sound effects must load within ten seconds", ready.await(10, TimeUnit.SECONDS))
            assertTrue("Native audio decoder rejected a clip: $failures", failures.isEmpty())
            clips.forEach { assertTrue("Audio playback must return a stream ID", pool.play(it, 0.8f, 0.8f, 1, 0, 1f) != 0) }
        } finally {
            pool.release()
        }
    }

    @Test fun playsStartAndEachResultOnce_evenAfterRestoration_andStartsAgainForNewMatch() {
        val sounds = mutableListOf<GameSound>()
        val feedback = Feedback({}, { sounds += it }, AppSettings(hapticsEnabled = false))
        val event = mutableStateOf(Event(null, false))
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val e = event.value
            GameFeedbackEffects(e.session, e.started, e.index, e.correct, feedback)
        }
        rule.runOnIdle { assertEquals(emptyList<GameSound>(), sounds); event.value = Event("match-1", true) }
        rule.runOnIdle {
            assertEquals(listOf(GameSound.MATCH_START), sounds)
            event.value = Event("match-1", true, 0, true)
        }
        rule.runOnIdle {
            assertEquals(listOf(GameSound.MATCH_START, GameSound.CORRECT), sounds)
            event.value = Event(null, false) // transient loading during activity/process restoration
        }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { event.value = Event("match-1", true, 0, true) }
        rule.runOnIdle {
            assertEquals(2, sounds.size)
            event.value = Event("match-1", false, 1, false)
        }
        rule.runOnIdle {
            assertEquals(listOf(GameSound.MATCH_START, GameSound.CORRECT, GameSound.WRONG), sounds)
            event.value = Event("match-1", false, 1, false) // identical result must not replay
        }
        rule.runOnIdle { assertEquals(3, sounds.size); event.value = Event("match-2", true) }
        rule.runOnIdle { assertEquals(GameSound.MATCH_START, sounds.last()); assertEquals(4, sounds.size) }
    }
}
