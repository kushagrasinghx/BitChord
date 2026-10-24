package com.music.bitchord.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.shared.wear.RawLyricLine
import com.music.bitchord.shared.wear.RawSyllable
import com.music.bitchord.shared.wear.SyncedLyricsPayload
import com.music.bitchord.shared.wear.WearSyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Sends the current track's synced lyrics to the watch.
 *
 * Only the lyrics travel here. What is playing, and where, already reaches the watch through
 * [WearBridge] (NowPlaying), and the watch's lyrics screen runs its clock from that.
 *
 *  - [publish] is called by PlaybackService each time its own lyrics lookup finishes
 *    (track change, settings change). It builds a [SyncedLyricsPayload] and puts it on the Data
 *    Layer as a gzip asset. A (track, content hash) already delivered is not sent again; if the
 *    watch lost its copy it asks, see [onMessageReceived].
 *  - a track with no word- or line-timed lyrics is announced as "none" so the watch can say so.
 *  - [start]/[stop] bracket the service's life and register the listener for the watch's request.
 */
object WearLyricsSync : MessageClient.OnMessageReceivedListener {

    private const val TAG = "WearLyricsSync"
    private const val TIMEOUT_S = 10L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var publishJob: Job? = null
    private var listening = false

    @Volatile private var current: SyncedLyricsPayload? = null
    @Volatile private var noneFor: String? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var delivered: DeliveredLog? = null

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (delivered == null) delivered = DeliveredLog(app)
        if (listening) return
        listening = true
        Wearable.getMessageClient(app).addListener(this)
    }

    fun stop(context: Context) {
        publishJob?.cancel()
        if (!listening) return
        listening = false
        Wearable.getMessageClient(context.applicationContext).removeListener(this)
    }

    /**
     * [lines] null or without any timing means "no synced lyrics". Safe to call from the main
     * thread; the work runs on its own IO scope and a newer call replaces an unfinished one.
     */
    fun publish(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        durationMs: Long,
        lines: List<LyricLine>?,
    ) {
        if (mediaId.isEmpty()) return
        val app = context.applicationContext
        appContext = app
        if (delivered == null) delivered = DeliveredLog(app)

        publishJob?.cancel()
        publishJob = scope.launch {
            try {
                // Same test the lookup uses: unsynced lyrics have no timing at all (timeMs == 0
                // everywhere). A first line that really starts at 0 is kept.
                val usable = lines.orEmpty().filter { it.text.isNotBlank() }
                if (usable.none { it.timeMs > 0 }) {
                    current = null
                    noneFor = mediaId
                    putNone(app, mediaId)
                    return@launch
                }
                val payload = SyncedLyricsPayload.build(
                    trackId = mediaId,
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    raw = usable.map { it.toRaw() },
                )
                current = payload
                noneFor = null
                val log = delivered
                if (log == null || !log.contains(mediaId, payload.contentHash)) {
                    if (putPayload(app, payload)) log?.add(mediaId, payload.contentHash)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "publish failed for $mediaId", e)
            }
        }
    }

    /**
     * Watch -> phone:
     *  - a clock ping: answered at once with this phone's clock, so the watch can tell how far its
     *    own wall clock is from the one that stamps every playback reading it receives
     *  - a lyrics request: the watch lost (or never had) the lyrics of a track; send them again,
     *    whatever the delivered log says
     */
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearSyncPaths.CLOCK_PING) {
            answerClockPing(event)
            return
        }
        if (event.path != WearSyncPaths.LYRICS_REQUEST) return
        val requested = String(event.data, Charsets.UTF_8)
        val app = appContext ?: return
        scope.launch {
            try {
                val payload = current
                when {
                    payload != null && payload.trackId == requested -> putPayload(app, payload)
                    noneFor == requested -> putNone(app, requested)
                    // Otherwise the lookup is still running; its result is published when it lands.
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "answering lyrics request failed", e)
            }
        }
    }

    // ---------------------------------------------------------------------------------

    private fun answerClockPing(event: MessageEvent) {
        val app = appContext ?: return
        val data = event.data
        if (data.size < 8) return
        val reply = ByteBuffer.allocate(16)
            .put(data, 0, 8) // the watch's own send time, echoed back
            .putLong(System.currentTimeMillis()) // this phone's clock, read as late as possible
            .array()
        Wearable.getMessageClient(app).sendMessage(event.sourceNodeId, WearSyncPaths.CLOCK_PONG, reply)
    }

    private fun putPayload(app: Context, payload: SyncedLyricsPayload): Boolean = try {
        val request = PutDataMapRequest.create(WearSyncPaths.LYRICS_PAYLOAD).apply {
            dataMap.putString(WearSyncPaths.KEY_TRACK_ID, payload.trackId)
            dataMap.putString(WearSyncPaths.KEY_HASH, payload.contentHash)
            dataMap.putAsset(WearSyncPaths.KEY_ASSET, Asset.createFromBytes(payload.toGzipBytes()))
            // Always differs, so a forced re-send still fires onDataChanged on the watch.
            dataMap.putLong(WearSyncPaths.KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Tasks.await(Wearable.getDataClient(app).putDataItem(request), TIMEOUT_S, TimeUnit.SECONDS)
        true
    } catch (e: Exception) {
        Log.w(TAG, "putDataItem failed", e)
        false
    }

    private fun putNone(app: Context, mediaId: String) {
        try {
            val request = PutDataMapRequest.create(WearSyncPaths.LYRICS_PAYLOAD).apply {
                dataMap.putString(WearSyncPaths.KEY_TRACK_ID, mediaId)
                dataMap.putString(WearSyncPaths.KEY_HASH, WearSyncPaths.NO_LYRICS_HASH)
                dataMap.putLong(WearSyncPaths.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(app).putDataItem(request), TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "putDataItem (none) failed", e)
        }
    }

    /**
     * The only place that knows the shape of [LyricLine]. A word with two or more syllables is
     * sent syllable by syllable, any other word as one piece; the payload builder derives the
     * character ranges and falls back to word-level timing for a line without word timing.
     */
    private fun LyricLine.toRaw(): RawLyricLine {
        val parts = ArrayList<RawSyllable>()
        for (word in words) {
            if (word.syllables.size >= 2) {
                for (s in word.syllables) {
                    val from = s.startChar.coerceIn(0, word.text.length)
                    val to = s.endChar.coerceIn(from, word.text.length)
                    val piece = word.text.substring(from, to)
                    if (piece.isNotBlank()) parts += RawSyllable(piece, s.startMs, s.endMs)
                }
            } else if (word.text.isNotBlank()) {
                parts += RawSyllable(word.text, word.startMs, word.endMs)
            }
        }
        return RawLyricLine(text = text, startMs = timeMs, endMs = sungUntilMs, syllables = parts)
    }

    /** Persistent, bounded, ordered record of "(track, hash) already pushed to the Data Layer". */
    private class DeliveredLog(context: Context) {
        private val prefs = context.getSharedPreferences("wear_lyrics_sync", Context.MODE_PRIVATE)
        private val entries = ArrayDeque<String>(
            prefs.getString(KEY, "").orEmpty().split('\n').filter { it.isNotEmpty() },
        )

        @Synchronized
        fun contains(trackId: String, hash: String) = entries.contains(key(trackId, hash))

        @Synchronized
        fun add(trackId: String, hash: String) {
            val k = key(trackId, hash)
            entries.remove(k)
            entries.addLast(k)
            while (entries.size > MAX) entries.removeFirst()
            prefs.edit().putString(KEY, entries.joinToString("\n")).apply()
        }

        private fun key(trackId: String, hash: String) = "${trackId.replace('\n', ' ')}#$hash"

        private companion object {
            const val KEY = "delivered"
            const val MAX = 150
        }
    }
}
