package com.music.bitchord.data.debrid

import com.music.bitchord.data.TrackLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Debrid torrent cache: which asked hashes are cached, and the download URL
 * for one of them.
 *
 * Auth is a user-supplied API key on every call — nothing is minted, stored,
 * or refreshed here. All three methods run on IO and return null/false
 * rather than throwing; the source treats that as "not cached right now".
 */
object DebridCache {

    private const val TAG = "BitChord"
    private const val BASE = "https://api.torbox.app"

    private val codec = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(codec) }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 6_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    /** One cached file: the torrent holding it, and the name it holds it under. */
    data class CachedFile(val torrentId: Long, val name: String)

    /**
     * Which of [md5s] the cache holds, by asked hash.
     *
     * One batched call no matter how many filenames are asked about — the
     * caller groups tiers and tracks into a single set first.
     */
    suspend fun checkCached(apiKey: String, md5s: List<String>): Map<String, CachedFile> {
        if (md5s.isEmpty()) return emptyMap()
        return runCatching {
            val text = client.post("$BASE/v1/api/torrents/checkcached") {
                url {
                    parameters.append("format", "object")
                    parameters.append("list_files", "false")
                }
                bearerAuth(apiKey.trim())
                contentType(ContentType.Application.Json)
                setBody(mapOf("hashes" to md5s))
            }.bodyAsText()
            val root = codec.parseToJsonElement(text).jsonObject
            // The cache answers success as boolean true; anything else names the
            // problem in `detail`/`error` and means no usable answer.
            if (root["success"]?.jsonPrimitive?.content != "true") {
                TrackLog.w(TAG, "Debrid cache check refused: ${text.take(160)}")
                return@runCatching emptyMap<String, CachedFile>()
            }
            val data = root["data"]?.jsonObject ?: return@runCatching emptyMap<String, CachedFile>()
            data.entries.mapNotNull { (hash, element) ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.longOrNull
                    ?: item["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                hash to CachedFile(id, name)
            }.toMap()
        }.getOrElse {
            TrackLog.w(TAG, "Debrid cache check error: ${it.message}")
            emptyMap()
        }
    }

    /**
     * An openable URL for [torrentId]'s default file.
     *
     * Resolved at play time, not at search time: download links expire, and a
     * URL minted during a search completed an hour ago is a 403 waiting to
     * happen.
     */
    suspend fun downloadUrl(apiKey: String, torrentId: Long): String? = runCatching {
        val text = client.get("$BASE/v1/api/torrents/requestdl") {
            parameter("token", apiKey.trim())
            parameter("torrent_id", torrentId)
            parameter("zip_link", false)
        }.bodyAsText()
        val root = codec.parseToJsonElement(text).jsonObject
        root["data"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
    }.getOrElse {
        TrackLog.w(TAG, "Debrid download-link error: ${it.message}")
        null
    }

    /** Whether [apiKey] is accepted, for the editor's Test button. */
    suspend fun validateKey(apiKey: String): Boolean = runCatching {
        val text = client.get("$BASE/v1/api/user/me") {
            bearerAuth(apiKey.trim())
        }.bodyAsText()
        codec.parseToJsonElement(text).jsonObject["success"]
            ?.jsonPrimitive?.content == "true"
    }.getOrDefault(false)

    /** Display name for a valid key, for the editor's Test button. Null when the key is rejected. */
    suspend fun accountName(apiKey: String): String? = runCatching {
        val text = client.get("$BASE/v1/api/user/me") {
            bearerAuth(apiKey.trim())
        }.bodyAsText()
        val root = codec.parseToJsonElement(text).jsonObject
        if (root["success"]?.jsonPrimitive?.content != "true") return@runCatching null
        val data = root["data"]?.jsonObject ?: return@runCatching null
        data["email"]?.jsonPrimitive?.contentOrNull
            ?: data["username"]?.jsonPrimitive?.contentOrNull
            ?: data["plan"]?.jsonPrimitive?.contentOrNull
            ?: data["plan"]?.jsonPrimitive?.intOrNull?.toString()
    }.getOrNull()
}
