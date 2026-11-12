package com.music.bitchord.wear.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.music.bitchord.wear.WearProtocol as P

/**
 * This watch as the phone's speaker.
 *
 * Not a player with a mind of its own: it holds only the track the phone is on
 * and the next couple, plays what [P.PATH_SPEAKER] messages tell it, and reports
 * back. The phone keeps the queue and decides everything — the same deal a
 * Cast receiver gets — so the watch UI keeps showing the phone's state and
 * every control on it still goes to the phone. Audio comes through the phone
 * too: see [PhoneStreamDataSource].
 *
 * Main thread throughout; the listener service posts here.
 */
object Speaker {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var app: Context? = null
    private var player: ExoPlayer? = null

    /** A load that arrived before the service had built its player. */
    private var pendingLoad: DataMap? = null

    /** Keeps the service bound, and so alive, while the watch is the speaker. */
    private var binding: ListenableFuture<MediaController>? = null

    /** Brings the playback service up. From the app, while it is on screen. */
    fun prepare(context: Context) {
        val c = context.applicationContext
        app = c
        if (binding != null) return
        binding = MediaController.Builder(c, SessionToken(c, ComponentName(c, WatchPlaybackService::class.java)))
            .buildAsync()
    }

    /** The service's player, from its onCreate. */
    fun attach(context: Context, exoPlayer: ExoPlayer) {
        app = context.applicationContext
        player = exoPlayer
        exoPlayer.addListener(listener)
        pendingLoad?.let(::load)
        pendingLoad = null
    }

    fun detach(exoPlayer: ExoPlayer) {
        exoPlayer.removeListener(listener)
        if (player === exoPlayer) player = null
        main.removeCallbacks(reportNow)
        main.removeCallbacks(heartbeat)
    }

    /** One message from the phone. Any thread. */
    fun handle(context: Context, command: String, payload: ByteArray?) {
        main.post { handleOnMain(context.applicationContext, command, payload) }
    }

    private fun handleOnMain(context: Context, command: String, payload: ByteArray?) {
        app = context
        val text = payload?.let { String(it, Charsets.UTF_8) }.orEmpty()
        when (command) {
            P.SPEAKER_LOAD -> {
                val map = payload?.let { runCatching { DataMap.fromByteArray(it) }.getOrNull() } ?: return
                if (player == null) {
                    pendingLoad = map
                    prepare(context)
                } else {
                    load(map)
                }
            }
            P.SPEAKER_AHEAD -> {
                val p = player ?: return
                val map = payload?.let { runCatching { DataMap.fromByteArray(it) }.getOrNull() } ?: return
                reconcileAhead(p, map.tracks())
            }
            P.SPEAKER_JUMP -> {
                val p = player ?: return
                (0 until p.mediaItemCount).firstOrNull { p.getMediaItemAt(it).mediaId == text }
                    ?.let { p.seekToDefaultPosition(it) }
                p.play()
            }
            P.SPEAKER_PLAY -> player?.run {
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
            P.SPEAKER_PAUSE -> player?.pause()
            P.SPEAKER_SEEK -> text.toLongOrNull()?.let { player?.seekTo(it) }
            P.SPEAKER_REPEAT -> player?.repeatMode = watchRepeat(text.toIntOrNull() ?: Player.REPEAT_MODE_OFF)
            P.SPEAKER_VOLUME -> {
                adjustVolume(context, text)
                report()
            }
            P.SPEAKER_STOP -> {
                pendingLoad = null
                player?.run {
                    stop()
                    clearMediaItems()
                }
                binding?.let(MediaController::releaseFuture)
                binding = null
            }
        }
    }

    /**
     * Makes what follows the playing track match [wanted], keeping the part
     * that already does. Never a blanket remove-and-add: the next track is
     * usually buffered already — read into, even, for a gapless change — and
     * removing it throws that away and flushes the audio sink, which is a
     * stall at every change of song.
     */
    private fun reconcileAhead(p: ExoPlayer, wanted: List<MediaItem>) {
        val after = p.currentMediaItemIndex + 1
        val held = (after until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
        var same = 0
        while (same < held.size && same < wanted.size && held[same] == wanted[same].mediaId) same++
        if (same == held.size && same == wanted.size) return
        if (after + same < p.mediaItemCount) p.removeMediaItems(after + same, p.mediaItemCount)
        if (same < wanted.size) p.addMediaItems(wanted.drop(same))
    }

    private fun load(map: DataMap) {
        val p = player ?: return
        p.repeatMode = watchRepeat(map.getInt(P.KEY_REPEAT))
        p.setMediaItems(map.tracks(), 0, map.getLong(P.KEY_POSITION_MS))
        p.prepare()
        p.playWhenReady = map.getBoolean(P.KEY_PLAYING)
        report()
    }

    /**
     * The phone wraps the queue itself, so the watch never loops the few tracks
     * it holds; only repeat-one, which stays inside the track, is played here.
     */
    private fun watchRepeat(phoneMode: Int): Int =
        if (phoneMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

    private fun DataMap.tracks(): List<MediaItem> = getDataMapArrayList(P.KEY_TRACKS).orEmpty().map {
        MediaItem.Builder()
            .setMediaId(it.getString(P.KEY_MEDIA_ID).orEmpty())
            .setUri(it.getString(P.KEY_URI).orEmpty())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(it.getString(P.KEY_TITLE))
                    .setArtist(it.getString(P.KEY_ARTIST))
                    .build(),
            )
            .build()
    }

    private fun adjustVolume(context: Context, spec: String) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = when {
            spec.startsWith("=") -> spec.drop(1).toIntOrNull() ?: return
            else -> now + (spec.removePrefix("+").toIntOrNull() ?: return)
        }.coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    // ---- Telling the phone ----

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Coalesced: one transition is several events in a row.
            main.removeCallbacks(reportNow)
            main.postDelayed(reportNow, REPORT_COALESCE_MS)
        }
    }

    private val reportNow = Runnable { report() }

    /** While playing, a periodic report keeps the phone's extrapolated playhead honest. */
    private val heartbeat = Runnable { report() }

    private fun report() {
        val p = player ?: return
        val context = app ?: return
        val audio = context.getSystemService(AudioManager::class.java)
        val map = DataMap().apply {
            putString(P.KEY_MEDIA_ID, p.currentMediaItem?.mediaId.orEmpty())
            putInt(P.KEY_STATE, p.playbackState)
            putBoolean(P.KEY_PLAYING, p.playWhenReady)
            putBoolean(P.KEY_ADVANCING, p.isPlaying)
            putLong(P.KEY_POSITION_MS, p.currentPosition.coerceAtLeast(0L))
            putLong(P.KEY_DURATION_MS, p.duration.coerceAtLeast(0L))
            putInt(P.KEY_VOLUME, audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
            putInt(P.KEY_VOLUME_MAX, audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0)
        }
        val bytes = map.toByteArray()
        io.execute {
            val node = PhoneStreamDataSource.phoneNode(context) ?: return@execute
            runCatching {
                Tasks.await(
                    Wearable.getMessageClient(context).sendMessage(node, "${P.PATH_COMMAND}/${P.CMD_SPEAKER_STATUS}", bytes),
                    10, TimeUnit.SECONDS,
                )
            }
        }
        main.removeCallbacks(heartbeat)
        if (p.isPlaying) main.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    private const val REPORT_COALESCE_MS = 80L
    private const val HEARTBEAT_MS = 10_000L
}
