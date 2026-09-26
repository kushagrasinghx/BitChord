package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.util.Locale

private val startNanos = System.nanoTime()

internal actual fun uptimeMillis(): Long = (System.nanoTime() - startNanos) / 1_000_000L

internal actual fun elapsedRealtimeMillis(): Long = (System.nanoTime() - startNanos) / 1_000_000L

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
