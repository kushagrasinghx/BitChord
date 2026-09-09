package com.music.bitchord.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * Guardrail for the offline edition: a remote URI is never handed to a
 * network-capable data source. Local content/file URIs are delegated to the
 * normal Media3 stack by the caller.
 */
object OfflineDataSource {
    fun requireLocal(dataSpec: DataSpec) {
        val scheme = dataSpec.uri.scheme?.lowercase()
        if (scheme != "content" && scheme != "file" && scheme != "android.resource") {
            throw IOException("Offline playback rejected non-local URI: ${dataSpec.uri}")
        }
    }
}
