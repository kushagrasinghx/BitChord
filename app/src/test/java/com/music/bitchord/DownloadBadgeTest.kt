package com.music.bitchord

import com.music.bitchord.download.offlinePackageBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadBadgeTest {

    @Test
    fun `offline manifest packages report the stored container`() {
        assertEquals("HLS", offlinePackageBadge(dash = false))
        // A DASH manifest is rewritten into the same HLS package; the badge
        // must describe what is on disk, not what the source called it.
        assertEquals("HLS", offlinePackageBadge(dash = true))
    }
}
