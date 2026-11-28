/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The one Player MediaSession sees for the life of the service. Crossfade and
 * version swaps replace [delegate] rather than `mediaSession.player`, so a
 * controller that resolves its player at a handoff boundary can never keep a
 * reference to the demoted deck.
 *
 * State is projected from the current delegate into SimpleBasePlayer's state
 * snapshot; every delegate event invalidates that snapshot so timeline,
 * metadata, commands and position remain those of the active deck.
 */
@OptIn(UnstableApi::class)
internal class StableSessionPlayer(
    initial: Player,
    looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    @Volatile private var delegate: Player = initial
    @Volatile private var released = false

    /**
     * Why playWhenReady last changed on the delegate. Controllers act on it
     * (audio focus loss, becoming noisy, end of item), so it is relayed rather
     * than every change being reported as a user request.
     */
    private var playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST

    private val relay = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            playWhenReadyReason = reason
        }

        override fun onEvents(player: Player, events: Player.Events) {
            invalidateState()
        }
    }

    init {
        delegate.addListener(relay)
    }

    fun switchTo(next: Player) {
        if (released || next === delegate) return
        delegate.removeListener(relay)
        delegate = next
        next.addListener(relay)
        invalidateState()
    }

    override fun getState(): State {
        if (released) return State.Builder().build()
        val p = delegate
        val timeline = p.currentTimeline
        // SimpleBasePlayer rejects a non-idle, non-ended state over an empty
        // playlist. The delegate reports a Cast/watch receiver's state while
        // the phone's queue can already be empty, so never build that pair.
        val playbackState = p.playbackState.let { state ->
            if (timeline.isEmpty && state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
                Player.STATE_IDLE
            } else {
                state
            }
        }
        return State.Builder()
            .setAvailableCommands(p.availableCommands)
            .setPlayWhenReady(p.playWhenReady, playWhenReadyReason)
            .setPlaybackState(playbackState)
            .setPlaybackSuppressionReason(p.playbackSuppressionReason)
            .setPlayerError(p.playerError)
            .setRepeatMode(p.repeatMode)
            .setShuffleModeEnabled(p.shuffleModeEnabled)
            .setIsLoading(p.isLoading)
            .setSeekBackIncrementMs(p.seekBackIncrement)
            .setSeekForwardIncrementMs(p.seekForwardIncrement)
            .setMaxSeekToPreviousPositionMs(p.maxSeekToPreviousPosition)
            .setPlaybackParameters(p.playbackParameters)
            .setTrackSelectionParameters(p.trackSelectionParameters)
            .setAudioAttributes(p.audioAttributes)
            .setAudioSessionId(p.audioSessionId)
            .setVolume(p.volume)
            .setDeviceInfo(p.deviceInfo)
            .setDeviceVolume(p.deviceVolume)
            .setIsDeviceMuted(p.isDeviceMuted)
            .setPlaylist(
                if (timeline.isEmpty) timeline else QueueOrderTimeline(timeline),
                p.currentTracks,
                p.mediaMetadata,
            )
            .setPlaylistMetadata(p.playlistMetadata)
            .setCurrentMediaItemIndex(p.currentMediaItemIndex)
            .setContentPositionMs { delegate.currentPosition }
            .setContentBufferedPositionMs { delegate.bufferedPosition }
            .setTotalBufferedDurationMs { delegate.totalBufferedDuration }
            .build()
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // play()/pause(), not the playWhenReady setter: SessionPlayer gives
        // play() a party-deferral and Cast path of its own.
        if (playWhenReady) delegate.play() else delegate.pause()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        delegate.prepare()
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        delegate.stop()
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> {
        if (!released) {
            released = true
            delegate.removeListener(relay)
            delegate.release()
            invalidateState()
        }
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        delegate.repeatMode = repeatMode
        return done()
    }

    override fun handleSetShuffleModeEnabled(enabled: Boolean): ListenableFuture<*> {
        delegate.shuffleModeEnabled = enabled
        return done()
    }

    override fun handleSetPlaybackParameters(parameters: PlaybackParameters): ListenableFuture<*> {
        delegate.playbackParameters = parameters
        return done()
    }

    override fun handleSetTrackSelectionParameters(parameters: TrackSelectionParameters): ListenableFuture<*> {
        delegate.trackSelectionParameters = parameters
        return done()
    }

    override fun handleSetPlaylistMetadata(metadata: MediaMetadata): ListenableFuture<*> {
        delegate.playlistMetadata = metadata
        return done()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        delegate.volume = volume
        return done()
    }

    override fun handleSetVolume(volume: Float, flags: Int): ListenableFuture<*> {
        delegate.volume = volume
        return done()
    }

    override fun handleSetDeviceVolume(volume: Int, flags: Int): ListenableFuture<*> {
        delegate.setDeviceVolume(volume, flags)
        return done()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        delegate.increaseDeviceVolume(flags)
        return done()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        delegate.decreaseDeviceVolume(flags)
        return done()
    }

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        delegate.setDeviceMuted(muted, flags)
        return done()
    }

    override fun handleSetAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
    ): ListenableFuture<*> {
        delegate.setAudioAttributes(audioAttributes, handleAudioFocus)
        return done()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        // SimpleBasePlayer passes C.INDEX_UNSET for setMediaItems(items,
        // resetPosition = true). Handed to ExoPlayer as an index, that means
        // "keep the current index and position" — the opposite — and throws
        // IllegalSeekPositionException when the new list is shorter.
        if (startIndex == C.INDEX_UNSET) {
            delegate.setMediaItems(mediaItems, /* resetPosition = */ true)
        } else {
            delegate.setMediaItems(mediaItems, startIndex, startPositionMs)
        }
        return done()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        delegate.addMediaItems(index, mediaItems)
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        delegate.moveMediaItems(fromIndex, toIndex, newIndex)
        return done()
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> {
        delegate.replaceMediaItems(fromIndex, toIndex, mediaItems)
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        delegate.removeMediaItems(fromIndex, toIndex)
        return done()
    }

    /**
     * SimpleBasePlayer reduces every seek-like command to an index and a
     * position. The delegate is a SessionPlayer that gives next, previous,
     * scrubbing, back/forward and default-position their own handling — the
     * party lock, Cast routing, crossfade cancel, and a "jump to a later row"
     * that is deliberately different from a plain Next — so each command is
     * handed back to the matching delegate method instead of a raw seekTo.
     */
    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val d = delegate
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT -> d.seekToNext()
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> d.seekToNextMediaItem()
            Player.COMMAND_SEEK_TO_PREVIOUS -> d.seekToPrevious()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> d.seekToPreviousMediaItem()
            Player.COMMAND_SEEK_BACK -> d.seekBack()
            Player.COMMAND_SEEK_FORWARD -> d.seekForward()
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> d.seekTo(positionMs)
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION ->
                if (mediaItemIndex == C.INDEX_UNSET || mediaItemIndex == d.currentMediaItemIndex) {
                    d.seekToDefaultPosition()
                } else {
                    d.seekToDefaultPosition(mediaItemIndex)
                }
            else ->
                if (mediaItemIndex == C.INDEX_UNSET) d.seekTo(positionMs)
                else d.seekTo(mediaItemIndex, positionMs)
        }
        return done()
    }
}

/**
 * The delegate's timeline in queue order whatever the shuffle flag says.
 *
 * SessionPlayer reports the *app's* shuffle (which reorders the queue itself)
 * while ExoPlayer's own shuffle stays off, so play order is always queue
 * order. Left as-is, SimpleBasePlayer would resolve next/previous — and
 * BasePlayer's hasNextMediaItem() gate — through ExoPlayer's unused random
 * shuffle order whenever the app's shuffle is on, picking or refusing the
 * wrong track.
 */
@OptIn(UnstableApi::class)
internal class QueueOrderTimeline(timeline: Timeline) : ForwardingTimeline(timeline) {
    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        timeline.getNextWindowIndex(windowIndex, repeatMode, false)

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        timeline.getPreviousWindowIndex(windowIndex, repeatMode, false)

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = timeline.getFirstWindowIndex(false)

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = timeline.getLastWindowIndex(false)
}
