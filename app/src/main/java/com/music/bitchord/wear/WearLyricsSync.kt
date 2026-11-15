package com.music.bitchord.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.wear.lyrics.RawBackground
import com.music.bitchord.wear.lyrics.RawLyricLine
import com.music.bitchord.wear.lyrics.RawSyllable
import com.music.bitchord.wear.lyrics.SyncedLyricsPayload
import com.music.bitchord.wear.lyrics.WearSyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withPermit
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Sends the current track's synced lyrics to the watch.
 *
 * Only the lyrics travel here. What is playing, and where, already reaches the watch through
 * [WearBridge] (NowPlaying), and the watch's lyrics screen runs its clock from that.
 *
 *  - [publish] is called by PlaybackService each time its own lyrics lookup finishes
 *    (track change, settings change). It builds a [SyncedLyricsPayload] and puts it on the Data
 *    Layer as a gzip asset. The item always holds the latest lyrics; only an identical repeat is
 *    skipped. If the watch lost its copy it asks, see [onMessageReceived].
 *  - a track with no word- or line-timed lyrics is announced as "none" so the watch can say so.
 *  - lyrics the player picked or synced by hand ([publish] with fromUi) win over the service's own
 *    lookup for that track.
 *  - the watch can ask for a translation or a Latin-script reading of the current lyrics; they are
 *    produced by the phone's own [LyricsTranslation] (same cache, same target language) and sent
 *    line by line, aligned to the payload.
 *  - [start]/[stop] bracket the service's life and register the listener for the watch's request.
 */
object WearLyricsSync : MessageClient.OnMessageReceivedListener {

    /** Where the phone's player is right now. Read on the main thread, see [playhead]. */
    class Playhead(val positionMs: Long, val advancing: Boolean, val speed: Float)

    /**
     * Set by PlaybackService: how to read the active player. A clock ping is answered with it, so
     * the watch can measure the phone's real position instead of inferring it from stamps.
     */
    @Volatile var playhead: (() -> Playhead?)? = null

    private const val TAG = "WearLyricsSync"
    private const val TIMEOUT_S = 10L
    private const val PROBE_PARALLEL = 10
    private const val RETRY_DELAY_MS = 600L

    /** The list counts as complete after this, whatever is still running; stragglers join it later. */
    private const val FIRST_PASS_MS = 9_000L
    private const val PUT_DEBOUNCE_MS = 350L

    /** The watch counts as "open" this long after its last message (its screen pings every 15 s). */
    private const val WATCH_ACTIVE_MS = 90_000L
    private const val SELECT_REFRESH_MS = 800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var publishJob: Job? = null
    private var listening = false

    @Volatile private var current: SyncedLyricsPayload? = null
    @Volatile private var noneFor: String? = null

    /** What the current payload was built from: the lines as the lookup returned them, and for each payload line its index in them. */
    @Volatile private var currentTrackId: String? = null
    @Volatile private var currentLines: List<LyricLine> = emptyList()
    @Volatile private var currentOrder: IntArray = IntArray(0)

    /** A track whose lyrics the player itself chose; the service's own lookup no longer overrides them. */
    @Volatile private var uiTrackId: String? = null

    /** What the watch is showing, so a different lyrics source re-sends it. */
    @Volatile private var wantedKind: String? = null
    @Volatile private var wantedTrack: String? = null
    private var extraJob: Job? = null

    /** What the current track is called, for looking its lyrics up in other sources. */
    @Volatile private var currentTitle = ""
    @Volatile private var currentArtist = ""
    @Volatile private var currentDuration = 0L
    @Volatile private var currentAlbum: String? = null
    @Volatile private var wantedLang: String? = null

    /** Sources found to have synced lyrics for [sourcesTrackId], filled when the watch opens the list. */
    private class SourceHit(val source: LyricsSource, val result: LyricsRepository.Result, val kind: String, val hash: String)

    private val sourceHits = ConcurrentHashMap<LyricsSource, SourceHit>()
    @Volatile private var sourcesTrackId: String? = null
    @Volatile private var sourcesDone = false
    private var sourcesJob: Job? = null
    @Volatile private var lastWatchContactMs = 0L
    @Volatile private var putPending = false
    private var offsetJob: Job? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var lastPutKey: String? = null

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (listening) return
        listening = true
        Wearable.getMessageClient(app).addListener(this)

        // The player's own "lyrics offset" (Settings / the offset sheet) is mirrored to the watch.
        offsetJob?.cancel()
        offsetJob = scope.launch { AppSettings.lyricsOffsetMs.collect { putOffset(app, it) } }
    }

    fun stop(context: Context) {
        publishJob?.cancel()
        offsetJob?.cancel()
        playhead = null
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
        album: String? = null,
        fromUi: Boolean = false,
    ) {
        if (mediaId.isEmpty()) return
        if (fromUi) uiTrackId = mediaId else if (uiTrackId == mediaId) return
        val app = context.applicationContext
        appContext = app
        currentTitle = title
        currentArtist = artist
        currentDuration = durationMs
        currentAlbum = album
        if (sourcesTrackId != mediaId) {
            sourcesJob?.cancel()
            sourceHits.clear()
            sourcesTrackId = null
            sourcesDone = false
        }

        publishJob?.cancel()
        publishJob = scope.launch {
            try {
                // Same test the lookup uses: unsynced lyrics have no timing at all (timeMs == 0
                // everywhere). A first line that really starts at 0 is kept.
                val usable = lines.orEmpty().filter { it.text.isNotBlank() }
                if (usable.none { it.timeMs > 0 }) {
                    current = null
                    noneFor = mediaId
                    currentTrackId = mediaId
                    currentLines = emptyList()
                    currentOrder = IntArray(0)
                    lastPutKey = "none#$mediaId"
                    putNone(app, mediaId)
                    return@launch
                }
                val payload = buildPayload(mediaId, lines.orEmpty())
                current = payload
                noneFor = null
                // Each payload line remembers which of the original lines it came from (-1 for a gap
                // that was invented), which is what lines translations up.
                currentTrackId = mediaId
                currentLines = lines.orEmpty()
                currentOrder = payload.lines.map { it.sourceIndex }.toIntArray()
                // The Data Layer item is what the watch reads, so it has to hold THIS payload. A source
                // switched back to one used earlier has the hash of a delivery long ago, while the
                // item now holds something else, so "delivered before" is the wrong question. Only
                // an identical repeat (the service and the player both report the same lines) is
                // skipped; the asset is content-addressed, so bytes the watch has are not re-sent.
                val key = "$mediaId#${payload.contentHash}"
                if (key != lastPutKey && putPayload(app, payload)) lastPutKey = key
                // The watch is showing a translation: these are different lyrics now.
                // With the watch's lyrics screen open the source list is worked out now, so it is
                // ready by the time anyone opens it.
                if (System.currentTimeMillis() - lastWatchContactMs < WATCH_ACTIVE_MS) handleSourcesRequest(mediaId)
                val kind = wantedKind
                if (kind != null && wantedTrack == mediaId) sendExtra(app, mediaId, kind)
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
     *    whatever was sent before
     */
    override fun onMessageReceived(event: MessageEvent) {
        lastWatchContactMs = System.currentTimeMillis()
        if (event.path == WearSyncPaths.CLOCK_PING) {
            answerClockPing(event)
            return
        }
        if (event.path == WearSyncPaths.LYRICS_TRANSFORM) {
            val parts = String(event.data, Charsets.UTF_8).split('\n')
            if (parts.size >= 2) handleTransform(parts[0], parts[1], parts.getOrNull(2))
            return
        }
        if (event.path == WearSyncPaths.LYRICS_SOURCES_REQUEST) {
            handleSourcesRequest(String(event.data, Charsets.UTF_8))
            return
        }
        if (event.path == WearSyncPaths.LYRICS_SELECT) {
            val parts = String(event.data, Charsets.UTF_8).split('\n')
            if (parts.size >= 2) handleSelect(parts[0], parts[1])
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

    // ---------------------------------------------------------------------------------
    // Choosing the lyrics source from the watch
    // ---------------------------------------------------------------------------------

    /**
     * The watch opened its source list. Every enabled source is asked once for this track (twice if
     * the first answer is empty, so a hiccup does not hide a source that has the lyrics), and only
     * those that answer with synced lyrics are listed, as they come in.
     */
    private fun handleSourcesRequest(trackId: String) {
        val app = appContext ?: return
        if (currentTrackId != trackId) return // lyrics not looked up yet; the watch asks again
        if (sourcesTrackId != trackId) {
            sourceHits.clear()
            sourcesTrackId = trackId
            sourcesDone = false
        }
        if (sourcesDone || sourcesJob?.isActive == true) {
            scope.launch { putSources(app, trackId, sourcesDone) }
            return
        }
        sourcesJob = scope.launch {
            try {
                probeSources(app, trackId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "listing sources failed", e)
            }
        }
    }

    private suspend fun probeSources(app: Context, trackId: String) {
        val enabled = AppSettings.lyricsSources.value
        val order = AppSettings.lyricsSourceOrder.value
        val candidates = (order + LyricsSource.entries).distinct().filter { it in enabled && !it.hidden }
        val gate = Semaphore(PROBE_PARALLEL)

        /** One try at one source: true if it had synced lyrics. */
        suspend fun attempt(source: LyricsSource): Boolean {
            val result = fetchOne(trackId, source) ?: return false
            val kind = when {
                result.lines.any { it.isWordSynced } -> "word"
                result.lines.any { it.timeMs > 0 && it.text.isNotBlank() } -> "line"
                else -> return false
            }
            if (sourcesTrackId != trackId) return true
            val hash = buildPayload(trackId, result.lines).contentHash
            sourceHits[source] = SourceHit(source, result, kind, hash)
            requestPut(app, trackId)
            return true
        }

        coroutineScope {
            val first = candidates.map { source -> async { gate.withPermit { attempt(source) } } }
            // Every source is asked at once. The list is called complete after the first answers,
            // however slow the stragglers are; they still join it when they arrive.
            withTimeoutOrNull(FIRST_PASS_MS) { first.awaitAll() }
            if (sourcesTrackId == trackId) {
                sourcesDone = true
                putSources(app, trackId, true)
            }
            // A source that answered "nothing" gets one more try, quietly, so that a hiccup does
            // not hide a source that has the lyrics.
            first.forEachIndexed { i, job ->
                val missed = job.isCompleted && !runCatching { job.await() }.getOrDefault(true)
                if (missed) {
                    launch {
                        gate.withPermit {
                            delay(RETRY_DELAY_MS)
                            attempt(candidates[i])
                        }
                    }
                }
            }
        }
        if (sourcesTrackId == trackId) putSources(app, trackId, true)
    }

    /** The list is sent as it grows, but not more often than a few times a second. */
    private fun requestPut(app: Context, trackId: String) {
        if (putPending) return
        putPending = true
        scope.launch {
            delay(PUT_DEBOUNCE_MS)
            putPending = false
            putSources(app, trackId, sourcesDone)
        }
    }

    private suspend fun fetchOne(trackId: String, source: LyricsSource): LyricsRepository.Result? = try {
        LyricsRepository.lyrics(
            videoId = trackId,
            title = currentTitle,
            artist = currentArtist,
            durationMs = currentDuration,
            album = currentAlbum,
            sources = setOf(source),
            order = listOf(source),
            prioritizeSyllableSync = false,
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun handleSelect(trackId: String, sourceId: String) {
        val app = appContext ?: return
        if (currentTrackId != trackId) return
        val hit = sourceHits.values.firstOrNull { it.source.name == sourceId } ?: return
        publish(app, trackId, currentTitle, currentArtist, currentDuration, hit.result.lines, currentAlbum, fromUi = true)
        // The list marks the source now in use once the new lyrics have been built.
        scope.launch {
            delay(SELECT_REFRESH_MS)
            putSources(app, trackId, sourcesDone)
        }
    }

    private fun putSources(app: Context, trackId: String, done: Boolean) {
        try {
            val order = AppSettings.lyricsSourceOrder.value
            val hits = sourceHits.values.sortedBy { order.indexOf(it.source).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            val currentHash = current?.contentHash
            val request = PutDataMapRequest.create(WearSyncPaths.LYRICS_SOURCES).apply {
                dataMap.putString(WearSyncPaths.KEY_TRACK_ID, trackId)
                dataMap.putStringArrayList(WearSyncPaths.KEY_IDS, ArrayList(hits.map { it.source.name }))
                dataMap.putStringArrayList(WearSyncPaths.KEY_LABELS, ArrayList(hits.map { sourceLabel(it.source) }))
                dataMap.putStringArrayList(WearSyncPaths.KEY_KINDS, ArrayList(hits.map { it.kind }))
                dataMap.putString(WearSyncPaths.KEY_CURRENT, hits.firstOrNull { it.hash == currentHash }?.source?.name.orEmpty())
                dataMap.putBoolean(WearSyncPaths.KEY_DONE, done)
                dataMap.putLong(WearSyncPaths.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(app).putDataItem(request), TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "putDataItem (sources) failed", e)
        }
    }

    /** The names the phone's own settings show. Anything not listed falls back to a tidied enum name. */
    private fun sourceLabel(source: LyricsSource): String = when (source.name) {
        "LRC_RED" -> "lrc.red"
        "BETTER_LYRICS" -> "BetterLyrics"
        "BETTER_LYRICS_PORTATO" -> "BetterLyrics Portato"
        "LYRICS_PLUS" -> "LyricsPlus"
        "BINI_LYRICS" -> "BiniLyrics"
        "UNISON" -> "Unison"
        "SIMP_MUSIC" -> "SimpMusic"
        "YOUTUBE_TRANSCRIPT" -> "YouTube captions"
        "YOUTUBE_MUSIC" -> "YouTube Music"
        "LRCLIB" -> "LRCLIB"
        "MUSIXMATCH" -> "Musixmatch"
        "PAXSENIX" -> "PaxSenix"
        "PAXSENIX_SPOTIFY" -> "PaxSenix: Spotify"
        "PAXSENIX_MUSIXMATCH" -> "PaxSenix: Musixmatch"
        "KUGOU" -> "KuGou"
        "MEGALOBIZ" -> "Megalobiz"
        "GENIUS" -> "Genius"
        else -> source.name.lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
    }

    // ---------------------------------------------------------------------------------
    // Translation / romanization for the watch
    // ---------------------------------------------------------------------------------

    private fun handleTransform(trackId: String, kind: String, language: String?) {
        val app = appContext ?: return
        if (kind != WearSyncPaths.KIND_TRANSLATION && kind != WearSyncPaths.KIND_ROMANIZATION) {
            wantedKind = null // back to the original lyrics
            extraJob?.cancel()
            return
        }
        wantedKind = kind
        wantedTrack = trackId
        wantedLang = language?.trim()?.takeIf { it.isNotEmpty() }
        // If the lyrics of this track are not published yet, publish() sends the extra once they are.
        if (currentTrackId == trackId && currentLines.isNotEmpty()) sendExtra(app, trackId, kind)
    }

    private fun sendExtra(app: Context, trackId: String, kind: String) {
        val lines = currentLines
        val order = currentOrder
        extraJob?.cancel()
        extraJob = scope.launch {
            val tag = targetLanguage(wantedLang)
            try {
                var status = WearSyncPaths.STATUS_UNAVAILABLE
                var texts = ArrayList<String>()
                if (kind == WearSyncPaths.KIND_TRANSLATION) {
                    when (val r = LyricsTranslation.translate(trackId, lines, tag)) {
                        is LyricsTranslation.Result.Translated -> {
                            status = WearSyncPaths.STATUS_OK
                            texts = textsFor(r.lines, order)
                        }
                        is LyricsTranslation.Result.SameLanguage -> status = WearSyncPaths.STATUS_SAME
                        LyricsTranslation.Result.Unavailable -> Unit
                    }
                } else {
                    when (val r = LyricsTranslation.romanize(trackId, lines, tag)) {
                        is LyricsTranslation.RomanizationResult.Romanized -> {
                            status = WearSyncPaths.STATUS_OK
                            texts = textsFor(r.lines, order)
                        }
                        LyricsTranslation.RomanizationResult.AlreadyRomanized -> status = WearSyncPaths.STATUS_SAME
                        LyricsTranslation.RomanizationResult.Unavailable -> Unit
                    }
                }
                putExtra(app, trackId, kind, status, tag, texts)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$kind failed for $trackId", e)
                putExtra(app, trackId, kind, WearSyncPaths.STATUS_UNAVAILABLE, tag, ArrayList())
            }
        }
    }

    /** One entry per payload line, in payload order; "" where the result has no such line. */
    private fun textsFor(result: List<LyricLine>, order: IntArray): ArrayList<String> {
        val out = ArrayList<String>(order.size)
        for (index in order) out += if (index < 0) "" else result.getOrNull(index)?.text.orEmpty()
        return out
    }

    /** The player's "translate to" choice; blank means "follow the app language". */
    private fun targetLanguage(fromWatch: String?): String {
        // The watch says which language it is set to; that is what its wearer reads.
        if (!fromWatch.isNullOrBlank()) return fromWatch
        val chosen = AppSettings.translationLanguage.value.trim()
        if (chosen.isNotEmpty()) return chosen
        val locale = Locale.getDefault()
        return if (locale.language == "zh") locale.toLanguageTag() else locale.language
    }

    private fun putExtra(
        app: Context,
        trackId: String,
        kind: String,
        status: String,
        language: String,
        texts: ArrayList<String>,
    ) {
        try {
            val request = PutDataMapRequest.create(WearSyncPaths.LYRICS_EXTRA).apply {
                dataMap.putString(WearSyncPaths.KEY_TRACK_ID, trackId)
                dataMap.putString(WearSyncPaths.KEY_KIND, kind)
                dataMap.putString(WearSyncPaths.KEY_STATUS, status)
                dataMap.putString(WearSyncPaths.KEY_LANG, language)
                dataMap.putStringArrayList(WearSyncPaths.KEY_TEXTS, texts)
                dataMap.putLong(WearSyncPaths.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(app).putDataItem(request), TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "putDataItem (extra) failed", e)
        }
    }

    /** Positive = lyrics later, as in the player: the position shown is the playhead minus this. */
    private fun putOffset(app: Context, offsetMs: Int) {
        try {
            val request = PutDataMapRequest.create(WearSyncPaths.LYRICS_OFFSET).apply {
                dataMap.putInt(WearSyncPaths.KEY_OFFSET_MS, offsetMs)
            }.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(app).putDataItem(request), TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "putDataItem (offset) failed", e)
        }
    }

    private fun answerClockPing(event: MessageEvent) {
        val app = appContext ?: return
        val data = event.data
        if (data.size < 8) return
        val sentAt = data.copyOfRange(0, 8) // the watch's own send time, echoed back
        val nodeId = event.sourceNodeId

        // The player may only be touched on the main thread, and the clock and the position have to
        // be read at the same instant, so both are read there, as late as possible.
        val reply = Runnable {
            val head = try {
                playhead?.invoke()
            } catch (e: Exception) {
                null
            }
            val body = ByteBuffer.allocate(29)
                .put(sentAt)
                .putLong(System.currentTimeMillis())
                .putLong(head?.positionMs ?: -1L)
                .put(if (head?.advancing == true) 1.toByte() else 0.toByte())
                .putFloat(head?.speed ?: 1f)
                .array()
            Wearable.getMessageClient(app).sendMessage(nodeId, WearSyncPaths.CLOCK_PONG, body)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) reply.run() else Handler(Looper.getMainLooper()).post(reply)
    }

    private fun putPayload(app: Context, payload: SyncedLyricsPayload): Boolean = try {
        lastPutKey = "${payload.trackId}#${payload.contentHash}"
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
    private fun LyricLine.toRaw(index: Int): RawLyricLine {
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
        val answering = background?.takeIf { it.text.isNotBlank() }
            ?.let { RawBackground(it.text, it.timeMs, it.endMs) }
        return RawLyricLine(
            text = text,
            startMs = timeMs,
            // The line's own end where it is known (word timing or a stated end): a long silence
            // after it is what the three dots are for.
            endMs = if (hasKnownEnd) endMs else null,
            syllables = parts,
            isGap = isGap,
            sourceIndex = index,
            background = answering,
        )
    }

    private fun buildPayload(trackId: String, lines: List<LyricLine>): SyncedLyricsPayload =
        SyncedLyricsPayload.build(
            trackId = trackId,
            title = currentTitle,
            artist = currentArtist,
            durationMs = currentDuration,
            raw = lines.mapIndexed { i, l -> l.toRaw(i) },
        )
}
