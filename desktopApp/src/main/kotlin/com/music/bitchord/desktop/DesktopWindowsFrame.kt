package com.music.bitchord.desktop

import kotlinx.coroutines.delay

/**
 * Windows' native frame kept underneath BitChord's Compose-drawn traffic-light controls.
 *
 * The Win32 window remains an overlapped window, so DWM owns its shadow, transitions, taskbar
 * maximization and resize border. Only the caption drawing is replaced by the app. This object is
 * never loaded on Linux, where the window manager continues to provide the ordinary decoration.
 */
internal object DesktopWindowsFrame {

    private val available: Boolean by lazy {
        DesktopPlatform.isWindows &&
            runCatching { DesktopAnalysisRuntime.loadNative(LIBRARY) }
                .onFailure { DesktopTrackLog.log("native Windows frame unavailable: ${it.message}") }
                .isSuccess
    }

    @Volatile
    private var installed = false

    suspend fun install(title: String): Boolean {
        if (!available) return false
        repeat(INSTALL_ATTEMPTS) {
            val ready = runCatching { nativeInstall(title) }.getOrElse {
                DesktopTrackLog.log("native Windows frame install failed: ${it.message}")
                false
            }
            if (ready) {
                installed = true
                return true
            }
            delay(INSTALL_RETRY_MILLIS)
        }
        DesktopTrackLog.log("native Windows frame could not find the visible app window")
        return false
    }

    fun minimize(): Boolean = installed && runCatching { nativeMinimize() }.getOrDefault(false)

    fun toggleMaximize(): Boolean =
        installed && runCatching { nativeToggleMaximize() }.getOrDefault(false)

    @JvmStatic
    private external fun nativeInstall(title: String): Boolean

    @JvmStatic
    private external fun nativeMinimize(): Boolean

    @JvmStatic
    private external fun nativeToggleMaximize(): Boolean

    private const val LIBRARY = "bitchord_window"
    private const val INSTALL_ATTEMPTS = 20
    private const val INSTALL_RETRY_MILLIS = 100L
}
