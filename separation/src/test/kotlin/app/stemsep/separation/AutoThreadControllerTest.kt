package app.stemsep.separation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoThreadControllerTest {
    @Test
    fun startsAtFourThreadsCappedByDevice() {
        assertEquals(4, AutoThreadController(threadLimit = 8).threads)
        assertEquals(2, AutoThreadController(threadLimit = 2).threads)
        assertEquals(1, AutoThreadController(threadLimit = 1).threads)
    }

    @Test
    fun raisesThreadsWhenBufferFallsBelowTarget() {
        val controller = AutoThreadController(threadLimit = 8)
        assertEquals(6, controller.observe(4f))
        assertEquals(6, controller.observe(4f))
        assertEquals(6, controller.observe(4f))
        assertEquals(8, controller.observe(4f))
    }

    @Test
    fun jumpsToLimitWhenBufferIsUrgent() {
        val controller = AutoThreadController(threadLimit = 8)
        assertEquals(8, controller.observe(1f))
    }

    @Test
    fun lowersThreadsOnlyAfterBufferRecovers() {
        val controller = AutoThreadController(threadLimit = 8)
        controller.observe(1f)
        assertEquals(8, controller.observe(9f))
        assertEquals(8, controller.observe(9f))
        assertEquals(7, controller.observe(9f))
    }

    @Test
    fun leavesThreadsAloneWhenPlaybackIsNotActive() {
        val controller = AutoThreadController(threadLimit = 8)
        assertEquals(4, controller.observe(null))
        controller.observe(1f)
        assertEquals(8, controller.observe(null))
    }
}
