package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.ExplicitFilter
import com.music.bitchord.ui.AppUi

// "Block explicit songs" in the shared lists: a blocked row is drawn greyed out,
// and tapping it asks before playing. Each row takes an `explicitBlocked`
// parameter, defaulting to [isExplicitBlocked], so the styling and the tap
// follow one value. The question itself is asked by the app around the lists
// (see [AppUiHost.askExplicitBlocked][com.music.bitchord.ui.AppUiHost.askExplicitBlocked]),
// so each platform draws it in its own alert style.

/** How dim a blocked explicit row is drawn, the same as an unavailable one. */
const val BLOCKED_ROW_ALPHA = 0.45f

/**
 * A tapped blocked explicit song, waiting on the app's alert. [play] is the
 * row's original tap, run once the listener allows the song or turns blocking
 * off; Cancel drops it.
 */
data class ExplicitPrompt(val videoId: String, val title: String, val play: () -> Unit)

/**
 * Whether "Block explicit songs" keeps [song] from playing. Always false while
 * blocking is off, so rows look exactly as before.
 */
@Composable
fun isExplicitBlocked(song: Song): Boolean = isExplicitBlocked(song.isExplicit, song.videoId)

/** [isExplicitBlocked] for a Home card. */
@Composable
fun isExplicitBlocked(item: ShelfItem): Boolean = isExplicitBlocked(item.isExplicit, item.videoId)

@Composable
private fun isExplicitBlocked(isExplicit: Boolean?, videoId: String?): Boolean {
    val enabled by AppUi.host.blockExplicit.collectAsStateWithLifecycle()
    val allowed by AppUi.host.explicitAllowed.collectAsStateWithLifecycle()
    return ExplicitFilter.blocks(isExplicit, videoId, enabled, allowed)
}

/** A song row's tap: plays as usual, unless [blocked], when it asks first (see [ExplicitPrompt]). */
fun explicitGatedClick(song: Song, blocked: Boolean, onClick: () -> Unit): () -> Unit =
    explicitGatedClick(song.videoId, song.title, blocked, onClick)

/** [explicitGatedClick] for a Home card. */
fun explicitGatedClick(item: ShelfItem, blocked: Boolean, onClick: () -> Unit): () -> Unit =
    explicitGatedClick(item.videoId, item.title, blocked, onClick)

private fun explicitGatedClick(videoId: String?, title: String, blocked: Boolean, onClick: () -> Unit): () -> Unit {
    if (!blocked || videoId == null) return onClick
    return { AppUi.host.askExplicitBlocked(ExplicitPrompt(videoId, title, onClick)) }
}
