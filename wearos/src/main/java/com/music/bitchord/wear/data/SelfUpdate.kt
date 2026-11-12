package com.music.bitchord.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.music.bitchord.wear.R
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import com.music.bitchord.wear.WearProtocol as P

/**
 * What the phone's "BitChord for Wear OS" settings need from this side: which
 * build is installed, and a way to put a newer one on without a computer.
 *
 * The phone carries the watch APK built alongside it and sends it over a
 * Wearable channel; this hands it to the platform installer. Android asks the
 * wearer to confirm the first time — the app was installed by ADB, so it is
 * not yet the installer of record — and a background app may not open that
 * prompt itself, so it is offered as a notification to tap. Once an update has
 * gone through here, BitChord *is* the installer of record and later updates
 * need no tap at all.
 */
object SelfUpdate {

    private const val TAG = "BitChordWear"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2

    /** Answers the phone's [P.PATH_WATCH_INFO_REQUEST]. Blocking: a listener thread. */
    fun publishInfo(context: Context) {
        val app = context.applicationContext
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull() ?: return
        val request = PutDataMapRequest.create(P.PATH_WATCH_INFO).apply {
            dataMap.putLong(P.KEY_VERSION_CODE, info.longVersionCode)
            dataMap.putString(P.KEY_VERSION_NAME, info.versionName.orEmpty())
            dataMap.putString(P.KEY_APK_SHA256, apkHash(app, info.lastUpdateTime))
            // Always a change, so asking twice still answers.
            dataMap.putLong(P.KEY_POSITION_AT, System.currentTimeMillis())
        }
        runCatching {
            Tasks.await(Wearable.getDataClient(app).putDataItem(request.asPutDataRequest().setUrgent()), 10, TimeUnit.SECONDS)
        }.onFailure { Log.w(TAG, "info publish failed: ${it.message}") }
    }

    /** SHA-256 of the installed APK, remembered per install so it is read once. */
    private fun apkHash(context: Context, installedAt: Long): String {
        val prefs = context.getSharedPreferences("self_update", Context.MODE_PRIVATE)
        if (prefs.getLong("hashed_at", -1) == installedAt) prefs.getString("hash", null)?.let { return it }
        val digest = MessageDigest.getInstance("SHA-256")
        File(context.applicationInfo.sourceDir).inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        prefs.edit().putLong("hashed_at", installedAt).putString("hash", hash).apply()
        return hash
    }

    private fun incoming(context: Context) = File(context.cacheDir, "update/BitChord.apk")

    /**
     * A newer APK arriving on [P.PATH_APK].
     *
     * Play services is handed the file to fill rather than this process reading
     * the stream itself: a Galaxy Watch freezes a background app a few seconds
     * after it is woken, and a few megabytes over Bluetooth takes longer than
     * that — read here, the transfer stalled mid-way every time. Play services
     * is never frozen; it says when it is done ([onReceived]).
     */
    fun receive(context: Context, channel: ChannelClient.Channel) {
        val app = context.applicationContext
        val file = incoming(app).apply {
            parentFile?.mkdirs()
            delete()
        }
        runCatching {
            Tasks.await(Wearable.getChannelClient(app).receiveFile(channel, Uri.fromFile(file), false), 30, TimeUnit.SECONDS)
        }.onFailure { Log.w(TAG, "update receive failed", it) }
    }

    /** The transfer [receive] started has ended; hand a complete file to the installer. */
    fun onReceived(context: Context, channel: ChannelClient.Channel, closeReason: Int) {
        val app = context.applicationContext
        val file = incoming(app)
        if (closeReason != ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL || !file.exists()) {
            Log.w(TAG, "update transfer ended early ($closeReason)")
            file.delete()
            return
        }
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            // Silent when allowed — once this app is its own installer of record.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val result = PendingIntent.getBroadcast(
                    app,
                    sessionId,
                    Intent(app, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(result.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "update install failed", e)
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
        } finally {
            file.delete()
        }
    }

    /**
     * The installer's prompt, waiting for the app to be on screen to show it.
     * A Galaxy Watch silences notifications from apps its phone has not
     * allowed, so the notification below can arrive with no alert at all;
     * the phone opens the app (see WearActivity) and the app shows this.
     */
    val pendingConfirm = MutableStateFlow<Intent?>(null)

    /** The installer needs the wearer's yes: held for the app, and offered as a notification too. */
    internal fun askToConfirm(context: Context, confirm: Intent) {
        pendingConfirm.value = confirm
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val tap = PendingIntent.getActivity(
            context,
            0,
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_ready))
            .setContentText(context.getString(R.string.update_tap))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    internal fun done(context: Context) {
        pendingConfirm.value = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/** The installer's answer for [SelfUpdate.receive]'s session. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { SelfUpdate.askToConfirm(context, it) }
            }
            PackageInstaller.STATUS_SUCCESS -> SelfUpdate.done(context)
            else -> Log.w(
                "BitChordWear",
                "update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
            )
        }
    }
}
