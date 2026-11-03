package com.music.bitchord.data.applemusic

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import java.io.Closeable
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A parsed identifier only. The supplied URL is never fetched or followed. */
data class AppleMusicPlaylistLink(val storefront: String, val playlistId: String) {
    companion object {
        fun parse(input: String): AppleMusicPlaylistLink? {
            val uri = try { URI(input.trim()) } catch (_: Exception) { return null }
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                !uri.host.equals("music.apple.com", ignoreCase = true) ||
                uri.rawUserInfo != null || uri.port !in setOf(-1, 443) || uri.rawFragment != null
            ) return null
            val segments = uri.rawPath.orEmpty().split('/').drop(1)
            if (segments.size !in 3..4 || segments.any { it.isBlank() }) return null
            val storefront = segments[0].lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z]{2}")) } ?: return null
            if (segments[1] != "playlist") return null
            val id = segments.last().takeIf { it.matches(Regex("pl\\.[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) } ?: return null
            return AppleMusicPlaylistLink(storefront, id)
        }
    }
}

sealed interface AppleMusicImportSource {
    data class LibraryPlaylist(val playlist: AppleMusicPlaylist) : AppleMusicImportSource
    data object LibrarySongs : AppleMusicImportSource
    data class PlaylistUrl(val url: String) : AppleMusicImportSource
}

enum class AppleMusicImportPhase { IDLE, LOADING, MATCHING, SAVING, COMPLETE }
enum class AppleMusicImportError { NOT_CONNECTED, INVALID_LINK, PLAYLIST_NOT_FOUND, LOAD_FAILED, SAVE_FAILED }

data class AppleMusicImportResult(
    /** Null if every source track was unavailable. No empty or fabricated playlist is saved. */
    val playlist: LocalPlaylist?,
    val tracks: List<AppleMusicTrackMapping>,
) {
    val totalTracks: Int get() = tracks.size
    val matchedCount: Int get() = tracks.count { it.status == AppleMusicTrackMatchStatus.YOUTUBE }
    val appleOnlyCount: Int get() = tracks.count { it.status == AppleMusicTrackMatchStatus.APPLE_ONLY }
    val unavailableCount: Int get() = tracks.count { it.status == AppleMusicTrackMatchStatus.UNAVAILABLE }
    val unmatchedTracks: List<AppleMusicTrack> get() = tracks.filter { it.status != AppleMusicTrackMatchStatus.YOUTUBE }.map { it.track }
    val unmatchedCount: Int get() = unmatchedTracks.size
    val savedCount: Int get() = playlist?.songs?.size ?: 0
}

data class AppleMusicImportState(
    val account: AppleMusicLibrarySession.State,
    val phase: AppleMusicImportPhase = AppleMusicImportPhase.IDLE,
    val loadedCount: Int = 0,
    val resolvedCount: Int = 0,
    val totalTracks: Int? = null,
    val result: AppleMusicImportResult? = null,
    val error: AppleMusicImportError? = null,
) {
    val isRunning: Boolean get() = phase in setOf(AppleMusicImportPhase.LOADING, AppleMusicImportPhase.MATCHING, AppleMusicImportPhase.SAVING)
}

/** Synchronous commit is called off-main, only after the account/request is revalidated. */
fun interface AppleMusicImportDestination {
    fun save(title: String, songs: List<Song>): LocalPlaylist
}

/**
 * Safe local import: retains real YouTube ids and known am-miss catalog ids in order,
 * skips unavailable uploads, and reports all unmatched rows. No remote playlist is
 * created implicitly. LocalPlaylistStore must be initialized by the application.
 */
class AppleMusicImportController(
    private val session: AppleMusicLibrarySession,
    private val mapper: AppleMusicTrackMapper,
    private val source: AppleMusicBrowseDataSource = AppleMusicRestBrowseDataSource,
    private val destination: AppleMusicImportDestination = AppleMusicImportDestination { title, songs ->
        LocalPlaylistStore.savePlaylist(title, songs)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val mutableState = MutableStateFlow(AppleMusicImportState(session.refresh()))
    val state = mutableState.asStateFlow()
    private val requests = AppleMusicLibraryRequests(session)
    private val accountListener = session.listen { account ->
        requests.cancel()
        mutableState.value = AppleMusicImportState(account)
    }

    /** Returns null for a redacted state error; cancellation is always rethrown. */
    suspend fun import(
        selection: AppleMusicImportSource,
        title: String? = null,
    ): AppleMusicImportResult? = withContext(dispatcher) {
        requests.run(
            onStart = { account -> mutableState.value = AppleMusicImportState(account, AppleMusicImportPhase.LOADING) },
            onCancelled = { mutableState.value = mutableState.value.copy(phase = AppleMusicImportPhase.IDLE) },
        ) { ticket ->
            try {
                val loaded = fetch(selection, ticket) ?: return@run null
                ticket.check()
                ticket.publish {
                    mutableState.value = mutableState.value.copy(
                        phase = AppleMusicImportPhase.MATCHING,
                        loadedCount = loaded.tracks.size,
                        totalTracks = loaded.tracks.size,
                    )
                }
                val rows = mapper.matchAll(loaded.tracks, loaded.origin, loaded.storefront, ticket::check) { _, _ ->
                    ticket.publish {
                        mutableState.value = mutableState.value.copy(resolvedCount = mutableState.value.resolvedCount + 1)
                    }
                }
                val songs = rows.filter {
                    it.status == AppleMusicTrackMatchStatus.YOUTUBE || it.status == AppleMusicTrackMatchStatus.APPLE_ONLY
                }.map { it.song }
                var playlist: LocalPlaylist? = null
                // Commit is intentionally non-suspending. Session invalidation and a new
                // selection cannot interleave between validation and the local store write.
                ticket.publish {
                    mutableState.value = mutableState.value.copy(phase = AppleMusicImportPhase.SAVING)
                    if (songs.isNotEmpty()) {
                        playlist = destination.save(title?.trim()?.takeIf { it.isNotEmpty() } ?: loaded.title, songs)
                    }
                }
                val result = AppleMusicImportResult(playlist, rows)
                ticket.publish {
                    mutableState.value = mutableState.value.copy(phase = AppleMusicImportPhase.COMPLETE, result = result)
                }
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ticket.publish {
                    val error = if (mutableState.value.phase == AppleMusicImportPhase.SAVING) {
                        AppleMusicImportError.SAVE_FAILED
                    } else AppleMusicImportError.LOAD_FAILED
                    mutableState.value = mutableState.value.copy(phase = AppleMusicImportPhase.IDLE, error = error)
                }
                null
            }
        }
    }

    private data class Loaded(
        val title: String,
        val tracks: List<AppleMusicTrack>,
        val origin: AppleMusicTrackOrigin,
        val storefront: String?,
    )

    private suspend fun fetch(selection: AppleMusicImportSource, ticket: AppleMusicLibraryRequests.Ticket): Loaded? {
        fun page(tracks: List<AppleMusicTrack>) = ticket.publish {
            mutableState.value = mutableState.value.copy(loadedCount = tracks.size)
        }
        if (selection !is AppleMusicImportSource.PlaylistUrl && !ticket.account.connected) {
            fail(AppleMusicImportError.NOT_CONNECTED, ticket)
            return null
        }
        return when (selection) {
            is AppleMusicImportSource.LibraryPlaylist -> {
                require(selection.playlist.id.isNotBlank())
                Loaded(
                    selection.playlist.name.ifBlank { "Apple Music Playlist" },
                    source.playlistTracks(selection.playlist.id, ::page),
                    AppleMusicTrackOrigin.LIBRARY, ticket.account.storefront,
                )
            }
            AppleMusicImportSource.LibrarySongs -> Loaded(
                "Apple Music Library", source.songs(::page), AppleMusicTrackOrigin.LIBRARY, ticket.account.storefront,
            )
            is AppleMusicImportSource.PlaylistUrl -> {
                val link = AppleMusicPlaylistLink.parse(selection.url)
                if (link == null) {
                    fail(AppleMusicImportError.INVALID_LINK, ticket)
                    return null
                }
                val playlist = source.catalogPlaylist(link.storefront, link.playlistId)
                ticket.check()
                if (playlist == null || playlist.id != link.playlistId) {
                    fail(AppleMusicImportError.PLAYLIST_NOT_FOUND, ticket)
                    return null
                }
                Loaded(
                    playlist.name.ifBlank { "Apple Music Playlist" },
                    source.catalogPlaylistTracks(link.storefront, link.playlistId, ::page),
                    AppleMusicTrackOrigin.CATALOG, link.storefront,
                )
            }
        }
    }

    private fun fail(error: AppleMusicImportError, ticket: AppleMusicLibraryRequests.Ticket) = ticket.publish {
        mutableState.value = mutableState.value.copy(phase = AppleMusicImportPhase.IDLE, error = error)
    }

    fun cancel() = session.locked {
        requests.cancel()
        mutableState.value = mutableState.value.copy(phase = AppleMusicImportPhase.IDLE)
    }

    override fun close() {
        accountListener.close()
        requests.close()
    }
}
