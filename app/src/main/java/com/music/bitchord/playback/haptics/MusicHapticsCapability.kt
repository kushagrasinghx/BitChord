package com.music.bitchord.playback.haptics

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Hardware and form-factor gate for Music Haptics.
 *
 * Ensures Music Haptics is **only** exposed on handheld mobile devices equipped
 * with an actual physical vibration/haptic actuator — never on Android TV,
 * Google TV, Wear OS, Android Auto, Chromebooks/PCs, or tablets/devices without
 * a vibrator motor.
 */
object MusicHapticsCapability {

    @Volatile
    private var cachedResult: Boolean? = null

    fun isSupported(context: Context): Boolean {
        cachedResult?.let { return it }
        val app = context.applicationContext
        val supported = computeSupported(app)
        cachedResult = supported
        return supported
    }

    @Suppress("DEPRECATION")
    private fun computeSupported(context: Context): Boolean {
        val pm = context.packageManager

        // Exclude TVs, watches, cars, and desktop/PC form factors
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return false
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return false
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        ) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            pm.hasSystemFeature(PackageManager.FEATURE_PC)
        ) return false

        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val modeType = uiModeManager?.currentModeType ?: Configuration.UI_MODE_TYPE_NORMAL
        if (modeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            modeType == Configuration.UI_MODE_TYPE_CAR ||
            modeType == Configuration.UI_MODE_TYPE_WATCH ||
            modeType == Configuration.UI_MODE_TYPE_DESK ||
            modeType == Configuration.UI_MODE_TYPE_APPLIANCE
        ) {
            return false
        }

        // Require a real physical vibration/haptic actuator
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        return vibrator != null && vibrator.hasVibrator()
    }
}
