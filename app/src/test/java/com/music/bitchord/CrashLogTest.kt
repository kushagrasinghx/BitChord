package com.music.bitchord

import com.music.bitchord.data.CrashLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = CrashLog.clear()

    @After
    fun tearDown() = CrashLog.clear()

    @Test
    fun reportCarriesExceptionAndNewestLinesAfterTheRingWraps() {
        repeat(4005) { i -> CrashLog.record('I', "Test", "<line ${i + 1}>", null) }

        val file = tmp.newFolder("crash").resolve("report.log")
        CrashLog.writeReport(
            file,
            Thread.currentThread(),
            RuntimeException("boom", IllegalStateException("root cause")),
            header = listOf("App: test"),
            appendix = { it.println("=== appendix ===") },
        )

        val text = file.readText()
        assertTrue(text.contains("boom"))
        assertTrue("cause chain is written", text.contains("root cause"))
        assertTrue(text.contains("App: test"))
        assertTrue(text.contains("<line 4005>"))
        assertTrue("oldest surviving line is kept", text.contains("<line 6>"))
        assertFalse("line 1 was overwritten by the ring", text.contains("<line 1>"))
        assertFalse(text.contains("<line 5>"))
        assertTrue(text.contains("=== appendix ==="))
        // Oldest first.
        assertTrue(text.indexOf("<line 6>") < text.indexOf("<line 4005>"))
    }

    @Test
    fun snapshotKeepsInsertionOrderBeforeWrapping() {
        CrashLog.record('D', "A", "first", null)
        CrashLog.record('E', "B", "second", RuntimeException("x"))
        val lines = CrashLog.snapshot()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("D A: first"))
        assertTrue(lines[1].contains("E B: second"))
        assertTrue(lines[1].contains("RuntimeException: x"))
    }

    @Test
    fun appendixFailureDoesNotLoseTheReport() {
        val file = tmp.newFile("r.log")
        CrashLog.writeReport(file, null, RuntimeException("boom")) { error("logcat unavailable") }
        assertTrue(file.readText().contains("boom"))
    }
}
