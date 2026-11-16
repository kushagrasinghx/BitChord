package com.music.bitchord.desktop

import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.awt.desktop.AppReopenedListener
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the window is on screen, and what closing it means. */
internal object DesktopWindowVisibility {

    private val _visible = MutableStateFlow(true)

    /** What the window's own `visible` is driven from. */
    val visible: StateFlow<Boolean> = _visible

    /** Whether a close should hide rather than quit. */
    @Volatile
    var keepRunningWhenClosed: Boolean = false

    private var windowRef: WeakReference<Window>? = null
    private var isListenerInstalled = false

    /** Attaches the active Compose window so show() can bring it to the foreground. */
    fun attachWindow(window: Window) {
        windowRef = WeakReference(window)
    }

    /**
     * Installs system-level reopen hooks (e.g. macOS Dock click when window is closed).
     */
    fun install() {
        if (isListenerInstalled) return
        isListenerInstalled = true

        if (DesktopPlatform.isMac && Desktop.isDesktopSupported()) {
            runCatching {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
                    desktop.addAppEventListener(AppReopenedListener {
                        EventQueue.invokeLater {
                            DesktopTrackLog.log("AppReopenedEvent received from macOS Dock")
                            show()
                        }
                    })
                    DesktopTrackLog.log("DesktopWindowVisibility: AppReopenedListener registered")
                }
            }.onFailure {
                DesktopTrackLog.log("DesktopWindowVisibility: failed to register AppReopenedListener: ${it.message}")
            }
        }
    }

    /** Brings the window back — from the Dock, tray menu, or the icon itself. */
    fun show() {
        _visible.value = true
        EventQueue.invokeLater {
            val win = windowRef?.get()
            if (win != null) {
                if (win is Frame && (win.extendedState and Frame.ICONIFIED) != 0) {
                    win.extendedState = win.extendedState and Frame.ICONIFIED.inv()
                }
                win.isVisible = true
                win.toFront()
                win.requestFocus()
            }
            if (DesktopPlatform.isMac) {
                runCatching {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                        Desktop.getDesktop().requestForeground(true)
                    }
                }
                DesktopMacFrame.activateApp()
            }
        }
    }

    /**
     * Handles the window's close request.
     * @return true when the caller should end the application; false when the
     */
    fun onCloseRequest(): Boolean {
        if (!keepRunningWhenClosed) return true
        // A window put away full-screen comes back full-screen with nothing on it, which reads as
        // the app having broken rather than as it having been hidden.
        DesktopWindowMode.exit()
        _visible.value = false
        DesktopTrackLog.log("window closed to the tray; playback continues")
        return false
    }
}
