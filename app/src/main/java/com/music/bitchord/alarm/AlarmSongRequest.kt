package com.music.bitchord.alarm

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song

/** Converts the persisted alarm selection into BitChord's canonical playable model. */
object AlarmSongRequest {
    fun toSong(selection: AlarmSong?): Song? = selection
        ?.takeIf(AlarmSong::isValid)
        ?.let {
            Song(
                videoId = it.videoId.trim(),
                title = it.title,
                artist = it.artist,
                thumbnailUrl = it.artworkUrl,
                durationText = it.durationText,
                playbackSourceType = PlaybackSourceType.ALARM,
            )
        }

    /** Alarm playback deliberately owns a queue containing exactly one selected song. */
    fun queue(selection: AlarmSong?): List<Song> = listOfNotNull(toSong(selection))
}
