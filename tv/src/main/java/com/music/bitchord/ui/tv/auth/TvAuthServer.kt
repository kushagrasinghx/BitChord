package com.music.bitchord.ui.tv.auth

import android.util.Log
import com.music.bitchord.data.sources.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

object TvAuthServer {

    private const val TAG = "TvAuthServer"
    private const val DEFAULT_PORT = 8765

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

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
                    // 1. Custom Addon / Source Submission
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

                    // 2. Toggle Addon Active State
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

                    // 3. Remove / Delete Addon
                    path == "/api/addon/delete" && method == "POST" -> {
                        val id = parseFormParam(requestBody, "id") ?: parseQueryParam(query, "id")
                        if (!id.isNullOrBlank()) {
                            SourceRegistry.remove(id)
                            sendJsonResponse(writer, """{"success":true}""")
                        } else {
                            sendJsonResponse(writer, """{"success":false,"error":"No ID"}""")
                        }
                    }

                    // 4. Update Existing Addon URL
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

                    // 5. Reorder Addons Priority
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

                    // 6. Default: Serve Addon Management Portal for all web requests (/, /addons, etc.)
                    else -> {
                        sendHtmlResponse(writer, getAddonsOnlyHtml())
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
  justify-content: center;
  padding: 8px 6px 4px;
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
  <!-- Brand Bar -->
  <div class="brand-bar">
    <div class="brand-identity">
      <svg class="brand-logo-svg" viewBox="0 0 730 484" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path fill="#FF2A55" d="M6.73729 316.937C20.201 294.159 49.6986 286.649 72.4033 300.174H72.4277C237.603 398.282 510.121 407.302 667.823 359.439C693.147 351.759 719.927 366.055 727.602 391.378C735.276 416.715 720.992 443.477 695.649 451.176C514.618 506.132 213.674 495.514 23.5058 382.622C0.678772 369.103 -6.78757 339.685 6.73729 316.937ZM77.8135 154.865C89.38 136.046 113.982 130.11 132.77 141.676C270.528 226.369 480.519 250.891 643.46 201.43C664.592 195.047 686.911 206.957 693.324 228.052C699.689 249.184 687.774 271.46 666.679 277.886C480.555 334.36 249.169 307.004 90.9707 209.79C72.1827 198.224 66.247 173.628 77.8135 154.865ZM140.542 15.3012C149.722 0.246392 169.428 -4.52761 184.482 4.71335C304.811 78.2128 456.29 94.8593 634.684 54.101C651.874 50.1843 669.01 60.9551 672.927 78.1518C676.862 95.3486 666.134 112.484 648.9 116.401C453.677 161.021 286.219 141.799 151.129 59.2417C136.074 50.0006 131.301 30.3561 140.542 15.3012Z"/>
      </svg>
      <span class="brand-name">BitChord <span class="brand-tv-pill">TV</span></span>
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
}
