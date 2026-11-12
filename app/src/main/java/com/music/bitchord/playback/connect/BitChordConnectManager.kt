package com.music.bitchord.playback.connect

import android.content.Context
import androidx.media3.common.Player
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.toSong
import com.music.bitchord.ui.player.ConnectDeviceUi
import com.music.bitchord.ui.player.ConnectUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object BitChordConnectManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _activeDevice = MutableStateFlow<ConnectDevice?>(null)
    val activeDevice: StateFlow<ConnectDevice?> = _activeDevice.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<ConnectDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ConnectDevice>> = _discoveredDevices.asStateFlow()

    private var discovery: BitChordConnectDiscovery? = null
    private var client: BitChordConnectClient? = null
    private var speaker: BitChordConnectSpeaker? = null
    private var server: BitChordConnectServer? = null

    private var localPlayerProvider: (() -> Player?)? = null

    val isConnected: Boolean
        get() = _activeDevice.value != null && speaker?.active == true

    fun ensureDiscovery(context: Context) {
        if (discovery == null) {
            val disc = BitChordConnectDiscovery(context.applicationContext, scope).also { discovery = it }
            scope.launch {
                disc.discoveredDevices.collect {
                    _discoveredDevices.value = it
                }
            }
            disc.startDiscovery()
        }
    }

    fun initialize(
        context: Context,
        playerProvider: () -> Player?,
        onPlaySongsRequested: (songs: List<Song>, startIndex: Int, positionMs: Long) -> Unit,
    ): BitChordConnectSpeaker {
        localPlayerProvider = playerProvider
        val appContext = context.applicationContext

        ensureDiscovery(appContext)

        val cli = client ?: BitChordConnectClient(
            context = appContext,
            scope = scope,
            onStateUpdated = { snapshot ->
                speaker?.onSnapshotReceived(snapshot)
            },
        ).also { client = it }

        val spk = speaker ?: BitChordConnectSpeaker(
            client = cli,
            localPlayer = playerProvider,
            onSpeakerStateChanged = {
                // state update trigger
            },
        ).also { speaker = it }

        if (server == null) {
            val srv = BitChordConnectServer(
                context = appContext,
                scope = scope,
                getPlayer = playerProvider,
                onPlaySongsRequested = onPlaySongsRequested,
            ).also { server = it }
            srv.start()
        }

        return spk
    }

    val connectUi: StateFlow<ConnectUi> = combine(
        _discoveredDevices,
        _activeDevice,
        _isConnecting,
    ) { devices, active, connecting ->
        val uiDevices = devices.map { dev ->
            ConnectDeviceUi(
                id = dev.id,
                name = dev.name,
                model = dev.model,
                active = active?.id == dev.id,
                isTv = dev.deviceType == ConnectDeviceType.TV,
            )
        }
        ConnectUi(
            devices = uiDevices,
            activeDeviceName = active?.name,
            connecting = connecting,
        )
    }.stateIn(scope, SharingStarted.Eagerly, ConnectUi())

    fun refreshDiscovery(context: Context? = null) {
        context?.let { ensureDiscovery(it) }
        discovery?.refresh()
    }

    fun connectTo(device: ConnectDevice) {
        val cli = client ?: return
        val spk = speaker ?: return
        val player = localPlayerProvider?.invoke()

        val oldDevice = _activeDevice.value
        if (oldDevice != null && oldDevice.id != device.id) {
            cli.sendCommand(ConnectCommand(action = ConnectCommand.ACTION_PAUSE))
            cli.disconnect()
        }

        _isConnecting.value = true
        _activeDevice.value = device

        cli.connect(device)
        spk.setActive(true)

        // Hand-off: ONLY if the phone is currently actively playing music, transfer playback to TV.
        // If the phone was idle/paused, do NOT overwrite the TV; instead let the phone adopt the TV's song!
        if (player != null && player.isPlaying && player.mediaItemCount > 0) {
            val currentPos = player.currentPosition.coerceAtLeast(0L)
            val currentItem = player.currentMediaItem
            val currentSong = currentItem?.toSong()

            if (currentSong != null) {
                cli.sendCommand(
                    ConnectCommand(
                        action = ConnectCommand.ACTION_PLAY_SONGS,
                        song = currentSong,
                        positionMs = currentPos,
                        startIndex = 0,
                    ),
                )
            }
            // Pause phone's local player so only the TV is outputting sound
            player.pause()
        }

        _isConnecting.value = false
    }

    fun disconnect(resumeHere: Boolean = true) {
        val player = localPlayerProvider?.invoke()
        val currentRemotePos = speaker?.positionMs ?: 0L
        val wasRemotePlaying = speaker?.isPlaying == true

        if (resumeHere && wasRemotePlaying) {
            client?.sendCommand(ConnectCommand(action = ConnectCommand.ACTION_PAUSE))
        }

        speaker?.setActive(false)
        client?.disconnect()
        _activeDevice.value = null

        if (resumeHere && wasRemotePlaying && player != null) {
            player.seekTo(currentRemotePos)
            player.prepare()
            player.play()
        }
    }

    fun sendCommand(command: ConnectCommand) {
        client?.sendCommand(command)
    }

    fun playSongOnDevice(song: Song, queue: List<Song> = emptyList(), startIndex: Int = 0) {
        val cli = client ?: return
        if (isConnected) {
            cli.sendCommand(
                ConnectCommand(
                    action = ConnectCommand.ACTION_PLAY_SONGS,
                    song = song,
                    songs = queue.takeIf { it.isNotEmpty() },
                    startIndex = startIndex,
                    positionMs = 0L,
                ),
            )
        }
    }
}
