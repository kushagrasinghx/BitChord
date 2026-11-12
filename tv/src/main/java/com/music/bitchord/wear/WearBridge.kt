package com.music.bitchord.wear

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.playback.cast.NoSpeaker
import com.music.bitchord.playback.cast.RemoteSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.music.bitchord.widget.MediaWidgetSnapshot

/**
 * The TV app's stand-in for the phone's watch bridge
 * (app/src/main/java/.../wear/WearBridge.kt): nobody pairs a watch with a TV, so
 * this keeps Play services Wearable, and the listener service that needs it, out
 * of the TV build altogether. Same signatures, nothing behind them.
 */
object WearBridge {
    @Suppress("UNUSED_PARAMETER")
    internal fun publish(context: Context, snapshot: MediaWidgetSnapshot, player: Player, playing: Boolean?) = Unit

    @Suppress("UNUSED_PARAMETER")
    internal fun republish(context: Context, player: Player) = Unit

    val watchSession: StateFlow<Boolean> = MutableStateFlow(false)

    @Suppress("UNUSED_PARAMETER")
    fun attachStreams(factory: DataSource.Factory?) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun createSpeaker(
        context: Context,
        scope: CoroutineScope,
        localPlayer: () -> ExoPlayer?,
        onChanged: () -> Unit,
    ): RemoteSpeaker = NoSpeaker

    fun releaseSpeaker() = Unit

    val watchOutput: StateFlow<WatchOutputState> = MutableStateFlow(WatchOutputState())

    @Suppress("UNUSED_PARAMETER")
    fun refreshWatchOutput(context: Context) = Unit

    fun selectWatchOutput() = Unit

    fun leaveWatchOutput() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun setWatchVolume(fraction: Float) = Unit
}
