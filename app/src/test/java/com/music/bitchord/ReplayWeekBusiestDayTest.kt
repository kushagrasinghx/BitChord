package com.music.bitchord

import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth

/** "This week" in Replay has a biggest day, the same as every longer period. */
class ReplayWeekBusiestDayTest {

    @Test
    fun `this week names its busiest day`() {
        val dir = Files.createTempDirectory("listening").toFile()
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        File(dir, "$month.json").writeText(
            """
            {"month":"$month",
             "tracks":[{"id":"abcdefghijk","title":"T","artist":"A","ms":90000,"plays":1,
                        "days":{"$today":{"ms":90000,"plays":1}}}],
             "days":{"${today.dayOfMonth}":90000},
             "trackDays":{"$today":{"ms":90000,"plays":1}}}
            """.trimIndent(),
        )
        ListeningStats::class.java.getDeclaredField("directory").apply { isAccessible = true }
            .set(ListeningStats, dir)

        val summary = runBlocking { ListeningStats.summary(ReplayPeriod.THIS_WEEK) }

        assertEquals(today.toString(), summary.busiestDay)
        assertEquals(90_000L, summary.busiestDayMs)
        dir.deleteRecursively()
    }
}
