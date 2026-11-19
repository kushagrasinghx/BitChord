package com.music.bitchord.data.smartoffline

import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.model.Song

/**
 * What the listener has said about their music, in the form the ranking can
 * use.
 *
 * ## Only durable things count
 *
 * A rotation is meant to still be right tomorrow, so it can only be built on
 * signals that survive until tomorrow. Two kinds of signal here do not:
 *
 * - The **YouTube rating** ([com.music.bitchord.data.LikeState]) is an
 *   in-memory overlay, re-seeded from the server each session and empty after
 *   a cold start. It is deliberately **not** consulted here.
 * - The **Spotify library** is fetched, so it is durable once fetched but
 *   needs the network and can change shape underneath. It is not consulted
 *   here either.
 *
 * What *is* consulted is the app's own persisted list of liked device files
 * and the playlists the listener has made — both written to disk by
 * [OfflinePlaylists] and read back the next morning.
 *
 * ## The identity problem
 *
 * The listening history is keyed by whatever id the track was played under,
 * which for a device file is either the file's own content URI or the id
 * BitChord gave it when it downloaded the file. The likes and playlists are
 * keyed by content URI. So a track's history id is matched against both, and
 * the answer is the same either way.
 */
object Preferences {

    /**
     * A lookup over the downloaded files, matching each track's history id to
     * what the listener has said about it.
     */
    fun lookup(downloaded: List<Song>): PreferenceLookup {
        val likedUris = OfflinePlaylists.liked.value
        val playlistUris = OfflinePlaylists.playlists.value
            .filterNot { it.isLiked }
            .flatMap { playlist -> playlist.songs }
            .mapNotNull { song -> song.localUri }
            .toHashSet()

        val liked = HashSet<String>()
        val inPlaylist = HashSet<String>()
        for (song in downloaded) {
            val id = song.videoId
            val uri = song.localUri
            // The file may be liked or filed under its own URI, or under the
            // id BitChord gave it when it downloaded it.
            if (uri != null && uri in likedUris) liked += id
            if (id in likedUris) liked += id
            if (uri != null && uri in playlistUris) inPlaylist += id
            if (id in playlistUris) inPlaylist += id
        }

        return PreferenceLookup { id ->
            when {
                id in liked -> TrackPreference.LIKED
                id in inPlaylist -> TrackPreference.PLAYLIST
                else -> TrackPreference.NONE
            }
        }
    }
}
