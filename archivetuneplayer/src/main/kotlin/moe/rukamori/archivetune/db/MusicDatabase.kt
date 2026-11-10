/*
 * BRIDGE (replaces ArchiveTune's db/MusicDatabase.kt — the Room database).
 *
 * Same class name and package, and the same members the copied player
 * sources call — but the store is an in-memory map fed by the host bridge,
 * not a Room database. Reads the player needs live (the current song's
 * lyrics, format, liked flag) come from the host; writes the menus make
 * (search history, lyric saves, add-to-playlist) land here and in the host
 * hooks. Library browsing screens are not part of the ported player and
 * never query this class.
 */

package moe.rukamori.archivetune.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.bridge.ArchiveTuneBridge
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistPlayCount
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class MusicDatabase {
    private val host get() = ArchiveTuneBridge.get()
    private val lock = Any()

    private val lyricsById = ConcurrentHashMap<String, LyricsEntity>()
    private val formatsById = ConcurrentHashMap<String, moe.rukamori.archivetune.db.entities.FormatEntity>()
    private val songsById = ConcurrentHashMap<String, Song>()
    private val playlistSongs = ConcurrentHashMap<String, MutableList<PlaylistSongMap>>()
    private val playlistsRevision = MutableStateFlow(0L)

    // ---- The executor-style wrappers the menus wrap their writes in. The
    //      store is in-memory, so they simply run inline.

    fun query(block: MusicDatabase.() -> Unit) {
        block(this)
    }

    fun transaction(block: MusicDatabase.() -> Unit) {
        synchronized(lock) { block(this) }
    }

    suspend fun <R> withTransaction(block: suspend MusicDatabase.() -> R): R = synchronized(lock) { block(this) }

    // ---- Reads.

    fun song(songId: String?): Flow<Song?> =
        flowOf(songId?.let { row(it) })

    fun getSongByIdBlocking(songId: String): Song? = row(songId)

    suspend fun getSongsByIds(songIds: List<String>): List<Song> = songIds.mapNotNull { row(it) }

    fun songArtistMap(songId: String): List<SongArtistMap> = emptyList()

    fun lyrics(id: String?): Flow<LyricsEntity?> = flowOf(id?.let { lyricsById[it] })

    suspend fun getLyricsById(id: String): LyricsEntity? = lyricsById[id]

    fun format(id: String?): Flow<moe.rukamori.archivetune.db.entities.FormatEntity?> =
        flowOf(id?.let { formatsById[it] })

    fun album(id: String): Flow<Album?> = flowOf(null)

    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> = flowOf(null)

    fun albumSongs(albumId: String): Flow<List<Song>> = flowOf(emptyList())

    fun artist(id: String): Flow<Artist?> = flowOf(null)

    fun getArtistById(id: String): ArtistEntity? = null

    fun blockedSongIds(): Flow<List<String>> = flowOf(emptyList())

    fun playlistsByCreateDateAsc(): Flow<List<Playlist>> =
        playlistsRevision.map { host?.playlists().orEmpty().map { it.toPlaylistRow() } }

    fun playlistPlayCounts(): Flow<List<PlaylistPlayCount>> = flowOf(emptyList())

    fun playlistByBrowseId(browseId: String): Flow<Playlist?> = flowOf(null)

    fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String> = emptyList()

    // ---- Writes. insert/update/upsert take Any so every call shape the
    //      copied blocks use compiles; dispatch is by entity class.

    fun insert(item: Any): Long {
        store(item)
        return 1L
    }

    fun insert(items: List<Any>): List<Long> = items.map { insert(it) }

    fun update(item: Any) {
        store(item)
    }

    fun upsert(item: Any) {
        store(item)
        bump()
    }

    fun upsert(items: List<Any>) {
        items.forEach(::store)
        bump()
    }

    private fun store(item: Any) {
        when (item) {
            is LyricsEntity -> lyricsById[item.id] = item
            is SongEntity -> songsById[item.id] =
                Song(song = item, artists = emptyList())

            is Song -> songsById[item.song.id] = item
            is PlaylistSongMap ->
                playlistSongs.getOrPut(item.playlistId) { mutableListOf() }.add(item)

            is PlaylistEntity -> bump()
        }
    }

    private fun bump() {
        playlistsRevision.value = playlistsRevision.value + 1
    }

    private fun row(id: String): Song? =
        songsById[id] ?: host?.song(id)?.let { Song(song = it, artists = emptyList()) }

    private fun PlaylistEntity.toPlaylistRow(): Playlist =
        Playlist(
            playlist = this,
            songCount = playlistSongs[this.id]?.size ?: 0,
            songThumbnails = emptyList(),
        )

    // ---- Playlist membership: the add-to-playlist menu.

    fun addSongToPlaylist(
        playlist: Playlist,
        songIds: List<String>,
    ) {
        addSongEntriesToPlaylist(
            playlist = playlist,
            songEntries = songIds.map { songId -> songId to null },
        )
    }

    fun addSongEntriesToPlaylist(
        playlist: Playlist,
        songEntries: List<Pair<String, String?>>,
    ) {
        var position = playlistSongs[playlist.id]?.size ?: 0
        songEntries.forEach { (songId, setVideoId) ->
            insert(
                PlaylistSongMap(
                    songId = songId,
                    playlistId = playlist.id,
                    position = position++,
                    setVideoId = setVideoId,
                ),
            )
            host?.addToPlaylist(playlist.id, songId)
        }
        bump()
    }

    // ---- Lyric persistence (the lyrics menu's save/replace path).

    fun replaceLyrics(
        id: String,
        lyrics: String,
        source: String,
        providerName: String = "",
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        lyricsById[id] =
            LyricsEntity(
                id = id,
                lyrics = lyrics,
                source = source,
                providerName = providerName,
                updatedAt = updatedAt,
            )
    }

    fun replaceLyricsIfAbsentOrNotFound(
        id: String,
        lyrics: String,
        source: String = LyricsEntity.Source.REMOTE.value,
        providerName: String = "",
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        val existing = lyricsById[id]
        if (existing == null || existing.lyrics == LyricsEntity.LYRICS_NOT_FOUND) {
            replaceLyrics(id, lyrics, source, providerName, updatedAt)
        }
    }

    fun backfillLyricsProviderName(
        id: String,
        providerName: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int {
        val existing = lyricsById[id] ?: return 0
        if (providerName.isNotEmpty() && existing.providerName.isNullOrEmpty()) {
            lyricsById[id] = existing.copy(providerName = providerName, updatedAt = updatedAt)
            return 1
        }
        return 0
    }

    // ---- Blocked songs: inert.

    suspend fun setSongBlockedAt(songId: String, blockedAt: LocalDateTime?) {}
}
