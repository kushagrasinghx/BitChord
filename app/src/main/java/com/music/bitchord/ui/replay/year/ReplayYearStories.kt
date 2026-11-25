package com.music.bitchord.ui.replay.year

import androidx.compose.runtime.snapshotFlow
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.replay.BACK_ZONE
import com.music.bitchord.ui.replay.Segment
import com.music.bitchord.ui.replay.StoryFrame
import com.music.bitchord.ui.replay.rotated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The Replay year story: the year told as a run of loud, animated cards, each
 * scored with the best part of a song that belongs to it.
 *
 * It keeps everything the ordinary Replay story taught (see
 * [com.music.bitchord.ui.replay.ReplayStories]): the fixed 9:16 frame, the
 * artwork mesh rotated to a new colour per card, tap right for on and left for
 * back, hold to stop the clock, swipe, the share button on every card. What it
 * adds is motion and sound: the cards don't slide past, they arrive, and each
 * card's big moment is timed to land with its clip's chorus.
 *
 * The one clock per card ([SlideClock]) drives every animation on it and the
 * progress bar both, so holding the card freezes the scene and the audio
 * together.
 */
@Composable
fun ReplayYearStories(
    year: ReplayYear,
    onClose: () -> Unit,
    /** Shares the card on screen, as its nearest card in the ordinary Replay. */
    onShare: (YearSlide) -> Unit,
    /** Holds everything while something (the share sheet) is up in front. */
    paused: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slides = year.slides
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    var index by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var held by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(readMuted(context)) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Off screen (home, another app, the screen locked) the story stops where
    // it is, music and all, as if a finger were on the card, and carries on
    // from there on coming back. Closing the app tears it down and lets go.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onScreen by lifecycle.currentStateFlow.collectAsState()
    val hidden = !onScreen.isAtLeast(Lifecycle.State.STARTED)

    // ── The soundtrack ──
    val player = remember { YearClipPlayer(context.applicationContext, scope) }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(muted) { player.muted = muted }
    val starts = remember { mutableStateMapOf<String, BestPartStart>() }

    // Every card's best part is looked up at once, three at a time and in the
    // order the cards are told, so the first ones are ready first. The story
    // waits for the opening card's (or [BUILD_MAX_MS], whichever is sooner)
    // behind a short "building" screen; the rest arrive while it plays.
    LaunchedEffect(year) {
        val songs = slides.mapNotNull { year.clips[it] }.distinctBy { it.videoId }
        val gate = Semaphore(LOOKUPS_AT_ONCE)
        songs.forEach { song ->
            BestParts.known(song.videoId)?.let { starts[song.videoId] = it }
            launch {
                if (song.videoId in starts) return@launch
                gate.withPermit { starts[song.videoId] = BestParts.startFor(context.applicationContext, song) }
            }
        }
        val first = year.clips[slides.first()]
        val began = System.currentTimeMillis()
        withTimeoutOrNull(BUILD_MAX_MS) {
            while (first != null && first.videoId !in starts) delay(50)
        }
        clipOf(player, first, starts, slides.first())?.let(player::prime)
        delay((BUILD_MIN_MS - (System.currentTimeMillis() - began)).coerceAtLeast(0))
        ready = true
    }

    val slide = slides[index.coerceIn(0, slides.lastIndex)]

    LaunchedEffect(index, ready) {
        if (!ready) return@LaunchedEffect
        player.play(clipOf(player, year.clips[slide], starts, slide))
        // The next card's clip waits on the deck that is still fading this one's
        // predecessor out, then loads; by the time this card ends it is ready.
        slides.getOrNull(index + 1)?.let { next ->
            delay(PRIME_AFTER_MS)
            player.prime(clipOf(player, year.clips[next], starts, next))
        }
    }
    LaunchedEffect(held, paused, hidden) { player.hold(held || paused || hidden) }

    // The card's haptics, written down by its own animations as it composes
    // (see [SlideClock.beat]) and struck here as its clock passes each one, so
    // every landing is felt on the frame it is seen. Off the same clock as the
    // pictures: holding the card holds them too, and coming back plays them again.
    val beats = remember(index) { HapticBeats() }
    val haptics = rememberHaptics()
    LaunchedEffect(index, ready, beats) {
        if (!ready) return@LaunchedEffect
        var last = -1f
        snapshotFlow { progress * PAGE_MS }.collect { now ->
            val hit = beats.strongestBetween(last, now)
            last = now
            // With motion reduced nothing slides or counts, so only the landings are felt.
            if (hit != null && !(reduceMotion && hit == Haptic.Tick)) haptics.play(hit)
        }
    }

    fun goTo(target: Int) {
        if (target !in slides.indices) return
        forward = target > index
        // Reset in the same frame the card changes: the new card's clock reads
        // this, and a reset that waited a frame would show it already finished.
        progress = 0f
        index = target
    }

    fun step(ahead: Boolean) {
        if (ahead && index == slides.lastIndex) {
            onClose()
            return
        }
        goTo(index + if (ahead) 1 else -1)
    }

    LaunchedEffect(index, held, paused, hidden, ready) {
        if (!ready || held || paused || hidden) return@LaunchedEffect
        val remaining = ((1f - progress) * PAGE_MS).toInt().coerceAtLeast(0)
        animate(progress, 1f, animationSpec = tween(remaining, easing = LinearEasing)) { value, _ -> progress = value }
        // The last card waits out its time and then closes, as the Replay story does.
        if (index == slides.lastIndex) onClose() else goTo(index + 1)
    }

    val palette = paletteFor(year, slide)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        StoryFrame {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF17171A))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                held = true
                                tryAwaitRelease()
                                held = false
                            },
                            // See ReplayStories: without it every press is a tap.
                            onLongPress = {},
                            onTap = { offset -> if (ready) step(offset.x >= size.width * BACK_ZONE) },
                        )
                    }
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onHorizontalDrag = { change, amount ->
                                dragged += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (ready && abs(dragged) > SWIPE_PX) step(ahead = dragged < 0)
                            },
                        )
                    },
            ) {
                YearBackdrop(slide, palette)

                if (!ready) {
                    Building()
                } else {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = { transitionInto(slides[targetState], forward, reduceMotion) },
                        label = "year-slide",
                    ) { shown ->
                        val live = shown == index
                        val clock = remember(live, reduceMotion, beats) {
                            // A card on its way out stays as it finished rather
                            // than replaying its entrance as it leaves.
                            if (live) SlideClock({ progress * PAGE_MS }, reduceMotion, beats)
                            else SlideClock({ Float.MAX_VALUE / 4f }, reduceMotion)
                        }
                        androidx.compose.runtime.CompositionLocalProvider(LocalSlideClock provides clock) {
                            YearSlideContent(slides[shown], year, palette)
                        }
                    }
                }

                YearChrome(
                    year = year.year,
                    count = slides.size,
                    current = index,
                    progress = { progress },
                    muted = muted,
                    onMute = {
                        muted = !muted
                        writeMuted(context, muted)
                    },
                    onClose = onClose,
                )

                if (ready) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 18.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { onShare(slide) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = stringResource(R.string.share_my_replay),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * How a card arrives. Not one slide for all of them: the run alternates between
 * zooming through, rising up and sweeping across, so turning a card feels like
 * a cut rather than a page.
 */
private fun transitionInto(slide: YearSlide, forward: Boolean, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(220)) togetherWith fadeOut(tween(180))
    val enterMs = 420
    val exitMs = 320
    return when (slide.ordinal % 3) {
        0 -> (scaleIn(tween(enterMs), initialScale = if (forward) 0.82f else 1.18f) + fadeIn(tween(enterMs))) togetherWith
            (scaleOut(tween(exitMs), targetScale = if (forward) 1.2f else 0.85f) + fadeOut(tween(exitMs)))
        1 -> (slideInVertically(tween(enterMs)) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(enterMs))) togetherWith
            (slideOutVertically(tween(exitMs)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(exitMs)))
        else -> (slideInHorizontally(tween(enterMs)) { if (forward) it else -it } + fadeIn(tween(enterMs / 2))) togetherWith
            (slideOutHorizontally(tween(exitMs)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(exitMs)))
    }
}

/**
 * The clip for [slide], started early enough that the chorus's first word lands
 * on the card's reveal. Its best part is whatever is known by now; a song still
 * being looked up starts at the fallback.
 */
private fun clipOf(
    player: YearClipPlayer,
    song: Song?,
    starts: Map<String, BestPartStart>,
    slide: YearSlide,
): YearClipPlayer.Clip? {
    song ?: return null
    val best = starts[song.videoId] ?: BestParts.known(song.videoId)
        ?: BestPartStart(BestPart.fallbackMs(0), BestPartStart.Source.FALLBACK)
    // The best part starts a quarter-second before its line is sung (see
    // BestPart.snap), so the word itself is at startMs + 250.
    val seek = (best.startMs + SNAP_LEAD_MS - slide.revealAtMs).coerceAtLeast(0)
    return player.clipFor(song, seek)
}

@Composable
private fun Building() {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(1f, tween(BUILD_MAX_MS.toInt(), easing = LinearEasing)) }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSlideClock provides SlideClock({ clock.value * BUILD_MAX_MS }, reduceMotion = false),
        ) {
            OrbitRings(start = 0, color = Color.White, modifier = Modifier.size(160.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.replay_year_building),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
                color = Color.White,
                modifier = Modifier.breathe(LocalSlideClock.current, 0, amount = 0.04f, periodMs = 1_400f),
            )
        }
    }
}

/**
 * The cross, the progress segments, and the name — the Replay story's chrome,
 * with a mute button beside the cross since this one has a soundtrack.
 */
@Composable
private fun YearChrome(
    year: Int,
    count: Int,
    current: Int,
    progress: () -> Float,
    muted: Boolean,
    onMute: () -> Unit,
    onClose: () -> Unit,
    /**
     * For the shared picture: only the name, where it sits on the story. The
     * buttons and segments keep their room, invisible, so the card below them
     * is laid out exactly as it was seen.
     */
    still: Boolean = false,
) {
    val controls = if (still) Modifier.alpha(0f) else Modifier
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).then(controls),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(if (muted) R.string.replay_year_unmute else R.string.replay_year_mute),
                tint = Color.White,
                modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onMute).padding(4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close_replay),
                tint = Color.White,
                modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClose).padding(4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = controls) {
            repeat(count) { i ->
                // Only the current segment reads the clock, so only it redraws per frame.
                if (i == current) {
                    Segment(fraction = progress(), modifier = Modifier.weight(1f))
                } else {
                    Segment(fraction = if (i < current) 1f else 0f, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Replay ${year % 100}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(width = 26.dp, height = 17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "BitChord",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

/** The same backdrop as the ordinary Replay story, card for card. */
@Composable
private fun YearBackdrop(slide: YearSlide, palette: com.music.bitchord.ui.player.MeshPalette) {
    MeshGradientBackground(palette = palette, trackKey = slide.name, animated = false)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.45f),
                    0.35f to Color.Black.copy(alpha = 0.10f),
                    1.0f to Color.Black.copy(alpha = 0.34f),
                ),
            ),
    )
}

/** The colours [slide] is drawn in: its song's artwork, turned to the card's own hue. */
@Composable
private fun paletteFor(year: ReplayYear, slide: YearSlide): com.music.bitchord.ui.player.MeshPalette {
    val artwork = year.clips[slide]?.thumbnailUrl ?: year.data.summary.songs.firstOrNull()?.song?.thumbnailUrl
    return rememberArtworkColors(artwork).rotated(slide.hue)
}

/**
 * [slide] as it stands when its time on the story is up, everything landed:
 * the backdrop, the card and the name above it, without the buttons, the
 * progress or the share button. What the shared picture is made from.
 */
@Composable
internal fun YearCardStill(year: ReplayYear, slide: YearSlide, modifier: Modifier = Modifier) {
    val palette = paletteFor(year, slide)
    val clock = remember { SlideClock({ PAGE_MS }, reduceMotion = false) }
    Box(modifier.background(Color(0xFF17171A))) {
        YearBackdrop(slide, palette)
        androidx.compose.runtime.CompositionLocalProvider(LocalSlideClock provides clock) {
            YearSlideContent(slide, year, palette)
        }
        YearChrome(
            year = year.year,
            count = year.slides.size,
            current = year.slides.indexOf(slide),
            progress = { 1f },
            muted = false,
            onMute = {},
            onClose = {},
            still = true,
        )
    }
}

private fun prefs(context: Context) = context.getSharedPreferences("replay_year", Context.MODE_PRIVATE)

private fun readMuted(context: Context): Boolean =
    runCatching { prefs(context).getBoolean("muted", false) }.getOrDefault(false)

private fun writeMuted(context: Context, muted: Boolean) {
    runCatching { prefs(context).edit().putBoolean("muted", muted).apply() }
}

/** How long a card holds before moving on. About the length of a chorus. */
private const val PAGE_MS = 15_000f

/** The "building" screen shows at least this long, so it reads as a moment rather than a flicker… */
private const val BUILD_MIN_MS = 1_200L

/** …and never longer than this, whatever is still being looked up. */
private const val BUILD_MAX_MS = 2_500L

private const val LOOKUPS_AT_ONCE = 3
private const val PRIME_AFTER_MS = 1_200L
private const val SNAP_LEAD_MS = 250L
private const val SWIPE_PX = 120f
