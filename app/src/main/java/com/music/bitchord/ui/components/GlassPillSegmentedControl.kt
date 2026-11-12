package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import com.music.bitchord.ui.graphics.fadingEdges
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The app's segmented control — Local Music's Songs / Artists / Albums tabs,
 * Search's Songs / Library / Addons switcher and its filters, the choices in
 * Settings — drawn as Liquid Glass, the way the bottom bar is. Installed
 * through [LocalPillSegmentedControlRenderer] while the Liquid Glass setting is
 * on and the device can draw it; with it off the shared flat control is used.
 *
 * It borrows the bar's one signature move on a small scale. At rest the
 * selected segment is a flat lift, exactly as the selected tab in the bar is.
 * Choose another and the lift becomes a piece of glass for the length of the
 * trip: it refracts the segment labels it passes over, wears the glass rim and
 * a contact shadow, glides across on a spring and, once it lands, sheds the
 * material back to the flat lift. See
 * [com.music.bitchord.ui.components.floatingtabbar.GlassSelectionPill] for the
 * bar's far fuller version of the same idea.
 *
 * What the lens refracts is *this control's own* recorded layer — its tint,
 * lift and labels — and not [LocalAppBackdrop]. These controls sit inside the
 * page, and the page is what [LocalAppBackdrop] records; a surface in it that
 * sampled that layer would be reading a layer it is itself being drawn into.
 * Recorded locally, the glass has nothing to loop on.
 */
object GlassPillSegmentedControlRenderer : PillSegmentedControlRenderer {
    @Composable
    override fun Render(
        options: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier,
        enabled: Boolean,
    ) {
        GlassSegmentedControl(options, selectedIndex, onSelect, modifier, enabled)
    }
}

@Composable
private fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    if (options.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val labelColor = glassContentColor()
    val indicator = glassIndicatorColor()
    val trackTint = if (light) TRACK_TINT_LIGHT else TRACK_TINT_DARK
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // "Reduce animation" lands every move at once with no glass on the way, and
    // "reduce dynamic blur" is the promise that nothing refracts: either way
    // the selected segment is the flat lift and nothing more.
    val glassMoves = !reduceAnimation && !reduceDynamicBlur
    val selected = selectedIndex.takeIf { it in options.indices }
    val recorded = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(TrackShape)
            // Drawn after everything inside it, so the rim lies over the tint
            // and the labels rather than under them.
            .liquidGlassRim(TrackShape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, TrackShape),
    ) {
        val inset = with(density) { TRACK_PADDING.toPx() }
        val labelPadding = with(density) { LABEL_PADDING.toPx() }
        val viewport = constraints.maxWidth.toFloat()
        val room = viewport - inset * 2f
        val natural = remember(options, labelStyle, measurer, density) {
            options.map { label ->
                measurer.measure(label, labelStyle, maxLines = 1, softWrap = false).size.width + labelPadding * 2f
            }
        }
        val needed = natural.sum()
        val scrolls = needed > room
        val widths = when {
            scrolls -> natural
            natural.max() * options.size <= room -> List(options.size) { room / options.size }
            else -> natural.map { it + (room - needed) / options.size }
        }
        val starts = widths.runningFold(inset) { start, width -> start + width }
        val contentWidth = if (scrolls) needed + inset * 2f else viewport

        val motion: AnimationSpec<Float> = if (reduceAnimation) snap() else spring(PILL_DAMPING, Spring.StiffnessMediumLow)
        val targetStart = selected?.let { starts[it] } ?: 0f
        val targetWidth = selected?.let { widths[it] } ?: 0f

        // The lift's place, kept in Animatables rather than animate-as-state
        // values so a finger can take it over: a drag [snapTo]s them where it
        // is, and on letting go the same spring that carries a tap carries it
        // the rest of the way to wherever it was dropped.
        val startAnim = remember { Animatable(targetStart) }
        val widthAnim = remember { Animatable(targetWidth) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(targetStart, targetWidth, dragging) {
            if (dragging) return@LaunchedEffect
            launch { startAnim.animateTo(targetStart, motion) }
            widthAnim.animateTo(targetWidth, motion)
        }

        // Whether the lift is on its way somewhere or held by a finger.
        // Derived, so the control recomposes when this flips and not on every
        // frame of the slide.
        val travelling by remember(targetStart, targetWidth) {
            derivedStateOf {
                dragging || abs(startAnim.value - targetStart) > 1f || abs(widthAnim.value - targetWidth) > 1f
            }
        }
        // The glass comes on fast and leaves slowly, so the landing is a
        // settling rather than a cut.
        val material by animateFloatAsState(
            targetValue = if (travelling && glassMoves) 1f else 0f,
            animationSpec = tween(durationMillis = if (travelling) 90 else 240),
            label = "segmentGlass",
        )
        val glassOn = material > 0.01f
        val scope = rememberCoroutineScope()
        // The segment under the finger last time a drag ticked, so a boundary
        // crossed is felt once.
        val lastTick = remember { intArrayOf(-1) }

        val scroll = rememberScrollState()
        // The selection centred, as far as the ends allow: there at once the
        // first time, slid there after.
        val placed = remember { booleanArrayOf(false) }
        LaunchedEffect(selected, natural, viewport) {
            if (!scrolls || selected == null) return@LaunchedEffect
            val max = snapshotFlow { scroll.maxValue }.first { it > 0 }
            val centred = (starts[selected] + widths[selected] / 2f - viewport / 2f).roundToInt().coerceIn(0, max)
            if (placed[0] && !reduceAnimation) scroll.animateScrollTo(centred) else scroll.scrollTo(centred)
            placed[0] = true
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (scrolls) {
                        val fadePx = with(density) { EDGE_FADE.toPx() }
                        Modifier
                            .fadingEdges(
                                EDGE_FADE,
                                start = { scroll.value / fadePx },
                                end = { (scroll.maxValue - scroll.value) / fadePx },
                            )
                            .horizontalScroll(scroll)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                Modifier
                    .width(with(density) { contentWidth.toDp() })
                    .then(
                        // Picking the lift up and carrying it, as on the bar. Only
                        // where every segment fits: a strip that scrolls needs the
                        // same drag for itself.
                        if (!scrolls && enabled) {
                            Modifier.pointerInput(starts, widths, selected) {
                                val first = starts.first()
                                val last = starts.last()
                                fun hover(x: Float) = starts.indexOfLast { it <= x }.coerceIn(0, options.lastIndex)
                                fun follow(x: Float) {
                                    val at = x.coerceIn(first, last)
                                    val i = hover(at)
                                    val w = widths[i]
                                    val left = (at - w / 2f).coerceIn(first, last - w)
                                    scope.launch {
                                        startAnim.snapTo(left)
                                        widthAnim.snapTo(w)
                                    }
                                    if (i != lastTick[0]) {
                                        if (lastTick[0] != -1) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastTick[0] = i
                                    }
                                }
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        dragging = true
                                        lastTick[0] = selected ?: -1
                                        follow(offset.x)
                                    },
                                    onDragEnd = {
                                        val i = lastTick[0]
                                        dragging = false
                                        lastTick[0] = -1
                                        if (i in options.indices && i != selected) onSelect(i)
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        lastTick[0] = -1
                                    },
                                ) { change, _ ->
                                    change.consume()
                                    follow(change.position.x)
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                // Everything the lens can see: the track's tint, the flat lift
                // and the labels. Recorded on its own so the glass above it
                // refracts this and never the page it sits in.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .layerBackdrop(recorded)
                        .background(trackTint),
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = TRACK_PADDING)) {
                        if (selected != null) {
                            // The flat lift. It thins out as the glass comes on
                            // — clear while lifted, as the bar's is — and is
                            // back in full when the glass has gone.
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .pillPlacement(pillStart = { startAnim.value }, pillWidth = { widthAnim.value })
                                    .clip(PillShape)
                                    .drawBehind {
                                        drawRect(indicator.copy(alpha = indicator.alpha * (1f - material)))
                                    },
                            )
                        }
                        options.forEachIndexed { index, label ->
                            Box(
                                modifier = Modifier
                                    .atX(starts[index])
                                    .width(with(density) { widths[index].toDp() })
                                    .clickable(enabled = enabled, interactionSource = null, indication = null) {
                                        if (index != selected) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelect(index)
                                        }
                                    }
                                    .padding(vertical = LABEL_VERTICAL_PADDING),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = labelStyle,
                                    color = labelColor,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                // The glass lift, over the labels it refracts. Composed only
                // while there is glass to show: at rest it would redraw the
                // identical picture the recorded layer already is.
                if (selected != null && glassOn) {
                    Box(Modifier.matchParentSize().padding(vertical = TRACK_PADDING)) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .pillPlacement(pillStart = { startAnim.value }, pillWidth = { widthAnim.value })
                                .drawBackdrop(
                                    backdrop = recorded,
                                    shape = { PillShape },
                                    effects = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            lens(
                                                refractionHeight = LENS_HEIGHT.toPx() * material,
                                                refractionAmount = LENS_AMOUNT.toPx() * material,
                                                chromaticAberration = true,
                                            )
                                        }
                                    },
                                    highlight = { Highlight.Default.copy(alpha = material.coerceIn(0f, 1f)) },
                                    shadow = { LIFT_SHADOW.copy(alpha = material.coerceIn(0f, 1f)) },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Places the lift [pillStart] pixels along and [pillWidth] wide, inside a
 * parent it matches the height of. Both are read on layout rather than in
 * composition, so a frame of the slide is a relayout and recomposes nothing.
 */
private fun Modifier.pillPlacement(pillStart: () -> Float, pillWidth: () -> Float): Modifier =
    layout { measurable, constraints ->
        val width = pillWidth().roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(pillStart().roundToInt(), 0)
        }
    }

/** Placed [x] pixels along. */
private fun Modifier.atX(x: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(IntOffset(x.roundToInt(), 0)) }
}

private val TrackShape = SquirclePillShape()
private val PillShape = SquirclePillShape()

/**
 * The track's tint. On dark a faint white veil; on light Apple's grey. Visible
 * over a flat page where the bar's dark tint over its blurred backdrop would
 * vanish — there is no backdrop under these for the glass to be seen against.
 */
private val TRACK_TINT_DARK = Color.White.copy(alpha = 0.10f)
private val TRACK_TINT_LIGHT = Color(0xFF767680).copy(alpha = 0.16f)

private val TRACK_PADDING = 4.dp
private val LABEL_PADDING = 18.dp
private val LABEL_VERTICAL_PADDING = 9.dp
private val EDGE_FADE = 28.dp
private const val PILL_DAMPING = 0.8f

/** The bar's lens, scaled to a pill under half the height. */
private val LENS_HEIGHT = 8.dp
private val LENS_AMOUNT = 9.dp

private val LIFT_SHADOW = Shadow(
    radius = 10.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.28f),
)
