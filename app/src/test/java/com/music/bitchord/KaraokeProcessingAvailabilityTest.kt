package com.music.bitchord

import com.music.bitchord.playback.KaraokeProcessingAvailability
import com.music.bitchord.playback.KaraokeProcessingAvailability.AVAILABLE
import com.music.bitchord.playback.KaraokeProcessingAvailability.BIT_PERFECT_ACTIVE
import com.music.bitchord.playback.KaraokeProcessingAvailability.NEEDS_LINEAR_PCM
import com.music.bitchord.playback.KaraokeProcessingAvailability.NEEDS_STEREO
import com.music.bitchord.playback.KaraokeProcessingAvailability.REMOTE_OUTPUT_ACTIVE
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Karaoke availability model: every incompatible route is reported as
 * such rather than the effect silently doing nothing.
 */
class KaraokeProcessingAvailabilityTest {

    @Test
    fun `local stereo pcm is available`() {
        assertEquals(
            AVAILABLE,
            KaraokeProcessingAvailability.resolve(
                channels = 2,
                dspAvailable = true,
                bitPerfectActive = false,
                remoteOutputActive = false,
            ),
        )
    }

    @Test
    fun `unknown channels are not a failure`() {
        assertEquals(
            AVAILABLE,
            KaraokeProcessingAvailability.resolve(
                channels = null,
                dspAvailable = true,
                bitPerfectActive = false,
                remoteOutputActive = false,
            ),
        )
    }

    @Test
    fun `mono needs stereo`() {
        assertEquals(
            NEEDS_STEREO,
            KaraokeProcessingAvailability.resolve(
                channels = 1,
                dspAvailable = true,
                bitPerfectActive = false,
                remoteOutputActive = false,
            ),
        )
    }

    @Test
    fun `multichannel needs stereo`() {
        assertEquals(
            NEEDS_STEREO,
            KaraokeProcessingAvailability.resolve(
                channels = 6,
                dspAvailable = true,
                bitPerfectActive = false,
                remoteOutputActive = false,
            ),
        )
    }

    @Test
    fun `a bitstream that was never decoded needs linear pcm`() {
        assertEquals(
            NEEDS_LINEAR_PCM,
            KaraokeProcessingAvailability.resolve(
                channels = 2,
                dspAvailable = false,
                bitPerfectActive = false,
                remoteOutputActive = false,
            ),
        )
    }

    @Test
    fun `bit perfect output bypasses processing`() {
        assertEquals(
            BIT_PERFECT_ACTIVE,
            KaraokeProcessingAvailability.resolve(
                channels = 2,
                dspAvailable = true,
                bitPerfectActive = true,
                remoteOutputActive = false,
            ),
        )
    }

    @Test
    fun `cast and the watch play the original stream`() {
        assertEquals(
            REMOTE_OUTPUT_ACTIVE,
            KaraokeProcessingAvailability.resolve(
                channels = 2,
                dspAvailable = true,
                bitPerfectActive = false,
                remoteOutputActive = true,
            ),
        )
    }

    @Test
    fun `a remote speaker outranks the other reasons`() {
        assertEquals(
            REMOTE_OUTPUT_ACTIVE,
            KaraokeProcessingAvailability.resolve(
                channels = 1,
                dspAvailable = false,
                bitPerfectActive = true,
                remoteOutputActive = true,
            ),
        )
        assertEquals(
            BIT_PERFECT_ACTIVE,
            KaraokeProcessingAvailability.resolve(
                channels = 1,
                dspAvailable = false,
                bitPerfectActive = true,
                remoteOutputActive = false,
            ),
        )
    }
}
