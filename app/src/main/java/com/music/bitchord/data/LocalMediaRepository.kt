package com.music.bitchord.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import androidx.core.content.ContextCompat
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable
internal data class CachedLocalSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val albumName: String? = null,
    val localUri: String? = null,
    val localPath: String? = null,
    val localDateAddedSeconds: Long? = null,
    val localDateModifiedSeconds: Long? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
) {
    fun toSong(): Song = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        albumName = albumName,
        localUri = localUri,
        localPath = localPath,
        localDateAddedSeconds = localDateAddedSeconds,
        localDateModifiedSeconds = localDateModifiedSeconds,
        discNumber = discNumber,
        trackNumber = trackNumber,
    )

    companion object {
        fun fromSong(s: Song): CachedLocalSong = CachedLocalSong(
            videoId = s.videoId,
            title = s.title,
            artist = s.artist,
            thumbnailUrl = s.thumbnailUrl,
            durationText = s.durationText,
            albumName = s.albumName,
            localUri = s.localUri,
            localPath = s.localPath,
            localDateAddedSeconds = s.localDateAddedSeconds,
            localDateModifiedSeconds = s.localDateModifiedSeconds,
            discNumber = s.discNumber,
            trackNumber = s.trackNumber,
        )
    }
}

object LocalMediaRepository {

    private const val TAG = "BitChord"
    private const val MIN_LOCAL_MUSIC_DURATION_MS = 30_000L
    private const val CACHE_FILE_NAME = "local_library_cache.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    val isIndexing = MutableStateFlow(false)
    val indexedCount = MutableStateFlow(0)
    val liveDiscoveredSongs = MutableStateFlow<List<Song>>(emptyList())

    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeScanJob: Deferred<List<Song>>? = null
    private val scanMutex = Mutex()

    @Volatile
    var cachedSongsMemory: List<Song>? = null
    private val matchingTitleToArtistsMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    private fun rebuildMatchingIndex(songs: List<Song>?) {
        matchingTitleToArtistsMap.clear()
        if (songs.isNullOrEmpty()) return
        val map = HashMap<String, MutableList<String>>()
        for (s in songs) {
            val cleanTitle = OnlineArtworkRepository.sanitizeTitle(s.title)
            val tNorm = cleanTitle.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
            if (tNorm.isNotEmpty()) {
                val cleanArtist = OnlineArtworkRepository.sanitizeArtist(s.artist)
                val aNorm = cleanArtist.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
                val list = map.getOrPut(tNorm) { mutableListOf() }
                if (aNorm !in list) list.add(aNorm)
            }
        }
        matchingTitleToArtistsMap.putAll(map)
    }

    fun hasCachedLocalMusic(context: Context): Boolean {
        if (!cachedSongsMemory.isNullOrEmpty()) return true
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        return cacheFile.exists() && cacheFile.length() > 0L
    }

    fun getCachedLocalMusic(context: Context): List<Song> {
        cachedSongsMemory?.let { return it }
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists() || cacheFile.length() == 0L) return emptyList()
        return runCatching {
            val text = cacheFile.readText(Charsets.UTF_8)
            val cached = json.decodeFromString<List<CachedLocalSong>>(text)
            val mapped = cached.map { it.toSong() }
            cachedSongsMemory = mapped
            rebuildMatchingIndex(mapped)
            mapped
        }.getOrElse { err ->
            Log.w(TAG, "Failed reading $CACHE_FILE_NAME: ${err.message}")
            emptyList()
        }
    }

    fun saveCachedLocalMusic(context: Context, songs: List<Song>) {
        cachedSongsMemory = songs
        rebuildMatchingIndex(songs)
        runCatching {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            val tmpFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            val mapped = songs.map { CachedLocalSong.fromSong(it) }
            val jsonStr = json.encodeToString(mapped)
            tmpFile.writeText(jsonStr, Charsets.UTF_8)
            if (tmpFile.exists()) {
                if (cacheFile.exists()) cacheFile.delete()
                tmpFile.renameTo(cacheFile)
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed saving $CACHE_FILE_NAME: ${err.message}")
        }
    }

    private const val LIKED_SONGS_PREFS = "local_liked_songs"
    private const val KEY_LIKED_SET = "liked_ids"

    fun isLocalTrackLiked(context: Context, videoId: String): Boolean {
        val prefs = context.getSharedPreferences(LIKED_SONGS_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_LIKED_SET, emptySet()) ?: emptySet()
        return videoId in set
    }

    fun getLikedTrackIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(LIKED_SONGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_LIKED_SET, emptySet()) ?: emptySet()
    }

    fun setLocalTrackLiked(context: Context, videoId: String, status: LikeStatus) {
        val prefs = context.getSharedPreferences(LIKED_SONGS_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_LIKED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (status == LikeStatus.LIKE) {
            set.add(videoId)
        } else {
            set.remove(videoId)
        }
        prefs.edit().putStringSet(KEY_LIKED_SET, set).apply()
    }

    fun clearCache(context: Context) {
        activeScanJob?.cancel()
        activeScanJob = null
        cachedSongsMemory = null
        rebuildMatchingIndex(null)
        runCatching {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) cacheFile.delete()
        }
        OnlineArtworkRepository.clearCache(context)
    }

    /**
     * Updates songs in memory and disk cache with resolved artwork URLs,
     * and emits the new list into [liveDiscoveredSongs] so the UI recomposes
     * immediately — without this the artwork would vanish on the next navigation
     * because the StateFlow held the pre-artwork version.
     */
    fun updateSongsArtwork(context: Context, updated: Map<String, String>) {
        if (updated.isEmpty()) return
        val current = cachedSongsMemory ?: getCachedLocalMusic(context)
        if (current.isEmpty()) return
        var changed = false
        val newSongs = current.map { song ->
            val newArt = updated[song.videoId] ?: updated[song.localUri] ?: updated[song.localPath]
            if (newArt != null && newArt != song.thumbnailUrl) {
                changed = true
                song.copy(thumbnailUrl = newArt)
            } else {
                song
            }
        }
        if (!changed) return
        saveCachedLocalMusic(context, newSongs)
        runCatching { com.music.bitchord.ui.screens.LocalMusicDerivedCache.invalidate() }
        updated.keys.forEach { key ->
            LocalAudioCoverFetcher.invalidate(key, context)
        }
        runCatching {
            coil3.SingletonImageLoader.get(context).memoryCache?.clear()
        }
        liveDiscoveredSongs.value = newSongs
    }

    /**
     * Asynchronously enriches local albums and songs that lack cover art.
     * Respects user permission, strictly preserves existing/attached art,
     * writes cover.jpg only if opted-in, and persists discovered art.
     */
    suspend fun enrichLibraryArtwork(context: Context, songs: List<Song>) = withContext(Dispatchers.IO) {
        if (!AppSettings.autoDiscoverArtwork.value) return@withContext
        val missingArt = songs.filter { s ->
            !OnlineArtworkRepository.hasAttachedArtwork(context, s)
        }
        if (missingArt.isEmpty()) return@withContext

        val updates = mutableMapOf<String, String>()

        // 1. Separate genuine albums from loose tracks / generic album singles
        val (albumTracks, looseTracks) = missingArt.partition { track ->
            val sanitized = OnlineArtworkRepository.sanitizeAlbum(track.albumName.orEmpty())
            sanitized.isNotBlank()
        }

        // 2. Process genuine albums (grouped by artist and album)
        if (albumTracks.isNotEmpty()) {
            val byAlbum = albumTracks.groupBy { track ->
                val cleanArtist = OnlineArtworkRepository.sanitizeArtist(track.artist)
                val cleanAlbum = OnlineArtworkRepository.sanitizeAlbum(track.albumName.orEmpty())
                "$cleanArtist:::$cleanAlbum"
            }

            for ((key, tracksInAlbum) in byAlbum) {
                val parts = key.split(":::")
                val artist = parts.getOrNull(0).orEmpty()
                val album = parts.getOrNull(1).orEmpty()
                if (album.isBlank()) continue

                val foundArt = OnlineArtworkRepository.resolveAlbumArt(context, artist, album)
                if (foundArt != null) {
                    if (AppSettings.saveAlbumCoverToFolder.value) {
                        OnlineArtworkRepository.saveCoverToAlbumFolder(context, tracksInAlbum, foundArt)
                    }
                    for (track in tracksInAlbum) {
                        updates[track.videoId] = foundArt
                        track.localUri?.let { updates[it] = foundArt }
                        track.localPath?.let { updates[it] = foundArt }
                    }
                }
                kotlinx.coroutines.delay(50)
            }
        }

        // 3. Process loose tracks individually so tracks NEVER hijack each other's artwork
        if (looseTracks.isNotEmpty()) {
            for (track in looseTracks) {
                val cleanArtist = OnlineArtworkRepository.sanitizeArtist(track.artist)
                val cleanTitle = OnlineArtworkRepository.sanitizeTitle(track.title)
                if (cleanTitle.isNotBlank()) {
                    val foundArt = OnlineArtworkRepository.resolveSongArt(context, cleanArtist, cleanTitle, null)
                    if (foundArt != null) {
                        if (AppSettings.saveAlbumCoverToFolder.value) {
                            OnlineArtworkRepository.saveCoverToAlbumFolder(context, track, foundArt)
                        }
                        updates[track.videoId] = foundArt
                        track.localUri?.let { updates[it] = foundArt }
                        track.localPath?.let { updates[it] = foundArt }
                    }
                }
                kotlinx.coroutines.delay(50)
            }
        }

        // Flush any pending disk cache writes
        OnlineArtworkRepository.flushDiskCache(context)

        if (updates.isNotEmpty()) {
            updateSongsArtwork(context, updates)
        }
    }

    // `mp4` only ever reaches this list as an audio row — MediaStore files a
    // video .mp4 under Video, not Audio — so it is the audio-only MP4s Dolby
    // and Apple music downloads come as (AC-4, E-AC-3, ALAC). `dsf`/`dff` are
    // DSD, which DsdExtractor plays.
    private val localMusicExtensions = setOf(
        "mp3", "m4a", "mp4", "flac", "ogg", "opus", "aac", "wav", "wave", "webm", "alac", "wma", "aiff", "dsf", "dff",
    )

    private val nonMusicPathSegments = listOf(
        "/alarms/",
        "/notifications/",
        "/ringtones/",
        "/podcasts/",
        "/audiobooks/",
        "/recordings/",
        "/voice recorder/",
        "/sound_recorder/",
        "/call_rec/",
        "/whatsapp voice notes/",
    )

    /** Check if storage/audio permission is granted to query device local music. */
    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Retrieves all songs in the `Music/BitChord` directory, combining app downloads
     * with any local audio files present in that folder.
     *
     * The download record is the better source for a title and a credit — it
     * holds what the catalogue row said, not what a scanner guessed off a
     * filename — but it only started carrying the album at all recently, and
     * an album page's rows never name their own release. So whatever the media
     * scanner read off each file is collected alongside and used to fill the
     * gaps, which is what keeps the Albums tab from being empty for everything
     * downloaded before that field existed.
     */
    suspend fun getDownloadedSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val appDownloads = Downloads.getDownloadedSongs(context)
        val knownUris = appDownloads.mapNotNull { it.localUri }.toSet()
        val extraSongs = mutableListOf<Song>()

        /** uri to what the media scanner read off that file. */
        val scanned = mutableMapOf<String, ScannedTags>()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                )
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%${DownloadStore.FOLDER}%")

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val tags = ScannedTags(
                            albumName = cursor.getString(albumCol).cleanTag(),
                            artworkUrl = "audio:$contentUri",
                            dateAddedSeconds = cursor.getLong(dateAddedCol),
                            dateModifiedSeconds = cursor.getLong(dateModifiedCol),
                        )
                        scanned[contentUri] = tags
                        if (contentUri !in knownUris && isAudioFileName(name)) {
                            extraSongs.add(buildSongFromUri(context, contentUri, name, tags))
                        }
                    }
                }
            } else {
                val folder = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_MUSIC,
                    ),
                    DownloadStore.FOLDER,
                )
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown().forEach { file ->
                        if (file.isFile && isAudioFileName(file.name)) {
                            val uriStr = Uri.fromFile(file).toString()
                            if (uriStr !in knownUris) {
                                val modified = file.lastModified().takeIf { it > 0 }?.div(1_000)
                                extraSongs.add(
                                    buildSongFromUri(
                                        context,
                                        uriStr,
                                        file.name,
                                        ScannedTags(null, null, modified, modified),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Failed scanning Music/BitChord directory: ${it.message}") }

        val filled = appDownloads.map { song ->
            val uri = song.localUri ?: return@map song
            val tags = scanned[uri] ?: return@map song
            song.copy(
                albumName = song.albumName ?: tags.albumName,
                localDateAddedSeconds = tags.dateAddedSeconds,
                localDateModifiedSeconds = tags.dateModifiedSeconds,
            )
        }

        (filled + extraSongs).distinctBy { it.localUri ?: it.videoId }
    }

    /**
     * The parts of a scanner row worth reading back — everything else about a
     * download is better known from the record that made it.
     */
    private class ScannedTags(
        val albumName: String?,
        val artworkUrl: String?,
        val dateAddedSeconds: Long?,
        val dateModifiedSeconds: Long?,
    )

    /** What MediaStore writes into a column it has nothing for. */
    private fun String?.cleanTag(): String? =
        takeUnless { it.isNullOrBlank() || it == "<unknown>" }

    /**
     * Retrieves all device local music.
     * Returns cached library immediately when available (Namida parity),
     * or triggers a full scan if no cache is present or forceRefresh is true.
     */
    suspend fun getLocalMusic(context: Context, forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        if (!hasStoragePermission(context)) return@withContext emptyList()

        if (!forceRefresh) {
            val cached = getCachedLocalMusic(context)
            if (cached.isNotEmpty()) return@withContext cached
        }

        scanAndCacheLocalMusic(context)
    }

    /**
     * Executes the comprehensive indexing scan across MediaStore volumes,
     * physical filesystem paths and SAF trees, deduplicating and caching the result to disk.
     */
    /**
     * Executes the comprehensive indexing scan across MediaStore volumes,
     * physical filesystem paths and SAF trees, deduplicating and caching the result to disk.
     * Guaranteed to persist in the background even if callers leave the local music screen.
     */
    suspend fun scanAndCacheLocalMusic(context: Context): List<Song> {
        if (!hasStoragePermission(context)) return emptyList()

        val job = scanMutex.withLock {
            val existing = activeScanJob
            if (existing != null && existing.isActive) {
                return@withLock existing
            }
            val appContext = context.applicationContext
            val newJob = scanScope.async {
                performScanAndCache(appContext)
            }
            activeScanJob = newJob
            newJob
        }
        return job.await()
    }

    private suspend fun performScanAndCache(context: Context): List<Song> = withContext(Dispatchers.IO) {
        isIndexing.value = true
        indexedCount.value = 0
        liveDiscoveredSongs.value = emptyList()
        try {
            val songs = mutableListOf<Song>()
            fun notifyProgress(force: Boolean = false) {
                indexedCount.value = songs.size
                if (force || songs.size - liveDiscoveredSongs.value.size >= 25) {
                    liveDiscoveredSongs.value = songs.toList()
                }
            }
        // This scan runs over every audio file on the device, which includes
        // whatever this app has downloaded into Music/BitChord alongside
        // everything else — but by content URI, the only thing MediaStore
        // offers here, that download is indistinguishable from a file the
        // user copied on by hand. Reversing [Downloads.saved] hands a
        // downloaded track its real YouTube id back, which is what lets
        // PlaybackTracker recognise it as a video worth registering a play
        // for — a content URI fails its id check on purpose, since most rows
        // here really are just local files with nothing to sync.
        val videoIdByUri = Downloads.saved.value.entries.associate { (id, uri) -> uri to id }
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Audio.Media.DISC_NUMBER)
        }

        val filterNonMusic = AppSettings.filterNonMusicAudio.value
        val selection = if (filterNonMusic) {
            val nonSpecial = "(${MediaStore.Audio.Media.IS_ALARM} = 0 OR ${MediaStore.Audio.Media.IS_ALARM} IS NULL)" +
                " AND (${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 OR ${MediaStore.Audio.Media.IS_NOTIFICATION} IS NULL)" +
                " AND (${MediaStore.Audio.Media.IS_RINGTONE} = 0 OR ${MediaStore.Audio.Media.IS_RINGTONE} IS NULL)" +
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) " AND (${MediaStore.Audio.Media.IS_PODCAST} = 0 OR ${MediaStore.Audio.Media.IS_PODCAST} IS NULL)" else "")
            "$nonSpecial AND (${MediaStore.Audio.Media.DURATION} >= ?" +
                " OR ${MediaStore.Audio.Media.DURATION} <= 0" +
                " OR ${MediaStore.Audio.Media.DURATION} IS NULL)"
        } else {
            null
        }
        val selectionArgs = if (filterNonMusic) {
            arrayOf(MIN_LOCAL_MUSIC_DURATION_MS.toString())
        } else {
            null
        }
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val contentUris = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val volumes = MediaStore.getExternalVolumeNames(context)
                for (vol in volumes) {
                    val u = MediaStore.Audio.Media.getContentUri(vol)
                    if (u !in contentUris) contentUris.add(u)
                }
            }
        }
        if (MediaStore.Audio.Media.EXTERNAL_CONTENT_URI !in contentUris) {
            contentUris.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        val seenUris = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        val folderFilter = AppSettings.localMusicFolderUri.value

        for (volumeUri in contentUris) {
            runCatching {
                context.contentResolver.query(
                    volumeUri,
                    projection.toTypedArray(),
                    selection,
                    selectionArgs,
                    sortOrder,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1
                    val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    val discCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val displayName = cursor.getString(displayNameCol).orEmpty()
                        val rawTitle = cursor.getString(titleCol)
                        val rawArtist = cursor.getString(artistCol)
                        val rawAlbum = cursor.getString(albumCol)
                        val durationMs = cursor.getLong(durationCol)
                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                        val relativePath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                        val norm = path?.normalizedPath()
                        if (norm != null && seenPaths.contains(norm)) continue

                        if (filterNonMusic && !isEligibleLocalMusic(durationMs, displayName, path)) continue
                        if (!isInSelectedFolder(path, relativePath, folderFilter)) continue

                        val contentUri = ContentUris.withAppendedId(volumeUri, id).toString()
                        if (!seenUris.add(contentUri)) continue
                        if (norm != null) seenPaths.add(norm)

                        var discNum = if (discCol >= 0) cursor.getInt(discCol).takeIf { it > 0 } else null
                        var trackNum: Int? = null
                        if (trackCol >= 0) {
                            val rawTrack = cursor.getInt(trackCol).takeIf { it > 0 }
                            if (rawTrack != null) {
                                if (rawTrack >= 1000) {
                                    if (discNum == null) discNum = rawTrack / 1000
                                    trackNum = rawTrack % 1000
                                } else {
                                    trackNum = rawTrack
                                }
                            }
                        }

                        if (rawAlbum != null) {
                            val discMatch = DISC_SUFFIX_REGEX.find(rawAlbum)
                            if (discMatch != null && discNum == null) {
                                val dg = discMatch.groupValues.getOrNull(1)
                                discNum = dg?.toIntOrNull() ?: dg?.firstOrNull()?.let { ch ->
                                    if (ch.isLetter()) (ch.uppercaseChar() - 'A' + 1) else null
                                }
                            }
                        }

                        if (discNum == null && path != null) {
                            val parent = File(path).parentFile
                            if (parent != null && DISC_FOLDER_REGEX.matches(parent.name.trim())) {
                                val match = DISC_FOLDER_REGEX.find(parent.name.trim())
                                val dg = match?.groupValues?.getOrNull(1)
                                discNum = dg?.toIntOrNull() ?: dg?.firstOrNull()?.let { ch ->
                                    if (ch.isLetter()) (ch.uppercaseChar() - 'A' + 1) else null
                                }
                            }
                        }

                        val title = rawTitle.cleanTag()?.removeAudioExtension()
                            ?: displayName.substringBeforeLast('.').takeIf { it.isNotBlank() }
                            ?: "Track $id"
                        val artist = rawArtist.takeUnless { it.isNullOrBlank() || it == "<unknown>" } ?: "Unknown Artist"
                        val albumName = rawAlbum.takeUnless { it.isNullOrBlank() || it == "<unknown>" }?.let { cleanDiscSuffix(it) }

                        val folderArt = path?.let { p ->
                            val parent = File(p).parentFile
                            val isDiscDir = parent != null && DISC_FOLDER_REGEX.matches(parent.name.trim())
                            val searchDirs = if (isDiscDir && parent?.parentFile != null) {
                                listOfNotNull(parent, parent.parentFile)
                            } else {
                                listOfNotNull(parent)
                            }
                            var foundArt: String? = null
                            for (dir in searchDirs) {
                                if (dir.exists()) {
                                    val found = File(dir, "cover.jpg").takeIf { it.exists() }
                                        ?: File(dir, "folder.jpg").takeIf { it.exists() }
                                        ?: File(dir, "Cover.jpg").takeIf { it.exists() }
                                        ?: File(dir, "Folder.jpg").takeIf { it.exists() }
                                        ?: File(dir, "cover.png").takeIf { it.exists() }
                                        ?: File(dir, "folder.png").takeIf { it.exists() }
                                    if (found != null) {
                                        foundArt = Uri.fromFile(found).toString()
                                        break
                                    }
                                }
                            }
                            foundArt
                        }
                        val artworkUrl = folderArt ?: "audio:$contentUri"
                        val durationText = durationMs.takeIf { it > 0 }?.let(::formatDuration)

                        songs.add(
                            Song(
                                videoId = videoIdByUri[contentUri] ?: contentUri,
                                title = title,
                                artist = artist,
                                thumbnailUrl = artworkUrl,
                                durationText = durationText,
                                albumName = albumName,
                                localUri = contentUri,
                                localPath = path,
                                localDateAddedSeconds = cursor.getLong(dateAddedCol),
                                localDateModifiedSeconds = cursor.getLong(dateModifiedCol),
                                discNumber = discNum,
                                trackNumber = trackNum,
                            )
                        )
                        if (songs.size % 25 == 0) {
                            notifyProgress()
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Failed scanning volume $volumeUri: ${it.message}") }
            notifyProgress(force = true)
        }

        // Also query MediaStore.Files across all volumes to catch audio files that
        // Android's media scanner did not index under MediaStore.Audio.Media.
        val filesUris = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val volumes = MediaStore.getExternalVolumeNames(context)
                for (vol in volumes) {
                    val u = MediaStore.Files.getContentUri(vol)
                    if (u !in filesUris) filesUris.add(u)
                }
            }
        }
        if (MediaStore.Files.getContentUri("external") !in filesUris) {
            filesUris.add(MediaStore.Files.getContentUri("external"))
        }

        val filesProjection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            filesProjection.add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        }

        val filesSelection = buildString {
            append("(")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'audio/%'")
            for (ext in localMusicExtensions) {
                append(" OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.")
                append(ext)
                append("'")
            }
            append(")")
            append(" AND (${MediaStore.Files.FileColumns.SIZE} > 10000 OR ${MediaStore.Files.FileColumns.SIZE} IS NULL)")
        }

        for (filesVolumeUri in filesUris) {
            runCatching {
                context.contentResolver.query(
                    filesVolumeUri,
                    filesProjection.toTypedArray(),
                    filesSelection,
                    null,
                    sortOrder,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol).orEmpty()
                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                        val relativePath = if (relPathCol >= 0) cursor.getString(relPathCol) else null
                        val contentUri = ContentUris.withAppendedId(filesVolumeUri, id).toString()

                        val norm = path?.normalizedPath()
                        if (norm != null && seenPaths.contains(norm)) continue
                        if (seenUris.contains(contentUri)) continue

                        if (filterNonMusic && !isEligibleLocalMusic(0, name, path)) continue
                        if (!isInSelectedFolder(path, relativePath, folderFilter)) continue

                        if (path != null) {
                            val f = File(path)
                            if (f.exists() && f.isFile) {
                                val song = buildSongFromFile(f)
                                if (song != null) {
                                    seenUris.add(contentUri)
                                    if (norm != null) seenPaths.add(norm)
                                    songs.add(song.copy(localUri = contentUri))
                                    if (songs.size % 25 == 0) notifyProgress()
                                    continue
                                }
                            }
                        }

                        val parsed = parseMetadataFromPath(name, null)
                        seenUris.add(contentUri)
                        if (norm != null) seenPaths.add(norm)
                        songs.add(
                            Song(
                                videoId = contentUri,
                                title = parsed.title,
                                artist = parsed.artist,
                                thumbnailUrl = "audio:$contentUri",
                                durationText = null,
                                albumName = parsed.album,
                                localUri = contentUri,
                                localPath = path,
                                localDateAddedSeconds = cursor.getLong(addedCol),
                                localDateModifiedSeconds = cursor.getLong(modCol),
                                discNumber = parsed.discNumber,
                                trackNumber = parsed.trackNumber,
                            ),
                        )
                        if (songs.size % 25 == 0) notifyProgress()
                    }
                }
            }.onFailure { Log.w(TAG, "Failed scanning MediaStore.Files on $filesVolumeUri: ${it.message}") }
            notifyProgress(force = true)
        }

        // Direct file discovery on selected folder and standard storage paths
        // to find songs not indexed by Android's system MediaScanner (Namida parity).
        val selectedPath = selectedFolderPath(folderFilter)
        val targetFolders = mutableListOf<File>()

        if (folderFilter.isNotBlank()) {
            if (!selectedPath.isNullOrBlank()) {
                val f = File(selectedPath)
                if (f.exists() && f.isDirectory) targetFolders.add(f)
            }
        } else {
            val internalStorage = File("/storage/emulated/0")
            if (internalStorage.exists() && internalStorage.isDirectory) {
                internalStorage.listFiles()?.forEach { dir ->
                    if (dir.isDirectory && !dir.name.startsWith(".") && dir.name != "Android") {
                        targetFolders.add(dir)
                    }
                }
            }

            // Direct SD Card scanning: detect physical SD card mount points in /storage
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                storageRoot.listFiles()?.forEach { vol ->
                    if (vol.isDirectory && vol.name != "emulated" && vol.name != "self" && !vol.name.startsWith(".")) {
                        vol.listFiles()?.forEach { dir ->
                            if (dir.isDirectory && !dir.name.startsWith(".") && dir.name != "Android" && dir !in targetFolders) {
                                targetFolders.add(dir)
                            }
                        }
                    }
                }
            }
            // Also query Android's external storage directories to discover all physical SD card mount roots
            runCatching {
                ContextCompat.getExternalFilesDirs(context, null).forEach { extDir ->
                    if (extDir != null) {
                        val rootPath = extDir.absolutePath.substringBefore("/Android/data")
                        if (rootPath.isNotBlank()) {
                            val vol = File(rootPath)
                            if (vol.exists() && vol.isDirectory) {
                                vol.listFiles()?.forEach { dir ->
                                    if (dir.isDirectory && !dir.name.startsWith(".") && dir.name != "Android" && dir !in targetFolders) {
                                        targetFolders.add(dir)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (folder in targetFolders) {
            runCatching {
                folder.walkTopDown().maxDepth(15).forEach { file ->
                    if (file.isFile && isAudioFileName(file.name)) {
                        val norm = file.absolutePath.normalizedPath()
                        if (!seenPaths.contains(norm)) {
                            if (filterNonMusic && !isEligibleLocalMusic(0, file.name, file.absolutePath)) return@forEach
                            val song = buildSongFromFile(file)
                            if (song != null) {
                                val u = song.localUri ?: return@forEach
                                if (seenUris.add(u)) {
                                    seenPaths.add(norm)
                                    songs.add(song)
                                    if (songs.size % 25 == 0) notifyProgress()
                                }
                            }
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Direct folder scan failed for ${folder.path}: ${it.message}") }
            notifyProgress(force = true)
        }

        // Also if SAF tree URI is configured, walk using fast DocumentsContract queries
        if (folderFilter.isNotBlank()) {
            runCatching {
                val parsedUri = Uri.parse(folderFilter)
                val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(parsedUri) }.getOrNull()
                    ?: parsedUri.lastPathSegment.orEmpty()

                fun scanSafFast(parentDocId: String, depth: Int, parentFolderArtUri: String? = null) {
                    if (depth <= 0) return
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parsedUri, parentDocId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    )
                    var folderCoverUri: String? = parentFolderArtUri
                    val subDirs = mutableListOf<String>()
                    val audioItems = mutableListOf<Triple<String, String, Long>>()

                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idCol)
                            val name = cursor.getString(nameCol) ?: continue
                            val mime = cursor.getString(mimeCol).orEmpty()
                            val mod = cursor.getLong(modCol)

                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (name != "Android" && !name.startsWith(".")) {
                                    subDirs.add(docId)
                                }
                            } else {
                                val lower = name.lowercase(Locale.ROOT)
                                if (lower == "cover.jpg" || lower == "folder.jpg" || lower == "cover.png" || lower == "folder.png") {
                                    folderCoverUri = DocumentsContract.buildDocumentUriUsingTree(parsedUri, docId).toString()
                                } else if (isAudioFileName(name)) {
                                    audioItems.add(Triple(docId, name, mod))
                                }
                            }
                        }
                    }

                    for (item in audioItems) {
                        val (docId, name, mod) = item
                        val resolvedPath = resolvePhysicalPath(docId)
                        val normPath = resolvedPath?.normalizedPath()
                        if (normPath != null && seenPaths.contains(normPath)) continue

                        if (filterNonMusic && !isEligibleLocalMusic(0, name, resolvedPath)) continue

                        val docUri = DocumentsContract.buildDocumentUriUsingTree(parsedUri, docId)
                        val uriStr = docUri.toString()
                        if (!seenUris.contains(uriStr)) {
                            val song = buildSongFromSaf(context, docUri, name, mod, folderCoverUri, resolvedPath, filterNonMusic)
                            if (song != null) {
                                seenUris.add(uriStr)
                                if (normPath != null) seenPaths.add(normPath)
                                songs.add(song)
                                if (songs.size % 25 == 0) notifyProgress()
                            }
                        }
                    }

                    for (subId in subDirs) {
                        scanSafFast(subId, depth - 1, folderCoverUri)
                    }
                }

                scanSafFast(rootDocId, 15, null)
                notifyProgress(force = true)
            }.onFailure { Log.w(TAG, "Fast SAF tree scan failed: ${it.message}") }
        }

        notifyProgress(force = true)

        // Propagate album artwork across the collection so tracks in an album inherit the cover
        val albumCoverMap = mutableMapOf<String, String>()
        for (song in songs) {
            val album = song.albumName?.trim()
            val art = song.thumbnailUrl
            if (!album.isNullOrBlank() && !art.isNullOrBlank() && !art.startsWith("audio:")) {
                albumCoverMap.putIfAbsent(album.lowercase(Locale.ROOT), art)
            }
        }
        val enrichedSongs = if (albumCoverMap.isNotEmpty()) {
            songs.map { song ->
                val album = song.albumName?.trim()?.lowercase(Locale.ROOT)
                val inherited = if (album != null) albumCoverMap[album] else null
                val currentThumb = song.thumbnailUrl
                if (inherited != null && (currentThumb == null || currentThumb.startsWith("audio:"))) {
                    song.copy(thumbnailUrl = inherited)
                } else {
                    song
                }
            }
        } else {
            songs
        }

        val deduplicated = deduplicateTracks(enrichedSongs, AppSettings.preventDuplicateTracks.value)
        saveCachedLocalMusic(context, deduplicated)
        indexedCount.value = deduplicated.size
        liveDiscoveredSongs.value = deduplicated
        if (AppSettings.autoDiscoverArtwork.value) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { enrichLibraryArtwork(context, deduplicated) }
            }
        }
        if (AppSettings.downloadCreateNomedia.value) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { com.music.bitchord.download.DownloadStore.cleanupGalleryImages(context) }
            }
        }
        deduplicated
    } finally {
        isIndexing.value = false
        scanMutex.withLock {
            if (activeScanJob?.isActive != true) {
                activeScanJob = null
            }
        }
    }
}

    internal fun deduplicateTracks(
        tracks: List<Song>,
        preventDuplicates: Boolean,
    ): List<Song> {
        if (tracks.isEmpty()) return emptyList()

        // 1. Physical path / unique URI deduplication (always active)
        val uniqueByPathOrUri = mutableMapOf<String, Song>()
        for (song in tracks) {
            val pathKey = song.localPath?.normalizedPath()
            val uriKey = song.localUri ?: song.videoId
            val key = pathKey ?: uriKey
            val existing = uniqueByPathOrUri[key]
            if (existing == null || isBetterSongEntry(song, existing)) {
                uniqueByPathOrUri[key] = song
            }
        }

        val primaryList = uniqueByPathOrUri.values.toList()
        if (!preventDuplicates) {
            return primaryList
        }

        // 2. Namida-parity smart deduplication across directories & storage volumes
        val resultMap = mutableMapOf<String, Song>()
        for (song in primaryList) {
            val cleanTitle = song.title.trim().lowercase(Locale.ROOT)
            val cleanArtist = song.artist.trim().lowercase(Locale.ROOT)
            val fileName = (song.localPath?.substringAfterLast('/') ?: song.title.removeAudioExtension()).trim().lowercase(Locale.ROOT)
            val duration = song.durationText?.trim() ?: ""

            val canonicalKey = when {
                // High confidence match: non-unknown artist and non-blank title
                cleanArtist != "unknown artist" && cleanTitle.isNotBlank() && cleanTitle.length > 2 -> {
                    "meta:$cleanArtist::$cleanTitle"
                }
                // Specific filename match
                fileName.isNotBlank() && !isGenericTrackFileName(fileName) -> {
                    "file:$fileName::$duration"
                }
                // Fallback to exact path or uri
                else -> {
                    "path:${song.localPath?.normalizedPath() ?: song.localUri ?: song.videoId}"
                }
            }

            val existing = resultMap[canonicalKey]
            if (existing == null || isBetterSongEntry(song, existing)) {
                resultMap[canonicalKey] = song
            }
        }

        return resultMap.values.toList()
    }

    private fun isGenericTrackFileName(name: String): Boolean {
        val base = name.substringBeforeLast('.').trim().lowercase(Locale.ROOT)
        return base.matches(Regex("""^(track\s*\d+|\d+|audio\s*\d+|sound\s*\d+|untitled|voice\s*\d+)$"""))
    }

    private fun isBetterSongEntry(candidate: Song, current: Song): Boolean {
        var scoreCandidate = 0
        var scoreCurrent = 0
        if (!candidate.durationText.isNullOrBlank()) scoreCandidate += 2
        if (!current.durationText.isNullOrBlank()) scoreCurrent += 2
        if (!candidate.albumName.isNullOrBlank()) scoreCandidate += 2
        if (!current.albumName.isNullOrBlank()) scoreCurrent += 2
        if (candidate.artist != "Unknown Artist" && candidate.artist.isNotBlank()) scoreCandidate += 1
        if (current.artist != "Unknown Artist" && current.artist.isNotBlank()) scoreCurrent += 1
        if (!candidate.localPath.isNullOrBlank()) scoreCandidate += 1
        val candThumb = candidate.thumbnailUrl
        if (candThumb != null && !candThumb.startsWith("audio:")) scoreCandidate += 1
        val currThumb = current.thumbnailUrl
        if (currThumb != null && !currThumb.startsWith("audio:")) scoreCurrent += 1
        return scoreCandidate > scoreCurrent
    }

    private val ARTIST_DELIMITER_REGEX = Regex(
        """\s*;\s*|\s*,\s*|\s+/\s+|\s+&\s+|\s+＆\s+|\s+(?:feat|ft|featuring)\.?\s+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Splits artist credits into individual artists, preserving compound names
     * without spaces (such as AC/DC).
     */
    fun splitArtists(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ARTIST_DELIMITER_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Unknown Artist", ignoreCase = true) }
    }

    /**
     * Returns all indexed local tracks featuring [artistName], either as primary
     * or guest artist.
     */
    fun getLocalSongsForArtist(artistName: String, context: Context? = null): List<Song> {
        val allSongs = cachedSongsMemory ?: context?.let { getCachedLocalMusic(it) } ?: return emptyList()
        val target = artistName.trim().lowercase(Locale.ROOT)
        return allSongs.filter { song ->
            splitArtists(song.artist).any { it.trim().lowercase(Locale.ROOT) == target }
        }
    }

    /** Human-readable path for the folder setting without exposing provider internals. */
    fun selectedFolderLabel(treeUri: String?): String? =
        if (treeUri.isNullOrBlank()) null else selectedFolder(treeUri)?.label

    /** Decoded physical filesystem path for a SAF folder tree, preserving exact casing. */
    fun selectedFolderPath(treeUri: String?): String? =
        if (treeUri.isNullOrBlank()) null else selectedFolder(treeUri)?.absolutePath

    internal fun isInSelectedFolder(path: String?, treeUri: String?): Boolean =
        isInSelectedFolder(path, null, treeUri)

    internal fun isInSelectedFolder(path: String?, relativePath: String?, treeUri: String?): Boolean {
        if (treeUri.isNullOrBlank()) return true
        val folder = selectedFolder(treeUri) ?: return false
        val target = folder.normalizedPath

        // 1. Direct absolute/physical path match
        if (!path.isNullOrBlank()) {
            val candidate = path.normalizedPath()
            if (candidate == target || candidate.startsWith("$target/")) return true
        }

        // 2. Relative path match (e.g. MediaStore.Audio.Media.RELATIVE_PATH = "Music/...")
        if (!relativePath.isNullOrBlank()) {
            val normRel = relativePath.replace('\\', '/').trim('/').lowercase(Locale.ROOT)
            val folderRel = folder.relativePath?.replace('\\', '/')?.trim('/')?.lowercase(Locale.ROOT)
            if (folderRel != null && (normRel == folderRel || normRel.startsWith("$folderRel/"))) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if a song matching the given artist and title already exists in the local library.
     */
    fun hasMatchingTrack(artist: String?, title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val cleanTitle = OnlineArtworkRepository.sanitizeTitle(title)
        val tNorm = cleanTitle.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
        if (tNorm.isEmpty()) return false

        if (matchingTitleToArtistsMap.isEmpty() && !cachedSongsMemory.isNullOrEmpty()) {
            rebuildMatchingIndex(cachedSongsMemory)
        }

        val artists = matchingTitleToArtistsMap[tNorm] ?: return false
        val cleanArtist = artist?.let { OnlineArtworkRepository.sanitizeArtist(it) }.orEmpty()
        val aNorm = cleanArtist.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)
        if (aNorm.isEmpty()) return true
        return artists.any { localA ->
            localA.isEmpty() || localA == aNorm || localA.contains(aNorm) || aNorm.contains(localA)
        }
    }

    private data class SelectedFolder(
        val absolutePath: String,
        val label: String,
        val relativePath: String? = null,
        val normalizedPath: String = absolutePath.normalizedPath(),
    )

    internal fun resolvePhysicalPath(docId: String): String? = runCatching {
        val decodedId = runCatching { java.net.URLDecoder.decode(docId, "UTF-8") }.getOrDefault(docId)
        if (decodedId.startsWith("raw:")) {
            return@runCatching decodedId.removePrefix("raw:").replace('\\', '/').trimEnd('/')
        }
        if (':' in decodedId) {
            val volume = decodedId.substringBefore(':')
            val relative = decodedId.substringAfter(':', "").trim('/')
            val volumeRoot = if (volume.equals("primary", ignoreCase = true)) {
                "/storage/emulated/0"
            } else {
                "/storage/$volume"
            }
            return@runCatching listOf(volumeRoot, relative).filter { it.isNotBlank() }.joinToString("/").replace('\\', '/').trimEnd('/')
        }
        null
    }.getOrNull()

    private fun selectedFolder(treeUri: String): SelectedFolder? = runCatching {
        val parsedUri = Uri.parse(treeUri)
        val rawDocId = runCatching { DocumentsContract.getTreeDocumentId(parsedUri) }.getOrNull()
            ?: parsedUri.lastPathSegment.orEmpty()
        val exactPath = resolvePhysicalPath(rawDocId) ?: return@runCatching null
        val decodedId = runCatching { java.net.URLDecoder.decode(rawDocId, "UTF-8") }.getOrDefault(rawDocId)
        val relative = if (decodedId.startsWith("raw:")) {
            null
        } else if (':' in decodedId) {
            decodedId.substringAfter(':', "").trim('/')
        } else {
            null
        }
        val label = if (decodedId.startsWith("raw:")) {
            exactPath.substringAfterLast('/')
        } else if (':' in decodedId) {
            val rel = decodedId.substringAfter(':', "").trim('/')
            val volume = decodedId.substringBefore(':')
            rel.ifBlank { volume }
        } else {
            exactPath.substringAfterLast('/')
        }
        SelectedFolder(
            absolutePath = exactPath,
            label = label,
            relativePath = relative,
        )
    }.getOrNull()

    private fun String.normalizedPath(): String {
        var p = replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)
        if (p.startsWith("/sdcard/")) {
            p = "/storage/emulated/0/" + p.removePrefix("/sdcard/")
        } else if (p == "/sdcard") {
            p = "/storage/emulated/0"
        } else if (p.startsWith("/storage/self/primary/")) {
            p = "/storage/emulated/0/" + p.removePrefix("/storage/self/primary/")
        }
        return p
    }

    /**
     * MediaStore's `IS_MUSIC` flag is advisory and often includes notification
     * sounds, voice notes and recorder output. Keep this second gate independent
     * of scanner metadata so the same bad rows stay out across Android vendors.
     */
    internal fun isEligibleLocalMusic(durationMs: Long, displayName: String, path: String?): Boolean {
        if (durationMs in 1 until MIN_LOCAL_MUSIC_DURATION_MS) return false

        val fileName = path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: displayName
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in localMusicExtensions) return false

        val normalizedPath = path
            ?.replace('\\', '/')
            ?.lowercase(Locale.ROOT)
            ?: "/${displayName.lowercase(Locale.ROOT)}"
        return nonMusicPathSegments.none(normalizedPath::contains)
    }

    private fun String.removeAudioExtension(): String {
        val extension = substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (extension in localMusicExtensions || extension == "wav") {
            substringBeforeLast('.').ifBlank { this }
        } else {
            this
        }
    }

    private fun isAudioFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in localMusicExtensions || ext == "3gp"
    }

    /**
     * A song built from a file in the downloads folder the app has no record of
     * — one copied in by hand, or left behind by an install whose record is
     * gone. The file's own tags are the only thing there is to go on; [scanned]
     * fills in what the retriever couldn't read, since the media scanner and
     * `MediaMetadataRetriever` do not agree on every container.
     */
    private fun buildSongFromUri(
        context: Context,
        uriStr: String,
        fileName: String,
        scanned: ScannedTags? = null,
    ): Song {
        var title = fileName.substringBeforeLast(".")
        var artist = "Unknown Artist"
        var albumName: String? = null
        var durationText: String? = null
        var artworkUrl: String? = scanned?.artworkUrl

        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uriStr))
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

                if (!metaTitle.isNullOrBlank()) title = metaTitle
                if (!metaArtist.isNullOrBlank()) artist = metaArtist
                albumName = metaAlbum.cleanTag()
                if (metaDur != null && metaDur > 0) durationText = formatDuration(metaDur)
            } finally {
                runCatching { retriever.release() }
            }
        }

        return Song(
            videoId = uriStr,
            title = title,
            artist = artist,
            thumbnailUrl = artworkUrl ?: "audio:$uriStr",
            durationText = durationText,
            albumName = albumName ?: scanned?.albumName,
            localUri = uriStr,
            localDateAddedSeconds = scanned?.dateAddedSeconds,
            localDateModifiedSeconds = scanned?.dateModifiedSeconds,
        )
    }

    private fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val minutes = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }

    val DISC_FOLDER_REGEX = Regex("""^(?:cd|disc|disk)\s*([0-9]+|[a-zA-Z]+)?.*$""", RegexOption.IGNORE_CASE)
    val DISC_SUFFIX_REGEX = Regex("""(?i)\s*(?:[-–—:]\s*)?(?:\(|\[)?\s*(?:cd|disc|disk)\s*([0-9]+|[a-zA-Z]+)\s*(?:\)|\])?""")

    fun cleanDiscSuffix(raw: String): String {
        val trimmed = raw.trim()
        val cleaned = trimmed
            .replace(Regex("""(?i)\s*[-–—:]\s*(?:cd|disc|disk)\s*[0-9a-z]+\b.*$"""), "")
            .replace(Regex("""(?i)\s*[\(\[\{]\s*(?:cd|disc|disk)\s*[0-9a-z]+\s*[\)\]\}]"""), "")
            .trim()
        return if (cleaned.isNotBlank()) cleaned else trimmed
    }

    internal data class ParsedFileMetadata(
        val title: String,
        val artist: String,
        val album: String?,
        val discNumber: Int? = null,
        val trackNumber: Int? = null,
    )

    internal fun String.cleanTrackNumber(): String {
        val trimmed = this.trim()
        val regex = Regex("""^(\d{1,3}[\.\-\s_]+|[A-Za-z]\d{1,2}[\.\-\s_]+|\d{1,2}-\d{1,2}[\.\-\s_]+)""")
        val result = trimmed.replace(regex, "").trim()
        return if (result.isNotBlank()) result else trimmed
    }

    internal fun parseMetadataFromPath(name: String, parentDir: File?): ParsedFileMetadata {
        val baseName = name.substringBeforeLast('.')
        var title = baseName
        var artist = "Unknown Artist"
        var album: String? = null
        var discNumber: Int? = null
        var trackNumber: Int? = null

        val discTrackMatch = Regex("""^(\d{1,2})-(\d{1,3})[\.\-\s_]+""").find(baseName)
        if (discTrackMatch != null) {
            discNumber = discTrackMatch.groupValues[1].toIntOrNull()
            trackNumber = discTrackMatch.groupValues[2].toIntOrNull()
        } else {
            val trackMatch = Regex("""^(\d{1,3})[\.\-\s_]+""").find(baseName)
            if (trackMatch != null) {
                trackNumber = trackMatch.groupValues[1].toIntOrNull()
            }
        }

        if (" - " in baseName) {
            val parts = baseName.split(" - ")
            val first = parts[0].trim()
            val rest = parts.drop(1).joinToString(" - ").trim()
            if (first.matches(Regex("""^\d+$""")) || first.matches(Regex("""^[A-Za-z]?\d+$"""))) {
                title = rest.ifBlank { baseName }
            } else {
                artist = first
                title = rest.cleanTrackNumber().ifBlank { rest }
            }
        } else {
            title = baseName.cleanTrackNumber().ifBlank { baseName }
        }

        if (parentDir != null && parentDir.name.isNotBlank()) {
            val pName = parentDir.name.trim()
            if (pName != "Music" && pName != "Download" && pName != "0" && pName != "emulated") {
                val isDiscDir = DISC_FOLDER_REGEX.matches(pName)
                if (isDiscDir) {
                    val match = DISC_FOLDER_REGEX.find(pName)
                    val dg = match?.groupValues?.getOrNull(1)
                    if (discNumber == null) {
                        discNumber = dg?.toIntOrNull() ?: dg?.firstOrNull()?.let { ch ->
                            if (ch.isLetter()) (ch.uppercaseChar() - 'A' + 1) else null
                        }
                    }
                    val gp = parentDir.parentFile
                    if (gp != null && gp.name.isNotBlank() &&
                        gp.name != "Music" && gp.name != "Download" && gp.name != "0" && gp.name != "emulated"
                    ) {
                        album = cleanDiscSuffix(gp.name)
                        val ggp = gp.parentFile
                        if (ggp != null && ggp.name.isNotBlank() &&
                            ggp.name != "Music" && ggp.name != "Download" && ggp.name != "0" && ggp.name != "emulated"
                        ) {
                            if (artist == "Unknown Artist") {
                                artist = ggp.name
                            }
                        }
                    }
                } else {
                    album = cleanDiscSuffix(pName)
                    val gp = parentDir.parentFile
                    if (gp != null && gp.name.isNotBlank() &&
                        gp.name != "Music" && gp.name != "Download" && gp.name != "0" && gp.name != "emulated"
                    ) {
                        if (artist == "Unknown Artist") {
                            artist = gp.name
                        }
                    }
                }
            }
        }

        if (album != null) {
            val discMatch = DISC_SUFFIX_REGEX.find(album)
            if (discMatch != null) {
                if (discNumber == null) {
                    val dg = discMatch.groupValues.getOrNull(1)
                    discNumber = dg?.toIntOrNull() ?: dg?.firstOrNull()?.let { ch ->
                        if (ch.isLetter()) (ch.uppercaseChar() - 'A' + 1) else null
                    }
                }
                album = cleanDiscSuffix(album)
            }
        }

        return ParsedFileMetadata(
            title = title.cleanTrackNumber().ifBlank { baseName },
            artist = artist,
            album = album,
            discNumber = discNumber,
            trackNumber = trackNumber,
        )
    }

    private fun buildSongFromFile(file: File): Song? {
        val path = file.absolutePath
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in localMusicExtensions) return null

        val parent = file.parentFile
        val parsed = parseMetadataFromPath(name, parent)

        var title = parsed.title
        var artist = parsed.artist
        var album = parsed.album
        var discNumber = parsed.discNumber
        var trackNumber = parsed.trackNumber
        var durationMs = 0L

        runCatching {
            val mr = MediaMetadataRetriever()
            mr.setDataSource(path)
            durationMs = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val metaTitle = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.cleanTag()?.removeAudioExtension()
            val metaArtist = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.cleanTag()
            val metaAlbum = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.cleanTag()
            val metaDisc = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            val metaTrack = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank() && metaArtist != "<unknown>") artist = metaArtist
            if (!metaAlbum.isNullOrBlank() && metaAlbum != "<unknown>") album = cleanDiscSuffix(metaAlbum)
            if (metaDisc != null && metaDisc > 0) discNumber = metaDisc
            if (metaTrack != null && metaTrack > 0) trackNumber = metaTrack

            mr.release()
        }

        if (AppSettings.filterNonMusicAudio.value && !isEligibleLocalMusic(durationMs, name, path)) {
            return null
        }

        val isDiscDir = parent != null && DISC_FOLDER_REGEX.matches(parent.name.trim())
        val searchDirs = if (isDiscDir && parent?.parentFile != null) {
            listOfNotNull(parent, parent.parentFile)
        } else {
            listOfNotNull(parent)
        }
        var folderArt: String? = null
        for (dir in searchDirs) {
            if (dir.exists()) {
                val found = File(dir, "cover.jpg").takeIf { it.exists() }
                    ?: File(dir, "folder.jpg").takeIf { it.exists() }
                    ?: File(dir, "Cover.jpg").takeIf { it.exists() }
                    ?: File(dir, "Folder.jpg").takeIf { it.exists() }
                    ?: File(dir, "cover.png").takeIf { it.exists() }
                    ?: File(dir, "folder.png").takeIf { it.exists() }
                if (found != null) {
                    folderArt = Uri.fromFile(found).toString()
                    break
                }
            }
        }

        val modified = file.lastModified().takeIf { it > 0 }?.div(1000)
        val fileUri = Uri.fromFile(file).toString()

        return Song(
            videoId = fileUri,
            title = title,
            artist = artist,
            thumbnailUrl = folderArt ?: "audio:$fileUri",
            durationText = durationMs.takeIf { it > 0 }?.let(::formatDuration),
            albumName = album,
            localUri = fileUri,
            localPath = path,
            localDateAddedSeconds = modified,
            localDateModifiedSeconds = modified,
            discNumber = discNumber,
            trackNumber = trackNumber,
        )
    }

    private fun buildSongFromSaf(
        context: Context,
        uri: Uri,
        name: String,
        lastModifiedMs: Long,
        folderArtUri: String? = null,
        physicalPath: String? = null,
        filterNonMusic: Boolean = false,
    ): Song? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in localMusicExtensions) return null

        val parentFile = physicalPath?.let { File(it).parentFile }
        val parsed = parseMetadataFromPath(name, parentFile)

        var title = parsed.title
        var artist = parsed.artist
        var album = parsed.album
        var discNumber = parsed.discNumber
        var trackNumber = parsed.trackNumber
        var durationMs = 0L

        runCatching {
            val mr = MediaMetadataRetriever()
            if (physicalPath != null && File(physicalPath).exists()) {
                mr.setDataSource(physicalPath)
            } else {
                mr.setDataSource(context, uri)
            }
            durationMs = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val metaTitle = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.cleanTag()?.removeAudioExtension()
            val metaArtist = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.cleanTag()
            val metaAlbum = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.cleanTag()
            val metaDisc = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            val metaTrack = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank() && metaArtist != "<unknown>") artist = metaArtist
            if (!metaAlbum.isNullOrBlank() && metaAlbum != "<unknown>") album = cleanDiscSuffix(metaAlbum)
            if (metaDisc != null && metaDisc > 0) discNumber = metaDisc
            if (metaTrack != null && metaTrack > 0) trackNumber = metaTrack

            mr.release()
        }

        if (filterNonMusic && !isEligibleLocalMusic(durationMs, name, physicalPath)) {
            return null
        }

        val modified = lastModifiedMs.takeIf { it > 0 }?.div(1000)
        val uriStr = uri.toString()

        return Song(
            videoId = uriStr,
            title = title,
            artist = artist,
            thumbnailUrl = folderArtUri ?: "audio:$uriStr",
            durationText = durationMs.takeIf { it > 0 }?.let(::formatDuration),
            albumName = album,
            localUri = uriStr,
            localPath = physicalPath,
            localDateAddedSeconds = modified,
            localDateModifiedSeconds = modified,
            discNumber = discNumber,
            trackNumber = trackNumber,
        )
    }
}
