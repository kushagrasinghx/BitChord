package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistIndexBuilderTest {
    private fun song(i: Int, entry: String = "slot-$i", video: String = "song-$i") =
        Song(videoId = video, title = "Track $i", artist = "Artist", thumbnailUrl = null, setVideoId = entry)

    @Test
    fun threeThousandSongsAreNotCutOffAtTenPages() {
        val builder = PlaylistIndexBuilder()
        (0 until 30).forEach { page ->
            val token = if (page == 29) null else "continuation-${page+1}"
            assertTrue(builder.addPage((page*100 until (page+1)*100).map(::song), token))
        }
        assertEquals(3000, builder.completeSnapshot()?.size)
        assertEquals("slot-2999", builder.completeSnapshot()?.last()?.setVideoId)
    }

    @Test
    fun duplicateRecordingsHaveIndependentPlaylistEntries() {
        val builder = PlaylistIndexBuilder()
        assertTrue(builder.addPage(listOf(song(1, "slot-a", "same-video"), song(2, "slot-b", "same-video")), null))
        assertEquals(2, builder.completeSnapshot()?.size)
        val delta = comparePlaylistSnapshots(builder.completeSnapshot()!!, listOf(song(2, "slot-b", "same-video")))
        assertEquals(listOf("slot-a"), delta.removed.map { it.setVideoId })
    }

    @Test
    fun additionDeletionAndReorderingAreDetectedEvenWithSameCount() {
        val before = listOf(song(1), song(2), song(3))
        val after = listOf(song(3), song(1), song(4))
        val delta = comparePlaylistSnapshots(before, after)
        assertEquals(listOf("slot-4"), delta.added.map { it.setVideoId })
        assertEquals(listOf("slot-2"), delta.removed.map { it.setVideoId })
        assertTrue(delta.reordered)
        assertTrue(delta.changed)
    }

    @Test
    fun unchangedSnapshotDoesNotTriggerRewrite() {
        val before = listOf(song(1), song(2))
        val delta = comparePlaylistSnapshots(before, before.toList())
        assertFalse(delta.changed)
    }

    @Test
    fun unfinishedAndLoopingScansCanNeverBeCommitted() {
        val partial = PlaylistIndexBuilder()
        assertTrue(partial.addPage(listOf(song(1)), "page-2"))
        assertNull(partial.completeSnapshot())
        assertFalse(partial.addPage(listOf(song(1)), "page-2"))
        assertNull(partial.completeSnapshot())
        val repeated = PlaylistIndexBuilder()
        assertTrue(repeated.addPage(listOf(song(1)), "page-2"))
        assertFalse(repeated.addPage(listOf(song(2)), "page-2"))
        assertNull(repeated.completeSnapshot())
    }
}
