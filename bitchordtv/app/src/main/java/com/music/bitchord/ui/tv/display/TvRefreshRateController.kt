package com.music.bitchord.ui.tv.display

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TvRefreshRateController {

    private const val TAG = "TvRefreshRateController"

    private val _capabilities = MutableStateFlow(TvDisplayCapabilities())
    val capabilities: StateFlow<TvDisplayCapabilities> = _capabilities.asStateFlow()

    private val _preference = MutableStateFlow(TvRefreshRatePreference.SYSTEM_AUTO)
    val preference: StateFlow<TvRefreshRatePreference> = _preference.asStateFlow()

    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    fun attach(activity: Activity) {
        val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager = dm

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        updateCapabilities(display)

        // Read stored preference
        val stored = AppSettings.tvRefreshRate.value
        val pref = TvRefreshRatePreference.fromString(stored)
        _preference.value = pref
        applyToWindow(activity, pref)

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == display?.displayId) {
                    updateCapabilities(display)
                    applyToWindow(activity, _preference.value)
                }
            }
        }
        displayListener = listener
        dm?.registerDisplayListener(listener, null)
    }

    fun detach() {
        displayListener?.let { displayManager?.unregisterDisplayListener(it) }
        displayListener = null
        displayManager = null
    }

    fun updateCapabilities(display: Display?) {
        val caps = TvDisplayCapabilitiesReader.readCapabilities(display)
        _capabilities.value = caps
        Log.d(TAG, "Active TV Display: ${caps.currentPhysicalWidth}x${caps.currentPhysicalHeight} @ ${caps.actualRefreshRateHz} Hz (120Hz Supported: ${caps.is120HzSupported})")
    }

    fun setPreference(activity: Activity, preference: TvRefreshRatePreference) {
        _preference.value = preference
        AppSettings.setTvRefreshRate(preference.name)
        applyToWindow(activity, preference)
    }

    private fun applyToWindow(activity: Activity, pref: TvRefreshRatePreference) {
        try {
            val window = activity.window ?: return
            val lp = window.attributes ?: WindowManager.LayoutParams()
            val caps = _capabilities.value

            // Find best matching mode or scan all supported modes for >=119Hz
            val targetModeId = when (pref) {
                TvRefreshRatePreference.SYSTEM_AUTO -> 0
                TvRefreshRatePreference.SMOOTH_60 -> caps.compatible60ModeId ?: 0
                TvRefreshRatePreference.ULTRA_120 -> {
                    caps.compatible120ModeId
                        ?: caps.supportedModes.firstOrNull { it.refreshRate >= 119.0f }?.id
                        ?: 0
                }
            }

            lp.preferredDisplayModeId = targetModeId

            // Legacy direct refresh rate override (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                when (pref) {
                    TvRefreshRatePreference.ULTRA_120 -> {
                        @Suppress("DEPRECATION")
                        lp.preferredRefreshRate = 120.0f
                    }
                    TvRefreshRatePreference.SMOOTH_60 -> {
                        @Suppress("DEPRECATION")
                        lp.preferredRefreshRate = 60.0f
                    }
                    TvRefreshRatePreference.SYSTEM_AUTO -> {
                        @Suppress("DEPRECATION")
                        lp.preferredRefreshRate = 0.0f
                    }
                }
            }

            // Modern Android R (API 30+) display refresh rate bounds via reflection for universal SDK compatibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val (minR, maxR) = when (pref) {
                    TvRefreshRatePreference.ULTRA_120 -> 120.0f to 120.0f
                    TvRefreshRatePreference.SMOOTH_60 -> 60.0f to 60.0f
                    TvRefreshRatePreference.SYSTEM_AUTO -> 0.0f to 0.0f
                }
                try {
                    lp.javaClass.getField("preferredMinDisplayRefreshRate").setFloat(lp, minR)
                    lp.javaClass.getField("preferredMaxDisplayRefreshRate").setFloat(lp, maxR)
                } catch (_: Throwable) {}
            }

            window.attributes = lp

            // Android S (API 31+) surface frame rate request for ultra-smooth 120fps Compose rendering
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val frameRate = when (pref) {
                        TvRefreshRatePreference.ULTRA_120 -> 120.0f
                        TvRefreshRatePreference.SMOOTH_60 -> 60.0f
                        TvRefreshRatePreference.SYSTEM_AUTO -> 0.0f
                    }
                    if (frameRate > 0f) {
                        val rootCtrl = window.decorView.rootSurfaceControl
                        if (rootCtrl != null) {
                            val setFrameRateMethod = rootCtrl.javaClass.methods.firstOrNull {
                                it.name == "setFrameRate" && it.parameterTypes.size == 3
                            }
                            setFrameRateMethod?.invoke(rootCtrl, frameRate, 0, 1)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "RootSurfaceControl.setFrameRate failed", t)
                }
            }

            Log.d(TAG, "Applied display settings: preferredDisplayModeId=$targetModeId, pref=$pref")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply display mode to window", e)
        }
    }
}
