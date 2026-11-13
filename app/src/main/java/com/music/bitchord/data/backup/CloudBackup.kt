package com.music.bitchord.data.backup

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.Backup
import com.music.bitchord.data.webdav.WebDavClient
import com.music.bitchord.data.webdav.WebDavConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads the manual backup payload to the configured WebDAV source or an
 * S3-compatible bucket, and restores it on request.
 *
 * One-way by design. Automatic downloads would make a stale cloud copy able to
 * overwrite a day of listening on the device that produced it, and the point of
 * this feature is to stop losing data, not to invent a merge policy for it.
 * Restoring is therefore an explicit, confirmed action; it uses the same
 * payload the file picker writes (see [Backup]).
 *
 * Scheduling follows [com.music.bitchord.download.PlaylistSync]'s shape: a daily
 * WorkManager job while the setting is on, plus a catch-up when the app goes to
 * the background so a session's listening isn't left waiting for the next
 * daily slot. WorkManager is not touched at all while the feature is off.
 */
object CloudBackup {

    private const val TAG = "BitChord"
    private const val PERIODIC_WORK = "cloud_backup"
    private const val ONE_OFF_WORK = "cloud_backup_now"
    private const val FILE_NAME = "bitchord-backup.json"

    /** A launch older than this gets an immediate catch-up. */
    private const val CATCH_UP_MILLIS = 6L * 60 * 60 * 1000

    /** Don't enqueue on every trip to the background during a listening session. */
    private const val BACKGROUND_INTERVAL_MILLIS = 15L * 60 * 1000

    private const val PERIOD_HOURS = 24L
    private const val FLEX_HOURS = 4L

    private val running = Mutex()

    fun init(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var wasEnabled: Boolean? = null
            CloudBackupSettings.config
                .map { it.enabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    val firstLook = wasEnabled == null
                    if (enabled) {
                        schedule(app, replace = !firstLook)
                        if (wasEnabled == false || (firstLook && catchUpDue())) backupNow(app)
                    } else if (!firstLook) {
                        cancel(app)
                    }
                    wasEnabled = enabled
                }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!CloudBackupSettings.config.value.enabled) return
                if (System.currentTimeMillis() - CloudBackupSettings.lastBackupAt.value <
                    BACKGROUND_INTERVAL_MILLIS
                ) {
                    return
                }
                backupNow(app)
            }
        })
    }

    fun backupNow(context: Context, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_OFF_WORK,
            // A user action wins over a queued retry; an automatic catch-up
            // leaves an upload that is already running alone.
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(PERIODIC_WORK)
        work.cancelUniqueWork(ONE_OFF_WORK)
    }

    /**
     * The upload itself. Public so the settings row can report its result
     * immediately instead of waiting for WorkManager's next scheduling window.
     */
    suspend fun performBackup(context: Context): Result<Int> = running.withLock {
        val config = CloudBackupSettings.config.value
        if (!CloudBackupSettings.isConfigured(config, AppSettings.webdavUrl.value)) {
            val message = "Cloud backup is not configured"
            CloudBackupSettings.markFailure(message)
            return@withLock Result.failure(IllegalStateException(message))
        }
        CloudBackupSettings.setWorking(true)
        try {
            val payload = Backup.payload().getOrThrow()
            when (config.provider) {
                CloudBackupSettings.Provider.WEBDAV -> uploadWebDav(config, payload.bytes)
                CloudBackupSettings.Provider.S3 -> uploadS3(config, payload.bytes)
            }
            CloudBackupSettings.markSuccess()
            Log.i(TAG, "Cloud backup uploaded (${payload.months} months, ${payload.bytes.size} bytes)")
            Result.success(payload.months)
        } catch (error: Throwable) {
            CloudBackupSettings.markFailure(error.message)
            Log.w(TAG, "Cloud backup failed", error)
            Result.failure(error)
        } finally {
            CloudBackupSettings.setWorking(false)
        }
    }

    /**
     * Downloads the cloud copy and replaces this device's settings and
     * listening history with it. The caller is expected to have confirmed the
     * replacement first — see the settings row.
     */
    suspend fun restore(context: Context): Result<Backup.Summary> = running.withLock {
        val config = CloudBackupSettings.config.value
        if (!CloudBackupSettings.isConfigured(config, AppSettings.webdavUrl.value)) {
            return@withLock Result.failure(IllegalStateException("Cloud backup is not configured"))
        }
        CloudBackupSettings.setWorking(true)
        try {
            val bytes = when (config.provider) {
                CloudBackupSettings.Provider.WEBDAV -> downloadWebDav(config)
                CloudBackupSettings.Provider.S3 -> downloadS3(config)
            }.getOrThrow()
            Backup.importBytes(bytes)
        } catch (error: Throwable) {
            Log.w(TAG, "Cloud restore failed", error)
            Result.failure(error)
        } finally {
            CloudBackupSettings.setWorking(false)
        }
    }

    /**
     * Checks a not-yet-saved configuration. The settings editor passes its
     * field values here, so a typo is caught before it replaces good settings.
     * WebDAV is tested against the Sources connection, which is the same one
     * an upload would use.
     */
    suspend fun test(
        config: CloudBackupSettings.Config,
        s3SecretKey: String,
        s3SessionToken: String,
    ): Result<Unit> {
        val normalized = config.normalized()
        return when (normalized.provider) {
            CloudBackupSettings.Provider.WEBDAV -> {
                val url = AppSettings.webdavUrl.value
                if (!WebDavConfig.isConfigured(url)) {
                    Result.failure(IllegalArgumentException("Configure your WebDAV server in Sources first"))
                } else {
                    val target = webDavTarget(normalized, FILE_NAME)
                    WebDavClient.exists(target, AppSettings.webdavUsername.value, AppSettings.webdavPassword.value).map { }
                }
            }
            CloudBackupSettings.Provider.S3 -> S3Client.test(
                s3Config(normalized, s3SecretKey, s3SessionToken),
                objectKey(normalized),
            )
        }
    }

    // ── Destinations ────────────────────────────────────────────────────────

    private suspend fun uploadWebDav(config: CloudBackupSettings.Config, bytes: ByteArray) {
        val folder = ensureWebDavFolder(config)
        val target = WebDavClient.joinUrl(folder, FILE_NAME)
        WebDavClient.putFile(
            fileUrl = target,
            stream = bytes.inputStream(),
            contentLength = bytes.size.toLong(),
            mimeType = "application/json",
            username = AppSettings.webdavUsername.value,
            password = AppSettings.webdavPassword.value,
            overwrite = true,
        ).getOrThrow()
    }

    private suspend fun downloadWebDav(config: CloudBackupSettings.Config): Result<ByteArray> =
        WebDavClient.getFile(
            webDavTarget(config, FILE_NAME),
            AppSettings.webdavUsername.value,
            AppSettings.webdavPassword.value,
        )

    private suspend fun uploadS3(config: CloudBackupSettings.Config, bytes: ByteArray) {
        S3Client.put(
            s3Config(config, CloudBackupSettings.s3SecretKey.value, CloudBackupSettings.s3SessionToken.value),
            objectKey(config),
            bytes,
        ).getOrThrow()
    }

    private suspend fun downloadS3(config: CloudBackupSettings.Config): Result<ByteArray> =
        S3Client.get(
            s3Config(config, CloudBackupSettings.s3SecretKey.value, CloudBackupSettings.s3SessionToken.value),
            objectKey(config),
        )

    /**
     * Creates each folder segment in turn; `ensureCollection` treats an
     * already-existing one as success. Returns the folder URL the file belongs
     * in, or the server root when no folder was configured.
     */
    private suspend fun ensureWebDavFolder(config: CloudBackupSettings.Config): String {
        var folder = WebDavConfig.normalizeUrl(AppSettings.webdavUrl.value)
        for (segment in config.webDavFolder.split('/').filter { it.isNotBlank() }) {
            folder = WebDavClient.joinUrl(folder, segment)
            WebDavClient.ensureCollection(
                folder,
                AppSettings.webdavUsername.value,
                AppSettings.webdavPassword.value,
            ).getOrThrow()
        }
        return folder
    }

    /** The file's URL without creating anything, for HEAD/GET. */
    private fun webDavTarget(config: CloudBackupSettings.Config, name: String): String {
        var folder = WebDavConfig.normalizeUrl(AppSettings.webdavUrl.value)
        for (segment in config.webDavFolder.split('/').filter { it.isNotBlank() }) {
            folder = WebDavClient.joinUrl(folder, segment)
        }
        return WebDavClient.joinUrl(folder, name)
    }

    private fun objectKey(config: CloudBackupSettings.Config): String {
        val prefix = CloudBackupSettings.normalizePath(config.s3Prefix)
        return if (prefix.isEmpty()) FILE_NAME else "$prefix/$FILE_NAME"
    }

    private fun s3Config(
        config: CloudBackupSettings.Config,
        secretKey: String,
        sessionToken: String,
    ): S3Client.Config = S3Client.Config(
        endpoint = config.s3Endpoint,
        region = config.s3Region,
        bucket = config.s3Bucket,
        accessKey = config.s3AccessKey,
        secretKey = secretKey,
        sessionToken = sessionToken.trim().takeIf { it.isNotBlank() },
        pathStyle = config.s3PathStyle,
    )

    // ── Scheduling ──────────────────────────────────────────────────────────

    private fun schedule(context: Context, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        ).setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    private fun catchUpDue(): Boolean =
        System.currentTimeMillis() - CloudBackupSettings.lastBackupAt.value >= CATCH_UP_MILLIS
}

/**
 * What WorkManager actually runs. A failure caused by the configuration itself
 * (bad key, wrong bucket) is not retried — it would fail the same way three
 * times. Network and TLS failures are, because the next attempt has a real
 * chance of succeeding.
 */
class CloudBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Turned off in a run of the app that died before it could cancel this.
        if (!CloudBackupSettings.config.value.enabled) {
            CloudBackup.cancel(applicationContext)
            return Result.success()
        }
        val outcome = CloudBackup.performBackup(applicationContext)
        return outcome.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                when {
                    error is IOException && runAttemptCount < 3 -> Result.retry()
                    error is IOException -> Result.failure()
                    runAttemptCount < 3 && error !is S3Client.S3Exception &&
                        error !is WebDavClient.WebDavException -> Result.retry()
                    else -> Result.failure()
                }
            },
        )
    }
}
