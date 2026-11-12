package com.music.bitchord.ui.tv

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Universal device capability and form-factor classifier.
 *
 * Distinguishes Android TV / Google TV / Fire TV devices from handhelds/tablets
 * using standard Android framework APIs. Also exposes low-RAM and hardware capability
 * detection so the UI and playback engines can scale their memory, graphics, and
 * animations gracefully without hardcoded OEM allowlists.
 */
object DeviceType {

    /**
     * Determines whether the current running environment is an Android TV / Google TV /
     * Fire OS TV device.
     */
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television")
    }

    /**
     * Returns true if the device has a physical touchscreen.
     */
    fun hasTouchScreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /**
     * Returns true if the device is a memory-constrained (low-RAM) device.
     * Used to throttle mesh gradient blur, video canvas playback, and cache sizes.
     */
    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isLowRamDevice
    }

    /**
     * Returns true if the device has a working microphone hardware feature.
     */
    fun hasMicrophone(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }
}
