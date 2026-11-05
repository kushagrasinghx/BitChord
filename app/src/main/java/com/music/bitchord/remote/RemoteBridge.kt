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
import com.music.bitchord.data.model.BrowseItem
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // ── queued-by attribution ─────────────────────────────────────

    /**
     * Who queued each song from the web remote, as (videoId, username)
     * pairs in the order the songs were added. The media session has no
     * per-item owner field, so the bridge keeps this side list and labels
     * a snapshot by walking the live queue and consuming matches in
     * order — duplicate songs stay in the order their owners added them.
     * Entries the walk does not consume (song skipped or removed) drop
     * out, so the list can never mislabel a later re-add.
     *
     * [queuedByLock] guards the list because it is mutated from HTTP
     * worker threads while a snapshot (built on the main thread) reads
     * it. The lock is held only for the duration of a call — it never
     * spans the main-thread latch in [onMain].
     */
    private val queuedByLock = Any()
    private val queuedBy = mutableListOf<Pair<String, String>>()

    private fun noteQueued(song: Song, user: String?) {
        val name = user?.takeIf { it.isNotBlank() } ?: return
        synchronized(queuedByLock) { queuedBy.add(song.videoId to name) }
    }

    /** Removes entries belonging to a queue about to be replaced. */
    private fun clearQueuedBy() {
        synchronized(queuedByLock) { queuedBy.clear() }
    }

    /**
     * Labels up to [count] queue songs from [attributions], consuming
     * matches in order and keeping only the consumed entries, so the
     * stored list shrinks along with the queue.
     */
    private fun attributeQueueIds(ids: List<String>): List<String?> {
        synchronized(queuedByLock) {
            val pending = queuedBy.toMutableList()
            val kept = mutableListOf<Pair<String, String>>()
            val labels = ids.map { id ->
                val idx = pending.indexOfFirst { it.first == id }
                if (idx < 0) {
                    null
                } else {
                    val entry = pending.removeAt(idx)
                    kept.add(entry)
                    entry.second
                }
            }
            queuedBy.clear()
            queuedBy.addAll(kept)
            return labels
        }
    }

    // ── state ────────────────────────────────────────────────────────

    /** Full player snapshot for the web page, or null while unconnected. */
    fun snapshot(app: Context, requester: String? = null): JSONObject? = onMain {
        val c = controller ?: return@onMain null
        val audio = app.getSystemService(AudioManager::class.java)
        val current = c.currentMediaItem?.toSong()
        val count = minOf(c.mediaItemCount, MAX_QUEUE_ITEMS)
        val songs = ArrayList<Song>(count)
        for (i in 0 until count) {
            c.getMediaItemAt(i).toSong()?.let { songs.add(it) }
        }
        val labels = attributeQueueIds(songs.map { it.videoId })
        val queue = JSONArray()
        songs.forEachIndexed { i, song -> queue.put(songJson(song, labels.getOrNull(i))) }
        val userList = JSONArray()
        synchronized(queuedByLock) { queuedBy.map { it.second }.distinct().forEach { userList.put(it) } }
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
            if (current != null) {
                put("current", songJson(current, labels.getOrNull(c.currentMediaItemIndex)))
            } else {
                put("current", JSONObject.NULL)
            }
            put("queue", queue)
            put("users", userList)
            if (requester != null) put("you", requester)
        }
    }

    // ── commands ─────────────────────────────────────────────────────

    /** Returns false when the command was unknown or nothing is connected. */
    fun command(app: Context, body: JSONObject, user: String? = null): Boolean {
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
                    clearQueuedBy()
                    noteQueued(song, user)
                    scope.launch { c.playSongs(listOf(song), 0) }
                    true
                }
                "addSong" -> {
                    val song = songFromJson(body.optJSONObject("song")) ?: return@onMain false
                    c.addMediaItem(song.toMediaItem())
                    noteQueued(song, user)
                    true
                }
                "addSongNext" -> {
                    val song = songFromJson(body.optJSONObject("song")) ?: return@onMain false
                    val at = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
                    c.addMediaItem(at, song.toMediaItem())
                    noteQueued(song, user)
                    true
                }
                "addSongAt" -> {
                    // The queue editor's undo: put a just-removed song back
                    // where it was, keeping the name it was queued under —
                    // the page hands the label back in the song object, and
                    // only a blank one falls back to the caller.
                    val raw = body.optJSONObject("song")
                    val song = songFromJson(raw) ?: return@onMain false
                    val at = body.optInt("index", c.mediaItemCount).coerceIn(0, c.mediaItemCount)
                    c.addMediaItem(at, song.toMediaItem())
                    noteQueued(song, raw?.optString("queuedBy")?.takeIf { it.isNotBlank() } ?: user)
                    true
                }
                "move" -> {
                    // The same Player.moveMediaItem the app's own queue UI
                    // sends for a drag (a plain reorder, no drag bracket —
                    // that only batches party publishes during a drag).
                    val count = c.mediaItemCount
                    val from = body.optInt("from", -1)
                    val to = body.optInt("to", -1)
                    if (from !in 0 until count || to !in 0 until count) {
                        false
                    } else if (from == to) {
                        true
                    } else {
                        c.moveMediaItem(from, to)
                        true
                    }
                }
                "remove" -> {
                    val index = body.optInt("index", -1)
                    if (index !in 0 until c.mediaItemCount) {
                        false
                    } else {
                        c.removeMediaItem(index)
                        true
                    }
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

    // ── search & browse ──────────────────────────────────────────────

    /**
     * Search across everything: songs, artists, albums and playlists —
     * one filtered repository search per kind, run together, the same
     * searches the app's own search screen makes. The page groups the
     * answer by kind; the browse entries open through [browse].
     */
    suspend fun searchAll(query: String): JSONObject {
        val out = JSONObject()
            .put("songs", JSONArray())
            .put("artists", JSONArray())
            .put("albums", JSONArray())
            .put("playlists", JSONArray())
        if (query.isBlank()) return out
        val pages = coroutineScope {
            listOf(
                async { searchRows(query, SearchFilter.SONGS) },
                async { searchRows(query, SearchFilter.ARTISTS) },
                async { searchRows(query, SearchFilter.ALBUMS) },
                async { searchRows(query, SearchFilter.PLAYLISTS) },
            ).map { it.await() }
        }
        val songs = JSONArray()
        pages[0].forEach { row ->
            when (row) {
                is SearchResult.Track -> songs.put(songJson(row.song))
                is SearchResult.TopTrack -> songs.put(songJson(row.song))
                else -> Unit
            }
        }
        out.put("songs", songs)
        out.put("artists", browseJsonArray(pages[1], "artist"))
        out.put("albums", browseJsonArray(pages[2], "album"))
        out.put("playlists", browseJsonArray(pages[3], "playlist"))
        return out
    }

    private suspend fun searchRows(query: String, filter: SearchFilter): List<SearchResult> =
        YtMusicRepository.search(query, filter).getOrNull().orEmpty().take(MAX_SEARCH_RESULTS)

    private fun browseJsonArray(rows: List<SearchResult>, kind: String): JSONArray {
        val arr = JSONArray()
        rows.filterIsInstance<SearchResult.Browse>()
            .take(MAX_BROWSE_RESULTS)
            .forEach { arr.put(browseJson(it.item, kind)) }
        return arr
    }

    private fun browseJson(item: BrowseItem, kind: String): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("browseId", item.browseId)
        put("title", item.title)
        put("subtitle", item.subtitle)
        put("thumbnailUrl", item.thumbnailUrl ?: JSONObject.NULL)
    }

    /**
     * The page behind an artist, album or playlist card — the same page
     * tapping the card opens in the app: an artist page's songs for an
     * artist (with its album/single shelves as further cards), the
     * browse listing for an album or playlist. A missing or unknown
     * kind falls back to the browseId prefixes the app classifies by.
     */
    suspend fun browse(browseId: String, kind: String): JSONObject {
        val resolved = when {
            kind == "artist" || browseId.startsWith("UC") -> "artist"
            kind == "album" || browseId.startsWith("MPREb") ||
                browseId.startsWith("VLOLAK") || browseId.startsWith("OLAK") -> "album"
            else -> "playlist"
        }
        var title: String? = null
        var subtitle: String? = null
        val sections = JSONArray()
        val songs: List<Song> = if (resolved == "artist") {
            YtMusicRepository.artistPage(browseId).fold(
                onSuccess = { page ->
                    title = page.name
                    page.sections.forEach { shelf ->
                        val items = JSONArray()
                        shelf.items.forEach { item ->
                            val id = item.browseId ?: return@forEach
                            items.put(
                                JSONObject()
                                    .put("browseId", id)
                                    .put("title", item.title)
                                    .put("subtitle", item.subtitle)
                                    .put("thumbnailUrl", item.thumbnailUrl ?: JSONObject.NULL),
                            )
                        }
                        if (items.length() > 0) {
                            sections.put(JSONObject().put("title", shelf.title).put("items", items))
                        }
                    }
                    page.songs
                },
                onFailure = { emptyList() },
            )
        } else {
            YtMusicRepository.browseSongs(browseId).fold(
                onSuccess = { page ->
                    title = page.header?.title
                    subtitle = page.header?.subtitle
                    page.songs
                },
                onFailure = { emptyList() },
            )
        }
        val arr = JSONArray()
        songs.take(MAX_BROWSE_SONGS).forEach { arr.put(songJson(it)) }
        return JSONObject().apply {
            put("kind", resolved)
            put("browseId", browseId)
            if (title != null) put("title", title)
            if (subtitle != null) put("subtitle", subtitle)
            put("songs", arr)
            put("sections", sections)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun songJson(song: Song, queuedBy: String? = null): JSONObject = JSONObject().apply {
        put("videoId", song.videoId)
        put("title", song.title)
        put("artist", song.artist)
        put("thumbnailUrl", song.thumbnailUrl ?: JSONObject.NULL)
        put("durationText", song.durationText ?: JSONObject.NULL)
        if (queuedBy != null) put("queuedBy", queuedBy)
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
    private const val MAX_BROWSE_RESULTS = 15
    private const val MAX_BROWSE_SONGS = 200
}
