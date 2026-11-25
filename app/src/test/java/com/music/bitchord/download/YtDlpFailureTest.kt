package com.music.bitchord.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format failure that started this work, and the policy that answers it.
 *
 * Both halves matter: the exact error has to be recognised as a format
 * failure (otherwise it is never retried), and the retry has to move to a
 * different client rather than asking the same one again. These tests pin the
 * policy without needing a device or a network.
 */
class YtDlpFailureTest {

    @Test
    fun `the reported failure is a format failure`() {
        val message = "ERROR: [youtube] dQw4w9WgXcQ: Requested format is not available. " +
            "Use --list-formats for a list of available formats"
        assertEquals(YtDlpFailureKind.FORMAT, classifyYtDlpFailure(message))
    }

    @Test
    fun `a video with no formats at all is a format failure`() {
        assertEquals(
            YtDlpFailureKind.FORMAT,
            classifyYtDlpFailure(
                "ERROR: [youtube] jNQXAC9IVRw: No video formats found!; please report this issue",
            ),
        )
    }

    @Test
    fun `ffmpeg postprocessing is not mistaken for a format failure`() {
        assertEquals(
            YtDlpFailureKind.FFMPEG,
            classifyYtDlpFailure(
                "ERROR: Postprocessing: ffmpeg not found. Please install or provide the path using --ffmpeg-location",
            ),
        )
    }

    @Test
    fun `transport failures are network failures`() {
        assertEquals(
            YtDlpFailureKind.NETWORK,
            classifyYtDlpFailure("ERROR: unable to download video data: HTTP Error 403: Forbidden"),
        )
        assertEquals(
            YtDlpFailureKind.NETWORK,
            classifyYtDlpFailure("ERROR: [youtube] x: Read timed out"),
        )
    }

    @Test
    fun `a refusal from the extractor stays an extractor failure`() {
        assertEquals(
            YtDlpFailureKind.EXTRACTOR,
            classifyYtDlpFailure("ERROR: [youtube] BaW_jenozKc: This video is unavailable"),
        )
    }

    @Test
    fun `the detail is the last useful line without its log prefix`() {
        val message = """
            WARNING: [youtube] falling back to another client
            ERROR: [youtube] x: This video is unavailable
        """.trimIndent()
        assertEquals("This video is unavailable", ytDlpDetail("ERROR: [youtube] x: This video is unavailable"))
        assertEquals("This video is unavailable", ytDlpDetail(message))
        assertNull(ytDlpDetail("WARNING: only a warning"))
    }

    @Test
    fun `each failure kind says something the listener can act on`() {
        assertEquals(
            "This video has no downloadable audio",
            YtDlpFailureKind.FORMAT.userMessage(null),
        )
        assertEquals(
            "Couldn't reach YouTube — check your connection",
            YtDlpFailureKind.NETWORK.userMessage(null),
        )
        assertEquals(
            "YouTube could not provide this video",
            YtDlpFailureKind.EXTRACTOR.userMessage(null),
        )
        assertEquals(
            "The audio could not be converted to MP3",
            YtDlpFailureKind.FFMPEG.userMessage(null),
        )
        assertEquals(
            "The download finished without an audio file",
            YtDlpFailureKind.OUTPUT.userMessage(null),
        )
        assertEquals(
            "The downloader could not start",
            YtDlpFailureKind.INIT.userMessage(null),
        )
        assertEquals(
            "This video is unavailable",
            YtDlpFailureKind.EXTRACTOR.userMessage("This video is unavailable"),
        )
    }

    @Test
    fun `the selector prefers audio-only and falls back to the best combined stream`() {
        assertEquals("bestaudio/best", YtDlpFormatSelection.SELECTOR)
        assertTrue(YtDlpFormatSelection.SELECTOR.startsWith("bestaudio"))
        assertTrue(YtDlpFormatSelection.SELECTOR.endsWith("/best"))
        // No hardcoded YouTube format id: the selector has to follow what the
        // extractor actually offers for each video.
        assertFalse(Regex("""\b\d{2,3}\b""").containsMatchIn(YtDlpFormatSelection.SELECTOR))
    }

    @Test
    fun `the first client is the no-JavaScript one and a different client follows`() {
        val clients = YtDlpFormatSelection.CLIENTS
        assertEquals("visionos,android", clients.first())
        assertEquals("android", clients[1])
        assertNull(clients.last())
        assertEquals(clients.size, clients.distinct().size)
    }

    @Test
    fun `only format and extractor failures try the next client`() {
        assertTrue(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.FORMAT, 0))
        assertTrue(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.EXTRACTOR, 0))
        assertTrue(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.FORMAT, 1))

        assertFalse(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.NETWORK, 0))
        assertFalse(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.FFMPEG, 0))
        assertFalse(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.OUTPUT, 0))
        assertFalse(YtDlpFormatSelection.shouldTryNext(YtDlpFailureKind.INIT, 0))

        // The last client has no successor, so even a format failure stops
        // there instead of looping on the same request.
        assertFalse(
            YtDlpFormatSelection.shouldTryNext(
                YtDlpFailureKind.FORMAT,
                YtDlpFormatSelection.CLIENTS.lastIndex,
            ),
        )
    }
}
