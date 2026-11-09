package com.music.bitchord.aod

import android.content.ComponentName
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The always-on playing screen, ported from ArchiveTune's AOD: while the
 * system would show its own screen saver, this DreamService shows the track
 * instead — artwork, title, artist, progress and transport, a clock and the
 * battery, drawn near-black and dimmed so it can stand on a bedside table all
 * night. Everything on it is chosen on the "AOD" settings page; with the
 * master switch off the service still runs but draws nothing, so the system's
 * own screen saver is what stays up.
 */
class AodDreamService : DreamService() {

    /** The DreamService is not an activity; Compose still needs its owners. */
    private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
        private val savedStateController = SavedStateRegistryController.create(this)
        fun performRestore(savedState: Bundle?) = savedStateController.performRestore(savedState)
        fun handleLifecycleEvent(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    private val owner = ServiceLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** What the screen draws, fed off the session. */
    private var metadata by mutableStateOf<MediaMetadata?>(null)
    private var playing by mutableStateOf(false)
    private var positionMs by mutableStateOf(0L)
    private var durationMs by mutableStateOf(0L)

    /** The pixel-shift offset, toggled once a minute to age the panel evenly. */
    private var pixelShift by mutableStateOf(0f)

    private var controller: MediaController? = null

    override fun onCreate() {
        super.onCreate()
        owner.performRestore(null)
        // Interactive, so the transport answers a tap — "touch lock" below is
        // the way to take that away for a pocket, not this.
        isInteractive = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    AodScreen(
                        metadata = metadata,
                        playing = playing,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        pixelShift = pixelShift,
                        onPlayPause = { controller?.let { c -> if (c.isPlaying) c.pause() else c.play() } },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                    )
                }
            },
        )
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        scope.launch {
            val token = SessionToken(
                applicationContext,
                ComponentName(applicationContext, PlaybackService::class.java),
            )
            val built = runCatching {
                MediaController.Builder(applicationContext, token).buildAsync().await()
            }.getOrNull() ?: return@launch
            if (!scope.isActive) {
                built.release()
                return@launch
            }
            controller = built
            metadata = built.mediaMetadata
            playing = built.isPlaying
            durationMs = built.duration
            built.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(newMetadata: MediaMetadata) {
                    metadata = newMetadata
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            })
            // The playhead, run on a wall clock rather than listening for
            // position discontinuities: a dozing frame drops the latter, and
            // one tick a second is all the bar asks.
            while (isActive) {
                positionMs = built.currentPosition
                durationMs = built.duration
                delay(1_000)
            }
        }

        scope.launch {
            // The brightness the AOD row set, eased further down the longer the
            // screen stays up when auto-dimming is on, and set once at the
            // start otherwise.
            val target = AppSettings.aodBrightness.value
            val autoDim = AppSettings.aodAutoDimming.value
            window.attributes = window.attributes.apply { screenBrightness = target }
            if (!autoDim) return@launch
            var held = 0f
            while (isActive) {
                delay(AUTO_DIM_STEP_MS)
                held += AUTO_DIM_STEP_MS
                // Half the set brightness over half an hour, easing the whole way.
                val floor = target * 0.5f
                val next = (target - (target - floor) * (held / AUTO_DIM_OVER_MS.toFloat()))
                    .coerceAtLeast(floor)
                window.attributes = window.attributes.apply { screenBrightness = next }
            }
        }

        scope.launch {
            // The auto timer gives the screen back — 0, never set, keeps it.
            val seconds = AppSettings.aodAutoTimerSeconds.value
            if (seconds > 0) {
                delay(seconds * 1_000L)
                finish()
            }
        }

        if (AppSettings.aodPixelShift.value) {
            scope.launch {
                while (isActive) {
                    delay(PIXEL_SHIFT_PERIOD_MS)
                    pixelShift = if (pixelShift == 0f) 1f else 0f
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        controller?.release()
        controller = null
    }

    private companion object {
        const val AUTO_DIM_STEP_MS = 5_000L
        const val AUTO_DIM_OVER_MS = 30 * 60_000L
        const val PIXEL_SHIFT_PERIOD_MS = 60_000L
    }
}
