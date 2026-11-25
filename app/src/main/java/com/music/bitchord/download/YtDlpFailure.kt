package com.music.bitchord.download

/**
 * Why a yt-dlp download failed, kept apart from the text it failed with.
 *
 * The downloader has one job after a failure — decide whether another client
 * or format expression is worth trying — and the caller has another: say
 * something a listener can act on. Neither is well served by matching on a
 * whole traceback, so the traceback is classified once here and the two
 * decisions read the result.
 */
internal enum class YtDlpFailureKind {
    /** No format matched the selector: the video has no audio yt-dlp can hand over. */
    FORMAT,

    /** The request never reached YouTube, or was cut off on the way. */
    NETWORK,

    /** YouTube answered, but refused this video or this client. */
    EXTRACTOR,

    /** The bytes arrived, but FFmpeg could not turn them into MP3. */
    FFMPEG,

    /** yt-dlp reported success but left no usable MP3 behind. */
    OUTPUT,

    /** yt-dlp or FFmpeg could not be started at all. */
    INIT,
}

/** A yt-dlp failure already classified, with a message written for the listener. */
internal class YtDlpFailure(
    val kind: YtDlpFailureKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The kind of failure [message] describes.
 *
 * Order matters: yt-dlp's format error ends in "not available", which would
 * otherwise read as an extractor refusal, and a failed FFmpeg post-process
 * quotes the extractor's own output above it. The most specific marker wins.
 */
internal fun classifyYtDlpFailure(message: String): YtDlpFailureKind {
    val text = message.lowercase()
    return when {
        "requested format is not available" in text ||
            "no video formats found" in text ||
            "no formats found" in text ||
            "format is not available" in text ||
            "only images are available" in text -> YtDlpFailureKind.FORMAT

        "ffmpeg" in text ||
            "postprocessing" in text ||
            "post-processing" in text ||
            "conversion failed" in text ||
            "could not convert" in text ||
            "audio conversion" in text -> YtDlpFailureKind.FFMPEG

        "unable to download" in text ||
            "http error" in text ||
            "timed out" in text ||
            "timeout" in text ||
            "connection" in text ||
            "network" in text ||
            "temporary failure" in text ||
            "ssl" in text ||
            "certificate" in text -> YtDlpFailureKind.NETWORK

        else -> YtDlpFailureKind.EXTRACTOR
    }
}

/** The `[youtube] id:` tag yt-dlp puts in front of its own sentence. */
private val YT_DLP_TAG = Regex("""^\[[^]]+]\s+(?:\S+:\s+)?""")

/** The last useful line of a yt-dlp traceback, without the log level or extractor tag. */
internal fun ytDlpDetail(message: String): String? =
    message.lineSequence()
        .map {
            it.trim()
                .removePrefix("ERROR: ")
                .removePrefix("error: ")
                .replaceFirst(YT_DLP_TAG, "")
        }
        .lastOrNull { it.isNotBlank() && !it.startsWith("WARNING") && !it.startsWith("Traceback") }

/** A short, actionable sentence for [kind]; extractor failures keep YouTube's own words. */
internal fun YtDlpFailureKind.userMessage(detail: String?): String = when (this) {
    YtDlpFailureKind.FORMAT -> "This video has no downloadable audio"
    YtDlpFailureKind.NETWORK -> "Couldn't reach YouTube — check your connection"
    YtDlpFailureKind.EXTRACTOR -> detail?.takeIf { it.isNotBlank() } ?: "YouTube could not provide this video"
    YtDlpFailureKind.FFMPEG -> "The audio could not be converted to MP3"
    YtDlpFailureKind.OUTPUT -> "The download finished without an audio file"
    YtDlpFailureKind.INIT -> "The downloader could not start"
}
