package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import com.my.kizzy.rpc.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import java.util.Locale
import java.util.UUID

/**
 * Publishes what is playing to Discord as a Rich Presence activity.
 *
 * Two routes, and the desktop gets the good one Android cannot have.
 *
 * **The local socket, preferred.** A Discord client running on this machine
 * listens on a named pipe, and an application sets a presence through it with
 * nothing but its own application id — no token, no login, no impersonation.
 * That is Discord's official Rich Presence mechanism and what every desktop
 * application uses. Android has no such client to talk to, which is the whole
 * reason its implementation looks the way it does.
 *
 * **The account gateway, as a fallback.** Android's route, ported: sign in as
 * the account and speak the client's own protocol, because on a phone there is
 * nothing else. It stays here for the case the socket cannot cover — Discord
 * not running locally, or running somewhere else — and it is offered the way
 * Android offers it, as the alternative rather than the front door, because it
 * hands an app full account access and account automation is against Discord's
 * terms.
 */
internal object DesktopDiscordRpc {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rpc: KizzyRPC? = null
    private var ipc: DesktopDiscordIpc? = null
    private var lastKey: String? = null

    private val _localClient = MutableStateFlow(false)

    /** Whether a Discord on this machine is answering, so no token is needed. */
    val localClient: StateFlow<Boolean> = _localClient

    /** Looks for a local Discord. Cheap, and the answer changes when one starts. */
    fun refreshLocalClient() {
        scope.launch {
            val found = DesktopDiscordIpc.connect(APPLICATION_ID)
            if (found != null) {
                ipc?.close()
                ipc = found
                _localClient.value = true
            } else {
                _localClient.value = ipc != null
            }
        }
    }

    private val _username = MutableStateFlow(DesktopPersistence().string(KEY_USERNAME, ""))

    /** Who the stored token belongs to, for the settings row to name. */
    val username: StateFlow<String> = _username

    private val _enabled = MutableStateFlow(DesktopPersistence().boolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled

    private val _token = MutableStateFlow(DesktopPersistence().string(KEY_TOKEN, ""))
    val token: StateFlow<String> = _token

    /** Checks a token by asking Discord who it belongs to, then keeps it. */
    suspend fun signIn(candidate: String): Result<UserInfo> =
        KizzyRPC.getUserInfo(candidate, USER_AGENT, superProperties())
            .onSuccess { user ->
                DesktopPersistence().saveString(KEY_TOKEN, candidate)
                DesktopPersistence().saveString(KEY_USERNAME, user.name)
                _token.value = candidate
                _username.value = user.name
                setEnabled(true)
            }

    fun signOut() {
        setEnabled(false)
        DesktopPersistence().saveString(KEY_TOKEN, "")
        DesktopPersistence().saveString(KEY_USERNAME, "")
        _token.value = ""
        _username.value = ""
    }

    fun setEnabled(value: Boolean) {
        DesktopPersistence().saveBoolean(KEY_ENABLED, value)
        _enabled.value = value
        if (!value) clear()
    }

    /**
     * Pushes the current track, or takes the presence down.
     *
     * Discord counts the progress bar down on its own clock from the start and
     * end instants, so a presence set once stays correct for the rest of the
     * track and only a real change is worth sending — which is what [lastKey]
     * decides. Playback speed is divided out of both instants: at 1.5x the
     * wall-clock time left is not the media time left, and a presence that
     * ignored that would finish its countdown mid-song.
     */
    fun onPlaybackStateChanged(state: DesktopPlaybackState, speed: Float) {
        if (!_enabled.value) return
        if (_token.value.isBlank() && ipc == null) return
        val song = state.song
        if (song == null || !state.isPlaying) {
            clear()
            return
        }
        val key = "${song.videoId}|${state.durationMs}|$speed"
        if (key == lastKey) return
        lastKey = key
        val position = state.positionMs
        val duration = state.durationMs.takeIf { it > 0 } ?: 0L
        scope.launch {
            val now = System.currentTimeMillis()
            val elapsed = (position / speed).toLong()
            val remaining = ((duration - position).coerceAtLeast(0L) / speed).toLong()
            val title = if (speed != 1.0f) {
                "${song.title} [${String.format(Locale.ROOT, "%.2fx", speed)}]"
            } else {
                song.title
            }
            // The socket first, and the gateway only if there is no socket:
            // publishing through both would put the same presence up twice.
            val local = ipc
            if (local != null) {
                val sent = runCatching {
                    local.setActivity(ipcActivity(song, title, now - elapsed, now + remaining))
                }.isSuccess
                if (sent) return@launch
                // A client that has quit takes its socket with it.
                ipc = null
                _localClient.value = false
            }
            if (_token.value.isBlank()) return@launch
            runCatching {
                val client = rpc ?: KizzyRPC(
                    token = _token.value,
                    os = if (DesktopPlatform.isWindows) "Windows" else "Linux",
                    browser = "Discord Client",
                    device = "desktop",
                    userAgent = USER_AGENT,
                    superPropertiesBase64 = superProperties(),
                ).also { rpc = it }

                client.setActivity(
                    name = DesktopDiscordSettings.activityName.value
                        .ifBlank { DesktopDiscordSettings.APP_NAME },
                    details = title,
                    state = song.artist,
                    detailsUrl = watchUrl(song),
                    // Never null: an activity with no large image falls back to
                    // whatever icon the application id points at, which is not
                    // ours to set.
                    largeImage = RpcImage.ExternalImage(
                        song.artworkAt(ART_PX)?.takeIf { it.startsWith("http") } ?: FALLBACK_ART_URL,
                    ),
                    smallImage = null,
                    largeText = song.albumName,
                    buttons = buttonLabels(song).takeIf { it.isNotEmpty() },
                    type = when (DesktopDiscordSettings.activityType.value) {
                        "playing" -> KizzyRPC.Type.PLAYING
                        "watching" -> KizzyRPC.Type.WATCHING
                        "competing" -> KizzyRPC.Type.COMPETING
                        else -> KizzyRPC.Type.LISTENING
                    },
                    statusDisplayType = if (DesktopDiscordSettings.useDetails.value) {
                        KizzyRPC.StatusDisplayType.DETAILS
                    } else {
                        KizzyRPC.StatusDisplayType.STATE
                    },
                    since = now,
                    startTime = now - elapsed,
                    endTime = now + remaining,
                    applicationId = APPLICATION_ID,
                    status = DesktopDiscordSettings.status.value,
                )
            }
        }
    }

    fun clear() {
        lastKey = null
        runCatching { ipc?.setActivity(null) }
        rpc?.closeRPC()
        rpc = null
    }

    /**
     * The activity as the local socket wants it.
     *
     * The same fields the gateway payload carries, in the shape SET_ACTIVITY
     * takes. Artwork goes over as a plain URL: mirroring it onto Discord's CDN
     * is what needs an account token, and the client resolves an http asset by
     * itself.
     */
    private fun ipcActivity(song: Song, title: String, startMs: Long, endMs: Long): JsonObject = buildJsonObject {
        put("type", DesktopDiscordSettings.activityNumber(DesktopDiscordSettings.activityType.value))
        DesktopDiscordSettings.activityName.value.takeIf(String::isNotBlank)?.let { put("name", it) }
        put("details", title)
        put("state", song.artist)
        // Which line Discord bolds, and repeats beside the name in a member
        // list — the whole point of "Lead with the song".
        put("status_display_type", if (DesktopDiscordSettings.useDetails.value) 1 else 2)
        putJsonObject("timestamps") {
            put("start", startMs)
            put("end", endMs)
        }
        putJsonObject("assets") {
            put("large_image", song.artworkAt(ART_PX)?.takeIf { it.startsWith("http") } ?: FALLBACK_ART_URL)
            song.albumName?.let { put("large_text", it) }
        }
        val labels = buttonLabels(song)
        if (labels.isNotEmpty()) {
            putJsonArray("buttons") {
                labels.forEach { (label, url) ->
                    add(buildJsonObject { put("label", label); put("url", url) })
                }
            }
        }
    }

    /** The buttons as configured: text with its variables filled in, and hidden ones dropped. */
    private fun buttonLabels(song: Song): List<Pair<String, String>> = buildList {
        val settings = DesktopDiscordSettings
        if (settings.button1Visible.value) {
            val text = settings.button1Text.value.ifEmpty { DesktopDiscordSettings.DEFAULT_BUTTON_1 }
            add(settings.resolveVariables(text, song.title, song.artist, song.albumName) to watchUrl(song))
        }
        if (settings.button2Visible.value) {
            val text = settings.button2Text.value.ifEmpty { DesktopDiscordSettings.DEFAULT_BUTTON_2 }
            add(settings.resolveVariables(text, song.title, song.artist, song.albumName) to PROJECT_URL)
        }
    }

    /**
     * The `X-Super-Properties` header Discord's own client sends.
     *
     * The gateway accepts a presence without it, but the endpoint that mirrors
     * external artwork onto Discord's CDN starts refusing a request with no
     * client fingerprint — so the cover art quietly stops appearing while
     * everything else keeps working. Built once, because rotating the ids per
     * request is what a real client never does.
     */
    private val superProperties: String by lazy {
        val fields = mapOf(
            "os" to if (DesktopPlatform.isWindows) "Windows" else "Linux",
            "browser" to "Discord Client",
            "device" to "",
            "system_locale" to Locale.getDefault().toString(),
            "client_version" to CLIENT_VERSION,
            "release_channel" to "stable",
            "client_build_number" to CLIENT_BUILD,
            "device_vendor_id" to UUID.randomUUID().toString(),
            "client_uuid" to UUID.randomUUID().toString(),
            "client_launch_id" to UUID.randomUUID().toString(),
        )
        val json = JsonObject(
            fields.mapValues { (key, value) ->
                if (key == "client_build_number") JsonPrimitive(value.toInt()) else JsonPrimitive(value)
            },
        )
        Base64.getEncoder().encodeToString(Json.encodeToString(JsonObject.serializer(), json).toByteArray())
    }

    private fun superProperties(): String = superProperties

    private fun watchUrl(song: Song) = "https://music.youtube.com/watch?v=${song.videoId}"

    private const val KEY_TOKEN = "discord_token"
    private const val KEY_USERNAME = "discord_username"
    private const val KEY_ENABLED = "discord_rpc_enabled"

    private const val APPLICATION_ID = "1411019391843172514"
    private const val PROJECT_URL = "https://github.com/kushagrasinghx/BitChord"
    private const val ART_PX = 480
    private const val FALLBACK_ART_URL =
        "https://raw.githubusercontent.com/kushagrasinghx/BitChord/main/app/src/main/ic_launcher-playstore.png"
    private const val CLIENT_VERSION = "0.0.83"
    private const val CLIENT_BUILD = "352675"
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) discord/0.0.83 Chrome/120.0.0.0 Electron/28.2.10 Safari/537.36"
}
