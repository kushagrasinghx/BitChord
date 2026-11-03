package com.music.bitchord.data.applemusic

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.emptyFlow

internal fun libraryTrack(id: String = "i.saved", catalogId: String? = "123") = AppleMusicTrack(
    id = id,
    catalogId = catalogId,
    title = "Northern Lights",
    artist = "The Example",
    album = "An Album",
    durationMs = 180_123,
    imageUrl = "https://image.example/art.jpg",
    isrc = "USABC1200001",
    explicit = true,
)

internal fun youtubeSong(id: String = "yt-song-001") = Song(
    videoId = id, title = "Northern Lights", artist = "The Example", thumbnailUrl = null,
)

internal class AppleMusicFakeAccount {
    var key: String? = "account-a"
    var storefront: String? = "us"
    val session = AppleMusicLibrarySession(
        accountKey = { key }, userStorefront = { storefront }, connectionChanges = emptyFlow(),
    )
}

internal class AppleMusicBrowseFake : AppleMusicBrowseDataSource {
    val calls = mutableListOf<String>()
    var playlistItems = listOf(AppleMusicPlaylist("p.real", "Road Trip"))
    var albumItems = listOf(AppleMusicAlbum("l.album", "Album", "Artist"))
    var artistItems = listOf(AppleMusicArtist("r.artist", "Artist"))
    var recentItems: List<AppleMusicLibraryItem> = listOf(playlistItems.single(), albumItems.single())
    var trackItems = listOf(libraryTrack())
    var catalogItems = listOf(libraryTrack("123", "123"))
    var catalogPlaylistItem: AppleMusicPlaylist? = AppleMusicPlaylist("pl.real", "Shared Playlist", catalogId = "pl.real")
    var playlistsRequest: suspend ((List<AppleMusicPlaylist>) -> Unit) -> List<AppleMusicPlaylist> = { onPage ->
        playlistItems.also(onPage)
    }
    var songsRequest: suspend ((List<AppleMusicTrack>) -> Unit) -> List<AppleMusicTrack> = { onPage ->
        trackItems.also(onPage)
    }
    var playlistTracksRequest: suspend ((List<AppleMusicTrack>) -> Unit) -> List<AppleMusicTrack> = { onPage ->
        trackItems.also(onPage)
    }

    override suspend fun playlists(onPage: (List<AppleMusicPlaylist>) -> Unit): List<AppleMusicPlaylist> {
        calls += "playlists"
        return playlistsRequest(onPage)
    }
    override suspend fun songs(onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack> {
        calls += "songs"
        return songsRequest(onPage)
    }
    override suspend fun albums(onPage: (List<AppleMusicAlbum>) -> Unit): List<AppleMusicAlbum> {
        calls += "albums"
        return albumItems.also(onPage)
    }
    override suspend fun artists(onPage: (List<AppleMusicArtist>) -> Unit): List<AppleMusicArtist> {
        calls += "artists"
        return artistItems.also(onPage)
    }
    override suspend fun artistAlbums(id: String, onPage: (List<AppleMusicAlbum>) -> Unit): List<AppleMusicAlbum> {
        calls += "artist:$id"
        return albumItems.also(onPage)
    }
    override suspend fun recentlyAdded(onPage: (List<AppleMusicLibraryItem>) -> Unit): List<AppleMusicLibraryItem> {
        calls += "recent"
        return recentItems.also(onPage)
    }
    override suspend fun playlistTracks(id: String, onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack> {
        calls += "playlist:$id"
        return playlistTracksRequest(onPage)
    }
    override suspend fun albumTracks(id: String, onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack> {
        calls += "album:$id"
        return trackItems.also(onPage)
    }
    override suspend fun searchSongs(storefront: String, query: String): List<AppleMusicTrack> {
        calls += "search:$storefront:$query"
        return catalogItems
    }
    override suspend fun catalogPlaylist(storefront: String, id: String): AppleMusicPlaylist? {
        calls += "catalog:$storefront:$id"
        return catalogPlaylistItem
    }
    override suspend fun catalogPlaylistTracks(storefront: String, id: String, onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack> {
        calls += "catalog-tracks:$storefront:$id"
        return catalogItems.also(onPage)
    }
}
