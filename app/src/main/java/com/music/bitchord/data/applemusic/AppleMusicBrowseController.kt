package com.music.bitchord.data.applemusic

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface AppleMusicBrowseSelection {
    data object Playlists : AppleMusicBrowseSelection
    data object Songs : AppleMusicBrowseSelection
    data object Albums : AppleMusicBrowseSelection
    data object Artists : AppleMusicBrowseSelection
    data object RecentlyAdded : AppleMusicBrowseSelection
    data object FavoriteSongs : AppleMusicBrowseSelection
    data class PlaylistTracks(val playlist: AppleMusicPlaylist) : AppleMusicBrowseSelection
    data class AlbumTracks(val album: AppleMusicAlbum) : AppleMusicBrowseSelection
    data class ArtistAlbums(val artist: AppleMusicArtist) : AppleMusicBrowseSelection
    data class Search(val query: String, val storefront: String? = null) : AppleMusicBrowseSelection
}

enum class AppleMusicBrowseError { NOT_CONNECTED, STOREFRONT_REQUIRED, FAVORITES_UNAVAILABLE, LOAD_FAILED }

data class AppleMusicBrowseState(
    val account: AppleMusicLibrarySession.State,
    val selection: AppleMusicBrowseSelection = AppleMusicBrowseSelection.Playlists,
    val items: List<AppleMusicLibraryItem> = emptyList(),
    val tracks: List<AppleMusicTrackMapping> = emptyList(),
    val isLoading: Boolean = false,
    val isMatching: Boolean = false,
    val favoriteSongsPlaylist: AppleMusicPlaylist? = null,
    val error: AppleMusicBrowseError? = null,
) {
    val matchedCount: Int get() = tracks.count { it.status == AppleMusicTrackMatchStatus.YOUTUBE }
    val unmatchedCount: Int get() = tracks.count {
        it.status == AppleMusicTrackMatchStatus.APPLE_ONLY || it.status == AppleMusicTrackMatchStatus.UNAVAILABLE
    }
}

/**
 * Phone presentation state with no Android/UI dependency. Launch [load], [select], and
 * [search] in the caller's scope; a new selection cancels the previous one. Close when
 * its owner is disposed. Partial pages and resolved rows are published progressively.
 * Cancellation propagates, errors are redacted enum values, and retries rematch misses.
 */
class AppleMusicBrowseController(
    private val session: AppleMusicLibrarySession,
    private val mapper: AppleMusicTrackMapper,
    private val source: AppleMusicBrowseDataSource = AppleMusicRestBrowseDataSource,
    private val favorites: AppleMusicFavoriteSongsVerifier = AppleMusicFavoriteSongsVerifier { null },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val mutableState = MutableStateFlow(AppleMusicBrowseState(session.refresh()))
    val state = mutableState.asStateFlow()
    private val requests = AppleMusicLibraryRequests(session)
    private val accountListener = session.listen { account ->
        requests.cancel()
        mutableState.value = AppleMusicBrowseState(account)
    }

    suspend fun select(playlist: AppleMusicPlaylist) = load(AppleMusicBrowseSelection.PlaylistTracks(playlist))
    suspend fun select(album: AppleMusicAlbum) = load(AppleMusicBrowseSelection.AlbumTracks(album))
    suspend fun search(query: String, storefront: String? = null) = load(AppleMusicBrowseSelection.Search(query, storefront))

    suspend fun load(selection: AppleMusicBrowseSelection = state.value.selection) = withContext(dispatcher) {
        requests.run(
            onStart = { account ->
                mutableState.value = AppleMusicBrowseState(account, selection, isLoading = true)
            },
            onCancelled = {
                mutableState.value = mutableState.value.copy(isLoading = false, isMatching = false)
            },
        ) { ticket ->
            try {
                if (selection !is AppleMusicBrowseSelection.Search && !ticket.account.connected) {
                    ticket.publish {
                        mutableState.value = mutableState.value.copy(isLoading = false, error = AppleMusicBrowseError.NOT_CONNECTED)
                    }
                } else {
                    loadSelection(selection, ticket)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ticket.publish {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false, isMatching = false, error = AppleMusicBrowseError.LOAD_FAILED,
                    )
                }
            }
        }
    }

    private suspend fun loadSelection(selection: AppleMusicBrowseSelection, ticket: AppleMusicLibraryRequests.Ticket) {
        when (selection) {
            AppleMusicBrowseSelection.Playlists -> {
                val playlists = source.playlists { publishItems(it, ticket) }
                publishItems(playlists, ticket)
                val verified = verifiedFavorites(playlists, ticket)
                ticket.publish { mutableState.value = mutableState.value.copy(favoriteSongsPlaylist = verified) }
            }
            AppleMusicBrowseSelection.Albums -> publishItems(source.albums { publishItems(it, ticket) }, ticket)
            AppleMusicBrowseSelection.Artists -> publishItems(source.artists { publishItems(it, ticket) }, ticket)
            is AppleMusicBrowseSelection.ArtistAlbums -> publishItems(
                source.artistAlbums(selection.artist.id) { publishItems(it, ticket) }, ticket,
            )
            AppleMusicBrowseSelection.RecentlyAdded -> publishItems(source.recentlyAdded { publishItems(it, ticket) }, ticket)
            AppleMusicBrowseSelection.Songs -> loadTracks(ticket) { onPage -> source.songs(onPage) }
            is AppleMusicBrowseSelection.PlaylistTracks -> loadTracks(ticket) { onPage ->
                require(selection.playlist.id.isNotBlank())
                source.playlistTracks(selection.playlist.id, onPage)
            }
            is AppleMusicBrowseSelection.AlbumTracks -> loadTracks(ticket) { onPage ->
                require(selection.album.id.isNotBlank())
                source.albumTracks(selection.album.id, onPage)
            }
            AppleMusicBrowseSelection.FavoriteSongs -> {
                val playlists = source.playlists { ticket.check() }
                val verified = verifiedFavorites(playlists, ticket)
                ticket.publish {
                    mutableState.value = mutableState.value.copy(
                        favoriteSongsPlaylist = verified,
                        error = if (verified == null) AppleMusicBrowseError.FAVORITES_UNAVAILABLE else null,
                    )
                }
                if (verified != null) loadTracks(ticket) { onPage -> source.playlistTracks(verified.id, onPage) }
                else publishItems(playlists, ticket) // Let the user select their actual collection explicitly.
            }
            is AppleMusicBrowseSelection.Search -> {
                val storefront = (selection.storefront ?: ticket.account.storefront)?.trim()?.lowercase()
                    ?.takeIf { it.matches(Regex("[a-z]{2}")) }
                if (storefront == null) {
                    ticket.publish { mutableState.value = mutableState.value.copy(error = AppleMusicBrowseError.STOREFRONT_REQUIRED) }
                } else if (selection.query.isNotBlank()) {
                    loadTracks(ticket, AppleMusicTrackOrigin.CATALOG, storefront) { onPage ->
                        source.searchSongs(storefront, selection.query.trim()).also(onPage)
                    }
                }
            }
        }
        ticket.publish { mutableState.value = mutableState.value.copy(isLoading = false, isMatching = false) }
    }

    private suspend fun verifiedFavorites(
        playlists: List<AppleMusicPlaylist>,
        ticket: AppleMusicLibraryRequests.Ticket,
    ): AppleMusicPlaylist? {
        ticket.check()
        val id = favorites.verifiedPlaylistId(playlists.toList())
        ticket.check()
        return playlists.singleOrNull { it.id == id }
    }

    private fun publishItems(items: List<AppleMusicLibraryItem>, ticket: AppleMusicLibraryRequests.Ticket) {
        ticket.publish { mutableState.value = mutableState.value.copy(items = items.toList()) }
    }

    private suspend fun loadTracks(
        ticket: AppleMusicLibraryRequests.Ticket,
        origin: AppleMusicTrackOrigin = AppleMusicTrackOrigin.LIBRARY,
        storefront: String? = ticket.account.storefront,
        fetch: suspend ((List<AppleMusicTrack>) -> Unit) -> List<AppleMusicTrack>,
    ) {
        fun pending(tracks: List<AppleMusicTrack>) = ticket.publish {
            mutableState.value = mutableState.value.copy(tracks = tracks.map { mapper.pending(it, origin) })
        }
        val tracks = fetch(::pending)
        ticket.check()
        pending(tracks)
        ticket.publish { mutableState.value = mutableState.value.copy(isLoading = false, isMatching = tracks.isNotEmpty()) }
        mapper.matchAll(tracks, origin, storefront, ticket::check) { index, row ->
            ticket.publish {
                mutableState.value = mutableState.value.copy(
                    tracks = mutableState.value.tracks.toMutableList().also { it[index] = row },
                )
            }
        }
    }

    fun cancel() = session.locked {
        requests.cancel()
        mutableState.value = mutableState.value.copy(isLoading = false, isMatching = false)
    }

    override fun close() {
        accountListener.close()
        requests.close()
    }
}
