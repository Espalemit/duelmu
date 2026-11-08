package id.eduduel.app.presentation.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.eduduel.app.R

internal enum class MusicTrack(val resource: Int, val volume: Float) {
    MENU(R.raw.music_menu, 0.30f),
    COMPETITIVE(R.raw.music_competitive, 0.28f),
}

/** Incoming destinations own the music; disposing an outgoing screen cannot overwrite it. */
internal class MusicScenes {
    private val owners = linkedMapOf<Any, MusicTrack>()
    val current: MusicTrack? get() = owners.values.lastOrNull()
    fun set(owner: Any, track: MusicTrack) { owners[owner] = track }
    fun remove(owner: Any) { owners.remove(owner) }
}

/** Main-thread owner of exactly one looping player and one audio-focus request. */
internal class BackgroundMusicController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(::onAudioFocusChanged, Handler(Looper.getMainLooper())).build()
    private val scenes = MusicScenes()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var enabled = false
    private var foreground = false
    private var focusRequested = false
    private var focusGranted = false
    private var focusBlocked = false
    private var ducked = false
    private var closed = false
    private var failedTrack: MusicTrack? = null
    @Volatile var currentTrack: MusicTrack? = null
        private set
    @Volatile var isPlaying = false
        private set
    @Volatile var lastError: String? = null
        private set

    fun setScene(owner: Any, track: MusicTrack) {
        val previous = scenes.current
        scenes.set(owner, track)
        if (previous != scenes.current) failedTrack = null
        reconcile()
    }

    fun removeScene(owner: Any) {
        val previous = scenes.current
        scenes.remove(owner)
        if (previous != scenes.current) failedTrack = null
        reconcile()
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value || closed) return
        enabled = value
        failedTrack = null
        if (value) focusBlocked = false
        reconcile()
    }

    fun setForeground(value: Boolean) {
        if (foreground == value || closed) return
        foreground = value
        if (value) { focusBlocked = false; failedTrack = null }
        reconcile()
    }

    internal fun onAudioFocusChanged(change: Int) {
        if (closed) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true; focusBlocked = false; ducked = false
                reconcile()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusGranted = false; focusBlocked = true
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusGranted = false; focusBlocked = true
                pause()
                if (focusRequested) runCatching { audio.abandonAudioFocusRequest(focusRequest) }
                focusRequested = false
            }
        }
    }

    private fun reconcile() {
        if (closed) return
        val track = scenes.current
        if (!enabled || !foreground || track == null) {
            pause()
            if (!enabled || track == null) releasePlayer()
            abandonFocus()
            return
        }
        if (focusBlocked) return
        if (!focusGranted) {
            focusRequested = true
            focusGranted = runCatching { audio.requestAudioFocus(focusRequest) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!focusGranted) { focusRequested = false; focusBlocked = true; return }
        }
        if (currentTrack != track) {
            releasePlayer()
            if (failedTrack == track) return
            prepare(track)
        }
        if (prepared && !isPlaying) {
            runCatching { applyVolume(); player?.start(); isPlaying = true }
                .onFailure { fail(track, it.javaClass.simpleName) }
        }
    }

    private fun prepare(track: MusicTrack) {
        val next = runCatching { MediaPlayer() }.getOrElse {
            lastError = it.javaClass.simpleName
            failedTrack = track
            return
        }
        player = next
        currentTrack = track
        lastError = null
        runCatching {
            next.setAudioAttributes(attributes)
            appContext.resources.openRawResourceFd(track.resource).use { asset ->
                next.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            next.isLooping = true
            next.setOnPreparedListener {
                if (!closed && player === it) { prepared = true; reconcile() }
            }
            next.setOnErrorListener { failed, what, extra ->
                if (player === failed) fail(track, "MediaPlayer $what/$extra")
                true
            }
            applyVolume()
            next.prepareAsync()
        }.onFailure { fail(track, it.javaClass.simpleName) }
    }

    private fun applyVolume() {
        val volume = (currentTrack?.volume ?: 0f) * if (ducked) 0.2f else 1f
        runCatching { player?.setVolume(volume, volume) }
    }

    private fun pause() {
        if (prepared && isPlaying) runCatching { player?.pause() }
        isPlaying = false
    }

    private fun fail(track: MusicTrack, message: String) {
        lastError = message
        failedTrack = track
        releasePlayer()
    }

    private fun releasePlayer() {
        val old = player
        player = null
        prepared = false
        isPlaying = false
        currentTrack = null
        runCatching { old?.release() }
    }

    private fun abandonFocus() {
        if (focusRequested) runCatching { audio.abandonAudioFocusRequest(focusRequest) }
        focusRequested = false
        focusGranted = false
        focusBlocked = false
        ducked = false
    }

    override fun close() {
        if (closed) return
        closed = true
        releasePlayer()
        abandonFocus()
    }
}

internal val LocalBackgroundMusic = staticCompositionLocalOf<BackgroundMusicController?> { null }

@Composable
internal fun rememberBackgroundMusic(enabled: Boolean): BackgroundMusicController? {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(context) { runCatching { BackgroundMusicController(context) }.getOrNull() }
    SideEffect { controller?.setEnabled(enabled) }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            controller?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        controller?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.close()
        }
    }
    return controller
}

@Composable
internal fun BackgroundMusicScene(track: MusicTrack) {
    val controller = LocalBackgroundMusic.current
    val owner = remember { Any() }
    DisposableEffect(controller, owner) {
        controller?.setScene(owner, track)
        onDispose { controller?.removeScene(owner) }
    }
    SideEffect { controller?.setScene(owner, track) }
}
