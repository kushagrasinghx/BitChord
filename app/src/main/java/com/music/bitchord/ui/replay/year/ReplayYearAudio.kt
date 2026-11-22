package com.music.bitchord.ui.replay.year

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AudioCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The year story's soundtrack: one short clip per card, each from its song's
 * best part, faded across from one card to the next.
 *
 * ## Why not the app's own player
 *
 * The clips must leave no trace. Through the main player they would land in the
 * queue and the history, count towards next year's Replay, scrobble, start
 * Automix analysing them and put each one in the notification. So the clips are
 * read straight off the same cache the lyrics share video cuts its audio from
 * ([AudioCache.opusShareSource]) into two players of their own that nothing else
 * knows about.
 *
 * ## Why two players
 *
 * So the next card's clip is already loaded and sitting at its start point
 * while this one plays. A single player would have to fetch and seek after the
 * card turned, and the reveal the clip is timed to would land in silence.
 * The two take turns: one is heard, the other waits.
 *
 * Audio focus is not asked for. The story pauses the app's own music itself
 * when it opens, and asking would make the system pause the app's session and
 * leave it waiting to be told to resume.
 *
 * Main thread only, like ExoPlayer.
 */
@OptIn(UnstableApi::class)
class YearClipPlayer(private val context: Context, private val scope: CoroutineScope) {

    /** A clip: what to read, and where to start. [key] identifies it across cards. */
    class Clip(val key: String, val startMs: Long, val source: () -> MediaSource)

    private class Deck(val player: ExoPlayer) {
        var key: String? = null
        var fade: Job? = null
    }

    private val decks = Array(2) { Deck(newPlayer()) }

    /** Which deck is the one being heard. */
    private var live = 0

    private var held = false

    var muted: Boolean = false
        set(value) {
            field = value
            val deck = decks[live]
            if (deck.key != null) fade(deck, if (value) 0f else 1f, MUTE_FADE_MS)
        }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false,
        )
        repeatMode = Player.REPEAT_MODE_OFF
        volume = 0f
    }

    /**
     * [song]'s audio from [startMs], or null when there is none to read: a row
     * from an addon has no YouTube recording to cut from, and the cache can't
     * open anything before the player service has set it up.
     */
    fun clipFor(song: Song, startMs: Long): Clip? {
        val id = song.videoId
        val key = "$id@$startMs"
        if (id.startsWith("content://") || id.startsWith("file://")) {
            return Clip(key, startMs) {
                DefaultMediaSourceFactory(context).createMediaSource(MediaItem.fromUri(Uri.parse(id)))
            }
        }
        if (!BestParts.isYouTubeId(id)) return null
        val (factory, uri) = AudioCache.opusShareSource(id) ?: return null
        return Clip(key, startMs) {
            DefaultMediaSourceFactory(factory).createMediaSource(MediaItem.fromUri(uri))
        }
    }

    /** Loads [clip] on the waiting deck, so [play] can start it without a gap. */
    fun prime(clip: Clip?) {
        if (clip == null || decks[live].key == clip.key) return
        val deck = decks[1 - live]
        if (deck.key == clip.key) return
        // The waiting deck is usually the one the last card is still fading out
        // on. Loading over it now would cut that fade dead, so it waits.
        val fading = deck.fade
        scope.launch {
            fading?.join()
            if (decks[live] !== deck && deck.key != clip.key) load(deck, clip)
        }
    }

    /**
     * Fades the clip being heard out and [clip] in. Null fades to silence, for
     * a card with nothing to play. Already playing [clip] carries on.
     */
    fun play(clip: Clip?) {
        val current = decks[live]
        if (clip != null && current.key == clip.key) {
            if (!held) current.player.play()
            return
        }
        stop(current, FADE_OUT_MS)
        if (clip == null) return
        val next = decks[1 - live]
        if (next.key != clip.key) load(next, clip)
        live = 1 - live
        next.player.volume = 0f
        if (!held) next.player.play()
        fade(next, if (muted) 0f else 1f, FADE_IN_MS)
    }

    /** A finger is on the card, or the share sheet is up: everything holds. */
    fun hold(holding: Boolean) {
        if (held == holding) return
        held = holding
        val deck = decks[live]
        if (deck.key == null) return
        if (holding) deck.player.pause() else deck.player.play()
    }

    /**
     * Fades out and lets go of both players.
     *
     * On a scope of its own, not [scope]: this runs as the story leaves the
     * screen, which is exactly when the story's scope is cancelled, and a fade
     * launched there would be cancelled with it, leaving a player that is
     * never released and still playing.
     */
    fun release() {
        val after = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        decks.forEach { deck ->
            deck.fade?.cancel()
            val player = deck.player
            if (!player.isPlaying) {
                player.release()
                return@forEach
            }
            after.launch {
                try {
                    ramp(player, 0f, FADE_OUT_MS)
                } finally {
                    player.release()
                }
            }
        }
    }

    private fun load(deck: Deck, clip: Clip) {
        deck.fade?.cancel()
        deck.player.volume = 0f
        deck.player.setMediaSource(clip.source(), clip.startMs)
        deck.player.playWhenReady = false
        deck.player.prepare()
        deck.key = clip.key
    }

    private fun stop(deck: Deck, millis: Long) {
        if (deck.key == null) return
        deck.fade?.cancel()
        deck.fade = scope.launch {
            ramp(deck.player, 0f, millis)
            deck.player.pause()
            deck.player.stop()
            deck.key = null
        }
    }

    private fun fade(deck: Deck, to: Float, millis: Long) {
        deck.fade?.cancel()
        deck.fade = scope.launch { ramp(deck.player, to, millis) }
    }

    /** Volume from wherever it is to [to], in small steps, a frame or so apart. */
    private suspend fun ramp(player: ExoPlayer, to: Float, millis: Long) {
        val from = player.volume
        val steps = (millis / STEP_MS).coerceAtLeast(1)
        for (i in 1..steps) {
            delay(STEP_MS)
            val t = i.toFloat() / steps
            // Rising fast and falling late: a straight line in both directions
            // sounds like a dip in the middle of the crossfade.
            val eased = if (to > from) kotlin.math.sqrt(t) else t * t
            player.volume = from + (to - from) * eased
        }
        player.volume = to
    }

    private companion object {
        const val FADE_IN_MS = 500L
        const val FADE_OUT_MS = 420L
        const val MUTE_FADE_MS = 200L
        const val STEP_MS = 16L
    }
}
