package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A display-frame clock for the AutoMix reveal. The controller publishes the audio
 * position every 20-30ms; read alone, that moved the mask in ~33 steps a second on a
 * 120Hz screen, which looked like a low-fps transition however fast frames rendered.
 * Readers of [progress] in a draw or layer scope are invalidated on every frame while
 * a reveal window is running, and see the sample carried forward to that frame. Main only.
 */
internal object AutoMixRevealClock {
    private val frame = mutableLongStateOf(0L)
    private var shownGeneration = Long.MIN_VALUE
    private var shown = 0f

    /** [sampled] is the caller's own reading of the same generation's progress. */
    fun progress(state: AutoMixVisualState?, sampled: Float): Float {
        val now = frame.longValue // Observed for per-frame invalidation.
        // No frame clock yet (or ever, off-device): the sample alone, as before.
        if (now == 0L || state == null || !state.mixActive || state.artworkWindow == null) return sampled
        val at = maxOf(sampled, state.artworkProgressAt(now))
        if (state.generation != shownGeneration) {
            shownGeneration = state.generation
            shown = 0f
        }
        // A sample can land a hair behind the previous frame's extrapolation; never step back.
        shown = maxOf(shown, at)
        return shown
    }

    fun revealing(state: AutoMixVisualState?): Boolean =
        state != null && state.phase == AutoMixVisualPhase.MIXING && state.artworkWindow != null &&
            state.artworkProgress < 1f

    suspend fun tick() {
        while (true) withFrameNanos { frame.longValue = it }
    }
}

/** Run the reveal's frame clock while a window is open. No recomposition per sample. */
@Composable
internal fun AutoMixRevealClockEffect() {
    val feed = PlayerPlatform.host.autoMixVisualState ?: return
    LaunchedEffect(feed) {
        feed.map { AutoMixRevealClock.revealing(it) }.distinctUntilChanged().collectLatest { running ->
            if (running) AutoMixRevealClock.tick()
        }
    }
}
