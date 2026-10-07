package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The system material behind the window. */
internal enum class DesktopBackdrop(
    /** What [DesktopWindowsFrame.setBackdrop] takes: DWM's DWMSBT_* value, or 0 for none. */
    val nativeKind: Int,
    val label: String,
) {
    OFF(0, "Off"),
    MICA(2, "Mica"),
    ACRYLIC(3, "Acrylic"),
}

/**
 * Windows 11's Mica and Acrylic, drawn by DWM behind the window.
 *
 * The window itself is created transparent whenever the material is possible at all — transparency
 * is fixed when the window is made — so switching between the three here is live, with no restart.
 * Compose decides whether the material also reaches the app background via [appBackground].
 */
internal object DesktopWindowBackdrop {

    internal const val KEY = "window_backdrop"

    /**
     * Whether this window can carry a material: Windows 11 and the native frame. Decided once, at
     * start, because it decides whether the window is created transparent.
     */
    val available: Boolean =
        (DesktopPlatform.isWindows && DesktopPlatform.drawsOwnWindowFrame &&
            System.getProperty("os.name").orEmpty().contains("11")) ||
        DesktopPlatform.isMac

    private val _selected = MutableStateFlow(
        runCatching { DesktopBackdrop.valueOf(DesktopPersistence().string(KEY, DesktopBackdrop.MICA.name)) }
            .getOrDefault(DesktopBackdrop.MICA),
    )

    /** What Settings shows and writes. */
    val selected: StateFlow<DesktopBackdrop> = _selected

    private val _appBackground = MutableStateFlow(
        DesktopPersistence().boolean(KEY_APP_BACKGROUND, true),
    )

    /** Whether the selected material replaces the app's solid gray page background too. */
    val appBackground: StateFlow<Boolean> = _appBackground

    private val _active = MutableStateFlow(DesktopBackdrop.OFF)

    /**
     * The material actually behind the window right now. Off until the native frame is installed,
     * and off if Windows or macOS turned the request down: the chrome only goes translucent over
     * a material that is really there.
     */
    val active: StateFlow<DesktopBackdrop> = _active

    fun set(value: DesktopBackdrop) {
        DesktopPersistence().saveString(KEY, value.name)
        _selected.value = value
        apply()
    }

    fun setAppBackground(value: Boolean) {
        DesktopPersistence().saveBoolean(KEY_APP_BACKGROUND, value)
        _appBackground.value = value
    }

    /** Asks the native frame for the selected material; called once in place, and on every change. */
    fun apply() {
        if (!available) return
        val wanted = _selected.value
        val applied = when {
            DesktopPlatform.isWindows -> DesktopWindowsFrame.setBackdrop(wanted.nativeKind)
            DesktopPlatform.isMac -> DesktopMacFrame.setBackdrop(wanted.nativeKind)
            else -> false
        }
        _active.value = if (applied) wanted else DesktopBackdrop.OFF
        if (!applied && wanted != DesktopBackdrop.OFF) {
            DesktopTrackLog.log("window backdrop: system declined ${wanted.label}")
        }
    }

    internal const val KEY_APP_BACKGROUND = "window_backdrop_app_background"
}
