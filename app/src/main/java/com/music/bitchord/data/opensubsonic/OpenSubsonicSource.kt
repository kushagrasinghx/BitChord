package com.music.bitchord.data.opensubsonic

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.MusicSource
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.StreamRequest
import java.util.Locale
import kotlinx.coroutines.async

private const val TAG = "BitChord"

class OpenSubsonicSource(
    override val config: SourceConfig
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.OPENSUBSONIC
    override val displayName: String get() = config.displayName

    override suspend fun health(): SourceHealth {
        val ok = OpenSubsonicService.ping(config.baseUrl, config.username, config.password)
        return if (ok) SourceHealth.Ok() else SourceHealth.Unreachable("Failed to connect to OpenSubsonic server")
    }

    override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<Song> {
        TrackLog.d(TAG, "▶ OpenSubsonic search() query=\"$query\" limit=$limit")
        val results = runCatching { OpenSubsonicService.search(config.baseUrl, config.username, config.password, query, limit) }.getOrDefault(emptyList())
        TrackLog.d(TAG, "  ✓ OpenSubsonic returned ${results.size} tracks")
        
        return results.map { raw ->
            val thumbnail = raw.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) }
            val m = raw.durationSeconds / 60
            val s = raw.durationSeconds % 60
            
            Song(
                videoId = SourceRegistry.trackKey(config.id, raw.id),
                title = raw.title,
                artist = raw.artist,
                albumName = raw.album,
                thumbnailUrl = thumbnail,
                durationText = String.format(Locale.ROOT, "%d:%02d", m, s),
                sourceQuality = if (raw.isLossless) "LOSSLESS" else "HIGH",
            )
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        TrackLog.d(TAG, "▶ OpenSubsonic stream() trackId=$trackId request=$request")
        
        // Construct stream URL
        // Subsonic by default streams at max bitrate or transcodes if maxBitRate is set.
        // BitChord requests different tiers.
        var url = OpenSubsonicService.getStreamUrl(config.baseUrl, config.username, config.password, trackId)
        
        if (request is StreamRequest.Capped) {
            // Capped means we request lower bitrate
            url += "&maxBitRate=${request.maxKbps}"
        }

        TrackLog.d(TAG, "  ✓ OpenSubsonic provided stream URL")
        return SourceStream(
            url = url,
            format = StreamFormat(), // Unknown until decoder gets it
        )
    }

    override suspend fun homeFeed(): List<com.music.bitchord.data.model.HomeShelf> = kotlinx.coroutines.coroutineScope {
        val shelves = mutableListOf<com.music.bitchord.data.model.HomeShelf>()
        
        val randomSongsJob = async { OpenSubsonicService.getRandomSongs(config.baseUrl, config.username, config.password, 10) }
        val frequentAlbumsJob = async { OpenSubsonicService.getAlbumList2(config.baseUrl, config.username, config.password, "frequent", 15) }
        val recentAlbumsJob = async { OpenSubsonicService.getAlbumList2(config.baseUrl, config.username, config.password, "recent", 15) }
        val newestAlbumsJob = async { OpenSubsonicService.getAlbumList2(config.baseUrl, config.username, config.password, "newest", 15) }
        val playlistsJob = async { OpenSubsonicService.getPlaylists(config.baseUrl, config.username, config.password) }
        val allAlbumsJob = async { OpenSubsonicService.getAlbumList2(config.baseUrl, config.username, config.password, "alphabeticalByName", 25) }

        // Your top songs (using random for now)
        val randomSongs = randomSongsJob.await()
        if (randomSongs.isNotEmpty()) {
            val items = randomSongs.map { song ->
                com.music.bitchord.data.model.ShelfItem(
                    title = song.title,
                    subtitle = song.artist,
                    thumbnailUrl = song.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) },
                    videoId = SourceRegistry.trackKey(config.id, song.id),
                    browseId = null
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = "Discovery", subtitle = "Your top songs", items = items))
        }

        // Most played
        val frequentAlbums = frequentAlbumsJob.await()
        if (frequentAlbums.isNotEmpty()) {
            val items = frequentAlbums.map { album ->
                com.music.bitchord.data.model.ShelfItem(
                    title = album.name,
                    subtitle = album.artist,
                    thumbnailUrl = album.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) },
                    videoId = null,
                    browseId = "bitchord://source?s=${config.id}&a=${album.id}"
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = "Most played", subtitle = "Albums", items = items))
        }

        // Last played
        val recentAlbums = recentAlbumsJob.await()
        if (recentAlbums.isNotEmpty()) {
            val items = recentAlbums.map { album ->
                com.music.bitchord.data.model.ShelfItem(
                    title = album.name,
                    subtitle = album.artist,
                    thumbnailUrl = album.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) },
                    videoId = null,
                    browseId = "bitchord://source?s=${config.id}&a=${album.id}"
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = "Last played", subtitle = "Albums", items = items))
        }

        // Recently added
        val newestAlbums = newestAlbumsJob.await()
        if (newestAlbums.isNotEmpty()) {
            val items = newestAlbums.map { album ->
                com.music.bitchord.data.model.ShelfItem(
                    title = album.name,
                    subtitle = album.artist,
                    thumbnailUrl = album.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) },
                    videoId = null,
                    browseId = "bitchord://source?s=${config.id}&a=${album.id}"
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = "Recently added", subtitle = "Albums", items = items))
        }

        // Playlists
        val playlists = playlistsJob.await()
        if (playlists.isNotEmpty()) {
            val items = playlists.map { playlist ->
                com.music.bitchord.data.model.ShelfItem(
                    title = playlist.name,
                    subtitle = playlist.comment,
                    thumbnailUrl = playlist.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) },
                    videoId = null,
                    browseId = "bitchord://source?s=${config.id}&p=${playlist.id}"
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = "Playlists", subtitle = "From your server", items = items))
        }

        // All Albums
        val allAlbums = allAlbumsJob.await()
        if (allAlbums.isNotEmpty()) {
            val items = allAlbums.map { album ->
                com.music.bitchord.data.model.ShelfItem(
                    title = album.name,
                    subtitle = album.artist,
                    thumbnailUrl = album.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) },
                    videoId = null,
                    browseId = "bitchord://source?s=${config.id}&a=${album.id}"
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = "All Albums", subtitle = "A-Z", items = items))
        }

        shelves
    }

    override suspend fun albumDetails(browseId: String): List<Song> {
        val tracks = OpenSubsonicService.getAlbum(config.baseUrl, config.username, config.password, browseId)
        return tracks.map { raw ->
            val thumbnail = raw.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) }
            val m = raw.durationSeconds / 60
            val s = raw.durationSeconds % 60
            
            Song(
                videoId = SourceRegistry.trackKey(config.id, raw.id),
                title = raw.title,
                artist = raw.artist,
                albumName = raw.album,
                thumbnailUrl = thumbnail,
                durationText = String.format(Locale.ROOT, "%d:%02d", m, s),
                sourceQuality = if (raw.isLossless) "LOSSLESS" else "HIGH",
            )
        }
    }
    override suspend fun playlistDetails(browseId: String): List<Song> {
        val tracks = OpenSubsonicService.getPlaylist(config.baseUrl, config.username, config.password, browseId)
        return tracks.map { raw ->
            val thumbnail = raw.coverArtId?.let { OpenSubsonicService.getCoverArtUrl(config.baseUrl, config.username, config.password, it) }
            val m = raw.durationSeconds / 60
            val s = raw.durationSeconds % 60
            
            Song(
                videoId = SourceRegistry.trackKey(config.id, raw.id),
                title = raw.title,
                artist = raw.artist,
                albumName = raw.album,
                thumbnailUrl = thumbnail,
                durationText = String.format(Locale.ROOT, "%d:%02d", m, s),
                sourceQuality = if (raw.isLossless) "LOSSLESS" else "HIGH",
            )
        }
    }
}
