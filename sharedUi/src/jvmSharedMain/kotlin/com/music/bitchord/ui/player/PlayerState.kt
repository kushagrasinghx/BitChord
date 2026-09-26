package com.music.bitchord.ui.player

import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import com.music.bitchord.playback.PlaybackPosition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The looping clip some releases publish alongside their cover, for [song], or
 * null — a miss is the normal answer.
 */
@Composable
internal fun rememberCanvasArtwork(song: Song): CanvasArtwork? {
    val canvasEnabled by PlayerSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by PlayerSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val prioritizeSpotifyCanvas by PlayerSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    val meteredConnection by PlayerSettings.meteredConnection.collectAsStateWithLifecycle()
    // The switch turns the feature off outright; this is the narrower "not
    // over cellular" case — see [PlayerSettings.canvasOverCellular] for why a
    // clip's own loop makes that worth guarding separately from a still image.
    val canvasAllowedNow = canvasEnabled && (meteredConnection != true || canvasOverCellular)
    // Keyed on the song's identity rather than its videoId: a version switch
    // swaps the videoId for another cut of the *same* song, and a canvas is
    // searched for by title and artist — resetting here threw the clip out
    // mid-loop, blanked the player down to the still sleeve and restarted it,
    // which is the flicker. A different song keys differently and starts
    // clean; the same-titled-impostor case is guarded where the clip is
    // adopted, by [CanvasArtwork.matches].
    var canvas by remember(song.title, song.artist) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(song.videoId, song.albumName, canvasAllowedNow, prioritizeSpotifyCanvas) {
        if (!canvasAllowedNow) {
            canvas = null
            return@LaunchedEffect
        }
        // Anything already settled for this track paints immediately: a
        // reopened player, or a track coming round again in the queue.
        //
        // Across a version switch the clip found for the last cut is carried
        // over rather than dropped — same title and artist, so the same
        // search, so the same clip — and only while it still *answers* to
        // what is playing: [CanvasArtwork.matches] is what stops a preserved
        // clip from being a different song's that happens to share a title.
        canvas = PlayerPlatform.host.cachedCanvas(song)
            ?: canvas?.takeIf { it.matches(song.title, song.artist, song.albumName) }

        // The album name is looked up separately and lands a moment after the
        // player opens, and it is the field that makes the catalogue searches
        // match. Give it that moment: if it arrives, this effect restarts and
        // all that was spent waiting is the wait. If it never does — a track
        // with no album, or a lookup that failed — the search still goes out,
        // just a beat later, which is imperceptible for decoration.
        if (canvas == null && song.albumName == null) delay(ALBUM_SETTLE_MS)
        // Keep what an earlier pass found if this one comes back empty, rather
        // than pulling a playing clip out from under itself.
        canvas = PlayerPlatform.host.canvasFor(song) ?: canvas
    }

    return canvas
}

/**
 * The player's cover: the one request every surface draws it from, whether it
 * has loaded, and the bounded retries after a failure.
 *
 * Everything here is keyed on the cover's [url] — see [rememberPlayerArtwork].
 */
@Stable
internal class PlayerArtwork(val url: String?, private val context: PlatformContext) {
    /**
     * Whether the cover is on screen. Keyed on the artwork rather than on the
     * track, because that is what it actually describes and because only Coil
     * can set it back to true.
     */
    var loaded by mutableStateOf(false)
        private set

    /**
     * Which go at this cover we are on, and the reason there is more than one.
     *
     * Coil does not retry: a request that fails is over, and the state it leaves
     * behind is the state this screen keeps until the model changes — which,
     * keyed on the cover, means until the next track. One dropped connection at
     * the wrong moment and the player showed its placeholder tile for a song it
     * would have drawn perfectly a second later, with the widget and the
     * notification both showing the cover from cache the whole time.
     *
     * Bounded and spaced, because the usual reason a cover fails is that there
     * is no network at all, and a retry per recomposition — which is what an
     * unremembered request effectively gave — is a spin, not a recovery.
     */
    var attempt by mutableIntStateOf(0)
        private set

    /** Latched on an error, and only upwards — cleared by the retry itself. */
    internal var failed by mutableStateOf(false)

    /**
     * The one request for this cover, built once.
     *
     * Both the sleeve and the full-bleed banner draw from it, which is what
     * their own comments claim ("one ask, one decode, one bitmap for both") and
     * what building it inline at each of them quietly failed to deliver: Coil
     * compares models to decide whether to start a new load, and two separately
     * built requests are never equal — `ImageRequest` has no `equals`, and
     * neither does the size resolver `.size()` hands it. So each was its own
     * load, and worse, *every recomposition* was another one. The player
     * recomposes at least twice a second off the position tick, and each pass
     * pushed the painter back through Loading before it settled on Success
     * again, which is exactly the [loaded] this screen hangs the banner, the
     * sleeve's alpha, its shadow and its placeholder icon on.
     *
     * Remembered on the cover and the attempt, so it changes when the picture
     * changes and when a retry is deliberately asked for, and at no other time.
     */
    val request: ImageRequest by derivedStateOf {
        val attempt = attempt
        ImageRequest.Builder(context)
            .data(url)
            .size(ART_PX)
            // What makes a retry a new request as far as Coil's model comparison
            // is concerned. Only from the second go onwards, so the ordinary
            // request stays byte-identical to the one the mesh and the palette
            // make of the same cover and goes on sharing their memory-cache
            // entry. The disk key is unaffected either way.
            .apply { if (attempt > 0) memoryCacheKeyExtra("attempt", attempt.toString()) }
            .build()
    }

    /** Fed from each painter drawing [request]. */
    fun onState(state: AsyncImagePainter.State) {
        loaded = state is AsyncImagePainter.State.Success
        // Only the failure is latched, and only upwards: the retry that clears
        // it is [failed]'s own effect, and clearing it from a Loading state here
        // would cancel that effect's wait every time the painter passed back
        // through Loading.
        if (state is AsyncImagePainter.State.Success) failed = false
        if (state is AsyncImagePainter.State.Error) failed = true
    }

    internal fun retry() {
        failed = false
        attempt++
    }
}

@Composable
internal fun rememberPlayerArtwork(remoteArt: String?): PlayerArtwork {
    val context = LocalPlatformContext.current
    val artUrl = remoteArt?.artworkAt(ART_PX)
    val art = remember(artUrl) { PlayerArtwork(artUrl, context) }
    LaunchedEffect(artUrl, art.failed) {
        // A track with no artwork at all fails immediately and would fail
        // identically three more times: there is no request to make, so there is
        // nothing a second go could do differently.
        if (artUrl == null || !art.failed || art.attempt >= ART_RETRIES) return@LaunchedEffect
        delay(ART_RETRY_DELAY_MS)
        art.retry()
    }
    return art
}

/**
 * The seek bar's own state: a drag in progress, and where the handle was
 * dropped until the player's position catches up with it.
 */
@Stable
internal class PlayerScrub {
    var scrubbing by mutableStateOf(false)
        private set
    var value by mutableFloatStateOf(0f)
        private set

    /**
     * After releasing the scrubber the player needs to buffer before it
     * reports the new position. Kept showing where the user dropped it so the
     * handle doesn't snap back and then jump forward once loading finishes.
     */
    internal var pendingSeek by mutableStateOf<Float?>(null)

    /** Where the handle is drawn. */
    fun shown(positionMs: Long, durationMs: Long): Float {
        val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        return when {
            scrubbing -> value
            pendingSeek != null -> pendingSeek!!
            else -> fraction.coerceIn(0f, 1f)
        }
    }

    fun drag(to: Float) {
        scrubbing = true
        value = to
    }

    fun release(seekTo: (Float) -> Unit) {
        pendingSeek = value
        seekTo(value)
        scrubbing = false
    }
}

@Composable
internal fun rememberPlayerScrub(trackId: String, position: PlaybackPosition, durationMs: Long): PlayerScrub {
    val scrub = remember { PlayerScrub() }
    var pendingSeek by scrub::pendingSeek

    // Released as soon as the player's own position agrees with where the handle
    // was dropped — and unconditionally a few seconds later whether it agrees or
    // not.
    //
    // The agreement test alone is not enough, because it is the only thing that
    // ever cleared the override: if the position never passes close to the
    // target — a clamped or rejected seek, a rendition swapped underneath, a
    // progress sample that steps straight over the window — nothing releases it
    // and the handle sits frozen at the drop point for the rest of the track.
    // Audio and lyrics follow the real position perfectly throughout, so the
    // failure looks like a stuck seek bar on a track that is playing fine.
    //
    // Tolerance is absolute rather than a share of the duration: two percent is
    // a quarter-second on a jingle and twelve seconds on a long mix, and it is
    // the wall-clock gap that decides whether the handle appears to jump.
    //
    // Watched from inside the effect rather than keyed on the position, so the
    // screen that owns this does not have to read the playhead to drive it.
    LaunchedEffect(durationMs, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (durationMs <= 0) return@LaunchedEffect
        snapshotFlow { position.positionMs }.first { positionMs ->
            abs(positionMs - (target * durationMs).toLong()) < SEEK_SETTLE_TOLERANCE_MS
        }
        pendingSeek = null
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(trackId) { pendingSeek = null }

    return scrub
}

/**
 * The volume bar's state: the system output level, tweened to whatever the
 * system reports, and followed exactly under a finger.
 */
@Stable
internal class PlayerVolume(
    private val system: SystemVolume,
    private val scope: CoroutineScope,
) {
    /**
     * Animatable rather than plain state: a hardware volume step is a jump of
     * 1/15th of the bar, which reads as a stutter unless it's tweened.
     */
    val level = Animatable(system.level.value)
    var dragging by mutableStateOf(false)
        private set

    fun drag(to: Float) {
        dragging = true
        // Follow the finger exactly; only external changes tween.
        scope.launch { level.snapTo(to) }
        system.set(to)
    }

    fun release() {
        dragging = false
    }
}

@Composable
internal fun rememberPlayerVolume(): PlayerVolume {
    val system = PlayerPlatform.host.volume
    val scope = rememberCoroutineScope()
    val volume = remember(system) { PlayerVolume(system, scope) }
    // Hardware volume keys and the system panel change the level behind our
    // back; the host follows them, and so does the bar.
    val systemVolume by system.level.collectAsStateWithLifecycle()

    // Glide to the level the system reports, but never fight the finger — a
    // drag writes the level, which calls straight back through here.
    LaunchedEffect(systemVolume) {
        if (!volume.dragging) {
            volume.level.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    return volume
}

/**
 * Reads the playhead inside a recomposition scope of its own, so a tick
 * recomposes [content] and not the screen around it — see [PlaybackPosition].
 */
@Composable
internal fun PlaybackPositionScope(positionMs: () -> Long, content: @Composable (Long) -> Unit) {
    content(positionMs())
}
