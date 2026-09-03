package com.music.bitchord.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.future
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.Downloads
import java.util.concurrent.ConcurrentHashMap
import com.music.bitchord.data.Http
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.discord.DiscordRPC
import com.music.bitchord.data.innertube.PlaybackTracker
import com.music.bitchord.data.stats.ListeningRecorder
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.ListenBrainzManager
import com.music.bitchord.data.scrobbling.ScrobbleManager
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.smart.AutomixAnalysisSource
import com.music.bitchord.widget.MediaWidget
import com.music.bitchord.widget.MediaWidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlinx.coroutines.TimeoutCancellationException
import java.util.Locale

/** Past this point in a track, back restarts it instead of skipping to the previous one. */
const val BACK_RESTARTS_AFTER_MS = 10_000L

/** Session command used by both the player UI and the media notification. */
const val ACTION_TOGGLE_AUTOPLAY = "com.music.bitchord.action.TOGGLE_AUTOPLAY"

/** Session command used by the media notification's Shuffle button. */
const val ACTION_TOGGLE_SHUFFLE = "com.music.bitchord.action.TOGGLE_SHUFFLE"

/** Session commands bracketing an explicit radio queue replacement. */
const val ACTION_BEGIN_RADIO_QUEUE = "com.music.bitchord.action.BEGIN_RADIO_QUEUE"
const val ACTION_COMMIT_RADIO_QUEUE = "com.music.bitchord.action.COMMIT_RADIO_QUEUE"

/**
 * Background playback via Media3. A [MediaLibraryService] gives us the media
 * notification, lockscreen/Bluetooth controls, and Android Auto surface for
 * free; UI processes attach with a MediaController.
 *
 * Queue items carry a `bitchord://watch?v=<videoId>` URI. The actual stream
 * URL is resolved lazily by [ResolvingDataSource] the moment ExoPlayer opens
 * the item — stream URLs expire after a few hours, so resolving at play time
 * (on Media3's loader thread, hence runBlocking is safe) keeps queues valid.
 *
 * A single ExoPlayer owns the queue and backs the session for the service's
 * whole life; [CrossfadeController] rides on top of it as volume automation.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val searchResults = ConcurrentHashMap<String, List<Song>>()
    private val songCache = ConcurrentHashMap<String, Song>()

    /**
     * [YtMusicRepository.home] cached briefly for Android Auto's browse tree.
     *
     * Recents and Quick Picks each read the same home feed, and Auto re-runs
     * `onGetChildren` every time either folder is opened — including just
     * backing out and back in. Without this, one glance at Quick Picks after
     * Recents paid for the full three-way home fetch twice in a row, which is
     * most of what read as lag browsing the car UI. The window is short
     * enough that a pull-to-refresh-style wait for genuinely new content
     * never has to wait this long for it.
     */
    /**
     * [YtMusicRepository.recents] cached for Android Auto's browse tree.
     */
    private var cachedRecents: Pair<Long, List<Song>>? = null
    private val recentsMutex = Mutex()
    private val RECENTS_CACHE_TTL_MS = 30_000L

    private suspend fun cachedRecentsSongs(): List<Song> = recentsMutex.withLock {
        cachedRecents?.let { (at, songs) ->
            if (SystemClock.elapsedRealtime() - at < RECENTS_CACHE_TTL_MS && songs.isNotEmpty()) return songs
        }
        val songs = try {
            withTimeoutOrNull(4000L) {
                YtMusicRepository.recents().getOrNull()
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (songs.isNotEmpty()) {
            cachedRecents = SystemClock.elapsedRealtime() to songs
            songs.forEach { songCache[it.videoId] = it }
        }
        songs
    }

    /**
     * [YtMusicRepository.quickPicks] cached for Android Auto's browse tree.
     * Excludes listening history songs to guarantee strictly unique data.
     */
    private var cachedQuickPicks: Pair<Long, List<Song>>? = null
    private val quickPicksMutex = Mutex()
    private val QUICK_PICKS_CACHE_TTL_MS = 60_000L

    private suspend fun cachedQuickPicksSongs(): List<Song> = quickPicksMutex.withLock {
        cachedQuickPicks?.let { (at, songs) ->
            if (SystemClock.elapsedRealtime() - at < QUICK_PICKS_CACHE_TTL_MS && songs.isNotEmpty()) return songs
        }
        val recentsIds = cachedRecents?.second?.mapTo(HashSet()) { it.videoId } ?: emptySet()
        val songs = try {
            withTimeoutOrNull(4000L) {
                YtMusicRepository.quickPicks(excludeSongIds = recentsIds).getOrNull()
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (songs.isNotEmpty()) {
            cachedQuickPicks = SystemClock.elapsedRealtime() to songs
            songs.forEach { songCache[it.videoId] = it }
        }
        songs
    }

    /**
     * [YtMusicRepository.browseSongs] for Liked Music cached for Android Auto's browse tree.
     */
    private var cachedLiked: Pair<Long, List<Song>>? = null
    private val likedMutex = Mutex()
    private val LIKED_CACHE_TTL_MS = 60_000L

    private suspend fun cachedLikedSongs(): List<Song> = likedMutex.withLock {
        if (com.music.bitchord.data.innertube.Innertube.cookie == null) return emptyList()
        cachedLiked?.let { (at, songs) ->
            if (SystemClock.elapsedRealtime() - at < LIKED_CACHE_TTL_MS && songs.isNotEmpty()) return songs
        }
        val songs = try {
            withTimeoutOrNull(4000L) {
                YtMusicRepository.browseSongs(YtMusicRepository.LIKED_MUSIC).getOrNull()?.songs?.ifEmpty { null }
                    ?: YtMusicRepository.browseSongs("LM").getOrNull()?.songs?.ifEmpty { null }
                    ?: run {
                        val libPlaylists = YtMusicRepository.libraryPlaylists().getOrNull()
                        val likedCard = libPlaylists?.firstOrNull {
                            it.browseId == "VLLM" || it.title.contains("liked", ignoreCase = true)
                        }
                        if (likedCard?.browseId != null) {
                            YtMusicRepository.browseSongs(likedCard.browseId).getOrNull()?.songs
                        } else null
                    }
                    ?: YtMusicRepository.browseSongs("FEmusic_liked_videos").getOrNull()?.songs
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (songs.isNotEmpty()) {
            cachedLiked = SystemClock.elapsedRealtime() to songs
            songs.forEach { songCache[it.videoId] = it }
            LikeState.seedLiked(songs.mapTo(HashSet()) { it.videoId })
        }
        songs
    }

    private var cachedHome: Pair<Long, com.music.bitchord.data.model.HomeFeed>? = null
    private val homeMutex = Mutex()
    private val HOME_CACHE_TTL_MS = 60_000L

    private suspend fun cachedHomeFeed(): com.music.bitchord.data.model.HomeFeed? = homeMutex.withLock {
        cachedHome?.let { (at, feed) ->
            if (SystemClock.elapsedRealtime() - at < HOME_CACHE_TTL_MS) return feed
        }
        val feed = YtMusicRepository.home().getOrNull() ?: return null
        cachedHome = SystemClock.elapsedRealtime() to feed
        feed
    }

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val outputDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = requestOutputReconfiguration()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = requestOutputReconfiguration()
    }

    /**
     * The player the session is on. Swaps with [spare] at every crossfade — see
     * [adoptPlayer] — so anything reading it must read it *now* rather than
     * capturing it.
     */
    private var player: ExoPlayer? = null

    /**
     * The idle player. Between transitions it holds nothing; to arm one,
     * [CrossfadeController] loads it with the queue positioned on the incoming
     * track.
     */
    private var spare: ExoPlayer? = null

    private var crossfade: CrossfadeController? = null
    private var configuredFloatOutput = false
    private var outputReconfigureJob: Job? = null

    /**
     * One audio-processor set per player, because both carry per-sink state — a
     * delay line, filter memory — that two sinks cannot share.
     *
     * The `A`/`B` pair is fixed to the players that own them; [activeFilter] and
     * [spareFilter] are the *roles*, and they trade places at every handoff
     * along with the players. Everything downstream talks in roles.
     */
    private val spatialAudioProcessorA = SpatialAudioProcessor()
    private val spatialAudioProcessorB = SpatialAudioProcessor()
    private val transitionFilterA = TransitionFilterProcessor()
    private val transitionFilterB = TransitionFilterProcessor()

    /**
     * Whether the format currently arriving at the active player's decoder
     * is Dolby Atmos (E-AC-3 JOC). Widening a JOC stream would fight the
     * object-based mix Dolby already spatializes, so the effect is forced
     * off for as long as this is true — see [applySpatialAudioEnabled].
     */
    private var activeTrackIsDolbyAtmos = false

    private var activeFilter: TransitionFilterProcessor = transitionFilterA
    private var spareFilter: TransitionFilterProcessor = transitionFilterB

    /** Automix's DSP analyzer — see [com.music.bitchord.playback.smart.TrackAnalyzer]. */
    private val trackAnalyzer = com.music.bitchord.playback.smart.TrackAnalyzer(this, AudioCache)

    /** Shared with the crossfade's tail player, so both read the same disk cache. */
    private var mediaSourceFactory: DefaultMediaSourceFactory? = null

    /** Last sampled position of the playing track, in seconds. */
    private var lastPositionSeconds = 0L

    /** When the current track was chosen, for the time-to-first-audio log. */
    private var trackSelectedAt: Long? = null

    private var scrobbleManager: ScrobbleManager? = null
    private var listenBrainzSong: Song? = null

    private var listenBrainzStartMs: Long = 0L

    private var listenBrainzDurationMs: Long? = null

    /**
     * The gateway connection publishing what's playing to Discord, or null when
     * the feature is off or no account is connected. See [DiscordRPC].
     */
    private var discordRpc: DiscordRPC? = null

    /**
     * The in-flight presence push. Held so the next one can cancel it: the
     * pushes hit the network — the artwork has to be mirrored onto Discord's CDN
     * before the activity can name it — and a skipped-through queue would
     * otherwise land its presences in whatever order the requests happened to
     * finish in, leaving the profile on a track the listener passed seconds ago.
     */
    private var discordUpdateJob: Job? = null

    /**
     * Whether a presence has been published and not yet taken down.
     *
     * Tracked because [KizzyRPC.close] — which is what clears the card — opens a
     * gateway connection first if one isn't already up. Clearing unconditionally
     * would therefore dial Discord for the sole purpose of sending it nothing,
     * every time playback paused without a presence ever having been set.
     */
    private var discordPresenceUp = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Commands exposed as the secondary buttons on the media notification. */
    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val autoplayCommand = SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val beginRadioQueueCommand = SessionCommand(ACTION_BEGIN_RADIO_QUEUE, Bundle.EMPTY)
    private val commitRadioQueueCommand = SessionCommand(ACTION_COMMIT_RADIO_QUEUE, Bundle.EMPTY)

    private var favoriteActionJob: Job? = null
    private var autoplayLoadJob: Job? = null
    private var autoplaySeed: String? = null

    /** Index in the live queue represented by entry zero of the persisted window. */
    private var persistedQueueStart = 0

    /**
     * What AutoPlay had queued when repeat-all was switched on, held so
     * switching it off again puts back the same tracks rather than a fresh
     * mix.
     *
     * Repeat-all loops the queue as it stands, so AutoPlay's endless supply of
     * new tracks comes out of it first — see [onRepeatModeChanged]. Fetching a
     * replacement mix afterwards is the obvious thing to do and the wrong one:
     * the track that was queued next has usually been analysed for the
     * transition into it by then (see
     * [com.music.bitchord.playback.smart.TrackAnalyzer]), and a different track
     * in its place means that several-second decode is spent again, on a song
     * that is now much closer than it was. Turning repeat on and back off
     * should leave the queue where it found it.
     */
    private var repeatAllStash: List<MediaItem> = emptyList()

    /**
     * The track that was playing when [repeatAllStash] was taken. The stash
     * describes what came after *that* track, so it is only put back if the
     * queue has not moved on in the meantime.
     */
    private var repeatAllStashSeed: String? = null

    /**
     * The last repeat mode seen, so [onRepeatModeChanged] can tell which
     * direction the change went in: the callback reports where the player has
     * arrived, and leaving repeat-all is the half that has to restore.
     */
    private var lastRepeatMode = Player.REPEAT_MODE_OFF

    /**
     * Every song AutoPlay has offered or played this service instance, kept
     * only so "don't repeat suggestions" has something to check against once
     * a song scrolls out of the live queue or the queue itself is replaced.
     * Never persisted — a fresh process means a fresh session.
     */
    private val sessionSongHistory = mutableListOf<Song>()

    private var serviceLyricsJob: Job? = null
    private var serviceLyrics: List<LyricLine>? = null
    private var lastPublishedSubtitle: String? = null
    private var lyricsTickerJob: Job? = null

    /**
     * Everything the service books against the player it is currently on.
     *
     * A field rather than an anonymous object registered once, because the
     * session moves between two players at every crossfade — see [adoptPlayer]
     * — and this has to move with it. It is attached to exactly one player at a
     * time: the one [player] names.
     */
    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // The player this fired on, which is by definition the one the
            // session is currently pointed at.
            val exoPlayer = player ?: return
            // The only number that describes what a listener actually
            // waits through. Every other timing in this app measures one
            // leg of getting a track started — a resolve, a client walk, an
            // extraction — and a leg being fast has repeatedly turned out
            // to say nothing about whether sound arrived quickly, because
            // the legs that were measured were the ones running in the
            // background for tracks nobody was waiting on.
            if (isPlaying) {
                trackSelectedAt?.let {
                    TrackLog.d(
                        "BitChord",
                        "TIMING first audio: ${SystemClock.elapsedRealtime() - it}ms since track selected",
                        about = exoPlayer.currentMediaItem?.mediaId,
                    )
                    trackSelectedAt = null
                }
            }
            if (isPlaying) registerCurrentPlay()
            // Nothing to read ahead for while paused, and a pause is often
            // the last thing that happens before the process goes idle.
            if (isPlaying) prefetchAround(exoPlayer) else AudioCache.cancel()
            if (isPlaying) lookForBetterCopy(exoPlayer)
            savePlaybackState(exoPlayer)
            // Not strictly needed for the glyph — onPlayWhenReadyChanged has
            // already flipped that — but this is where hasNext/hasPrevious and
            // the artwork are known to be settled.
            publishWidgetState()

            val song = exoPlayer.currentMediaItem?.toSong()
            val durationMs = exoPlayer.duration.takeIf { it > 0 }
            scrobbleManager?.onPlayerStateChanged(isPlaying, song, durationMs)

            // The listening record has to be told a pause happened, not merely
            // stop being told about play: its sampler measures the gap between
            // ticks, and an unclosed one across a pause is an afternoon on the
            // lock screen arriving as an afternoon of listening.
            if (!isPlaying) ListeningRecorder.onStopped()

            // ListenBrainz: "now playing" on play/resume too, not just on
            // transition — a track started from idle or resumed from pause
            // otherwise stays silent on the site.
            if (isPlaying && song != null) {
                if (listenBrainzSong?.videoId != song.videoId || listenBrainzStartMs == 0L) {
                    listenBrainzSong = song
                    listenBrainzStartMs = System.currentTimeMillis()
                    listenBrainzDurationMs = durationMs
                } else if (listenBrainzDurationMs == null) {
                    listenBrainzDurationMs = durationMs
                }
                submitListenBrainzPlayingNow(song, exoPlayer.currentPosition, durationMs)
            }

            // Discord: a pause has to clear the presence, not just stop
            // refreshing it. Discord's countdown runs on its own clock from the
            // timestamps it was given, so a presence left up while paused goes
            // on advancing through a song that has stopped — and finishes it.
            if (isPlaying) {
                pushDiscordPresence(exoPlayer)
                startLyricsTicker()
            } else {
                clearDiscordPresence()
                stopLyricsTicker()
            }
        }

        /**
         * The one callback the home-screen widget can be driven from.
         *
         * `onIsPlayingChanged` is too late by seconds: this app resolves a
         * stream before it can buffer one, and for a YouTube track that means a
         * NewPipe extraction, all of which happens with `isPlaying` still false.
         * A widget keyed on that answers a tap on play by leaving the play glyph
         * exactly where it was — the control reads as broken, and the obvious
         * response is to tap it again. `playWhenReady` flips on the command, not
         * on the audio, which is what the media notification shows too.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            publishWidgetState(playing = playWhenReady)
        }

        /**
         * A seek is the one change to a playing track that no other callback
         * reports, and the only one Discord cannot work out for itself: its bar
         * is drawn from two absolute instants, so moving the playhead without
         * sending new ones leaves the profile counting down from where the
         * listener no longer is.
         */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val exoPlayer = player ?: return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                if (exoPlayer.isPlaying) pushDiscordPresence(exoPlayer)
                updateLyricSubtitle()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The player this fired on, which is by definition the one the
            // session is currently pointed at.
            val exoPlayer = player ?: return
            // A quality swap replaces the playing item, which Media3
            // reports here as a playlist change — indistinguishable, from
            // this callback's point of view, from the queue moving on. It
            // is not the queue moving on: it is the same song, at the same
            // position, from a better source. Letting the bookkeeping below
            // run for it scrobbled the track twice, wrote a second history
            // entry, resubmitted it to ListenBrainz and closed out its
            // play count mid-play — all of which happened, and all of which
            // are invisible until someone reads their listening history.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                mediaItem?.mediaId != null &&
                mediaItem.mediaId == swappingMediaId
            ) {
                swappingMediaId = null
                return
            }

            // No crossfade case to allow for here any more. A blended advance
            // never reaches this callback — the incoming track starts as the
            // *first* item of the other player — so it is booked by
            // [adoptPlayer] instead, and what is left arriving here is only ever
            // ExoPlayer moving the queue on by itself, a repeat, or a skip.
            onTrackBecameCurrent(
                mediaItem,
                previousEnded = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                reason = reason,
            )
            autoplayLoadJob?.cancel()
            autoplayLoadJob = null
            autoplaySeed = null
            loadAutoplayForCurrentTrack()
            loadLyricsForCurrentTrack()
            if (exoPlayer.isPlaying) startLyricsTicker()
            mediaSession?.setCustomLayout(notificationButtons())
        }

        /**
         * A failed stream is not a failed track: nothing else in this
         * service ever calls [Player.prepare] again, so before this
         * existed a single read error left the player in `STATE_IDLE` for
         * good. The notification kept the song on it, the play button kept
         * being pressed, and nothing happened — which is exactly what a
         * broken app looks like from the outside.
         */
        override fun onPlayerError(error: PlaybackException) {
            // The player this fired on, which is by definition the one the
            // session is currently pointed at.
            val exoPlayer = player ?: return
            recoverFrom(error, exoPlayer)
        }

        // Nothing follows the last track, so there is no transition to
        // pause on — the queue simply runs out and the timer is spent.
        override fun onPlaybackStateChanged(state: Int) {
            // The player this fired on, which is by definition the one the
            // session is currently pointed at.
            val exoPlayer = player ?: return
            if (state == Player.STATE_ENDED) {
                SleepTimer.cancel()
                // The queue ran dry, so no transition will ever close the last
                // track out. Without this its history entry keeps whatever
                // watchtime the 30-second sampler happened to have reported and
                // is never marked finished — so the one play most likely to be
                // a full, deliberate listen is the one recorded as abandoned.
                PlaybackTracker.onPlaybackFinished(lastPositionSeconds)
                lastPositionSeconds = 0
                // The last track finished with nothing after it, so no
                // transition will ever close it out. Scrobble it now.
                val lastSong = listenBrainzSong
                if (lastSong != null && listenBrainzStartMs > 0L) {
                    val lastStart = listenBrainzStartMs
                    val lastDuration = listenBrainzDurationMs
                        ?: exoPlayer.duration.takeIf { it > 0 }
                    submitListenBrainzFinished(lastSong, lastStart, lastDuration)
                }
                listenBrainzSong = null
                listenBrainzStartMs = 0L
                listenBrainzDurationMs = null
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val previous = lastRepeatMode
            lastRepeatMode = repeatMode
            // Repeat-all loops the queue as it stands; AutoPlay's tracks are the
            // opposite of that — an endless supply of new ones — so they come
            // back out first, and native REPEAT_MODE_ALL then wraps a plain
            // queue exactly as it should. [loadAutoplayForCurrentTrack] leaves
            // it alone for as long as repeat-all stays on.
            //
            // Done here rather than in the UI that used to do it because this is
            // the only place that sees the *previous* mode, and taking the
            // tracks back is only half the job: they have to go in again when
            // the loop ends, or a listener who cycles repeat on and straight
            // back off is left with a queue that simply stops after the playing
            // track.
            when {
                repeatMode == Player.REPEAT_MODE_ALL -> stashAutoplayTracks()
                previous == Player.REPEAT_MODE_ALL -> restoreAutoplayTracks()
            }
            // Turning repeat-all back off can leave the current item at the end
            // of the queue, which is the same trigger as a normal transition.
            loadAutoplayForCurrentTrack()
        }

        /**
         * AutoPlay appends to the queue after the transition that ran it
         * dry, so the track to read ahead for often only exists once the
         * timeline has changed.
         */
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // The player this fired on, which is by definition the one the
            // session is currently pointed at.
            val exoPlayer = player ?: return
            if (exoPlayer.isPlaying) prefetchAround(exoPlayer)
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                saveQueueSnapshot(exoPlayer)
                mediaSession?.setCustomLayout(notificationButtons())
            }
        }
    }

    /** Registered alongside [playbackListener], and moved with it. */
    private val formatListener = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            // Taken off the event's own window rather than off the player,
            // so it names the track this format arrived for even if the
            // queue has moved on again since. See [audioFormatFor].
            audioFormatFor = eventTime.mediaId()
            // Ground truth for a real-device listening test: this is the
            // renderer's own Format, straight off the decoder with none of
            // the app's caching/upgrade logic in between, so it's the one
            // line that can prove a "hi-res" session never quietly slid
            // onto a lower-rate stream mid-track. `adb logcat -s DECODE:I`.
            val khz = format.sampleRate.takeIf { it != Format.NO_VALUE }
                ?.let { "%.1fkHz".format(Locale.ROOT, it / 1000.0) } ?: "?kHz"
            val kbps = format.bitrate.takeIf { it != Format.NO_VALUE }
                ?.let { "${it / 1000}kbps" } ?: "bitrate n/a"
            val depth = bitDepthOf(format.pcmEncoding)?.let { "${it}-bit" } ?: "?-bit"
            TrackLog.i(
                "DECODE",
                "$audioFormatFor <- ${format.sampleMimeType} $khz $kbps $depth ${format.channelCount}ch",
                about = audioFormatFor,
            )
            activeTrackIsDolbyAtmos = NerdStats.isDolbyAtmosMime(format.sampleMimeType)
            applySpatialAudioEnabled()
            publishNerdStats()
        }

        /**
         * The seam, measured rather than described. This fires when the
         * audio track starts putting samples out again after the sink was
         * flushed, which for a quality swap is the exact instant the music
         * comes back — and the gap between it and the swap is the only
         * number that says whether any of the work above paid off. Every
         * other timing here brackets a fetch, and a fetch being fast has
         * repeatedly said nothing about whether the listener heard a hole.
         */
        override fun onAudioPositionAdvancing(
            eventTime: AnalyticsListener.EventTime,
            playoutStartSystemTimeMs: Long,
        ) {
            val cutAt = swapCutAt ?: return
            swapCutAt = null
            TrackLog.d(
                "BitChord",
                "swap seam: ${SystemClock.elapsedRealtime() - cutAt}ms of silence",
                about = eventTime.mediaId(),
            )
        }

        /**
         * The three legs the seam breaks into, logged separately because
         * they have entirely different fixes: getting the new source
         * loaded and past the load control's gate, standing a decoder up,
         * and opening an audio track. Only the first is ours to shorten.
         */
        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            val cutAt = swapCutAt ?: return
            if (state == Player.STATE_READY) {
                TrackLog.d(
                    "BitChord",
                    "swap leg: ready ${SystemClock.elapsedRealtime() - cutAt}ms after the cut",
                    about = eventTime.mediaId(),
                )
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            TrackLog.i(
                "AUDIO_OUT",
                "decoder=$decoderName initialized in ${initializationDurationMs}ms",
                about = eventTime.mediaId(),
            )
            val cutAt = swapCutAt ?: return
            TrackLog.d(
                "BitChord",
                "swap leg: $decoderName stood up in ${initializationDurationMs}ms, " +
                    "${SystemClock.elapsedRealtime() - cutAt}ms after the cut",
                about = eventTime.mediaId(),
            )
        }

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            AudioOutputStatus.publishAudioTrack(
                encoding = audioTrackConfig.encoding,
                sampleRateHz = audioTrackConfig.sampleRate,
            )
            TrackLog.i(
                "AUDIO_OUT",
                "AudioTrack ${audioTrackConfig.sampleRate}Hz encoding=${audioTrackConfig.encoding}",
                about = eventTime.mediaId(),
            )
        }

    }

    /**
     * Which track an analytics event is about, taken off the event's own window.
     *
     * The player has moved on by the time some of these arrive — a format
     * change for the outgoing track lands after the transition — so its
     * `currentMediaItem` names the wrong one. The event carries the timeline it
     * was raised against, which does not.
     */
    private fun AnalyticsListener.EventTime.mediaId(): String? = timeline
        .takeIf { windowIndex < it.windowCount }
        ?.getWindow(windowIndex, Timeline.Window())
        ?.mediaItem
        ?.mediaId

    override fun onCreate() {
        super.onCreate()

        if (com.music.bitchord.data.innertube.Innertube.cookie == null) {
            com.music.bitchord.data.innertube.Innertube.cookie = com.music.bitchord.auth.AuthStore(this).cookie
        }

        // First, because everything below assumes it is standing up fresh and
        // one of the two ways this service starts does not give it that.
        //
        // A cold start runs in a new process, where the session-scoped state
        // these two hold is empty anyway. A *warm* one doesn't: closing the app
        // destroys the service while Android keeps the process to reuse, so
        // without this a second service inherits the first one's idea of what
        // was playing and what has already been asked about. That cost the
        // reported bug all three of its symptoms — a badge reading "Lossless"
        // over a player holding no bytes, and a track that had been upgraded to
        // FLAC playing its cached Opus with no second look, permanently, because
        // its id was still recorded as answered. Both are documented where the
        // state lives.
        NerdStats.forgetLastSession()
        QualityUpgrade.forgetLastSession()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_logo) },
        )

        // The player screen toggles QueueShuffle directly on its MediaController.
        // Observe the shared state here so the notification's Shuffle icon and
        // label follow that toggle immediately as well.
        scope.launch {
            QueueShuffle.enabled
                .collectLatest {
                    mediaSession?.setCustomLayout(notificationButtons())
                }
        }
        scope.launch {
            LikeState.overrides.collectLatest {
                mediaSession?.setCustomLayout(notificationButtons())
            }
        }

        // No user agent on the factory: the right one depends on which client
        // minted the URL, so it is set per request below. Setting it here as
        // well would not override that — OkHttpDataSource *appends* the
        // factory's agent after the request's, and the fetch would go out
        // carrying two contradictory User-Agent headers.
        val resolvingFactory = ResolvingDataSource.Factory(
            // Innermost, so it chunks the real googlevideo URL the resolver
            // below has already substituted in — see [ChunkedDataSource] for
            // why an open-ended read of one is worth avoiding.
            ChunkedDataSource.Factory(OkHttpDataSource.Factory(Http.client), STREAM_CHUNK_BYTES),
        ) { dataSpec ->
            // Which track everything below is for, said once, because none of
            // it would otherwise know: this runs on ExoPlayer's loader thread
            // with a DataSpec and nothing else, and the work it starts — the
            // source ladder, the module sandbox, a client walk — logs from
            // places several layers deep that have no idea whose bytes they
            // are fetching. Read-ahead means the track being resolved here is
            // usually *not* the one playing, which is exactly why the lines
            // have to say. See [TrackLog.about].
            val about = TrackLog.about(mediaIdIn(dataSpec.uri))
            // A source-backed track is resolved by whichever source can serve
            // it, which is not necessarily the one it was queued from — see
            // [SourceResolver.resolve]. Handled ahead of the YouTube path
            // because these carry no `v` parameter and would otherwise fall
            // straight through unresolved.
            if (dataSpec.uri.authority == "source") {
                val stream = runBlocking(about) {
                    withTimeout(RESOLVE_TIMEOUT_MS) { SourceResolver.resolve(dataSpec.uri) }
                } ?: throw java.io.IOException("No enabled source could serve ${dataSpec.uri.getQueryParameter("n")}")
                NerdStats.onSourceStream(dataSpec.uri.getQueryParameter("t"), stream.format)
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(stream.url))
                    .setHttpRequestHeaders(stream.headers)
                    .build()
            }
            val videoId = dataSpec.uri.getQueryParameter("v")
                ?: return@Factory dataSpec
            // An explicit rollback is not a preference for a different
            // candidate: it means this exact YouTube rendition, immediately.
            // Answer it before StreamChoice, the module race and a pending
            // upgrade can put another source back under the listener.
            if (dataSpec.uri.getQueryParameter(DIRECT_YOUTUBE_PARAMETER) == "1") {
                QualityUpgrade.forget(videoId)
                StreamChoice.forget(videoId)
                NerdStats.clearDeclared(videoId)
                val streamUrl = try {
                    runBlocking(about) {
                        withTimeout(RESOLVE_TIMEOUT_MS) { StreamResolver.resolve(videoId) }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw java.io.IOException("Direct YouTube resolution timed out for $videoId", e)
                }
                val headers = PlayerClient.forStreamUrl(streamUrl).mediaHeaders()
                TrackLog.d("BitChord", "serving original YouTube version for $videoId", about = videoId)
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(streamUrl))
                    .setHttpRequestHeaders(headers)
                    .build()
            }
            // Automix owns a base-cache Opus rendition. It must bypass the
            // playback race winner (JioSaavn, a module, or a lossless upgrade)
            // and resolve directly to YouTube for this analysis-only request.
            if (AutomixAnalysisSource.requestsYouTubeOpus(
                    dataSpec.uri.getQueryParameter(AutomixAnalysisSource.OPUS_QUERY_PARAMETER),
                )
            ) {
                val streamUrl = try {
                    runBlocking(about) {
                        withTimeout(RESOLVE_TIMEOUT_MS) { StreamResolver.resolve(videoId) }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw java.io.IOException("Automix Opus resolution timed out for $videoId", e)
                }
                val headers = PlayerClient.forStreamUrl(streamUrl).mediaHeaders()
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(streamUrl))
                    .setHttpRequestHeaders(headers)
                    .build()
            }
            // An upgraded item carries a marker and its stream has already
            // been found — see [QualityUpgrade]. Answered before anything
            // else, and without re-resolving: this exact URL is what the
            // player was told it was getting when it agreed to the swap.
            QualityUpgrade.forcedStream(dataSpec.uri)?.let { upgraded ->
                // An audition opens this same stream before a note of the one
                // playing has been touched — see [auditionUpgrade] — so what it
                // is about to be handed describes a swap that has not happened
                // and may never. Recording it here would light "Lossless" over
                // the lossy stream still coming out of the speaker. The real
                // open, moments later, records it.
                val proving = QualityUpgrade.isAuditioning(videoId)
                if (!proving) NerdStats.onSourceStream(videoId, upgraded.format)
                // Logged because the alternative — a swap that silently never
                // reached its stream — is indistinguishable in the logs from
                // one that reached it and got nothing back, and the two have
                // opposite fixes.
                TrackLog.d(
                    "BitChord",
                    "${if (proving) "auditioning" else "serving"} upgraded $videoId " +
                        "from ${Uri.parse(upgraded.url).host} " +
                        "at ${dataSpec.position} (${upgraded.format.summary})",
                    about = videoId,
                )
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(upgraded.url))
                    .setHttpRequestHeaders(upgraded.headers)
                    .build()
            }
            // A downloaded copy is *not* substituted here, deliberately. This
            // point is inside the HTTP-only half of the chain — below
            // DefaultDataSource, which has already given up on dispatching by
            // scheme, and below the cache bypass that keeps local files from
            // being written to disk a second time. A content:// URI returned
            // from here reaches OkHttp, which refuses it as a malformed URL.
            // Which copy of a track to play is settled where the item is built
            // instead: see [Song.toMediaItem].
            // Whoever is already filling this track's cache entry keeps it.
            // Everything below decides between servers holding *different
            // files*, and this method is called again for every re-open of a
            // track — including the continuation fetch when playback runs off
            // the end of the cached bytes. Deciding afresh each time is how
            // the middle of an MP4 ended up appended to a WebM. See
            // [StreamChoice].
            StreamChoice.of(videoId)?.let { serving ->
                // What the stream claims to be, restated on every open rather
                // than only on the one that chose it.
                //
                // The race below is what used to report this, and it was enough
                // while the race was the only way a substitution could be made.
                // Read-ahead now pins one before the track is reached, so a
                // warmed track arrives *here* on its very first open and never
                // reaches the race at all — leaving the player with a 320kbps
                // stream and nothing on record saying so, and the quality badge
                // reading blank until the decoder got far enough to measure it
                // for itself.
                //
                // Only when the format states something. A plain YouTube choice
                // is remembered with an empty one, and writing that over a
                // claim some other path made would be worse than saying nothing.
                if (serving.format != StreamFormat()) {
                    NerdStats.onSourceStream(videoId, serving.format)
                }
                // Read-ahead can pin JioSaavn's quick 320kbps answer before
                // this track becomes current.  It is the right answer for an
                // immediate start, but it is not the final quality verdict:
                // returning here used to bypass [resolveWithModulePriority],
                // the only path which calls [QualityUpgrade.settledForLess].
                // Consequently a warmed track displayed its high-quality
                // source but never kept the upgrading state or asked the
                // module for its lossless copy.
                //
                // Only substituted, non-lossless choices need this. A pinned
                // YouTube URL has no source result to promote, and a lossless
                // module result already satisfies the request.
                if (StreamChoice.isSubstitute(videoId) &&
                    serving.format.isLossless != true &&
                    QualityUpgrade.couldStillUpgrade(videoId, dataSpec.uri)
                ) {
                    val pending = QualityUpgrade.settledForLess(
                        mediaId = videoId,
                        target = SourceResolver.targetIn(dataSpec.uri),
                        playing = serving.format,
                    )
                    if (!pending) NerdStats.onLosslessRaceEnd(videoId)
                }
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(serving.url))
                    .setHttpRequestHeaders(serving.headers)
                    .build()
            }
            // A track queued from YouTube may be held by a source the user
            // ranked above it — see [SourceResolver.substituteForYouTube] and
            // [raceYouTubeOrModule]. Only worth the extra lookup when
            // something actually outranks YouTube; otherwise this is the
            // plain resolve every build before this one made.
            if (!SourceResolver.canSubstituteForYouTube()) {
                val streamUrl = try {
                    runBlocking(about) {
                        withTimeout(RESOLVE_TIMEOUT_MS) { StreamResolver.resolve(videoId) }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw java.io.IOException("Stream resolution timed out for $videoId", e)
                }
                // googlevideo names the client that minted the URL inside the
                // URL itself, and compares it against the request that comes
                // back for the bytes. A mismatch is answered with a throttled
                // trickle or a 403 rather than an error worth the name, so the
                // fetch is dressed as whatever the URL says it should be.
                val headers = PlayerClient.forStreamUrl(streamUrl).mediaHeaders()
                // Recorded even though only one server can answer here: a
                // source enabled from Settings mid-track flips the branch
                // above under a half-filled cache entry, and the entry would
                // then be finished by a different file.
                StreamChoice.remember(videoId, SourceStream(streamUrl, headers = headers), substituted = false)
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(streamUrl))
                    .setHttpRequestHeaders(headers)
                    .build()
            }
            val won = runBlocking(about) {
                resolveWithModulePriority(
                    videoId = videoId,
                    target = SourceResolver.targetIn(dataSpec.uri),
                )
            }
            when (won) {
                is Resolved.Module -> {
                    NerdStats.onSourceStream(videoId, won.stream.format)
                    StreamChoice.remember(videoId, won.stream, substituted = true)
                    dataSpec.buildUpon()
                        .setUri(Uri.parse(won.stream.url))
                        .setHttpRequestHeaders(won.stream.headers)
                        .build()
                }
                // A module could have served this and didn't — it missed, its
                // server was slow, or the lookup ran out of budget. The last
                // of those is worth chasing rather than accepting: measured
                // here, a module's stream URL arrived 66ms after the live path
                // gave up on it, and the difference between a FLAC and a
                // YouTube Opus stream came down to that. The second look has
                // no such deadline, so what was nearly in hand is asked for
                // again while the fallback plays.
                is Resolved.YouTube -> {
                    val headers = PlayerClient.forStreamUrl(won.url).mediaHeaders()
                    StreamChoice.remember(videoId, SourceStream(won.url, headers = headers), substituted = false)
                    dataSpec.buildUpon()
                        .setUri(Uri.parse(won.url))
                        .setHttpRequestHeaders(headers)
                        .build()
                }
            }
        }
        // Read-ahead resolves streams through the same chain the player does.
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, resolvingFactory)
        AudioCache.setUpstream(defaultDataSourceFactory)
        mediaSourceFactory = DefaultMediaSourceFactory(AudioCache.playbackFactory(defaultDataSourceFactory))
            .setLoadErrorHandlingPolicy(PermanentAwareLoadErrorPolicy())

        configuredFloatOutput = shouldEnableFloatOutput()
        val exoPlayer = buildPlayer(spatialAudioProcessorA, transitionFilterA, ownsSession = true)
        val sparePlayer = buildPlayer(spatialAudioProcessorB, transitionFilterB, ownsSession = false)
        player = exoPlayer
        spare = sparePlayer
        // Both sinks feed the same session id, so the system equalizer and any
        // other effect attached to the app applies to whichever player happens
        // to be audible. Without it a crossfade would audibly change EQ halfway
        // through, and again at every handoff.
        sparePlayer.audioSessionId = exoPlayer.audioSessionId
        audioManager?.registerAudioDeviceCallback(outputDeviceCallback, null)
        applyOutputRoute()

        AppSettings.audioSessionId.value = exoPlayer.audioSessionId
        applySettings(exoPlayer)
        applySettings(sparePlayer)
        observeSettings()
        observeScrobbling()
        observeDiscord()
        watchSleepTimer()
        if (restoreLastQueue(exoPlayer)) {
            publishWidgetState()
        } else {
            MediaWidgetSnapshot.save(this, MediaWidgetSnapshot.EMPTY)
            MediaWidget.refresh(this)
        }
        // History pings fire once a track is actually audible — both when
        // playback starts and when the queue moves on while already playing.
        lastRepeatMode = exoPlayer.repeatMode
        exoPlayer.addListener(playbackListener)
        loadAutoplayForCurrentTrack()

        // Only the analytics listener reports the format the audio renderer was
        // configured with. Treated as a trigger rather than a source: the
        // publisher reads the format off the player, so it can't go stale
        // against the track the bitrate is looked up for.
        exoPlayer.addAnalyticsListener(formatListener)

        reportProgress()

        val controller = createCrossfadeController()
        crossfade = controller
        controller.start()

        mediaSession = MediaLibrarySession.Builder(
            this,
            SessionPlayer(exoPlayer, controller) { lastPublishedSubtitle },
            MediaLibraryCallback(),
        )
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .build()
        mediaSession?.setCustomLayout(notificationButtons())
    }

    private fun createCrossfadeController() = CrossfadeController(
            scope,
            active = { requireNotNull(player) },
            standby = { requireNotNull(spare) },
            onHandoff = ::adoptPlayer,
            analysisFor = { item -> trackAnalyzer.analysisFor(item.mediaId) },
            requestAnalysis = { item, durationMs ->
                item.localConfiguration?.uri?.let { uri ->
                    trackAnalyzer.request(item.mediaId, uri, durationMs / 1000.0)
                }
            },
            // "Incoming" and "outgoing" are roles, not players. The controller
            // only ever filters after the handoff, by which point the incoming
            // track is on the session player and the outgoing one is on the
            // spare — so these read the role fields fresh on every call rather
            // than closing over an instance that will have changed hands.
            filters = object : TransitionFilters {
                override fun incoming(lowPassHz: Float, highPassHz: Float) =
                    activeFilter.setCutoffs(lowPassHz, highPassHz)

                override fun outgoing(lowPassHz: Float, highPassHz: Float) =
                    spareFilter.setCutoffs(lowPassHz, highPassHz)
            },
            analysisRunningFor = { item -> trackAnalyzer.isAnalysing(item.mediaId) },
        )

    /**
     * The one custom layout advertised to all Media3 control surfaces.
     *
     * AutoPlay is deliberately not here. It stays a player-screen control: the
     * session command remains available so [toggleAutoplay] still routes through
     * this service, it just isn't offered as a notification button.
     */
    private fun notificationButtons(): List<CommandButton> {
        val favorite = CommandButton.Builder(
            if (LikeState.overrides.value[player?.currentMediaItem?.mediaId] == LikeStatus.LIKE) {
                CommandButton.ICON_HEART_FILLED
            } else {
                CommandButton.ICON_HEART_UNFILLED
            },
        )
            .setSessionCommand(favoriteCommand)
            .setDisplayName("Favorite")
            .build()
        val shuffleEnabled = QueueShuffle.enabled.value
        val shuffle = CommandButton.Builder(
            if (shuffleEnabled) {
                CommandButton.ICON_SHUFFLE_ON
            } else {
                CommandButton.ICON_SHUFFLE_OFF
            },
        )
            .setSessionCommand(shuffleCommand)
            .setDisplayName(if (shuffleEnabled) "Shuffle off" else "Shuffle on")
            .build()
        return listOf(favorite, shuffle)
    }

    private fun toggleShuffleFromNotification() {
        player?.let(QueueShuffle::toggle)
        mediaSession?.setCustomLayout(notificationButtons())
    }

    private fun toggleAutoplayFromNotification() {
        val enabled = !AppSettings.autoplay.value
        AppSettings.setAutoplay(enabled)
        if (enabled) {
            autoplayLoadJob?.cancel()
            autoplayLoadJob = null
            autoplaySeed = null
            loadAutoplayForCurrentTrack()
        } else {
            autoplayLoadJob?.cancel()
            autoplayLoadJob = null
            autoplaySeed = null
            dropAutoplayTracksFromQueue()
            // Switching AutoPlay off is the listener saying they don't want
            // those tracks; leaving a stash behind would put them back the next
            // time repeat-all ended.
            repeatAllStash = emptyList()
            repeatAllStashSeed = null
        }
        mediaSession?.setCustomLayout(notificationButtons())
    }

    /**
     * Tops the queue back up to [MAX_QUEUED_AUTOPLAY] AutoPlay-suggested tracks
     * ahead of whatever is currently playing. Run on every track change rather
     * than only once the queue runs dry, so a freshly played suggestion is
     * replaced by a new one appended after the ones still waiting instead of
     * everything arriving in one burst at the end of the queue.
     */
    private fun loadAutoplayForCurrentTrack() {
        val exoPlayer = player ?: return
        if (!AppSettings.autoplay.value || exoPlayer.repeatMode == Player.REPEAT_MODE_ALL) {
            return
        }
        val current = exoPlayer.currentMediaItem?.toSong() ?: return
        if (AppSettings.dontRepeatSuggestions.value) sessionSongHistory += current
        val queuedAutoplay = (exoPlayer.currentMediaItemIndex + 1 until exoPlayer.mediaItemCount)
            .count { exoPlayer.getMediaItemAt(it).fromAutoplay }
        val needed = MAX_QUEUED_AUTOPLAY - queuedAutoplay
        if (needed <= 0) return
        if (autoplaySeed == current.videoId) return
        autoplaySeed = current.videoId
        autoplayLoadJob = scope.launch {
            val queueSongs = (0 until exoPlayer.mediaItemCount)
                .map { exoPlayer.getMediaItemAt(it).toSong() }
            val existing = if (AppSettings.dontRepeatSuggestions.value) {
                queueSongs + sessionSongHistory
            } else {
                queueSongs
            }
            loadAutoplayTracks(existing, current, needed)
                .onSuccess { resolved ->
                    val activePlayer = player ?: return@onSuccess
                    if (!AppSettings.autoplay.value ||
                        activePlayer.currentMediaItem?.mediaId != current.videoId
                    ) {
                        return@onSuccess
                    }
                    activePlayer.addMediaItems(resolved.map { it.toMediaItem() })
                    if (AppSettings.dontRepeatSuggestions.value) sessionSongHistory += resolved
                }
                .onFailure {
                    TrackLog.w("BitChord", "notification autoplay failed: ${it.message}", about = current.videoId)
                }
        }
    }

    /**
     * Takes back what AutoPlay queued and hasn't played yet — what switching
     * AutoPlay off means for a queue it has already been extending. Removed
     * from the bottom up so the indexes ahead of each removal still hold, and
     * handed back in queue order for the one caller that intends to put them
     * in again.
     */
    private fun dropAutoplayTracksFromQueue(): List<MediaItem> {
        val exoPlayer = player ?: return emptyList()
        val dropped = mutableListOf<MediaItem>()
        for (index in exoPlayer.mediaItemCount - 1 downTo exoPlayer.currentMediaItemIndex + 1) {
            val item = exoPlayer.getMediaItemAt(index)
            if (!item.fromAutoplay) continue
            dropped += item
            exoPlayer.removeMediaItem(index)
        }
        return dropped.reversed()
    }

    /** Clears the queue's AutoPlay tail for the duration of repeat-all, keeping it to put back. */
    private fun stashAutoplayTracks() {
        val exoPlayer = player ?: return
        // Only ever taken once per stretch of repeat-all: cycling
        // OFF -> ALL -> ONE -> OFF sets the mode three times, and the second
        // and third of those must not overwrite a full stash with the empty
        // queue tail the first one left behind.
        if (repeatAllStash.isNotEmpty()) return
        val dropped = dropAutoplayTracksFromQueue()
        if (dropped.isEmpty()) return
        repeatAllStash = dropped
        repeatAllStashSeed = exoPlayer.currentMediaItem?.mediaId
    }

    /**
     * Puts the stashed AutoPlay tracks back when repeat-all ends.
     *
     * Refused, rather than forced, in the cases where the stash no longer
     * describes the queue: AutoPlay switched off while the loop ran, or the
     * loop played on past the track the stash was taken behind. Both leave
     * [loadAutoplayForCurrentTrack] to fill the queue the ordinary way.
     */
    private fun restoreAutoplayTracks() {
        val exoPlayer = player ?: return
        val stashed = repeatAllStash
        val seed = repeatAllStashSeed
        repeatAllStash = emptyList()
        repeatAllStashSeed = null
        // The seed is stale now either way, so the queue can be topped up again
        // for this track — without this the guard in [loadAutoplayForCurrentTrack]
        // reads a track it has already loaded for and returns, which is how a
        // queue whose stash was refused ended up with nothing after it at all.
        autoplayLoadJob?.cancel()
        autoplayLoadJob = null
        autoplaySeed = null
        if (stashed.isEmpty() || !AppSettings.autoplay.value) return
        if (exoPlayer.currentMediaItem?.mediaId != seed) return
        // A track the listener queued by hand during the loop is not queued
        // twice for having been in the mix before it.
        val present = (0 until exoPlayer.mediaItemCount)
            .mapTo(mutableSetOf()) { exoPlayer.getMediaItemAt(it).mediaId }
        val restored = stashed.filter { it.mediaId !in present }
        if (restored.isEmpty()) return
        exoPlayer.addMediaItems(restored)
    }

    private fun toggleFavoriteFromNotification(videoId: String) {
        favoriteActionJob?.cancel()
        val previous = LikeState.overrides.value[videoId] ?: LikeStatus.INDIFFERENT
        val target = if (previous == LikeStatus.LIKE) {
            LikeStatus.INDIFFERENT
        } else {
            LikeStatus.LIKE
        }

        // Match the player UI: update both surfaces immediately, then reconcile
        // the optimistic state with YouTube in the background.
        LikeState.set(videoId, target)
        mediaSession?.setCustomLayout(notificationButtons())
        favoriteActionJob = scope.launch {
            YtMusicRepository.rate(videoId, target)
                .onFailure {
                    LikeState.set(videoId, previous)
                    mediaSession?.setCustomLayout(notificationButtons())
                    TrackLog.w("BitChord", "notification favorite failed: ${it.message}", about = videoId)
                }
        }
    }

    /**
     * Both players, built identically. Only [ownsSession] differs, and only at
     * construction — it moves at every handoff, see [setSessionOwner].
     *
     * They share the media source factory, so whichever one is arming reads from
     * the same on-disk cache the other is playing out of rather than
     * re-resolving a stream URL for audio that is already local.
     */
    private fun buildPlayer(
        spatial: SpatialAudioProcessor,
        filter: TransitionFilterProcessor,
        ownsSession: Boolean,
    ): ExoPlayer = ExoPlayer.Builder(this)
        .setRenderersFactory(silenceSkippingRenderers(spatial, filter))
        .setMediaSourceFactory(requireNotNull(mediaSourceFactory))
        .setLoadControl(farBufferingLoadControl())
        .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ ownsSession)
        .setHandleAudioBecomingNoisy(ownsSession)
        // Back restarts the track once you're this far into it; only a
        // press before that steps to the previous one.
        .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)
        .build()

    /**
     * Moves the session onto the player the crossfade has just started the
     * incoming track on. This is the whole of the handoff: no seek, no
     * re-buffer, and no audio rendered twice.
     *
     * Order matters in one place — focus is released on the outgoing player
     * *before* the incoming one asks for it, so the app never holds two focus
     * requests at once and never briefly holds none.
     */
    private fun adoptPlayer(outgoing: ExoPlayer, incoming: ExoPlayer) {
        setSessionOwner(outgoing, owns = false)
        setSessionOwner(incoming, owns = true)

        outgoing.removeListener(playbackListener)
        outgoing.removeAnalyticsListener(formatListener)
        // The fields move before the listeners are attached, so anything the
        // first callback reads already describes the new arrangement.
        player = incoming
        spare = outgoing
        val heldFilter = activeFilter
        activeFilter = spareFilter
        spareFilter = heldFilter
        incoming.addListener(playbackListener)
        incoming.addAnalyticsListener(formatListener)

        mediaSession?.player = SessionPlayer(incoming, requireNotNull(crossfade)) { lastPublishedSubtitle }

        // The queue moving on used to arrive here as an item transition on the
        // one player that owned the queue. It cannot any more — the incoming
        // track started as its own player's *first* item, which fires on a
        // player nothing was listening to yet — so the bookkeeping that hung off
        // that callback is driven explicitly instead. Without this the crossfade
        // would silently stop scrobbling, stop writing history, stop honouring
        // "sleep after this song" and stop reading ahead.
        onTrackBecameCurrent(
            incoming.currentMediaItem,
            previousEnded = true,
            reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            alreadyAudible = true,
        )

        // A crossfade starts the incoming item on the player that was idle, so
        // it never delivers onMediaItemTransition to playbackListener. Keep
        // AutoPlay's refill on the same track-change path as the ordinary
        // player transition: otherwise the initial suggestions are consumed
        // one by one and a long background session eventually runs dry.
        autoplayLoadJob?.cancel()
        autoplayLoadJob = null
        autoplaySeed = null
        loadAutoplayForCurrentTrack()
    }

    /**
     * Only one player may handle audio focus at a time.
     *
     * Two focus-handling players in one process fight each other: the standby
     * taking focus as it starts would have Media3 pause the player that lost it,
     * cutting the outgoing track dead instead of fading it. Focus follows the
     * session, and so does "becoming noisy" — unplugging headphones should pause
     * the song you are listening to, which is whichever one the session is on.
     */
    private fun setSessionOwner(target: ExoPlayer, owns: Boolean) {
        target.setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ owns)
        target.setHandleAudioBecomingNoisy(owns)
    }

    /**
     * Where a tap on the session lands. Media3 uses this both as the media
     * notification's contentIntent and as the session activity handed to the
     * platform MediaSession.
     *
     * This is not cosmetic on One UI: Samsung's Now Bar / Live Notification
     * chip is a launcher for the session, so a session that advertises nowhere
     * to go is skipped and only the plain shade notification survives. Same
     * reason the notification itself was previously un-tappable.
     */
    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            // MainActivity is singleTask, so this resumes the existing task
            // rather than stacking a second copy of the UI.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun registerCurrentPlay() {
        player?.currentMediaItem?.mediaId?.let(PlaybackTracker::onPlaying)
    }

    /**
     * Everything that has to happen when a different song becomes the one
     * playing: history, scrobbles, ListenBrainz, the sleep timer, read-ahead
     * and the second look for a better copy.
     *
     * Called from two places, and it has to be, because there are now two ways
     * for the current song to change. ExoPlayer's own item transition covers
     * the ordinary ones — the queue advancing, a skip, a repeat. A crossfade
     * covers none of them: the incoming track starts life as the *first* item
     * of the other player, which fires a transition on a player nothing is
     * listening to yet, so [adoptPlayer] calls this by hand at the handoff.
     * Before that split existed this logic lived inside the callback, and
     * moving to two players would have silently stopped every crossfaded track
     * from being scrobbled, recorded, or read ahead for.
     *
     * @param previousEnded whether the song being replaced ran to its end, as
     *   opposed to being skipped past. Only an ended song is a listen.
     * @param alreadyAudible whether the track was already sounding when it
     *   became current, which is only true of a crossfade handoff.
     */
    private fun onTrackBecameCurrent(
        mediaItem: MediaItem?,
        previousEnded: Boolean,
        reason: Int,
        alreadyAudible: Boolean = false,
    ) {
        val exoPlayer = player ?: return

        // A crossfade handoff never fires [formatListener] for the entering
        // track — [CrossfadeController] starts its decoder during ARMING,
        // while the listener is still on the outgoing player, and it isn't
        // reattached until this handoff, by which point the format has
        // already been reported once and won't be again. The claim from
        // source resolution stands in until the decoder itself confirms or
        // corrects it — see [formatListener]'s onAudioInputFormatChanged.
        activeTrackIsDolbyAtmos = NerdStats.declaredFormat(mediaItem?.mediaId)?.isDolbyAtmos == true
        applySpatialAudioEnabled()

        // Keep a real, bounded history in the player rather than merely hiding
        // old rows in Compose. MediaController mirrors the playlist across the
        // session boundary, persistence snapshots it, and crossfade duplicates
        // it onto the standby player, so letting completed entries accumulate
        // made every one of those paths progressively more expensive.
        val expiredHistory = queueHistoryTrimCount(exoPlayer.currentMediaItemIndex)
        if (expiredHistory > 0) {
            if (exoPlayer.repeatMode == Player.REPEAT_MODE_ALL) {
                // Repeat-all still means the whole queue. Rotate history that
                // has fallen out of the visible 25-song window to the end
                // instead of deleting it; it becomes upcoming again in the
                // same order and the loop can continue indefinitely.
                repeat(expiredHistory) {
                    exoPlayer.moveMediaItem(0, exoPlayer.mediaItemCount - 1)
                }
            } else {
                exoPlayer.removeMediaItems(0, expiredHistory)
            }
        }
        // A *different* track is a clean slate for [recoverFrom], and so is the
        // same track becoming current for any reason other than that method's
        // own retry. The distinction is the whole of the reported loading loop.
        //
        // This was an unconditional clear, on the reasoning that the count exists
        // to stop one broken stream looping rather than to hold a grudge for the
        // session — and that reasoning is right about the listener pressing play
        // again, which is why it is kept below. What it missed is that "a track
        // became current" is not the same event as "something other than the
        // retry happened": ExoPlayer fires a transition for PLAYLIST_CHANGED and
        // for SEEK, and [recoverFrom]'s recovery *is* a seek — so the counter was
        // reset by the very retries it was counting. The report shows four resets
        // in 2m41s, each followed by a fresh "attempt 1", eight failures against
        // a budget of two, and roughly twenty-seven full resolve walks for one
        // unplayable track. [retryingMediaId] is the one case that must not
        // reset; everything else still does.
        val becameCurrent = mediaItem?.mediaId
        if (becameCurrent == null || becameCurrent != retryingMediaId) {
            recoveries.clear()
        }
        retryingMediaId = null

        // Where the wait starts, for the log in onIsPlayingChanged — unless
        // there was no wait. A crossfaded track has been audible for as long as
        // it has been current, so `onIsPlayingChanged` will never fire for it
        // and an armed timer would sit there until some unrelated buffering
        // blip tripped it, reporting a wait of seconds for a track that started
        // instantly. Measured one at 16871ms.
        trackSelectedAt = if (alreadyAudible) null else SystemClock.elapsedRealtime()
        if (alreadyAudible) {
            TrackLog.d(
                "BitChord",
                "TIMING first audio: 0ms, the crossfade covered it",
                about = mediaItem?.mediaId,
            )
        }
        // And the same instant on the wall clock, which is the one
        // logcat stamps its lines with — see [TrackLog].
        mediaItem?.mediaId?.let(TrackLog::onTrackStarted)
        TrackLog.d(
            "BitChord",
            "TIMING track selected: ${mediaItem?.mediaId} (reason=$reason)",
            about = mediaItem?.mediaId,
        )

        // currentPosition already belongs to the new item by now, so
        // the outgoing track is closed out on the last sampled value.
        PlaybackTracker.onTrackChanged(lastPositionSeconds)
        lastPositionSeconds = 0

        // Scrobbling: stop old song, start new song
        scrobbleManager?.onSongStop()
        // And the local record, which needs the transition even when the track
        // id doesn't change: repeat-one plays the same song again, and without
        // this the second play through is a continuation of the first and is
        // never counted.
        ListeningRecorder.onStopped()
        val newSong = mediaItem?.toSong()
        val durationMs = exoPlayer.duration.takeIf { it > 0 }
        if (exoPlayer.isPlaying) {
            scrobbleManager?.onSongStart(newSong, durationMs)
        }

        // ListenBrainz: submit finished for old song, playing_now for new song.
        // The finished listen only counts when the track actually ended —
        // an auto-advance, a repeat, or a crossfade at the very end. A
        // manual skip (SEEK) means the song wasn't listened to, so it must
        // not be scrobbled.
        val ended = previousEnded
        val prevSong = listenBrainzSong
        val prevStart = listenBrainzStartMs
        if (prevSong != null && ended && prevStart > 0L) {
            submitListenBrainzFinished(prevSong, prevStart, listenBrainzDurationMs)
        }
        listenBrainzSong = newSong
        listenBrainzStartMs = if (exoPlayer.isPlaying) System.currentTimeMillis() else 0L
        listenBrainzDurationMs = durationMs
        if (newSong != null && exoPlayer.isPlaying) {
            submitListenBrainzPlayingNow(newSong, 0L, durationMs)
        }

        // Discord: the whole of "live updating" for a card whose bar Discord
        // draws itself. Only a track change needs a new presence; the countdown
        // in between is Discord's own arithmetic.
        if (exoPlayer.isPlaying) pushDiscordPresence(exoPlayer)

        // "Sleep after this song": the queue moving on by itself is the
        // moment the track the user meant has finished. REPEAT counts
        // too, or the timer would never fire with repeat-one on.
        if (ended && SleepTimer.afterTrack.value) {
            exoPlayer.pause()
            SleepTimer.cancel()
        }
        if (exoPlayer.isPlaying) registerCurrentPlay()
        savePlaybackState(exoPlayer)
        prefetchAround(exoPlayer)
        // The second look belongs to the track it was started for; the
        // queue moving on ends it, whatever it had found — and starts
        // the new track's own, which nothing else here would. The
        // track arriving has usually been resolved already, by
        // ExoPlayer preparing the next item while this one played, so
        // it is pending by now; the ones that aren't are picked up by
        // the sampler in [reportProgress].
        upgradeJob?.cancel()
        lookForBetterCopy(exoPlayer)
        // Covers crossfades too: a blended advance never reaches
        // onMediaItemTransition, and [adoptPlayer] calls this handler by hand.
        publishWidgetState()
        loadLyricsForCurrentTrack()
        if (exoPlayer.isPlaying) startLyricsTicker()
        // Cleared rather than re-published. The renderer is still
        // configured for the track that just ended at this point, so
        // reading the format here reports the *previous* song — which
        // is how a lossy track spent its whole resolve showing the
        // "Hi-Res Lossless" badge the track before it had earned.
        // Nothing measured is better than something wrong, and the
        // gap is exactly when "Loading lossless" should be showing
        // instead. The periodic sampler below and
        // onAudioInputFormatChanged both re-publish once the decoder
        // has actually settled on this track, so the same-format case
        // the old call was here to cover is still covered.
        NerdStats.current.value = null
    }

    /** The background hunt for a better copy of whatever is playing. */
    private var upgradeJob: Job? = null

    /** Which track [upgradeJob] is hunting for — see [lookForBetterCopy]. */
    private var upgradeFor: String? = null

    /**
     * How many times each track has been picked up off the floor, so a stream
     * that fails the same way every time stops rather than loops. Reset when
     * the queue genuinely moves on, not when a track merely re-prepares.
     */
    private val recoveries = mutableMapOf<String, Int>()

    /**
     * The track [recoverFrom] is about to seek-and-prepare, so that the
     * transition its own retry may fire can be told from the queue moving on or
     * the listener asking again — see [onTrackBecameCurrent]. Cleared by the
     * transition it describes, the same way [swappingMediaId] is.
     */
    private var retryingMediaId: String? = null

    /**
     * Media3's retry budget, with one thing added: a load error that cannot
     * succeed on a second attempt does not get one.
     *
     * There was no policy here at all, which meant
     * [DefaultLoadErrorHandlingPolicy] — three tries at a 1s/2s/3s backoff,
     * against *any* IOException. Stacked on top of this service's own
     * [MAX_RECOVERIES] counter and read-ahead's independent resolves, that is
     * where the "infinite loading" came from: the app logged
     * "leaving it alone" after its third attempt, and then Media3 quietly
     * started a fourth on its own schedule — visible in the report as a full
     * resolve walk beginning with no track selection before it, forty seconds
     * after the app had given up. Nothing in the log named it, because nothing
     * in the app had asked for it.
     *
     * Only [StreamResolver.PermanentlyUnplayableException] is refused, and it is
     * refused rather than delayed because the resolver has already established
     * the answer cannot change — it is the type it uses to say exactly that.
     * Everything else keeps the default behaviour, which is right: a shaped
     * response or a dropped connection is worth another go.
     */
    private class PermanentAwareLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (isPermanent(loadErrorInfo.exception)) return C.TIME_UNSET
            return super.getRetryDelayMsFor(loadErrorInfo)
        }

        private fun isPermanent(error: Throwable?): Boolean {
            var cause = error
            var depth = 0
            while (cause != null && depth++ < CAUSE_DEPTH) {
                if (cause is StreamResolver.PermanentlyUnplayableException) return true
                cause = cause.cause?.takeIf { it !== cause }
            }
            return false
        }

        private companion object {
            /** Media3 and the coroutine machinery both wrap; nothing nests deeper than this. */
            const val CAUSE_DEPTH = 8
        }
    }

    /**
     * Puts a track that died mid-read back on its feet.
     *
     * Two things get thrown away before trying again, because both have been
     * seen to be the actual fault and neither is visible from the exception:
     *
     *  - The cached bytes. An entry filled from two different files reads
     *    fine until playback reaches the seam and then throws forever, and no
     *    number of retries against the same entry will do anything else.
     *  - The choice of who serves the track. If the source that was picked is
     *    the one handing over something unreadable, resolving again from
     *    scratch is the only way to land anywhere else.
     *
     * The position is kept: this should look like a hiccup, not like the song
     * starting over.
     */
    private fun recoverFrom(error: PlaybackException, player: ExoPlayer) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val uri = item.localConfiguration?.uri
        val position = player.currentPosition.coerceAtLeast(0L)
        val attempts = recoveries.getOrDefault(mediaId, 0) + 1
        recoveries[mediaId] = attempts
        TrackLog.w(
            "BitChord",
            "playback failed for $mediaId at ${position}ms (${error.errorCodeName}), attempt $attempts",
            error,
            about = mediaId,
        )
        // A local file that is not there is the one failure retrying cannot
        // touch, and the only one where the fix is to stop asking for the file.
        //
        // Everything below this point recovers a *stream*: it discards cached
        // bytes, releases the choice of who serves the track, and prepares the
        // same item again. Against `file:///…/Drake - Janice STFU.m4a` that is
        // all wasted — the uri is baked into the item already in the timeline,
        // so `prepare()` reopens the identical dead path and fails identically.
        // Observed as eight `ERROR_CODE_IO_FILE_NOT_FOUND`s in three seconds
        // against a download whose file had been deleted from a file manager,
        // ending in a track that simply refused to play.
        //
        // Rebuilding the item without its local uri is what turns that into a
        // stream, and dropping the record is what stops the next play walking
        // into the same hole. Deliberately ahead of the verdict and the attempt
        // budget below: this is not an attempt spent, it is a different source.
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND &&
            restreamMissingLocalFile(player, item, uri, position)
        ) {
            return
        }
        // Giving up on the *retry*, not on everything below it.
        //
        // A track that has exhausted its attempts is not finished with.
        // [recoveries] is cleared the moment any track becomes current, so the
        // listener who presses play again gets a fresh count — and used to get,
        // along with it, the exact URL that had just failed three times.
        // [StreamChoice] outlives this method by [StreamChoice.TTL_MS], and the
        // resolving factory reads it *before* it resolves anything, so the
        // replay failed instantly and in silence: no resolve logged, no lookup
        // attempted, and none of the refusals recorded below ever consulted,
        // because reaching them means getting as far as resolving. Fifteen
        // minutes of a track that cannot be played and does not even try, which
        // to the listener is a track that is permanently broken. Reported as
        // "sometimes songs don't play even if I've played it before", and the
        // 1.4 log of one shows it exactly — a selection, five seconds, a 404,
        // and not one resolver line in between.
        //
        // So everything from here to the discard runs either way, and only the
        // seek-and-prepare at the end is skipped.
        //
        // A verdict counts as exhausting the attempts immediately. The resolver
        // only throws [StreamResolver.PermanentlyUnplayableException] once it has
        // established that no client, no session and no extraction can serve the
        // track, so the two further attempts this would otherwise spend are two
        // more full walks for an answer already in hand — and in the report they
        // were exactly that, at roughly seventeen youtubei requests each.
        val verdict = permanentReason(error)
        val givingUp = verdict != null || attempts > MAX_RECOVERIES
        if (verdict != null) {
            TrackLog.w("BitChord", "$mediaId cannot be played: $verdict", about = mediaId)
        } else if (givingUp) {
            TrackLog.w("BitChord", "$mediaId has failed $attempts times; leaving it alone", about = mediaId)
        }
        // The upgraded rendition goes with the cache entry it lived in, so the
        // marker on the URI would otherwise point at nothing.
        QualityUpgrade.forget(mediaId)
        // A track that died on an upgraded URI died on the *upgrade*, and it
        // must not be offered that same swap again the moment it recovers.
        // Left unrecorded, the second look starts over on the retry, finds the
        // same FLAC at the same dead URL, cuts the audio for it again, and
        // fails again — twice more before [MAX_RECOVERIES] stops it. Observed
        // on a Tidal URL answering ERROR_CODE_IO_BAD_HTTP_STATUS.
        if (uri?.let(QualityUpgrade::cacheTag) != null) {
            QualityUpgrade.refuseUpgrades(mediaId)
        }
        // Whatever failed took its claimed format with it. The stream that
        // recovers is a different one and has not promised anything yet, so
        // leaving the old claim behind is how a badge earned by a FLAC ends up
        // sitting over the Opus that replaced it.
        NerdStats.clearDeclared(mediaId)
        // A track that died on a substituted stream died on the *substitution*,
        // and the retry must not be free to make the same one again. The lookup
        // behind it is deterministic and, by the second attempt, cached — so it
        // wins the race against YouTube by the same margin it won it the first
        // time and hands back the identical dead URL, until [MAX_RECOVERIES]
        // stops trying. That is a track that never plays at all while a working
        // YouTube URL sits in [StreamResolver]'s cache, resolved and unused.
        // The same reasoning as [QualityUpgrade.refuseUpgrades] above, for the
        // substitution that happens *before* the first note rather than after.
        // Read before the forget below, which is what clears the evidence.
        uri?.getQueryParameter("v")?.takeIf(StreamChoice::isSubstitute)?.let { videoId ->
            StreamChoice.refuseSubstitutes(videoId)
            TrackLog.w(
                "BitChord",
                "$videoId broke on a substituted stream; YouTube serves it for now",
                about = mediaId,
            )
            // And no swapping back to it mid-song either: the second look asks
            // the same catalogues the same question and would cut the audio that
            // just recovered to land on the same refusal.
            QualityUpgrade.refuseUpgrades(videoId)
        }
        uri?.getQueryParameter("v")?.let(StreamChoice::forget)
        scope.launch(TrackLog.about(mediaId)) {
            // Long enough for the released source to let go of the cache keys
            // about to be removed, short enough to read as a stutter.
            delay(RECOVERY_DELAY_MS)
            uri?.let { withContext(Dispatchers.IO) { AudioCache.discard(it) } }
            // The bytes go even when nothing is going to be prepared after
            // them. A half-filled entry whose owner has just been forgotten is
            // the seam this file's [StreamChoice] note is about: the next play
            // resolves freely, lands on a different source, and streams it into
            // the middle of the last one. Releasing the choice without dropping
            // the bytes would trade one stuck track for a corrupt one.
            if (givingUp) {
                // A track nobody can play must not leave the player parked in
                // IDLE on it. Nothing else in this service calls prepare() again,
                // so before this the queue simply stopped: the notification kept
                // showing the song, the play button kept doing nothing, and from
                // the outside that is indistinguishable from a hung app — which
                // is what the report describes and what "it was stuck on my
                // phone too" means. Moving on is the only honest answer, and it
                // is only safe to do for a verdict: a track that merely ran out
                // of attempts may still be playable when the listener presses
                // play, and skipping past it would silently eat it.
                if (verdict != null) withContext(Dispatchers.Main) { skipPastUnplayable(mediaId, verdict) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                val player = this@PlaybackService.player ?: return@withContext
                if (player.currentMediaItem?.mediaId != mediaId) return@withContext
                TrackLog.d("BitChord", "retrying $mediaId from ${position}ms")
                // Claimed before the seek, so the transition it may fire is
                // recognised as this retry rather than read as a fresh start and
                // handed a fresh attempt budget.
                retryingMediaId = mediaId
                player.seekTo(player.currentMediaItemIndex, position)
                player.prepare()
            }
        }
    }

    /**
     * Swaps a track whose downloaded file has gone missing back onto a stream,
     * in place and at the same position.
     *
     * The file was downloaded and then deleted from under the app — a file
     * manager, a cleaner, a wiped SD card — leaving [Downloads]' record pointing
     * at nothing. [Song.toMediaItem] checks that record before it builds an
     * item, but only at build time: an item already sitting in the timeline was
     * built when the file was still there can retain that stale URI while it
     * waits in the live queue. This is the other end of that, and the only one
     * that can see the file is gone rather than guess.
     *
     * The record goes first, then the item is rebuilt from its own metadata with
     * the local uri stripped, which sends [Song.toMediaItem] down its streaming
     * branch. Position is kept, so this reads as the hiccup [recoverFrom] is
     * written around rather than the song starting over.
     *
     * @return false when this is not that situation and the caller should carry
     *   on with its ordinary stream recovery — including the case where stripping
     *   the local uri changes nothing, which is a device-library track whose
     *   mediaId *is* the missing file and for which there is no stream to fall
     *   back to. Returning true there would be a swap that fixes nothing, on a
     *   loop.
     */
    private fun restreamMissingLocalFile(
        player: ExoPlayer,
        item: MediaItem,
        uri: Uri?,
        position: Long,
    ): Boolean {
        val scheme = uri?.scheme
        if (scheme != "file" && scheme != "content") return false
        val mediaId = item.mediaId

        Downloads.forgetMissing(mediaId)
        val restreamed = item.toSong().copy(localUri = null, localPath = null).toMediaItem()
        if (restreamed.localConfiguration?.uri == uri) {
            TrackLog.w(
                "BitChord",
                "$mediaId is a local file that is gone and has no stream behind it",
                about = mediaId,
            )
            return false
        }

        TrackLog.w(
            "BitChord",
            "$mediaId was downloaded but $uri is gone; streaming it instead",
            about = mediaId,
        )
        // Not claimed as [retryingMediaId], unlike the seek-and-prepare retry
        // below: that flag exists to stop a retry against the *same* stream
        // refilling its own attempt budget, and this is a different source
        // entirely. Letting the transition clear the count is the right answer
        // here — a stream that has never been tried deserves the full budget.
        recoveries.remove(mediaId)
        player.replaceMediaItem(player.currentMediaItemIndex, restreamed)
        player.seekTo(player.currentMediaItemIndex, position)
        player.prepare()
        return true
    }

    /**
     * Leave a track the resolver has ruled out and carry on down the queue.
     *
     * The item is left in place rather than removed: the listener queued it, and
     * the reason it cannot be played is usually temporary in a way this service
     * cannot see the end of — signing in clears an age gate, and travelling
     * clears a region block. Removing it would quietly rewrite a queue on the
     * strength of a ten-minute verdict.
     *
     * With nothing after it there is nowhere to go, and stopping is then the
     * correct end state rather than a failure to recover: the error stays on the
     * player, which is what puts a message in front of the listener — see
     * `rememberPlayerState` in
     * [PlayerConnection][com.music.bitchord.playback.PlayerConnection].
     */
    private fun skipPastUnplayable(mediaId: String, reason: String) {
        val exoPlayer = player ?: return
        if (exoPlayer.currentMediaItem?.mediaId != mediaId) return
        if (!exoPlayer.hasNextMediaItem()) {
            TrackLog.w("BitChord", "$reason — and nothing after it in the queue", about = mediaId)
            return
        }
        TrackLog.w("BitChord", "$reason — skipping to the next track", about = mediaId)
        exoPlayer.seekToNextMediaItem()
        // No play() here: an error does not clear playWhenReady, so prepare()
        // resumes exactly as far as the listener had asked for. Calling play()
        // would un-pause a queue they had paused.
        exoPlayer.prepare()
    }

    /**
     * Why [error] means "never", or null if it only means "not just now".
     *
     * Unwrapped by hand because the classification is the resolver's and the
     * exception has been through Media3's loader by the time it arrives:
     * `ExoPlaybackException` wrapping `Loader.UnexpectedLoaderException` wrapping
     * what the resolver actually threw. A shallow `is` check sees only the
     * outermost of those three.
     */
    private fun permanentReason(error: Throwable?): String? {
        var cause = error
        var depth = 0
        while (cause != null && depth++ < PERMANENT_CAUSE_DEPTH) {
            if (cause is StreamResolver.PermanentlyUnplayableException) {
                return cause.message ?: "This track cannot be played"
            }
            cause = cause.cause?.takeIf { it !== cause }
        }
        return null
    }

    /**
     * The track whose item this service is about to replace under it, so that
     * [Player.Listener.onMediaItemTransition] can tell a quality swap from the
     * queue actually moving on. Cleared by the transition it describes.
     */
    private var swappingMediaId: String? = null

    /**
     * When the audio was last cut for a quality swap, so the analytics listener
     * can say how long it stayed cut. Null except across a swap.
     */
    private var swapCutAt: Long? = null

    /**
     * The track [ExoPlayer.getAudioFormat] is currently describing.
     *
     * `audioFormat` is a property of the *renderer*, not of the queue item, and
     * it keeps naming the outgoing track's codec until the renderer has read a
     * sample of the incoming one. Anything that asks "what is playing right
     * now" in the moments after a transition is therefore told about the track
     * before it, and [adoptCachedTrack] is asked exactly there — a queue
     * advance is one of the places [lookForBetterCopy] runs from.
     *
     * Observed: 'Harleys In Hawaii' came up fifteen milliseconds after the
     * queue moved onto it, twenty seconds after the previous track had been
     * upgraded to FLAC. The renderer still said `audio/flac`, so a WebM Opus
     * stream — verified by the `1A 45 DF A3` on its cache entry — was written
     * off as "already lossless from cache" and, because that verdict is
     * recorded once and for good, never offered an upgrade again for the rest
     * of the session.
     */
    private var audioFormatFor: String? = null

    /**
     * Starts the second look for the playing track, if it settled for less
     * than was asked for — see [QualityUpgrade].
     *
     * Runs at most once per track: [QualityUpgrade.lookAgain] drops the track
     * from its pending set whatever the answer, so the repeated calls this
     * gets cost nothing after the first. It needs to be called from several
     * places for that reason — a track becomes eligible at a different moment
     * depending on how it was reached. Called only from
     * `onIsPlayingChanged`, it fired for the first track of a session and for
     * nothing after it: the queue advancing while already playing is not a
     * change in `isPlaying`, so every track but the first kept a lookup that
     * had already found its FLAC and was never asked for it.
     *
     * Eligibility has two sources, because being resolved and being played are
     * not the same event. A track the resolver saw is already marked; a track
     * served from the disk cache was never resolved at all and is judged here
     * instead — see [adoptCachedTrack] and [QualityUpgrade.adoptUnresolved].
     */
    private fun lookForBetterCopy(player: ExoPlayer) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val uri = item.localConfiguration?.uri
        val alreadyPending = QualityUpgrade.isPending(mediaId)
        val shelved = QualityUpgrade.shelvedFor(mediaId)
        if (shelved == null && !alreadyPending && !QualityUpgrade.couldStillUpgrade(mediaId, uri)) return
        if (upgradeJob?.isActive == true) {
            // Already hunting for this track. One left over from a track the
            // queue has moved past is a different matter: it can only come
            // back with an answer about a song nobody is listening to, and
            // until it does it holds the slot the current track needs.
            if (upgradeFor == mediaId) return
            upgradeJob?.cancel()
        }
        upgradeFor = mediaId
        if (alreadyPending) {
            TrackLog.d("BitChord", "looking again for a better copy of $mediaId", about = mediaId)
        }
        upgradeJob = scope.launch(TrackLog.about(mediaId)) {
            // A previous visit to this track already did all the expensive
            // parts and lost the swap to a skip. Nothing about the answer has
            // gone stale — the stream is still parked and its bytes are still
            // on disk — so this goes straight to the swap and skips the ten
            // seconds of catalogue searching it would otherwise repeat.
            if (shelved != null) {
                TrackLog.d("BitChord", "re-offering the upgrade already proved for $mediaId")
                NerdStats.onLosslessRaceStart(mediaId)
                try {
                    swapIn(mediaId, shelved)
                } finally {
                    NerdStats.onLosslessRaceEnd(mediaId)
                }
                return@launch
            }
            // The runtime the decoder reports is the only measured evidence
            // about what is playing, and everything downstream weighs
            // candidates against it — so it is worth a short wait rather than
            // a null. It is genuinely not known yet at some of the moments
            // this is called from: a queue advance runs its transition before
            // the item it moved onto has finished preparing.
            val playingSeconds = withTimeoutOrNull(DURATION_SETTLE_MS) {
                while (true) {
                    val ms = withContext(Dispatchers.Main) {
                        this@PlaybackService.player
                            ?.takeIf { it.currentMediaItem?.mediaId == mediaId }
                            ?.duration
                            ?: 0L
                    }
                    if (ms > 0) return@withTimeoutOrNull (ms / 1000).toInt()
                    delay(UPGRADE_PROVE_STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") null
            }
            // Re-asked rather than carried down from above, because the wait
            // is long enough for the answer to have changed: the resolver runs
            // on the loader thread and marks a track pending as it opens the
            // source, which for a track being fetched is precisely what has to
            // happen before the decoder can report the runtime waited for just
            // above. Reading the flag from before the wait meant a freshly
            // resolved track arrived here looking un-resolved, was handed to
            // the cached-track path, was refused by it for being pending, and
            // lost its upgrade until the next progress sample came round.
            if (!QualityUpgrade.isPending(mediaId) &&
                (uri == null || !adoptCachedTrack(mediaId, uri, playingSeconds))
            ) {
                return@launch
            }
            val better = withContext(Dispatchers.IO) {
                QualityUpgrade.lookAgain(mediaId, playingSeconds)
            } ?: return@launch
            try {
                swapIn(mediaId, better)
            } finally {
                // The badge comes down when the upgrade is done, not when the
                // search that found it was — including the deliberate wait in
                // [swapIn] before the audio is allowed to be cut. See
                // [QualityUpgrade.lookAgain]. In `finally` because a queue
                // that moves on cancels this job, and a cancelled swap has to
                // put the badge out as surely as a completed one.
                NerdStats.onLosslessRaceEnd(mediaId)
            }
        }
    }

    /**
     * Decides whether a track nothing resolved is worth a second look, now that
     * the decoder has settled enough to say what it is playing.
     *
     * Two questions that need the player rather than the queue entry:
     *
     *  - **What codec is actually coming out.** A cache entry can already hold
     *    the FLAC a previous session upgraded to, and hunting a lossless copy
     *    of a track that is already lossless buys a break in the audio for
     *    nothing. An unknown codec is not read as "lossy": it means the
     *    renderer has not been configured yet, so the track is left un-adopted
     *    and the progress sampler asks again a few seconds later. A codec the
     *    renderer is reporting for *some other track* gets the same treatment,
     *    and has to, because it is indistinguishable from an answer — see
     *    [audioFormatFor] for what it cost to read one on trust.
     *  - **Whether the listener owns the file.** A downloaded track resolves to
     *    its own copy on disk — see the resolving data source above, which
     *    answers it before the module race is ever reached, so a download has
     *    never been a candidate for substitution. Reproduced here because this
     *    path skips that resolver entirely; without it the second look would
     *    spend data replacing a file the user deliberately saved.
     *
     * @param durationSec the runtime the decoder reports, waited for by the
     *   caller — needed here to turn the size of the cache entry into a
     *   bitrate. See [cachedFloor].
     */
    private suspend fun adoptCachedTrack(mediaId: String, uri: Uri, durationSec: Int?): Boolean {
        val format = withContext(Dispatchers.Main) {
            player
                ?.takeIf { it.currentMediaItem?.mediaId == mediaId && audioFormatFor == mediaId }
                ?.audioFormat
        } ?: return false
        val mime = format.sampleMimeType ?: return false
        val videoId = uri.getQueryParameter("v") ?: return false
        val downloaded = com.music.bitchord.download.Downloads.savedUri(this, videoId) != null
        if (downloaded) return false
        return QualityUpgrade.adoptUnresolved(
            mediaId = mediaId,
            uri = uri,
            target = SourceResolver.targetIn(uri),
            playingMime = mime,
            playing = withContext(Dispatchers.IO) { cachedFloor(uri, format, durationSec) },
        )
    }

    /**
     * How good the bytes already on disk are, in the only terms a track nothing
     * resolved can be measured in.
     *
     * Two measurements, in order of directness:
     *
     *  - **What the decoder says.** `Format.bitrate` is populated for the
     *    containers that carry the field, which for what BitChord plays means
     *    MP4/AAC — the 320kbps copy a module served last session reports itself
     *    exactly.
     *  - **What the cache entry weighs.** Opus in WebM, which is what YouTube
     *    serves and so what most base entries hold, states no bitrate at all;
     *    but the rendition's full length is recorded in the cache index, and
     *    bytes over seconds *is* a bitrate. Slightly high, because container
     *    overhead counts toward the byte total and not toward the audio — which
     *    errs toward leaving the track alone, the right direction for a figure
     *    that decides whether to cut into playing audio.
     *
     * Null only when neither is available: an entry whose content length was
     * never recorded, or a runtime the decoder never reported. That is the old
     * behaviour of this path, and it is now the exception rather than the rule.
     *
     * The codec is deliberately not filled in. [StreamFormat.isLossless] reads
     * it, and a name carried over from the decoder's mime type would have to be
     * translated to be recognised — where being wrong means claiming a cached
     * stream is already lossless and abandoning the upgrade. Only the bitrate
     * is wanted here; [QualityUpgrade.adoptUnresolved] settles the lossless
     * question separately, from the mime type itself.
     */
    private fun cachedFloor(uri: Uri, format: Format, durationSec: Int?): StreamFormat? {
        format.bitrate.takeIf { it != Format.NO_VALUE && it > 0 }?.let {
            return StreamFormat(kbps = it / 1000)
        }
        val seconds = durationSec?.takeIf { it > 0 } ?: return null
        val bytes = AudioCache.contentLengthOf(uri).takeIf { it > 0 } ?: return null
        return StreamFormat(kbps = (bytes * 8 / seconds / 1000).toInt())
    }

    /** Where the playing track stands, read off the player in one hop. */
    private class SwapPoint(
        val item: MediaItem,
        val uri: String,
        val position: Long,
        val duration: Long,
    )

    /**
     * Replaces the playing track's audio with [stream], keeping the position.
     *
     * The break this causes is the whole cost of the feature, so the guards
     * are worth more than the swap is:
     *
     *  - The track must still be the one the search was started for. A skip
     *    during the lookup makes the answer worthless, not merely late.
     *  - There has to be enough of it left to be worth interrupting. Cutting
     *    the last few seconds of a song to improve the last few seconds of a
     *    song is a straight loss.
     *  - **The replacement has to be ready before anything is taken away.**
     *    See [auditionUpgrade]; this is what the break costs, so it is what
     *    the cut is bought against.
     *
     * The mechanism is [MediaItem.buildUpon] with a marked URI rather than a
     * new item: Media3 only rebuilds a media source when the replacement's
     * playback URI differs, so an item rebuilt identically would be accepted
     * and quietly keep playing the old stream.
     */
    private suspend fun swapIn(mediaId: String, stream: SourceStream) {
        val at = withContext(Dispatchers.Main) { swapPointFor(mediaId) } ?: return
        if (at.duration > 0 && at.duration - at.position < UPGRADE_MIN_REMAINING_MS) {
            TrackLog.d("BitChord", "upgrade abandoned: only ${at.duration - at.position}ms of the track left")
            return
        }

        val upgradedUri = QualityUpgrade.upgradedUri(at.uri)
        // Whether the rendition entry already holds *this* stream's bytes,
        // asked before [force] overwrites the record of what filled it. True
        // only for a shelved upgrade being re-offered, where throwing the entry
        // away would mean paying for the same megabytes twice — and where
        // keeping it is safe for the one reason the discard exists: the file
        // under that key came from this very URL.
        val alreadyFilled = QualityUpgrade.forcedStream(Uri.parse(upgradedUri))?.url == stream.url
        // Parked before the audition rather than at the swap: the silent player
        // reaches its bytes through the same resolving data source the real one
        // does, and that is where a marked URI is turned back into a stream.
        QualityUpgrade.force(mediaId, stream)
        val warmedThrough = auditionUpgrade(mediaId, at, upgradedUri, stream, alreadyFilled)
        if (warmedThrough == null) {
            // Nothing was cut, so there is nothing to put back: the listener
            // keeps the stream they already had and never learns this
            // happened. Which is the point — this is the failure that used to
            // arrive as a break in the audio followed by the same lossy stream
            // returning a few seconds later. Dropping the parked stream stops
            // [QualityUpgrade.forcedStream] serving a URL that has just failed
            // to prove itself.
            QualityUpgrade.forget(mediaId)
            withContext(Dispatchers.IO) { AudioCache.discardRendition(Uri.parse(upgradedUri)) }
            return
        }

        // There used to be an unconditional five-second hold here, keyed off the
        // track's own position, so that an upgrade arriving with the first note
        // could not cut the song a millisecond in. Its own reasoning said it was
        // "almost always already past by now", and that turned out to be the
        // whole story: by the time this line is reached the search, the stream
        // lookup and the audition have all run, and the audition alone spends
        // seconds on the network. So the guard was not usually deciding to wait
        // — it was adding its five seconds to whatever the swap had already
        // cost, on exactly the tracks that had been quickest to find a better
        // copy. Removed rather than shortened: it is the crossfade grace below
        // that protects the case an upgrade can genuinely spoil, and it does so
        // by asking whether a transition actually happened rather than assuming
        // one might have.
        //
        // Never cut into a crossfade in flight. `replaceMediaItem` tears the
        // session player's source down and rebuilds it — CrossfadeController
        // is either syncing its tail player's position against that same
        // source (arming), riding a ~90ms handoff between the two (lapping),
        // or ramping volume off the incoming track's own position (fading),
        // and all three read a session-player discontinuity as either an
        // unrecognised seek (bail, with an audible ramp-out) or a progress
        // calculation reset to whatever position the new source opens at.
        // Either way the blend breaks rather than merely waits.
        //
        // Bounded so a stuck flag can never leave the upgrade waiting forever;
        // past the timeout this falls through to the same check made again,
        // authoritatively, below — so an unusually long-running crossfade
        // still gets one more look rather than being forced through.
        //
        // This loop is only the coarse wait. [crossfade] is read again inside
        // the `withContext` below with no suspension between that read and
        // `replaceMediaItem` — both run on the same single-threaded Main
        // dispatcher [scope] does — so that second check is the one this
        // logic actually depends on for correctness, not this one.
        var waitedForCrossfade = 0L
        while (withContext(Dispatchers.Main) { crossfade?.isTransitioning() } == true &&
            waitedForCrossfade < UPGRADE_CROSSFADE_WAIT_TIMEOUT_MS
        ) {
            delay(UPGRADE_CROSSFADE_POLL_MS)
            waitedForCrossfade += UPGRADE_CROSSFADE_POLL_MS
        }

        // Nor the instant one ends. The loop above releases on the tick the
        // blend completes, and a swap made there lands its cut a few hundred
        // milliseconds after the incoming track finally stood alone: the
        // listener hears the mix land and the music stop, in that order, which
        // reads as the transition having broken rather than as a track quietly
        // getting better. This is the one delay on this path, and it is why the
        // blanket one above it could go: a hold measured from the track's own
        // start never covered this case anyway, since an Automix hands over at a
        // cue point that can be well past it.
        //
        // Keyed off when a transition last ended rather than off whether the
        // loop above actually waited, so the same grace covers an upgrade
        // shelved by the check below and re-offered moments later — the same
        // swap, the same few seconds after the same blend, arriving by a
        // different route. And nothing is held back on a track nowhere near a
        // transition: the reading is then already long past the grace.
        withContext(Dispatchers.Main) { crossfade?.msSinceTransition() }?.let { since ->
            if (since < UPGRADE_AFTER_CROSSFADE_MS) {
                val settle = UPGRADE_AFTER_CROSSFADE_MS - since
                TrackLog.d("BitChord", "upgrade for $mediaId holding ${settle}ms; a transition just ended")
                delay(settle)
            }
        }

        withContext(Dispatchers.Main) {
            val now = swapPointFor(mediaId)
            if (crossfade?.isTransitioning() == true) {
                // Caught right before the swap that would have broken it —
                // everything spent proving this stream is still worth keeping
                // for next time rather than throwing away, exactly like the
                // "queue moved on" case just below.
                QualityUpgrade.shelve(mediaId, stream)
                TrackLog.d("BitChord", "upgrade for $mediaId shelved: a crossfade was still running")
                return@withContext
            }
            if (now == null) {
                // The queue moved on between the upgrade being proved and the
                // swap being made — a skip, or a track that ran out. Everything
                // this cost is still in hand, so it goes on the shelf rather
                // than in the bin; see [QualityUpgrade.shelve]. Logged because
                // this used to be the one exit here that left no trace at all,
                // and from the logs "found a FLAC, cached it, swapped nothing"
                // was indistinguishable from never having looked.
                QualityUpgrade.shelve(mediaId, stream)
                TrackLog.d("BitChord", "upgrade for $mediaId proved but the queue moved on; shelved")
                return@withContext
            }
            val player = player ?: return@withContext
            if (now.duration > 0 && now.duration - now.position < UPGRADE_MIN_REMAINING_MS) {
                TrackLog.d("BitChord", "upgrade abandoned: only ${now.duration - now.position}ms of the track left")
                QualityUpgrade.forget(mediaId)
                return@withContext
            }
            // The parked stream can be taken away underneath a swap in flight:
            // a playback failure on the *old* stream runs [recoverFrom], which
            // forgets the pending upgrade along with everything else it clears.
            // Swapping onto a marked URI with nothing parked behind it would
            // send the resolver off to find a stream of its own and write it
            // into the rendition entry the audition just filled — two files,
            // one key, which is the corruption the audition exists to avoid.
            if (QualityUpgrade.forcedStream(Uri.parse(upgradedUri)) == null) {
                TrackLog.d("BitChord", "upgrade abandoned: its stream was dropped while it was being proved")
                return@withContext
            }
            // Not fatal, just slower than intended, and worth being able to see
            // in a log: the audition buffers ahead of a moving target and can
            // only lose that race on a connection that is barely keeping up.
            if (now.position > warmedThrough) {
                TrackLog.d(
                    "BitChord",
                    "upgrade landing at ${now.position}ms, past the ${warmedThrough}ms warmed for it",
                )
            }

            // Read before the swap overwrites it — see [watchUpgrade]'s
            // NerdStats cleanup for why the pre-upgrade claim has to be
            // captured here rather than looked up again on revert.
            val previousFormat = NerdStats.declaredFormat(mediaId)
            swappingMediaId = mediaId
            swapCutAt = SystemClock.elapsedRealtime()
            val upgradedMetadata = now.item.mediaMetadata.buildUpon()
                .setExtras(Bundle(now.item.mediaMetadata.extras ?: Bundle()).apply {
                    putBoolean(EXTRA_QUALITY_UPGRADED, true)
                })
                .build()
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                now.item.buildUpon()
                    .setUri(upgradedUri)
                    .setMediaMetadata(upgradedMetadata)
                    .withResolvedStreamType(stream.url)
                    .build(),
            )
            player.seekTo(player.currentMediaItemIndex, now.position)
            player.prepare()
            QualityUpgrade.unshelve(mediaId)
            TrackLog.d("BitChord", "upgraded to ${stream.format.summary} at ${now.position}ms")
            watchUpgrade(mediaId, now.uri, now.position, now.duration, previousFormat)
            if (QualityUpgrade.continueAfterLossySwap(mediaId)) {
                // The immediate JioSaavn improvement stays audible while a
                // slower lossless source is checked against its higher-quality
                // floor. Wait for this pass to release `upgradeJob`; otherwise
                // the second pass would see the first one as still active and
                // return without starting its lookup.
                val firstPass = upgradeJob
                scope.launch {
                    firstPass?.join()
                    lookForBetterCopy(player)
                }
            }
            // The opening again, this time sized for Automix rather than for
            // a container header.
            //
            // An upgraded rendition is only ever fetched from the swap point
            // onward, so its first seconds are the one region nothing downloads
            // on its own — [UPGRADE_HEADER_BYTES] covers the header and stops
            // well short of enough *audio* to measure. A megabyte of lossless is
            // four seconds, against the twelve the analyzer needs, so a track
            // that upgrades early could never be analysed from any rendition:
            // the lossless copy had no audio at its head and the copy it
            // replaced was discarded.
            //
            // After the swap and off the main thread, because nothing waits on
            // it — the upgrade is already audible and this only decides whether
            // the *next* transition can be a real mix.
            launch(Dispatchers.IO) {
                AudioCache.warmRange(Uri.parse(upgradedUri), 0, ANALYSIS_HEAD_BYTES)
            }
        }
    }

    /** Main thread. Null unless [mediaId] is still current and has another upgrade slot. */
    private fun swapPointFor(mediaId: String): SwapPoint? {
        val player = player ?: return null
        val item = player.currentMediaItem ?: return null
        if (item.mediaId != mediaId) return null
        val uri = item.localConfiguration?.uri?.toString() ?: return null
        // One mid-track lossy improvement (typically Opus → JioSaavn) must not
        // prevent the requested lossless copy from replacing it. Two marked
        // URIs get distinct cache entries through [QualityUpgrade.upgradedUri].
        if (uri.contains("${QualityUpgrade.MARKER}=hifi-")) return null
        return SwapPoint(item, uri, player.currentPosition, player.duration)
    }

    /**
     * Proves the upgraded stream on a second, silent player before a note of
     * the one playing is touched.
     *
     * This is the difference between a swap that is heard and one that is not.
     * `replaceMediaItem` + `prepare` tears the old source down first and builds
     * the new one from nothing: a connection to the CDN, a container header, a
     * range request for wherever the seek lands, a decoder configured, and only
     * then audio. Measured on this device that ran to about a second of silence
     * every time, and the whole of it was spent doing work that had no reason to
     * wait for the music to stop.
     *
     * So it doesn't. A throwaway player opens the same upgraded URI, seeked to
     * where the listener is, and fills the *same on-disk cache entry* the real
     * player will read from — [QualityUpgrade.MARKER] keys that entry apart from
     * the rendition being replaced, which is what makes this safe. When the swap
     * finally happens the bytes are already local, the container is already
     * known to parse, and what is left is a decoder init. The old stream plays
     * through all of it.
     *
     * The second thing it buys is that a failed upgrade stops costing anything.
     * Every way this can go wrong — a dead URL, a 403, a truncated body, a
     * catalogue that matched the wrong cut of the song, a source that promised
     * FLAC and serves Opus — now happens to a player nobody is listening to, and
     * the answer is simply that no swap occurs. Before, all of them were
     * discovered *after* the audio had been cut, and cost a break, several
     * seconds of silence in `STATE_BUFFERING`, and a second break putting the
     * old stream back. See [watchUpgrade], which is now the backstop for this
     * rather than the first line of defence.
     *
     * Silent by construction rather than by volume: with `playWhenReady` false
     * the renderers are enabled and decode — which is all the proof needed —
     * but nothing is started and no second `AudioTrack` is ever opened. It takes
     * no audio focus and backs no session, so nothing else in the app can see it.
     *
     * @return how far into the track the upgrade is buffered and ready, or null
     *   if it never got there.
     */
    private suspend fun auditionUpgrade(
        mediaId: String,
        at: SwapPoint,
        upgradedUri: String,
        stream: SourceStream,
        renditionAlreadyFilled: Boolean,
    ): Long? {
        QualityUpgrade.beginAudition(mediaId)
        val startedAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            // A clean entry first, because `#hifi` names a *slot* and not a
            // file. Every audition is a fresh candidate — a different catalogue,
            // a different master, a different length — and anything left under
            // that key by an earlier attempt at the same track belongs to a
            // different one of those. Media3 will happily read the two as one
            // stream, which is how a whole contiguous 32MB entry ended up
            // decoding to this:
            //
            // ```
            //   Target buffer size reached with less than 500ms of buffered media
            //   IllegalStateException: Playback stuck buffering and not loading
            // ```
            //
            // — a spliced file that cost the swap, the recovery, and seven
            // seconds of silence. The cost of being wrong the other way is one
            // re-download of a track being upgraded twice in a session, which
            // is why a re-offered upgrade is exempt: there the bytes under the
            // key are known to have come from the URL about to be used again.
            if (!renditionAlreadyFilled) AudioCache.discardRendition(Uri.parse(upgradedUri))
            // Then the opening, on its own, because the audition will not cache
            // it: a progressive source parses the container from byte zero and
            // then *seeks away*, leaving behind only the handful of bytes it
            // read before jumping. The real player has to parse the same header
            // from scratch after the swap, and it was reaching the network to do
            // it — the one read nothing can start without. Ahead of the audition
            // rather than beside it, since Media3 locks a cache entry to a
            // single writer.
            AudioCache.warmRange(Uri.parse(upgradedUri), 0, UPGRADE_HEADER_BYTES)
        }
        val audition = withContext(Dispatchers.Main) {
            buildAuditionPlayer().apply {
                setMediaItem(
                    at.item.buildUpon()
                        .setUri(upgradedUri)
                        .withResolvedStreamType(stream.url)
                        .build(),
                )
                seekTo(at.position)
                prepare()
            }
        }
        val warmedThrough: Long?
        try {
            warmedThrough = withTimeoutOrNull(UPGRADE_AUDITION_MS) {
                while (true) {
                    val verdict = withContext(Dispatchers.Main) {
                        auditionVerdict(audition, at.duration, stream)
                    }
                    when (verdict) {
                        is Audition.Ready -> return@withTimeoutOrNull verdict.bufferedTo
                        is Audition.Rejected -> {
                            TrackLog.w("BitChord", "upgrade dropped before it was heard: ${verdict.why}")
                            return@withTimeoutOrNull null
                        }
                        Audition.Waiting -> delay(UPGRADE_PROVE_STEP_MS)
                    }
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        } finally {
            // Not optional and not cancellable: a queue that moves on cancels
            // this job, and a player left behind holds an audio decoder and a
            // write lock on a cache entry for the rest of the session.
            withContext(NonCancellable + Dispatchers.Main) { audition.release() }
            QualityUpgrade.endAudition(mediaId)
        }
        val took = SystemClock.elapsedRealtime() - startedAt
        if (warmedThrough == null) {
            TrackLog.d("BitChord", "upgrade for $mediaId never proved itself in ${took}ms")
            return null
        }
        TrackLog.d(
            "BitChord",
            "upgrade to ${stream.format.summary} proved in ${took}ms, buffered through ${warmedThrough}ms",
        )
        // Media3 locks a cache entry to one writer, and the audition lets go of
        // its hold as the sources are released rather than as `release()`
        // returns. Swapping onto a key still held would have the real player
        // stream bytes it has already paid to cache, or block behind the lock —
        // the stall [AudioCache]'s key factory documents. Free to wait for: the
        // old stream is still playing.
        delay(AUDITION_RELEASE_MS)
        TrackLog.d("BitChord", AudioCache.cachedSummary(Uri.parse(upgradedUri)))
        return warmedThrough
    }

    /**
     * Tells Media3 the type of a stream found behind one of our virtual
     * `bitchord://` playback URIs.
     *
     * The resolver replaces that URI with the real URL only after
     * [DefaultMediaSourceFactory] has selected a source implementation. A
     * Tidal upgrade is an HLS manifest, but its virtual URI has no `.m3u8`
     * suffix, so the factory otherwise locks it into a progressive source and
     * fails to parse the manifest as audio bytes.
     */
    private fun MediaItem.Builder.withResolvedStreamType(streamUrl: String): MediaItem.Builder =
        if (streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            setMimeType(MimeTypes.APPLICATION_M3U8)
        } else {
            this
        }

    /** How an audition in progress is coming along — see [auditionUpgrade]. */
    private sealed interface Audition {
        data object Waiting : Audition

        /** Good, and buffered through this position in the track. */
        class Ready(val bufferedTo: Long) : Audition

        class Rejected(val why: String) : Audition
    }

    /**
     * Main thread. Everything that has to be true before the audio is cut,
     * asked of the audition player rather than of the catalogue that made the
     * claims.
     */
    private fun auditionVerdict(
        audition: ExoPlayer,
        previousDuration: Long,
        stream: SourceStream,
    ): Audition {
        audition.playerError?.let {
            return Audition.Rejected("${it.errorCodeName} opening ${stream.format.summary}")
        }
        // The failure a mid-track swap cannot survive, and the one that never
        // raises an error: a replacement that came up short does not fail, it
        // reaches the end of what it has and reports the track as over. Caught
        // here it costs nothing at all; caught after the swap it costs the
        // listener their song. See [watchUpgrade].
        if (audition.playbackState == Player.STATE_ENDED) {
            return Audition.Rejected("replacement ended immediately")
        }
        if (audition.playbackState != Player.STATE_READY) return Audition.Waiting
        val length = audition.duration
        if (length <= 0) return Audition.Waiting
        if (previousDuration > 0 && abs(length - previousDuration) > UPGRADE_LENGTH_SLACK_MS) {
            return Audition.Rejected("replacement is ${length}ms against ${previousDuration}ms")
        }
        // What the decoder was actually configured with, against what the
        // source said it was sending. The one failure mode a claim cannot
        // catch, because the claim is the thing that is wrong: a catalogue
        // advertising FLAC and serving a transcode buys a break in the audio
        // for no gain whatsoever.
        val mime = audition.audioFormat?.sampleMimeType
        if (mime != null && stream.format.isLossless == true && !NerdStats.isLosslessMime(mime)) {
            return Audition.Rejected("promised ${stream.format.summary}, decoder was handed $mime")
        }
        val buffered = audition.bufferedPosition
        // Aimed at where the listener will be, not where they were when this
        // started: the audition buffers ahead of a track that is still playing,
        // so the window it has to cover keeps moving. On any connection worth
        // upgrading over, buffering outruns playback and this converges in a
        // couple of seconds; on one where it doesn't, the swap would have
        // stalled anyway and the timeout is the right answer.
        val wantedThrough = (player?.currentPosition ?: 0L) + UPGRADE_PREBUFFER_MS
        // The only reason to settle for less: there is no more track to buffer.
        //
        // `isLoading` was tried here as a second escape — "the loader has
        // stopped of its own accord, so this is as good as it gets" — and it
        // was wrong every single time. [ChunkedDataSource] closes and reopens
        // the upstream every two megabytes, and `isLoading` goes false in the
        // gap between one range finishing and the next being asked for. A poll
        // landing in that gap read it as a full buffer, so every upgrade was
        // declared ready with roughly one chunk in hand and the swap then
        // landed seconds past the end of it, back on the network:
        //
        // ```
        //   upgrade to FLAC proved in 8701ms, buffered through 32496ms
        //   upgrade landing at 39889ms, past the 32496ms warmed for it
        // ```
        if (buffered >= wantedThrough || audition.bufferedPercentage >= 100) {
            return Audition.Ready(buffered)
        }
        return Audition.Waiting
    }

    /**
     * The throwaway player an upgrade is proved on.
     *
     * Shares the media source factory, and therefore the disk cache, with the
     * real one — which is the entire point: what this fetches is what the real
     * player reads a moment later. Deliberately plainer than the two players
     * [buildPlayer] builds, because nothing here is ever heard: stock
     * renderers, no spatial processor, no audio session, no focus, no session.
     *
     * The one thing it does not share is the load control. [farBufferingLoadControl]
     * stops at [FAR_BUFFER_BYTES], which is sized for a player that only has to
     * stay ahead of itself; this one has to buffer past a *moving* target —
     * [UPGRADE_PREBUFFER_MS] beyond wherever the listener has got to by the time
     * it finishes — and eight megabytes is under fifteen seconds of hi-res FLAC,
     * which the drift alone can eat. Held for seconds and then released with the
     * player.
     */
    private fun buildAuditionPlayer(): ExoPlayer = ExoPlayer.Builder(this)
        .setMediaSourceFactory(requireNotNull(mediaSourceFactory))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ AUDITION_BUFFER_MS,
                    /* maxBufferMs = */ AUDITION_BUFFER_MS,
                    /* bufferForPlaybackMs = */ START_PLAYBACK_MS,
                    /* bufferForPlaybackAfterRebufferMs = */ START_PLAYBACK_MS,
                )
                .setTargetBufferBytes(AUDITION_BUFFER_BYTES)
                .build(),
        )
        .build()
        .apply {
            playWhenReady = false
            volume = 0f
        }

    /**
     * Puts the old stream back if the upgraded one turns out to be broken.
     *
     * Learned the hard way: a swapped-in source that comes up short — a
     * truncated body, a CDN that answers a range request with something other
     * than the file — does not raise an error. It reports no duration, plays
     * for a few seconds and hits end-of-stream, and ExoPlayer does the correct
     * thing with a track that has ended, which is to advance to the next one.
     * The listener's song simply vanishes eight seconds in. That is a far worse
     * outcome than the lossy stream this was trying to improve on, so the new
     * source has to prove itself against the length the old one already knew
     * before it is allowed to keep the track.
     */
    private fun watchUpgrade(
        mediaId: String,
        previousUri: String,
        position: Long,
        previousDuration: Long,
        previousFormat: StreamFormat?,
    ) {
        if (previousDuration <= 0) return
        scope.launch(TrackLog.about(mediaId)) {
            val agreed = withTimeoutOrNull(UPGRADE_PROVE_MS) {
                while (true) {
                    val current = player?.takeIf { it.currentMediaItem?.mediaId == mediaId }
                        ?: return@withTimeoutOrNull false
                    val now = current.duration
                    if (now > 0) return@withTimeoutOrNull abs(now - previousDuration) <= UPGRADE_LENGTH_SLACK_MS
                    // The failure this whole check exists for, caught when it
                    // happens rather than at the ceiling: a replacement that
                    // came up short does not raise an error, it reaches the
                    // end of what it has and reports the track as over. That
                    // is a decisive no, and waiting out the rest of the window
                    // for it only delays the old stream coming back.
                    if (current.playbackState == Player.STATE_ENDED) return@withTimeoutOrNull false
                    delay(UPGRADE_PROVE_STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            if (agreed == true) return@launch
            val player = player ?: return@launch
            val item = player.currentMediaItem ?: return@launch
            if (item.mediaId != mediaId) return@launch
            // State and buffered position alongside the length: a replacement
            // that loaded and disagreed about the track looks identical here
            // to one that never loaded at all, and only the second is a fault
            // in the stream rather than a wrong match.
            TrackLog.w(
                "BitChord",
                "upgrade reverted: replacement reports ${player.duration}ms against " +
                    "${previousDuration}ms (state=${player.playbackState}, " +
                    "buffered=${player.bufferedPosition}ms)",
            )
            QualityUpgrade.forget(mediaId)
            // The FLAC/whatever claim recorded when the swap went out is no
            // longer what's playing — restore what was declared before it
            // (or clear it, if nothing was), so "stats for nerds" doesn't
            // keep calling the fallback lossless after the upgrade it
            // borrowed that claim from got reverted.
            if (previousFormat != null) {
                NerdStats.onSourceStream(mediaId, previousFormat)
            } else {
                NerdStats.clearDeclared(mediaId)
            }
            swappingMediaId = mediaId
            val abandoned = item.localConfiguration?.uri
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.buildUpon().setUri(previousUri).build(),
            )
            player.seekTo(player.currentMediaItemIndex, position)
            player.prepare()
            // Whatever the replacement wrote is a prefix of a file nothing will
            // ever finish, under a key the *next* upgrade of this track would
            // key to as well — see [AudioCache.discardRendition]. Off the main
            // thread and behind the same pause a recovery takes, because the
            // source just released still holds the entry for a moment.
            abandoned?.let {
                launch(Dispatchers.IO) {
                    delay(RECOVERY_DELAY_MS)
                    AudioCache.discardRendition(it)
                }
            }
        }
    }

    /** What [resolveWithModulePriority] settled on. */
    private sealed interface Resolved {
        data class Module(val stream: SourceStream) : Resolved
        data class YouTube(val url: String) : Resolved
    }

    /**
     * Resolves a YouTube-queued track by racing the higher-ranked modules
     * against YouTube itself, and handing whatever the modules are still doing
     * to [QualityUpgrade] if YouTube gets there first.
     *
     * Nobody gets a head start. An earlier version gave the modules six
     * seconds of silence to answer in before the fallback was even *asked*
     * for, on the reasoning that a module answering inside that window plays
     * with no seam in it. What that actually bought, on every track the
     * modules were slow on, was six seconds of nothing followed by a YouTube
     * client walk starting from cold — the wait and the seam, rather than one
     * or the other. Starting both at once removes the first of those: the
     * track begins as soon as *anything* can serve it.
     *
     * The speculative resolve this reinstates was dropped once before, for a
     * real reason — it is several round trips to `youtubei.googleapis.com`
     * competing for the same radio and connection pool as the lookup beside
     * it, and on a track the modules do have, that work is thrown away. What
     * changed is that it is no longer speculative: YouTube is now the expected
     * outcome for anything the modules don't answer quickly, so its walk is on
     * the critical path rather than hedging one. It is also coalesced and
     * cached — see [StreamResolver.resolve] — so even a discarded walk warms
     * the URL this track will want if the upgrade later falls through.
     *
     * A module that wins the race outright still wins the track, which is the
     * one thing worth keeping from the old head start: the lossless copy plays
     * from the first note and there is no swap at all. That is a narrower
     * window than it sounds, and deliberately so — read-ahead warms the
     * YouTube URL for the queue (see [AudioCache.prefetchQueue]), so on a
     * track that was read ahead the fallback answers in milliseconds and
     * almost always wins. The swap is the ordinary path now; playing from the
     * first note is the prize for a module quick enough to beat a cached URL.
     *
     * A lookup that loses is not cancelled. It is handed over still running,
     * because it is not wrong, only late, and the thing it is about to return
     * is exactly the stream that would have played seamlessly had it been
     * quicker. It finishes on its own time and the track swaps up to it
     * mid-song, which is the trade this whole path exists to make: a short
     * break in the audio, in exchange for the listener hearing something now
     * rather than waiting in silence for the good copy.
     */
    private suspend fun resolveWithModulePriority(
        videoId: String,
        target: TrackMatcher.Target,
    ): Resolved {
        // A substitute already broke this track once — see
        // [StreamChoice.refuseSubstitutes]. Racing the modules again would find
        // the same catalogue holding the same unplayable URL, so there is
        // nothing to race: YouTube is the one answer here that hasn't failed.
        // Skipped entirely rather than merely deprioritised, because a lookup
        // that loses is handed to [QualityUpgrade] rather than dropped, and
        // handing over the search that just cost three attempts would only
        // schedule a fourth.
        if (StreamChoice.substitutesRefused(videoId)) {
            return Resolved.YouTube(StreamResolver.resolve(videoId))
        }
        NerdStats.onLosslessRaceStart(videoId)
        // Both legs are parented to the service's scope rather than to the
        // caller, so neither inherits whose track this is — see
        // [TrackLog.about]. Without it the module walk and the client walk both
        // log from a scope that knows nothing, which is most of what a resolve
        // has to say about itself.
        val lookup = scope.async(Dispatchers.IO + TrackLog.about(videoId)) {
            withTimeoutOrNull(SUBSTITUTE_TIMEOUT_MS) { SourceResolver.substituteForYouTube(target) }
        }
        // Started now rather than after the modules have had their say, and
        // wrapped rather than thrown from: it is awaited only on the paths
        // that need it, and an async that fails without ever being awaited is
        // an unhandled exception in this service's scope.
        val fallback = scope.async(Dispatchers.IO + TrackLog.about(videoId)) {
            runCatching { StreamResolver.resolve(videoId) }
        }

        // First past the post. A null because [lookup] won is a module miss; a
        // null because [fallback] won means YouTube has a URL and the modules
        // are still looking — [lookup.isActive] below is what tells those
        // apart, which is the question the old head start answered by timing
        // out rather than by asking.
        val quick: SourceStream? = select {
            lookup.onAwait { it }
            // A fallback that finished without a URL has not won anything.
            //
            // This clause used to yield null unconditionally, which treats "the
            // YouTube walk is over" as "YouTube has a URL" — true only while
            // failing was the slow outcome. It no longer is: [StreamResolver]
            // now answers a known-unplayable track immediately, so the losing
            // leg crosses the line first and, before this, took the track down
            // with it while a module lookup that was about to succeed was still
            // running. Exactly the case in the report — an age-gated track that
            // YouTube would never serve and a catalogue that had it all along.
            fallback.onAwait { resolved -> if (resolved.isSuccess) null else lookup.await() }
        }

        if (quick != null) {
            // The modules got there first, so the YouTube walk is genuinely
            // spare work now. Cancelling drops only this service's wait on it;
            // [StreamResolver] parents the walk itself elsewhere and lets it
            // finish into its own cache.
            fallback.cancel()
            // Everything that was asked for, ahead of the fallback: the
            // ordinary good case, and the one with no seam in it.
            if (!quick.belowRequest) {
                NerdStats.onLosslessRaceEnd(videoId)
                return Resolved.Module(quick)
            }
            // Less than was asked for — but a lossy copy from a module still
            // beats going back to YouTube for one. Worth a second look, and
            // with this lookup already finished that look starts from scratch.
            val settled = QualityUpgrade.settledForLess(
                mediaId = videoId,
                target = target,
                playing = quick.format,
            )
            if (!settled) NerdStats.onLosslessRaceEnd(videoId)
            return Resolved.Module(quick)
        }

        val url = fallback.await().getOrThrow()
        // Marked pending only here, with the fallback's own bitrate in hand:
        // that figure is the answer to "better than what?" the second look
        // measures candidates against, and it isn't known until the client
        // walk has picked a format. A lookup still running is handed over to
        // be waited on rather than repeated; one that already finished with
        // nothing leaves the second look to find its own candidates.
        val pending = QualityUpgrade.settledForLess(
            mediaId = videoId,
            target = target,
            inFlight = lookup.takeIf { lookup.isActive },
            playing = NerdStats.pickedBitrateKbps(videoId)?.let { StreamFormat(kbps = it) },
        )
        if (!pending) NerdStats.onLosslessRaceEnd(videoId)
        return Resolved.YouTube(url)
    }

    /**
     * Publishes what the decoder is really being fed, for "stats for nerds".
     *
     * Bitrate is the awkward one: YouTube's WebM and MP4 containers carry no
     * bitrate field, so [Format.bitrate] arrives as `NO_VALUE` and the honest
     * figure is whatever named this stream instead. The source's own figure
     * comes ahead of YouTube's because a track can have both: one resolved
     * through YouTube and then upgraded to a module stream mid-song has a
     * stale 160 sitting in [NerdStats.pickedBitrateKbps] describing audio that
     * stopped playing several seconds ago. Anything still unknown is left null
     * for the UI to omit — better a shorter line than a made-up number.
     */
    private fun publishNerdStats() {
        val player = player ?: return
        val format = player.audioFormat
        val mediaId = player.currentMediaItem?.mediaId
        NerdStats.current.value = NerdStats.Snapshot(
            mimeType = format?.sampleMimeType,
            bitrateKbps = format?.bitrate?.takeIf { it != Format.NO_VALUE }?.div(1000)
                ?: NerdStats.declaredFormat(mediaId)?.kbps
                ?: NerdStats.pickedBitrateKbps(mediaId),
            sampleRateHz = format?.sampleRate?.takeIf { it != Format.NO_VALUE },
            channels = format?.channelCount?.takeIf { it != Format.NO_VALUE },
            bitDepth = format?.pcmEncoding?.let(::bitDepthOf),
            claimed = NerdStats.declaredFormat(mediaId),
        )
    }

    /** Serialize only the bounded window needed for a future cold-start resume. */
    private fun saveQueueSnapshot(player: ExoPlayer) {
        if (player.mediaItemCount == 0) {
            persistedQueueStart = 0
            LastPlayed.clear()
            return
        }
        val range = LastPlayed.window(player.mediaItemCount, player.currentMediaItemIndex)
        if (range.isEmpty()) return
        persistedQueueStart = range.first
        LastPlayed.saveQueue(
            songs = range.map { player.getMediaItemAt(it).toSong() },
            index = player.currentMediaItemIndex - range.first,
        )
        savePlaybackState(player)
    }

    /** Make the newly installed radio queue the durable cold-start boundary. */
    private fun saveQueueSnapshotImmediately(player: ExoPlayer) {
        if (player.mediaItemCount == 0) {
            persistedQueueStart = 0
            LastPlayed.clearImmediately()
            return
        }
        val range = LastPlayed.window(player.mediaItemCount, player.currentMediaItemIndex)
        persistedQueueStart = range.first
        LastPlayed.saveQueueImmediately(
            songs = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
        )
    }

    /** Drop every service-side reference that could resurrect the former queue. */
    private fun beginRadioQueue() {
        crossfade?.onSkipRequested()
        autoplayLoadJob?.cancel()
        autoplayLoadJob = null
        autoplaySeed = null
        repeatAllStash = emptyList()
        repeatAllStashSeed = null
        sessionSongHistory.clear()
        persistedQueueStart = 0
        LastPlayed.clearImmediately()
    }

    /** Persist index and position without touching or serializing queue contents. */
    private fun savePlaybackState(player: ExoPlayer) {
        if (player.mediaItemCount == 0) return
        LastPlayed.savePlaybackState(
            index = player.currentMediaItemIndex - persistedQueueStart,
            positionMs = player.currentPosition,
        )
    }

    /** Restore the bounded queue without preparing or resolving a stream. */
    private fun restoreLastQueue(player: ExoPlayer): Boolean {
        val last = LastPlayed.load() ?: return false
        persistedQueueStart = 0
        player.setMediaItems(last.songs.map { it.toMediaItem() }, last.index, last.positionMs)
        return true
    }

    /**
     * PCM sample depth the renderer settled on, in bits.
     *
     * This is the figure that decides whether a hi-res file is being played as
     * one. A 24-bit FLAC whose renderer reports 16-bit PCM has been truncated
     * somewhere between the decoder and the sink, and no other number on the
     * stats line would show it — the sample rate and the codec both survive
     * that unharmed.
     *
     * `ENCODING_INVALID` and `NO_VALUE` mean the renderer hasn't said, which is
     * common for pass-through and for formats decoded straight to float, and
     * is reported as unknown rather than as a failure.
     */
    private fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    /**
     * Tell the home-screen widgets what is playing.
     *
     * Kept out of [saveQueue] even though every caller does both: that one also
     * runs from the per-second sampler in [reportProgress], and pushing a bitmap
     * to the launcher once a second would be a lot of work to redraw the same
     * picture.
     *
     * [playing] overrides what the player reports, for the one caller that knows
     * better than it does — teardown, where the player is still nominally set to
     * play right up to the moment it is released.
     */
    private fun publishWidgetState(playing: Boolean? = null) {
        val exoPlayer = player ?: return
        val song = exoPlayer.currentMediaItem?.toSong() ?: return
        MediaWidgetSnapshot.save(
            this,
            MediaWidgetSnapshot(
                mediaId = song.videoId,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.thumbnailUrl,
                // playWhenReady, not isPlaying — see MediaWidgetSnapshot.isPlaying.
                isPlaying = playing ?: exoPlayer.playWhenReady,
                hasPrevious = exoPlayer.hasPreviousMediaItem(),
                hasNext = exoPlayer.hasNextMediaItem(),
            ),
        )
        MediaWidget.refresh(this)
    }

    /**
     * Hands the cache the queue ahead of the one playing: [AudioCache.QUEUE_DEPTH]
     * tracks is more than it does anything with, but it decides that, not this.
     */
    private fun prefetchAround(player: ExoPlayer) {
        val nextIndex = player.nextMediaItemIndex
        val upcoming = if (nextIndex != C.INDEX_UNSET) {
            val end = (nextIndex + AudioCache.QUEUE_DEPTH - 1).coerceAtMost(player.mediaItemCount - 1)
            (nextIndex..end).map { index ->
                val item = player.getMediaItemAt(index)
                // The title, artist and runtime the item was built with — see
                // [Song.toMediaItem]. Read here, on the player's own thread,
                // because read-ahead runs off the queue rather than off the
                // session and has no other way to reach the track's metadata.
                AudioCache.Upcoming(
                    mediaId = item.mediaId,
                    target = item.localConfiguration?.uri
                        ?.let(SourceResolver::targetIn)
                        ?: TrackMatcher.Target("", ""),
                )
            }
        } else {
            emptyList()
        }
        AudioCache.prefetchQueue(upcoming)
    }

    /**
     * Feeds played-seconds to [PlaybackTracker]. The tracker can't read the
     * player itself — ExoPlayer is confined to this thread — and a history
     * entry with no watchtime behind it barely registers as a listen, so the
     * sampling has to come from here.
     */
    private fun reportProgress() {
        scope.launch {
            while (isActive) {
                // Re-read every tick rather than captured once: the session
                // moves between two players, and a sampler pinned to the one
                // that happened to be first would go on reporting a player that
                // has been silent since the last crossfade.
                val player = this@PlaybackService.player
                if (player != null && player.isPlaying) {
                    lastPositionSeconds = player.currentPosition / 1000
                    player.currentMediaItem?.mediaId?.let {
                        PlaybackTracker.onProgress(it, lastPositionSeconds)
                    }
                    // The device's own listening record — see [ListeningRecorder],
                    // which counts wall-clock time between ticks rather than
                    // reading the position. This loop is the only place in the app
                    // that ticks exactly while audio is coming out, which is what
                    // makes it the right place to count from.
                    player.currentMediaItem?.toSong()?.let {
                        ListeningRecorder.onSample(it, player.duration)
                    }
                    // Only two primitive preference values. Queue JSON is
                    // written from onTimelineChanged, never from this loop.
                    savePlaybackState(player)
                    // The renderer can settle on its format a moment after the
                    // track change, which no callback of ours follows up on.
                    publishNerdStats()
                    // The backstop for the second look. The callbacks that
                    // start it fire at moments a track may not be resolved
                    // yet — the resolve happens on the loader thread when the
                    // source is opened, which for a track skipped to directly
                    // is after its own transition has been and gone. Cheap to
                    // repeat: it returns immediately unless the track is
                    // pending and nothing is already looking.
                    lookForBetterCopy(player)
                }
                delay(PROGRESS_SAMPLE_MS)
            }
        }
    }

    /**
     * Pause when the sleep timer runs out.
     *
     * `collectLatest` is what makes re-setting the timer work: the pending wait
     * is cancelled and restarted on the new deadline instead of both firing.
     */
    private fun watchSleepTimer() {
        scope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining > 0) delay(remaining)
                player?.pause()
                SleepTimer.cancel()
            }
        }
    }

    /**
     * Buffers as far ahead as a whole track rather than a rolling window.
     *
     * Media3's audio default stops loading at 13 buffer segments — around 830kB,
     * or 40 seconds of a 160kbps stream — and everything past that is fetched
     * only as playback consumes it. Since the data source writes through to
     * [AudioCache], how far ahead the player loads is also how much of the
     * track ends up on disk, and a seek past the buffered part is the one that
     * has to wait on the network.
     *
     * This matters for the track playback *starts* on. Everything after it is
     * on disk in full before it is reached, read ahead while it was still the
     * queued track — a first track has had no such chance.
     *
     * The byte ceiling is what governs; the duration is set past any song so
     * that it never becomes the binding constraint.
     *
     * Two further departures from the defaults, both about how long the
     * listener waits for sound:
     *
     *  - **Back buffer.** Media3 keeps nothing behind the playhead, so a seek
     *    *backwards* drops the buffer and reloads, while a seek forwards lands
     *    in samples already held. Half a minute of history closes that gap for
     *    the seek people actually make — nudging back a few seconds to catch a
     *    lyric — and it is deliberately no longer than that. The byte ceiling
     *    above counts *everything* the player holds, history included, so a
     *    back buffer wide enough to keep a whole track would spend the entire
     *    read-ahead budget on audio already heard: past the ceiling, loading
     *    stops, and since every second played moves a second from the front of
     *    the buffer to the back, the total never falls again and it never
     *    restarts. Read-ahead collapses and the track stalls every couple of
     *    seconds for the rest of its length. Seeking further back than this
     *    window is a disk read anyway, not a network one — [AudioCache] has
     *    written every byte already played.
     *  - **Thresholds to (re)start playback.** The defaults — 2.5s of audio
     *    before starting, 5s before resuming after a rebuffer — are sized for
     *    streaming video over a network that might stall again. Here the bytes
     *    are usually already on disk, so those seconds are spent waiting on a
     *    buffer that fills instantly and are simply dead air after a seek.
     *    Resuming is given more room than starting: a stall means the network
     *    is genuinely struggling, and coming back with a second of audio in
     *    hand only buys the next stall.
     */
    private fun farBufferingLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            /* maxBufferMs = */ FAR_BUFFER_MS,
            /* bufferForPlaybackMs = */ START_PLAYBACK_MS,
            /* bufferForPlaybackAfterRebufferMs = */ RESUME_PLAYBACK_MS,
        )
        .setTargetBufferBytes(FAR_BUFFER_BYTES)
        .setBackBuffer(/* backBufferDurationMs = */ BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ true)
        .build()

    /**
     * Renderers whose audio sink only skips silence worth skipping.
     *
     * Media3's stock threshold is 100ms, which eats the breaths, rests and
     * pre-chorus beats *inside* a song — the setting reads as "make the music
     * sound rushed" rather than "trim dead air". A second-long floor leaves
     * musical pauses alone and still collapses the run-in and run-out of a
     * track. Everything else about the chain stays default, so
     * `skipSilenceEnabled` keeps driving it as before.
     */
    private fun silenceSkippingRenderers(
        spatial: SpatialAudioProcessor,
        transition: TransitionFilterProcessor,
    ) = object : DefaultRenderersFactory(this) {
        init {
            // Do not force PCM_FLOAT onto an OEM speaker mixer merely because
            // the preference asks for it. The selected USB route must advertise
            // the format; otherwise Media3 uses its stable PCM16 path.
            setEnableAudioFloatOutput(configuredFloatOutput)
            if (configuredFloatOutput) {
                // c2.sec.flac.decoder produces one extra 232.2ms timestamp
                // advance per decoded buffer when Media3 requests float PCM.
                // Keep the real PCM_FLOAT sink, but decode FLAC through the
                // platform software codec on affected Samsung devices.
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val selector = if (mimeType == MimeTypes.AUDIO_FLAC) {
                        MediaCodecSelector.PREFER_SOFTWARE
                    } else {
                        MediaCodecSelector.DEFAULT
                    }
                    val candidates = selector.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder,
                    )
                    if (mimeType == MimeTypes.AUDIO_FLAC) {
                        candidates.filterNot { AudioOutputPolicy.isUnsafeFloatFlacDecoder(it.name) }
                    } else {
                        candidates
                    }
                }
            }
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(
                    // Transition filtering last of the two: widening is a
                    // property of the track, and a bass swap that ran before it
                    // would have its own low end fed back in by the crossfeed.
                    arrayOf(spatial, transition),
                    SilenceSkippingAudioProcessor(
                        MIN_SILENCE_US,
                        SilenceSkippingAudioProcessor.DEFAULT_SILENCE_RETENTION_RATIO,
                        SilenceSkippingAudioProcessor.DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US,
                        SilenceSkippingAudioProcessor.DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE,
                        SilenceSkippingAudioProcessor.DEFAULT_SILENCE_THRESHOLD_LEVEL,
                    ),
                    SonicAudioProcessor(),
                ),
            )
            .build()
    }

    /**
     * Push current settings onto a player. Called for both: whichever one is
     * idle right now is the one the next transition will start a song on, so it
     * cannot be left on stale settings.
     */
    private fun applySettings(player: ExoPlayer) {
        player.skipSilenceEnabled = AppSettings.skipSilence.value
        player.setPlaybackSpeed(AppSettings.playbackSpeed.value)
        // Restore persisted shuffle and repeat states on startup.
        if (AppSettings.shuffleEnabled.value) {
            QueueShuffle.setEnabled(true)
        }
        player.repeatMode = AppSettings.repeatMode.value
    }

    /**
     * Applies the user's USB-DAC preference through the public Android routing
     * API. This does not bypass the system USB driver: it intentionally lets
     * Android negotiate only formats the connected DAC actually advertises.
     */
    private fun applyOutputRoute() {
        val manager = audioManager ?: return
        val usb = preferredUsbDevice()
        val preferred = usb.takeIf { AppSettings.preferUsbDac.value }
        eachPlayer { it.setPreferredAudioDevice(preferred) }
        AudioOutputStatus.publish(
            manager = manager,
            requestedPcmMode = AppSettings.outputPcmMode.value,
            preferred = preferred,
            floatEnabled = shouldEnableFloatOutput(),
        )
    }

    private fun requestOutputReconfiguration() {
        outputReconfigureJob?.cancel()
        outputReconfigureJob = scope.launch {
            // Replacing two renderers during a blend would cut one half of it.
            // Wait for the short transition to settle, then swap the engine.
            while (crossfade?.isTransitioning() == true) delay(50)
            val requestedFloat = shouldEnableFloatOutput()
            if (requestedFloat == configuredFloatOutput) {
                applyOutputRoute()
            } else {
                rebuildPlayersForOutput(requestedFloat)
            }
        }
    }

    /**
     * Rebuilds Media3's immutable AudioSink configuration without restarting
     * the app or service. Queue, item, position and play state are transferred
     * to the replacement player; the old AudioTrack is released only after the
     * MediaSession points at the new one.
     */
    private fun rebuildPlayersForOutput(enableFloat: Boolean) {
        val oldActive = player ?: return
        val oldSpare = spare ?: return
        val items = List(oldActive.mediaItemCount) { oldActive.getMediaItemAt(it) }
        val index = oldActive.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
        val position = oldActive.currentPosition.coerceAtLeast(0L)
        val playWhenReady = oldActive.playWhenReady
        val repeatMode = oldActive.repeatMode
        val shuffleMode = oldActive.shuffleModeEnabled

        oldActive.playWhenReady = false
        crossfade?.release()
        oldActive.removeListener(playbackListener)
        oldActive.removeAnalyticsListener(formatListener)

        configuredFloatOutput = enableFloat
        activeFilter = transitionFilterA
        spareFilter = transitionFilterB
        val newActive = buildPlayer(spatialAudioProcessorA, transitionFilterA, ownsSession = true)
        val newSpare = buildPlayer(spatialAudioProcessorB, transitionFilterB, ownsSession = false)
        player = newActive
        spare = newSpare
        newSpare.audioSessionId = newActive.audioSessionId
        AppSettings.audioSessionId.value = newActive.audioSessionId
        applySettings(newActive)
        applySettings(newSpare)
        newActive.repeatMode = repeatMode
        newActive.shuffleModeEnabled = shuffleMode
        if (items.isNotEmpty()) {
            newActive.setMediaItems(items, index.coerceIn(items.indices), position)
        }
        newActive.addListener(playbackListener)
        newActive.addAnalyticsListener(formatListener)

        val newCrossfade = createCrossfadeController()
        crossfade = newCrossfade
        newCrossfade.start()
        mediaSession?.player = SessionPlayer(newActive, newCrossfade) { lastPublishedSubtitle }
        applyOutputRoute()
        if (items.isNotEmpty()) newActive.prepare()
        newActive.playWhenReady = playWhenReady

        oldActive.release()
        oldSpare.release()
        TrackLog.i(
            "AUDIO_OUT",
            "live output switch: ${if (enableFloat) "PCM_FLOAT" else "PCM_16BIT"}",
            about = newActive.currentMediaItem?.mediaId,
        )
    }

    private fun preferredUsbDevice(): AudioDeviceInfo? = audioManager
        ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        ?.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }

    private fun shouldEnableFloatOutput(): Boolean {
        val usb = preferredUsbDevice()
        return AudioOutputPolicy.shouldUseFloatOutput(
            requestedMode = AppSettings.outputPcmMode.value,
            isPreferredUsbRoute = AppSettings.preferUsbDac.value && usb != null,
            advertisesPcmFloat = usb?.encodings?.contains(AudioFormat.ENCODING_PCM_FLOAT) == true,
        )
    }

    /** Runs [body] against both players, in whichever roles they currently hold. */
    private inline fun eachPlayer(body: (ExoPlayer) -> Unit) {
        player?.let(body)
        spare?.let(body)
    }

    private fun observeSettings() {
        scope.launch {
            AppSettings.skipSilence.collect { on -> eachPlayer { it.skipSilenceEnabled = on } }
        }
        scope.launch {
            AppSettings.preferUsbDac.drop(1).collect { requestOutputReconfiguration() }
        }
        scope.launch {
            AppSettings.outputPcmMode.drop(1).collect { requestOutputReconfiguration() }
        }
        scope.launch {
            // Not applied to a player mid-transition: [CrossfadeController]
            // stacks a beatmatch stretch on top of this setting, and writing the
            // raw value over it would drop the incoming track back to its own
            // tempo halfway through a blend. The controller re-reads the setting
            // when it restores the rate, so the change still lands.
            AppSettings.playbackSpeed.collect { speed ->
                if (crossfade?.isTransitioning() == true) return@collect
                eachPlayer { it.setPlaybackSpeed(speed) }
            }
        }
        scope.launch {
            combine(
                AppSettings.syncedLyrics,
                AppSettings.lyricsSources,
                AppSettings.lyricsSourceOrder,
                AppSettings.prioritizeSyllableSync,
            ) { synced, sources, order, prio ->
                synced to sources
            }.distinctUntilChanged().collect {
                loadLyricsForCurrentTrack()
            }
        }
        scope.launch {
            AppSettings.spatialAudio.collect { applySpatialAudioEnabled() }
        }
    }

    /**
     * The effective spatial-audio state: the user's toggle, unless the
     * active player's decoder is currently on a Dolby Atmos (E-AC-3 JOC)
     * stream, in which case it's forced off. [activeTrackIsDolbyAtmos] only
     * ever describes the active player — see [formatListener] — so both
     * processors are kept in lockstep rather than tracking a role swap.
     */
    private fun applySpatialAudioEnabled() {
        val enabled = AppSettings.spatialAudio.value && !activeTrackIsDolbyAtmos
        spatialAudioProcessorA.enabled = enabled
        spatialAudioProcessorB.enabled = enabled
    }

    private fun observeScrobbling() {
        // Keep the manager alive while its timing settings change, so updating
        // a preference does not cancel the current track's scrobble timer.
        scope.launch {
            // Explicit <Any, _>: these flows have mixed element types, and letting
            // the reified vararg combine() infer T lands on an intersection type.
            combine<Any, ScrobblingSnapshot>(
                AppSettings.lastfmEnabled,
                AppSettings.lastfmScrobbleEnabled,
                AppSettings.lastfmNowPlaying,
                AppSettings.lastfmPrimaryArtistOnly,
                AppSettings.lastfmSessionKey,
                AppSettings.lastfmApiKey,
                AppSettings.lastfmSecret,
                AppSettings.lastfmEndpoint,
                AppSettings.scrobbleMinDuration,
                AppSettings.scrobbleDelayPercent,
                AppSettings.scrobbleDelaySeconds,
            ) { values ->
                ScrobblingSnapshot(
                    lastfmEnabled = values[0] as Boolean,
                    scrobbleEnabled = values[1] as Boolean,
                    nowPlaying = values[2] as Boolean,
                    primaryArtistOnly = values[3] as Boolean,
                    sessionKey = values[4] as String,
                    apiKey = values[5] as String,
                    secret = values[6] as String,
                    endpoint = values[7] as String,
                    minDuration = values[8] as Int,
                    delayPercent = values[9] as Float,
                    delaySeconds = values[10] as Int,
                )
            }.collectLatest { snapshot ->
                val shouldEnable = AppSettings.scrobblingAvailable &&
                    snapshot.lastfmEnabled &&
                    snapshot.scrobbleEnabled &&
                    snapshot.sessionKey.isNotBlank() &&
                    snapshot.apiKey.isNotBlank() &&
                    snapshot.secret.isNotBlank()

                if (!shouldEnable) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                    return@collectLatest
                }

                LastFM.configure(
                    endpoint = snapshot.endpoint.ifBlank { LastFM.DEFAULT_API_ENDPOINT },
                    apiKey = snapshot.apiKey,
                    secret = snapshot.secret,
                    sessionKey = snapshot.sessionKey,
                )
                val manager = scrobbleManager ?: ScrobbleManager(scope).also {
                    scrobbleManager = it
                }
                manager.minSongDuration = snapshot.minDuration
                manager.scrobbleDelayPercent = snapshot.delayPercent
                manager.scrobbleDelaySeconds = snapshot.delaySeconds
                manager.useNowPlaying = snapshot.nowPlaying
                manager.usePrimaryArtistOnly = snapshot.primaryArtistOnly

                player?.let { exoPlayer ->
                    if (exoPlayer.isPlaying) {
                        manager.onPlayerStateChanged(
                            isPlaying = true,
                            song = exoPlayer.currentMediaItem?.toSong(),
                            durationMs = exoPlayer.duration.takeIf { it > 0 },
                        )
                    }
                }
            }
        }
    }

    private data class ScrobblingSnapshot(
        val lastfmEnabled: Boolean,
        val scrobbleEnabled: Boolean,
        val nowPlaying: Boolean,
        val primaryArtistOnly: Boolean,
        val sessionKey: String,
        val apiKey: String,
        val secret: String,
        val endpoint: String,
        val minDuration: Int,
        val delayPercent: Float,
        val delaySeconds: Int,
    )

    // ---- Discord Rich Presence -------------------------------------------------

    /**
     * Keeps the gateway connection in step with the settings that decide whether
     * there should be one, and re-pushes the presence when the settings that
     * decide what it *says* change.
     *
     * Two collectors rather than one because the two do different work. The
     * account and the master switch can only be honoured by building or tearing
     * down a connection; everything else is a field in a payload that can be
     * re-sent over the connection already open. Combining them would reconnect
     * the socket every time the user typed a character into a button label.
     */
    private fun observeDiscord() {
        scope.launch {
            combine(
                AppSettings.discordToken,
                AppSettings.discordRpcEnabled,
            ) { token, enabled -> token.takeIf { enabled && it.isNotBlank() } }
                .distinctUntilChanged()
                .collectLatest { token ->
                    // Torn down before anything is built, so switching accounts
                    // can't leave the old one's socket up publishing under a
                    // profile the user has just disconnected.
                    discordUpdateJob?.cancel()
                    discordRpc?.let { rpc ->
                        val wasUp = discordPresenceUp
                        discordPresenceUp = false
                        // On IO, not on this collector's main thread: the
                        // teardown closes a socket, and closing one gracefully
                        // — which is what flushes the presence-clear queued on
                        // the line above — blocks until the frame is away.
                        //
                        // Bounded, and that is the point rather than a
                        // precaution. This runs on the way to *replacing*
                        // [discordRpc], so for as long as it takes the field is
                        // null and the feature is off: a teardown that hung —
                        // which one waiting on an unreachable socket did — read
                        // to the user as a switch that had stopped working
                        // altogether until the app was restarted.
                        withContext(Dispatchers.IO + NonCancellable) {
                            withTimeoutOrNull(DISCORD_TEARDOWN_TIMEOUT_MS) {
                                if (wasUp) runCatching { rpc.close() }
                            }
                            runCatching { rpc.closeRPC() }
                        }
                    }
                    discordRpc = null

                    if (token == null) return@collectLatest
                    discordRpc = DiscordRPC(this@PlaybackService, token)
                    // A presence that only appeared at the next track change
                    // would make turning the switch on look like it had done
                    // nothing for the length of a song.
                    player?.takeIf { it.isPlaying }?.let(::pushDiscordPresence)
                }
        }

        // The card's own contents, plus the playback rate — which is not
        // cosmetic here: the timestamps are wall-clock instants with the rate
        // divided out, so a change to it invalidates a presence already up.
        scope.launch {
            // Explicit <Any, _> for the same reason as [observeScrobbling]:
            // mixed element types, and the reified vararg combine() otherwise
            // infers an intersection type. Compared as a list rather than a
            // joined string so two different settings can't stringify alike.
            combine<Any, List<Any>>(
                AppSettings.discordUseDetails,
                AppSettings.discordStatus,
                AppSettings.discordActivityType,
                AppSettings.discordActivityName,
                AppSettings.discordButton1Text,
                AppSettings.discordButton1Visible,
                AppSettings.discordButton2Text,
                AppSettings.discordButton2Visible,
                AppSettings.playbackSpeed,
            ) { it.toList() }
                .distinctUntilChanged()
                // Dropped so the collector's first emission — which arrives at
                // startup, before anything is playing — isn't treated as a
                // change the user made.
                .drop(1)
                .collect {
                    player?.takeIf { p -> p.isPlaying }?.let(::pushDiscordPresence)
                }
        }

        // A network coming back, which is the other half of surviving a spell in
        // the background: the gateway heals itself, but its retry backoff climbs
        // to a minute, and a listener who walked back into Wi-Fi shouldn't watch
        // a blank profile for that long. Nudging it here collapses the wait.
        //
        // [AppSettings.meteredConnection] is null only while there is no active
        // network, so null -> non-null is exactly "we are online again". A
        // metered/unmetered flip is worth acting on too: the socket does not
        // survive a transport handover, and the old one may not have noticed yet.
        scope.launch {
            AppSettings.meteredConnection
                .drop(1)
                .collect { metered ->
                    if (metered == null) return@collect
                    val rpc = discordRpc ?: return@collect
                    withContext(Dispatchers.IO) { runCatching { rpc.wakeUp() } }
                    // Re-pushed rather than left to the gateway's own replay,
                    // because a handover can strand the socket in a state where
                    // it believes it is still connected: the push is what makes
                    // it prove otherwise.
                    if (discordPresenceUp) {
                        player?.takeIf { p -> p.isPlaying }?.let(::pushDiscordPresence)
                    }
                }
        }
    }

    /**
     * Publishes the track [exoPlayer] is on as the user's Discord presence.
     *
     * A no-op with no connection, which is the ordinary case — most people will
     * never connect an account, and this is called from the middle of every
     * track change.
     */
    private fun pushDiscordPresence(exoPlayer: ExoPlayer) {
        val rpc = discordRpc ?: return
        val song = exoPlayer.currentMediaItem?.toSong() ?: return
        // Read on the main thread, before the push is handed to IO: by the time
        // a coroutine gets to run, the queue may have moved on, and ExoPlayer's
        // state is only legal to read from the thread it was built on.
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        val speed = exoPlayer.playbackParameters.speed

        discordUpdateJob?.cancel()
        discordPresenceUp = true
        discordUpdateJob = scope.launch(Dispatchers.IO) {
            rpc.updateSong(
                song = song,
                currentPlaybackTimeMillis = positionMs,
                durationMillis = durationMs,
                playbackSpeed = speed,
                useDetails = AppSettings.discordUseDetails.value,
                status = AppSettings.discordStatus.value,
                button1Text = AppSettings.discordButton1Text.value,
                button1Visible = AppSettings.discordButton1Visible.value,
                button2Text = AppSettings.discordButton2Text.value,
                button2Visible = AppSettings.discordButton2Visible.value,
                activityType = AppSettings.discordActivityType.value,
                activityName = AppSettings.discordActivityName.value,
            ).onFailure {
                TrackLog.d("BitChord", "Discord presence failed: ${it.message}", about = song.videoId)
            }
        }
    }

    /**
     * Takes the presence down but leaves the socket up, so resuming doesn't pay
     * for a reconnect. Discord clears the card on an activity-less presence.
     */
    private fun clearDiscordPresence() {
        val rpc = discordRpc ?: return
        if (!discordPresenceUp) return
        discordPresenceUp = false
        discordUpdateJob?.cancel()
        discordUpdateJob = scope.launch(Dispatchers.IO) {
            runCatching { rpc.close() }
        }
    }

    /**
     * Submits a finished ListenBrainz listen, but only if the service is
     * actually scrobbling — the settings are read at call time so the helper
     * stays a no-op whenever ListenBrainz is switched off.
     */
    private fun submitListenBrainzFinished(song: Song, startMs: Long, durationMs: Long?) {
        val lbEnabled = AppSettings.scrobblingAvailable && AppSettings.listenBrainzEnabled.value
        val lbToken = AppSettings.listenBrainzToken.value
        if (!lbEnabled || lbToken.isBlank()) return
        val endMs = System.currentTimeMillis()
        scope.launch {
            ListenBrainzManager.submitFinished(lbToken, song, startMs, endMs, durationMs, AppSettings.listenBrainzPrimaryArtistOnly.value)
        }
    }

    /** Sends a ListenBrainz "now playing" update for the current track. */
    private fun submitListenBrainzPlayingNow(song: Song, positionMs: Long, durationMs: Long?) {
        val lbEnabled = AppSettings.scrobblingAvailable && AppSettings.listenBrainzEnabled.value
        val lbToken = AppSettings.listenBrainzToken.value
        if (!lbEnabled || lbToken.isBlank()) return
        scope.launch {
            ListenBrainzManager.submitPlayingNow(lbToken, song, positionMs, durationMs, AppSettings.listenBrainzPrimaryArtistOnly.value)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    /**
     * Called by Android when the user swipes this app's task away from the
     * recent apps screen.
     *
     * When [AppSettings.stopOnTaskRemoved] is on we stop the player and let the
     * service die naturally; otherwise we leave it running in the background so
     * music continues past the swipe, which is the default Android behaviour for
     * a foreground-service-backed media session.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (AppSettings.stopOnTaskRemoved.value) {
            // Both, or a swipe-away mid-crossfade leaves the outgoing track
            // playing on its own out of a service that is on its way out.
            eachPlayer { it.stop() }
            stopSelf()
        }
    }


    override fun onDestroy() {
        audioManager?.unregisterAudioDeviceCallback(outputDeviceCallback)
        player?.let(::savePlaybackState)
        // And to leave the widgets showing a play button. Nothing else reports a
        // swipe-away, so a widget left on the home screen would sit there with a
        // pause glyph on a service that no longer exists.
        publishWidgetState(playing = false)
        lyricsTickerJob?.cancel()
        lyricsTickerJob = null
        serviceLyricsJob?.cancel()
        serviceLyricsJob = null
        serviceLyrics = null
        AudioCache.cancel()
        trackAnalyzer.release()
        // The YouTube Music history entry for whatever was playing, closed out
        // on the same terms as the ListenBrainz submit below: a swipe-away never
        // fires STATE_ENDED, and the tracker's own scope outlives this service,
        // so the ping still goes out after the service scope is cancelled.
        PlaybackTracker.onPlaybackFinished(
            player?.currentPosition?.div(1000) ?: lastPositionSeconds,
        )
        // Also the last chance to close out the track that was playing — a
        // swipe-away or stop never fires STATE_ENDED, so the session would
        // otherwise end with an un-scrobbled song. This must not ride on the
        // service scope: it is cancelled a few lines down, and the request
        // should still reach ListenBrainz.
        val lastSong = listenBrainzSong
        if (lastSong != null && listenBrainzStartMs > 0L) {
            val lbEnabled =
                AppSettings.scrobblingAvailable && AppSettings.listenBrainzEnabled.value
            val lbToken = AppSettings.listenBrainzToken.value
            if (lbEnabled && lbToken.isNotBlank()) {
                val lastStart = listenBrainzStartMs
                val lastDuration = player?.duration?.takeIf { it > 0 }
                CoroutineScope(Dispatchers.IO).launch {
                    ListenBrainzManager.submitFinished(
                        lbToken, lastSong, lastStart, System.currentTimeMillis(), lastDuration,
                        AppSettings.listenBrainzPrimaryArtistOnly.value,
                    )
                }
            }
        }
        scrobbleManager?.destroy()
        scrobbleManager = null
        // Last chance to get the current track's minutes onto disk: the scope is
        // cancelled a few lines down and the sampler goes with it.
        ListeningRecorder.onStopped()
        // Discord, on the same terms as the ListenBrainz submit above: the
        // service scope is cancelled a few lines down, and a presence left up
        // would advertise a track that stopped when the process did — until
        // Discord noticed the socket had gone, which can take minutes.
        discordRpc?.let { rpc ->
            discordRpc = null
            val wasUp = discordPresenceUp
            discordPresenceUp = false
            CoroutineScope(Dispatchers.IO).launch {
                withTimeoutOrNull(DISCORD_TEARDOWN_TIMEOUT_MS) {
                    if (wasUp) runCatching { rpc.close() }
                }
                runCatching { rpc.closeRPC() }
            }
        }
        scope.cancel()
        crossfade?.release()
        crossfade = null
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(playbackListener)
        player?.removeAnalyticsListener(formatListener)
        player?.release()
        player = null
        // Released too, and not conditionally: mid-crossfade it is holding a
        // decoder and an open audio track of its own, and the service going away
        // is not a reason to leave either behind.
        spare?.release()
        spare = null
        super.onDestroy()
    }

    private fun loadLyricsForCurrentTrack() {
        val currentSong = player?.currentMediaItem?.toSong() ?: run {
            serviceLyrics = null
            stopLyricsTicker()
            return
        }

        if (!AppSettings.syncedLyrics.value) {
            serviceLyrics = null
            stopLyricsTicker()
            return
        }

        val trackDurationMs = (player?.duration ?: 0L).takeIf { it > 0 } ?: 0L

        serviceLyricsJob?.cancel()
        serviceLyricsJob = scope.launch(Dispatchers.IO) {
            val localUri = currentSong.localUri
            var lines: List<LyricLine>? = null
            if (localUri != null) {
                lines = EmbeddedLyrics.forUri(this@PlaybackService, localUri)
            }
            if (lines == null) {
                val found = LyricsRepository.lyrics(
                    videoId = currentSong.videoId,
                    title = currentSong.title,
                    artist = currentSong.artist,
                    durationMs = trackDurationMs,
                    album = currentSong.albumName,
                    sources = AppSettings.lyricsSources.value,
                    order = AppSettings.lyricsSourceOrder.value,
                    prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
                )
                lines = found?.lines
            }
            withContext(Dispatchers.Main) {
                serviceLyrics = lines
                if (player?.isPlaying == true) {
                    updateLyricSubtitle()
                }
            }
        }
    }

    private fun startLyricsTicker() {
        if (lyricsTickerJob?.isActive == true) return
        lyricsTickerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                updateLyricSubtitle()
                delay(500L)
            }
        }
    }

    private fun stopLyricsTicker() {
        lyricsTickerJob?.cancel()
        lyricsTickerJob = null
        updateLyricSubtitle()
    }

    private fun updateLyricSubtitle() {
        val exoPlayer = player ?: return
        val currentSong = exoPlayer.currentMediaItem?.toSong() ?: return
        val lines = serviceLyrics
        val pos = exoPlayer.currentPosition
        val subtitleText = if (lines != null && lines.isNotEmpty() && AppSettings.syncedLyrics.value) {
            val idx = lines.indexOfLast { it.timeMs <= pos }
            val currentLine = lines.getOrNull(idx)
            if (currentLine != null && !currentLine.isGap && currentLine.text.isNotBlank()) {
                "♪ ${currentLine.text}"
            } else {
                currentSong.artist
            }
        } else {
            currentSong.artist
        }

        if (subtitleText != lastPublishedSubtitle) {
            lastPublishedSubtitle = subtitleText
            val metadata = MediaMetadata.Builder()
                .setTitle(currentSong.title)
                .setArtist(currentSong.artist)
                .setSubtitle(subtitleText)
                .setAlbumTitle(currentSong.albumName)
                .setArtworkUri(currentSong.artworkAt(NOTIFICATION_ART_PX)?.toUri())
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
            exoPlayer.playlistMetadata = metadata
        }
    }

    /**
     * What the MediaSession, and so every control surface, actually talks to.
     *
     * Two behaviours are grafted onto the player here rather than left to
     * ExoPlayer's defaults:
     *
     * **Back restarts the track.** ExoPlayer already implements
     * restart-then-skip in [Player.seekToPrevious], gated on
     * `maxSeekToPreviousPosition`. External surfaces don't use it:
     * [DefaultMediaNotificationProvider] binds its previous button to
     * `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM`, which skips unconditionally. So
     * that command is redirected rather than left to behave differently
     * depending on which back button was pressed.
     *
     * **A skip cancels the crossfade.** Blending is for a track running out,
     * not for one being changed: told to move on, the listener wants the song
     * they were on to stop, not to keep playing over the one they asked for.
     * So every skip tells [CrossfadeController] to drop whatever is in flight
     * and then moves the queue plainly.
     *
     * Command availability is deliberately untouched — mutating it through a
     * [ForwardingPlayer] means intercepting listener callbacks too. The one
     * consequence is the first track of a queue, where ExoPlayer withholds
     * `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM` for want of a previous item: back
     * stays inert on those surfaces, exactly as it already was. In the app it
     * restarts, since that path asks for `COMMAND_SEEK_TO_PREVIOUS`.
     */
    private class SessionPlayer(
        player: Player,
        private val crossfade: CrossfadeController,
        private val getSubtitle: () -> String?,
    ) : ForwardingPlayer(player) {

        override fun getMediaMetadata(): MediaMetadata {
            val base = wrappedPlayer.mediaMetadata
            val subtitle = getSubtitle()
            return if (!subtitle.isNullOrBlank()) {
                base.buildUpon().setSubtitle(subtitle).build()
            } else {
                base
            }
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            crossfade.onSkipRequested()
            val skipped = skippedByQueueJump(currentMediaItemIndex, mediaItemIndex)
            if (skipped == null) {
                wrappedPlayer.seekTo(mediaItemIndex, positionMs)
                return
            }

            // A direct choice of a later queue row bypasses every item between
            // here and there. Remove those unplayed rows before seeking so they
            // do not masquerade as listening history. The selected item's new
            // index is the first removed slot.
            wrappedPlayer.removeMediaItems(skipped.first, skipped.last + 1)
            wrappedPlayer.seekTo(skipped.first, positionMs)
        }

        override fun seekToPreviousMediaItem() {
            crossfade.onSkipRequested()
            wrappedPlayer.seekToPrevious()
        }

        override fun seekToNextMediaItem() {
            crossfade.onSkipRequested()
            wrappedPlayer.seekToNextMediaItem()
        }

        override fun seekToNext() {
            crossfade.onSkipRequested()
            wrappedPlayer.seekToNext()
        }
    }

    /**
     * Handles browsing and content resolution for Android Auto and other MediaBrowser clients.
     */
    private inner class MediaLibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(favoriteCommand)
                .add(autoplayCommand)
                .add(shuffleCommand)
                .add(beginRadioQueueCommand)
                .add(commitRadioQueueCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_AUTOPLAY -> toggleAutoplayFromNotification()
                ACTION_TOGGLE_SHUFFLE -> toggleShuffleFromNotification()
                ACTION_BEGIN_RADIO_QUEUE -> beginRadioQueue()
                ACTION_COMMIT_RADIO_QUEUE -> player?.let(::saveQueueSnapshotImmediately)
                ACTION_TOGGLE_FAVORITE -> session.player.currentMediaItem?.mediaId?.let {
                    toggleFavoriteFromNotification(it)
                }
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                )
            }
            // The actual YouTube rating is asynchronous. The command itself has been accepted;
            // the notification is refreshed when the network write completes.
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val isRecent = params?.isRecent == true ||
                params?.extras?.getBoolean("android.service.media.extra.RECENT") == true ||
                params?.extras?.getBoolean("androidx.media.MediaBrowserCompat.EXTRA_RECENT") == true

            val rootId = if (isRecent) MEDIA_RECENTS_ID else MEDIA_ROOT_ID
            val rootExtras = Bundle().apply {
                putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
                putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED_AX, true)
                putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_AX, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_AX, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_LEGACY, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_LEGACY, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt("android.media.extras.CONTENT_STYLE_BROWSABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt("android.media.extras.CONTENT_STYLE_PLAYABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            }
            val rootItem = MediaItem.Builder()
                .setMediaId(rootId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                        .setExtras(rootExtras)
                        .build(),
                )
                .build()
            val rootParams = LibraryParams.Builder()
                .setExtras(rootExtras)
                .build()
            scope.launch(Dispatchers.IO) {
                try {
                    cachedHomeFeed()
                } catch (_: Exception) {}
            }

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
            val isGridFolder = parentId == MEDIA_RECENTS_ID || parentId == MEDIA_QUICK_PICKS_ID
            val items: List<MediaItem> = when (parentId) {
                MEDIA_ROOT_ID -> listOf(
                    createFolderItem(MEDIA_RECENTS_ID, "Recents", folderType = MediaMetadata.FOLDER_TYPE_ALBUMS, isGrid = true),
                    createFolderItem(MEDIA_QUICK_PICKS_ID, "Quick Picks", folderType = MediaMetadata.FOLDER_TYPE_ALBUMS, isGrid = true),
                    createFolderItem(MEDIA_PLAYLISTS_ID, "Playlists", folderType = MediaMetadata.FOLDER_TYPE_PLAYLISTS),
                    createFolderItem(MEDIA_MORE_ID, "More", folderType = MediaMetadata.FOLDER_TYPE_MIXED),
                )
                MEDIA_MORE_ID -> listOf(
                    createFolderItem(MEDIA_LIKED_ID, "Liked Music", folderType = MediaMetadata.FOLDER_TYPE_PLAYLISTS),
                    createFolderItem(MEDIA_DOWNLOADS_ID, "Downloads", folderType = MediaMetadata.FOLDER_TYPE_MIXED),
                    createFolderItem(MEDIA_LOCAL_MUSIC_ID, "Local Music", folderType = MediaMetadata.FOLDER_TYPE_MIXED),
                )
                MEDIA_RECENTS_ID -> {
                    val songs = cachedRecentsSongs()
                    val resultItems = if (songs.isNotEmpty()) {
                        songs.map { it.toMediaItem().withGridStyle() }
                    } else if (com.music.bitchord.data.innertube.Innertube.cookie == null) {
                        listOf(createLoginPromptItem().withGridStyle())
                    } else {
                        emptyList()
                    }
                    resultItems
                }
                MEDIA_QUICK_PICKS_ID -> {
                    val songs = cachedQuickPicksSongs()
                    val resultItems = if (songs.isNotEmpty()) {
                        songs.map { it.toMediaItem().withGridStyle() }
                    } else {
                        val home = cachedHomeFeed()
                        val qpItems = home?.shelves?.flatMap { it.items }?.mapNotNull { it.toMediaItemOrNull() }
                        if (!qpItems.isNullOrEmpty()) {
                            qpItems.map { it.withGridStyle() }
                        } else if (com.music.bitchord.data.innertube.Innertube.cookie == null) {
                            listOf(createLoginPromptItem().withGridStyle())
                        } else {
                            emptyList()
                        }
                    }
                    resultItems
                }
                MEDIA_LIKED_ID -> {
                    if (com.music.bitchord.data.innertube.Innertube.cookie == null) {
                        listOf(createLoginPromptItem())
                    } else {
                        val songs = cachedLikedSongs()
                        if (songs.isNotEmpty()) {
                            songs.map { it.toMediaItem() }
                        } else {
                            listOf(createLoginPromptItem())
                        }
                    }
                }
                MEDIA_DOWNLOADS_ID -> {
                    val downloaded = Downloads.getDownloadedSongs(this@PlaybackService)
                    downloaded.forEach { songCache[it.videoId] = it }
                    downloaded.map { it.toMediaItem() }
                }
                MEDIA_PLAYLISTS_ID -> {
                    val playlists = try {
                        withTimeoutOrNull(3000L) {
                            YtMusicRepository.libraryPlaylists().getOrNull()
                                ?: YtMusicRepository.userPlaylists().getOrNull()?.map {
                                    ShelfItem(
                                        title = it.title,
                                        subtitle = it.subtitle,
                                        thumbnailUrl = it.thumbnailUrl,
                                        videoId = null,
                                        browseId = "VL${it.playlistId}",
                                    )
                                }
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    playlists.map { playlist ->
                        val browseId = playlist.browseId ?: "VL${playlist.videoId}"
                        val mediaId = if (browseId.startsWith("VL")) "playlist:${browseId.removePrefix("VL")}" else "browse:$browseId"
                        MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(playlist.title)
                                    .setSubtitle(playlist.subtitle)
                                    .setArtworkUri(playlist.thumbnailUrl?.artworkAt(NOTIFICATION_ART_PX)?.let { Uri.parse(it) })
                                    .setIsBrowsable(true)
                                    .setIsPlayable(true)
                                    .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                    .build(),
                            )
                            .build()
                    }
                }
                MEDIA_LOCAL_MUSIC_ID -> {
                    val local = LocalMediaRepository.getLocalMusic(this@PlaybackService)
                    local.forEach { songCache[it.videoId] = it }
                    local.map { it.toMediaItem() }
                }
                else -> {
                    when {
                        parentId.startsWith("playlist:") -> {
                            val playlistId = parentId.removePrefix("playlist:")
                            val songs = try {
                                withTimeoutOrNull(2500L) {
                                    YtMusicRepository.browseSongs("VL$playlistId").getOrNull()?.songs
                                        ?: YtMusicRepository.browseSongs(playlistId).getOrNull()?.songs
                                } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                            songs.forEach { songCache[it.videoId] = it }
                            songs.map { it.toMediaItem() }
                        }
                        parentId.startsWith("browse:") -> {
                            val browseId = parentId.removePrefix("browse:")
                            val songs = try {
                                withTimeoutOrNull(2500L) {
                                    YtMusicRepository.browseSongs(browseId).getOrNull()?.songs
                                } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                            songs.forEach { songCache[it.videoId] = it }
                            songs.map { it.toMediaItem() }
                        }
                        else -> {
                            val cachedSong = songCache[parentId]
                            if (cachedSong != null) {
                                listOf(cachedSong.toMediaItem())
                            } else {
                                emptyList()
                            }
                        }
                    }
                }
            }
            val returnParams = if (isGridFolder) {
                LibraryParams.Builder().setExtras(Bundle().apply {
                    putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_AX, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_AX, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_LEGACY, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_LEGACY, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt("android.media.extras.CONTENT_STYLE_BROWSABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt("android.media.extras.CONTENT_STYLE_PLAYABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
                    putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED_AX, true)
                }).build()
            } else {
                params
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), returnParams)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
            val item = when (mediaId) {
                MEDIA_ROOT_ID -> MediaItem.Builder()
                    .setMediaId(MEDIA_ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(getString(R.string.app_name))
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                            .build(),
                    ).build()
                MEDIA_RECENTS_ID -> createFolderItem(MEDIA_RECENTS_ID, "Recents", folderType = MediaMetadata.FOLDER_TYPE_ALBUMS, isGrid = true)
                MEDIA_QUICK_PICKS_ID -> createFolderItem(MEDIA_QUICK_PICKS_ID, "Quick Picks", folderType = MediaMetadata.FOLDER_TYPE_ALBUMS, isGrid = true)
                MEDIA_PLAYLISTS_ID -> createFolderItem(MEDIA_PLAYLISTS_ID, "Playlists", folderType = MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                MEDIA_MORE_ID -> createFolderItem(MEDIA_MORE_ID, "More", folderType = MediaMetadata.FOLDER_TYPE_MIXED)
                MEDIA_LIKED_ID -> createFolderItem(MEDIA_LIKED_ID, "Liked Music", folderType = MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                MEDIA_DOWNLOADS_ID -> createFolderItem(MEDIA_DOWNLOADS_ID, "Downloads")
                MEDIA_LOCAL_MUSIC_ID -> createFolderItem(MEDIA_LOCAL_MUSIC_ID, "Local Music")
                "msg:login_required" -> createLoginPromptItem()
                else -> {
                    if (mediaId.startsWith("msg:")) {
                        createLoginPromptItem()
                    } else {
                        val cached = songCache[mediaId]
                        if (cached != null) {
                            cached.toMediaItem()
                        } else if (mediaId.startsWith("playlist:") || mediaId.startsWith("browse:")) {
                            MediaItem.Builder()
                                .setMediaId(mediaId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Playlist")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                        .build(),
                                ).build()
                        } else {
                            Song(videoId = mediaId, title = "Track", artist = "Artist", thumbnailUrl = null).toMediaItem()
                        }
                    }
                }
            }
            LibraryResult.ofItem(item, null)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future(Dispatchers.IO) {
            // 1. If player currently holds items in its queue, resume from it
            val activePlayer = player
            if (activePlayer != null && activePlayer.mediaItemCount > 0) {
                val currentItems = (0 until activePlayer.mediaItemCount).map { activePlayer.getMediaItemAt(it) }
                val startIndex = activePlayer.currentMediaItemIndex.coerceIn(0, currentItems.size - 1)
                val startPositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                return@future MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.copyOf(currentItems),
                    startIndex,
                    startPositionMs,
                )
            }

            // 2. Fallback to local downloaded songs (offline-ready)
            val downloaded = Downloads.getDownloadedSongs(this@PlaybackService)
            if (downloaded.isNotEmpty()) {
                val items = downloaded.map { it.toMediaItem() }
                return@future MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.copyOf(items),
                    0,
                    0L,
                )
            }

            // 3. Fallback to device local music
            val localSongs = LocalMediaRepository.getLocalMusic(this@PlaybackService)
            if (localSongs.isNotEmpty()) {
                val items = localSongs.map { it.toMediaItem() }
                return@future MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.copyOf(items),
                    0,
                    0L,
                )
            }

            // 5. Fallback to Quick Picks / Home shelves
            val fallbackSongs = try {
                withTimeoutOrNull(2500L) {
                    val home = YtMusicRepository.home().getOrNull()
                    home?.shelves?.flatMap { shelf ->
                        shelf.items.mapNotNull { it.toMediaItemOrNull() }
                    }?.distinctBy { it.mediaId }
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            if (fallbackSongs.isNotEmpty()) {
                return@future MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.copyOf(fallbackSongs),
                    0,
                    0L,
                )
            }

            // Return empty instead of throwing an unhandled exception to prevent crash
            MediaSession.MediaItemsWithStartPosition(
                ImmutableList.of(),
                0,
                0L,
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future(Dispatchers.IO) {
            val results = try {
                withTimeoutOrNull(3000L) {
                    YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val songs = results.mapNotNull { if (it is SearchResult.Track) it.song else null }
            searchResults[query] = songs
            songs.forEach { songCache[it.videoId] = it }
            session.notifySearchResultChanged(browser, query, songs.size, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
            val songs = searchResults[query] ?: try {
                withTimeoutOrNull(3000L) {
                    YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
                        ?.mapNotNull { if (it is SearchResult.Track) it.song else null }
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            songs.forEach { songCache[it.videoId] = it }
            val fromIndex = (page * pageSize).coerceAtMost(songs.size)
            val toIndex = ((page + 1) * pageSize).coerceAtMost(songs.size)
            val paged = if (fromIndex < toIndex) songs.subList(fromIndex, toIndex) else songs
            val items = paged.map { it.toMediaItem() }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future(Dispatchers.IO) {
            val resolved = resolveMediaItems(mediaItems)
            val validIndex = startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
            MediaSession.MediaItemsWithStartPosition(
                ImmutableList.copyOf(resolved),
                validIndex,
                startPositionMs.coerceAtLeast(0L),
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future(Dispatchers.IO) {
            resolveMediaItems(mediaItems)
        }

        private suspend fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
            val resolved = mutableListOf<MediaItem>()
            for (item in mediaItems) {
                val id = item.mediaId
                val searchQuery = item.requestMetadata.searchQuery
                when {
                    !searchQuery.isNullOrBlank() -> {
                        val cached = searchResults[searchQuery]
                        val songs = if (!cached.isNullOrEmpty()) {
                            cached
                        } else {
                            try {
                                withTimeoutOrNull(3000L) {
                                    YtMusicRepository.search(searchQuery, SearchFilter.SONGS).getOrNull()
                                        ?.mapNotNull { if (it is SearchResult.Track) it.song else null }
                                } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        songs.forEach { songCache[it.videoId] = it }
                        if (songs.isNotEmpty()) {
                            resolved.addAll(songs.map { it.toMediaItem() })
                        }
                    }
                    id.startsWith("playlist:") -> {
                        val playlistId = id.removePrefix("playlist:")
                        val songs = try {
                            withTimeoutOrNull(3000L) {
                                YtMusicRepository.browseSongs("VL$playlistId").getOrNull()?.songs
                                    ?: YtMusicRepository.browseSongs(playlistId).getOrNull()?.songs
                            } ?: emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        songs.forEach { songCache[it.videoId] = it }
                        resolved.addAll(songs.map { it.toMediaItem() })
                    }
                    id.startsWith("browse:") -> {
                        val browseId = id.removePrefix("browse:")
                        val songs = try {
                            withTimeoutOrNull(3000L) {
                                YtMusicRepository.browseSongs(browseId).getOrNull()?.songs
                            } ?: emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        songs.forEach { songCache[it.videoId] = it }
                        resolved.addAll(songs.map { it.toMediaItem() })
                    }
                    id == MEDIA_ROOT_ID || id == MEDIA_RECENTS_ID -> {
                        val songs = cachedRecentsSongs()
                        resolved.addAll(songs.map { it.toMediaItem() })
                    }
                    id == MEDIA_QUICK_PICKS_ID -> {
                        val songs = cachedQuickPicksSongs()
                        resolved.addAll(songs.map { it.toMediaItem() })
                    }
                    id == MEDIA_LIKED_ID -> {
                        val songs = cachedLikedSongs()
                        if (songs.isNotEmpty()) {
                            resolved.addAll(songs.map { it.toMediaItem() })
                        }
                    }
                    id == MEDIA_DOWNLOADS_ID -> {
                        val downloaded = Downloads.getDownloadedSongs(this@PlaybackService)
                        downloaded.forEach { songCache[it.videoId] = it }
                        resolved.addAll(downloaded.map { it.toMediaItem() })
                    }
                    id == MEDIA_PLAYLISTS_ID -> {
                        val songs = try {
                            withTimeoutOrNull(2500L) {
                                val playlists = YtMusicRepository.userPlaylists().getOrNull() ?: emptyList()
                                val first = playlists.firstOrNull()
                                if (first != null) {
                                    YtMusicRepository.browseSongs("VL${first.playlistId}").getOrNull()?.songs
                                        ?: YtMusicRepository.browseSongs(first.playlistId).getOrNull()?.songs
                                } else null
                            } ?: emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        songs.forEach { songCache[it.videoId] = it }
                        resolved.addAll(songs.map { it.toMediaItem() })
                    }
                    id == MEDIA_LOCAL_MUSIC_ID -> {
                        val local = LocalMediaRepository.getLocalMusic(this@PlaybackService)
                        local.forEach { songCache[it.videoId] = it }
                        resolved.addAll(local.map { it.toMediaItem() })
                    }
                    songCache.containsKey(id) -> {
                        resolved.add(songCache[id]!!.toMediaItem())
                    }
                    item.localConfiguration != null -> {
                        resolved.add(item)
                    }
                    item.requestMetadata.mediaUri != null -> {
                        val uri = item.requestMetadata.mediaUri!!
                        val videoId = uri.getQueryParameter("v") ?: uri.lastPathSegment.orEmpty()
                        if (videoId.isNotEmpty() && !videoId.startsWith("http")) {
                            val cached = songCache[videoId]
                            if (cached != null) {
                                resolved.add(cached.toMediaItem())
                            } else {
                                resolved.add(Song(videoId = videoId, title = item.mediaMetadata.title?.toString() ?: "Track", artist = item.mediaMetadata.artist?.toString() ?: "Artist", thumbnailUrl = null).toMediaItem())
                            }
                        } else {
                            resolved.add(item.buildUpon().setUri(uri).build())
                        }
                    }
                    else -> {
                        val cached = songCache[id]
                        if (cached != null) {
                            resolved.add(cached.toMediaItem())
                        } else {
                            val song = item.toSong()
                            resolved.add(song.toMediaItem())
                        }
                    }
                }
            }
            return if (resolved.isEmpty()) mediaItems else resolved
        }
    }

    private fun createFolderItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        folderType: Int = MediaMetadata.FOLDER_TYPE_MIXED,
        isGrid: Boolean = false,
    ): MediaItem {
        val style = if (isGrid) CONTENT_STYLE_GRID_ITEM_HINT_VALUE else CONTENT_STYLE_LIST_ITEM_HINT_VALUE
        val extras = Bundle().apply {
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, style)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, style)
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_AX, style)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_AX, style)
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_LEGACY, style)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_LEGACY, style)
            putInt("android.media.extras.CONTENT_STYLE_BROWSABLE_HINT", style)
            putInt("android.media.extras.CONTENT_STYLE_PLAYABLE_HINT", style)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", style)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", style)
            putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
            putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED_AX, true)
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setFolderType(if (isGrid && folderType == MediaMetadata.FOLDER_TYPE_MIXED) MediaMetadata.FOLDER_TYPE_ALBUMS else folderType)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    private fun MediaItem.withGridStyle(): MediaItem {
        val currentExtras = mediaMetadata.extras ?: Bundle()
        val newExtras = Bundle(currentExtras).apply {
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_AX, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_AX, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT_LEGACY, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT_LEGACY, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt("android.media.extras.CONTENT_STYLE_BROWSABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt("android.media.extras.CONTENT_STYLE_PLAYABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
            putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED_AX, true)
        }
        return buildUpon()
            .setMediaMetadata(
                mediaMetadata.buildUpon()
                    .setIsBrowsable(true)
                    .setIsPlayable(true)
                    .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                    .setExtras(newExtras)
                    .build(),
            )
            .build()
    }

    private fun ShelfItem.toMediaItemOrNull(): MediaItem? {
        val vid = videoId
        if (vid != null) {
            val song = Song(
                videoId = vid,
                title = title,
                artist = subtitle,
                thumbnailUrl = thumbnailUrl,
            )
            songCache[vid] = song
            return song.toMediaItem()
        }
        val bid = browseId
        if (bid != null) {
            return MediaItem.Builder()
                .setMediaId("browse:$bid")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtworkUri(thumbnailUrl.artworkAt(NOTIFICATION_ART_PX)?.let { Uri.parse(it) })
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                        .build(),
                )
                .build()
        }
        return null
    }

    private fun createLoginPromptItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("msg:login_required")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Login with Youtube on your phone")
                    .setSubtitle("Open BitChord on phone to sign in")
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    private companion object {
        const val MEDIA_ROOT_ID = "root"
        const val MEDIA_RECENTS_ID = "recents"
        const val MEDIA_QUICK_PICKS_ID = "quick_picks"
        const val MEDIA_PLAYLISTS_ID = "playlists"
        const val MEDIA_MORE_ID = "more"
        const val MEDIA_LIKED_ID = "liked"
        const val MEDIA_DOWNLOADS_ID = "downloads"
        const val MEDIA_LOCAL_MUSIC_ID = "local_music"

        // Android Auto Content Style Hints
        const val EXTRA_CONTENT_STYLE_SUPPORTED = "android.media.browse.extra.CONTENT_STYLE_SUPPORTED"
        const val EXTRA_CONTENT_STYLE_SUPPORTED_AX = "androidx.media.contentstyle.CONTENT_STYLE_SUPPORTED"
        const val EXTRA_CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.extra.CONTENT_STYLE_BROWSABLE_HINT"
        const val EXTRA_CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.extra.CONTENT_STYLE_PLAYABLE_HINT"
        const val EXTRA_CONTENT_STYLE_BROWSABLE_HINT_AX = "androidx.media.contentstyle.CONTENT_STYLE_BROWSABLE_HINT"
        const val EXTRA_CONTENT_STYLE_PLAYABLE_HINT_AX = "androidx.media.contentstyle.CONTENT_STYLE_PLAYABLE_HINT"
        const val EXTRA_CONTENT_STYLE_BROWSABLE_HINT_LEGACY = "androidx.media.utils.CONTENT_STYLE_BROWSABLE_HINT"
        const val EXTRA_CONTENT_STYLE_PLAYABLE_HINT_LEGACY = "androidx.media.utils.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
        const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 4
        /**
         * Shared by both players. Identical on purpose: they take turns being
         * the session, and a difference here would be an audible change of
         * routing at the handoff.
         */
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        const val CHANNEL_ID = "bitchord_playback"
        const val SESSION_ID = "BitChordPlayback"
        const val ACTION_TOGGLE_FAVORITE = "com.music.bitchord.action.TOGGLE_FAVORITE"

        /** How often played-seconds are sampled off the player. */
        const val PROGRESS_SAMPLE_MS = 5_000L

        /**
         * How long a Discord teardown may spend clearing the presence before the
         * socket is closed out from under it.
         *
         * Closing the gateway ends the session, which clears the card on
         * Discord's side anyway — the explicit clear only makes it immediate. So
         * this is a bound on politeness, not on correctness, and it is short
         * because whatever is tearing down is waiting on it.
         */
        const val DISCORD_TEARDOWN_TIMEOUT_MS = 3_000L

        /**
         * Size of each range the player fetches. The same figure read-ahead
         * uses, and for the same reason — see [ChunkedDataSource].
         */
        const val STREAM_CHUNK_BYTES = 2L * 1024 * 1024

        /** Shortest gap "skip silence" is allowed to touch. */
        const val MIN_SILENCE_US = 1_000_000L

        /** Past any song, so the byte ceiling is what stops loading. */
        const val FAR_BUFFER_MS = 15 * 60 * 1000

        /** ~6 minutes at 160kbps: a whole track, for all but the longest. */
        const val FAR_BUFFER_BYTES = 8 * 1024 * 1024

        /**
         * A short nudge backwards, and no more: this shares the byte ceiling
         * above with the read-ahead it would otherwise starve.
         */
        const val BACK_BUFFER_MS = 30 * 1000

        /** Enough to cover the decoder's own latency, not seconds of dead air. */
        const val START_PLAYBACK_MS = 500

        /** More room after a stall than at the start — see the load control. */
        const val RESUME_PLAYBACK_MS = 2_000

        /**
         * Outer cap on stream resolution. Individual client calls and probes
         * have their own timeouts, but iterating all seven plus the NewPipe
         * fallback can accumulate far beyond what a listener should wait.
         *
         * The NewPipe fallback alone — a scrape of the watch page, shaped
         * harder than anything else this app asks Google for — routinely
         * takes 45-90s on its own when every player client is bot-checked, a
         * state that has become the common case rather than the rare one. A
         * cap shorter than that doesn't bound the wait; it cancels the
         * resolve just as it was about to succeed, and the retry that
         * follows restarts the same slow walk from zero, so the listener
         * waits *longer* under a tighter cap than a looser one.
         */
        const val RESOLVE_TIMEOUT_MS = 120_000L

        /**
         * Cap on offering a YouTube track to a higher-ranked source.
         *
         * Nothing like [RESOLVE_TIMEOUT_MS], because the two are not the same
         * kind of wait: that one bounds the only way to hear the track, this
         * one bounds an optional upgrade over a stream YouTube will serve
         * anyway. Generous enough for a cold module — index fetch, JS
         * download, engine init, search, then the stream URL — and short
         * enough that a dead server costs a pause rather than a stall.
         */
        const val SUBSTITUTE_TIMEOUT_MS = 20_000L

        /**
         * How much of a track has to be left for a mid-track quality swap to
         * be worth the break in the audio it costs.
         */
        const val UPGRADE_MIN_REMAINING_MS = 20_000L

        /** How often to recheck [CrossfadeController.isTransitioning] while an upgrade waits on one. */
        const val UPGRADE_CROSSFADE_POLL_MS = 250L

        /**
         * Longest an upgrade waits on a crossfade before giving up and
         * checking once more, authoritatively, right at the swap point. Well
         * past the longest transition either mode plans — 12s for a manual
         * crossfade, or a Automix's own beat-bounded overlap, plus its arm
         * lead — so this is a guard against something stuck, not a limit
         * expected to bind in the ordinary case.
         */
        const val UPGRADE_CROSSFADE_WAIT_TIMEOUT_MS = 20_000L

        /**
         * How long a transition has to have been over before an upgrade may cut
         * into the track it handed to.
         *
         * Not a second guard on the same thing as
         * [UPGRADE_CROSSFADE_WAIT_TIMEOUT_MS]'s loop, which only keeps the swap
         * out of a blend *in flight*. This is about the moment just after one:
         * the mix resolves, and the music stops a quarter of a second later for
         * a rebuild the listener has no reason to connect to bitrate. Long
         * enough for the new track to have established itself as the thing
         * playing, short enough that an upgrade is not being meaningfully
         * delayed — and it applies only where a transition actually ran, so the
         * ordinary swap, minutes from any blend, is as immediate as it was.
         */
        const val UPGRADE_AFTER_CROSSFADE_MS = 5_000L

        /**
         * How long a replacement gets to report a length before it is
         * disbelieved.
         *
         * This is silence, not patience: the swap has already cut the audio,
         * and the track sits in `STATE_BUFFERING` for the whole of it before
         * the old stream comes back. It was cut from eight seconds to two and
         * a half on the strength of "a replacement that works reports its
         * length in well under a tenth of this" — which was true of what the
         * swap landed on at the time, and is not true of a FLAC. Measured
         * here, an upgrade to a 16-bit Qobuz stream was still buffering its
         * first chunk when the window closed:
         *
         * ```
         *   upgrade reverted: replacement reports -9223372036854775807ms
         *     against 259141ms (state=2, buffered=5002ms)
         * ```
         *
         * — a working FLAC thrown away for being slower to open than a lossy
         * MP4, which is the one thing this feature exists to fetch. The
         * failure the short window was protecting against is caught by state
         * now rather than by the clock (see [watchUpgrade]), so the ceiling
         * only bounds the genuinely stuck case, and can afford to be long
         * enough for a large file over a phone connection.
         */
        const val UPGRADE_PROVE_MS = 10_000L
        const val UPGRADE_PROVE_STEP_MS = 200L

        /**
         * How long an upgrade gets to prove itself before the swap is dropped.
         *
         * Nothing like [UPGRADE_PROVE_MS], and for one reason: that window is
         * silence and this one is music. The audition runs on a player nobody
         * is listening to while the old stream plays through the whole of it,
         * so the only thing a longer ceiling costs is a decoder held open a
         * few seconds more. Generous enough for a cold hi-res FLAC over a
         * phone connection, since a stream slow to open is exactly the one
         * this feature exists to fetch and exactly the one the old
         * cut-then-wait order threw away.
         */
        const val UPGRADE_AUDITION_MS = 25_000L

        /**
         * How far past the listener an upgrade has to be buffered before it is
         * allowed to take over.
         *
         * This is the number that makes the swap inaudible. Everything inside
         * this window is on disk by the time the real player asks for it, so
         * the seam is a decoder init rather than a round trip to a CDN. It has
         * to cover the drift as well: the track keeps playing while the
         * audition buffers, so the swap lands some seconds past where the
         * audition started, and a window shorter than the audition takes would
         * put the swap point back on the network. Twelve seconds is comfortably
         * more than either.
         */
        const val UPGRADE_PREBUFFER_MS = 12_000L

        /**
         * How much of the upgraded file's opening is fetched before the
         * audition starts — see [AudioCache.warmRange] for why the audition
         * cannot be relied on to leave it behind.
         *
         * A megabyte because a FLAC header is not a header: STREAMINFO is 34
         * bytes, but the seek table, the tags and an embedded cover in front of
         * the first audio frame routinely run to hundreds of kilobytes, and a
         * range that stops short of the first frame buys nothing at all.
         */
        const val UPGRADE_HEADER_BYTES = 1L * 1024 * 1024

        /**
         * Opening fetched after an upgrade so the track stays analysable. Four
         * megabytes is a little over twelve seconds of lossless — the shortest
         * window Automix's head pass accepts — and many times that for a
         * compressed rendition, which simply finishes sooner.
         */
        const val ANALYSIS_HEAD_BYTES = 4L * 1024 * 1024

        /**
         * The audition's own buffer, in time and in bytes.
         *
         * Both well past [FAR_BUFFER_MS]'s byte ceiling, and deliberately: this
         * player has to end up [UPGRADE_PREBUFFER_MS] ahead of a position that
         * keeps moving while it works, so what it needs is the window plus
         * however long it took to fill — and at 4.6Mbit/s a hi-res FLAC eats
         * eight megabytes in under fifteen seconds. Transient, and freed with
         * the player a moment later.
         */
        const val AUDITION_BUFFER_MS = 40_000

        const val AUDITION_BUFFER_BYTES = 24 * 1024 * 1024

        /**
         * The pause between releasing the audition player and swapping onto
         * what it cached. Same reason as [RECOVERY_DELAY_MS] — Media3 lets go
         * of a cache entry as the source is released, not as the call returns —
         * and free here, because the old stream is still playing.
         */
        const val AUDITION_RELEASE_MS = 250L

        /**
         * How long the second look waits for the playing track to report its
         * own length before giving up and going on the claimed one.
         *
         * Costs nothing when it isn't needed — a prepared track answers on the
         * first poll — and it runs with the music still playing, so what it
         * spends is patience rather than silence.
         */
        const val DURATION_SETTLE_MS = 8_000L

        /**
         * How far the replacement's length may sit from the length already
         * known for this track. Anything past this is a different file, or a
         * broken one, and either way not what is being listened to.
         */
        const val UPGRADE_LENGTH_SLACK_MS = 3_000L

        /** How many times one track is picked up off the floor — see [recoverFrom]. */
        const val MAX_RECOVERIES = 2

        /**
         * How far into an exception's causes a resolver verdict is looked for.
         * Media3 wraps twice on this path and the coroutine machinery may add
         * one; nothing nests deeper. See [permanentReason].
         */
        const val PERMANENT_CAUSE_DEPTH = 8

        /**
         * The pause before a retry. Media3 refuses to remove a cache entry a
         * reader still holds, and the reader is let go asynchronously as the
         * failed source is released, so the discard needs a moment to land
         * before the same track is asked for again.
         */
        const val RECOVERY_DELAY_MS = 350L
    }
}
