package com.music.bitchord.playback.dlna

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.playback.cast.CastStream
import com.music.bitchord.playback.cast.RemoteSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the output drawer needs about network speakers. */
data class DlnaOutputState(
    /** Renderers found on the Wi-Fi, the active one always among them. */
    val devices: List<DlnaDevice> = emptyList(),
    /** The renderer that is the speaker, if one is. */
    val activeId: String? = null,
    /** Its volume, 0..1. */
    val volume: Float = 0f,
) {
    val active: Boolean get() = activeId != null
}

/**
 * DLNA / UPnP renderers as outputs: finding them for the drawer and making one
 * the speaker. [DlnaSpeaker] is the receiver the session player reports while
 * one is; it is built by the playback service like the Cast and watch ones.
 */
object DlnaOutput {
    private const val TAG = "BitChordDlna"

    /** Network calls that nobody waits on, and discovery. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DlnaOutputState())
    val state: StateFlow<DlnaOutputState> = _state.asStateFlow()

    private var searchJob: Job? = null

    @Volatile internal var speaker: DlnaSpeaker? = null
        private set

    /** Look for renderers again — the output drawer opening. */
    fun refresh() {
        if (searchJob?.isActive == true) return
        searchJob = io.launch {
            val found = Upnp.search()
            _state.update { state ->
                val playing = state.devices.firstOrNull { it.id == state.activeId }
                state.copy(devices = (found + listOfNotNull(playing)).distinctBy { it.id }.sortedBy { it.name.lowercase() })
            }
        }
    }

    /** Make the renderer [id] the speaker. Main thread. */
    fun select(id: String) {
        val device = _state.value.devices.firstOrNull { it.id == id } ?: return
        speaker?.start(device)
    }

    /** Take the music back onto this phone; [resumeHere] keeps it playing. Main thread. */
    fun leave(resumeHere: Boolean) {
        speaker?.stop(resumeHere)
    }

    /** The drawer's slider while a renderer is the speaker. */
    fun setVolume(fraction: Float) {
        speaker?.setVolume(fraction)
    }

    internal fun onSpeakerChanged(speaker: DlnaSpeaker) {
        _state.update {
            it.copy(
                activeId = speaker.device?.id?.takeIf { speaker.active },
                volume = speaker.deviceVolume / 100f,
            )
        }
    }

    internal fun background(block: () -> Unit) {
        io.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "renderer call failed", t)
            }
        }
    }

    /** Built by the playback service; see [DlnaSpeaker]. */
    fun createSpeaker(
        scope: CoroutineScope,
        localPlayer: () -> ExoPlayer?,
        resolve: suspend (MediaItem) -> CastStream?,
        say: (Int) -> Unit,
        onChanged: () -> Unit,
    ): RemoteSpeaker = DlnaSpeaker(scope, localPlayer, resolve, say, onChanged).also { speaker = it }

    /** The service is going: the renderer stops and the speaker with it. */
    fun releaseSpeaker() {
        speaker?.takeIf { it.active }?.stop(resumeHere = false)
        speaker = null
    }
}
