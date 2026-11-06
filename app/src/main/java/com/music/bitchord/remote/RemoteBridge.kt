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
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * The attribution walk, pure: given the queue's videoIds in order and
 * the (videoId, name) entries recorded for web-queued songs, returns
 * the label for each queue position (null where a song carries none)
 * and the entries the walk consumed. Matching consumes entries in
 * order, so duplicate songs keep the order their owners added them;
 * entries the walk does not consume belong to songs no longer queued
 * and are dropped by the caller. The walk runs over the WHOLE queue,
 * not just the page's visible slice, so a label on a song past the
 * display cap survives until the song itself leaves.
 */
internal fun attributeIds(
    queueIds: List<String>,
    entries: List<Pair<String, String>>,
): Pair<List<String?>, List<Pair<String, String>>> {
    val pending = entries.toMutableList()
    val kept = mutableListOf<Pair<String, String>>()
    val labels = queueIds.map { id ->
        val idx = pending.indexOfFirst { it.first == id }
        if (idx < 0) {
            null
        } else {
            val entry = pending.removeAt(idx)
            kept.add(entry)
            entry.second
        }
    }
    return labels to kept
}

/**
 * Which kind of page a browse card opens, pure: an explicit kind from
 * the search results wins; otherwise the browseId prefixes the app
 * itself classifies by decide. Order matters — `VLOLAK` (an album in
 * a playlist-shaped wrapper) must be tested before the plain `VL`
 * playlist prefix.
 */
internal fun resolveBrowseKind(browseId: String, kind: String): String = when {
    kind == "artist" || browseId.startsWith("UC") -> "artist"
    kind == "album" || browseId.startsWith("MPREb") ||
        browseId.startsWith("VLOLAK") || browseId.startsWith("OLAK") -> "album"
    else -> "playlist"
}

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
            // Labels, the undo record and cached lyrics live exactly as
            // long as the server does — the docs promise it, so clear
            // them here rather than letting the next start inherit them.
            synchronized(queuedByLock) { queuedBy.clear() }
            lyricsCache.clear()
            lastRemoved = null
        }
    }

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
     * Labels the queue from the stored entries via [attributeIds],
     * keeping only the entries the walk consumed, so the stored list
     * shrinks along with the queue.
     */
    private fun attributeQueueIds(ids: List<String>): List<String?> {
        synchronized(queuedByLock) {
            val (labels, kept) = attributeIds(ids, queuedBy.toList())
            queuedBy.clear()
            queuedBy.addAll(kept)
            return labels
        }
    }

    /** The label at [index] without consuming anything (a copy is walked). */
    private fun peekLabel(ids: List<String>, index: Int): String? {
        val copy = synchronized(queuedByLock) { queuedBy.toList() }
        return attributeIds(ids, copy).first.getOrNull(index)
    }

    /**
     * The most recent removal made through the web remote, kept for the
     * page's Undo: the bridge — not the page — remembers the label the
     * song carried, so a re-insert restores the original attribution
     * from server-side truth instead of trusting the request body.
     * Only ever touched inside [onMain] blocks, so it is main-thread
     * confined like the controller itself.
     */
    private data class RemovedSong(val song: Song, val label: String?)
    private var lastRemoved: RemovedSong? = null

    // ── state ────────────────────────────────────────────────────────

    /** Full player snapshot for the web page, or null while unconnected. */
    fun snapshot(app: Context, requester: String? = null): JSONObject? = onMain {
        val c = controller ?: return@onMain null
        val audio = app.getSystemService(AudioManager::class.java)
        val current = c.currentMediaItem?.toSong()
        // Attribution walks the whole queue so a label past the display
        // cap is not pruned while its song is still queued; only the
        // JSON is capped, and labels stay indexed by queue position.
        val all = ArrayList<Song?>(c.mediaItemCount)
        for (i in 0 until c.mediaItemCount) {
            all.add(c.getMediaItemAt(i).toSong())
        }
        val labels = attributeQueueIds(all.map { it?.videoId ?: "" })
        val queue = JSONArray()
        var shown = 0
        all.forEachIndexed { i, song ->
            if (song != null && shown < MAX_QUEUE_ITEMS) {
                queue.put(songJson(song, labels.getOrNull(i)))
                shown++
            }
        }
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
                    // where it was, with the label it carried. The label
                    // comes from the bridge's own removal record when the
                    // song matches it — never from the request body, which
                    // any joined user could fill with someone else's name.
                    val song = songFromJson(body.optJSONObject("song")) ?: return@onMain false
                    val stash = lastRemoved?.takeIf { it.song.videoId == song.videoId }
                    if (stash != null) lastRemoved = null
                    var at = body.optInt("index", c.mediaItemCount).coerceIn(0, c.mediaItemCount)
                    // The page names the song that used to follow the
                    // removed one; if it is no longer at the index, the
                    // queue moved since the removal and the song goes at
                    // the end rather than landing mid-list at random.
                    val beforeId = body.optString("beforeVideoId")
                    if (beforeId.isNotBlank() && at < c.mediaItemCount &&
                        c.getMediaItemAt(at).toSong()?.videoId != beforeId
                    ) {
                        at = c.mediaItemCount
                    }
                    c.addMediaItem(at, song.toMediaItem())
                    noteQueued(song, stash?.label ?: user)
                    true
                }
                "move" -> {
                    // The same Player.moveMediaItem the app's own queue UI
                    // sends for a drag (a plain reorder, no drag bracket —
                    // that only batches party publishes during a drag).
                    // The page names the song it means (fromId): with
                    // several people editing, positions shift between a
                    // page's render and its tap, and moving the wrong
                    // song is worse than refusing the move.
                    val count = c.mediaItemCount
                    val from = body.optInt("from", -1)
                    val to = body.optInt("to", -1)
                    val fromId = body.optString("fromId")
                    if (from !in 0 until count || to !in 0 until count) {
                        false
                    } else if (fromId.isNotBlank() &&
                        c.getMediaItemAt(from).toSong()?.videoId != fromId
                    ) {
                        false
                    } else if (from == to) {
                        true
                    } else {
                        c.moveMediaItem(from, to)
                        true
                    }
                }
                "remove" -> {
                    // The page names the song it means (videoId) for the
                    // same reason as move: an index alone can point at a
                    // different song by the time the tap lands.
                    val index = body.optInt("index", -1)
                    if (index !in 0 until c.mediaItemCount) {
                        false
                    } else {
                        val expected = body.optString("videoId")
                        val song = c.getMediaItemAt(index).toSong()
                        if (expected.isNotBlank() && song?.videoId != expected) {
                            false
                        } else {
                            if (song != null) {
                                val ids = (0 until c.mediaItemCount).map {
                                    c.getMediaItemAt(it).toSong()?.videoId ?: ""
                                }
                                lastRemoved = RemovedSong(song, peekLabel(ids, index))
                            }
                            c.removeMediaItem(index)
                            true
                        }
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
        val resolved = resolveBrowseKind(browseId, kind)
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

    // ── lyrics ───────────────────────────────────────────────────────

    /** videoId → the lyrics answer for it, so several browsers opening
     *  the panel pay the provider lookup once per track. */
    private val lyricsCache = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()

    /**
     * Serializes the cache-check → lookup → store sequence: without it,
     * two browsers opening the panel at the same moment both miss the
     * cache and both pay the provider race. The second simply waits
     * and then reads the first's answer.
     */
    private val lyricsMutex = Mutex()

    /**
     * A fingerprint of the lyrics settings the cache was filled under.
     * Change the sources or their order in Settings and the next call
     * sees a different fingerprint and starts the cache over, instead
     * of serving answers (or misses) the old settings produced.
     */
    @Volatile private var lyricsSettingsKey: String? = null

    private fun currentLyricsSettingsKey(): String = listOf(
        AppSettings.lyricsSources.value.joinToString(","),
        AppSettings.lyricsSourceOrder.value.joinToString(","),
        AppSettings.prioritizeSyllableSync.value.toString(),
    ).joinToString("|")

    /**
     * Lyrics for the track playing right now, from the same repository
     * and the same source settings the app's own lyrics view uses.
     * Lines carry their timestamps (`synced` when any line is timed);
     * an empty-text line is an instrumental gap. While the track's
     * duration is still unknown the answer is `pending` rather than
     * "none" — providers match on duration, and it lands a beat after
     * the track does.
     */
    suspend fun lyrics(): JSONObject {
        val c = controller ?: return JSONObject().put("found", false)
        val song = onMain { c.currentMediaItem?.toSong() } ?: return JSONObject().put("found", false)
        return lyricsMutex.withLock {
            val key = currentLyricsSettingsKey()
            if (key != lyricsSettingsKey) {
                lyricsCache.clear()
                lyricsSettingsKey = key
            }
            lyricsCache[song.videoId]?.let { return@withLock it }
            val durationMs = onMain { c.duration } ?: 0L
            if (durationMs <= 0L) {
                return@withLock JSONObject()
                    .put("found", false)
                    .put("pending", true)
                    .put("videoId", song.videoId)
            }
            val found = LyricsRepository.lyrics(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationMs = durationMs,
                sources = AppSettings.lyricsSources.value,
                order = AppSettings.lyricsSourceOrder.value,
                prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
            )
            val out = JSONObject().put("videoId", song.videoId)
            if (found == null) {
                out.put("found", false)
            } else {
                val lines = JSONArray()
                var synced = false
                for (line in found.lines) {
                    if (line.timeMs > 0) synced = true
                    lines.put(JSONObject().put("at", line.timeMs).put("text", line.text))
                }
                out.put("found", true)
                out.put("synced", synced)
                out.put("source", found.source.label)
                out.put("lines", lines)
            }
            if (lyricsCache.size > 64) lyricsCache.clear()
            lyricsCache[song.videoId] = out
            out
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
