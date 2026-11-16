package com.music.bitchord.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The frame the player's backdrop takes its colours from while motion artwork is playing.
 *
 * A canvas is usually not the still cover in motion — it is a different shot entirely, and often a
 * different palette. Built from the still, the mesh behind a black-and-white clip stayed the green
 * of the album art it replaced, which is two pictures of the same song disagreeing on screen.
 *
 * One slot, because the player shows one canvas at a time; keyed by clip so a frame published by a
 * track that has since changed cannot be picked up by the next one.
 */
internal object DesktopCanvasBackdrop {

    private var held by mutableStateOf<Pair<String, ImageBitmap>?>(null)

    /** Called with the clip's first frame, which is all the mesh needs. */
    fun publish(url: String, frame: ImageBitmap) {
        held = url to frame
    }

    /** Dropped when the clip stops, so the still cover takes the backdrop back. */
    fun clear(url: String) {
        if (held?.first == url) held = null
    }

    /** The frame for [url], or null when that clip is not the one playing. */
    fun frameFor(url: String?): ImageBitmap? =
        url?.let { wanted -> held?.takeIf { it.first == wanted }?.second }
}
