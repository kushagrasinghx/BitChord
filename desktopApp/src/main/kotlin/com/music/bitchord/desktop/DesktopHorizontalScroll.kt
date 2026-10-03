package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent

/** Two-finger horizontal scrolling, which otherwise never reaches the app. */
internal object DesktopHorizontalScroll {

    /** -1 for a swipe left, +1 for a swipe right. */
    private val _ticks = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val ticks: SharedFlow<Int> = _ticks

    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        runCatching {
            Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
                val mouse = event as? MouseEvent ?: return@addAWTEventListener
                if (mouse.id != MouseEvent.MOUSE_PRESSED) return@addAWTEventListener
                when (mouse.button) {
                    HORIZONTAL_LEFT -> _ticks.tryEmit(-1)
                    HORIZONTAL_RIGHT -> _ticks.tryEmit(1)
                }
            }, AWTEvent.MOUSE_EVENT_MASK)
        }
    }

    /** X11 button 6, renumbered by the JDK. */
    private const val HORIZONTAL_LEFT = 4

    /** X11 button 7, likewise. */
    private const val HORIZONTAL_RIGHT = 5
}
