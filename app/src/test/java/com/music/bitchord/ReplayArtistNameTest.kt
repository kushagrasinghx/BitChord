package com.music.bitchord

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.YearMonth

/** Replay's artist chart keeps an artist whose name has a comma or ampersand in it whole. */
class ReplayArtistNameTest {

    @Test
    fun `a linked artist keeps a name with a comma, an unlinked credit is still cut to its lead`() {
        val dir = Files.createTempDirectory("listening").toFile()
        val month = YearMonth.now()
        // A month written before this change: the whole credit, with no link behind it.
        File(dir, "$month.json").writeText(
            """{"month":"$month","artists":[{"name":"Cheema Y & Gur Sidhu","ms":60000,"plays":1}]}""",
        )
        ListeningStats::class.java.getDeclaredField("directory").apply { isAccessible = true }
            .set(ListeningStats, dir)

        val song = Song(
            videoId = "abcdefghijk",
            title = "See You Again",
            artist = "Tyler, The Creator",
            thumbnailUrl = null,
            artists = listOf(ArtistRef("Tyler, The Creator", "UC_tyler")),
        )
        ListeningStats.record(song, playedMs = 60_000, countsAsPlay = true)

        val names = runBlocking { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.artists.map { it.title }

        assertTrue(names.toString(), "Tyler, The Creator" in names)
        assertTrue(names.toString(), "Tyler" !in names)
        assertTrue(names.toString(), "Cheema Y" in names)
        dir.deleteRecursively()
    }
}
