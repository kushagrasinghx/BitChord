package com.music.bitchord.alarm

import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver

data class AlarmResolvedStream(
    val url: String,
    val headers: Map<String, String>,
)

object AlarmStreamResolver {
    suspend fun resolve(song: AlarmSong): AlarmResolvedStream {
        val url = StreamResolver.resolve(song.videoId)
        return AlarmResolvedStream(url, PlayerClient.forStreamUrl(url).mediaHeaders())
    }
}
