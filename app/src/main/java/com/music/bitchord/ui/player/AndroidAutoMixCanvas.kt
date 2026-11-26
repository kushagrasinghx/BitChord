package com.music.bitchord.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.AutoMixVisualFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** The attached Now Playing slots share these two leases. Main only. */
internal object NowPlayingCanvasDecoders {
    private const val MAX_DECODERS = 2
    /** See CanvasArtworkPlayer's DEFERRED_RELEASE_MS: release() blocks main for 40-50ms. */
    private const val DEFERRED_RELEASE_MS = 1_000L
    private val inUse = MutableStateFlow(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun awaitCapacity() {
        inUse.first { it < MAX_DECODERS }
    }

    fun open(context: Context): Lease? {
        if (inUse.value >= MAX_DECODERS) return null
        val player = runCatching { newCanvasDecoder(context) }.getOrNull() ?: return null
        inUse.value++
        return Lease(player) { inUse.value-- }
    }

    class Lease(val player: ExoPlayer, private val returnSlot: () -> Unit) {
        var closed = false
            private set

        /** Set once the deferred release has run; a surface destroyed after that is not ours to hold. */
        var released = false
            private set

        /**
         * stop() hands the codec back on the player's own thread, so the slot is free
         * straight away. release() blocks the main thread until that thread quits, which
         * landed on the reveal's final frames (outgoing promotion) or mid-reveal (a
         * sealed-out standby), so it runs later, off the transition.
         */
        fun close(afterRelease: () -> Unit = {}) {
            if (closed) return
            closed = true
            try {
                runCatching { player.stop() }
            } finally {
                returnSlot()
            }
            pendingRelease = afterRelease
            mainHandler.postDelayed(::releaseWhenIdle, DEFERRED_RELEASE_MS)
        }

        private var pendingRelease: () -> Unit = {}

        // A retired Spotify Canvas or sealed-out standby closes while the mix is still
        // animating. Keep its blocking release() off every frame of that mix.
        private fun releaseWhenIdle() {
            val visual = AutoMixVisualFeed.state.value
            if (visual?.mixActive == true && visual.admitsArtwork &&
                AppSettings.autoMixArtwork.value.allows(AppSettings.smartFadeEnabled.value, visual.generation) &&
                (visual.remoteArtworkScope == null || visual.remoteArtworkScope == AppSettings.remoteArtworkScope.value)
            ) {
                mainHandler.postDelayed(::releaseWhenIdle, DEFERRED_RELEASE_MS)
                return
            }
            runCatching { player.release() }
            released = true
            pendingRelease()
        }
    }
}
