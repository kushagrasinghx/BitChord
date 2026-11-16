package com.music.bitchord.ui.tv.auth

import android.content.Context
import android.util.Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

data class TvRemoteStatus(
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val currentPositionMs: Long,
    val durationMs: Long,
)

object TvAuthServer {

    private const val TAG = "TvAuthServer"
    private const val DEFAULT_PORT = 8765

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Remote callbacks
    var statusProvider: (() -> TvRemoteStatus)? = null
    var onPlayPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onSeekAction: ((Long) -> Unit)? = null
    var onPlaySongAction: ((Song) -> Unit)? = null
    var onKeyAction: ((String) -> Unit)? = null

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallbackIp: String? = null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                        if (fallbackIp == null) fallbackIp = host
                    }
                }
            }
            return fallbackIp
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve local IP", e)
        }
        return null
    }

    suspend fun start(
        onCookieReceived: ((String) -> Unit)? = null,
    ): Pair<Int, String>? = withContext(Dispatchers.IO) {
        stop()

        val ip = getLocalIpAddress() ?: return@withContext null
        try {
            val socket = try {
                ServerSocket(DEFAULT_PORT)
            } catch (_: Exception) {
                ServerSocket(0)
            }
            serverSocket = socket
            isRunning.set(true)
            val port = socket.localPort

            Thread {
                while (isRunning.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        handleClient(client, onCookieReceived)
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }.start()

            Pair(port, ip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TV server", e)
            null
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: Socket, onCookieReceived: ((String) -> Unit)?) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                val requestLine = reader.readLine() ?: return@Thread
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"
                val path = rawPath.substringBefore("?")
                val query = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""

                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) break
                    if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                var requestBody = ""
                if (contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val count = reader.read(bodyChars, read, contentLength - read)
                        if (count <= 0) break
                        read += count
                    }
                    requestBody = String(bodyChars)
                }

                when {
                    // 1. API: Get Current Status
                    path == "/api/status" -> {
                        val status = statusProvider?.invoke() ?: TvRemoteStatus(false, "No Song Playing", "BitChord TV", null, 0, 0)
                        val json = JSONObject().apply {
                            put("isPlaying", status.isPlaying)
                            put("title", status.title)
                            put("artist", status.artist)
                            put("artworkUrl", status.artworkUrl ?: "")
                            put("currentPositionMs", status.currentPositionMs)
                            put("durationMs", status.durationMs)
                        }
                        sendJsonResponse(writer, json.toString())
                    }

                    // 2. API: Control Playback Actions
                    path == "/api/action" -> {
                        val action = if (method == "POST") {
                            parseFormParam(requestBody, "action") ?: parseQueryParam(query, "action")
                        } else {
                            parseQueryParam(query, "action")
                        }

                        when (action) {
                            "play", "pause", "play_pause" -> onPlayPauseAction?.invoke()
                            "next" -> onNextAction?.invoke()
                            "prev", "previous" -> onPreviousAction?.invoke()
                            "seek" -> {
                                val ms = (parseFormParam(requestBody, "ms") ?: parseQueryParam(query, "ms"))?.toLongOrNull() ?: 0L
                                onSeekAction?.invoke(ms)
                            }
                            "key_up", "key_down", "key_left", "key_right", "key_select", "key_back" -> {
                                onKeyAction?.invoke(action)
                            }
                        }
                        sendJsonResponse(writer, """{"success":true}""")
                    }

                    // 3. API: Search Music from Phone
                    path == "/api/search" -> {
                        val q = parseQueryParam(query, "q") ?: ""
                        if (q.isBlank()) {
                            sendJsonResponse(writer, "[]")
                        } else {
                            scope.launch {
                                val results = YtMusicRepository.search(q, SearchFilter.SONGS).getOrDefault(emptyList())
                                val arr = JSONArray()
                                results.take(15).forEach { item ->
                                    val song = (item as? SearchResult.Track)?.song
                                    if (song != null) {
                                        val itemObj = JSONObject().apply {
                                            put("videoId", song.videoId)
                                            put("title", song.title)
                                            put("subtitle", song.artist)
                                            put("thumbnailUrl", song.thumbnailUrl ?: "")
                                        }
                                        arr.put(itemObj)
                                    }
                                }
                                sendJsonResponse(writer, arr.toString())
                            }
                        }
                    }

                    // 4. API: Play Song from Phone Search
                    path == "/api/play" -> {
                        val videoId = parseFormParam(requestBody, "videoId") ?: parseQueryParam(query, "videoId")
                        val title = parseFormParam(requestBody, "title") ?: parseQueryParam(query, "title") ?: "Track"
                        val artist = parseFormParam(requestBody, "artist") ?: parseQueryParam(query, "artist") ?: ""
                        val thumb = parseFormParam(requestBody, "thumbnailUrl") ?: parseQueryParam(query, "thumbnailUrl")

                        if (!videoId.isNullOrBlank()) {
                            val song = Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumb)
                            onPlaySongAction?.invoke(song)
                        }
                        sendJsonResponse(writer, """{"success":true}""")
                    }

                    // 5. Auth Cookie Submission
                    path == "/submit" && method == "POST" -> {
                        val cookieValue = parseCookieFromBody(requestBody)
                        if (!cookieValue.isNullOrBlank()) {
                            onCookieReceived?.invoke(cookieValue)
                            sendHtmlResponse(writer, getSuccessHtml())
                        } else {
                            sendHtmlResponse(writer, getAuthHtml(error = "No cookie detected. Please follow the instructions."))
                        }
                    }

                    // 5b. Custom Addon / Source Submission
                    path == "/api/addon" && method == "POST" -> {
                        val addonUrl = parseFormParam(requestBody, "addonUrl") ?: parseQueryParam(query, "addonUrl")
                        if (!addonUrl.isNullOrBlank()) {
                            scope.launch {
                                try {
                                    val found = withContext(Dispatchers.IO) {
                                        SourceRegistry.identify(addonUrl.trim()).getOrThrow()
                                    }
                                    SourceRegistry.add(found)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to identify addon from web: $addonUrl", e)
                                }
                            }
                            sendJsonResponse(writer, """{"success":true}""")
                        } else {
                            sendJsonResponse(writer, """{"success":false,"error":"No URL"}""")
                        }
                    }

                    // 5c. Toggle Addon Active State
                    path == "/api/addon/toggle" && method == "POST" -> {
                        val id = parseFormParam(requestBody, "id") ?: parseQueryParam(query, "id")
                        if (!id.isNullOrBlank()) {
                            val cfg = SourceRegistry.config(id)
                            if (cfg != null) {
                                SourceRegistry.setEnabled(id, !cfg.enabled)
                            }
                            sendJsonResponse(writer, """{"success":true}""")
                        } else {
                            sendJsonResponse(writer, """{"success":false,"error":"No ID"}""")
                        }
                    }

                    // 5d. Remove / Delete Addon
                    path == "/api/addon/delete" && method == "POST" -> {
                        val id = parseFormParam(requestBody, "id") ?: parseQueryParam(query, "id")
                        if (!id.isNullOrBlank()) {
                            SourceRegistry.remove(id)
                            sendJsonResponse(writer, """{"success":true}""")
                        } else {
                            sendJsonResponse(writer, """{"success":false,"error":"No ID"}""")
                        }
                    }

                    // 5e. Update Existing Addon URL
                    path == "/api/addon/update" && method == "POST" -> {
                        val id = parseFormParam(requestBody, "id") ?: parseQueryParam(query, "id")
                        val url = parseFormParam(requestBody, "url") ?: parseQueryParam(query, "url")
                        if (!id.isNullOrBlank() && !url.isNullOrBlank()) {
                            val existing = SourceRegistry.config(id)
                            if (existing != null) {
                                scope.launch {
                                    try {
                                        val updated = withContext(Dispatchers.IO) {
                                            SourceRegistry.identify(url.trim(), existing).getOrThrow()
                                        }
                                        SourceRegistry.update(updated)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to update addon $id with $url", e)
                                    }
                                }
                                sendJsonResponse(writer, """{"success":true}""")
                            } else {
                                sendJsonResponse(writer, """{"success":false,"error":"Addon not found"}""")
                            }
                        } else {
                            sendJsonResponse(writer, """{"success":false,"error":"Missing parameters"}""")
                        }
                    }

                    // 5f. Reorder Addons Priority
                    path == "/api/addon/reorder" && method == "POST" -> {
                        val orderParam = parseFormParam(requestBody, "order") ?: parseQueryParam(query, "order")
                        if (!orderParam.isNullOrBlank()) {
                            val ids = orderParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            SourceRegistry.reorderAddons(ids)
                            sendJsonResponse(writer, """{"success":true}""")
                        } else {
                            sendJsonResponse(writer, """{"success":false,"error":"No order specified"}""")
                        }
                    }

                    // 6. Mobile Web Remote Control Page (/remote)
                    path == "/remote" -> {
                        sendHtmlResponse(writer, getRemoteControlHtml())
                    }

                    // 6b. Dedicated Addon Setup Page (/addons)
                    path == "/addons" -> {
                        sendHtmlResponse(writer, getAddonsOnlyHtml())
                    }

                    // 7. Default Landing & Auth Page (/)
                    else -> {
                        sendHtmlResponse(writer, getAuthHtml())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client error in TvAuthServer", e)
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun sendJsonResponse(writer: PrintWriter, json: String) {
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Content-Length: ${json.toByteArray().size}\r\n")
        writer.print("\r\n")
        writer.print(json)
        writer.flush()
    }

    private fun sendHtmlResponse(writer: PrintWriter, html: String) {
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${html.toByteArray().size}\r\n")
        writer.print("\r\n")
        writer.print(html)
        writer.flush()
    }

    private fun parseFormParam(body: String, param: String): String? {
        val pairs = body.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                if (key == param) {
                    return URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                }
            }
        }
        return null
    }

    private fun parseQueryParam(query: String, param: String): String? {
        return parseFormParam(query, param)
    }

    private fun parseCookieFromBody(body: String): String? {
        if (body.startsWith("cookie=")) {
            val raw = body.substring("cookie=".length)
            return try {
                URLDecoder.decode(raw, "UTF-8").trim()
            } catch (_: Exception) {
                raw.trim()
            }
        }
        val pairs = body.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8").trim()
                if (key == "cookie" && value.isNotBlank()) {
                    return value
                }
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML Templates: Apple TV Mobile Remote & Google Sign-In Pages
    // ─────────────────────────────────────────────────────────────────────────

    private fun getRemoteControlHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>BitChord TV • Mobile Remote</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
body { background: #08080B; color: #FFF; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif; min-height: 100vh; padding: 18px; display: flex; flex-direction: column; align-items: center; }
.header { display: flex; align-items: center; gap: 10px; width: 100%; max-width: 400px; padding: 10px 0 16px; }
.logo-badge { width: 34px; height: 34px; border-radius: 50%; background: #FA2D48; display: flex; align-items: center; justify-content: center; font-size: 18px; }
.header h1 { font-size: 20px; font-weight: 800; letter-spacing: -0.4px; }
.card { width: 100%; max-width: 400px; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12); border-radius: 24px; padding: 20px; margin-bottom: 16px; backdrop-filter: blur(20px); }
.now-playing { display: flex; gap: 14px; align-items: center; margin-bottom: 14px; }
.art { width: 68px; height: 68px; border-radius: 14px; background: #222; object-fit: cover; box-shadow: 0 8px 20px rgba(0,0,0,0.5); }
.info { flex: 1; min-width: 0; }
.title { font-size: 16px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.artist { font-size: 13px; color: #A0A0AB; margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.progress-bar { width: 100%; height: 6px; background: rgba(255,255,255,0.2); border-radius: 3px; position: relative; margin: 12px 0 6px; cursor: pointer; }
.progress-fill { height: 100%; background: #FA2D48; border-radius: 3px; width: 0%; transition: width 0.3s linear; }
.time-row { display: flex; justify-content: space-between; font-size: 11px; color: #888; }
.transport-row { display: flex; justify-content: center; align-items: center; gap: 20px; margin-top: 14px; }
.btn { border: none; outline: none; background: rgba(255,255,255,0.15); color: #FFF; border-radius: 50%; width: 50px; height: 50px; display: flex; align-items: center; justify-content: center; font-size: 20px; cursor: pointer; active { transform: scale(0.92); } }
.btn-play { width: 64px; height: 64px; background: #FA2D48; font-size: 26px; box-shadow: 0 4px 18px rgba(250,45,72,0.4); }
.dpad-card { width: 100%; max-width: 400px; display: flex; flex-direction: column; align-items: center; gap: 8px; margin-bottom: 16px; }
.dpad-row { display: flex; gap: 8px; }
.dpad-btn { width: 72px; height: 54px; border-radius: 14px; background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.15); color: #FFF; font-size: 16px; font-weight: bold; cursor: pointer; }
.dpad-ok { background: #FA2D48; border-color: #FA2D48; }
.search-card { width: 100%; max-width: 400px; }
.search-input { width: 100%; height: 44px; border-radius: 14px; background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.18); color: #FFF; padding: 0 14px; font-size: 15px; outline: none; }
.search-results { margin-top: 10px; max-height: 220px; overflow-y: auto; }
.result-item { display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 10px; cursor: pointer; margin-bottom: 4px; }
.result-item:hover { background: rgba(255,255,255,0.1); }
.result-thumb { width: 40px; height: 40px; border-radius: 8px; object-fit: cover; }
.result-text { flex: 1; min-width: 0; }
.result-title { font-size: 13px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.result-sub { font-size: 11px; color: #888; }
</style>
</head>
<body>
<div class="header">
  <div class="logo-badge">♪</div>
  <h1>BitChord TV Remote</h1>
</div>

<div class="card">
  <div class="now-playing">
    <img id="trackArt" class="art" src="" style="display:none;" />
    <div class="info">
      <div id="trackTitle" class="title">Loading TV...</div>
      <div id="trackArtist" class="artist">Connecting to BitChord TV</div>
    </div>
  </div>
  <div class="progress-bar" id="pBar" onclick="handleSeek(event)">
    <div class="progress-fill" id="pFill"></div>
  </div>
  <div class="time-row">
    <span id="tElapsed">0:00</span>
    <span id="tRemain">-0:00</span>
  </div>
  <div class="transport-row">
    <button class="btn" onclick="sendAction('prev')">⏮</button>
    <button class="btn btn-play" id="playBtn" onclick="sendAction('play_pause')">▶</button>
    <button class="btn" onclick="sendAction('next')">⏭</button>
  </div>
</div>

<div class="card dpad-card">
  <div class="dpad-row"><button class="dpad-btn" onclick="sendAction('key_up')">▲</button></div>
  <div class="dpad-row">
    <button class="dpad-btn" onclick="sendAction('key_left')">◀</button>
    <button class="dpad-btn dpad-ok" onclick="sendAction('key_select')">OK</button>
    <button class="dpad-btn" onclick="sendAction('key_right')">▶</button>
  </div>
  <div class="dpad-row"><button class="dpad-btn" onclick="sendAction('key_down')">▼</button></div>
  <div class="dpad-row" style="margin-top: 6px;">
    <button class="dpad-btn" style="width: 110px;" onclick="sendAction('key_back')">Back</button>
  </div>
</div>

<div class="card search-card">
  <input type="text" class="search-input" id="sQuery" placeholder="Search song to play on TV..." onkeydown="if(event.key==='Enter')searchMusic()" />
  <div class="search-results" id="sResults"></div>
</div>

<script>
let dur = 0;
function fmtTime(ms) {
  if (ms <= 0) return "0:00";
  let s = Math.floor(ms / 1000), m = Math.floor(s / 60), r = s % 60;
  return m + ":" + (r < 10 ? "0" : "") + r;
}
function updateStatus() {
  fetch('/api/status').then(r => r.json()).then(data => {
    document.getElementById('trackTitle').innerText = data.title || "No Song Playing";
    document.getElementById('trackArtist').innerText = data.artist || "BitChord TV";
    let art = document.getElementById('trackArt');
    if (data.artworkUrl) { art.src = data.artworkUrl; art.style.display = 'block'; } else { art.style.display = 'none'; }
    document.getElementById('playBtn').innerText = data.isPlaying ? '❚❚' : '▶';
    dur = data.durationMs || 1;
    let pos = data.currentPositionMs || 0;
    let pct = Math.min(100, Math.max(0, (pos / dur) * 100));
    document.getElementById('pFill').style.width = pct + '%';
    document.getElementById('tElapsed').innerText = fmtTime(pos);
    document.getElementById('tRemain').innerText = '-' + fmtTime(Math.max(0, dur - pos));
  }).catch(() => {});
}
function sendAction(act, extra = '') {
  fetch('/api/action?action=' + act + extra, { method: 'POST' });
  setTimeout(updateStatus, 150);
}
function handleSeek(e) {
  let rect = document.getElementById('pBar').getBoundingClientRect();
  let pct = (e.clientX - rect.left) / rect.width;
  let targetMs = Math.floor(pct * dur);
  sendAction('seek', '&ms=' + targetMs);
}
function searchMusic() {
  let q = document.getElementById('sQuery').value;
  if (!q) return;
  fetch('/api/search?q=' + encodeURIComponent(q)).then(r => r.json()).then(items => {
    let container = document.getElementById('sResults');
    container.innerHTML = '';
    items.forEach(item => {
      let div = document.createElement('div');
      div.className = 'result-item';
      div.innerHTML = '<img class="result-thumb" src="' + (item.thumbnailUrl || '') + '" /><div class="result-text"><div class="result-title">' + item.title + '</div><div class="result-sub">' + item.subtitle + '</div></div>';
      div.onclick = () => {
        fetch('/api/play?videoId=' + encodeURIComponent(item.videoId) + '&title=' + encodeURIComponent(item.title) + '&artist=' + encodeURIComponent(item.subtitle) + '&thumbnailUrl=' + encodeURIComponent(item.thumbnailUrl), { method: 'POST' });
        container.innerHTML = '<div style="padding:10px;text-align:center;color:#00E5FF;">Playing ' + item.title + ' on TV!</div>';
      };
      container.appendChild(div);
    });
  });
}
setInterval(updateStatus, 1200);
updateStatus();
</script>
</body>
</html>
""".trimIndent()

    private fun getAuthHtml(error: String? = null): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>BitChord TV • 1-Tap Sign In</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #08080B; color: #FFF; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif; padding: 24px 16px; display: flex; flex-direction: column; align-items: center; }
.card { width: 100%; max-width: 460px; background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.14); border-radius: 24px; padding: 24px; margin-bottom: 20px; backdrop-filter: blur(24px); }
.btn-google { width: 100%; height: 52px; background: #FFFFFF; color: #000; border: none; border-radius: 16px; font-size: 16px; font-weight: 700; cursor: pointer; text-decoration: none; display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 14px; box-shadow: 0 4px 16px rgba(255,255,255,0.15); }
.btn-primary { width: 100%; height: 50px; background: #FA2D48; color: #FFF; border: none; border-radius: 14px; font-size: 16px; font-weight: bold; cursor: pointer; text-decoration: none; display: flex; align-items: center; justify-content: center; margin-top: 12px; }
.btn-secondary { width: 100%; height: 48px; background: rgba(255,255,255,0.14); color: #FFF; border: none; border-radius: 14px; font-size: 15px; font-weight: 600; cursor: pointer; text-decoration: none; display: flex; align-items: center; justify-content: center; margin-top: 12px; }
textarea { width: 100%; height: 90px; background: rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.2); border-radius: 14px; color: #FFF; padding: 12px; font-size: 13px; font-family: monospace; margin-top: 10px; outline: none; }
.step-box { background: rgba(255,255,255,0.05); border-radius: 14px; padding: 14px; margin-top: 14px; font-size: 13px; line-height: 1.5; color: #DDD; }
</style>
</head>
<body>
<div class="card" style="text-align: center;">
  <div style="width: 52px; height: 52px; border-radius: 50%; background: #FA2D48; margin: 0 auto 12px; display: flex; align-items: center; justify-content: center; font-size: 24px;">♪</div>
  <h1 style="font-size: 22px; font-weight: 800; margin-bottom: 6px;">Sign In to BitChord TV</h1>
  <p style="font-size: 14px; color: #A0A0AB;">Fast & easy login from your mobile phone</p>

  <div class="step-box" style="text-align: left; margin-top: 18px;">
    <strong>Option 1 (Fastest):</strong>
    <ol style="margin-left: 18px; margin-top: 6px;">
      <li>Tap the button below to open YouTube Music in your browser.</li>
      <li>Sign in with your Google Account if not already logged in.</li>
      <li>Return here and paste your cookie, or use the 1-click script to log in automatically!</li>
    </ol>
  </div>

  <a href="https://music.youtube.com" target="_blank" class="btn-google">
    <svg width="20" height="20" viewBox="0 0 24 24"><path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"/><path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.34 24 12 24z"/><path fill="#FBBC05" d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.18 0 9.99 0 12s.45 3.82 1.25 5.42l4.03-3.15z"/><path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.34 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98z"/></svg>
    Open YouTube Music on Phone
  </a>
</div>

<div class="card">
  <h2 style="font-size: 17px; font-weight: bold; margin-bottom: 8px;">Direct Session Submission</h2>
  <p style="font-size: 13px; color: #AAA; line-height: 1.4;">Paste your YouTube Music session cookie / headers to immediately log in on TV:</p>
  ${if (error != null) "<div style='color:#FF375F;font-size:13px;margin-top:8px;'>$error</div>" else ""}
  <form action="/submit" method="POST">
    <textarea name="cookie" placeholder="Paste SAPISID / Session Cookie here..."></textarea>
    <button type="submit" class="btn-primary">Connect Account to TV</button>
  </form>
</div>

<div class="card">
  <h2 style="font-size: 17px; font-weight: bold; margin-bottom: 8px;">Audio Stream Addons</h2>
  <p style="font-size: 13px; color: #AAA; line-height: 1.4;">Paste your custom Addon Manifest URL to connect custom audio sources on this TV:</p>
  <form id="addonForm" style="margin-top: 10px;">
    <textarea id="addonUrl" placeholder="https://your-domain/manifest.json" style="height: 54px;"></textarea>
    <button type="button" class="btn-primary" onclick="submitAddon()">Add Custom Addon</button>
  </form>
  <div id="addonMsg" style="margin-top: 8px; font-size: 13px; text-align: center;"></div>

  ${run {
    val addons = SourceRegistry.configs.value.filter { it.kind == SourceKind.ADDON }
    if (addons.isNotEmpty()) {
        val items = addons.joinToString("") { addon ->
            """
            <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.06); padding:10px 14px; border-radius:12px; margin-top:8px;">
              <div style="text-align:left; overflow:hidden; margin-right:8px;">
                <div style="font-weight:600; font-size:14px; color:#FFF;">${addon.displayName}</div>
                <div style="font-size:11px; color:#888; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:200px;">${addon.baseUrl}</div>
              </div>
              <div style="display:flex; gap:6px; flex-shrink:0;">
                <button type="button" onclick="toggleAddon('${addon.id}')" style="background:${if (addon.enabled) "rgba(0,229,255,0.2)" else "rgba(255,255,255,0.1)"}; color:${if (addon.enabled) "#00E5FF" else "#AAA"}; border:none; border-radius:8px; padding:6px 10px; font-size:12px; font-weight:bold; cursor:pointer;">
                  ${if (addon.enabled) "Active" else "Disabled"}
                </button>
                <button type="button" onclick="deleteAddon('${addon.id}')" style="background:rgba(255,55,95,0.15); color:#FF375F; border:none; border-radius:8px; padding:6px 9px; font-size:12px; font-weight:bold; cursor:pointer;">
                  ✕
                </button>
              </div>
            </div>
            """.trimIndent()
        }
        """
        <div style="margin-top:16px; border-top:1px solid rgba(255,255,255,0.1); padding-top:12px;">
          <div style="font-size:13px; font-weight:600; color:#AAA; margin-bottom:4px; text-align:left;">Configured TV Addons:</div>
          $items
        </div>
        """.trimIndent()
    } else ""
  }}
</div>
<script>
function toggleAddon(id) {
  fetch('/api/addon/toggle', { method: 'POST', body: 'id=' + encodeURIComponent(id) })
    .then(() => location.reload());
}
function deleteAddon(id) {
  if (confirm('Delete this addon from TV?')) {
    fetch('/api/addon/delete', { method: 'POST', body: 'id=' + encodeURIComponent(id) })
      .then(() => location.reload());
  }
}
function submitAddon() {
  let url = document.getElementById('addonUrl').value;
  let msg = document.getElementById('addonMsg');
  if (!url) return;
  msg.innerHTML = '<span style="color:#AAA;">Connecting to addon...</span>';
  fetch('/api/addon', { method: 'POST', body: 'addonUrl=' + encodeURIComponent(url) })
    .then(r => r.json()).then(d => {
      msg.innerHTML = d.success ? '<span style="color:#00E5FF;font-weight:bold;">✓ Addon sent to TV successfully!</span>' : '<span style="color:#FF375F;">Failed to add addon.</span>';
      if (d.success) {
        document.getElementById('addonUrl').value = '';
        setTimeout(() => location.reload(), 1000);
      }
    }).catch(e => { msg.innerHTML = '<span style="color:#FF375F;">Network error</span>'; });
}
</script>
</body>
</html>
""".trimIndent()

    private fun getAddonsOnlyHtml(): String {
        val addons = SourceRegistry.configs.value.filter { it.kind.isUserAdded }
        val addonsListHtml = if (addons.isNotEmpty()) {
            addons.mapIndexed { index, addon ->
                val canMoveUp = index > 0
                val canMoveDown = index < addons.size - 1
                """
                <div class="addon-card">
                  <div class="addon-header">
                    <div class="addon-title-wrap">
                      <span class="addon-badge">#${index + 1}</span>
                      <span class="addon-name">${addon.displayName}</span>
                    </div>
                    <button type="button" onclick="toggleAddon('${addon.id}')" class="status-pill ${if (addon.enabled) "status-active" else "status-disabled"}">
                      ${if (addon.enabled) "● Active" else "○ Disabled"}
                    </button>
                  </div>

                  <div class="url-edit-bar">
                    <input type="text" id="url_${addon.id}" value="${addon.baseUrl}" class="url-field" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false">
                    <button type="button" onclick="updateAddonUrl('${addon.id}')" class="btn-save">
                      Save
                    </button>
                  </div>
                  <div id="msg_${addon.id}" class="inline-msg"></div>

                  <div class="addon-footer">
                    <div class="priority-group">
                      <span class="priority-label">Priority:</span>
                      <button type="button" onclick="moveAddon(${index}, -1)" class="btn-arrow" ${if (!canMoveUp) "disabled" else ""}>▲</button>
                      <button type="button" onclick="moveAddon(${index}, 1)" class="btn-arrow" ${if (!canMoveDown) "disabled" else ""}>▼</button>
                    </div>
                    <button type="button" onclick="deleteAddon('${addon.id}')" class="btn-delete">
                      ✕ Delete
                    </button>
                  </div>
                </div>
                """.trimIndent()
            }.joinToString("")
        } else {
            """<div class="empty-state">No custom addons configured yet. Enter a manifest URL above to get started.</div>"""
        }

        val addonIdsJs = addons.joinToString(",") { "'${it.id}'" }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>BitChord TV • Audio Sources & Addons</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
body {
  background: #09090C;
  color: #EDEDED;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  padding: 24px 16px 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
}
.container {
  width: 100%;
  max-width: 440px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.brand-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 8px;
}
.brand-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-logo-svg {
  width: 32px;
  height: 22px;
}
.brand-name {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.3px;
  color: #FFFFFF;
  display: flex;
  align-items: center;
  gap: 6px;
}
.brand-tv-pill {
  font-size: 10px;
  font-weight: 800;
  text-transform: uppercase;
  background: rgba(255, 42, 85, 0.18);
  color: #FF2A55;
  padding: 2px 6px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}
.nav-pills {
  display: flex;
  gap: 6px;
}
.nav-pill {
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  color: #888899;
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  transition: all 0.2s;
}
.nav-pill.active {
  color: #FFFFFF;
  background: rgba(255, 255, 255, 0.12);
}
.nav-pill:hover {
  color: #FFFFFF;
}
.card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 20px;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}
.header-card {
  text-align: center;
  padding: 22px 18px;
}
.header-card h1 {
  font-size: 20px;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: -0.3px;
  margin-bottom: 4px;
}
.header-card p {
  font-size: 13px;
  color: #8E8E9F;
  line-height: 1.4;
}
.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #FFFFFF;
  margin-bottom: 6px;
  letter-spacing: -0.2px;
}
.card-subtitle {
  font-size: 13px;
  color: #8E8E9F;
  line-height: 1.4;
  margin-bottom: 14px;
}
.input-wrap {
  width: 100%;
  margin-bottom: 12px;
}
.text-input {
  width: 100%;
  height: 48px;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  color: #FFFFFF;
  padding: 0 14px;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  outline: none;
  transition: border-color 0.2s;
}
.text-input:focus {
  border-color: #FF2A55;
}
.btn-primary {
  width: 100%;
  height: 46px;
  background: #FF2A55;
  color: #FFFFFF;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.2s, transform 0.1s;
  box-shadow: 0 4px 14px rgba(255, 42, 85, 0.28);
}
.btn-primary:active {
  transform: scale(0.98);
}
.addon-card {
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 10px;
}
.addon-card:last-child {
  margin-bottom: 0;
}
.addon-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.addon-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  padding-right: 8px;
}
.addon-badge {
  font-size: 11px;
  font-weight: 700;
  color: #9999AA;
  background: rgba(255, 255, 255, 0.08);
  padding: 2px 7px;
  border-radius: 6px;
  flex-shrink: 0;
}
.addon-name {
  font-size: 15px;
  font-weight: 700;
  color: #FFFFFF;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.status-pill {
  border: none;
  border-radius: 20px;
  padding: 4px 11px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}
.status-active {
  background: rgba(0, 229, 255, 0.15);
  color: #00E5FF;
}
.status-disabled {
  background: rgba(255, 255, 255, 0.08);
  color: #777788;
}
.url-edit-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.url-field {
  flex: 1;
  min-width: 0;
  height: 38px;
  background: rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #E2E2E8;
  padding: 0 10px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, monospace;
  outline: none;
}
.url-field:focus {
  border-color: #FF2A55;
}
.btn-save {
  height: 38px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.12);
  color: #FFFFFF;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.2s;
}
.btn-save:hover {
  background: rgba(255, 255, 255, 0.18);
}
.addon-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 4px;
}
.priority-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.priority-label {
  font-size: 12px;
  color: #777788;
  margin-right: 2px;
}
.btn-arrow {
  width: 30px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.08);
  border: none;
  border-radius: 8px;
  color: #E0E0EA;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-arrow:disabled {
  opacity: 0.25;
  cursor: default;
}
.btn-delete {
  background: transparent;
  color: #FF456A;
  border: 1px solid rgba(255, 69, 106, 0.25);
  border-radius: 8px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-delete:hover {
  background: rgba(255, 69, 106, 0.12);
}
.empty-state {
  text-align: center;
  padding: 24px 12px;
  color: #777788;
  font-size: 13px;
  line-height: 1.5;
}
.inline-msg {
  font-size: 12px;
  margin-bottom: 6px;
  text-align: right;
}
</style>
</head>
<body>
<div class="container">
  <!-- Brand & Navigation Bar -->
  <div class="brand-bar">
    <div class="brand-identity">
      <svg class="brand-logo-svg" viewBox="0 0 730 484" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path fill="#FF2A55" d="M6.73729 316.937C20.201 294.159 49.6986 286.649 72.4033 300.174H72.4277C237.603 398.282 510.121 407.302 667.823 359.439C693.147 351.759 719.927 366.055 727.602 391.378C735.276 416.715 720.992 443.477 695.649 451.176C514.618 506.132 213.674 495.514 23.5058 382.622C0.678772 369.103 -6.78757 339.685 6.73729 316.937ZM77.8135 154.865C89.38 136.046 113.982 130.11 132.77 141.676C270.528 226.369 480.519 250.891 643.46 201.43C664.592 195.047 686.911 206.957 693.324 228.052C699.689 249.184 687.774 271.46 666.679 277.886C480.555 334.36 249.169 307.004 90.9707 209.79C72.1827 198.224 66.247 173.628 77.8135 154.865ZM140.542 15.3012C149.722 0.246392 169.428 -4.52761 184.482 4.71335C304.811 78.2128 456.29 94.8593 634.684 54.101C651.874 50.1843 669.01 60.9551 672.927 78.1518C676.862 95.3486 666.134 112.484 648.9 116.401C453.677 161.021 286.219 141.799 151.129 59.2417C136.074 50.0006 131.301 30.3561 140.542 15.3012Z"/>
      </svg>
      <span class="brand-name">BitChord <span class="brand-tv-pill">TV</span></span>
    </div>
    <div class="nav-pills">
      <a href="/addons" class="nav-pill active">Sources</a>
      <a href="/remote" class="nav-pill">Remote</a>
      <a href="/" class="nav-pill">Account</a>
    </div>
  </div>

  <!-- Header Card -->
  <div class="card header-card">
    <h1>Audio Stream Addons</h1>
    <p>Connect and prioritize custom audio stream sources on this TV</p>
  </div>

  <!-- Add Addon Form -->
  <div class="card">
    <div class="card-title">Add Custom Addon</div>
    <div class="card-subtitle">Paste your Addon Manifest URL to connect custom audio sources:</div>
    <form id="addonForm" onsubmit="event.preventDefault(); submitAddon();">
      <div class="input-wrap">
        <input type="text" id="addonUrl" class="text-input" placeholder="https://your-domain/manifest.json" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false">
      </div>
      <button type="submit" class="btn-primary" id="btnSubmit">+ Connect Addon</button>
    </form>
    <div id="addonMsg" style="margin-top: 10px; font-size: 13px; text-align: center;"></div>
  </div>

  <!-- Configured TV Addons -->
  <div class="card">
    <div class="card-title">Configured TV Addons</div>
    <div class="card-subtitle">Active addons are prioritized during playback:</div>
    $addonsListHtml
  </div>
</div>

<script>
let currentAddonIds = [$addonIdsJs];

function moveAddon(idx, delta) {
  let target = idx + delta;
  if (target < 0 || target >= currentAddonIds.length) return;
  let temp = currentAddonIds[idx];
  currentAddonIds[idx] = currentAddonIds[target];
  currentAddonIds[target] = temp;
  fetch('/api/addon/reorder', { method: 'POST', body: 'order=' + encodeURIComponent(currentAddonIds.join(',')) })
    .then(() => location.reload());
}

function updateAddonUrl(id) {
  let url = document.getElementById('url_' + id).value.trim();
  let msg = document.getElementById('msg_' + id);
  if (!url) return;
  msg.innerHTML = '<span style="color:#AAA;">Updating...</span>';
  fetch('/api/addon/update', { method: 'POST', body: 'id=' + encodeURIComponent(id) + '&url=' + encodeURIComponent(url) })
    .then(r => r.json()).then(d => {
      if (d.success) {
        msg.innerHTML = '<span style="color:#00E5FF;font-weight:bold;">✓ URL updated!</span>';
        setTimeout(() => location.reload(), 800);
      } else {
        msg.innerHTML = '<span style="color:#FF375F;">Failed to update: ' + (d.error || 'Check URL') + '</span>';
      }
    }).catch(e => {
      msg.innerHTML = '<span style="color:#FF375F;">Network error</span>';
    });
}

function toggleAddon(id) {
  fetch('/api/addon/toggle', { method: 'POST', body: 'id=' + encodeURIComponent(id) })
    .then(() => location.reload());
}

function deleteAddon(id) {
  if (confirm('Delete this addon from TV?')) {
    fetch('/api/addon/delete', { method: 'POST', body: 'id=' + encodeURIComponent(id) })
      .then(() => location.reload());
  }
}

function submitAddon() {
  let url = document.getElementById('addonUrl').value.trim();
  let msg = document.getElementById('addonMsg');
  let btn = document.getElementById('btnSubmit');
  if (!url) return;
  msg.innerHTML = '<span style="color:#AAA;">Connecting to addon...</span>';
  btn.disabled = true;
  btn.style.opacity = '0.6';
  fetch('/api/addon', { method: 'POST', body: 'addonUrl=' + encodeURIComponent(url) })
    .then(r => r.json()).then(d => {
      if (d.success) {
        msg.innerHTML = '<span style="color:#00E5FF;font-weight:bold;">✓ Addon added to TV successfully!</span>';
        document.getElementById('addonUrl').value = '';
        setTimeout(() => location.reload(), 800);
      } else {
        msg.innerHTML = '<span style="color:#FF375F;">Failed to add addon</span>';
        btn.disabled = false;
        btn.style.opacity = '1';
      }
    }).catch(e => {
      msg.innerHTML = '<span style="color:#FF375F;">Network error</span>';
      btn.disabled = false;
      btn.style.opacity = '1';
    });
}
</script>
</body>
</html>
""".trimIndent()
    }

    private fun getSuccessHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>BitChord TV • Connected</title>
<style>
body { background: #08080B; color: #FFF; font-family: -apple-system, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; text-align: center; }
.card { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15); border-radius: 24px; padding: 32px; max-width: 380px; }
.btn { display: inline-block; background: #FA2D48; color: #FFF; padding: 12px 24px; border-radius: 14px; text-decoration: none; font-weight: bold; margin-top: 18px; }
</style>
</head>
<body>
<div class="card">
  <div style="font-size: 48px; margin-bottom: 12px;">✅</div>
  <h1 style="font-size: 22px; font-weight: bold; margin-bottom: 8px;">Paired Successfully!</h1>
  <p style="font-size: 14px; color: #AAA;">Your BitChord TV is now connected. You can now use the mobile remote to control playback.</p>
  <a href="/remote" class="btn">📱 Open TV Remote</a>
</div>
</body>
</html>
""".trimIndent()
}
