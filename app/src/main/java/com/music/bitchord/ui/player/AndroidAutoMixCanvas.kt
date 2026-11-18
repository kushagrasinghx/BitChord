package com.music.bitchord.ui.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** The attached Now Playing slots share these two leases. Main only. */
internal object NowPlayingCanvasDecoders {
    private const val MAX_DECODERS = 2
    private val inUse = MutableStateFlow(0)

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

        fun close() {
            if (closed) return
            closed = true
            try {
                runCatching { player.release() }
            } finally {
                returnSlot()
            }
        }
    }
}
