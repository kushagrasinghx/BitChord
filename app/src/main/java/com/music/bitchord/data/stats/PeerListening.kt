package com.music.bitchord.data.stats

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.YearMonth

/**
 * The listening of this account's *other* devices, as last downloaded.
 *
 * Kept apart from [ListeningStats]' own folder on purpose, and never written
 * into it: a phone's own months are the only thing it uploads, so anything
 * folded in from another phone would go back up as this one's and be counted
 * twice by everybody. Here each device has a folder of its own, its months are
 * *replaced* whenever a newer copy comes down, and the Replay adds them in only
 * when it is read. Downloading the same month twice therefore changes nothing,
 * and the order devices sync in doesn't matter.
 *
 * ```
 * files/listening-peers/<deviceId>/index.json   — name, and each month's Drive checksum
 * files/listening-peers/<deviceId>/2026-11.json — that device's month, as it uploaded it
 * ```
 */
object PeerListening {

    /** What is held for one other device. */
    @Serializable
    data class Peer(
        val deviceId: String,
        val name: String = "",
        /** When that device last synced, from its manifest. */
        val updatedAt: Long = 0L,
        /** Month → Drive's md5 of the copy held here, to skip unchanged ones. */
        val months: Map<String, String> = emptyMap(),
    )

    private const val DIRECTORY = "listening-peers"
    private const val INDEX = "index.json"
    private const val TAG = "BitChordPeers"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private lateinit var root: File
    private val lock = Any()

    fun init(context: Context) {
        root = File(context.filesDir, DIRECTORY)
    }

    private val ready: Boolean get() = this::root.isInitialized

    /** Every other device with anything held, as last synced. */
    fun peers(): List<Peer> = synchronized(lock) {
        if (!ready) return emptyList()
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { readIndex(it) }.orEmpty()
    }

    /** Every month any other device has listening in. */
    fun months(): Set<YearMonth> = peers()
        .flatMap { it.months.keys }
        .mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
        .toSet()

    /** Each other device's copy of [month], for adding into a Replay. */
    fun buckets(month: String): List<StoredBucket> = synchronized(lock) {
        if (!ready) return emptyList()
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val file = File(dir, "$month.json")
            if (!file.exists()) return@mapNotNull null
            runCatching { BucketMath.decode(file.readBytes()) }
                .onFailure { Log.w(TAG, "Discarding unreadable ${dir.name}/$month", it) }
                .getOrNull()
        }.orEmpty()
    }

    /**
     * Replaces [deviceId]'s copy of [month] with [bytes] (a month as
     * [BucketMath.canonicalBytes] wrote it) and records [md5] against it.
     * Written aside and renamed, so a kill mid-write leaves the old copy.
     */
    fun putMonth(deviceId: String, name: String, updatedAt: Long, month: String, bytes: ByteArray, md5: String) {
        synchronized(lock) {
            val dir = dir(deviceId)
            val file = File(dir, "$month.json")
            val temporary = File(dir, "$month.json.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
            val index = readIndex(dir) ?: Peer(deviceId)
            writeIndex(dir, index.copy(name = name, updatedAt = updatedAt, months = index.months + (month to md5)))
        }
    }

    /** Records [deviceId]'s name and sync time without touching its months. */
    fun touch(deviceId: String, name: String, updatedAt: Long) {
        synchronized(lock) {
            val dir = dir(deviceId)
            val index = readIndex(dir) ?: Peer(deviceId)
            writeIndex(dir, index.copy(name = name, updatedAt = updatedAt))
        }
    }

    /** Drops [month] of [deviceId]: it no longer has it either. */
    fun removeMonth(deviceId: String, month: String) {
        synchronized(lock) {
            if (!ready) return
            val dir = File(root, deviceId)
            File(dir, "$month.json").delete()
            readIndex(dir)?.let { writeIndex(dir, it.copy(months = it.months - month)) }
        }
    }

    /** Forgets [deviceId] entirely — removed from Drive, or now this phone. */
    fun removeDevice(deviceId: String) {
        synchronized(lock) {
            if (ready) File(root, deviceId).deleteRecursively()
        }
    }

    /** Forgets every other device, as disconnecting from Drive does. */
    fun clear() {
        synchronized(lock) {
            if (ready) root.deleteRecursively()
        }
    }

    private fun dir(deviceId: String): File {
        require(deviceId.isNotBlank() && deviceId.none { it == '/' || it == '\\' || it == '.' }) {
            "Not a device id: $deviceId"
        }
        return File(root, deviceId).apply { mkdirs() }
    }

    private fun readIndex(dir: File): Peer? {
        val file = File(dir, INDEX)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(Peer.serializer(), file.readText()) }.getOrNull()
    }

    private fun writeIndex(dir: File, peer: Peer) {
        File(dir, INDEX).writeText(json.encodeToString(Peer.serializer(), peer))
    }
}
