package com.music.bitchord.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Prefer music-only version": a music video is heard as its catalogue song,
 * and never starts as the video first.
 *
 * [PlaybackService] converts the playing row and the next one, so a blend into
 * the next track is planned against the song rather than found to be a video
 * when it arrives. A converted row is an ordinary audio row — no `&m=1`, not
 * video-origin — which is all it takes for Automix, crossfade and the source
 * upgrade path to treat it like any other song. See [asMusicOnlyOf].
 *
 * Until a row has its answer its bytes are held above the cache, at
 * [holdingFactory], the same way [DirectSourceGate] holds a track at the
 * resolver: above, because a video already on disk would otherwise start at
 * once. The player sits buffering meanwhile, and replacing the row interrupts
 * the loader thread, which ends the wait.
 *
 * A row with no audio version is let go — see [settle] — and plays the video's
 * own audio, without the picture.
 */
object MusicOnlyPlayback {

    private const val TAG = "BitChord"

    /** How long a row waits for its audio version before playing the video's own audio. */
    const val ANSWER_TIMEOUT_MS = 15_000L

    /** The hold's own limit, past [ANSWER_TIMEOUT_MS]: nothing may wait on the service forever. */
    private const val HOLD_LIMIT_MS = ANSWER_TIMEOUT_MS + 5_000L

    /** Videos the listener switched to by hand from the player, which stay videos. */
    private val kept = MutableStateFlow<Set<String>>(emptySet())

    /** Videos playing as themselves for now, because no audio version was found. */
    private val settled = MutableStateFlow<Set<String>>(emptySet())

    /** Never in a party, where everyone hears the one cut — see [ListenTogether]. */
    fun isActive(): Boolean =
        AppSettings.preferMusicOnly.value && !ListenTogether.state.value.inParty

    /** Whether [videoId], a music video, shows its picture rather than its artwork. */
    fun showsPicture(videoId: String): Boolean = !isActive() || videoId in kept.value

    /** Whether this playback URI is a music video still owed its answer. */
    fun holds(uri: Uri): Boolean {
        if (uri.scheme != "bitchord" || uri.authority != "watch") return false
        // `m` marks a video row, and a row reverted by hand is that exact upload.
        if (uri.getQueryParameter("m") != "1") return false
        if (uri.getQueryParameter(DIRECT_YOUTUBE_PARAMETER) == "1") return false
        val videoId = mediaIdIn(uri) ?: return false
        return isActive() && videoId !in kept.value && videoId !in settled.value
    }

    /** A download has no `bitchord` URI, so it is never converted: it is what's on the device. */
    fun wants(item: MediaItem): Boolean = item.localConfiguration?.uri?.let(::holds) == true

    /** [videoId] plays as itself: no audio version, or none in time. */
    fun settle(videoId: String) = settled.update { it + videoId }

    /** A later play asks again, as a failed lookup may only have been the network. */
    fun forgetExcept(videoIds: Set<String>) = settled.update { it intersect videoIds }

    /** The listener switched to [videoId] by hand. */
    fun keep(videoId: String) = kept.update { it + videoId }

    /** The listener switched away from [videoId] by hand. */
    fun letGo(videoId: String) = kept.update { it - videoId }

    /** The setting changed, so nothing decided under the old value stands. */
    fun reset() {
        kept.value = emptySet()
        settled.value = emptySet()
    }

    private suspend fun awaitAnswer(videoId: String) {
        withTimeoutOrNull(HOLD_LIMIT_MS) {
            combine(
                AppSettings.preferMusicOnly,
                ListenTogether.state,
                kept,
                settled,
            ) { on, party, keptNow, settledNow ->
                !on || party.inParty || videoId in keptNow || videoId in settledNow
            }.first { it }
        }
    }

    /** [upstream], except that a music video owed its answer does not open until it has one. */
    @OptIn(UnstableApi::class)
    fun holdingFactory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val inner = upstream.createDataSource()
        object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) =
                inner.addTransferListener(transferListener)

            override fun open(dataSpec: DataSpec): Long {
                if (holds(dataSpec.uri)) {
                    mediaIdIn(dataSpec.uri)?.let { videoId ->
                        TrackLog.d(TAG, "holding music video for its audio version", about = videoId)
                        runBlocking { awaitAnswer(videoId) }
                    }
                }
                return inner.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                inner.read(buffer, offset, length)

            override fun getUri(): Uri? = inner.uri

            override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

            override fun close() = inner.close()
        }
    }
}

/**
 * This audio version, standing in [video]'s place in the queue.
 *
 * Not video-origin, unlike the player menu's switch: that flag is what keeps
 * Automix off a row, and the point of converting ahead of time is to have it on.
 */
fun Song.asMusicOnlyOf(video: Song): Song = copy(
    isVideoOrigin = false,
    queueTier = video.queueTier,
    queueEntryId = video.queueEntryId,
    radioName = video.radioName,
    playbackSource = video.playbackSource,
    playbackSourceType = video.playbackSourceType,
    playbackSourceId = video.playbackSourceId,
)
