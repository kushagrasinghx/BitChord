package com.music.bitchord

import com.music.bitchord.data.sources.readBounded
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedReadsTest {

    @Test
    fun `short responses are read in full`() {
        val bytes = "{\"ok\":true}".toByteArray()
        assertArrayEquals(bytes, readBounded(ByteArrayInputStream(bytes), 1024))
    }

    @Test
    fun `a response larger than the cap is rejected`() {
        assertThrows(IOException::class.java) {
            readBounded(ByteArrayInputStream(ByteArray(2048)), 1024)
        }
    }

    @Test
    fun `exactly the cap is allowed`() {
        assertArrayEquals(ByteArray(1024), readBounded(ByteArrayInputStream(ByteArray(1024)), 1024))
    }
}
