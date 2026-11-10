/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.isLocalMediaId
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object LocalMediaUriHeals {
    private const val FILE_NAME = "local_uri_heals.json"
    private const val MAX_ENTRIES = 512

    @Volatile
    private var healed: Map<String, String> = emptyMap()

    @Volatile
    private var loaded = false

    private val persistenceMutex = Mutex()

    private val failedHealAttempts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun uriFor(mediaId: String): String? {
        if (!mediaId.isLocalMediaId()) return null
        if (!mediaId.startsWith("content://")) return null
        return healed[mediaId]
    }

    fun record(mediaId: String, healedUri: String) {
        if (mediaId == healedUri) return
        if (healed[mediaId] == healedUri) return
        healed = healed + (mediaId to healedUri)

        if (healed.size > MAX_ENTRIES) {
            healed = healed.entries.toList().takeLast(MAX_ENTRIES).associate { it.key to it.value }
        }
    }

    fun markUnhealable(mediaId: String) {
        failedHealAttempts.add(mediaId)
    }

    fun isKnownUnhealable(mediaId: String): Boolean = failedHealAttempts.contains(mediaId)

    fun loadBlocking(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.isFile) return
            val json = JSONObject(file.readText())
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key)
                if (value.startsWith("content://")) map[key] = value
            }
            healed = map
        }
    }

    suspend fun persist(context: Context) {
        persistenceMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = healed
                    val json = JSONObject()
                    snapshot.forEach { (k, v) -> json.put(k, v) }
                    File(context.filesDir, FILE_NAME).writeText(json.toString())
                }
            }
        }
    }

    fun resolveReplacement(
        context: Context,
        staleMediaId: String,
        title: String?,
        durationMs: Long?,
        artist: String?,
    ): String? {
        if (!staleMediaId.isLocalMediaId()) return null
        val staleUri = Uri.parse(staleMediaId)

        runCatching {
            context.contentResolver.query(
                staleUri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) return staleMediaId
            }
        }

        if (title.isNullOrBlank()) return null

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val candidates = mutableListOf<Pair<Long, Int>>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val rowTitle = cursor.getString(titleIdx) ?: continue
                    if (!rowTitle.equals(title, ignoreCase = true)) continue
                    var score = 0
                    val rowDuration = cursor.getLong(durationIdx)
                    if (durationMs != null && durationMs > 0) {
                        val delta = kotlin.math.abs(rowDuration - durationMs)
                        if (delta > 3_000L) continue
                        if (delta <= 250L) score += 2
                    }
                    val rowArtist = cursor.getString(artistIdx)
                    if (artist != null && rowArtist.equals(artist, ignoreCase = true)) score += 1
                    candidates += cursor.getLong(idIdx) to score
                }
            }
        }
        val best = candidates.maxByOrNull { it.second } ?: return null
        return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            .buildUpon()
            .appendPath(best.first.toString())
            .toString()
    }
}
