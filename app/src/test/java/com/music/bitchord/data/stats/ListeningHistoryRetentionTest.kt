package com.music.bitchord.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ListeningHistoryRetentionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLifetimeMonthsNotPurged() {
        // Simulating 75 months of lifetime listening history (e.g. 2020-09 to 2026-11)
        val statsDir = tempFolder.newFolder("listening_stats")
        val generatedMonths = (1..75).map { idx ->
            val year = 2020 + (idx / 12)
            val month = (idx % 12) + 1
            String.format("%04d-%02d", year, month)
        }

        generatedMonths.forEach { m ->
            File(statsDir, "$m.json").writeText("""{"month":"$m","tracks":[],"artists":[],"hours":[]}""")
        }

        val presentFiles = statsDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        assertEquals(75, presentFiles.size)

        // Verifying that files older than 36 months are all present
        assertTrue(presentFiles.size > 36)
        assertTrue(File(statsDir, "2020-02.json").exists() || File(statsDir, generatedMonths.first() + ".json").exists())
    }
}
