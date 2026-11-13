package com.music.bitchord.sponsorblock

import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Fetches SponsorBlock skip segments (https://sponsor.ajay.app), cached in memory. */
object SponsorBlockClient {

    private const val TAG = "SponsorBlock"
    private const val API_URL = "https://sponsor.ajay.app"
    private const val CACHE_SIZE = 64

    private val cache = object : LinkedHashMap<String, List<SponsorSegment>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SponsorSegment>>?) =
            size > CACHE_SIZE
    }

    /** Segments for [videoId]; empty when none exist or the request fails. */
    suspend fun getSegments(
        videoId: String,
        categories: List<SponsorBlockCategory> = SponsorBlockCategory.entries,
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || categories.isEmpty()) return@withContext emptyList()
        synchronized(cache) { cache[videoId] }?.let { return@withContext it }

        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/skipSegments")
            .addQueryParameter("videoID", videoId)
            .addQueryParameter("categories", categories.joinToString(",", "[", "]") { "\"${it.key}\"" })
            .build()
        val request = Request.Builder().url(url).get().build()

        try {
            Http.client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    synchronized(cache) { cache[videoId] = emptyList() }
                    return@withContext emptyList()
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for $videoId")
                    return@withContext emptyList()
                }
                parseSegments(response.body?.string().orEmpty()).also {
                    synchronized(cache) { cache[videoId] = it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed for $videoId: ${e.message}")
            emptyList()
        }
    }

    /** Parses an API response into segments sorted by start time. */
    internal fun parseSegments(body: String): List<SponsorSegment> {
        if (body.isBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(body).jsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val category = obj["category"]?.jsonPrimitive?.contentOrNull
                ?.let(SponsorBlockCategory::fromKey) ?: return@mapNotNull null
            val range = obj["segment"]?.jsonArray ?: return@mapNotNull null
            val start = range.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val end = range.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            SponsorSegment(
                uuid = obj["UUID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                category = category,
                startMs = (start * 1000).toLong(),
                endMs = (end * 1000).toLong(),
            )
        }.sortedBy { it.startMs }
    }
}
