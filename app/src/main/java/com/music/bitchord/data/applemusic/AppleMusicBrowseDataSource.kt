package com.music.bitchord.data.applemusic

/** Page callbacks contain accumulated immutable snapshots, not just the newest page. */
interface AppleMusicBrowseDataSource {
    suspend fun playlists(onPage: (List<AppleMusicPlaylist>) -> Unit): List<AppleMusicPlaylist>
    suspend fun songs(onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack>
    suspend fun albums(onPage: (List<AppleMusicAlbum>) -> Unit): List<AppleMusicAlbum>
    suspend fun artists(onPage: (List<AppleMusicArtist>) -> Unit): List<AppleMusicArtist>
    suspend fun artistAlbums(id: String, onPage: (List<AppleMusicAlbum>) -> Unit): List<AppleMusicAlbum>
    suspend fun recentlyAdded(onPage: (List<AppleMusicLibraryItem>) -> Unit): List<AppleMusicLibraryItem>
    suspend fun playlistTracks(id: String, onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack>
    suspend fun albumTracks(id: String, onPage: (List<AppleMusicTrack>) -> Unit): List<AppleMusicTrack>
    suspend fun searchSongs(storefront: String, query: String): List<AppleMusicTrack>
    suspend fun catalogPlaylist(storefront: String, id: String): AppleMusicPlaylist?
    suspend fun catalogPlaylistTracks(
        storefront: String,
        id: String,
        onPage: (List<AppleMusicTrack>) -> Unit,
    ): List<AppleMusicTrack>
}

object AppleMusicRestBrowseDataSource : AppleMusicBrowseDataSource {
    override suspend fun playlists(onPage: (List<AppleMusicPlaylist>) -> Unit) = AppleMusicLibrary.playlists(onPage)
    override suspend fun songs(onPage: (List<AppleMusicTrack>) -> Unit) = AppleMusicLibrary.songs(onPage)
    override suspend fun albums(onPage: (List<AppleMusicAlbum>) -> Unit) = AppleMusicLibrary.albums(onPage)
    override suspend fun artists(onPage: (List<AppleMusicArtist>) -> Unit) = AppleMusicLibrary.artists(onPage)
    override suspend fun artistAlbums(id: String, onPage: (List<AppleMusicAlbum>) -> Unit) = AppleMusicLibrary.artistAlbums(id, onPage)
    override suspend fun recentlyAdded(onPage: (List<AppleMusicLibraryItem>) -> Unit) = AppleMusicLibrary.recentlyAdded(onPage)
    override suspend fun playlistTracks(id: String, onPage: (List<AppleMusicTrack>) -> Unit) = AppleMusicLibrary.tracks(id, onPage)
    override suspend fun albumTracks(id: String, onPage: (List<AppleMusicTrack>) -> Unit) = AppleMusicLibrary.albumTracks(id, onPage)
    override suspend fun searchSongs(storefront: String, query: String) = AppleMusicLibrary.searchSongs(storefront, query)
    override suspend fun catalogPlaylist(storefront: String, id: String) = AppleMusicLibrary.catalogPlaylist(storefront, id)
    override suspend fun catalogPlaylistTracks(storefront: String, id: String, onPage: (List<AppleMusicTrack>) -> Unit) =
        AppleMusicLibrary.catalogPlaylistTracks(storefront, id, onPage)
}

/**
 * A trusted adapter may return the id of a real playlist it has independently verified
 * as Apple's Favorite Songs collection. The controller also requires that id to occur
 * in the fetched library. A localized name, isFavorite, or personal-mix is NOT proof.
 * REST metadata currently supplies no verified discriminator, so the default is unavailable.
 */
fun interface AppleMusicFavoriteSongsVerifier {
    suspend fun verifiedPlaylistId(playlists: List<AppleMusicPlaylist>): String?
}
