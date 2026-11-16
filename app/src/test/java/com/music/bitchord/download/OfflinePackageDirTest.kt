package com.music.bitchord.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OfflinePackageDirTest {

    @Test
    fun `java hash collisions no longer share an offline directory`() {
        // "Aa" and "BB" have the same java.lang.String hashCode.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(offlinePackageDirName("Aa"), offlinePackageDirName("BB"))
    }

    @Test
    fun `directory names are deterministic and bounded`() {
        assertEquals(offlinePackageDirName("video-1"), offlinePackageDirName("video-1"))
        assertEquals(16, offlinePackageDirName("video-1").length)
    }
}
