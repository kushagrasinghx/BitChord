package com.music.bitchord.playback.applemusic

import android.content.Context
import com.music.bitchord.data.applemusic.AppleMusicMatcher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One persistent matcher per process/cache file, shared with library-to-YouTube seeding. */
object AppleMusicPlayback {
    private val lock = Mutex()
    private var matcher: AppleMusicMatcher? = null

    suspend fun matcher(context: Context): AppleMusicMatcher = withContext(Dispatchers.IO) {
        lock.withLock {
            matcher ?: AppleMusicMatcher(
                cacheFile = File(context.applicationContext.filesDir, "apple_music_playback_matches"),
            ).also { matcher = it }
        }
    }
}
