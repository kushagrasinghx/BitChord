package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicReference

/**
 * The YouTube session shared by search and playback: a visitor id, and — once somebody has signed
 * in — who that session acts as.
 */
internal object DesktopYouTubeSession {
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val visitorData = AtomicReference<String?>(null)
    private val visitorPattern = Regex("""Cg[A-Za-z0-9_%-]{40,}""")
    private val client = HttpClient(CIO)

    /**
     * Whether the visitor id in hand came from the signed-in shell rather than being minted
     * anonymously.
     */
    @Volatile
    private var sessionBound = false

    /** Returns the current visitor id, minting one once for this process. */
    suspend fun ensureVisitorData(refresh: Boolean = false): String? {
        if (!refresh) visitorData.get()?.let { return it }
        val minted = runCatching {
            val body = client.get("https://www.youtube.com/sw.js_data") {
                header("User-Agent", WEB_USER_AGENT)
            }.bodyAsText()
            findVisitorData(Json.parseToJsonElement(body.substringAfter("\n", body.drop(5))))
        }.getOrNull()
        if (minted != null && (refresh || !sessionBound)) {
            visitorData.set(minted)
            sessionBound = false
        }
        return visitorData.get()
    }

    /**
     * Reads who the stored cookie actually acts as out of the signed-in music.youtube.com shell,
     * and adopts its visitor id.
     */
    suspend fun adoptSessionScope(cookie: String): DesktopYouTubeAuth.Session? {
        val html = runCatching {
            client.get("https://music.youtube.com/") {
                header("User-Agent", WEB_USER_AGENT)
                header("Accept-Language", "en-US,en;q=0.9")
                header("Cookie", cookie)
                DesktopYouTubeAuth.signatureFor(cookie, DesktopYouTubeAuth.MUSIC_ORIGIN)
                    ?.let { header("Authorization", it) }
            }.bodyAsText()
        }.getOrElse {
            DesktopTrackLog.log("could not read the session scope: ${it.message}")
            return null
        }

        // The one value that must not be guessed.
        val signedIn = CONFIG_LOGGED_IN.find(html)?.groupValues?.get(1) == "true"
        if (!signedIn) {
            DesktopTrackLog.log("music.youtube.com served a signed-out shell; not scoping requests")
            return null
        }
        // The shell's own visitor id, bound to this session — see [sessionBound].
        CONFIG_VISITOR_DATA.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let {
            visitorData.set(it)
            sessionBound = true
        }
        return DesktopYouTubeAuth.Session(
            cookie = cookie,
            authUser = CONFIG_SESSION_INDEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "0",
            pageId = CONFIG_PAGE_ID.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() },
            // `<accountSyncId>||<sessionSyncId>`; only the first half names the account, and the
            // second changes on its own schedule.
            dataSyncId = CONFIG_DATASYNC_ID.find(html)?.groupValues?.get(1)
                ?.substringBefore("||")
                ?.takeIf { it.isNotBlank() },
        )
    }

    private val CONFIG_LOGGED_IN = Regex(""""LOGGED_IN"\s*:\s*(true|false)""")
    private val CONFIG_DATASYNC_ID = Regex(""""DATASYNC_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_PAGE_ID = Regex(""""DELEGATED_SESSION_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_SESSION_INDEX = Regex(""""SESSION_INDEX"\s*:\s*"?(\d+)""")
    private val CONFIG_VISITOR_DATA = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")

    /** Browse/search responses sometimes already contain the same id. */
    fun capture(response: JsonObject) {
        response["responseContext"]?.jsonObject
            ?.get("visitorData")?.jsonPrimitive?.contentOrNull
            ?.takeIf { visitorPattern.matches(it) }
            ?.let { visitorData.compareAndSet(null, it) }
    }

    private fun findVisitorData(element: JsonElement): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::findVisitorData)
        is JsonPrimitive -> element.contentOrNull?.takeIf(visitorPattern::matches)
        else -> null
    }
}
