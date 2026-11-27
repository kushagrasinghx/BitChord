package com.music.bitchord.ui.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.TrackMatcher
import java.util.concurrent.ConcurrentHashMap
import com.music.bitchord.data.DebugLog as Log
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.music.bitchord.data.canvas.CanvasArtwork
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The picked lines on the share video's clock: which stretches of the track
 * play, back to back, and when each line is lit in the result.
 */
internal class ShareTimeline(
    /** Start and end of each stretch of the track, in track milliseconds. */
    val segments: List<Pair<Long, Long>>,
    /** Each sung line — its index in the request — and when it is lit, in output μs. */
    val windows: List<LineWindow>,
    val durationMs: Long,
) {
    /** Where in the track the video is at [outputUs]: the stretches laid end to end. */
    fun trackMsAt(outputUs: Long): Long {
        var left = (outputUs / 1000).coerceAtLeast(0L)
        for ((start, end) in segments) {
            val length = end - start
            if (left < length) return start + left
            left -= length
        }
        return segments.lastOrNull()?.second ?: 0L
    }
}

internal class LineWindow(val line: Int, val fromUs: Long, val toUs: Long)

/**
 * The timeline for [lines], or null when they carry no timing (unsynced
 * lyrics). Lines that run on from each other share one stretch, so a verse
 * plays as one take; a skip ("(...)" on the card) cuts straight to the next.
 */
internal fun shareTimeline(lines: List<LyricsShareLine>): ShareTimeline? {
    val segments = mutableListOf<Pair<Long, Long>>()
    val windows = mutableListOf<LineWindow>()
    var cursor = 0L
    lines.forEachIndexed { index, line ->
        val start = line.startMs ?: return@forEachIndexed
        val room = MAX_SHARE_MS - cursor
        if (room <= MIN_LINE_MS) return@forEachIndexed
        val end = minOf(line.endMs ?: return@forEachIndexed, start + room)
        if (end - start < MIN_LINE_MS) return@forEachIndexed
        val last = segments.lastOrNull()
        if (last != null && last.second == start) {
            segments[segments.lastIndex] = last.first to end
        } else {
            segments += start to end
        }
        windows += LineWindow(index, cursor * 1000, (cursor + end - start) * 1000)
        cursor += end - start
    }
    return if (windows.isEmpty()) null else ShareTimeline(segments, windows, cursor)
}

/**
 * The lyrics card as an MP4, ready for a story.
 *
 * Under the card: the song's animated cover when [clip] is given — Spotify's
 * Canvas or Apple's motion artwork, cropped to the frame, blurred and dimmed a
 * little so white type stays legible — or else the player's backdrop, still.
 *
 * When the lines are synced the video is the song itself: the stretch of the
 * track the lines are sung over is cut from the song's YouTube Opus — see
 * [shareOpusFor] — and [painter] is asked for the card with each line lit as
 * the vocal reaches it; the cover loops underneath for as long as that takes.
 * If no Opus can be had the same video is made silent, and unsynced lyrics
 * get the card as it is, held for [MIN_LENGTH_MS].
 *
 * Returns the file in the share folder, or null when nothing could be exported.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun exportLyricsVideo(
    context: Context,
    request: LyricsShareRequest,
    clip: CanvasArtwork?,
    /** The card without its backdrop, drawn at any moment of the song. */
    painter: LyricsCardPainter,
    /** A story: Apple's 3:4 rendition crops to 9:16 with less lost than its square one. */
    portrait: Boolean,
): File? {
    val folder = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
    val out = File(folder, VIDEO_FILE_NAME)
    val timeline = shareTimeline(request.lines)
    // H.264 wants even sides; the card's height is whatever its lines came to.
    val width = painter.width and 1.inv()
    val height = painter.height and 1.inv()
    val overlay = SyncedOverlay(painter, timeline, width, height)
    val lengthMs = timeline?.durationMs ?: MIN_LENGTH_MS

    // The still backdrop, as an image the export can hold for the video's length.
    val backdrop = if (clip == null) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(folder, BACKDROP_FILE_NAME)
                val picture = renderLyricsShareBackdrop(request, height)
                file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                file
            }.getOrNull()
        } ?: return null
    } else {
        null
    }
    val urls = clip?.let {
        listOfNotNull(it.portraitUrl.takeIf { portrait }, it.url, it.fallbackUrl).distinct()
    }

    // Looked up once: for an addon or JioSaavn row it is a YouTube search.
    val opus = timeline?.let { shareOpusFor(request.song) }?.let { (factory, uri) ->
        ShareAudio(factory, uri, timeline)
    }

    suspend fun attempt(withAudio: Boolean): Boolean {
        val audio = if (withAudio) opus ?: return false else null
        if (backdrop != null) {
            val still = EditedMediaItem.Builder(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(backdrop))
                    .setMimeType(MimeTypes.IMAGE_PNG)
                    .setImageDurationMs(lengthMs)
                    .build(),
            )
                .setFrameRate(FRAME_RATE)
                .setEffects(Effects(emptyList(), listOf(fit(width, height))))
                .build()
            return transform(context, EditedMediaItemSequence.Builder(still).build(), audio, overlay, out)
        }
        for (url in urls.orEmpty()) {
            val item = EditedMediaItem.Builder(MediaItem.fromUri(url))
                .setRemoveAudio(true)
                .setEffects(clipEffects(width, height))
                .build()
            val video = if (audio != null) {
                // The song sets the length; the cover loops under it.
                EditedMediaItemSequence.Builder(item).setIsLooping(true).build()
            } else {
                val length = probeLengthMs(context, url) ?: continue
                val repeats = ceil(lengthMs / length.toDouble()).toInt().coerceIn(1, MAX_REPEATS)
                EditedMediaItemSequence.Builder(List(repeats) { item }).build()
            }
            if (transform(context, video, audio, overlay, out)) return true
        }
        return false
    }

    if (opus != null && attempt(withAudio = true)) return out
    return if (attempt(withAudio = false)) out else null
}

/** Scaled and cropped to fill the card's frame. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun fit(width: Int, height: Int) =
    Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)

/**
 * Blurred at a quarter of the size and scaled back up: the same picture as
 * blurring the full frame with four times the radius, for a sixteenth of the
 * pixels. Then dimmed, under white type.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun clipEffects(width: Int, height: Int) = Effects(
    emptyList(),
    listOf(
        fit((width / 4) and 1.inv(), (height / 4) and 1.inv()),
        GaussianBlur(BLUR_SIGMA),
        fit(width, height),
        RgbAdjustment.Builder().setRedScale(DIM).setGreenScale(DIM).setBlueScale(DIM).build(),
    ),
)

/**
 * The card over every frame, drawn at the moment of the song that frame plays —
 * the sweep, the lifts and the held words moving exactly as they do in the
 * player. One bitmap, redrawn in place on the export's GL thread; the redraw
 * changes its generation id, which is what tells the overlay to upload it.
 * Without a timeline (unsynced lyrics) the still card, drawn once.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class SyncedOverlay(
    private val painter: LyricsCardPainter,
    private val timeline: ShareTimeline?,
    width: Int,
    height: Int,
) : BitmapOverlay() {
    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private var drawnAt: Long? = Long.MIN_VALUE

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val at = timeline?.trackMsAt(presentationTimeUs)
        if (at != drawnAt) {
            painter.render(at, bitmap)
            drawnAt = at
        }
        return bitmap
    }
}

/**
 * Which items are the song: those go through the player's chain, everything
 * else — the cover clip, Apple's HLS — through a plain one, so the clip never
 * lands in the audio cache or in front of the stream resolver.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class RoutingSourceFactory(
    private val plain: MediaSource.Factory,
    private val song: MediaSource.Factory,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        plain.setDrmSessionManagerProvider(provider)
        song.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        plain.setLoadErrorHandlingPolicy(policy)
        song.setLoadErrorHandlingPolicy(policy)
    }

    override fun getSupportedTypes(): IntArray = plain.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        if (mediaItem.localConfiguration?.tag === SongAudio) song.createMediaSource(mediaItem)
        else plain.createMediaSource(mediaItem)
}

/** Marks the song's own items for [RoutingSourceFactory]. */
private object SongAudio

/** Where the share video's audio is read from, and which stretches of it. */
private class ShareAudio(val factory: DataSource.Factory, val uri: Uri, val timeline: ShareTimeline)

/**
 * The song's YouTube Opus, whatever it is playing from right now.
 *
 * A share is cut from the same rendition every time — the Opus YouTube serves
 * and the player fetches first — rather than from whichever stream playback
 * settled on: Direct Source Stream's addon file, JioSaavn, a lossless upgrade.
 * A YouTube track reads its own; an addon or JioSaavn row (and a file on the
 * device) is matched to its YouTube recording first, the way Automix's analysis
 * is, and has no audio when nothing matches. See [AudioCache.opusShareSource].
 */
private suspend fun shareOpusFor(song: Song): Pair<DataSource.Factory, Uri>? {
    val id = youTubeIdOf(song) ?: return null
    return AudioCache.opusShareSource(id)
}

private suspend fun youTubeIdOf(song: Song): String? {
    val key = song.videoId
    if (key.isBlank()) return null
    val local = key.startsWith("content://") || key.startsWith("file://")
    if (!local && SourceRegistry.parseTrackKey(key) == null) return key
    youTubeMatches[key]?.let { return it }
    val target = TrackMatcher.targetOf(song).copy(isVideo = false)
    if (target.title.isBlank()) return null
    return withContext(Dispatchers.IO) {
        for (query in TrackMatcher.queries(target)) {
            val candidates = runCatching { YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull() }
                .getOrNull()
                ?.filterIsInstance<SearchResult.Track>()
                ?.map { it.song }
                .orEmpty()
            val match = TrackMatcher.best(candidates, target) ?: continue
            youTubeMatches[key] = match.videoId
            return@withContext match.videoId
        }
        null
    }
}

/** Addon and JioSaavn rows already matched to YouTube, so a second export doesn't search again. */
private val youTubeMatches = ConcurrentHashMap<String, String>()

/** The clip's length, read off a muted player that is released straight after. */
@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun probeLengthMs(context: Context, url: String): Long? = withContext(Dispatchers.Main) {
    val player = ExoPlayer.Builder(context).build()
    try {
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && cont.isActive) {
                            cont.resume(player.duration.takeIf { it != C.TIME_UNSET && it > 0 })
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
                player.volume = 0f
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
        }
    } finally {
        player.release()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun transform(
    context: Context,
    video: EditedMediaItemSequence,
    /** The Opus and the stretches to cut from it; null for a silent video. */
    audio: ShareAudio?,
    overlay: BitmapOverlay,
    out: File,
): Boolean {
    val sequences = mutableListOf(video)
    var songFactory: MediaSource.Factory = DefaultMediaSourceFactory(context)
    if (audio != null) {
        val pieces = audio.timeline.segments.map { (start, end) ->
            EditedMediaItem.Builder(
                MediaItem.Builder()
                    .setUri(audio.uri)
                    .setTag(SongAudio)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(start)
                            .setEndPositionMs(end)
                            .build(),
                    )
                    .build(),
            )
                .setRemoveVideo(true)
                .build()
        }
        sequences += EditedMediaItemSequence.Builder(pieces).build()
        songFactory = DefaultMediaSourceFactory(audio.factory)
    }
    // The card goes on over the whole composition, so its clock is the
    // video's own — the same however many times the cover has looped.
    val composition = Composition.Builder(sequences)
        .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
        .build()

    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setAssetLoaderFactory(
                    DefaultAssetLoaderFactory(
                        context,
                        DefaultDecoderFactory.Builder(context).build(),
                        Clock.DEFAULT,
                        RoutingSourceFactory(DefaultMediaSourceFactory(context), songFactory),
                        DataSourceBitmapLoader(context),
                    ),
                )
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build(),
                        )
                        .build(),
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        Log.w(TAG, "lyrics video export failed (audio=${audio != null})", exportException)
                        if (cont.isActive) cont.resume(false)
                    }
                })
                .build()
            out.delete()
            transformer.start(composition, out.path)
            // The sheet closed mid-export: stop encoding, on the thread it runs on.
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
        }
    }
}

/** [video] handed to another app, through the same FileProvider folder the card goes through. */
internal fun shareVideoIntent(context: Context, video: File): Intent? = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
    Intent(Intent.ACTION_SEND)
        .setType(VIDEO_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}.getOrNull()

/** Copies [video] into the device's Movies/BitChord, where a gallery will find it. */
internal suspend fun saveVideoToGallery(context: Context, video: File, label: String): Boolean =
    withContext(Dispatchers.IO) {
        val name = "bitchord-lyrics-${label.replace(' ', '-').lowercase(Locale.ROOT)}.mp4"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                // Pending while it is written: the gallery doesn't list a row
                // until it is cleared, so "Saved" never names a half-copied file.
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BitChord")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri: Uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("no row")
                try {
                    val written = resolver.openOutputStream(uri)?.use { sink ->
                        video.inputStream().use { it.copyTo(sink) }
                    } ?: error("no stream")
                    check(written == video.length()) { "short write" }
                    val published = resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                    check(published == 1) { "not published" }
                } catch (e: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "BitChord")
                dir.mkdirs()
                video.copyTo(File(dir, name), overwrite = true)
            }
            true
        }.getOrDefault(false)
    }

private const val SHARE_FOLDER = "shared"
private const val VIDEO_FILE_NAME = "lyrics.mp4"
private const val VIDEO_MIME = "video/mp4"

/** Long enough to read five lines in a story before it loops. */
private const val MIN_LENGTH_MS = 10_000L

/** Instagram's limit for one story clip. */
private const val MAX_SHARE_MS = 60_000L

/** Shorter than this and a "line" is a stamp glitch, not something sung. */
private const val MIN_LINE_MS = 300L

private const val FRAME_RATE = 30
private const val BACKDROP_FILE_NAME = "lyrics-backdrop.png"
private const val TAG = "LyricsShareVideo"
private const val MAX_REPEATS = 6
private const val PROBE_TIMEOUT_MS = 10_000L

/** At quarter size: about the blur the preview draws at full size. */
private const val BLUR_SIGMA = 12f

/**
 * The clip at 60%: about as dark as the player's own backdrop, so a bright
 * Canvas sits under the words the way the gradient does.
 */
private const val DIM = 0.6f

/** Comfortably past what Instagram re-encodes a 1080p story to. */
private const val VIDEO_BITRATE = 10_000_000
