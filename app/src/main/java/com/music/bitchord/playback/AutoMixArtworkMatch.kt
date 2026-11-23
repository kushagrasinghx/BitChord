package com.music.bitchord.playback

import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt

internal data class AutoMixCoverSource(val url: String, val remoteScope: Long? = null)

/** The exact full-size request shared by both tracks, provided their mini requests also agree. */
internal fun matchingAutoMixCover(outgoing: AutoMixCoverSource?, incoming: AutoMixCoverSource?): String? {
    if (outgoing == null || incoming == null || outgoing.url.isBlank() || incoming.url.isBlank()) return null
    if (outgoing.remoteScope != incoming.remoteScope) return null
    // Do not infer aliases for authenticated endpoints or extracted private files.
    if (outgoing.remoteScope != null && outgoing.url != incoming.url) return null
    val full = incoming.url.artworkAt(PLAYER_ART_PX)
    return full.takeIf {
        it == outgoing.url.artworkAt(PLAYER_ART_PX) &&
            incoming.url.artworkAt(ROW_ART_PX) == outgoing.url.artworkAt(ROW_ART_PX)
    }
}

internal data class AutoMixCanvasPresentation(val clip: CanvasArtwork?)

enum class AutoMixArtworkAdmission { REVEAL, WAITING, BYPASS }

internal fun artworkAdmission(
    autoMix: Boolean, art: Boolean, mini: Boolean,
    matchingSources: () -> Boolean,
    prepared: () -> Boolean,
    matchingPresentation: () -> Boolean?,
): AutoMixArtworkAdmission {
    if (!autoMix || (!art && !mini) || !matchingSources()) return AutoMixArtworkAdmission.REVEAL
    val samePresentation = if (art) matchingPresentation() else true
    if (samePresentation == false) return AutoMixArtworkAdmission.REVEAL
    return if (samePresentation == true && prepared()) AutoMixArtworkAdmission.BYPASS else AutoMixArtworkAdmission.WAITING
}

/** Null is unresolved; a known still presentation has no clip. */
internal fun sameAutoMixCanvas(outgoing: AutoMixCanvasPresentation?, incoming: AutoMixCanvasPresentation?): Boolean {
    if (outgoing == null || incoming == null) return false
    val clip = outgoing.clip ?: return incoming.clip == null
    return incoming.clip?.samePresentationAs(clip) == true
}
