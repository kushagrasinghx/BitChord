package com.music.bitchord.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable
data class CachedArtistProfile(
    val name: String,
    val heroUrl: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val subscriberCountText: String? = null,
    val monthlyListenerCount: String? = null,
    val topSongTitles: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Persistent disk cache for offline artist profiles.
 * Preserves online-enriched hero pictures, transparent artist logos, bios,
 * subscriber/listener counts, and top-song ordering when browsing offline.
 */
object ArtistProfileCache {

    private const val FILE_NAME = "artist_profile_cache.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val memory = LinkedHashMap<String, CachedArtistProfile>(200, 0.75f, true)
    private var loaded = false

    private fun normalize(name: String): String =
        name.trim().lowercase(Locale.ROOT)

    private fun load(context: Context) {
        if (loaded) return
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            loaded = true
            return
        }
        runCatching {
            val text = file.readText(Charsets.UTF_8)
            val list = json.decodeFromString<List<CachedArtistProfile>>(text)
            synchronized(memory) {
                list.forEach { profile ->
                    memory[normalize(profile.name)] = profile
                }
            }
            loaded = true
        }
    }

    private fun save(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        runCatching {
            val list = synchronized(memory) { memory.values.toList() }
            val text = json.encodeToString(list)
            tmp.writeText(text, Charsets.UTF_8)
            if (tmp.exists()) {
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun get(context: Context, artistName: String): CachedArtistProfile? {
        load(context)
        val key = normalize(artistName)
        return synchronized(memory) { memory[key] }
    }

    fun put(context: Context, profile: CachedArtistProfile) {
        load(context)
        val key = normalize(profile.name)
        synchronized(memory) {
            memory[key] = profile
        }
        save(context)
    }
}
