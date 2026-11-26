package com.music.bitchord.data

/**
 * The parts of LocalMediaRepository (app/.../data/LocalMediaRepository.kt)
 * that read nothing but strings and numbers: which files count as music, and
 * how a file's own tags are cleaned up. In commonMain so the web player's
 * Local Music, which reads files rather than MediaStore rows, keeps and names
 * the same tracks the phone does; LocalMediaRepository delegates here.
 */
object LocalMusicRules {
    const val MIN_LOCAL_MUSIC_DURATION_MS = 30_000L

    // `mp4` only ever reaches this list as an audio row — MediaStore files a
    // video .mp4 under Video, not Audio — so it is the audio-only MP4s Dolby
    // and Apple music downloads come as (AC-4, E-AC-3, ALAC). `dsf`/`dff` are
    // DSD, which DsdExtractor plays.
    val localMusicExtensions = setOf(
        "mp3", "m4a", "mp4", "flac", "ogg", "opus", "aac", "webm", "dsf", "dff",
    )

    private val nonMusicPathSegments = listOf(
        "/alarms/",
        "/notifications/",
        "/ringtones/",
        "/podcasts/",
        "/audiobooks/",
        "/recordings/",
        "/voice recorder/",
        "/sound_recorder/",
        "/call_rec/",
        "/whatsapp voice notes/",
    )

    /**
     * MediaStore's `IS_MUSIC` flag is advisory and often includes notification
     * sounds, voice notes and recorder output. Keep this second gate independent
     * of scanner metadata so the same bad rows stay out across Android vendors.
     */
    fun isEligibleLocalMusic(durationMs: Long, displayName: String, path: String?): Boolean {
        if (durationMs in 1 until MIN_LOCAL_MUSIC_DURATION_MS) return false

        val fileName = path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: displayName
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in localMusicExtensions) return false

        val normalizedPath = path
            ?.replace('\\', '/')
            ?.lowercase()
            ?: return true
        return nonMusicPathSegments.none(normalizedPath::contains)
    }

    /** What MediaStore writes into a column it has nothing for. */
    fun cleanTag(raw: String?): String? =
        raw.takeUnless { it.isNullOrBlank() || it == "<unknown>" }

    fun removeAudioExtension(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return if (extension in localMusicExtensions || extension == "wav") {
            name.substringBeforeLast('.').ifBlank { name }
        } else {
            name
        }
    }

    /**
     * MediaStore packs the disc into the same int (`disc * 1000 + track`), so
     * 1003 is disc 1 track 3 rather than track 1003. Unpacks it; null when the
     * scanner wrote nothing usable.
     */
    fun trackNumberFromMediaStore(raw: Int?): Int? {
        if (raw == null || raw <= 0) return null
        val track = if (raw >= 1000) raw % 1000 else raw
        return track.takeIf { it in 1..999 }
    }

    /**
     * `CD_TRACK_NUMBER` arrives as `"3"` or `"3/12"`. Takes the head; null when
     * it isn't a number.
     */
    fun trackNumberFromTag(raw: String?): Int? {
        val head = raw?.substringBefore('/')?.trim() ?: return null
        return head.toIntOrNull()?.takeIf { it in 1..999 }
    }
}
