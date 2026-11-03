package com.music.bitchord.playback.applemusic

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.music.bitchord.playback.mediaIdIn
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Wrap ONLY the phone's playback factory, outside AudioCache (including cached bytes).
 * Cast/watch and the fallback resolver must never receive this gate. Media3 interrupts
 * the loader when a period is cancelled, cancelling runBlocking just like DirectSourceGate.
 */
class AppleMusicDataSource(
    private val upstream: DataSource,
    private val speaker: AppleMusicSpeaker,
) : DataSource by upstream {
    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri.scheme == "bitchord") {
            val mediaId = mediaIdIn(dataSpec.uri)
            if (mediaId != null) {
                try {
                    runBlocking {
                        while (speaker.holdsLocalAudio(mediaId)) delay(25)
                    }
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Apple Music audio wait interrupted").apply { initCause(interrupted) }
                }
                // Neither external marker is a YouTube video id, even without an SDK.
                if (AppleMusicSpeaker.isAppleRow(mediaId)) {
                    throw IOException("Apple Music catalog rows require an authorized MusicKit player")
                }
            }
        }
        return upstream.open(dataSpec)
    }
}
