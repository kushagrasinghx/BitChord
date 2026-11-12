package com.music.bitchord.wear.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.music.bitchord.wear.R
import com.music.bitchord.wear.data.NowPlaying
import com.music.bitchord.wear.data.Volume
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the player can ask of the phone. */
class PlayerActions(
    val togglePlay: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val toggleLike: () -> Unit,
    val volume: (Int) -> Unit,
    val seekTo: (Long) -> Unit,
    val openQueue: () -> Unit,
    val openMore: () -> Unit,
)

/**
 * The phone's player, on a round screen.
 *
 * The cover *is* the play button: a disc with the progress ring round it, the
 * way a watch reads progress, and the glyph over it. That is what lets title,
 * transport and the three actions all fit a 40mm Galaxy Watch without anything
 * shrinking past legible — every size below is a fraction of the screen.
 *
 * The crown is the phone's volume, as on every watch media app.
 */
@Composable
fun NowPlayingScreen(
    np: NowPlaying,
    artwork: ImageBitmap?,
    volume: Volume?,
    actions: PlayerActions,
) {
    val view = LocalView.current
    var rotary by remember { mutableFloatStateOf(0f) }
    // Bumped on every crown step; the readout shows while it keeps changing.
    var volumeNudge by remember { mutableIntStateOf(0) }

    // Hold-to-seek: the target while the disc is a scrubber, null otherwise.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var scrubHeld by remember { mutableStateOf(false) }
    var scrubActivity by remember { mutableIntStateOf(0) }
    fun scrubBy(deltaMs: Long) {
        val from = scrubMs ?: return
        scrubMs = (from + deltaMs).coerceIn(0L, np.durationMs.coerceAtLeast(0L))
        scrubActivity++
    }
    fun commitScrub() {
        scrubMs?.let(actions.seekTo)
        scrubMs = null
        scrubHeld = false
    }
    // Let go and left alone for a moment: that is the answer.
    LaunchedEffect(scrubActivity, scrubHeld) {
        if (scrubMs == null || scrubHeld) return@LaunchedEffect
        delay(SCRUB_SETTLE_MS)
        commitScrub()
    }
    // A new track makes the old target meaningless.
    LaunchedEffect(np.mediaId) {
        scrubMs = null
        scrubHeld = false
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val px = event.verticalScrollPixels
                // A notched bezel reports one big event per detent, a smooth
                // crown a stream of small ones: one step per detent either way.
                val steps = if (abs(px) >= ROTARY_STEP_PX) {
                    rotary = 0f
                    if (px > 0) 1 else -1
                } else {
                    rotary += px
                    (rotary / ROTARY_STEP_PX).toInt().also { rotary -= it * ROTARY_STEP_PX }
                }
                if (steps != 0) {
                    // While scrubbing the bezel seeks instead, five seconds a detent.
                    if (scrubMs != null) {
                        scrubBy(steps * SCRUB_STEP_MS)
                    } else {
                        actions.volume(steps)
                        volumeNudge++
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                true
            }
            // The crown goes to whichever screen is on top, so this one takes
            // focus back whenever Up Next or More is swiped away.
            .requestFocusOnHierarchyActive()
            .focusable(),
    ) {
        val w = maxWidth
        val h = maxHeight

        MeshBackground(np.colors)

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Below the curved clock.
            Spacer(Modifier.height(h * 0.15f))
            key(np.mediaId) {
                Text(
                    text = np.title,
                    style = WearType.title,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = w * 0.72f)
                        // Twice, then rest: a title that scrolls forever is a
                        // screen that never stops redrawing.
                        .basicMarquee(iterations = 2, initialDelayMillis = 1_200, repeatDelayMillis = 2_000),
                )
            }
            Text(
                text = np.artist,
                style = WearType.subtitle,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = w * 0.64f),
            )

            Spacer(Modifier.height(h * 0.035f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphButton(
                    icon = R.drawable.ic_previous,
                    description = stringResource(R.string.previous),
                    size = w * 0.2f,
                    iconSize = w * 0.105f,
                    onClick = actions.previous,
                )
                Spacer(Modifier.width(w * 0.015f))
                PlayDisc(
                    np = np,
                    artwork = artwork,
                    size = h * 0.40f,
                    scrubMs = scrubMs,
                    onToggle = actions.togglePlay,
                    onScrubStart = {
                        scrubHeld = true
                        scrubMs = np.positionAt(System.currentTimeMillis())
                        scrubActivity++
                    },
                    onScrubBy = ::scrubBy,
                    onScrubRelease = {
                        scrubHeld = false
                        scrubActivity++
                    },
                    onCommit = ::commitScrub,
                )
                Spacer(Modifier.width(w * 0.015f))
                GlyphButton(
                    icon = R.drawable.ic_next,
                    description = stringResource(R.string.next),
                    size = w * 0.2f,
                    iconSize = w * 0.105f,
                    enabled = np.hasNext,
                    onClick = actions.next,
                )
            }

            Spacer(Modifier.height(h * 0.04f))

            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.045f)) {
                val chip = w * 0.155f
                val glyph = w * 0.075f
                // Only where the phone's player shows one: signed in, not a local file.
                if (np.canLike) GlyphButton(
                    icon = if (np.liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    description = stringResource(if (np.liked) R.string.loved else R.string.love),
                    size = chip,
                    iconSize = glyph,
                    tint = if (np.liked) AccentRed else Color.White,
                    background = Color.White.copy(alpha = 0.14f),
                    onClick = actions.toggleLike,
                )
                GlyphButton(
                    icon = R.drawable.ic_queue,
                    description = stringResource(R.string.up_next),
                    size = chip,
                    iconSize = glyph,
                    background = Color.White.copy(alpha = 0.14f),
                    onClick = actions.openQueue,
                )
                GlyphButton(
                    icon = R.drawable.ic_more,
                    description = stringResource(R.string.more),
                    size = chip,
                    iconSize = glyph,
                    background = Color.White.copy(alpha = 0.14f),
                    onClick = actions.openMore,
                )
            }
        }

        VolumeReadout(volume = volume, nudge = volumeNudge, edgeInset = 5.dp)
    }
}

/**
 * The cover, round, with the progress ring outside it and the play glyph on
 * top. While the phone is still finding a stream — play pressed, nothing
 * audible yet — the ring turns instead, so a slow YouTube resolve reads as
 * "working" rather than as a tap that didn't land.
 *
 * Held, it becomes a scrubber, as on Spotify's watch player: drag sideways
 * through the song, or turn the bezel. The ring turns red at the target
 * ([scrubMs]) and the centre reads it out; the caller commits it.
 */
@Composable
private fun PlayDisc(
    np: NowPlaying,
    artwork: ImageBitmap?,
    size: Dp,
    scrubMs: Long?,
    onToggle: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubBy: (Long) -> Unit,
    onScrubRelease: () -> Unit,
    onCommit: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val progress = rememberProgress(np)
    val ambient = LocalAmbient.current.active
    val scrubbing = scrubMs != null
    val buffering = np.playing && !np.advancing
    val spin = if (buffering && !ambient && !scrubbing) {
        rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
            label = "spin",
        )
    } else {
        null
    }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        when {
            scrubbing -> 1.04f
            pressed -> 0.94f
            else -> 1f
        },
        label = "discPress",
    )
    // Read by the gesture blocks, which live across recompositions.
    val latestScrubbing by rememberUpdatedState(scrubbing)
    val latestDuration by rememberUpdatedState(np.durationMs)
    val toggleLabel = stringResource(if (np.playing) R.string.pause else R.string.play)

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val stroke = (if (scrubMs != null) 5.dp else 3.5.dp).toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = Color.White.copy(alpha = 0.22f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke),
                )
                val turning = spin?.value
                val fraction = if (scrubMs != null && np.durationMs > 0) {
                    scrubMs.toFloat() / np.durationMs
                } else {
                    progress.value
                }
                if (turning != null) {
                    drawArc(
                        color = Color.White,
                        startAngle = turning - 90f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                } else if (fraction * 360f > 0.5f) {
                    drawArc(
                        color = if (scrubMs != null) AccentRed else Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                onClick(toggleLabel) {
                    onToggle()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    // The drag detector below owns the hold; this is set only
                    // so a hold is never also taken as a tap when let go.
                    onLongPress = {},
                    onTap = { if (latestScrubbing) onCommit() else onToggle() },
                )
            }
            .pointerInput(Unit) {
                // A full screen's width of drag covers half the song, but never
                // less than a minute, so a short track stays controllable.
                val screenPx = with(density) { 210.dp.toPx() }
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (latestDuration > 0) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onScrubStart()
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (latestScrubbing) {
                            val span = maxOf(latestDuration / 2, 60_000L)
                            onScrubBy((drag.x / screenPx * span).toLong())
                        }
                    },
                    onDragEnd = { if (latestScrubbing) onScrubRelease() },
                    onDragCancel = { if (latestScrubbing) onScrubRelease() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(size * 0.065f)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = when {
                                scrubbing -> 0.62f
                                artwork != null -> 0.30f
                                else -> 0f
                            },
                        ),
                    ),
            )
            if (scrubMs != null) {
                val from = remember(np) { np.positionAt(System.currentTimeMillis()) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTime(scrubMs),
                        style = WearType.title.copy(fontFeatureSettings = "tnum"),
                        color = Color.White,
                    )
                    Text(
                        text = formatDelta(scrubMs - from),
                        style = WearType.caption.copy(fontFeatureSettings = "tnum"),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            } else {
                Icon(
                    painter = painterResource(if (np.playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.38f),
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun formatDelta(ms: Long): String =
    (if (ms < 0) "−" else "+") + formatTime(abs(ms))

/**
 * The playhead, run on from the phone's last reading.
 *
 * Read only in the ring's draw pass, so a tick redraws one arc and recomposes
 * nothing. Twice a second is a pixel or so of ring on a three-minute song —
 * smooth to the eye at a fraction of a frame-clock's cost — and only while the
 * screen is on.
 */
@Composable
private fun rememberProgress(np: NowPlaying): State<Float> {
    val progress = remember { mutableFloatStateOf(np.progressAt(System.currentTimeMillis())) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The always-on screen draws its own ring once a minute; this one rests.
    val ambient = LocalAmbient.current.active
    LaunchedEffect(np, ambient) {
        progress.floatValue = np.progressAt(System.currentTimeMillis())
        if (!np.advancing || ambient) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                progress.floatValue = np.progressAt(System.currentTimeMillis())
                delay(PROGRESS_TICK_MS)
            }
        }
    }
    return progress
}

/** A round glyph button that dips when pressed instead of rippling. */
@Composable
fun GlyphButton(
    @DrawableRes icon: Int,
    description: String,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    background: Color = Color.Transparent,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "glyphPress")
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (pressed) background.copy(alpha = (background.alpha + 0.12f).coerceAtMost(1f)) else background)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The phone's volume as an arc on the right edge, where the crown is — shown
 * while the crown turns and gone a moment after.
 */
@Composable
private fun BoxScope.VolumeReadout(volume: Volume?, nudge: Int, edgeInset: Dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(nudge) {
        if (nudge == 0) return@LaunchedEffect
        visible = true
        delay(VOLUME_SHOWN_MS)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 120 else 400), label = "volumeAlpha")
    val fraction by animateFloatAsState(volume?.fraction ?: 0f, tween(120), label = "volumeLevel")
    if (alpha == 0f || volume == null) return

    Canvas(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        val stroke = 6.dp.toPx()
        val inset = edgeInset.toPx() + stroke / 2f
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        // 0° is three o'clock; the arc is centred on it and fills upwards.
        drawArc(
            color = Color.White.copy(alpha = 0.25f),
            startAngle = VOLUME_ARC / 2f,
            sweepAngle = -VOLUME_ARC,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (fraction > 0f) {
            drawArc(
                color = Color.White,
                startAngle = VOLUME_ARC / 2f,
                sweepAngle = -VOLUME_ARC * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
    Row(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = edgeInset + 16.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_volume),
            contentDescription = stringResource(R.string.volume),
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = (fraction * 100).roundToInt().toString(),
            style = WearType.caption,
            color = Color.White,
        )
    }
}

/** Crown travel per volume step, for crowns that report a stream of small events. */
private const val ROTARY_STEP_PX = 48f
private const val PROGRESS_TICK_MS = 500L
private const val VOLUME_SHOWN_MS = 1_400L
private const val VOLUME_ARC = 64f
private const val SCRUB_STEP_MS = 5_000L
private const val SCRUB_SETTLE_MS = 1_200L
