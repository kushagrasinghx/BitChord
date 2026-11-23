package com.music.bitchord.mcp

import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class CatalogueCacheTest {
    @Test fun boundedLruAndExpiry() {
        var now = 0L
        val cache = CatalogueCache<String, Int>(2, 100) { now }
        cache.put("a", 1); cache.put("b", 2)
        assertEquals(1, cache.get("a"))
        cache.put("c", 3)
        assertNull(cache.get("b"))
        now = 100
        assertNull(cache.get("a")); assertNull(cache.get("c"))
    }
    @Test fun dnsCauseIsRecognizedWithoutExposingMessage() {
        assertTrue(IllegalStateException("private", UnknownHostException("private host")).isCatalogueDnsFailure())
        assertFalse(IllegalArgumentException("ordinary").isCatalogueDnsFailure())
    }
    @Test fun cyclicCausesTerminate() {
        val a = Exception(); val b = Exception(a); a.initCause(b)
        assertFalse(a.isCatalogueDnsFailure())
    }
}
