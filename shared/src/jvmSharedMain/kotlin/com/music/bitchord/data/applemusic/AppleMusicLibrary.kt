package com.music.bitchord.data.applemusic

import com.music.bitchord.data.Http

/** A library playlist page id: this prefix followed by its actual Apple library id. */
const val APPLE_MUSIC_PAGE_PREFIX = "applemusic:playlist:"

/** Recently added can contain both albums and playlists; unknown resource types are skipped. */
sealed interface AppleMusicLibraryItem {
    val id: String
}

data class AppleMusicTrack(
    override val id: String,
    /** Null for uploads/unavailable library songs without an explicit catalog mapping. */
    val catalogId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val durationMs: Int = 0,
    val imageUrl: String? = null,
    val isrc: String? = null,
    val explicit: Boolean? = null,
    val isVideo: Boolean = false,
) : AppleMusicLibraryItem

data class AppleMusicPlaylist(
    override val id: String,
    val name: String = "",
    val owner: String? = null,
    val imageUrl: String? = null,
    val catalogId: String? = null,
    val description: String? = null,
    val canEdit: Boolean = false,
    /** Optional server metadata, not an assertion that this is the Favorite Songs collection. */
    val isFavorite: Boolean? = null,
    val playlistType: String? = null,
) : AppleMusicLibraryItem

data class AppleMusicAlbum(
    override val id: String,
    val name: String = "",
    val artist: String = "",
    val imageUrl: String? = null,
    val catalogId: String? = null,
    val trackCount: Int = 0,
    val releaseDate: String? = null,
) : AppleMusicLibraryItem

data class AppleMusicArtist(
    override val id: String,
    val name: String = "",
    val imageUrl: String? = null,
    val catalogId: String? = null,
) : AppleMusicLibraryItem

/** Plain REST metadata only. Applications supply auth; this layer does not own MusicKit playback. */
object AppleMusicLibrary {
    interface Auth {
        suspend fun developerToken(): String?
        suspend fun userToken(): String?
    }

    /** Installed by the application. Catalog requests need only [Auth.developerToken]. */
    @Volatile
    var auth: Auth? = null

    private val rest by lazy { AppleMusicRestClient(Http.client, { auth }) }

    /** Only song resources, never the music-video rows Apple can include in mixed results. */
    suspend fun searchSongs(storefront: String, query: String): List<AppleMusicTrack> = parseSearchSongsPage(rest.get(
        appleMusicUrl("catalog", storefront, "search").newBuilder()
            .addQueryParameter("term", query)
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "25")
            .build(),
    )).items

    suspend fun songsByIsrc(storefront: String, isrc: String): List<AppleMusicTrack> = rest.pages(
        appleMusicUrl("catalog", storefront, "songs").newBuilder()
            .addQueryParameter("filter[isrc]", isrc)
            .addQueryParameter("limit", "25")
            .build(),
        { parseTrackPage(it, songsOnly = true) },
    )

    /** The signed-in user's storefront, not a guessed device locale or a hardcoded fallback. */
    suspend fun storefront(): String = parseStorefront(rest.get(appleMusicUrl("me", "storefront")))

    /** All library playlists, including any real Favorite Songs playlist returned by Apple. */
    suspend fun playlists(
        onPage: (List<AppleMusicPlaylist>) -> Unit = {},
    ): List<AppleMusicPlaylist> = rest.pages(
        libraryUrl("playlists"), ::parsePlaylistPage, onPage,
    )

    /** Library ids are required here. Use [catalogPlaylistTracks] for shared catalog links. */
    suspend fun tracks(
        playlistId: String,
        onPage: (List<AppleMusicTrack>) -> Unit = {},
    ): List<AppleMusicTrack> = rest.pages(
        libraryUrl("playlists", playlistId, "tracks"), ::parseTrackPage, onPage,
    )

    suspend fun songs(
        onPage: (List<AppleMusicTrack>) -> Unit = {},
    ): List<AppleMusicTrack> = rest.pages(
        libraryUrl("songs"), { parseTrackPage(it, songsOnly = true) }, onPage,
    )

    suspend fun albums(
        onPage: (List<AppleMusicAlbum>) -> Unit = {},
    ): List<AppleMusicAlbum> = rest.pages(
        libraryUrl("albums"), ::parseAlbumPage, onPage,
    )

    suspend fun albumTracks(
        albumId: String,
        onPage: (List<AppleMusicTrack>) -> Unit = {},
    ): List<AppleMusicTrack> = rest.pages(
        libraryUrl("albums", albumId, "tracks"), ::parseTrackPage, onPage,
    )

    suspend fun recentlyAdded(
        onPage: (List<AppleMusicLibraryItem>) -> Unit = {},
    ): List<AppleMusicLibraryItem> = rest.pages(
        libraryUrl("recently-added", limit = 25), ::parseRecentlyAddedPage, onPage,
    )

    suspend fun artists(
        onPage: (List<AppleMusicArtist>) -> Unit = {},
    ): List<AppleMusicArtist> = rest.pages(
        libraryUrl("artists"), ::parseArtistPage, onPage,
    )

    suspend fun artistAlbums(
        artistId: String,
        onPage: (List<AppleMusicAlbum>) -> Unit = {},
    ): List<AppleMusicAlbum> = rest.pages(
        libraryUrl("artists", artistId, "albums"), ::parseAlbumPage, onPage,
    )

    /** Metadata for a catalog playlist id extracted from a music.apple.com link. */
    suspend fun catalogPlaylist(storefront: String, playlistId: String): AppleMusicPlaylist? =
        parsePlaylistPage(rest.get(appleMusicUrl("catalog", storefront, "playlists", playlistId)))
            .items.firstOrNull()

    suspend fun catalogPlaylistTracks(
        storefront: String,
        playlistId: String,
        onPage: (List<AppleMusicTrack>) -> Unit = {},
    ): List<AppleMusicTrack> = rest.pages(
        appleMusicUrl("catalog", storefront, "playlists", playlistId, "tracks").newBuilder()
            .addQueryParameter("limit", "100")
            .build(),
        ::parseTrackPage,
        onPage,
    )

    private fun libraryUrl(vararg segments: String, limit: Int = 100) =
        appleMusicUrl("me", "library", *segments).newBuilder()
            .addQueryParameter("limit", limit.toString())
            .build()
}
