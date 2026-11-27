package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

data class UsageBreakdown(
    val streamingWifiBytes: Long = 0L,
    val streamingMobileBytes: Long = 0L,
    val downloadWifiBytes: Long = 0L,
    val downloadMobileBytes: Long = 0L,
) {
    val streamingBytes: Long get() = streamingWifiBytes + streamingMobileBytes
    val downloadBytes: Long get() = downloadWifiBytes + downloadMobileBytes
    val wifiBytes: Long get() = streamingWifiBytes + downloadWifiBytes
    val mobileBytes: Long get() = streamingMobileBytes + downloadMobileBytes
    val totalBytes: Long get() = streamingBytes + downloadBytes

    operator fun plus(other: UsageBreakdown): UsageBreakdown = UsageBreakdown(
        streamingWifiBytes = streamingWifiBytes + other.streamingWifiBytes,
        streamingMobileBytes = streamingMobileBytes + other.streamingMobileBytes,
        downloadWifiBytes = downloadWifiBytes + other.downloadWifiBytes,
        downloadMobileBytes = downloadMobileBytes + other.downloadMobileBytes,
    )
}

data class DataUsageSummary(
    val today: UsageBreakdown = UsageBreakdown(),
    val last7Days: UsageBreakdown = UsageBreakdown(),
    val last30Days: UsageBreakdown = UsageBreakdown(),
)

/**
 * Tracks network bytes consumed by audio streaming and offline downloads,
 * split by Wi-Fi vs mobile data and aggregated over daily, 7-day, and 30-day windows.
 */
object DataUsageTracker {

    private const val PREF_NAME = "bitchord_data_usage"
    private const val KEY_DAILY_JSON = "daily_usage_json"
    private const val MAX_DAYS_RETAINED = 90L
    private const val FLUSH_INTERVAL_MS = 10_000L
    private const val FLUSH_BYTE_THRESHOLD = 256L * 1024L

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Volatile
    private var prefs: SharedPreferences? = null
    @Volatile
    private var appContext: Context? = null

    private val lock = Any()
    private val dailyBuckets = LinkedHashMap<String, UsageBreakdown>()

    private val pendingStreamWifi = AtomicLong(0L)
    private val pendingStreamMobile = AtomicLong(0L)
    private val pendingDownloadWifi = AtomicLong(0L)
    private val pendingDownloadMobile = AtomicLong(0L)
    private val lastFlushMs = AtomicLong(0L)

    private val _summary = MutableStateFlow(DataUsageSummary())
    val summary: StateFlow<DataUsageSummary> = _summary.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            if (prefs != null) return
            prefs = p
            loadLocked(p.getString(KEY_DAILY_JSON, null))
            publishSummaryLocked(LocalDate.now())
        }
    }

    fun recordStreamBytes(bytes: Long) {
        if (bytes <= 0L) return
        val wifi = isUnmeteredNetwork()
        if (wifi) pendingStreamWifi.addAndGet(bytes) else pendingStreamMobile.addAndGet(bytes)
        maybeFlush()
    }

    fun recordDownloadBytes(bytes: Long) {
        if (bytes <= 0L) return
        val wifi = isUnmeteredNetwork()
        if (wifi) pendingDownloadWifi.addAndGet(bytes) else pendingDownloadMobile.addAndGet(bytes)
        flush()
    }

    fun flush() {
        val sw = pendingStreamWifi.getAndSet(0L)
        val sm = pendingStreamMobile.getAndSet(0L)
        val dw = pendingDownloadWifi.getAndSet(0L)
        val dm = pendingDownloadMobile.getAndSet(0L)
        if (sw == 0L && sm == 0L && dw == 0L && dm == 0L) return
        lastFlushMs.set(System.currentTimeMillis())

        val delta = UsageBreakdown(
            streamingWifiBytes = sw,
            streamingMobileBytes = sm,
            downloadWifiBytes = dw,
            downloadMobileBytes = dm,
        )
        val today = LocalDate.now()
        synchronized(lock) {
            val key = today.format(dateFormatter)
            dailyBuckets[key] = (dailyBuckets[key] ?: UsageBreakdown()) + delta
            pruneOldBucketsLocked(today)
            saveLocked()
            publishSummaryLocked(today)
        }
    }

    fun reset() {
        pendingStreamWifi.set(0L)
        pendingStreamMobile.set(0L)
        pendingDownloadWifi.set(0L)
        pendingDownloadMobile.set(0L)
        synchronized(lock) {
            dailyBuckets.clear()
            prefs?.edit()?.remove(KEY_DAILY_JSON)?.apply()
            _summary.value = DataUsageSummary()
        }
    }

    internal fun aggregateSummary(
        buckets: Map<String, UsageBreakdown>,
        today: LocalDate,
    ): DataUsageSummary {
        val todayKey = today.format(dateFormatter)
        val cutoff7 = today.minusDays(6)
        val cutoff30 = today.minusDays(29)

        var todaySum = UsageBreakdown()
        var weekSum = UsageBreakdown()
        var monthSum = UsageBreakdown()

        for ((key, value) in buckets) {
            val date = runCatching { LocalDate.parse(key, dateFormatter) }.getOrNull() ?: continue
            if (date.isAfter(today)) continue
            if (key == todayKey) todaySum += value
            if (!date.isBefore(cutoff7)) weekSum += value
            if (!date.isBefore(cutoff30)) monthSum += value
        }
        return DataUsageSummary(
            today = todaySum,
            last7Days = weekSum,
            last30Days = monthSum,
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    private fun maybeFlush() {
        val totalPending = pendingStreamWifi.get() + pendingStreamMobile.get() +
            pendingDownloadWifi.get() + pendingDownloadMobile.get()
        val now = System.currentTimeMillis()
        if (totalPending >= FLUSH_BYTE_THRESHOLD || now - lastFlushMs.get() >= FLUSH_INTERVAL_MS) {
            flush()
        }
    }

    private fun isUnmeteredNetwork(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun pruneOldBucketsLocked(today: LocalDate) {
        val cutoff = today.minusDays(MAX_DAYS_RETAINED)
        val iter = dailyBuckets.entries.iterator()
        while (iter.hasNext()) {
            val date = runCatching { LocalDate.parse(iter.next().key, dateFormatter) }.getOrNull()
            if (date == null || date.isBefore(cutoff)) iter.remove()
        }
    }

    private fun publishSummaryLocked(today: LocalDate) {
        _summary.value = aggregateSummary(dailyBuckets, today)
    }

    private fun saveLocked() {
        val root = JSONObject()
        for ((key, v) in dailyBuckets) {
            root.put(
                key,
                JSONObject().apply {
                    put("sw", v.streamingWifiBytes)
                    put("sm", v.streamingMobileBytes)
                    put("dw", v.downloadWifiBytes)
                    put("dm", v.downloadMobileBytes)
                },
            )
        }
        prefs?.edit()?.putString(KEY_DAILY_JSON, root.toString())?.apply()
    }

    private fun loadLocked(raw: String?) {
        dailyBuckets.clear()
        if (raw.isNullOrBlank()) return
        runCatching {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                dailyBuckets[key] = UsageBreakdown(
                    streamingWifiBytes = obj.optLong("sw", 0L),
                    streamingMobileBytes = obj.optLong("sm", 0L),
                    downloadWifiBytes = obj.optLong("dw", 0L),
                    downloadMobileBytes = obj.optLong("dm", 0L),
                )
            }
        }
    }
}
