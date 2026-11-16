package com.music.bitchord

import com.music.bitchord.download.DownloadStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A transfer that cannot finish must stop before writing what it would throw
 * away.
 *
 * The check at `DownloadStore.begin` is a snapshot from before the first byte;
 * a queue of four lossless albums can fill the device long before the last one
 * starts. These cover the decision itself: an exact remainder when the length is
 * known, and nothing but headroom when it is not.
 */
class DownloadSpaceHeadroomTest {

    private val mib = 1024L * 1024L

    @Test
    fun `a known remainder must fit with room to tag the file`() {
        assertTrue(DownloadStore.transferFits(300L * mib, 100L * mib))
        assertFalse(DownloadStore.transferFits(100L * mib, 300L * mib))
    }

    @Test
    fun `the headroom is required on top of the remainder`() {
        val remaining = 100L * mib
        assertTrue(
            DownloadStore.transferFits(remaining + DownloadStore.TRANSFER_HEADROOM_BYTES, remaining),
        )
        assertFalse(
            DownloadStore.transferFits(remaining + DownloadStore.TRANSFER_HEADROOM_BYTES - 1, remaining),
        )
    }

    @Test
    fun `an unknown remainder asks only for the headroom`() {
        assertTrue(DownloadStore.transferFits(DownloadStore.TRANSFER_HEADROOM_BYTES, null))
        assertFalse(DownloadStore.transferFits(DownloadStore.TRANSFER_HEADROOM_BYTES - 1, null))
    }

    @Test
    fun `a negative remainder is treated as unknown rather than as credit`() {
        assertTrue(DownloadStore.transferFits(DownloadStore.TRANSFER_HEADROOM_BYTES, -4096L))
        assertFalse(DownloadStore.transferFits(DownloadStore.TRANSFER_HEADROOM_BYTES - 1, -4096L))
    }
}
