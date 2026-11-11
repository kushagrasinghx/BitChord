package com.music.bitchord

import com.music.bitchord.download.offlinePackageBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadBadgeTest {

    @Test
    fun `offline manifest packages report the stored container`() {
        assertEquals("HLS", offlinePackageBadge(dash = false))
        assertEquals("DASH", offlinePackageBadge(dash = true))
    }
}
