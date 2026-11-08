package id.eduduel.app.presentation.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import id.eduduel.app.R
import id.eduduel.app.data.settings.AppSettings

enum class GameSound { TAP, CORRECT, WRONG, MATCH_START }

/** Short clips mixed locally; button taps do not cut off an answer or the starting fanfare. */
internal class GameSoundPlayer(context: Context) : AutoCloseable {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val loaded = mutableSetOf<Int>()
    private val pending = mutableMapOf<Int, Long>()
    private var closed = false
    private val samples: Map<GameSound, Int>

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            synchronized(this) {
                if (!closed && status == 0) {
                    loaded += id
                    val requestedAt = pending.remove(id)
                    // Only replay a recent request, never an old sound after a slow load.
                    if (requestedAt != null && SystemClock.elapsedRealtime() - requestedAt < 1_000) playSample(id)
                }
            }
        }
        samples = mapOf(
            GameSound.TAP to pool.load(context, R.raw.sound_tap, 1),
            GameSound.CORRECT to pool.load(context, R.raw.sound_correct, 1),
            GameSound.WRONG to pool.load(context, R.raw.sound_wrong, 1),
            GameSound.MATCH_START to pool.load(context, R.raw.sound_match_start, 1),
        )
    }

    @Synchronized
    fun play(sound: GameSound) {
        if (closed) return
        val id = samples[sound]?.takeIf { it != 0 } ?: return
        if (id in loaded) playSample(id) else pending[id] = SystemClock.elapsedRealtime()
    }

    private fun playSample(id: Int) {
        runCatching { pool.play(id, 0.8f, 0.8f, 1, 0, 1f) }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        pending.clear()
        pool.release()
    }
}

/** Preferences gate sound and haptics separately; audio failure cannot interrupt a game. */
class Feedback(
    private val haptic: (HapticFeedbackType) -> Unit,
    private val playSound: (GameSound) -> Unit,
    private val settings: AppSettings,
) {
    private fun sound(effect: GameSound) {
        if (settings.soundEnabled) runCatching { playSound(effect) }
    }

    fun tap() {
        if (settings.hapticsEnabled) haptic(HapticFeedbackType.TextHandleMove)
        sound(GameSound.TAP)
    }

    fun result(correct: Boolean) {
        if (settings.hapticsEnabled) haptic(if (correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        sound(if (correct) GameSound.CORRECT else GameSound.WRONG)
    }

    fun matchStart() = sound(GameSound.MATCH_START)
}

@Composable
fun rememberFeedback(settings: AppSettings): Feedback {
    val context = LocalContext.current.applicationContext
    val haptic = LocalHapticFeedback.current
    val player = remember(context, settings.soundEnabled) {
        if (settings.soundEnabled) runCatching { GameSoundPlayer(context) }.getOrNull() else null
    }
    DisposableEffect(player) { onDispose { player?.close() } }
    return remember(settings, haptic, player) {
        Feedback({ haptic.performHapticFeedback(it) }, { player?.play(it) }, settings)
    }
}

/** Consume each event once per session, including after recomposition or activity recreation. */
@Composable
fun GameFeedbackEffects(
    sessionId: String?,
    started: Boolean,
    resultIndex: Int?,
    correct: Boolean?,
    feedback: Feedback?,
) {
    var soundSession by rememberSaveable { mutableStateOf<String?>(null) }
    var startPlayed by rememberSaveable { mutableStateOf(false) }
    var lastResult by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(sessionId, started, resultIndex, correct) {
        if (sessionId == null) return@LaunchedEffect
        if (soundSession != sessionId) {
            soundSession = sessionId
            startPlayed = false
            lastResult = -1
        }
        if (started && !startPlayed) {
            startPlayed = true
            feedback?.matchStart()
        }
        if (resultIndex != null && correct != null && resultIndex > lastResult) {
            lastResult = resultIndex
            feedback?.result(correct)
        }
    }
}
