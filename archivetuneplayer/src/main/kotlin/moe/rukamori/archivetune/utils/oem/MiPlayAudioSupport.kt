package moe.rukamori.archivetune.utils.oem

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object MiPlayAudioSupport {
    private const val ACTION_MIPLAY_DETAIL = "miui.intent.action.ACTIVITY_MIPLAY_DETAIL"
    private const val AUDIO_RECORD_CLASS = "miui.media.MiuiAudioPlaybackRecorder"
    private const val PACKAGE_NAME = "com.milink.service"
    private const val SERVICE_NAME = "com.miui.miplay.audio.service.CoreService"
    private const val WHITE_TARGET = "com.milink.service:hide_foreground"

    fun supportMiPlay(context: Context): Boolean {
        try {
            context.packageManager.getServiceInfo(
                ComponentName(PACKAGE_NAME, SERVICE_NAME),
                PackageManager.MATCH_ALL,
            )

            context.classLoader.loadClass(AUDIO_RECORD_CLASS)

            val isInternationalBuild = isInternationalBuild()
            val systemUIReady = systemUIReady(context)
            val notificationReady = notificationReady(context)
            return !isInternationalBuild && systemUIReady && notificationReady
        } catch (_: Exception) {
            return false
        }
    }

    private fun isInternationalBuild(): Boolean =
        try {
            val clazz = Class.forName("miui.os.Build")
            val field = clazz.getField("IS_INTERNATIONAL_BUILD")
            field.isAccessible = true
            field.getBoolean(null)
        } catch (_: Exception) {
            false
        }

    private fun systemUIReady(context: Context): Boolean {
        val intent =
            Intent(ACTION_MIPLAY_DETAIL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        return try {
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun notificationReady(context: Context): Boolean =
        try {
            val systemUiAppInfo =
                context.packageManager.getApplicationInfo(
                    "com.android.systemui",
                    0,
                )
            val resources = context.packageManager.getResourcesForApplication(systemUiAppInfo)
            val identifier =
                @SuppressLint("DiscouragedApi")
                resources.getIdentifier(
                    "system_foreground_notification_whitelist",
                    "array",
                    "com.android.systemui",
                )

            if (identifier > 0) {
                val whiteList = resources.getStringArray(identifier)
                val contains = whiteList.contains(WHITE_TARGET)
                contains
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
}
