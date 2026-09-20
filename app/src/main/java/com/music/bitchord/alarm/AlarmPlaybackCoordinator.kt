package com.music.bitchord.alarm

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.toMediaItem
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded MediaController binding; playback remains owned by PlaybackService. */
object AlarmPlaybackCoordinator {

    fun play(
        context: Context,
        selection: AlarmSong,
        complete: (Boolean) -> Unit,
    ) {
        val queue = AlarmSongRequest.queue(selection)
        if (queue.size != 1) {
            complete(false)
            return
        }
        connect(context, complete) { controller, finish, isFinished ->
            // Replace restored playback atomically and force this alarm queue to play once.
            controller.stop()
            controller.clearMediaItems()
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.setMediaItems(listOf(queue.single().toMediaItem()), 0, 0L)
            controller.prepare()
            controller.play()

            val handler = Handler(Looper.getMainLooper())
            fun probe() {
                if (isFinished()) return
                when {
                    controller.playerError != null -> finish(false, 0L)
                    controller.mediaItemCount == 1 &&
                        controller.currentMediaItem?.mediaId == selection.videoId.trim() &&
                        controller.playWhenReady -> finish(true, SETTLE_MS)
                    else -> handler.postDelayed(::probe, PROBE_MS)
                }
            }
            handler.postDelayed(::probe, PROBE_MS)
        }
    }

    fun stop(context: Context, complete: (Boolean) -> Unit) {
        connect(context, complete) { controller, finish, _ ->
            controller.stop()
            controller.clearMediaItems()
            finish(true, STOP_SETTLE_MS)
        }
    }

    private fun connect(
        context: Context,
        complete: (Boolean) -> Unit,
        command: (MediaController, (Boolean, Long) -> Unit, () -> Boolean) -> Unit,
    ) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        val done = AtomicBoolean(false)

        fun finish(success: Boolean, delayMillis: Long) {
            if (!done.compareAndSet(false, true)) return
            handler.postDelayed(
                {
                    MediaController.releaseFuture(future)
                    complete(success)
                },
                delayMillis,
            )
        }

        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    finish(false, 0L)
                } else {
                    runCatching { command(controller, ::finish, done::get) }
                        .onFailure { finish(false, 0L) }
                }
            },
            ContextCompat.getMainExecutor(app),
        )
        handler.postDelayed({ finish(false, 0L) }, GIVE_UP_MS)
    }

    private const val PROBE_MS = 250L
    private const val SETTLE_MS = 2_000L
    private const val STOP_SETTLE_MS = 250L
    private const val GIVE_UP_MS = 8_000L
}
