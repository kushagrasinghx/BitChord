package com.music.bitchord.playback

import com.music.bitchord.data.model.Song

/**
 * The rule behind "Block explicit songs" (#720), kept apart from the UI so the
 * player, AutoPlay and the lists all block exactly the same songs.
 *
 * While the setting is on (`enabled`), a song is blocked when YouTube Music
 * marks it explicit and the listener hasn't allowed it: `allowed` holds the
 * video ids of songs let through one at a time. Songs with no explicit badge
 * either way (local files, uploads) are never blocked.
 */
object ExplicitFilter {
    /** Whether [song] is kept from playing. */
    fun blocks(song: Song, enabled: Boolean, allowed: Set<String>): Boolean =
        blocks(song.isExplicit, song.videoId, enabled, allowed)

    /** [blocks] for something that isn't a [Song] yet, such as a Home card. */
    fun blocks(isExplicit: Boolean?, videoId: String?, enabled: Boolean, allowed: Set<String>): Boolean =
        enabled && isExplicit == true && videoId != null && videoId !in allowed

    /** [songs] without the blocked ones, in their original order. */
    fun filter(songs: List<Song>, enabled: Boolean, allowed: Set<String>): List<Song> =
        if (!enabled) songs else songs.filterNot { blocks(it, enabled, allowed) }
}
