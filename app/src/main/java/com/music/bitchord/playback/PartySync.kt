package com.music.bitchord.playback

import android.os.SystemClock
import androidx.media3.common.Player
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Makes the player obey the party, and the party obey this player.
 *
 * Lives in [PlaybackService] rather than in the UI on purpose: a party has to
 * survive the app being backgrounded and the screen going off, which is most of
 * what listening together actually looks like. Bound to a composable it would
 * desync the moment somebody put their phone down.
 *
 * ## Telling the two directions apart
 *
 * The hard part of this is not moving the playhead, it is knowing *who moved
 * it*. A naive binder that reacts to player events and republishes them ends up
 * echoing its own corrections around the party forever. This one never has to
 * guess, because the two directions arrive through physically different doors:
 *
 *  - **Outbound** is [onLocalIntent], called only from [PlaybackService]'s
 *    `SessionPlayer` — the [androidx.media3.common.ForwardingPlayer] every
 *    *user* action passes through, wherever it came from: the app, the
 *    notification, a headset button, Android Auto. Plus the one thing that is
 *    the user's intent without being their action, the queue moving on by
 *    itself at the end of a track.
 *  - **Inbound** is [reconcile], which writes straight to the ExoPlayer,
 *    underneath that wrapper. So nothing this class does to the player can ever
 *    come back to it as an intent.
 *
 * One deliberate consequence: a pause this app did not ask for — audio focus
 * lost to another app, a call — goes directly to the player and is *not*
 * published. One person taking a call does not stop the music for everyone else.
 *
 * ## Losing the audio to another app
 *
 * That device must then also stop *following*, which is a second thing and not
 * the same one. [reconcile] exists to put a player that is not where the party
 * is back where the party is, and a player silenced by focus loss looks exactly
 * like one that has fallen behind — so it was dutifully seeking it into place
 * and pressing play, which took the audio straight back off whatever the
 * listener had just started. The song they had left came back over the top of
 * it, every seven hundred milliseconds, for as long as they kept trying.
 *
 * So focus loss detaches this device from the party until its own user asks to
 * come back ([focusLost]), and coming back is a catch-up rather than a control
 * ([rejoining]): the gap opened by being away belongs to the device that was
 * away, and publishing it as a seek would haul four other people back to where
 * one of them took a phone call.
 *
 * ## Following in time
 *
 * [reconcile] converges on [ListenTogether.partyPositionMs], which is the
 * party's position translated into this device's own clock. Three details carry
 * most of the weight:
 *
 *  - **Resuming waits for the scheduled start.** The server anchors a resume a
 *    few hundred milliseconds into the future so every device has one instant to
 *    aim at. Calling `play()` as soon as the frame lands would start this device
 *    early by exactly that lead — and being *consistently* early is worse than
 *    being occasionally late, because it is below the drift limit and would
 *    never be corrected. So a resume waits out [ListenTogether.msUntilStart].
 *  - **Drift is corrected by seeking, and only when it is real.** A seek is
 *    audible, so [DRIFT_LIMIT_MS] is set well above the jitter of two decoders
 *    running independently and at the level of an actual desync — a buffering
 *    stall, a doze, a device that came back from a tunnel.
 *  - **Position is only trusted once the clock is.** Before the first
 *    ping/pong there is no measured offset, so track and play/pause are applied
 *    and the playhead is left alone rather than seeked to a guess.
 */
class PartySync(
    private val scope: CoroutineScope,
    /** Read fresh every time: the service swaps players at a crossfade. */
    private val player: () -> Player?,
) {

    private val jobs = mutableListOf<Job>()
    private var publishJob: Job? = null
    private var startJob: Job? = null

    /**
     * Until when [reconcile] should keep its hands off.
     *
     * Set when this device's user does something. Between the action and the
     * server's echo of it, the party state still describes the world before the
     * button was pressed — reconciling against it in that window would undo the
     * user's own action in front of them.
     */
    private var reconcileQuietUntilMs = 0L

    /**
     * The party seq at which this device's own controls will have all landed.
     *
     * Not "the seq before I published": choosing a track publishes *two*
     * controls, a queue and a track, and the party is in a torn state between
     * them — new queue, old track. Clearing the quiet window on the first one
     * let [reconcile] run against exactly that state and haul the player back
     * off the song the user had just picked. Which is what it did.
     */
    private var awaitSeq = Long.MAX_VALUE

    /** Guards against re-issuing a load for a track already being loaded. */
    private var loadingVideoId: String? = null

    /**
     * The last party control this device has put itself exactly on.
     *
     * A control is a discontinuity — everybody is meant to land on the same
     * instant, precisely — whereas the time between controls is a slow drift
     * worth tolerating. So a seq not yet aligned to is matched exactly, and
     * after that [DRIFT_LIMIT_MS] applies. Without this the device that *issued*
     * a control keeps whatever head start issuing it gave it: below the drift
     * limit, so never corrected, and permanent.
     */
    private var alignedSeq = -1L

    /**
     * A resume this device has accepted but not yet performed.
     *
     * Deferring the local `play()` hides the user's intent from the player:
     * [publish] reads `playWhenReady`, which is still false, and would report
     * "same track, still paused" — so the one control the tap existed to send
     * never goes out, the party stays paused, and the only thing that starts
     * anything is the fallback, a second and a half later. This carries the
     * intent across that gap.
     */
    @Volatile
    private var deferredPlayPending = false

    /**
     * This device has been silenced by something its user did not ask for.
     *
     * Set when the player gives up audio focus permanently, which is what
     * another app starting playback looks like from here. While it is set this
     * class does nothing at all to the player: not a resume, not a load, not a
     * corrective seek. The party carries on for everybody else; this device is
     * simply no longer one of the places it is coming out of.
     *
     * Cleared by the user asking for the music back, and as a backstop by the
     * player playing again through any route at all.
     */
    @Volatile
    private var focusLost = false

    /**
     * The user has asked to come back after [focusLost], and this device is
     * behind by however long it was away.
     *
     * That gap is this device's to close. Published as a seek — which is what
     * an ordinary resume does, and exactly what it should do — it would drag
     * four other people back to the moment one of them answered a phone call.
     * So a rejoin sends nothing and lets [reconcile] do the catching up.
     */
    @Volatile
    private var rejoining = false

    /** Consecutive over-limit readings. See the drift branch of [reconcile]. */
    private var driftStrikes = 0

    /** When the player may next be seeked for drift, having just been. */
    private var driftCooldownUntilMs = 0L

    private var lastPartyCode: String? = null

    fun start() {
        jobs += scope.launch {
            ListenTogether.state
                // A new state, or leaving/joining. Not every field: this exists
                // to react promptly to a control, and the round-trip counter
                // changing is not one.
                .map { Triple(it.playback.seq, it.queue.seq, it.code) }
                .distinctUntilChanged()
                .collect { (seq, _, code) ->
                    if (code != lastPartyCode) {
                        val wasInParty = lastPartyCode != null
                        val nowInParty = code != null
                        lastPartyCode = code
                        if (!wasInParty && nowInParty) {
                            onEnteredParty()
                        } else if (wasInParty && !nowInParty) {
                            onLeftParty()
                        }
                    }
                    // Every control this device sent has come back around, so
                    // the party now describes the world the user made — or
                    // somebody else has moved it on past ours, which is equally
                    // a reason to stop holding reconcile off.
                    if (seq >= awaitSeq) reconcileQuietUntilMs = 0L
                    reconcile()
                }
        }
        jobs += scope.launch {
            while (true) {
                delay(TICK_MS)
                // The screen is the only thing that opens this socket otherwise,
                // and a party outlives the screen. A process restarted by the
                // system into a party it is still a member of has the membership
                // but no connection, and would sit silently out of step; this is
                // what puts it back. Idempotent — it returns immediately when a
                // socket is already up, or when there is no party.
                if (ListenTogether.state.value.inParty) ListenTogether.ensureConnected()
                reconcile()
            }
        }
    }

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        publishJob?.cancel()
        startJob?.cancel()
    }

    /**
     * The user did something to playback on this device — or the queue moved on
     * by itself, which is the same thing as far as the party is concerned.
     *
     * Debounced, because one gesture is several calls: choosing a track in the
     * app is `setMediaItems` then `prepare` then `play`, and publishing each
     * would put three controls on the wire for one tap.
     */
    fun onLocalIntent() {
        val party = ListenTogether.state.value
        if (!party.inParty) return
        // Whatever the user just pressed, they want this device in the party
        // again — so it follows from here, and this one publish is a catch-up
        // rather than a control. See [focusLost].
        if (focusLost) {
            Log.i(TAG, "rejoining the party after losing the audio")
            focusLost = false
            rejoining = true
        }
        reconcileQuietUntilMs = SystemClock.elapsedRealtime() + INTENT_QUIET_MS
        // Nothing has been published yet, so there is no seq to wait for and
        // the window must not clear on somebody else's control either — it is
        // protecting an action of ours that has not gone out.
        awaitSeq = Long.MAX_VALUE
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            publish()
        }
    }

    /**
     * The player's `playWhenReady` moved, and why.
     *
     * The *why* is the whole reason this exists and is only available here:
     * [Player] has no getter for it, so a pause caused by another app taking
     * the audio is indistinguishable, a tick later, from any other pause. It
     * has to be caught as it happens. See [focusLost].
     */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            // Audible again by some route — a rejoin, a headset button, the
            // notification. Whatever it was, following the party is right.
            focusLost = false
            return
        }
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
            ListenTogether.state.value.inParty
        ) {
            Log.i(TAG, "another app took the audio; dropping out of the party until asked back")
            focusLost = true
            deferredPlayPending = false
            startJob?.cancel()
        }
    }

    /**
     * Whether a `play()` from this device should be held back for the party.
     *
     * True means the caller must *not* start the player: this class will, at the
     * instant the server schedules for everyone. That instant is a few hundred
     * milliseconds out, and the difference between honouring it and not is the
     * difference between a party and a device that is permanently ahead of one.
     * Starting here and letting drift correction sort it out does not work —
     * the head start a resume gives the device that issued it is smaller than
     * any drift threshold worth having, so it would never be corrected at all.
     *
     * False whenever the party could not schedule anything — no party, no
     * socket, no measured clock — in which case play behaves exactly as it does
     * outside this feature. The press is never simply swallowed: [reconcile]
     * starts the player on the echo, and [deferredPlayFallback] starts it anyway
     * if that echo never comes.
     */
    fun shouldDeferPlay(): Boolean {
        val party = ListenTogether.state.value
        if (!party.inParty || party.connection != ListenTogether.Connection.LIVE) return false
        if (!party.clockSynced) return false
        deferredPlayPending = true
        deferredPlayFallback()
        return true
    }

    /**
     * The press must do something even if the party cannot answer.
     *
     * A control can be lost, and a socket can be up in name only. Left to the
     * echo alone, that case is a play button that does nothing — far worse than
     * being briefly out of step, and impossible for the listener to diagnose.
     */
    private fun deferredPlayFallback() {
        startJob?.cancel()
        startJob = scope.launch {
            delay(DEFERRED_PLAY_TIMEOUT_MS)
            val exo = player() ?: return@launch
            if (deferredPlayPending && !exo.playWhenReady) {
                Log.w(TAG, "party never acknowledged the resume; starting locally")
                deferredPlayPending = false
                exo.play()
            }
        }
    }

    // ------------------------------------------------------------ inbound --

    private fun reconcile() {
        val party = ListenTogether.state.value
        if (!party.inParty) {
            loadingVideoId = null
            focusLost = false
            rejoining = false
            return
        }
        if (SystemClock.elapsedRealtime() < reconcileQuietUntilMs) return
        // Another app has the audio. Following the party from here means seeking
        // this player into place and pressing play, which takes the audio back
        // off whatever the listener just started — so this device follows
        // nothing until its own user asks it to. See [focusLost].
        if (focusLost) return
        val target = party.playback
        val track = target.track ?: return
        val exo = player() ?: return

        // Suppressed, not stopped: a notification chime or a short clip holds
        // the audio for a moment and hands it straight back, with `playWhenReady`
        // never going false. The playhead is frozen meanwhile, so it reads as
        // drift that is not there and would be "corrected" by seeking a player
        // nobody can hear. It converges on its own the moment the audio returns.
        if (exo.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) return

        // Playing something off this device. The party cannot follow a
        // content:// URI and this device should not be yanked off the file
        // somebody deliberately chose, so the two are simply left uncoupled
        // until playback returns to something with a catalogue id.
        if (exo.currentMediaItem?.toSong()?.isDeviceFile() == true) return

        // A playing party cannot be joined in time without a measured clock.
        // [PartyPlayback.positionMs] is the position at an anchor that may be
        // minutes old, so acting on it unsynced would not merely be imprecise —
        // it would start this device at wherever the song was when the last
        // control happened. Pausing needs no clock, so that still applies.
        if (target.isPlaying && !party.clockSynced) return

        if (exo.currentMediaItem?.mediaId != track.videoId) {
            load(party)
            return
        }
        loadingVideoId = null
        reconcileQueue(party, exo)

        if (!target.isPlaying) {
            deferredPlayPending = false
            if (exo.playWhenReady) exo.pause()
            // Held where the party paused it, so that everybody resumes from the
            // same place rather than from wherever their own playhead stopped.
            if (abs(exo.currentPosition - target.positionMs) > PAUSED_TOLERANCE_MS) {
                exo.seekTo(target.positionMs)
            }
            return
        }

        // Safe to read as a real position from here down: the party is playing
        // and the clock has been measured, both checked above.
        val want = ListenTogether.partyPositionMs()
        if (!exo.playWhenReady) {
            val wait = ListenTogether.msUntilStart()
            if (wait > 0) {
                // The party's resume is scheduled, not immediate. Wake up for
                // it rather than waiting for the next tick, which could be most
                // of a tick late — and late is what this is here to avoid.
                startJob?.cancel()
                startJob = scope.launch {
                    delay(wait)
                    reconcile()
                }
                return
            }
            if (want != null) exo.seekTo(want)
            deferredPlayPending = false
            startJob?.cancel()
            exo.play()
            alignedSeq = target.seq
            return
        }

        if (want == null) return

        // Only a settled player can be measured. While it is buffering,
        // `currentPosition` is where it will resume rather than where it is, so
        // the party runs on and this reads as drift that is not there.
        if (exo.playbackState != Player.STATE_READY) {
            driftStrikes = 0
            return
        }

        val drift = exo.currentPosition - want
        // A control this device has not yet put itself on. Everyone is meant to
        // land on it exactly, so it is matched without regard to the drift
        // limit — including on the device that issued it, which is otherwise
        // left holding the head start that issuing it gave it.
        if (target.seq != alignedSeq) {
            alignedSeq = target.seq
            if (abs(drift) > ALIGN_TOLERANCE_MS) {
                Log.i(TAG, "aligning ${drift}ms onto party control ${target.seq}")
                exo.seekTo(want)
            }
            return
        }
        if (abs(drift) <= DRIFT_LIMIT_MS) {
            driftStrikes = 0
            return
        }
        // Seeking is not free, and on a slow device it is not cheap either: the
        // re-buffer it costs can be longer than the gap being closed, so the
        // correction arrives already as far behind as the error it was fixing,
        // and does it again, forever. That loop is real — it was measured here
        // at a flat -1402ms every 1.4s, to the millisecond, for as long as it
        // was left running.
        //
        // Two things keep it out. A correction has to be asked for twice in a
        // row before it happens, so one reading taken while the pipeline was
        // catching up cannot trigger anything; and after one, the player is left
        // alone long enough to settle and show what it is really doing.
        val now = SystemClock.elapsedRealtime()
        if (now < driftCooldownUntilMs) return
        if (++driftStrikes < DRIFT_STRIKES) return
        Log.i(TAG, "correcting ${drift}ms of drift against the party")
        driftStrikes = 0
        driftCooldownUntilMs = now + DRIFT_COOLDOWN_MS
        exo.seekTo(want)
    }

    /**
     * Puts the party's running order on this player and starts at its position.
     *
     * The queue comes across as well as the track so that next and previous
     * work locally and so that the next track is prefetched — a device that had
     * only the current song would stall at every change while it resolved a
     * stream from cold.
     */
    private fun load(party: ListenTogether.State) {
        val track = party.playback.track ?: return
        if (loadingVideoId == track.videoId) return
        loadingVideoId = track.videoId

        scope.launch {
            // The queue is only usable if the track is actually in it. It may
            // not be: the queue and the track are two controls, and between them
            // the party holds a new running order with the old song still
            // current. `indexOfFirst(...).coerceAtLeast(0)` turned that
            // not-found into index 0 and played whatever happened to be first —
            // a different song entirely, with nothing on screen to explain it.
            // Falling back to the track alone is always right; falling back to
            // position zero never is.
            val partyQueue = party.queue.items
            val index = partyQueue.indexOfFirst { it.videoId == track.videoId }
            val queue = if (index >= 0) partyQueue else listOf(track)
            val startIndex = if (index >= 0) index else 0
            // Off the main thread: building an item resolves artwork sizes and
            // asks [com.music.bitchord.download.Downloads] whether each track is
            // already on disk, which is a stat per downloaded song. See
            // [MediaController.playSongs], which moves it for the same reason.
            val items = withContext(Dispatchers.Default) { queue.map { it.toSong().toMediaItem() } }
            val exo = player()
            if (exo == null) {
                // Nothing was applied, so the next tick has to be free to try
                // again rather than believing this track is already on its way.
                loadingVideoId = null
                return@launch
            }
            // Read after the build, not before: assembling a long queue takes
            // real time, and the party's playhead has moved on by exactly that
            // much. Taken beforehand, every track change would start this device
            // a little behind and then be hauled forward by a correcting seek.
            val startAt = ListenTogether.partyPositionMs() ?: party.playback.positionMs
            exo.setMediaItems(items, startIndex, startAt)
            exo.prepare()
            // Not started here even when the party is playing: the resume may be
            // scheduled a moment out, and [reconcile] owns that wait. Preparing
            // now is what makes this device ready to hit that instant.
            if (party.playback.isPlaying && ListenTogether.msUntilStart() <= 0L) exo.play()
            reconcile()
        }
    }

    // ----------------------------------------------------------- outbound --

    private fun publish() {
        val party = ListenTogether.state.value
        if (!party.inParty) return
        // Coming back from having lost the audio. This device is behind, and
        // possibly on a track the party left minutes ago — everything it could
        // say right now is stale, and every one of those is a control that would
        // move four other people backwards. So it says nothing and lets
        // [reconcile], which is no longer held off, bring it to the party.
        if (rejoining) {
            rejoining = false
            reconcileQuietUntilMs = 0L
            return
        }
        val exo = player() ?: return
        val song = exo.currentMediaItem?.toSong() ?: return
        // A file on this device is not something a party can play: it is
        // identified by a content:// or file:// URI that means nothing anywhere
        // else, and handing one out would have every other member fail to
        // resolve it. So local playback simply says nothing, and [reconcile]
        // leaves this device alone while it lasts — the party carries on with
        // what it was doing, and normal service resumes on the next track that
        // has a catalogue id. A *downloaded* track is not this case: it has a
        // real id and everybody else can stream it perfectly well.
        if (song.isDeviceFile()) return
        val track = song.toPartyTrack(exo.duration)
        val position = exo.currentPosition.coerceAtLeast(0L)
        // What the user asked for, which during a deferred resume is not what
        // the player is doing yet — that is the whole point of the deferral.
        val wantsPlaying = deferredPlayPending || exo.playWhenReady
        val base = party.playback.seq
        var controls = 0

        // Compared by id first, which is a plain field read per item. Building
        // the full list is not — it parses a metadata bundle per track — and
        // this runs on every pause and every seek, on a queue that can be
        // hundreds long.
        val localIds = (0 until exo.mediaItemCount)
            .map { exo.getMediaItemAt(it).mediaId }
            // Device files are dropped rather than sent for everyone else to
            // fail on, so the index has to be found in what is left, not in the
            // local list — otherwise it points at the wrong row.
            .filterNot { it.startsWith("content://") || it.startsWith("file://") }

        val trackIndex = localIds.indexOf(track.videoId)
        val clampedIds = if (trackIndex >= 0) {
            val upcomingEnd = (trackIndex + 1 + MAX_PARTY_UPCOMING_QUEUE).coerceAtMost(localIds.size)
            localIds.subList(0, upcomingEnd)
        } else {
            localIds.take(1 + MAX_PARTY_UPCOMING_QUEUE)
        }

        // Covers the ways a running order changes without the playhead moving —
        // Play next, Add to queue, removing a row, dragging one. Before this,
        // none of them reached the party and its copy of the queue silently went
        // stale until the next track change happened to rebuild it.
        val partyIds = party.queue.items.map(PartyTrack::videoId)
        if (clampedIds != partyIds) {
            val singleMove = if (partyIds.size == clampedIds.size && trackIndex >= 0 && trackIndex < partyIds.size && partyIds[trackIndex] == clampedIds[trackIndex]) {
                detectSingleMove(partyIds, clampedIds)
            } else {
                null
            }

            if (singleMove != null && singleMove.fromIndex > trackIndex && singleMove.toIndex > trackIndex) {
                ListenTogether.queueMove(singleMove.fromIndex, singleMove.toIndex, singleMove.videoId)
                controls++
            } else {
                val countToTake = clampedIds.size
                val queue = (0 until countToTake)
                    .map { exo.getMediaItemAt(it).toSong() }
                    .filterNot(Song::isDeviceFile)
                    .map { it.toPartyTrack(0L) }
                ListenTogether.setQueue(queue, trackIndex)
                controls++
            }
        }

        when {
            party.playback.track?.videoId != track.videoId -> {
                // After the queue, never before: a track change that arrives
                // pointing into a running order nobody has yet is the bug that
                // played the wrong song.
                ListenTogether.setTrack(track, position, wantsPlaying)
                controls++
            }
            party.playback.isPlaying != wantsPlaying -> {
                if (wantsPlaying) ListenTogether.play(position) else ListenTogether.pause(position)
                controls++
            }
            // Same track, same playing state — so what the user did was move
            // the playhead. Unless it did not move far, in which case this is
            // not a seek at all.
            //
            // The case that matters is two devices reaching the end of a track
            // at the same moment: both report the advance, the second one finds
            // the party already on the new track and would otherwise publish its
            // own position as a seek — which drags the first device, which
            // republishes, and so on. Nobody asked for any of it.
            else -> {
                val partyPosition = ListenTogether.partyPositionMs()
                if (partyPosition == null || abs(position - partyPosition) > SEEK_REPORT_FLOOR_MS) {
                    ListenTogether.seek(position)
                    controls++
                }
            }
        }

        // The party has caught up with this device once every control sent has
        // come back around. Nothing sent means nothing to wait for, and the
        // quiet window should stop holding reconcile off immediately.
        awaitSeq = base + controls
        if (controls == 0) reconcileQuietUntilMs = 0L
    }

    private fun onEnteredParty() {
        val exo = player() ?: return
        Log.i(TAG, "entered party, stashing personal queue")
        PartyPersonalQueueStash.stashFromPlayer(exo)
    }

    private fun onLeftParty() {
        Log.i(TAG, "left party, restoring personal queue if stashed")
        loadingVideoId = null
        focusLost = false
        rejoining = false
        val stashed = PartyPersonalQueueStash.load()
        if (stashed != null) {
            val exo = player() ?: return
            val items = stashed.songs.map { it.toMediaItem() }
            exo.setMediaItems(items, stashed.index, stashed.positionMs)
            exo.prepare()
            if (stashed.wasPlaying) {
                exo.play()
            } else {
                exo.pause()
            }
            PartyPersonalQueueStash.clear()
        }
    }

    private fun reconcileQueue(party: ListenTogether.State, exo: Player) {
        val partyQueue = party.queue.items
        if (partyQueue.isEmpty()) return

        val currentIndex = exo.currentMediaItemIndex
        val currentMediaId = exo.currentMediaItem?.mediaId ?: return
        val partyIndex = partyQueue.indexOfFirst { it.videoId == currentMediaId }
        if (partyIndex < 0) return

        val desiredUpcoming = partyQueue.subList(partyIndex + 1, partyQueue.size).take(MAX_PARTY_UPCOMING_QUEUE)
        val desiredUpcomingIds = desiredUpcoming.map { it.videoId }

        val localUpcomingIds = (currentIndex + 1 until exo.mediaItemCount).map {
            exo.getMediaItemAt(it).mediaId
        }

        if (localUpcomingIds != desiredUpcomingIds) {
            // The normal case (a listener or AutoPlay appending a song) must
            // never rewrite the timeline. Adding after the active item keeps
            // its decoder and audio renderer untouched on every device.
            if (desiredUpcomingIds.startsWith(localUpcomingIds)) {
                exo.addMediaItems(desiredUpcoming.drop(localUpcomingIds.size).map { it.toSong().toMediaItem() })
                return
            }

            // Likewise, trimming only the tail leaves the active source alone.
            if (localUpcomingIds.startsWith(desiredUpcomingIds)) {
                exo.removeMediaItems(currentIndex + 1 + desiredUpcomingIds.size, exo.mediaItemCount)
                return
            }

            val singleMove = if (localUpcomingIds.size == desiredUpcomingIds.size) {
                detectSingleMove(localUpcomingIds, desiredUpcomingIds)
            } else {
                null
            }

            if (singleMove != null) {
                exo.moveMediaItem(
                    currentIndex + 1 + singleMove.fromIndex,
                    currentIndex + 1 + singleMove.toIndex,
                )
            } else {
                // One atomic timeline edit. Removing and then adding the tail
                // creates an empty intermediate timeline, which can briefly
                // rebuffer or interrupt the current renderer on some devices.
                // The active item is deliberately outside this replacement.
                exo.replaceMediaItems(
                    currentIndex + 1,
                    exo.mediaItemCount,
                    desiredUpcoming.map { it.toSong().toMediaItem() },
                )
            }
        }
    }

    private fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val TAG = "PartySync"

        /**
         * How often the playhead is checked against the party's.
         *
         * Frequent enough that a device coming back from a stall is corrected
         * within about a second, and cheap: in-process field reads off the
         * ExoPlayer, no binder call and no allocation on the quiet path.
         */
        const val TICK_MS = 700L

        /**
         * How far out of step is worth an audible seek.
         *
         * Above the jitter of two decoders on different hardware, and at the
         * level of a real desync. Tightening this does not make a party more
         * synchronised — it makes it seek more often, which is what people
         * actually hear.
         */
        const val DRIFT_LIMIT_MS = 1_200L

        /** Readings in a row above the limit before a correction is made. */
        const val DRIFT_STRIKES = 2

        /**
         * How long the player is left alone after a corrective seek.
         *
         * Long enough to cover the re-buffer a seek costs on a slow device
         * plus room to show a settled position afterwards. This is the value
         * that decides whether correction converges or oscillates, so it is
         * deliberately generous: a party a second out for a few seconds is
         * fine, a party seeking every 1.4s is not listenable.
         */
        const val DRIFT_COOLDOWN_MS = 6_000L

        /** While paused there is nothing to hear, so the playhead can be exact. */
        const val PAUSED_TOLERANCE_MS = 400L

        /**
         * How exactly a device lands on a control. Low enough that nobody keeps
         * a head start worth hearing, high enough not to seek over the few tens
         * of milliseconds between asking the player where it is and it acting.
         */
        const val ALIGN_TOLERANCE_MS = 120L

        /**
         * Below this, a difference from the party's playhead is not a seek
         * anybody performed — it is two devices being normally, slightly apart.
         */
        const val SEEK_REPORT_FLOOR_MS = 1_000L

        /** How long a deferred resume waits for the party before giving up on it. */
        const val DEFERRED_PLAY_TIMEOUT_MS = 1_800L

        /**
         * Long enough to coalesce the burst one tap makes — choosing a track
         * is `setMediaItems`, `prepare`, `play` within a few milliseconds —
         * and no longer, because a resume is held until this has elapsed and
         * every millisecond here is silence after the button was pressed.
         */
        const val PUBLISH_DEBOUNCE_MS = 120L

        /**
         * How long a local action is protected from being reconciled away.
         *
         * Generous window (4500ms) to accommodate high latency (800ms-1000ms+)
         * before server WebSocket echoes arrive. Cleared as soon as seq >= awaitSeq.
         */
        const val INTENT_QUIET_MS = 4_500L

        /** Maximum upcoming tracks in a party queue excluding the currently playing one. */
        const val MAX_PARTY_UPCOMING_QUEUE = 25

        /** The server's own ceiling; publishing more would only be truncated. */
        const val MAX_PUBLISHED_QUEUE = 1 + MAX_PARTY_UPCOMING_QUEUE
    }
}

/**
 * What the party needs to know about a track: enough to name it and show it.
 *
 * Everything else about how this device is playing it — which source answered,
 * at what quality, from the network or from a download — stays here, which is
 * what lets two people in a party be on different sources and still be in the
 * same place in the same song.
 */
/**
 * Whether this is a file on the device rather than a track from a catalogue.
 *
 * Asked of the *id*, not of where the bytes are coming from, and the difference
 * matters: a downloaded catalogue track also plays off disk, but it has a real
 * id, so every other device in the party can find and stream it. Only something
 * whose whole identity is a `content://` or `file://` URI is unshareable — that
 * URI names a row in this device's media store and nothing at all anywhere else.
 */
private fun Song.isDeviceFile(): Boolean =
    videoId.startsWith("content://") || videoId.startsWith("file://")

internal fun Song.toPartyTrack(playerDurationMs: Long): PartyTrack = PartyTrack(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // The player's own figure when it has one, since it comes from the decoder;
    // otherwise what the row that queued the track claimed.
    durationMs = playerDurationMs.takeIf { it > 0L }
        ?: TrackMatcher.secondsOf(durationText)?.let { it * 1000L },
    fromAutoplay = fromAutoplay,
)

private fun PartyTrack.toSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // Not cosmetic: this is what a cross-source match is made on, so a device
    // whose sources differ from the sender's needs it to find the same
    // recording. See [Song.matchQuery].
    durationText = durationMs?.let { ms ->
        val total = ms / 1000
        "%d:%02d".format(total / 60, total % 60)
    },
    fromAutoplay = fromAutoplay,
)

internal data class QueueMoveDelta(
    val fromIndex: Int,
    val toIndex: Int,
    val videoId: String,
)

/**
 * Detects if [newList] is the result of moving exactly one item in [oldList].
 * If so, returns the from and to indices (offset by [baseOffset]) and the item's id.
 * Returns null if the lists cannot be explained by a single move.
 */
internal fun detectSingleMove(
    oldList: List<String>,
    newList: List<String>,
    baseOffset: Int = 0,
): QueueMoveDelta? {
    if (oldList.size != newList.size || oldList == newList || oldList.isEmpty()) return null
    if (oldList.groupingBy { it }.eachCount() != newList.groupingBy { it }.eachCount()) return null

    for (from in oldList.indices) {
        val item = oldList[from]
        val withoutItem = oldList.toMutableList().apply { removeAt(from) }
        for (to in oldList.indices) {
            if (from == to) continue
            val simulated = withoutItem.toMutableList().apply { add(to, item) }
            if (simulated == newList) {
                return QueueMoveDelta(
                    fromIndex = baseOffset + from,
                    toIndex = baseOffset + to,
                    videoId = item,
                )
            }
        }
    }
    return null
}
