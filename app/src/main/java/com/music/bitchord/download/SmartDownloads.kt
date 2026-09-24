package com.music.bitchord.download

import android.content.Context
import android.content.SharedPreferences
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ListeningStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What the automatic download pass remembers between runs, and the schedule it
 * runs on.
 *
 * The pass itself is [SmartDownloadsWorker]; everything here is either state it
 * needs to survive the process, or the question of *when* it happens.
 *
 * The ledger is deliberately its own preference file rather than one of
 * [AppSettings]': those flows are the listener's answers, restored verbatim
 * from a backup and read back by [com.music.bitchord.data.stats.Backup]. This
 * is the feature's working memory — a few thousand ids and a day's counter —
 * and restoring it onto another device, where the downloads named by it don't
 * exist, would be worse than not having it.
 */
@Serializable
internal data class SmartDownloadLedger(
    /**
     * Every id this feature asked for, and the moment it asked.
     *
     * Written when the track is *queued*, not when it lands, and never removed
     * by eviction — which is the whole reason a deleted automatic download
     * doesn't come back. The two cases a caller has to tell apart:
     *
     * - **failed** — [Downloads.active] still holds a [DownloadState.Failed]
     *   for the id, and the worker takes it out of here so the next run tries
     *   again. See [SmartDownloadsWorker.harvest].
     * - **deliberately removed** — either the listener deleted it or the keep
     *   limit evicted it. Either way nothing holds a failure for it, so it
     *   stays here and is never fetched a second time.
     *
     * The timestamp is what makes "keep the newest N" answerable without
     * re-deriving an order from file dates that MediaStore rewrites.
     */
    val decided: Map<String, Long> = emptyMap(),

    /** The day [used] counted, as `yyyy-MM-dd`. Empty before the first run. */
    val day: String = "",

    /** How many tracks were queued on [day]. Reset when [day] rolls over. */
    val used: Int = 0,

    /** Where in the library's playlist list the last run stopped. */
    val playlistCursor: Int = 0,
)

/**
 * The automatic download pass's memory and its place on the schedule.
 *
 * Scheduling is WorkManager — the first and only thing in this project that
 * runs on a timer, so there was nothing to build it on top of. The request asks
 * for an unmetered network by default, a stricter bound than
 * [AppSettings.wifiOnlyDownloads] puts on a download somebody tapped and is
 * watching, because this one spends storage in the background where nobody
 * would notice the bill until it arrived. [AppSettings.smartDownloadsCellular]
 * relaxes that to "any connection", and [reschedule] is what makes the
 * relaxation take effect immediately rather than on the next cold start.
 */
internal object SmartDownloads {

    private const val PREFS = "bitchord_smart_downloads"
    private const val KEY_LEDGER = "ledger"
    private const val TAG = "BitChord"

    /** Unique name of the once-a-day pass. */
    const val WORK_NAME = "smart-downloads"

    /** Unique name of the pass run immediately after the switch is turned on. */
    const val NOW_WORK_NAME = "smart-downloads-now"

    /**
     * Recorded on every track as the reason it was queued, so the Downloads
     * sheet still says where a track nobody tapped came from.
     */
    const val FROM = "smart-downloads"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Serialises runs against each other.
     *
     * A periodic pass and the immediate one can be scheduled minutes apart and
     * would otherwise both read the ledger before either wrote it, each seeing
     * the day's quota as still unspent and queueing twice as much as the limit
     * allows.
     */
    private val lock = Mutex()

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Held for the whole harvest, so two runs cannot spend one quota twice. */
    val runLock: Mutex
        get() = lock

    suspend fun read(): SmartDownloadLedger = withContext(Dispatchers.IO) {
        if (!::prefs.isInitialized) return@withContext SmartDownloadLedger()
        runCatching {
            prefs.getString(KEY_LEDGER, null)?.let { json.decodeFromString<SmartDownloadLedger>(it) }
        }.getOrNull() ?: SmartDownloadLedger()
    }

    suspend fun write(ledger: SmartDownloadLedger) {
        withContext(Dispatchers.IO) {
            if (!::prefs.isInitialized) return@withContext
            prefs.edit().putString(KEY_LEDGER, json.encodeToString(ledger)).apply()
        }
    }

    /**
     * Every id this feature has ever asked for, whether or not it is still on
     * disk.
     *
     * The Downloads page uses it to tell a track somebody chose from one the
     * background chose, which is the only distinction the keep limit and its
     * eviction order ever draw — so a listener hiding one or the other is
     * drawing the same line the feature itself draws, not a new one.
     */
    suspend fun decidedIds(): Set<String> = read().decided.keys

    /**
     * Registers the once-a-day pass, if the listener has it turned on.
     *
     * Called from the application on every cold start and from the switch's own
     * setter, so a device that was rebooted or had the process killed still
     * ends up with the request registered. [ExistingPeriodicWorkPolicy.KEEP]
     * rather than UPDATE: nothing about the request changes once written, and
     * replacing it would push the next run off every time the app opens.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SmartDownloadsWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Re-registers the daily pass so a changed network constraint bites today.
     *
     * [schedule] holds its request with KEEP because replacing it on every cold
     * start would push the next run forward indefinitely. But the constraint is
     * read from [AppSettings.smartDownloadsCellular] *at registration time*, so
     * the request written before somebody allowed background data would sit
     * waiting for Wi-Fi forever. UPDATE is therefore only ever used from here,
     * and only ever right after that setting changed.
     */
    fun reschedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SmartDownloadsWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Runs the pass now rather than whenever the daily request comes round. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SmartDownloadsWorker>()
            .setConstraints(networkConstraint())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        // The likeliest reason this then looks like nothing happened: the
        // request above carries a network constraint, so on a phone sitting on
        // mobile data with background data still refused it stays enqueued
        // until Wi-Fi comes back and never starts a worker at all. Saying so at
        // the moment the switch was flipped is cheaper than guessing later.
        Log.i(
            TAG,
            if (AppSettings.smartDownloadsCellular.value) {
                "smart downloads queued to run now"
            } else {
                "smart downloads queued to run now — waiting for an unmetered network"
            },
        )
    }

    /**
     * Drops both requests when the switch goes off.
     *
     * The worker would exit at its first line anyway, but a registered request
     * still wakes the process once a day to reach that line, and the listener
     * who turned a feature off has said they don't want that.
     */
    fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(WORK_NAME)
        work.cancelUniqueWork(NOW_WORK_NAME)
    }

    /**
     * Enforces [AppSettings.smartDownloadsKeep] against what is actually on
     * disk, least-recently-heard automatic download first.
     *
     * Only ids the feature put in [SmartDownloadLedger.decided] are considered,
     * so a track somebody tapped to download can never be dropped to make room
     * for one they didn't ask for. An evicted id stays in the ledger — see the
     * note there — which is what stops the next run from fetching it straight
     * back and evicting something else in its place.
     *
     * The order is what changed here, and it is the whole point: a folder is
     * not a queue, so the wrong thing to drop is not the *oldest download* but
     * the *oldest listen*. A three-year-old favourite kept at the bottom of a
     * rolling window would be the first thing thrown away, while a track
     * fetched last week and never played sat there indefinitely. Time last
     * heard comes first from [ListeningStats]; ties — and there are always
     * ties among tracks never played, which is most of them — go to the
     * earliest time this feature asked for them, so a back catalogue drains
     * oldest-first instead of in whatever order the library happened to be in.
     *
     * An id [ListeningStats] has forgotten counts as never heard and is
     * therefore evicted first. That is the intended reading: history is
     * pruned to a handful of months, and anything outside it is precisely the
     * music somebody stopped listening to.
     */
    suspend fun trim(context: Context) {
        val keep = AppSettings.smartDownloadsKeep.value
        val ledger = read()
        if (ledger.decided.isEmpty()) return
        val present = Downloads.saved.value.keys.filter { it in ledger.decided }
        val surplus = present.size - keep
        if (surplus <= 0) return
        val heard = runCatching { ListeningStats.lastPlayedAt(present) }.getOrDefault(emptyMap())
        evictionOrder(present, heard, ledger.decided)
            .take(surplus)
            .forEach { Downloads.delete(context, it) }
    }

    /**
     * What the keep limit is currently made of, in bytes.
     *
     * Counted from the automatic downloads only — the same set [trim] drops —
     * because the number on screen has to mean the same thing as the number
     * that enforces it. Files someone tapped to download are shown by the
     * storage settings, not here, and folding them in would report a footprint
     * a listener has no way to shrink, on a phone whose user has just
     * downloaded a hundred tracks by hand.
     *
     * The average is over whatever is on disk right now rather than a fixed
     * figure, so a listener on 256 kbps Opus sees a limit that weighs what
     * *their* limit weighs; [SmartDownloadFootprint.estimateBytes] then scales
     * that average up to the whole allowance.
     */
    suspend fun footprint(context: Context): SmartDownloadFootprint = withContext(Dispatchers.IO) {
        val ledger = read()
        val automatic = Downloads.saved.value.filterKeys { it in ledger.decided }
        var used = 0L
        automatic.values.forEach { uri -> used += sizeOf(context, uri) }
        val count = automatic.size
        val average = if (count > 0) used / count else 0L
        SmartDownloadFootprint(
            count = count,
            usedBytes = used,
            averageBytes = average,
            estimateBytes = average * AppSettings.smartDownloadsKeep.value,
        )
    }

    /** Bytes one file occupies, or zero for anything that will not answer. */
    private fun sizeOf(context: Context, uriString: String): Long = runCatching {
        val uri = uriString.toUri()
        when (uri.scheme) {
            "file" -> File(uri.path ?: return@runCatching 0L).length()
            // Exported downloads live in the shared Music collection, where the
            // path is not readable directly and the size has to be asked for.
            else -> context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
                } ?: 0L
        }
    }.getOrDefault(0L)

    private fun networkConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (AppSettings.smartDownloadsCellular.value) {
                NetworkType.CONNECTED
            } else {
                NetworkType.UNMETERED
            },
        )
        .build()
}

/**
 * How far into its allowance the automatic downloads have eaten.
 *
 * @param count automatic downloads currently on disk
 * @param usedBytes their combined size
 * @param averageBytes what one of them weighs, derived from [usedBytes] over
 *   [count]; zero when nothing is on disk yet
 * @param estimateBytes [averageBytes] scaled up to
 *   [AppSettings.smartDownloadsKeep] tracks — the weight this limit *would*
 *   have if it were filled with the same music
 */
internal data class SmartDownloadFootprint(
    val count: Int,
    val usedBytes: Long,
    val averageBytes: Long,
    val estimateBytes: Long,
)

/**
 * How many tracks this ledger counted on [today].
 *
 * Zero whenever the ledger belongs to another day, which is the whole of the
 * daily-reset rule: the pass writes the date it counted under, so the next run
 * only has to compare two strings to know whether the quota refilled.
 */
internal fun SmartDownloadLedger.usedOn(today: String): Int = if (day == today) used else 0

/** Tracks still allowed to be queued on [today], never below zero. */
internal fun SmartDownloadLedger.remainingQuota(today: String, limit: Int): Int =
    (limit - usedOn(today)).coerceAtLeast(0)

/**
 * The order [SmartDownloads.trim] deletes in: least-recently-heard first.
 *
 * Two keys rather than one, because a folder of automatic downloads is mostly
 * tracks nobody has got round to playing yet and they all read as *never* heard
 * — a single key would leave them in whatever order the library feed happened
 * to hand them over, and "oldest queued first" is the only sensible tiebreak
 * left. Anything [ListeningStats] does not know about scores zero, so a track
 * outside the pruned history window is treated as never heard and goes before
 * one heard last week.
 *
 * Pure on purpose: this is the whole of the policy, and the rest of [trim] is
 * only gathering the three maps it needs to ask for it.
 */
internal fun evictionOrder(
    present: List<String>,
    heardAt: Map<String, Long>,
    decidedAt: Map<String, Long>,
): List<String> = present.sortedWith(
    compareBy<String> { heardAt[it] ?: 0L }
        .thenBy { decidedAt[it] ?: Long.MAX_VALUE },
)

/**
 * Whether [videoId] is a track this feature has not already asked for.
 *
 * The pass's only filter, and the reason it needs no memory of *where* it had
 * got to: anything already decided is off the list forever, anything already
 * on disk is off it while it lasts, and what is left in between is exactly
 * "new and not yet offline". A track that failed sits in [decided] too until
 * [SmartDownloadsWorker] sees the failure and takes it out.
 */
internal fun isNewCandidate(videoId: String, decided: Set<String>, saved: Set<String>): Boolean =
    videoId.isNotBlank() && videoId !in decided && videoId !in saved
