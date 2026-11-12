package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Durable state for the logical bounded CONTEXT queue.
 *
 * This is deliberately separate from [LastPlayed].  LastPlayed's JSON is an
 * established physical-queue compatibility format and must remain readable by
 * older builds.  This store owns the canonical context and logical window; its
 * physical entries are only the bounded projection needed to restore USER_QUEUE,
 * AUTOPLAY, history, and the exact current item without rebuilding a full list.
 */
object BoundedContextPersistence {

    data class Entry(
        val song: Song,
        val canonicalIndex: Int? = null,
        val cycleId: Long? = null,
    )

    data class State(
        val context: ContextSnapshot,
        val source: QueueSource,
        val window: QueueWindowSnapshot,
        val entries: List<Entry>,
        val currentIndex: Int,
        val positionMs: Long,
    )

    private lateinit var prefs: SharedPreferences
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Synchronized
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Test-only dependency injection; production uses [init]. */
    @Synchronized
    internal fun initForTesting(testPrefs: SharedPreferences) {
        prefs = testPrefs
        writer = Executor { it.run() }
    }

    /**
     * Encoding the canonical list is O(collection) and the service saves on
     * every track change and queue edit, so [save] only records the newest
     * state on the caller's (main) thread. One background writer encodes and
     * stores whatever is newest when it gets there; a burst of saves costs one
     * encode.
     */
    private var writer: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bounded-context-save").apply { isDaemon = true }
    }
    private var pending: State? = null

    /** Bumped by every clear and synchronous write, so an older queued write is dropped. */
    private var generation = 0L

    fun save(state: State) {
        synchronized(this) {
            val queued = pending != null
            pending = state
            if (queued) return
        }
        writer.execute(::flushPending)
    }

    private fun flushPending() {
        val (state, startedAt) = synchronized(this) {
            val next = pending ?: return
            pending = null
            next to generation
        }
        val encoded = encode(state) ?: return
        synchronized(this) {
            if (startedAt != generation) return
            write(state, encoded).apply()
        }
    }

    fun saveImmediately(state: State) {
        synchronized(this) {
            generation += 1
            pending = null
        }
        val encoded = encode(state) ?: return
        synchronized(this) { write(state, encoded).commit() }
    }

    private fun write(state: State, encoded: String): SharedPreferences.Editor =
        prefs.edit()
            .putString(KEY_STATE, encoded)
            .putString(KEY_CURRENT_ENTRY_ID, state.entries.getOrNull(state.currentIndex)?.song?.queueEntryId)
            .putLong(KEY_POSITION, state.positionMs.coerceAtLeast(0L))

    /**
     * Update only the cheap progress scalars for the entry that was persisted.
     *
     * A progress callback can race a queue replacement.  Comparing the callback's
     * entry id with the id written beside the last snapshot prevents an old player
     * from moving the new queue's position.  Deliberately no state decoding or JSON
     * encoding happens here.
     */
    @Synchronized
    fun savePlaybackState(currentEntryId: String?, positionMs: Long) {
        if (currentEntryId.isNullOrBlank() || !prefs.contains(KEY_STATE)) return
        if (runCatching { prefs.getString(KEY_CURRENT_ENTRY_ID, null) }.getOrNull() != currentEntryId) return
        prefs.edit()
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    /** A malformed snapshot is treated as absent by design. */
    @Synchronized
    fun load(): State? {
        val raw = runCatching { prefs.getString(KEY_STATE, null) }.getOrNull() ?: return null
        val state = decode(raw) ?: return null
        val currentEntryId = state.entries[state.currentIndex].song.queueEntryId
        val scalarEntryId = runCatching { prefs.getString(KEY_CURRENT_ENTRY_ID, null) }.getOrNull()
        if (currentEntryId != scalarEntryId) return state
        val positionMs = runCatching { prefs.getLong(KEY_POSITION, state.positionMs) }.getOrNull()
            ?: return state
        return state.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    @Synchronized
    fun clear() {
        generation += 1
        pending = null
        prefs.edit().clear().apply()
    }

    @Synchronized
    fun clearImmediately() {
        generation += 1
        pending = null
        prefs.edit().clear().commit()
    }

    /** Visible to JVM tests; the Android wrapper above remains the only writer. */
    internal fun encode(state: State): String? = runCatching {
        require(validateState(state) != null) { "invalid bounded context state" }
        json.encodeToString(StoredState.serializer(), state.toStored())
    }.getOrNull()?.takeIf {
        it.toByteArray(StandardCharsets.UTF_8).size <= MAX_SERIALIZED_BYTES
    }

    /** Visible to JVM tests and used by [load] for defensive validation. */
    internal fun decode(raw: String): State? {
        if (raw.length > MAX_SERIALIZED_BYTES) return null
        if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_SERIALIZED_BYTES) return null
        return runCatching {
            json.decodeFromString<StoredState>(raw).toState()
        }.getOrNull()
    }

    /**
     * Build the bounded physical queue that a player may restore.
     *
     * The canonical context remains in [State.context] and the logical cursor
     * remains in [State.window].  Only physical history/current, the actual user
     * and autoplay tails, and a fresh bounded context look-ahead are returned.
     * Context entries are matched by their logical `(canonicalIndex, cycleId)`
     * anchor so their existing physical entry ids survive a restart.
     */
    internal fun restoreProjection(state: State, repeatAll: Boolean = false): State? {
        val restoredWindow = validateState(state) ?: return null
        val current = state.entries[state.currentIndex]
        val history = state.entries
            .subList(0, state.currentIndex)
            .takeLast(MAX_HISTORY)
        val userTail = state.entries
            .subList(state.currentIndex + 1, state.entries.size)
            .filter { it.song.queueTier == QueueTier.USER_QUEUE }
        val autoplayTail = state.entries
            .subList(state.currentIndex + 1, state.entries.size)
            .filter { it.song.queueTier == QueueTier.AUTOPLAY }

        val anchoredEntries = state.entries.drop(state.currentIndex + 1)
            .asSequence()
            .filter { it.canonicalIndex != null && it.cycleId != null }
            .associateBy { it.canonicalIndex!! to it.cycleId!! }
        val upcoming = runCatching {
            BoundedQueuePlanner.physicalUpcoming(
                window = restoredWindow,
                repeatAll = repeatAll && BoundedQueuePlanner.canRepeat(
                    state.context.continuationState,
                ),
                count = MAX_CONTEXT_UPCOMING,
            )
        }.getOrNull() ?: return null
        val usedIds = (history + current + userTail + autoplayTail)
            .mapTo(HashSet()) { it.song.queueEntryId!! }
        val retainedAnchors = (history + current)
            .mapNotNull { entry ->
                val canonicalIndex = entry.canonicalIndex
                val cycleId = entry.cycleId
                if (canonicalIndex == null || cycleId == null) null else canonicalIndex to cycleId
            }
            .toSet()
        val contextUpcoming = upcoming
            .filterNot { it.canonicalIndex to it.cycleId in retainedAnchors }
            .map { logicalEntry ->
                val canonical = state.context.songs[logicalEntry.canonicalIndex]
                val matching = anchoredEntries[logicalEntry.canonicalIndex to logicalEntry.cycleId]
                val baseId = matching?.song?.queueEntryId
                    ?: "window:${state.context.contextToken}:${logicalEntry.cycleId}:${logicalEntry.canonicalIndex}"
                var entryId = baseId
                var suffix = 0
                while (!usedIds.add(entryId)) {
                    suffix += 1
                    entryId = "$baseId:restored:$suffix"
                }
                Entry(
                    song = canonical.copy(
                        queueTier = QueueTier.CONTEXT,
                        queueEntryId = entryId,
                    ),
                    canonicalIndex = logicalEntry.canonicalIndex,
                    cycleId = logicalEntry.cycleId,
                )
            }

        val projectedEntries = history + current + userTail + contextUpcoming + autoplayTail
        if (projectedEntries.isEmpty()) return null
        val projected = state.copy(
            window = restoredWindow.snapshot(),
            entries = projectedEntries,
            currentIndex = history.size,
        )
        return validateState(projected)?.let { projected }
    }

    private fun State.toStored() = StoredState(
        schemaVersion = SCHEMA_VERSION,
        context = StoredContext.from(context, source),
        window = StoredWindow.from(window),
        entries = entries.map(StoredEntry::from),
        currentIndex = currentIndex,
        positionMs = positionMs.coerceAtLeast(0L),
    )

    @Serializable
    private data class StoredState(
        val schemaVersion: Int = SCHEMA_VERSION,
        val context: StoredContext,
        val window: StoredWindow,
        val entries: List<StoredEntry>,
        val currentIndex: Int,
        val positionMs: Long,
    ) {
        fun toState(): State? {
            if (schemaVersion != SCHEMA_VERSION) return null
            if (context.contextToken.isBlank() || context.sourceKey.isBlank()) return null

            val contextSongs = context.songs.map { it.toSong() ?: return null }

            val continuationState = context.continuationState.toContinuationState() ?: return null
            val diagnostic = context.diagnostic?.toContinuationDiagnostic() ?:
                if (context.diagnostic == null) null else return null
            val sourceType = context.sourceType.toPlaybackSourceType() ?: return null
            val restoredContext = ContextSnapshot(
                contextToken = context.contextToken,
                sourceKey = context.sourceKey,
                browseId = context.browseId,
                songs = contextSongs,
                // The registry assigns a fresh process-local version on restore.
                version = 0L,
                continuationToken = context.continuationToken,
                continuationState = continuationState,
                diagnostic = diagnostic,
                seenContinuationTokens = context.seenContinuationTokens,
            )
            val source = QueueSource(
                title = context.sourceTitle,
                type = sourceType,
                id = context.sourceId,
            )

            val restoredWindow = window.toSnapshot() ?: return null
            if (entries.isEmpty() || currentIndex !in entries.indices || positionMs < 0L) return null
            val restoredEntries = entries.map { it.toEntry(contextSongs) ?: return null }
            val restored = State(
                context = restoredContext,
                source = source,
                window = restoredWindow,
                entries = restoredEntries,
                currentIndex = currentIndex,
                positionMs = positionMs,
            )
            return restored.takeIf { validateState(it) != null }
        }
    }

    @Serializable
    private data class StoredContext(
        val contextToken: String,
        val sourceKey: String,
        val browseId: String?,
        val sourceTitle: String,
        val sourceType: String,
        val sourceId: String?,
        val songs: List<StoredSong>,
        val continuationToken: String?,
        val continuationState: String,
        val diagnostic: String? = null,
        val seenContinuationTokens: List<String> = emptyList(),
    ) {
        companion object {
            fun from(snapshot: ContextSnapshot, source: QueueSource) = StoredContext(
                contextToken = snapshot.contextToken,
                sourceKey = snapshot.sourceKey,
                browseId = snapshot.browseId,
                sourceTitle = source.title,
                sourceType = source.type.name,
                sourceId = source.id,
                songs = snapshot.songs.map(StoredSong::from),
                continuationToken = snapshot.continuationToken,
                continuationState = snapshot.continuationState.name,
                diagnostic = snapshot.diagnostic?.name,
                seenContinuationTokens = snapshot.seenContinuationTokens,
            )
        }
    }

    @Serializable
    private data class StoredWindow(
        val canonicalSize: Int,
        val order: List<Int>,
        val cursor: Int,
        val currentCanonicalIndex: Int?,
        val shuffleEnabled: Boolean,
        val shuffleSeed: Long,
        val cycleId: Long,
        val consumedThisCycle: List<Int>,
        val lastCycleFinalCanonicalIndex: Int?,
        val plannedCycles: List<StoredCycle> = emptyList(),
    ) {
        fun toSnapshot(): QueueWindowSnapshot? {
            if (canonicalSize < 0 || order.size != canonicalSize) return null
            if (cycleId < 0L) return null
            return QueueWindowSnapshot(
                canonicalSize = canonicalSize,
                order = order,
                cursor = cursor,
                currentCanonicalIndex = currentCanonicalIndex,
                shuffleEnabled = shuffleEnabled,
                shuffleSeed = shuffleSeed,
                cycleId = cycleId,
                consumedThisCycle = consumedThisCycle,
                lastCycleFinalCanonicalIndex = lastCycleFinalCanonicalIndex,
                plannedCycles = plannedCycles.map {
                    QueueWindowCycleSnapshot(it.cycleId, it.order)
                },
            )
        }

        companion object {
            fun from(snapshot: QueueWindowSnapshot) = StoredWindow(
                canonicalSize = snapshot.canonicalSize,
                order = snapshot.order,
                cursor = snapshot.cursor,
                currentCanonicalIndex = snapshot.currentCanonicalIndex,
                shuffleEnabled = snapshot.shuffleEnabled,
                shuffleSeed = snapshot.shuffleSeed,
                cycleId = snapshot.cycleId,
                consumedThisCycle = snapshot.consumedThisCycle,
                lastCycleFinalCanonicalIndex = snapshot.lastCycleFinalCanonicalIndex,
                plannedCycles = snapshot.plannedCycles.map(StoredCycle::from),
            )
        }
    }

    @Serializable
    private data class StoredCycle(
        val cycleId: Long,
        val order: List<Int>,
    ) {
        companion object {
            fun from(snapshot: QueueWindowCycleSnapshot) = StoredCycle(
                cycleId = snapshot.cycleId,
                order = snapshot.order,
            )
        }
    }

    @Serializable
    private data class StoredEntry(
        val song: StoredSong,
        val canonicalIndex: Int?,
        val cycleId: Long?,
    ) {
        fun toEntry(contextSongs: List<Song>): Entry? {
            val resolvedSong = song.toSong() ?: return null
            val index = canonicalIndex
            if (index != null) {
                if (index !in contextSongs.indices) return null
                if (resolvedSong.contextEntryKey() != contextSongs[index].contextEntryKey()) return null
                if (cycleId == null || cycleId < 0L) return null
            } else if (cycleId != null) {
                return null
            }
            return Entry(resolvedSong, canonicalIndex, cycleId)
        }

        companion object {
            fun from(entry: Entry) = StoredEntry(
                song = StoredSong.from(entry.song),
                canonicalIndex = entry.canonicalIndex,
                cycleId = entry.cycleId,
            )
        }
    }

    @Serializable
    private data class StoredArtist(
        val name: String,
        val browseId: String?,
    )

    @Serializable
    private data class StoredSong(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String?,
        val durationText: String? = null,
        val artistId: String? = null,
        val artists: List<StoredArtist> = emptyList(),
        val albumId: String? = null,
        val albumName: String? = null,
        val isVideo: Boolean = false,
        val isVideoOrigin: Boolean = false,
        val setVideoId: String? = null,
        val queueTier: String = QueueTier.CONTEXT.name,
        val queueEntryId: String? = null,
        val radioName: String? = null,
        val localUri: String? = null,
        val downloadFormat: String? = null,
        val localPath: String? = null,
        val localDateAddedSeconds: Long? = null,
        val localDateModifiedSeconds: Long? = null,
        val sourceQuality: String? = null,
        val isExplicit: Boolean? = null,
        val playbackSource: String? = null,
        val playbackSourceType: String? = null,
        val playbackSourceId: String? = null,
    ) {
        fun toSong(): Song? {
            if (videoId.isBlank()) return null
            val tier = runCatching { QueueTier.valueOf(queueTier) }.getOrNull() ?: return null
            val sourceType = playbackSourceType?.let {
                runCatching { PlaybackSourceType.valueOf(it) }.getOrNull()
            }
            if (playbackSourceType != null && sourceType == null) return null
            return Song(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                durationText = durationText,
                artistId = artistId,
                artists = artists.map { ArtistRef(it.name, it.browseId) },
                albumId = albumId,
                albumName = albumName,
                isVideo = isVideo,
                isVideoOrigin = isVideoOrigin,
                setVideoId = setVideoId,
                queueTier = tier,
                queueEntryId = queueEntryId,
                radioName = radioName,
                localUri = localUri,
                downloadFormat = downloadFormat,
                localPath = localPath,
                localDateAddedSeconds = localDateAddedSeconds,
                localDateModifiedSeconds = localDateModifiedSeconds,
                sourceQuality = sourceQuality,
                isExplicit = isExplicit,
                playbackSource = playbackSource,
                playbackSourceType = sourceType,
                playbackSourceId = playbackSourceId,
            )
        }

        companion object {
            fun from(song: Song) = StoredSong(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationText = song.durationText,
                artistId = song.artistId,
                artists = song.artists.map { StoredArtist(it.name, it.browseId) },
                albumId = song.albumId,
                albumName = song.albumName,
                isVideo = song.isVideo,
                isVideoOrigin = song.isVideoOrigin,
                setVideoId = song.setVideoId,
                queueTier = song.queueTier.name,
                queueEntryId = song.queueEntryId,
                radioName = song.radioName,
                localUri = song.localUri,
                downloadFormat = song.downloadFormat,
                localPath = song.localPath,
                localDateAddedSeconds = song.localDateAddedSeconds,
                localDateModifiedSeconds = song.localDateModifiedSeconds,
                sourceQuality = song.sourceQuality,
                isExplicit = song.isExplicit,
                playbackSource = song.playbackSource,
                playbackSourceType = song.playbackSourceType?.name,
                playbackSourceId = song.playbackSourceId,
            )
        }
    }

    /** Return the restored window only when every cross-object invariant holds. */
    private fun validateState(state: State): QueueWindow? {
        val context = state.context
        val source = state.source
        if (context.contextToken.isBlank() || context.sourceKey.isBlank()) return null
        if (source.title.isBlank() || source.id?.isBlank() == true) return null
        if (context.sourceKey != sourceKey(source) || context.browseId != source.id) return null
        if (context.continuationToken?.isBlank() == true) return null
        if (context.continuationState == ContinuationState.AVAILABLE ||
            context.continuationState == ContinuationState.IN_FLIGHT
        ) {
            if (context.continuationToken.isNullOrBlank()) return null
        }
        val seenContinuationTokens = HashSet<String>(context.seenContinuationTokens.size + 1)
        context.seenContinuationTokens.forEach { token ->
            if (token.isBlank() || !seenContinuationTokens.add(token)) return null
        }
        context.continuationToken?.let { token ->
            seenContinuationTokens.add(token)
        }

        val canonical = context.songs
        if (canonical.isEmpty() || canonical.size > MAX_CANONICAL_SONGS) return null
        if (canonical.any { it.videoId.isBlank() || it.queueTier != QueueTier.CONTEXT }) return null
        if (canonical.map { it.contextEntryKey() }.toSet().size != canonical.size) return null
        if (canonical.any { !it.matchesSource(source) }) return null

        val window = runCatching { QueueWindow.restore(state.window) }.getOrNull() ?: return null
        if (state.window.canonicalSize != canonical.size) return null
        if (window.currentCanonicalIndex == null || window.cursor < 0) return null

        if (state.entries.isEmpty() || state.entries.size > MAX_PHYSICAL_ENTRIES) return null
        if (state.currentIndex !in state.entries.indices || state.positionMs < 0L) return null

        val knownCycles = buildSet {
            add(window.cycleId)
            state.window.plannedCycles.forEach { add(it.cycleId) }
        }
        val entryIds = HashSet<String>(state.entries.size)
        val anchors = HashSet<Pair<Int, Long>>()
        state.entries.forEachIndexed { index, entry ->
            val entryId = entry.song.queueEntryId?.takeIf { it.isNotBlank() } ?: return null
            if (!entryIds.add(entryId)) return null
            if (entry.song.videoId.isBlank()) return null

            val canonicalIndex = entry.canonicalIndex
            if (canonicalIndex == null) {
                if (entry.cycleId != null) return null
                // An unanchored CONTEXT row is safe only as old physical history.
                if (entry.song.queueTier == QueueTier.CONTEXT && index >= state.currentIndex) {
                    return null
                }
                if (entry.song.queueTier != QueueTier.CONTEXT &&
                    entry.song.queueTier != QueueTier.USER_QUEUE &&
                    entry.song.queueTier != QueueTier.AUTOPLAY
                ) return null
            } else {
                if (entry.song.queueTier != QueueTier.CONTEXT) return null
                if (canonicalIndex !in canonical.indices) return null
                val cycleId = entry.cycleId ?: return null
                if (cycleId < 0L ||
                    (cycleId !in knownCycles && index >= state.currentIndex)
                ) return null
                if (!anchors.add(canonicalIndex to cycleId)) return null
                val expected = canonical[canonicalIndex]
                if (entry.song.contextEntryKey() != expected.contextEntryKey()) return null
            }
        }

        val current = state.entries[state.currentIndex]
        if (current.song.queueTier == QueueTier.CONTEXT) {
            val canonicalIndex = current.canonicalIndex ?: return null
            val cycleId = current.cycleId ?: return null
            if (canonicalIndex != window.currentCanonicalIndex || cycleId != window.cycleId) {
                return null
            }
        }
        return window
    }

    private fun Song.matchesSource(source: QueueSource): Boolean =
        (playbackSource == null || playbackSource == source.title) &&
            (playbackSourceType == null || playbackSourceType == source.type) &&
            (playbackSourceId == null || playbackSourceId == source.id)

    private fun sourceKey(source: QueueSource): String =
        "${source.type.name}:${source.id.orEmpty()}"

    private fun String.toContinuationState(): ContinuationState? =
        runCatching { ContinuationState.valueOf(this) }.getOrNull()

    private fun String.toContinuationDiagnostic(): ContinuationDiagnostic? =
        runCatching { ContinuationDiagnostic.valueOf(this) }.getOrNull()

    private fun String.toPlaybackSourceType(): PlaybackSourceType? =
        runCatching { PlaybackSourceType.valueOf(this) }.getOrNull()

    private const val SCHEMA_VERSION = 1
    // A complete 10k-song canonical context plus all physical tails can exceed
    // the old 4 MiB cutoff.  This remains a bounded prefs record while leaving
    // enough room for real metadata and long user/autoplay tails.
    // Past this the snapshot is skipped and LastPlayed restores instead. A
    // SharedPreferences value stays resident for the life of the process, and
    // encoding allocates it several times over, so this is a heap budget.
    private const val MAX_SERIALIZED_BYTES = 8 * 1024 * 1024
    private const val MAX_CANONICAL_SONGS = 100_000
    private const val MAX_PHYSICAL_ENTRIES = 100_000
    private const val MAX_HISTORY = 25
    private const val MAX_CONTEXT_UPCOMING = 25
    private const val PREFS_NAME = "bitchord_bounded_context"
    private const val KEY_STATE = "state"
    private const val KEY_CURRENT_ENTRY_ID = "current_entry_id"
    private const val KEY_POSITION = "position"
}
