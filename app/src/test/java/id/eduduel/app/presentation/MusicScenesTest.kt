package id.eduduel.app.presentation

import id.eduduel.app.data.settings.AppSettings
import id.eduduel.app.presentation.components.MusicScenes
import id.eduduel.app.presentation.components.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicScenesTest {
    @Test fun noSceneMeansNoMusic() { assertNull(MusicScenes().current) }

    @Test fun incomingSceneWinsEvenIfOutgoingSceneUpdatesDuringTransition() {
        val scenes = MusicScenes()
        val home = Any(); val match = Any()
        scenes.set(home, MusicTrack.MENU)
        scenes.set(match, MusicTrack.COMPETITIVE)
        scenes.set(home, MusicTrack.MENU)
        assertEquals(MusicTrack.COMPETITIVE, scenes.current)
        scenes.remove(home)
        assertEquals(MusicTrack.COMPETITIVE, scenes.current)
    }

    @Test fun removingIncomingSceneFallsBackWithoutLosingOlderScene() {
        val scenes = MusicScenes()
        val home = Any(); val match = Any()
        scenes.set(home, MusicTrack.MENU)
        scenes.set(match, MusicTrack.COMPETITIVE)
        scenes.remove(match)
        assertEquals(MusicTrack.MENU, scenes.current)
        scenes.remove(home)
        assertNull(scenes.current)
    }

    @Test fun oneScreenCanReturnToCalmMusicWhenMatchFinishes() {
        val scenes = MusicScenes()
        val solo = Any()
        scenes.set(solo, MusicTrack.COMPETITIVE)
        scenes.set(solo, MusicTrack.MENU)
        assertEquals(MusicTrack.MENU, scenes.current)
    }

    @Test fun musicAndSoundEffectsCanBeMutedSeparately() {
        assertTrue(AppSettings().musicEnabled)
        assertTrue(AppSettings(soundEnabled = false).musicEnabled)
        val mutedMusic = AppSettings(musicEnabled = false)
        assertFalse(mutedMusic.musicEnabled)
        assertTrue(mutedMusic.soundEnabled)
    }
}
