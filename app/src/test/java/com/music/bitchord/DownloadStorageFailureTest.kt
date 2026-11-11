package com.music.bitchord

import com.music.bitchord.download.isOutOfSpace
import com.music.bitchord.download.storageFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A full device is not a connection problem.
 *
 * Every download failure used to reach the listener as "check your connection",
 * including the ones where the destination had simply filled up mid-transfer —
 * so the one thing they could have fixed was the one thing they were not told.
 */
class DownloadStorageFailureTest {

    @Test
    fun `enospc is recognised at any depth of the cause chain`() {
        assertTrue(IOException("write failed: ENOSPC (No space left on device)").isOutOfSpace())
        assertTrue(IOException("wrapped", IllegalStateException("No space left on device")).isOutOfSpace())
        assertTrue(IOException("There is not enough space on the disk").isOutOfSpace())
    }

    @Test
    fun `network failures are not storage failures`() {
        assertFalse(IOException("Connection reset by peer").isOutOfSpace())
        assertFalse(IOException("timeout").isOutOfSpace())
        assertNull(storageFailureMessage(IOException("timeout")))
    }

    @Test
    fun `a storage fault is reported as one`() {
        assertEquals(
            "Not enough free storage to finish this download",
            storageFailureMessage(IOException("ENOSPC")),
        )
    }
}
