package com.music.bitchord

import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotSame
import org.junit.Test

class DownloadEnqueueDispatchTest {

    @Test
    fun `enqueue decisions never run on the main thread`() {
        assertNotSame(
            Dispatchers.Main,
            Downloads.enqueueDispatcher,
        )
    }
}
