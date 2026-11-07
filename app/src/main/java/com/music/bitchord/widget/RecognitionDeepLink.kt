package com.music.bitchord.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A match on its way from [RecognitionActivity]'s microphone to the Search
 * page that turns it into a playable song.
 *
 * Both ends share one process — the recorder is launched by the widget and
 * launches the app itself — so a StateFlow is the entire channel. The
 * ordering is the protocol: [postMatch] runs before MainActivity's launch
 * does, because the hand-off is set on the way to opening the app, and
 * the composition reads it whenever it starts — cold or warm — without
 * anything riding on Intent extras or saved state. If the process instead
 * died between the match and the launch, stopping here is the right
 * response anyway: the user's tap was answered the first time, and the next
 * tap is a whole new listen.
 */
object RecognitionDeepLink {

    private val _pendingMatch = MutableStateFlow<String?>(null)

    /** The matched song's search query, or null while nothing is pending. */
    val pendingMatch: StateFlow<String?> = _pendingMatch.asStateFlow()

    /** Called by [RecognitionActivity] once the fingerprint has a song's name. */
    fun postMatch(query: String) {
        _pendingMatch.value = query
    }

    /** Clears the hand-off; called by the composition that consumes it. */
    fun matchHandled() {
        _pendingMatch.value = null
    }
}
