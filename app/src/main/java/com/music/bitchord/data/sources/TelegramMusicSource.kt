package com.music.bitchord.data.sources

import android.net.Uri
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.telegram.TelegramAuthStatus
import com.music.bitchord.data.telegram.TelegramClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Local-index Telegram provider; every Telegram request is issued by TDLib on this device. */
class TelegramMusicSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {
    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.TELEGRAM
    override val displayName: String get() = config.label.ifBlank { SourceKind.TELEGRAM.label }

    override suspend fun health(): SourceHealth = when (val status = TelegramClientManager.auth.value) {
        is TelegramAuthStatus.Ready -> SourceHealth.Ok(status.accountName)
        is TelegramAuthStatus.Error -> SourceHealth.Rejected(status.message)
        else -> SourceHealth.Unreachable("Connect a Telegram account in Settings → Telegram Music.")
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
        target: TrackMatcher.Target?,
    ): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !TelegramClientManager.isReady || limit <= 0) return@withContext emptyList()
        TelegramClientManager.indexDatabase.search(query, limit, TelegramClientManager.selectedChatIds).map { track ->
            Song(
                videoId = SourceRegistry.trackKey(config.id, track.id),
                title = track.title.ifBlank { track.fileName.substringBeforeLast('.', track.fileName) },
                artist = track.artist.ifBlank { "Unknown Artist" },
                albumName = track.album,
                albumId = track.album?.let { "telegram:${track.artist.lowercase(Locale.ROOT)}:$it" },
                thumbnailUrl = null,
                durationText = track.durationSeconds.takeIf { it > 0 }?.let { "${it / 60}:${"%02d".format(Locale.ROOT, it % 60)}" },
                // Telegram's filename and MIME type are hints, not proof that
                // a FLAC/ALAC upload was not transcoded. Keep the quality
                // unverified until audio bytes have been inspected.
                sourceQuality = "UNKNOWN",
            )
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? = withContext(Dispatchers.IO) {
        if (!TelegramClientManager.isReady) return@withContext null
        val track = TelegramClientManager.indexDatabase.track(trackId) ?: return@withContext null
        val uri = Uri.Builder()
            .scheme("telegram")
            .authority("audio")
            .appendQueryParameter("id", track.id)
            .build()
        SourceStream(
            url = uri.toString(),
            // The DB codec value is a filename/MIME hint, not an observation
            // from the decoder. Let Media3 sniff the original bytes and report
            // the actual format through the existing Stats for Nerds path.
            format = StreamFormat(),
            durationSec = track.durationSeconds.takeIf { it > 0 },
        )
    }

}
