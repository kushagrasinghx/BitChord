package com.music.bitchord.desktop

import com.music.bitchord.data.discord.DiscordApplication
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import com.my.kizzy.rpc.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    /**
     * Every pipe write, probe and gateway push, one at a time and in the order
     * they were asked for. Concurrent pushes could leave an older track up
     * after a newer one, or open two sockets at once and leak one; and none of
     * it may run on the UI thread, where a Discord that has stopped reading its
     * pipe would stall a frame. [ipc], [rpc], [lastKey] and [lastProbeAt] are
     * only touched from here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var rpc: KizzyRPC? = null
    private var ipc: DesktopDiscordIpc? = null
    private var lastKey: String? = null
    private var lastProbeAt = 0L

    private val _localClient = MutableStateFlow(false)

    /** Whether a Discord on this machine is answering, so no token is needed. */
    val localClient: StateFlow<Boolean> = _localClient

    /** Looks for a local Discord. Cheap, and the answer changes when one starts. */
    fun refreshLocalClient() {
        scope.launch {
            lastProbeAt = System.currentTimeMillis()
            val found = DesktopDiscordIpc.connect(DiscordApplication.ID)
            if (found != null) {
                ipc?.close()
                ipc = found
                _localClient.value = true
                DesktopTrackLog.log("discord: local client on ${found.label}")
            } else {
                _localClient.value = ipc != null
                DesktopTrackLog.log("discord: no local client answering")
            }
        }
    }

    /**
     * Looks for a local Discord from the push path, so a client started or
     * restarted mid-session is picked up without reopening settings.
     *
     * A miss holds off the next look for [PROBE_BACKOFF_MS] unless [force]d:
     * with presence on and no Discord running, every play, pause and track
     * change would otherwise scan every pipe again.
     */
    private fun reconnect(reason: String, force: Boolean = false): DesktopDiscordIpc? {
        val now = System.currentTimeMillis()
        if (!force && now - lastProbeAt < PROBE_BACKOFF_MS) return null
        lastProbeAt = now
        val found = DesktopDiscordIpc.connect(DiscordApplication.ID) ?: return null
        ipc = found
        _localClient.value = true
        DesktopTrackLog.log("discord: reconnect ($reason) on ${found.label}")
        return found
    }

    /** Sends over [local]; on failure closes and forgets it, so the next push looks again. */
    private fun pushLocal(local: DesktopDiscordIpc, activity: JsonObject): Boolean {
        val failure = runCatching { local.setActivity(activity) }.exceptionOrNull() ?: return true
        DesktopTrackLog.log("discord: local push failed: ${failure.message}")
        // A client that has quit takes its socket with it.
        runCatching { local.close() }
        if (ipc === local) ipc = null
        _localClient.value = false
        return false
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
        val song = state.song
        if (song == null || !state.isPlaying) {
            clear()
            return
        }
        // Read off the state handed in rather than NerdStats.current: the effect
        // that fills that in runs after this one in the same frame, so it would
        // still hold the previous track's format.
        val quality = qualityLine(state)
        val key = "${song.videoId}|${state.durationMs}|$speed|$quality"
        val position = state.positionMs
        val duration = state.durationMs.takeIf { it > 0 } ?: 0L
        scope.launch {
            // Only marked sent once a push lands, so a failed one is tried again
            // on the next change instead of being skipped as a duplicate.
            if (key == lastKey) return@launch
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
            val activity = ipcActivity(song, title, now - elapsed, now + remaining, quality)
            val held = ipc
            val local = held ?: reconnect("track")
            if (local != null) {
                if (pushLocal(local, activity)) {
                    lastKey = key
                    return@launch
                }
                // A socket that had been working died under us: Discord may
                // have restarted, so look once more straight away.
                if (held != null) {
                    val retry = reconnect("retry", force = true)
                    if (retry != null && pushLocal(retry, activity)) {
                        lastKey = key
                        return@launch
                    }
                }
            }
            if (_token.value.isBlank()) return@launch
            val gatewayFailure = runCatching {
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
                    // No usable artwork sends none, and Discord draws the
                    // BitChord application's icon instead.
                    largeImage = song.artworkAt(ART_PX)
                        ?.takeIf { it.startsWith("http") }
                        ?.let(RpcImage::ExternalImage),
                    smallImage = null,
                    largeText = quality,
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
                    applicationId = DiscordApplication.ID,
                    status = DesktopDiscordSettings.status.value,
                )
            }.exceptionOrNull()
            if (gatewayFailure == null) lastKey = key
        }
    }

    fun clear() {
        scope.launch {
            lastKey = null
            runCatching { ipc?.setActivity(null) }
            rpc?.closeRPC()
            rpc = null
        }
    }

    /**
     * The activity as the local socket wants it.
     *
     * The same fields the gateway payload carries, in the shape SET_ACTIVITY
     * takes. Artwork goes over as a plain URL: mirroring it onto Discord's CDN
     * is what needs an account token, and the client resolves an http asset by
     * itself.
     */
    private fun ipcActivity(
        song: Song,
        title: String,
        startMs: Long,
        endMs: Long,
        quality: String?,
    ): JsonObject = buildJsonObject {
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
            song.artworkAt(ART_PX)?.takeIf { it.startsWith("http") }?.let { put("large_image", it) }
            quality?.let { put("large_text", it) }
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

    /** Measured premium line, or null when the toggle is off or the stream is lossy. */
    private fun qualityLine(state: DesktopPlaybackState): String? {
        if (!DesktopDiscordSettings.showAudioQuality.value) return null
        return desktopAudioQualityLine(state.streamFormat?.nerdSnapshot(state.streamSourceId))
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

    /** How long a scan that found no local Discord holds off the next one. */
    private const val PROBE_BACKOFF_MS = 10_000L
    private const val PROJECT_URL = "https://github.com/kushagrasinghx/BitChord"
    private const val ART_PX = 480
    private const val CLIENT_VERSION = "0.0.83"
    private const val CLIENT_BUILD = "352675"
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) discord/0.0.83 Chrome/120.0.0.0 Electron/28.2.10 Safari/537.36"
}
