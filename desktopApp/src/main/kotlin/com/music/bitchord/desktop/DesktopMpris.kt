package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.TypeRef
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val MPRIS_ROOT_INTERFACE = "org.mpris.MediaPlayer2"
private const val MPRIS_PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
private const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
private const val MPRIS_BUS_NAME = "org.mpris.MediaPlayer2.bitchord"

interface MprisMetadataType : TypeRef<Map<String, Variant<*>>>

@DBusInterfaceName(MPRIS_ROOT_INTERFACE)
@DBusProperty(name = "CanQuit", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanRaise", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "HasTrackList", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "Identity", type = String::class, access = Access.READ)
@DBusProperty(name = "DesktopEntry", type = String::class, access = Access.READ)
@DBusProperty(name = "SupportedUriSchemes", type = MprisUriSchemesType::class, access = Access.READ)
@DBusProperty(name = "SupportedMimeTypes", type = MprisMimeTypesType::class, access = Access.READ)
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

interface MprisUriSchemesType : TypeRef<List<String>>

interface MprisMimeTypesType : TypeRef<List<String>>

@DBusInterfaceName(MPRIS_PLAYER_INTERFACE)
@DBusProperty(name = "Metadata", type = MprisMetadataType::class, access = Access.READ)
@DBusProperty(name = "PlaybackStatus", type = String::class, access = Access.READ)
@DBusProperty(name = "LoopStatus", type = String::class, access = Access.READ_WRITE)
@DBusProperty(name = "Rate", type = Double::class, access = Access.READ_WRITE)
@DBusProperty(name = "Shuffle", type = Boolean::class, access = Access.READ_WRITE)
@DBusProperty(name = "Volume", type = Double::class, access = Access.READ_WRITE)
@DBusProperty(name = "Position", type = Long::class, access = Access.READ)
@DBusProperty(name = "MinimumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "MaximumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "CanGoNext", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPlay", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPause", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanSeek", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanControl", type = Boolean::class, access = Access.READ)
interface MprisPlayer : DBusInterface {
    fun Previous()
    fun Next()
    fun Stop()
    fun Play()
    fun Pause()
    fun PlayPause()
    fun Seek(offsetUs: Long)
    fun SetPosition(trackId: DBusPath, positionUs: Long)
    fun OpenUri(uri: String)
}

/** Publishes the desktop player's state through the standard Linux MPRIS session-bus interfaces. */
internal class DesktopMprisController(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onShuffleChanged: (Boolean) -> Unit,
    private val onLoopStatusChanged: (String) -> Unit,
    private val onRateChanged: (Double) -> Unit,
    private val onVolumeChanged: (Double) -> Unit,
    private val onSeek: (Long) -> Unit,
) {
    private val lock = Any()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BitChord-MPRIS").apply { isDaemon = true }
    }
    private var connection: DBusConnection? = null
    private var started = false
    private var state = DesktopPlaybackState()
    private var shuffle = false
    private var loopStatus = "None"
    private var rate = 1.0
    private var publishedSongId: String? = null
    private var publishedPlaybackStatus: String? = null
    private var publishedShuffle = false
    private var publishedLoopStatus = "None"
    private var publishedRate = 1.0
    private var publishedVolume = 1.0

    fun start() {
        if (!isLinux() || synchronized(lock) { started }) return
        synchronized(lock) { started = true }
        worker.execute {
            runCatching {
                // Not shared.
                val bus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                bus.requestBusName(MPRIS_BUS_NAME)
                bus.exportObject(MPRIS_OBJECT_PATH, ExportedPlayer(this@DesktopMprisController))
                synchronized(lock) { connection = bus }
                publish(force = true)
            }.onFailure { failure ->
                synchronized(lock) {
                    connection?.close()
                    connection = null
                    started = false
                }
                System.err.println("BitChord MPRIS unavailable: ${failure.message}")
            }
        }
    }

    fun stop() {
        val bus = synchronized(lock) {
            started = false
            val current = connection
            connection = null
            current
        }
        runCatching { bus?.close() }
        worker.shutdownNow()
    }

    fun update(
        playback: DesktopPlaybackState,
        shuffle: Boolean,
        loopStatus: String,
        rate: Double,
    ) {
        synchronized(lock) {
            state = playback
            this.shuffle = shuffle
            this.loopStatus = loopStatus
            this.rate = rate.coerceIn(0.25, 3.0)
        }
        publish(force = false)
    }

    private fun publish(force: Boolean) {
        val currentConnection: DBusConnection
        val changed: MutableMap<String, Variant<*>>
        synchronized(lock) {
            currentConnection = connection ?: return
            val playbackStatus = playbackStatus(state)
            changed = linkedMapOf()
            if (force || publishedSongId != state.song?.videoId) {
                changed["Metadata"] = variant(metadata(state.song), "a{sv}")
            }
            if (force || publishedPlaybackStatus != playbackStatus) {
                changed["PlaybackStatus"] = variant(playbackStatus)
            }
            if (force || publishedShuffle != shuffle) {
                changed["Shuffle"] = variant(shuffle)
            }
            if (force || publishedLoopStatus != loopStatus) {
                changed["LoopStatus"] = variant(loopStatus)
            }
            if (force || publishedRate != rate) {
                changed["Rate"] = variant(rate)
            }
            if (force || publishedVolume != state.volume.toDouble()) {
                changed["Volume"] = variant(state.volume.toDouble().coerceIn(0.0, 1.0))
            }
            publishedSongId = state.song?.videoId
            publishedPlaybackStatus = playbackStatus
            publishedShuffle = shuffle
            publishedLoopStatus = loopStatus
            publishedRate = rate
            publishedVolume = state.volume.toDouble()
        }
        if (changed.isEmpty()) return
        runCatching {
            currentConnection.sendMessage(
                Properties.PropertiesChanged(
                    MPRIS_OBJECT_PATH,
                    MPRIS_PLAYER_INTERFACE,
                    changed,
                    emptyList(),
                ),
            )
        }
    }

    /** One property, answered from the same table as [allProperties]. */
    private fun property(interfaceName: String, propertyName: String): Variant<*> =
        allProperties(interfaceName)[propertyName] ?: unknownProperty(interfaceName, propertyName)

    private fun allProperties(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        MPRIS_ROOT_INTERFACE -> linkedMapOf(
            "CanQuit" to variant(false),
            "CanRaise" to variant(false),
            "HasTrackList" to variant(false),
            "Identity" to variant("BitChord"),
            "DesktopEntry" to variant("bitchord"),
            "SupportedUriSchemes" to variant(listOf("file", "http", "https"), "as"),
            "SupportedMimeTypes" to variant(
                listOf("audio/mpeg", "audio/mp4", "audio/aac", "audio/flac", "audio/ogg"),
                "as",
            ),
        )
        MPRIS_PLAYER_INTERFACE -> synchronized(lock) {
            linkedMapOf(
                "Metadata" to variant(metadata(state.song), "a{sv}"),
                "PlaybackStatus" to variant(playbackStatus(state)),
                "LoopStatus" to variant(loopStatus),
                "Rate" to variant(rate),
                "Shuffle" to variant(shuffle),
                "Volume" to variant(state.volume.toDouble().coerceIn(0.0, 1.0)),
                "Position" to variant(state.positionMs.coerceAtLeast(0L) * 1_000L),
                "MinimumRate" to variant(0.25),
                "MaximumRate" to variant(3.0),
                "CanGoNext" to variant(state.song != null),
                "CanGoPrevious" to variant(state.song != null),
                "CanPlay" to variant(state.song != null && state.error == null),
                "CanPause" to variant(state.song != null),
                "CanSeek" to variant(state.song != null && durationMs(state.song, state.durationMs) > 0L),
                "CanControl" to variant(true),
            )
        }
        else -> unknownProperty(interfaceName, "")
    }

    private fun setProperty(interfaceName: String, propertyName: String, value: Any?) {
        val unwrapped = (value as? Variant<*>)?.value ?: value
        if (interfaceName != MPRIS_PLAYER_INTERFACE) throw unknownProperty(interfaceName, propertyName)
        when (propertyName) {
            "Shuffle" -> (unwrapped as? Boolean)?.let(onShuffleChanged)
                ?: throw DBusExecutionException("Shuffle expects a boolean")
            "LoopStatus" -> (unwrapped as? String)?.let(onLoopStatusChanged)
                ?: throw DBusExecutionException("LoopStatus expects a string")
            "Rate" -> (unwrapped as? Number)?.toDouble()?.let(onRateChanged)
                ?: throw DBusExecutionException("Rate expects a number")
            "Volume" -> (unwrapped as? Number)?.toDouble()?.let(onVolumeChanged)
                ?: throw DBusExecutionException("Volume expects a number")
            else -> throw unknownProperty(interfaceName, propertyName)
        }
    }

    private fun metadata(song: Song?): Map<String, Variant<*>> = song?.let { current ->
        // A LinkedHashMap, deliberately: `buildMap` hands back an internal builder class that
        // dbus-java refuses to marshal.
        LinkedHashMap<String, Variant<*>>().apply {
            put("mpris:trackid", variant(DBusPath(trackPath(current))))
            put("xesam:title", variant(current.title))
            if (current.artist.isNotBlank()) put("xesam:artist", variant(listOf(current.artist), "as"))
            current.albumName?.takeIf(String::isNotBlank)?.let { put("xesam:album", variant(it)) }
            durationMs(current, state.durationMs).takeIf { it > 0L }?.let {
                put("mpris:length", variant(it * 1_000L))
            }
            current.thumbnailUrl?.takeIf(String::isNotBlank)?.let { put("mpris:artUrl", variant(it)) }
            current.localUri?.takeIf(String::isNotBlank)?.let { put("xesam:url", variant(it)) }
        }
    } ?: emptyMap()

    private fun unknownProperty(interfaceName: String, propertyName: String): Nothing =
        throw DBusExecutionException("Unknown property $interfaceName.$propertyName")

    private fun playbackStatus(playback: DesktopPlaybackState): String = when {
        playback.isPlaying -> "Playing"
        playback.song != null && playback.error == null -> "Paused"
        else -> "Stopped"
    }

    private fun durationMs(song: Song?, knownDurationMs: Long): Long =
        knownDurationMs.takeIf { it > 0L } ?: song?.durationText.durationMillis()

    private fun trackPath(song: Song): String =
        "$MPRIS_OBJECT_PATH/track/${Integer.toUnsignedString(song.videoId.hashCode(), 16)}"

    private fun variant(value: Any, signature: String? = null): Variant<*> =
        if (signature == null) Variant(value) else Variant(value, signature)

    private fun isLinux(): Boolean =
        DesktopPlatform.isLinux

    private class ExportedPlayer(private val controller: DesktopMprisController) :
        MprisRoot,
        MprisPlayer,
        Properties {
        override fun getObjectPath(): String = MPRIS_OBJECT_PATH

        override fun Raise() = Unit

        override fun Quit() = Unit

        override fun Previous() = controller.onPrevious()

        override fun Next() = controller.onNext()

        override fun Stop() = controller.onPause()

        override fun Play() = controller.onPlay()

        override fun Pause() = controller.onPause()

        override fun PlayPause() = controller.onPlayPause()

        override fun Seek(offsetUs: Long) {
            controller.onSeek(offsetUs / 1_000L)
        }

        override fun SetPosition(trackId: DBusPath, positionUs: Long) {
            controller.onSeek(positionUs / 1_000L)
        }

        override fun OpenUri(uri: String) = Unit

        override fun <A> Get(interfaceName: String, propertyName: String): A =
            controller.property(interfaceName, propertyName) as A

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
            controller.setProperty(interfaceName, propertyName, value)
        }

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
            controller.allProperties(interfaceName)
    }
}
