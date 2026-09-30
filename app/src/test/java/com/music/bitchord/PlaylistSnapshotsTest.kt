package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.completePlaylistSnapshot
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class PlaylistSnapshotsTest {
    private fun song(id: String, entry: String? = null) = Song(id, "Title $id", "Artist", null, setVideoId = entry)
    private fun page(songs: List<Song>, next: String? = null, suggested: List<Song> = emptyList()) =
        YtMusicRepository.SongPage(songs, next, suggested)

    @Test fun `collects more than ten pages in server order`() = runBlocking {
        val fetched = mutableListOf<String>()
        val result = completePlaylistSnapshot(page(listOf(song("0")), "1"), fetchMore = { token ->
            fetched.add(token)
            val index = token.toInt()
            Result.success(page(listOf(song(token)), if (index < 15) "${index + 1}" else null))
        }).getOrThrow()
        assertEquals((1..15).map(Int::toString), fetched)
        assertEquals((0..15).map(Int::toString), result.songs.map { it.videoId })
        assertNull(result.continuation)
    }

    @Test fun `preserves duplicate playlist entries and first page metadata`() = runBlocking {
        val header = InnertubeParser.BrowseHeader("Playlist", "Description", "https://example.com/art.jpg")
        val first = page(listOf(song("same", "entry-a"), song("other")), "next").copy(
            header = header, owned = true, library = LibraryState("PL123", true), description = "First description",
        )
        val result = completePlaylistSnapshot(first, fetchMore = {
            Result.success(page(listOf(song("same", "entry-a"), song("same", "entry-b"), song("other"), song("last")))
                .copy(header = header.copy(title = "Wrong title"), owned = false, description = "Wrong description"))
        }).getOrThrow()
        assertEquals(listOf("same", "other", "same", "last"), result.songs.map { it.videoId })
        assertEquals(listOf("entry-a", null, "entry-b", null), result.songs.map { it.setVideoId })
        assertEquals(first.header, result.header)
        assertEquals(first.owned, result.owned)
        assertEquals(first.library, result.library)
        assertEquals(first.description, result.description)
    }

    @Test fun `replacement reflects removals and order independently of previous snapshot`() = runBlocking {
        val previous = page(listOf(song("removed"), song("a"), song("b")))
        val replacement = completePlaylistSnapshot(page(listOf(song("b")), "next"), fetchMore = {
            Result.success(page(listOf(song("a"), song("new"))))
        }).getOrThrow()
        assertEquals(listOf("b", "a", "new"), replacement.songs.map { it.videoId })
        assertFalse(replacement.songs.any { it.videoId == "removed" })
        assertEquals(listOf("removed", "a", "b"), previous.songs.map { it.videoId })
    }

    @Test fun `suggestions are distinct and exclude tracks found on later pages`() = runBlocking {
        val first = page(listOf(song("a")), "next", listOf(song("later"), song("suggestion")))
        val result = completePlaylistSnapshot(first, fetchMore = {
            Result.success(page(listOf(song("later")), suggested = listOf(song("suggestion"), song("new-suggestion"), song("a"))))
        }).getOrThrow()
        assertEquals(listOf("suggestion", "new-suggestion"), result.suggested.map { it.videoId })
    }

    @Test fun `failure returns no complete value and preserves the original failure`() = runBlocking {
        val failure = IllegalStateException("Network unavailable")
        val partial = mutableListOf<YtMusicRepository.SongPage>()
        val result = completePlaylistSnapshot(page(listOf(song("a")), "next"), fetchMore = {
            Result.failure(failure)
        }, onPartial = partial::add)
        assertSame(failure, result.exceptionOrNull())
        assertNull(result.getOrNull())
        assertEquals(listOf("a"), partial.single().songs.map { it.videoId })
        assertEquals("next", partial.single().continuation)
    }

    @Test fun `continuation cycle fails instead of publishing a complete list`() = runBlocking {
        var calls = 0
        val result = completePlaylistSnapshot(page(listOf(song("a")), "loop"), fetchMore = {
            calls++
            Result.success(page(listOf(song("b")), "loop"))
        })
        assertEquals(1, calls)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertNull(result.getOrNull())
    }

    @Test fun `inactive listener stops before fetching or publishing any partial content`() = runBlocking {
        var calls = 0
        val partial = mutableListOf<YtMusicRepository.SongPage>()
        val result = completePlaylistSnapshot(page(listOf(song("a")), "next"), fetchMore = {
            calls++
            Result.success(page(emptyList()))
        }, shouldContinue = { false }, onPartial = partial::add)
        assertEquals(0, calls)
        assertTrue(partial.isEmpty())
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test fun `listener change while fetching rejects newly fetched content`() = runBlocking {
        var active = true
        val partial = mutableListOf<YtMusicRepository.SongPage>()
        val result = completePlaylistSnapshot(page(listOf(song("a")), "next"), fetchMore = {
            active = false
            Result.success(page(listOf(song("b"))))
        }, shouldContinue = { active }, onPartial = partial::add)
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, partial.size)
        assertEquals(listOf("a"), partial.single().songs.map { it.videoId })
    }

    @Test fun `thrown cancellation remains a failed result`() = runBlocking {
        val cancellation = CancellationException("Refresh cancelled")
        val result = completePlaylistSnapshot(page(listOf(song("a")), "next"), fetchMore = { throw cancellation })
        assertSame(cancellation, result.exceptionOrNull())
        assertNull(result.getOrNull())
    }

    @Test fun `successful empty replacement is valid and does not fetch`() = runBlocking {
        var called = false
        val empty = page(emptyList())
        val result = completePlaylistSnapshot(empty, fetchMore = {
            called = true
            Result.failure(IllegalStateException("Unexpected continuation"))
        }).getOrThrow()
        assertFalse(called)
        assertEquals(empty, result)
    }

    @Test fun `partial callbacks retain earlier immutable pages`() = runBlocking {
        val partial = mutableListOf<YtMusicRepository.SongPage>()
        val result = completePlaylistSnapshot(page(listOf(song("a")), "next"), fetchMore = {
            Result.success(page(listOf(song("b"))))
        }, onPartial = partial::add).getOrThrow()
        assertEquals(listOf("a"), partial.first().songs.map { it.videoId })
        assertEquals(listOf("a", "b"), partial.last().songs.map { it.videoId })
        assertEquals(result, partial.last())
    }
}
