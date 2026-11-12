package com.music.bitchord.wear

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.flow.update
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.ACTION_TOGGLE_FAVORITE
import com.music.bitchord.playback.ACTION_TOGGLE_SHUFFLE
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.ui.player.artworkMeshColors
import com.music.bitchord.widget.MediaWidgetSnapshot
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.google.android.gms.wearable.ChannelClient
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.DirectSourceGate
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.playback.cast.CastSink
import com.music.bitchord.playback.cast.RemoteSpeaker
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.music.bitchord.wear.WearProtocol as P

/**
 * Tells the watch app what is playing.
 *
 * Fed from the same place the home-screen widgets are —
 * [publishWidgetState][PlaybackService] — so the watch moves exactly when the
 * widget and the notification do, plus [republish] for the two things only a
 * watch draws: where the playhead is after a seek, and the repeat mode.
 *
 * Costs nothing on a phone without a watch. State is always remembered, but it
 * is only encoded, given artwork and handed to Play services once a watch with
 * the BitChord app on it is known to be reachable — and a phone with no Wear OS
 * app at all answers that with an error, once, and is left alone after it.
 *
 * TV builds from these sources too, and carries a no-op file at this same path
 * in its place (tv/src/main/java/.../wear/WearBridge.kt), so neither Play
 * services Wearable nor [WearCommandService] reach the TV app.
 */
object WearBridge {

    private const val TAG = "BitChordWear"

    /** Coalesces the bursts the player reports in — play is two or three callbacks in a row. */
    private const val COALESCE_MS = 120L

    /** Rows of Up Next sent with a thumbnail. The rest are text, which is all a quick flick shows. */
    private const val QUEUE_ART_ROWS = 12

    /** Rows of Up Next sent at all. */
    private const val QUEUE_AHEAD = 40

    private class Playing(
        val snapshot: MediaWidgetSnapshot,
        val album: String?,
        val playing: Boolean,
        val advancing: Boolean,
        val positionMs: Long,
        val positionAt: Long,
        val durationMs: Long,
        val speed: Float,
        val repeatMode: Int,
    )

    private class Art(val url: String, val asset: Asset, val colors: ArrayList<Int>)

    /** One row of Up Next, read off the session by [WearCommandService]. */
    internal class QueueRow(val index: Int, val title: String, val artist: String, val artworkUrl: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var last: Playing? = null
    @Volatile private var art: Art? = null

    /** Null until asked. Kept current by a capability listener after that. */
    @Volatile private var watchReachable: Boolean? = null
    @Volatile private var listening = false
    private var flushJob: Job? = null

    /**
     * Called by the playback service with the snapshot it just gave the widgets.
     * Main thread: the player is read here, not later.
     */
    internal fun publish(context: Context, snapshot: MediaWidgetSnapshot, player: Player, playing: Boolean?) {
        last = Playing(
            snapshot = snapshot,
            album = player.currentMediaItem?.mediaMetadata?.albumTitle?.toString(),
            playing = playing ?: player.playWhenReady,
            // A service being torn down still reports isPlaying for a moment.
            advancing = playing != false && player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            positionAt = System.currentTimeMillis(),
            durationMs = player.duration.coerceAtLeast(0L),
            speed = player.playbackParameters.speed,
            repeatMode = player.repeatMode,
        )
        schedule(context.applicationContext)
    }

    /** The playhead moved by itself (a seek) or the repeat mode changed; the track did not. */
    internal fun republish(context: Context, player: Player) {
        val previous = last ?: return
        publish(context, previous.snapshot, player, null)
    }

    // ---- Called from WearCommandService ----

    /** A message from a watch is proof one is reachable, whatever the last capability answer was. */
    internal fun onWatchSeen(context: Context) {
        watchReachable = true
        ensureListening(context.applicationContext)
    }

    /**
     * Answers a watch app that just opened. Uses what the service last said if
     * it is alive in this process, and otherwise the widget's persisted
     * snapshot — so opening the watch app never starts the player by itself.
     */
    internal fun sync(context: Context) {
        val app = context.applicationContext
        if (last == null) {
            val saved = MediaWidgetSnapshot.load(app)
            if (saved.hasTrack) {
                last = Playing(
                    snapshot = saved,
                    album = null,
                    playing = false,
                    advancing = false,
                    positionMs = 0L,
                    positionAt = System.currentTimeMillis(),
                    durationMs = 0L,
                    speed = 1f,
                    repeatMode = AppSettings.repeatMode.value,
                )
            }
        }
        scope.launch { flush(app) }
    }

    /** The phone's own media volume, a few steps at a time, as the crown turns. */
    internal fun adjustVolume(context: Context, steps: Int) {
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        schedule(app)
    }

    internal fun publishQueue(context: Context, rows: List<QueueRow>, currentIndex: Int, currentMediaId: String?) {
        val app = context.applicationContext
        val request = PutDataMapRequest.create(P.PATH_QUEUE)
        val items = ArrayList<DataMap>(rows.size)
        rows.forEachIndexed { position, row ->
            items += DataMap().apply {
                putInt(P.KEY_INDEX, row.index)
                putString(P.KEY_TITLE, row.title)
                putString(P.KEY_ARTIST, row.artist)
                if (position < QUEUE_ART_ROWS) {
                    runBlocking { loadBitmap(app, row.artworkUrl, ROW_ART_PX, P.ROW_ART_PX) }
                        ?.let { putAsset(P.KEY_ART, Asset.createFromBytes(it.jpeg(80))) }
                }
            }
        }
        request.dataMap.apply {
            putDataMapArrayList(P.KEY_ITEMS, items)
            putInt(P.KEY_CURRENT_INDEX, currentIndex)
            putString(P.KEY_QUEUE_MEDIA_ID, currentMediaId.orEmpty())
            // Always a change, so asking twice for the same queue still lands.
            putLong(P.KEY_POSITION_AT, System.currentTimeMillis())
        }
        put(app, request)
    }

    internal val queueAhead get() = QUEUE_AHEAD

    // ---- Playing on the watch ----

    private val _watchSession = MutableStateFlow(false)

    /**
     * True while the watch is playing the queue itself. The playback service
     * stays in the foreground for as long as it is — see PlaybackService — or
     * Android would freeze this process mid-stream once the phone's own
     * player had been paused a while.
     */
    val watchSession: StateFlow<Boolean> = _watchSession.asStateFlow()

    @Volatile private var streams: DataSource.Factory? = null

    /**
     * The playback service's own source chain — resolver, cache, SMB, local
     * files — so a track on the watch is read exactly the way the phone would
     * read it, whatever kind of track it is. Null when the service goes.
     */
    fun attachStreams(factory: DataSource.Factory?) {
        streams = factory
    }

    /** Every output the phone could play through, as its own player lists them. */
    // ---- The watch as the speaker ----

    private val _watchOutput = MutableStateFlow(WatchOutputState())

    /** The watch as a row in the phone's output sheet. */
    val watchOutput: StateFlow<WatchOutputState> = _watchOutput.asStateFlow()

    private fun onWatchNodes(nodes: Set<Node>) {
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        watchNode = node?.id ?: watchNode
        _watchOutput.update { it.copy(available = node != null, name = node?.displayName ?: it.name) }
    }

    /** Asks again which watch is in reach — the output sheet opening. */
    fun refreshWatchOutput(context: Context) {
        val app = context.applicationContext
        scope.launch {
            watchReachable = null
            isWatchReachable(app)
        }
    }

    /** Make the watch the speaker, from the phone. Main thread. */
    fun selectWatchOutput() {
        speaker?.start()
    }

    /** Take the music back from the watch onto this phone, playing on if it was. Main thread. */
    fun leaveWatchOutput() {
        speaker?.stop(resumeHere = true)
    }

    /** The output sheet's slider while the watch is the speaker. */
    fun setWatchVolume(fraction: Float) {
        speaker?.setVolume(fraction)
    }

    internal fun onSpeakerChanged(speaker: WatchSpeaker) {
        val volume = speaker.watchVolume?.let { (level, max) -> level.toFloat() / max } ?: 0f
        _watchOutput.update { it.copy(active = speaker.active, volume = volume) }
    }

    @Volatile internal var speaker: WatchSpeaker? = null
        private set

    /** Built by the playback service; [WatchSpeaker] is the receiver it reports while active. */
    fun createSpeaker(
        context: Context,
        scope: CoroutineScope,
        localPlayer: () -> ExoPlayer?,
        onChanged: () -> Unit,
    ): RemoteSpeaker = WatchSpeaker(context.applicationContext, localPlayer, onChanged).also { speaker = it }

    /** The service is going: the watch stops and the speaker with it. */
    fun releaseSpeaker() {
        speaker?.takeIf { it.active }?.stop(resumeHere = false)
        speaker = null
    }

    internal fun setWatchSession(active: Boolean) {
        _watchSession.value = active
    }

    @Volatile private var watchNode: String? = null

    /** A message to the watch app, with whether it got there. */
    internal fun sendToWatch(context: Context, path: String, payload: ByteArray, result: (Boolean) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            repeat(2) {
                val node = watchNode ?: runCatching {
                    Tasks.await(
                        Wearable.getCapabilityClient(app).getCapability(P.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE),
                        5, TimeUnit.SECONDS,
                    ).nodes.let { nodes -> (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id }
                }.getOrNull()?.also { watchNode = it }
                if (node != null) {
                    val sent = runCatching {
                        Tasks.await(Wearable.getMessageClient(app).sendMessage(node, path, payload), 10, TimeUnit.SECONDS)
                    }.isSuccess
                    if (sent) return@launch result(true)
                }
                watchNode = null
            }
            result(false)
        }
    }

    internal fun publishOutputs(context: Context) {
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val request = PutDataMapRequest.create(P.PATH_OUTPUTS)
        request.dataMap.apply {
            putDataMapArrayList(
                P.KEY_OUTPUTS,
                AudioRouting.outputs(audio).mapTo(ArrayList()) { d ->
                    DataMap().apply {
                        putInt(P.KEY_OUTPUT_ID, d.id)
                        putString(P.KEY_OUTPUT_NAME, d.name)
                        putString(P.KEY_OUTPUT_KIND, d.kind.name)
                        // None of them is the speaker while the watch is.
                        putBoolean(P.KEY_OUTPUT_ACTIVE, d.isActive && speaker?.active != true)
                    }
                },
            )
            putBoolean(P.KEY_WATCH_SPEAKER, speaker?.active == true)
            putLong(P.KEY_POSITION_AT, System.currentTimeMillis())
        }
        // Read here, on the caller's thread, but handed over off the main one.
        scope.launch { put(app, request) }
    }

    private val streamPool = Executors.newCachedThreadPool()

    /**
     * Serves one read of the watch's player — see [P.PATH_STREAM]. A seek on
     * the watch closes its channel and opens another at the new position, so
     * an early close is the normal end of most of these, not an error.
     */
    internal fun serveStream(context: Context, channel: ChannelClient.Channel, ensureService: () -> Unit) {
        val app = context.applicationContext
        streamPool.execute {
            val client = Wearable.getChannelClient(app)
            var source: DataSource? = null
            try {
                val input = DataInputStream(Tasks.await(client.getInputStream(channel), 10, TimeUnit.SECONDS))
                val output = DataOutputStream(
                    BufferedOutputStream(Tasks.await(client.getOutputStream(channel), 10, TimeUnit.SECONDS), STREAM_BUFFER),
                )
                val uri = input.readUTF()
                val position = input.readLong()
                val length = input.readLong()
                val asked = SystemClock.elapsedRealtime()
                val factory = streams ?: run {
                    // The service builds the chain: bring it up and give it a moment.
                    ensureService()
                    var waited = 0
                    while (streams == null && waited < 6_000) {
                        Thread.sleep(100)
                        waited += 100
                    }
                    streams
                }
                val opened = factory?.let { f ->
                    val ds = f.createDataSource().also { source = it }
                    runCatching {
                        // The watch reads what *it* is playing, which the
                        // phone's paused player knows nothing about; holding it
                        // for the phone's read-ahead window would stall it.
                        DirectSourceGate.ungated { ds.open(
                            DataSpec.Builder()
                                .setUri(
                                    // An addon track's cache entry is named by
                                    // the track alone, whatever quality filled
                                    // it, so the watch's read gets its own —
                                    // never the lossless file the phone cached.
                                    // A YouTube-matched track needs nothing: the
                                    // capped ceiling already keys it apart. And
                                    // only those: the tag is in the address, so
                                    // it also misses the resolver's own cache.
                                    Uri.parse(uri).let { parsed ->
                                        if (parsed.scheme != "bitchord" || parsed.authority != "source") parsed else parsed.buildUpon()
                                            .appendQueryParameter(AudioCache.WATCH_READ_PARAM, "1")
                                            .build()
                                    },
                                )
                                .setPosition(position)
                                .setLength(if (length < 0) C.LENGTH_UNSET.toLong() else length)
                                .build(),
                        ) }
                    }.onFailure { Log.w(TAG, "stream open failed: ${it.message}") }.getOrNull()
                }
                val ds = source
                if (opened == null || ds == null) {
                    output.writeByte(P.STREAM_FAILED)
                    output.writeLong(-1L)
                    output.flush()
                    return@execute
                }
                output.writeByte(P.STREAM_OK)
                output.writeLong(if (opened == C.LENGTH_UNSET.toLong()) -1L else opened)
                output.flush()
                Log.d(TAG, "stream open ${SystemClock.elapsedRealtime() - asked}ms @$position ${uri.take(60)}")
                val buffer = ByteArray(STREAM_BUFFER)
                var served = 0L
                while (true) {
                    val read = ds.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        served += read
                    }
                }
                output.flush()
                Log.d(TAG, "stream done ${served / 1024}KB in ${SystemClock.elapsedRealtime() - asked}ms")
            } catch (e: Exception) {
                Log.d(TAG, "stream ended: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                runCatching { source?.close() }
                runCatching { Tasks.await(client.close(channel), 5, TimeUnit.SECONDS) }
            }
        }
    }

    private const val STREAM_BUFFER = 32 * 1024

    // ---- Internals ----

    private fun schedule(app: Context) {
        synchronized(lock) {
            flushJob?.cancel()
            flushJob = scope.launch {
                delay(COALESCE_MS)
                if (isWatchReachable(app)) flush(app)
            }
        }
    }

    private suspend fun flush(app: Context) {
        val now = last
        val request = PutDataMapRequest.create(P.PATH_NOW_PLAYING)
        val map = request.dataMap
        if (now == null || !now.snapshot.hasTrack) {
            map.putBoolean(P.KEY_HAS_TRACK, false)
        } else {
            val s = now.snapshot
            map.putBoolean(P.KEY_HAS_TRACK, true)
            map.putString(P.KEY_MEDIA_ID, s.mediaId.orEmpty())
            map.putString(P.KEY_TITLE, s.title)
            map.putString(P.KEY_ARTIST, s.artist)
            map.putString(P.KEY_ALBUM, now.album.orEmpty())
            map.putBoolean(P.KEY_PLAYING, now.playing)
            map.putBoolean(P.KEY_ADVANCING, now.advancing)
            map.putLong(P.KEY_POSITION_MS, now.positionMs)
            map.putLong(P.KEY_POSITION_AT, now.positionAt)
            map.putLong(P.KEY_DURATION_MS, now.durationMs)
            map.putFloat(P.KEY_SPEED, now.speed)
            map.putBoolean(P.KEY_HAS_PREVIOUS, s.hasPrevious)
            map.putBoolean(P.KEY_HAS_NEXT, s.hasNext)
            map.putBoolean(P.KEY_LIKED, s.isLiked)
            map.putBoolean(P.KEY_CAN_LIKE, s.canLike)
            map.putBoolean(P.KEY_SHUFFLE, s.shuffleEnabled)
            map.putInt(P.KEY_REPEAT, now.repeatMode)
            artworkFor(app, s.artworkUrl)?.let { cover ->
                map.putAsset(P.KEY_ART, cover.asset)
                map.putString(P.KEY_ART_ID, cover.url)
                map.putIntegerArrayList(P.KEY_COLORS, cover.colors)
            }
        }
        map.putBoolean(P.KEY_WATCH_SPEAKER, speaker?.active == true)
        val watchVolume = speaker?.watchVolume
        if (watchVolume != null) {
            map.putInt(P.KEY_VOLUME, watchVolume.first)
            map.putInt(P.KEY_VOLUME_MAX, watchVolume.second)
        } else {
            app.getSystemService(AudioManager::class.java)?.let { audio ->
                map.putInt(P.KEY_VOLUME, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                map.putInt(P.KEY_VOLUME_MAX, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            }
        }
        put(app, request)
    }

    private fun put(app: Context, request: PutDataMapRequest) {
        val task = Wearable.getDataClient(app).putDataItem(request.asPutDataRequest().setUrgent())
        runCatching { Tasks.await(task, 10, TimeUnit.SECONDS) }
            .onFailure { Log.w(TAG, "put ${request.uri.path} failed: ${it.message}") }
    }

    private suspend fun artworkFor(app: Context, url: String?): Art? {
        if (url.isNullOrBlank()) return null
        art?.takeIf { it.url == url }?.let { return it }
        val bitmap = loadBitmap(app, url, NOTIFICATION_ART_PX, P.ART_PX) ?: return null
        val colors = artworkMeshColors(bitmap.asImageBitmap()).mapTo(ArrayList()) { it.toArgb() }
        return Art(url, Asset.createFromBytes(bitmap.jpeg(85)), colors).also { art = it }
    }

    /**
     * Through the app's own size ladder ([ladderPx]), so the watch's copy comes
     * out of the disk cache the notification and the rows already filled
     * instead of over the network, and decoded straight down to [px].
     */
    private suspend fun loadBitmap(app: Context, url: String?, ladderPx: Int, px: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        val request = ImageRequest.Builder(app)
            .data(url.artworkAt(ladderPx) ?: url)
            .size(px)
            .allowHardware(false)
            .build()
        val result = runCatching { SingletonImageLoader.get(app).execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
        // Coil keeps aspect; a non-square cover is centre-cropped like everywhere else.
        val side = minOf(bitmap.width, bitmap.height)
        val square = if (bitmap.width == bitmap.height) bitmap else Bitmap.createBitmap(
            bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side,
        )
        return if (side > px) Bitmap.createScaledBitmap(square, px, px, true) else square
    }

    private fun Bitmap.jpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun isWatchReachable(app: Context): Boolean {
        watchReachable?.let { return it }
        val nodes = runCatching {
            Tasks.await(
                Wearable.getCapabilityClient(app)
                    .getCapability(P.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE),
                5, TimeUnit.SECONDS,
            ).nodes
        }.getOrDefault(emptySet())
        val reachable = nodes.isNotEmpty()
        watchReachable = reachable
        onWatchNodes(nodes)
        ensureListening(app)
        return reachable
    }

    private fun ensureListening(app: Context) {
        if (listening) return
        listening = true
        runCatching {
            Wearable.getCapabilityClient(app).addListener(
                { info ->
                    val reachable = info.nodes.isNotEmpty()
                    onWatchNodes(info.nodes)
                    val arrived = reachable && watchReachable != true
                    watchReachable = reachable
                    // A watch that just came into range gets the current state
                    // without having to open the app to ask.
                    if (arrived) schedule(app)
                },
                P.CAPABILITY_WATCH,
            )
        }
    }
}

/**
 * Commands from the watch.
 *
 * Executed through a [MediaController] — the same door the widget's buttons
 * and the app's own player use — so the service does what it always does for a
 * play or a skip, and binding the controller is also what brings the player up
 * from nothing when the phone app isn't running.
 *
 * One controller is kept for a few seconds after the last command rather than
 * built per message, so a run of skips from the watch is a run of instant
 * commands, not a run of reconnects.
 */
class WearCommandService : WearableListenerService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path ?: return
        if (!path.startsWith(P.PATH_COMMAND + "/")) return
        val command = path.substringAfterLast('/')
        val arg = event.data?.let { String(it, Charsets.UTF_8) }.orEmpty()
        WearBridge.onWatchSeen(this)

        when (command) {
            P.CMD_SYNC -> {
                WearBridge.sync(this)
                return
            }
            P.CMD_VOLUME -> {
                val steps = arg.toIntOrNull() ?: 0
                // The crown turns whatever is the speaker.
                val speaker = WearBridge.speaker
                if (speaker?.active == true) main.post { speaker.nudgeVolume(steps) } else WearBridge.adjustVolume(this, steps)
                return
            }
            P.CMD_SPEAKER_STATUS -> {
                val map = event.data?.let { runCatching { DataMap.fromByteArray(it) }.getOrNull() } ?: return
                main.post { WearBridge.speaker?.onStatus(map) }
                return
            }
            P.CMD_OUTPUTS -> {
                WearBridge.publishOutputs(this)
                return
            }
            P.CMD_SELECT_OUTPUT -> {
                main.post {
                    AudioRouting.select(arg.toIntOrNull())
                    // A phone output picked while the watch plays brings the music back here.
                    WearBridge.speaker?.stop(resumeHere = true)
                    WearBridge.publishOutputs(this)
                }
                return
            }
        }

        // This runs on Play services' worker thread, and the service is let go
        // the moment it returns. Held until the command is actually executed, so
        // a process with nothing else keeping it up is not reclaimed in between.
        val done = CountDownLatch(1)
        Controllers.use(applicationContext, onMissing = { done.countDown() }) { controller ->
            runCatching { execute(controller, command, arg) }
                .onFailure { Log.w("BitChordWear", "command $command failed", it) }
            done.countDown()
        }
        done.await(8, TimeUnit.SECONDS)
    }

    private fun execute(controller: MediaController, command: String, arg: String) {
        if (controller.mediaItemCount == 0) {
            WearBridge.sync(this)
            return
        }
        when (command) {
            P.CMD_TOGGLE -> if (controller.playWhenReady) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_ENDED) controller.seekTo(0L)
                controller.prepareIfIdle()
                controller.play()
            }
            P.CMD_NEXT -> {
                controller.seekToNextMediaItem()
                controller.prepareIfIdle()
            }
            P.CMD_PREVIOUS -> {
                controller.seekToPrevious()
                controller.prepareIfIdle()
            }
            P.CMD_LIKE -> controller.sendCustomCommand(
                SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY),
                Bundle.EMPTY,
            )
            P.CMD_SHUFFLE -> controller.sendCustomCommand(
                SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY),
                Bundle.EMPTY,
            )
            // Same cycle, and same persistence, as the player's own button.
            P.CMD_REPEAT -> {
                val next = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                AppSettings.setRepeatMode(next)
                controller.repeatMode = next
            }
            P.CMD_SPEAKER_WATCH -> {
                WearBridge.speaker?.start()
                WearBridge.publishOutputs(this)
            }
            P.CMD_SEEK -> {
                val position = arg.toLongOrNull() ?: return
                controller.seekTo(position.coerceAtLeast(0L))
            }
            P.CMD_SKIP_TO -> {
                val index = arg.toIntOrNull()?.takeIf { it in 0 until controller.mediaItemCount } ?: return
                controller.seekToDefaultPosition(index)
                controller.prepareIfIdle()
                controller.play()
            }
            P.CMD_QUEUE -> {
                val current = controller.currentMediaItemIndex
                // One played track above the playing one, for context, like the phone's list.
                val from = (current - 1).coerceAtLeast(0)
                val to = (current + WearBridge.queueAhead).coerceAtMost(controller.mediaItemCount - 1)
                val rows = (from..to).map { i ->
                    val metadata = controller.getMediaItemAt(i).mediaMetadata
                    WearBridge.QueueRow(
                        index = i,
                        title = metadata.title?.toString().orEmpty(),
                        artist = metadata.artist?.toString().orEmpty(),
                        artworkUrl = metadata.artworkUri?.toString(),
                    )
                }
                val mediaId = controller.currentMediaItem?.mediaId
                // Off the main thread: the thumbnails are a few disk reads each.
                Thread { WearBridge.publishQueue(applicationContext, rows, current, mediaId) }.start()
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != P.PATH_STREAM) return
        WearBridge.onWatchSeen(this)
        WearBridge.serveStream(this, channel) {
            // Binding is what brings the service, and the chain it builds, up.
            Controllers.use(applicationContext, onMissing = {}) {}
        }
    }

    private fun MediaController.prepareIfIdle() {
        if (playbackState == Player.STATE_IDLE) prepare()
    }

    /** The shared, briefly kept controller. Main thread only. */
    private object Controllers {
        private const val IDLE_RELEASE_MS = 10_000L
        private const val GIVE_UP_MS = 7_000L

        private val main = Handler(Looper.getMainLooper())
        private var future: ListenableFuture<MediaController>? = null
        private val release = Runnable {
            future?.let(MediaController::releaseFuture)
            future = null
        }

        fun use(app: Context, onMissing: () -> Unit, block: (MediaController) -> Unit) {
            main.post {
                main.removeCallbacks(release)
                val pending = future?.takeUnless { it.isDone && it.connected() == null }
                    ?: MediaController.Builder(
                        app,
                        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
                    ).buildAsync().also { future = it }
                var ran = false
                pending.addListener(
                    {
                        if (ran) return@addListener
                        ran = true
                        val controller = pending.connected()
                        if (controller == null) {
                            future = null
                            onMissing()
                        } else {
                            block(controller)
                        }
                        main.postDelayed(release, IDLE_RELEASE_MS)
                    },
                    ContextCompat.getMainExecutor(app),
                )
                // A session that never connects must not hold the worker forever.
                main.postDelayed({ if (!ran) { ran = true; onMissing() } }, GIVE_UP_MS)
            }
        }

        private fun ListenableFuture<MediaController>.connected(): MediaController? =
            runCatching { get() }.getOrNull()?.takeIf { it.isConnected }
    }
}

/**
 * The watch as the speaker — [CastPlayback][com.music.bitchord.playback.cast.CastPlayback]'s
 * model with the watch app as the receiver.
 *
 * The phone's player keeps the queue and is held paused; the watch plays the
 * current track (and the next [AHEAD], so it moves on gaplessly with the phone
 * asleep) and owns the clock, which the session player reports. The phone
 * moving to a track loads or jumps the watch to it; the watch moving on — a
 * track finished — moves the phone's player to the same one, and the queue
 * after it is sent again. Main thread throughout.
 */
internal class WatchSpeaker(
    private val app: Context,
    private val localPlayer: () -> ExoPlayer?,
    private val onChanged: () -> Unit,
) : RemoteSpeaker {

    override var sink: CastSink? = null

    override var active = false
        private set

    private class Status(
        val mediaId: String,
        val state: Int,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val volume: Int,
        val volumeMax: Int,
        /** elapsedRealtime when it arrived, to run the playhead on from. */
        val at: Long,
    )

    private var status: Status? = null
    private var wantsPlay = false
    private var loading = false
    private var finished = false
    private var requestedPositionMs = 0L
    private var requestedAt = 0L

    /** What the watch holds, the playing track first. */
    private var onWatch: List<String> = emptyList()

    override val playWhenReady: Boolean get() = wantsPlay
    override val isPlaying: Boolean get() = active && !loading && status?.isPlaying == true

    override val playbackState: Int
        get() = when {
            loading -> Player.STATE_BUFFERING
            finished -> Player.STATE_ENDED
            status?.state == Player.STATE_BUFFERING -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }

    override val positionMs: Long
        get() {
            val s = status
            if (s == null || loading || SystemClock.elapsedRealtime() - requestedAt < SEEK_GRACE_MS) return requestedPositionMs
            if (!s.isPlaying) return s.positionMs
            val ran = SystemClock.elapsedRealtime() - s.at
            return (s.positionMs + ran).let { if (s.durationMs > 0) it.coerceAtMost(s.durationMs) else it }
        }

    override val durationMs: Long get() = status?.durationMs?.takeIf { it > 0 } ?: C.TIME_UNSET

    override val deviceVolume: Int
        get() = status?.takeIf { it.volumeMax > 0 }?.let { it.volume * 100 / it.volumeMax } ?: 0

    /** The watch's own volume, for the watch app's readout: (level, max). */
    val watchVolume: Pair<Int, Int>? get() = status?.takeIf { active && it.volumeMax > 0 }?.let { it.volume to it.volumeMax }

    // ---- start / stop -------------------------------------------------------

    fun start() {
        if (active) return
        val player = localPlayer() ?: return
        if (player.mediaItemCount == 0) return
        active = true
        WearBridge.setWatchSession(true)
        // The link to the watch carries ~1 Mbit/s at best: lossless, at
        // 0.9–1.5, can never get ahead of playback (measured: a 48 kHz FLAC
        // took 14 s to start and stalled again 2 s in). Medium is YouTube's
        // Opus alone — High still admits addons that hand back FLAC — and it
        // also moves the cache key off the lossless copy the phone holds.
        AppSettings.streamCeilingOverride.value = AudioQuality.MEDIUM
        val wasPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        val position = player.currentPosition.coerceAtLeast(0L)
        // The phone goes quiet first, so the two are never both audible —
        // and idle, not just paused: a paused player keeps buffering whatever
        // track it is moved to, and every move the watch makes moves it, so it
        // would be fetching each song alongside the watch's own stream.
        player.pause()
        player.stop()
        load(player.currentMediaItem, position, play = wasPlaying)
        sink?.onRemoteRouteChanged()
        changed()
    }

    /**
     * Hands the music back to the phone at the watch's place. [resumeHere]:
     * the listener chose a phone output, so it carries on playing there.
     */
    fun stop(resumeHere: Boolean) {
        if (!active) return
        val s = status
        val wasPlaying = wantsPlay
        val position = positionMs
        send(P.SPEAKER_STOP)
        active = false
        WearBridge.setWatchSession(false)
        AppSettings.streamCeilingOverride.value = null
        status = null
        loading = false
        finished = false
        onWatch = emptyList()
        localPlayer()?.let { player ->
            val index = s?.mediaId?.let { indexOf(player, it) }
            if (index != null) player.seekTo(index, position) else player.seekTo(position)
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
            if (resumeHere && wasPlaying) player.play()
        }
        wantsPlay = false
        sink?.onRemoteRouteChanged()
        changed()
    }

    // ---- transport ------------------------------------------------------------

    override fun play() {
        if (!active) return
        wantsPlay = true
        if (finished) {
            finished = false
            localPlayer()?.currentMediaItem?.let { load(it, 0L, play = true) }
            return
        }
        send(P.SPEAKER_PLAY)
        changed()
    }

    override fun pause() {
        if (!active) return
        wantsPlay = false
        send(P.SPEAKER_PAUSE)
        changed()
    }

    override fun seekTo(positionMs: Long) {
        if (!active) return
        requestedPositionMs = positionMs.coerceAtLeast(0L)
        requestedAt = SystemClock.elapsedRealtime()
        send(P.SPEAKER_SEEK, requestedPositionMs.toString())
        changed()
    }

    override fun setVolume(fraction: Float) {
        val max = status?.volumeMax ?: return
        send(P.SPEAKER_VOLUME, "=" + (fraction.coerceIn(0f, 1f) * max).roundToInt())
    }

    /** The crown on the watch, or the phone's volume keys, while the watch is the speaker. */
    fun nudgeVolume(steps: Int) {
        if (!active || steps == 0) return
        send(P.SPEAKER_VOLUME, if (steps > 0) "+$steps" else "$steps")
    }

    // ---- the phone's player moved ---------------------------------------------

    override fun onLocalTransition(item: MediaItem?, reason: Int) {
        if (!active || item == null) return
        if (item.mediaId == status?.mediaId && !loading) {
            // Already there: the watch moved first and this is the phone catching up.
            sendAhead()
            return
        }
        if (item.mediaId in onWatch && !loading) {
            // Held already: a jump, and an instant one.
            finished = false
            requestedPositionMs = 0L
            requestedAt = SystemClock.elapsedRealtime()
            send(P.SPEAKER_JUMP, item.mediaId)
            changed()
        } else {
            load(item, 0L, play = wantsPlay || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        }
    }

    override fun onLocalQueueChanged() {
        if (active) sendAhead()
    }

    override fun onLocalRepeatChanged(repeatMode: Int) {
        if (active) send(P.SPEAKER_REPEAT, repeatMode.toString())
    }

    override fun onLocalStartedPlaying(player: Player) {
        if (!active) return
        player.pause()
        play()
    }

    // ---- the watch moved --------------------------------------------------------

    fun onStatus(map: DataMap) {
        if (!active) return
        val s = Status(
            mediaId = map.getString(P.KEY_MEDIA_ID).orEmpty(),
            state = map.getInt(P.KEY_STATE),
            playWhenReady = map.getBoolean(P.KEY_PLAYING),
            isPlaying = map.getBoolean(P.KEY_ADVANCING),
            positionMs = map.getLong(P.KEY_POSITION_MS),
            durationMs = map.getLong(P.KEY_DURATION_MS),
            volume = map.getInt(P.KEY_VOLUME),
            volumeMax = map.getInt(P.KEY_VOLUME_MAX),
            at = SystemClock.elapsedRealtime(),
        )
        val volumeChanged = status?.let { it.volume != s.volume } ?: true
        status = s
        requestedAt = 0L
        if (s.mediaId == onWatch.firstOrNull() || s.mediaId in onWatch) loading = false
        // The watch's word on play state wins — a headset unplugged, focus lost.
        if (!loading) wantsPlay = s.playWhenReady

        val player = localPlayer()
        if (player != null && s.mediaId.isNotEmpty() && player.currentMediaItem?.mediaId != s.mediaId) {
            // The watch moved on by itself: the phone's queue follows.
            indexOf(player, s.mediaId)?.let { player.seekTo(it, 0L) }
        }
        if (!loading && s.state == Player.STATE_ENDED) onWatchFinished()
        sink?.onRemotePlaybackChanged()
        if (volumeChanged) sink?.onRemoteVolumeChanged()
        changed()
    }

    /** The watch ran out of the tracks it holds; the phone's queue goes on. */
    private fun onWatchFinished() {
        val player = localPlayer() ?: return
        when {
            player.hasNextMediaItem() -> {
                wantsPlay = true
                player.seekToNextMediaItem()
            }
            player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0 -> {
                wantsPlay = true
                player.seekTo(0, 0L)
            }
            else -> {
                wantsPlay = false
                finished = true
            }
        }
    }

    // ---- loading ------------------------------------------------------------------

    private fun load(item: MediaItem?, positionMs: Long, play: Boolean) {
        val player = localPlayer() ?: return
        item ?: return
        val index = indexOf(player, item.mediaId) ?: player.currentMediaItemIndex
        val tracks = tracksFrom(player, index)
        if (tracks.isEmpty()) return
        wantsPlay = play
        loading = true
        finished = false
        requestedPositionMs = positionMs
        requestedAt = SystemClock.elapsedRealtime()
        onWatch = tracks.map { it.getString(P.KEY_MEDIA_ID).orEmpty() }
        val map = DataMap().apply {
            putDataMapArrayList(P.KEY_TRACKS, tracks)
            putLong(P.KEY_POSITION_MS, positionMs)
            putBoolean(P.KEY_PLAYING, play)
            putInt(P.KEY_REPEAT, player.repeatMode)
        }
        send(P.SPEAKER_LOAD, map.toByteArray())
        changed()
    }

    /** Replaces what the watch holds after the playing track with what the phone's queue says. */
    private fun sendAhead() {
        val player = localPlayer() ?: return
        val playing = status?.mediaId ?: onWatch.firstOrNull() ?: return
        val index = indexOf(player, playing) ?: return
        val ahead = tracksFrom(player, index).drop(1)
        val ids = listOf(playing) + ahead.map { it.getString(P.KEY_MEDIA_ID).orEmpty() }
        if (ids == onWatch) return
        onWatch = ids
        send(P.SPEAKER_AHEAD, DataMap().apply { putDataMapArrayList(P.KEY_TRACKS, ArrayList(ahead)) }.toByteArray())
    }

    private fun tracksFrom(player: Player, index: Int): ArrayList<DataMap> {
        if (index !in 0 until player.mediaItemCount) return ArrayList()
        val last = (index + AHEAD).coerceAtMost(player.mediaItemCount - 1)
        return (index..last).mapNotNullTo(ArrayList()) { i ->
            val item = player.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri ?: item.toSong().toMediaItem().localConfiguration?.uri
                ?: return@mapNotNullTo null
            DataMap().apply {
                putString(P.KEY_MEDIA_ID, item.mediaId)
                putString(P.KEY_TITLE, item.mediaMetadata.title?.toString().orEmpty())
                putString(P.KEY_ARTIST, item.mediaMetadata.artist?.toString().orEmpty())
                putString(P.KEY_URI, uri.toString())
            }
        }
    }

    private fun indexOf(player: Player, mediaId: String): Int? {
        val from = player.currentMediaItemIndex.coerceAtLeast(0)
        return (from until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId }
            ?: (0 until from).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId }
    }

    private fun send(command: String, arg: String = "") = send(command, arg.toByteArray())

    private fun send(command: String, payload: ByteArray) {
        WearBridge.sendToWatch(app, "${P.PATH_SPEAKER}/$command", payload) { delivered ->
            // A watch that cannot be reached is not a speaker any more: the
            // music comes back to the phone, paused, rather than going silent.
            if (!delivered) main.post { if (active) stop(resumeHere = false) }
        }
    }

    private fun changed() {
        WearBridge.onSpeakerChanged(this)
        onChanged()
    }

    private val main = Handler(Looper.getMainLooper())

    private companion object {
        /** Tracks held on the watch past the playing one. */
        const val AHEAD = 2

        /** How long a seek is believed over the watch's last report. */
        const val SEEK_GRACE_MS = 1_500L
    }
}
