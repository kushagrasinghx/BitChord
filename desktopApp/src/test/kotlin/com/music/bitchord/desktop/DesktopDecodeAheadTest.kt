package com.music.bitchord.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDecodeAheadTest {

    /** Blocks of [blockSize] samples, each filled with its own index; [blocks] of them per pass. */
    private class CountingSource(private val blocks: Int, private val blockSize: Int = 4) : DesktopSampleSource {
        @Volatile var next = 0
        @Volatile var closedOn: Thread? = null
        val closed = CountDownLatch(1)
        /** Held while a read should look like a stalled network fetch. */
        @Volatile var stall: CountDownLatch? = null
        private val buffer = FloatArray(blockSize)

        override var sampleCount: Int = 0
            private set

        override fun readSamples(): FloatArray? {
            stall?.await()
            if (next >= blocks) return null
            buffer.fill(next.toFloat())
            next++
            sampleCount = blockSize
            return buffer
        }

        override fun seek(micros: Long): Boolean {
            next = (micros / 1_000).toInt()
            return true
        }

        override fun close() {
            closedOn = Thread.currentThread()
            closed.countDown()
        }
    }

    private fun ahead(source: DesktopSampleSource, aheadSamples: Int = 16) = DesktopDecodeAhead(
        source = source,
        outputFormat = DesktopPcmFormat(44_100, 2, 4, isFloat = true),
        durationUs = null,
        measuredFormat = null,
        aheadSamples = aheadSamples,
    )

    @Test
    fun `plays every block in order and then ends`() {
        val reader = ahead(CountingSource(blocks = 10))
        val seen = generateSequence { reader.readSamples()?.copyOf(reader.sampleCount) }.map { it.first().toInt() }.toList()
        assertEquals((0 until 10).toList(), seen)
        assertNull(reader.readSamples())
        reader.close()
    }

    @Test
    fun `holds a copy, not the decoder's reused buffer`() {
        val reader = ahead(CountingSource(blocks = 3))
        val first = reader.readSamples()!!
        Thread.sleep(50)
        assertEquals(0f, first[0])
        reader.close()
    }

    @Test
    fun `a seek drops what was decoded ahead of the old position`() {
        val reader = ahead(CountingSource(blocks = 1_000))
        assertEquals(0f, reader.readSamples()!![0])
        Thread.sleep(50)
        reader.seek(500_000)
        assertEquals(500f, reader.readSamples()!![0])
        assertEquals(501f, reader.readSamples()!![0])
        reader.close()
    }

    @Test
    fun `a seek while a read is stalled never plays the stale block`() {
        val source = CountingSource(blocks = 1_000)
        val reader = ahead(source, aheadSamples = 4)
        assertEquals(0f, reader.readSamples()!![0])
        Thread.sleep(50)
        // The worker is now parked on a full queue holding block 1; stall its next read mid-flight.
        source.stall = CountDownLatch(1)
        reader.readSamples()
        Thread.sleep(50)
        reader.seek(700_000)
        source.stall!!.countDown()
        source.stall = null
        assertEquals(700f, reader.readSamples()!![0])
        reader.close()
    }

    @Test
    fun `seeking after the end plays again`() {
        val reader = ahead(CountingSource(blocks = 2))
        while (reader.readSamples() != null) Unit
        reader.seek(0)
        assertEquals(0f, reader.readSamples()!![0])
        reader.close()
    }

    @Test
    fun `the decoder is closed on the worker, not the caller`() {
        val source = CountingSource(blocks = 1_000)
        val reader = ahead(source)
        reader.readSamples()
        reader.close()
        assertTrue(source.closed.await(2, TimeUnit.SECONDS))
        assertTrue(source.closedOn !== Thread.currentThread())
        assertNull(reader.readSamples())
    }
}
