package com.music.bitchord.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A better copy of the playing track, found after it started on YouTube and
 * offered to the listener instead of swapped in.
 *
 * Under Direct Source Stream with "Start on YouTube if the addon is slow" on,
 * a track whose addon answer wasn't ready when it had to start plays from
 * YouTube to its end unless the listener asks for the better copy. The player
 * draws the offer as a button where the quality badge goes, and a tap comes
 * back here as [accepted], which the playback service turns into the swap.
 *
 * Here rather than next to the stream it describes because the player UI lives
 * in a module that cannot see playback: this holds only what the button needs.
 */
object UpgradeOffers {

    /** [label] is what the button names, e.g. "FLAC", "Hi-Res" or "320 kbps". */
    data class Offer(val mediaId: String, val label: String)

    private val _offered = MutableStateFlow<Offer?>(null)

    /** The offer for the playing track, or null. One at a time: only the playing track has one. */
    val offered: StateFlow<Offer?> = _offered.asStateFlow()

    private val _accepted = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Media ids the listener tapped the offer for. */
    val accepted: SharedFlow<String> = _accepted.asSharedFlow()

    fun publish(mediaId: String, label: String) {
        _offered.value = Offer(mediaId, label)
    }

    fun withdraw(mediaId: String) {
        if (_offered.value?.mediaId == mediaId) _offered.value = null
    }

    fun accept(mediaId: String) {
        _accepted.tryEmit(mediaId)
    }
}
