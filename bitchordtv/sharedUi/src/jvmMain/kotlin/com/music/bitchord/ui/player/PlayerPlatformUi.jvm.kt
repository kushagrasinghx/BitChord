package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkRect

private val startNanos = System.nanoTime()

internal actual fun uptimeMillis(): Long = (System.nanoTime() - startNanos) / 1_000_000L

// Skia blurs at every size; there is no platform floor to check.
internal actual val renderEffectBlurSupported: Boolean = true

// A desktop display's idle timeout is the user's own business.
@Composable
internal actual fun KeepScreenOn(enabled: Boolean) = Unit

@Composable
internal actual fun appLanguageTag(): String = Locale.getDefault().toLanguageTag()

@Composable
internal actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { PlayerBack.Entry { current.value() } }
    DisposableEffect(enabled) {
        if (enabled) PlayerBack.push(entry)
        onDispose { PlayerBack.remove(entry) }
    }
}

/**
 * The desktop's back stack for the player's layers — the lyrics, the queue, a
 * drawer, a dialog. The window routes Escape here; the newest enabled layer
 * takes it, the same order Android's back dispatcher gives them.
 */
object PlayerBack {
    class Entry(val onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    internal fun push(entry: Entry) {
        synchronized(entries) {
            entries.remove(entry)
            entries.add(entry)
        }
    }

    internal fun remove(entry: Entry) {
        synchronized(entries) { entries.remove(entry) }
    }

    /** Back, from the window. False when no layer of the player wanted it. */
    fun dispatch(): Boolean {
        val top = synchronized(entries) { entries.lastOrNull() } ?: return false
        top.onBack()
        return true
    }
}

/** Linear, so a layer moved by a fraction of a pixel lands between two. */
private val subPixelSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

/**
 * The whole pixels go through the canvas as usual; the fraction left over goes
 * through a layer resampled on its way back, which is the one move Skia will
 * make by less than a pixel vertically. A layer only while there is a fraction
 * worth moving: a word at rest, or exactly on a pixel, costs what it did.
 */
/**
 * False, and it has to be: a list here is scrolled by the wheel, and a wheel
 * tick is not a drag. Were this true, scrolling the rows of a drawer would push
 * the drawer itself down — a mouse-wheel gesture is [androidx.compose.ui.input
 * .nestedscroll.NestedScrollSource.UserInput] exactly like a finger is, and
 * nothing in the chain can tell them apart — until the drag crossed the dismiss
 * threshold and closed it, taking the reader's scroll with it. The drawer still
 * has its own gesture here: a drag with the button held, which is also the only
 * one a desktop user expects to move a sheet.
 */
internal actual val drawerFollowsListScroll: Boolean get() = false

internal actual fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    val whole = floor(dy)
    val fraction = dy - whole
    if (fraction < 0.02f || fraction > 0.98f) {
        clipRect(left = left, top = top, right = right, bottom = bottom) {
            translate(top = round(dy)) { block() }
        }
        return
    }
    val canvas = drawContext.canvas.nativeCanvas
    val paint = Paint().apply {
        imageFilter = ImageFilter.makeMatrixTransform(
            Matrix33.makeTranslate(0f, fraction),
            subPixelSampling,
            null,
        )
    }
    // Room for the pixel the fraction reaches into below the clip.
    canvas.saveLayer(SkRect.makeLTRB(left, top - 1f, right, bottom + 1f), paint)
    // Clipped where it will be *before* the layer moves it, so it lands
    // exactly on the rectangle asked for.
    clipRect(left = left, top = top - fraction, right = right, bottom = bottom - fraction) {
        translate(top = whole) { block() }
    }
    canvas.restore()
    paint.close()
}
