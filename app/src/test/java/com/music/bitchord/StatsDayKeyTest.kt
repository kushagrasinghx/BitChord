package com.music.bitchord

import com.music.bitchord.data.stats.ListeningStats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Per-day listening is stored under `YYYY-MM-DD` keys and read back with
 * `LocalDate.parse`, so the key must use ASCII digits in every device language.
 */
class StatsDayKeyTest {

    private val saved = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(saved)

    @Test
    fun `a day key reads back as a date in languages with their own digits`() {
        for (tag in listOf("en", "mr", "bn", "ar-EG", "fa")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val key = ListeningStats.dayKey("2026-11", 5)
            assertEquals(tag, "2026-11-05", key)
            assertEquals(tag, LocalDate.of(2026, 11, 5), LocalDate.parse(key))
        }
    }
}
