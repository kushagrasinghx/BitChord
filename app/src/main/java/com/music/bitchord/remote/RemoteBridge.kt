package com.music.bitchord.remote

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import com.music.bitchord.playback.toggleShuffle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The web remote's hands on the player: a [MediaController] into
 * [PlaybackService], held for as long as the remote server runs.
 *
 * This is deliberately the same door the app's own UI and the Wear bridge
 * use — the media session, through the service's `SessionPlayer` — so a
 * tap in a browser is indistinguishable from a tap in the app: it counts
 * as this device's own intent (a Listen Together party hears it, the
 * notification updates, Android Auto follows). Nothing here reaches under
 * the session to the raw ExoPlayer.
 *
 * Controller calls have to happen on the main thread; the HTTP worker
 * threads that call in here hop over and wait for the answer.
 */
object RemoteBridge {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    @Volatile private var controller: MediaController? = null

    fun connect(app: Context) {
        main.post {
            if (controller != null || future != null) return@post
            future = MediaController.Builder(
                app,
                SessionToken(app, ComponentName(app, PlaybackService::class.java)),
            ).buildAsync().also { f ->
                f.addListener(
                    {
                        controller = runCatching { f.get() }.getOrNull()?.takeIf { it.isConnected }
                        if (controller == null) future = null
                    },
                    ContextCompat.getMainExecutor(app),
                )
            }
        }
    }

    fun disconnect() {
        main.post {
            future?.let(MediaController::releaseFuture)
            future = null
            controller = null
        }
    }

    fun isConnected(): Boolean = controller != null

    // ── state ────────────────────────────────────────────────────────

    /** Full player snapshot for the web page, or null while unconnected. */
    fun snapshot(app: Context): JSONObject? = onMain {
        val c = controller ?: return@onMain null
        val audio = app.getSystemService(AudioManager::class.java)
        val current = c.currentMediaItem?.toSong()
        val queue = JSONArray()
        val count = minOf(c.mediaItemCount, MAX_QUEUE_ITEMS)
        for (i in 0 until count) {
            queue.put(songJson(c.getMediaItemAt(i).toSong()))
        }
        JSONObject().apply {
            put("connected", true)
            put("playing", c.isPlaying)
            put("loading", c.playbackState == Player.STATE_BUFFERING)
            put("positionMs", c.currentPosition.coerceAtLeast(0L))
            put("durationMs", c.duration.coerceAtLeast(0L))
            put("index", c.currentMediaItemIndex)
            put("queueSize", c.mediaItemCount)
            put("shuffle", c.shuffleModeEnabled)
            put("repeat", c.repeatMode)
            put("hasNext", c.hasNextMediaItem())
            put("hasPrevious", c.hasPreviousMediaItem())
            if (audio != null) {
                put("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                put("volumeMax", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            }
            if (current != null) put("current", songJson(current)) else put("current", JSONObject.NULL)
            put("queue", queue)
        }
    }

    // ── commands ─────────────────────────────────────────────────────

    /** Returns false when the command was unknown or nothing is connected. */
    fun command(app: Context, body: JSONObject): Boolean {
        val action = body.optString("action")
        if (action == "search") return false // search is its own route
        return onMain {
            val c = controller ?: return@onMain false
            when (action) {
                "play" -> {
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    c.play()
                    true
                }
                "pause" -> {
                    c.pause()
                    true
                }
                "toggle" -> {
                    if (c.isPlaying) c.pause() else {
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        c.play()
                    }
                    true
                }
                "next" -> {
                    c.seekToNextMediaItem()
                    true
                }
                "prev" -> {
                    c.seekToPreviousMediaItem()
                    true
                }
                "seek" -> {
                    c.seekTo(body.optLong("ms", 0L).coerceAtLeast(0L))
                    true
                }
                "playIndex" -> {
                    val index = body.optInt("index", -1)
                    if (index < 0 || index >= c.mediaItemCount) {
                        false
                    } else {
                        c.seekToDefaultPosition(index)
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        c.play()
                        true
                    }
                }
                "playSong" -> {
                    val song = songFromJson(body.optJSONObject("song")) ?: return@onMain false
                    scope.launch { c.playSongs(listOf(song), 0) }
                    true
                }
                "addSong" -> {
                    val song = songFromJson(body.optJSONObject("song")) ?: return@onMain false
                    c.addMediaItem(song.toMediaItem())
                    true
                }
                "addSongNext" -> {
                    val song = songFromJson(body.optJSONObject("song")) ?: return@onMain false
                    val at = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
                    c.addMediaItem(at, song.toMediaItem())
                    true
                }
                "volume" -> {
                    val audio = app.getSystemService(AudioManager::class.java)
                    if (audio == null) {
                        false
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val level = (body.optDouble("level", -1.0) * max).roundToInt()
                        if (level < 0) {
                            false
                        } else {
                            audio.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                level.coerceIn(0, max),
                                0,
                            )
                            true
                        }
                    }
                }
                "shuffle" -> {
                    c.toggleShuffle()
                    true
                }
                "repeat" -> {
                    c.repeatMode = when (c.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    true
                }
                else -> false
            }
        } ?: false
    }

    // ── search ───────────────────────────────────────────────────────

    /** YouTube Music song search, the same repository the app searches with. */
    suspend fun search(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val page = YtMusicRepository.searchPage(query, SearchFilter.SONGS).getOrNull()
            ?: return emptyList()
        return page.rows.mapNotNull { row ->
            when (row) {
                is SearchResult.Track -> row.song
                is SearchResult.TopTrack -> row.song
                else -> null
            }
        }.take(MAX_SEARCH_RESULTS)
    }

    fun searchJson(songs: List<Song>): JSONObject {
        val results = JSONArray()
        for (song in songs) results.put(songJson(song))
        return JSONObject().put("results", results)
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun songJson(song: Song): JSONObject = JSONObject().apply {
        put("videoId", song.videoId)
        put("title", song.title)
        put("artist", song.artist)
        put("thumbnailUrl", song.thumbnailUrl ?: JSONObject.NULL)
        put("durationText", song.durationText ?: JSONObject.NULL)
    }

    private fun songFromJson(o: JSONObject?): Song? {
        if (o == null) return null
        val videoId = o.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        return Song(
            videoId = videoId,
            title = o.optString("title").ifBlank { "Unknown title" },
            artist = o.optString("artist"),
            thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotBlank() },
            durationText = o.optString("durationText").takeIf { it.isNotBlank() },
        )
    }

    /** Runs [block] on the main thread and waits briefly for its result. */
    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        main.post {
            result = runCatching { block() }.getOrNull()
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        return result
    }

    private const val MAX_QUEUE_ITEMS = 200
    private const val MAX_SEARCH_RESULTS = 30
}
