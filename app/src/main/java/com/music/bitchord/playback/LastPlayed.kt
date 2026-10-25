package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.Executor

/**
 * A bounded queue snapshot for restoring playback after process death.
 *
 * Queue contents are serialized only when Media3 reports that the playlist
 * changed. The frequently changing index and position live in a separate small file,
 * so progress sampling neither serializes nor rewrites the song list.
 */
object LastPlayed {

    class Snapshot(val songs: List<Song>, val index: Int, val positionMs: Long)

    private lateinit var store: PlaybackSnapshotStore<Snapshot>
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        initStores(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            context.getSharedPreferences("${PREFS_NAME}_progress", Context.MODE_PRIVATE),
        )
    }

    internal fun initForTesting(
        snapshots: SharedPreferences,
        progress: SharedPreferences,
        writer: Executor = Executor { it.run() },
    ) = initStores(snapshots, progress, writer)

    private fun initStores(
        snapshots: SharedPreferences,
        progress: SharedPreferences,
        writer: Executor = playbackPersistenceWriter,
    ) {
        store = PlaybackSnapshotStore(
            snapshots, progress, KEY_QUEUE,
            encode = { snapshot ->
                json.encodeToString(
                    StoredQueue.serializer(),
                    StoredQueue(
                        tracks = snapshot.songs.map(StoredTrack::from),
                        index = snapshot.index,
                        positionMs = snapshot.positionMs,
                    ),
                )
            },
            decode = { raw ->
                runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull()
                    ?.takeIf { it.tracks.isNotEmpty() }
                    ?.let { stored ->
                        Snapshot(stored.tracks.map(StoredTrack::toSong), stored.legacyIndex ?: 0, stored.legacyPositionMs ?: 0L)
                    }
            },
            initialProgress = { PlaybackProgress(index = it.index, positionMs = it.positionMs) },
            legacyProgress = { prefs, snapshot ->
                PlaybackProgress(
                    index = prefs.getInt(KEY_INDEX, snapshot.index),
                    positionMs = prefs.getLong(KEY_POSITION, snapshot.positionMs).coerceAtLeast(0L),
                )
            },
            writer = writer,
        )
    }

    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return start until (safeIndex + 1 + KEEP_AHEAD).coerceAtMost(size)
    }

    /** Capture a bounded queue now; its conversion and encoding run on the writer. */
    fun saveQueue(songs: List<Song>, index: Int, positionMs: Long = 0L) {
        if (songs.isEmpty()) {
            clear()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = window(songs.size, safeIndex)
        store.save(Snapshot(
            songs.subList(bounds.first, bounds.last + 1).toList(),
            safeIndex - bounds.first,
            positionMs.coerceAtLeast(0L),
        ))
    }

    fun savePlaybackState(index: Int, positionMs: Long) {
        store.updateProgress {
            PlaybackProgress(index = index.coerceAtLeast(0), positionMs = positionMs.coerceAtLeast(0L))
        }
    }

    fun load(): Snapshot? {
        val saved = store.load() ?: return null
        return Snapshot(
            saved.snapshot.songs,
            saved.progress.index.coerceIn(saved.snapshot.songs.indices),
            saved.progress.positionMs.coerceAtLeast(0L),
        )
    }

    fun clear() = store.clear()

    fun flush(): ListenableFuture<Boolean> = store.flush()

    @Serializable
    private data class StoredQueue(
        val tracks: List<StoredTrack>,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val index: Int? = null,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val positionMs: Long? = null,
    ) {
        val legacyIndex: Int? get() = index
        val legacyPositionMs: Long? get() = positionMs
    }

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val tier: String? = null,
        val entryId: String? = null,
        val local: String? = null,
        val path: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val explicit: Boolean? = null,
        val video: Boolean = false,
        val radio: String? = null,
        val source: String? = null,
        val sourceType: String? = null,
        val sourceId: String? = null,
    ) {
        fun toSong(): Song {
            val resolvedTier = when (tier) {
                "USER_QUEUE" -> com.music.bitchord.data.model.QueueTier.USER_QUEUE
                "CONTEXT" -> com.music.bitchord.data.model.QueueTier.CONTEXT
                "AUTOPLAY" -> com.music.bitchord.data.model.QueueTier.AUTOPLAY
                else -> if (auto) com.music.bitchord.data.model.QueueTier.AUTOPLAY else com.music.bitchord.data.model.QueueTier.CONTEXT
            }
            return Song(
                videoId = id,
                title = title,
                artist = artist,
                thumbnailUrl = artwork,
                durationText = duration,
                albumName = album,
                isExplicit = explicit,
                isVideo = video,
                queueTier = resolvedTier,
                queueEntryId = entryId,
                radioName = radio,
                playbackSource = source,
                playbackSourceType = sourceType?.let {
                    runCatching { com.music.bitchord.data.model.PlaybackSourceType.valueOf(it) }.getOrNull()
                },
                playbackSourceId = sourceId,
                localUri = local,
                localPath = path,
            )
        }

        companion object {
            fun from(song: Song) = StoredTrack(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                tier = song.queueTier.name,
                entryId = song.queueEntryId,
                local = song.localUri,
                path = song.localPath,
                duration = song.durationText,
                album = song.albumName,
                explicit = song.isExplicit,
                video = song.isVideo,
                radio = song.radioName,
                source = song.playbackSource,
                sourceType = song.playbackSourceType?.name,
                sourceId = song.playbackSourceId,
            )
        }
    }

    private const val KEEP_AHEAD = 50
    private const val PREFS_NAME = "bitchord_last_played"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
}
