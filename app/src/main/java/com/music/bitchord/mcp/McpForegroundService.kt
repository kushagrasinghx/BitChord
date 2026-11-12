package com.music.bitchord.mcp

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import com.music.bitchord.R

/** User-enabled MCP lifetime owner; no wake lock, boot receiver or background FGS launch. */
class McpForegroundService : Service() {
    // McpServer no longer installs onChanged; this service is its sole lifecycle owner.
    private val settingsChanged: () -> Unit = { onSettingsChanged() }
    private fun onSettingsChanged() {
        // Preferences may notify off-main; serialize Android service lifecycle on main.
        android.os.Handler(mainLooper).post {
            if (McpSettings.onChanged === settingsChanged) {
                if (McpSettings.enabled) McpServer.start(this) else {
                    McpServer.stop()
                    stopSelf()
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        McpSettings.init(this)
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "MCP access", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps user-enabled AI access available while using another app." })
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle("BitChord MCP")
                .setContentText("AI access enabled. Turn off MCP in BitChord settings to stop.")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .apply {
                    if (launch != null) setContentIntent(PendingIntent.getActivity(
                        this@McpForegroundService, 0, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ))
                }.build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            McpSettings.onChanged = settingsChanged
            if (McpSettings.enabled) McpServer.start(this) else stopSelf()
        } catch (_: RuntimeException) {
            Toast.makeText(this, "Unable to keep MCP running. Reopen BitChord and try again.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!McpSettings.enabled) stopSelf(startId) else McpServer.start(this)
        // No unattended restart: the next visible activity can retry safely.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (McpSettings.onChanged === settingsChanged) McpSettings.onChanged = null
        McpServer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "mcp_access"
        private const val NOTIFICATION_ID = 8765

        /** Call only from a resumed activity / visible settings interaction. */
        fun syncFromForeground(context: Context) {
            McpSettings.init(context)
            val intent = Intent(context, McpForegroundService::class.java)
            if (!McpSettings.enabled) {
                context.stopService(intent)
                McpServer.stop()
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (_: RuntimeException) {
                McpServer.stop()
                Toast.makeText(context, "Android could not start MCP background access. Reopen BitChord and try again.", Toast.LENGTH_LONG).show()
            }
        }

        fun install(application: Application) {
            McpSettings.init(application)
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = syncFromForeground(activity)
                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }
    }
}
