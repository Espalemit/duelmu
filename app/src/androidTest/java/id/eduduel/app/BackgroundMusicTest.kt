package id.eduduel.app

import android.media.AudioManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.eduduel.app.presentation.components.BackgroundMusicController
import id.eduduel.app.presentation.components.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundMusicTest {
    @get:Rule val rule = createComposeRule()

    @Test fun nativeMusic_switchesTracks_andHonorsMuteForegroundAndAudioFocus() {
        rule.setContent {}
        lateinit var music: BackgroundMusicController
        val screen = Any()
        rule.runOnIdle {
            music = BackgroundMusicController(InstrumentationRegistry.getInstrumentation().targetContext)
            music.setScene(screen, MusicTrack.MENU)
            music.setEnabled(true)
            assertFalse(music.isPlaying)
            music.setForeground(true)
        }
        try {
            rule.waitUntil(10_000) { music.isPlaying && music.currentTrack == MusicTrack.MENU }
            rule.runOnIdle {
                assertNull(music.lastError)
                music.onAudioFocusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                assertFalse(music.isPlaying)
                music.onAudioFocusChanged(AudioManager.AUDIOFOCUS_GAIN)
            }
            rule.waitUntil(5_000) { music.isPlaying }
            rule.runOnIdle { music.setScene(screen, MusicTrack.COMPETITIVE) }
            rule.waitUntil(10_000) { music.isPlaying && music.currentTrack == MusicTrack.COMPETITIVE }
            rule.runOnIdle {
                assertNull(music.lastError)
                music.onAudioFocusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
                music.onAudioFocusChanged(AudioManager.AUDIOFOCUS_GAIN)
                music.setForeground(false)
                assertFalse(music.isPlaying)
                music.onAudioFocusChanged(AudioManager.AUDIOFOCUS_GAIN)
                assertFalse(music.isPlaying)
                music.setForeground(true)
            }
            rule.waitUntil(5_000) { music.isPlaying }
            rule.runOnIdle {
                music.setEnabled(false)
                assertFalse(music.isPlaying)
                assertNull(music.currentTrack)
                music.onAudioFocusChanged(AudioManager.AUDIOFOCUS_GAIN)
                assertFalse(music.isPlaying)
                music.setEnabled(true)
            }
            rule.waitUntil(10_000) { music.isPlaying }
        } finally {
            rule.runOnIdle {
                music.close()
                music.close()
                assertFalse(music.isPlaying)
                assertEquals(null, music.currentTrack)
            }
        }
    }
}
