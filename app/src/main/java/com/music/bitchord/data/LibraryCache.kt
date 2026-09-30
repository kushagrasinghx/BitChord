package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** The last successful remote snapshot. Being stale never makes its content unavailable. */
@Serializable
data class Cached<T>(val value: T, val fetchedAt: Long) {
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < LibraryCache.REFRESH_INTERVAL_MS
}

/**
 * Shared metadata for the library, playlist picker and playlist pages. Audio files remain
 * owned by Downloads. Each caller supplies the account/profile scope used by its session.
 * Memory reads are immediate; load and mutations do all disk work on Dispatchers.IO.
 */
object LibraryCache {
    const val REFRESH_INTERVAL_MS = 5 * 60 * 1_000L
    private const val DOWNLOADED_PLAYLIST_PREFIX = "local:playlist:"
    @Volatile private var store: LibraryMetadataStore? = null

    fun init(context: Context) {
        if (store == null) synchronized(this) {
            if (store == null) store = LibraryMetadataStore(File(context.noBackupFilesDir, "library-metadata"))
        }
    }

    /** A downloaded page and its YouTube page refer to the same cache entry. */
    fun canonicalPlaylistId(browseId: String): String {
        val id = browseId.removePrefix(DOWNLOADED_PLAYLIST_PREFIX)
        return when {
            id.startsWith("VL") -> id
            id.startsWith("PL") || id.startsWith("RD") || id.startsWith("OLAK5uy_") -> "VL$id"
            else -> id
        }
    }

    suspend fun load(scope: String) { store?.load(scope) }
    fun library(scope: String): Cached<LibraryPage>? = store?.library(scope)
    fun playlists(scope: String): Cached<List<UserPlaylist>>? = store?.playlists(scope)
    fun playlist(scope: String, browseId: String): Cached<YtMusicRepository.SongPage>? =
        store?.playlist(scope, browseId)

    suspend fun putLibrary(scope: String, value: LibraryPage) { store?.putLibrary(scope, value) }
    suspend fun putPlaylists(scope: String, value: List<UserPlaylist>) { store?.putPlaylists(scope, value) }
    suspend fun putPlaylist(scope: String, browseId: String, value: YtMusicRepository.SongPage) {
        store?.putPlaylist(scope, browseId, value)
    }
    suspend fun invalidateLibrary(scope: String) { store?.invalidateLibrary(scope) }
    suspend fun invalidatePlaylist(scope: String, browseId: String) { store?.invalidatePlaylist(scope, browseId) }
    suspend fun removePlaylist(scope: String, browseId: String) { store?.removePlaylist(scope, browseId) }
    suspend fun clearOnlineLibrary(scope: String, keepPlaylistIds: Set<String> = emptySet()) {
        store?.clearOnlineLibrary(scope, keepPlaylistIds)
    }
    suspend fun clear(scope: String? = null) { store?.clear(scope) }
}

/** File-backed implementation kept separate so persistence and account isolation are testable. */
internal class LibraryMetadataStore(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val diskMutex = Mutex()
    private val memoryLock = Any()
    private val memory = LinkedHashMap<String, ScopeSnapshot>(MAX_SCOPES, 0.75f, true)
    private val loaded = mutableSetOf<String>()

    @Serializable
    private data class ScopeSnapshot(
        val scope: String,
        val version: Int = 1,
        val library: Cached<LibraryPage>? = null,
        val playlists: Cached<List<UserPlaylist>>? = null,
        val pages: Map<String, Cached<YtMusicRepository.SongPage>> = emptyMap(),
    )

    fun library(scope: String): Cached<LibraryPage>? = synchronized(memoryLock) { memory[scope]?.library }
    fun playlists(scope: String): Cached<List<UserPlaylist>>? = synchronized(memoryLock) { memory[scope]?.playlists }
    fun playlist(scope: String, browseId: String): Cached<YtMusicRepository.SongPage>? = synchronized(memoryLock) {
        memory[scope]?.pages?.get(LibraryCache.canonicalPlaylistId(browseId))
    }

    suspend fun load(scope: String) = withContext(Dispatchers.IO) {
        diskMutex.withLock { loadLocked(scope) }
    }

    private fun loadLocked(scope: String) {
        if (synchronized(memoryLock) { scope in loaded }) return
        val file = fileFor(scope)
        val snapshot = runCatching {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return@runCatching null
            json.decodeFromString<ScopeSnapshot>(file.readText()).takeIf { it.version == 1 && it.scope == scope }
        }.getOrNull() ?: ScopeSnapshot(scope)
        synchronized(memoryLock) {
            loaded.add(scope)
            memory[scope] = snapshot
            trimMemory()
        }
    }

    suspend fun putLibrary(scope: String, value: LibraryPage) = mutate(scope) {
        copy(library = Cached(value.copy(
            likedSongs = value.likedSongs.map(Song::remoteMetadata),
            librarySongs = value.librarySongs.map(Song::remoteMetadata),
            // Continuation tokens are transient session state, not offline metadata.
            likedContinuation = null,
        ), now()))
    }

    suspend fun putPlaylists(scope: String, value: List<UserPlaylist>) = mutate(scope) {
        copy(playlists = Cached(value.toList(), now()))
    }

    suspend fun putPlaylist(scope: String, browseId: String, value: YtMusicRepository.SongPage) {
        // A failed or unfinished continuation must never replace a complete offline list.
        require(value.continuation == null) { "Only complete playlist snapshots can be cached" }
        val id = LibraryCache.canonicalPlaylistId(browseId)
        require(id.isNotBlank() && !id.startsWith("local:")) { "Only remote playlist metadata can be cached" }
        mutate(scope) {
            val updated = pages + (id to Cached(value.copy(
                songs = value.songs.map(Song::remoteMetadata),
                suggested = value.suggested.map(Song::remoteMetadata),
            ), now()))
            copy(pages = updated.entries.sortedByDescending { it.value.fetchedAt }
                .take(MAX_PLAYLISTS).associate { it.toPair() })
        }
    }

    suspend fun invalidateLibrary(scope: String) = mutate(scope) {
        copy(library = library?.copy(fetchedAt = 0), playlists = playlists?.copy(fetchedAt = 0))
    }

    suspend fun invalidatePlaylist(scope: String, browseId: String) = mutate(scope) {
        val id = LibraryCache.canonicalPlaylistId(browseId)
        val page = pages[id]
        if (page == null) this else copy(pages = pages + (id to page.copy(fetchedAt = 0)))
    }

    suspend fun removePlaylist(scope: String, browseId: String) = mutate(scope) {
        copy(pages = pages - LibraryCache.canonicalPlaylistId(browseId))
    }

    suspend fun clearOnlineLibrary(scope: String, keepPlaylistIds: Set<String> = emptySet()) {
        val keep = keepPlaylistIds.mapTo(mutableSetOf(), LibraryCache::canonicalPlaylistId)
        mutate(scope) { copy(library = null, playlists = null, pages = pages.filterKeys { it in keep }) }
    }

    suspend fun clear(scope: String? = null) = withContext(Dispatchers.IO) {
        diskMutex.withLock {
            synchronized(memoryLock) {
                if (scope == null) { memory.clear(); loaded.clear() }
                else { memory.remove(scope); loaded.remove(scope) }
            }
            if (scope == null) directory.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
            else fileFor(scope).delete()
            Unit
        }
    }

    private suspend fun mutate(scope: String, update: ScopeSnapshot.() -> ScopeSnapshot) = withContext(Dispatchers.IO) {
        diskMutex.withLock {
            loadLocked(scope)
            val snapshot = synchronized(memoryLock) {
                (memory[scope] ?: ScopeSnapshot(scope)).update().also { memory[scope] = it }
            }
            // Cache IO is best effort. A full disk must not fail an otherwise successful refresh.
            runCatching { write(snapshot) }
            Unit
        }
    }

    private fun write(snapshot: ScopeSnapshot) {
        val encoded = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        if (encoded.size > MAX_FILE_BYTES) return
        if (!directory.isDirectory && !directory.mkdirs()) return
        val destination = fileFor(snapshot.scope)
        val temporary = File(directory, destination.name + ".tmp")
        try {
            temporary.outputStream().use { output -> output.write(encoded); output.fd.sync() }
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            trimDisk()
        } finally {
            temporary.delete()
        }
    }

    private fun fileFor(scope: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$FILE_PREFIX$hash.json")
    }

    private fun trimMemory() {
        while (memory.size > MAX_SCOPES) {
            val eldest = memory.keys.first()
            memory.remove(eldest)
            loaded.remove(eldest)
        }
    }

    private fun trimDisk() {
        val files = directory.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() } ?: return
        var bytes = 0L
        files.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= MAX_SCOPES || bytes > MAX_DISK_BYTES) file.delete()
        }
    }

    private companion object {
        const val FILE_PREFIX = "snapshot-"
        const val MAX_SCOPES = 8
        const val MAX_PLAYLISTS = 256
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_DISK_BYTES = 64L * 1024 * 1024
    }
}

/** Local audio locations and queue/session fields are resolved at use time, never restored from metadata. */
private fun Song.remoteMetadata(): Song = copy(
    localUri = null,
    localPath = null,
    localDateAddedSeconds = null,
    localDateModifiedSeconds = null,
    downloadFormat = null,
    queueTier = QueueTier.CONTEXT,
    queueEntryId = null,
    radioName = null,
    playbackSource = null,
    playbackSourceType = null,
    playbackSourceId = null,
)
