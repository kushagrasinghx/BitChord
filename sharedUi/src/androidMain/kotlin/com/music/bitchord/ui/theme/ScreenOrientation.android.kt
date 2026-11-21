package com.music.bitchord.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class ActivityOrientation(private val activity: Activity) : ScreenOrientation {
    /** What the activity asked for before the first nudge, given back at the end. */
    private var original: Int? = null
    private var listener: OrientationEventListener? = null

    override fun landscape() = request(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, landscape = true)

    override fun portrait() = request(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, landscape = false)

    private fun request(orientation: Int, landscape: Boolean) {
        if (original == null) original = activity.requestedOrientation
        activity.requestedOrientation = orientation
        listener?.disable()
        // With auto-rotate off the sensor would only turn it straight back:
        // the request then holds until this is let go of.
        if (!autoRotate()) return
        // Turned sideways, the screen stays sideways until the phone is held
        // upright again. Handing it back to the sensor the moment the phone
        // was turned instead raced the system's own reading of it, which
        // trails this one: for that moment the system still saw it upright,
        // and the screen flicked to portrait and back.
        var turned = false
        listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val upright = degrees <= 25 || degrees >= 335
                val release = if (landscape) {
                    if (degrees in 60..120 || degrees in 240..300) turned = true
                    turned && upright
                } else {
                    // Held the way it was turned: the sensor has it from here.
                    upright
                }
                if (release) {
                    disable()
                    listener = null
                    original?.let { activity.requestedOrientation = it }
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() else listener = null }
    }

    private fun autoRotate(): Boolean = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
    }.getOrDefault(true)

    fun release() {
        listener?.disable()
        listener = null
        original?.let { activity.requestedOrientation = it }
        original = null
    }
}

private object NoOrientation : ScreenOrientation {
    override fun landscape() = Unit
    override fun portrait() = Unit
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun rememberScreenOrientation(): ScreenOrientation {
    val activity = LocalContext.current.findActivity() ?: return NoOrientation
    val orientation = remember(activity) { ActivityOrientation(activity) }
    DisposableEffect(orientation) { onDispose { orientation.release() } }
    return orientation
}
