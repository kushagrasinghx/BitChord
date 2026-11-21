package com.music.bitchord

import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.screens.AddedOrder
import com.music.bitchord.ui.screens.addedOrderOf
import com.music.bitchord.ui.screens.sortedForDetail
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Date added" on a detail page: when a song joined it, not how old its file is (#1593). */
class DateAddedSortTest {

    private fun song(id: String, fileAdded: Long? = null) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        albumName = null,
        thumbnailUrl = null,
        localDateAddedSeconds = fileAdded,
    )

    private fun List<Song>.ids() = map { it.videoId }

    @Test
    fun `a song added to a playlist today is the most recent, however old its file`() {
        // Appended last, but downloaded long before the rest.
        val playlist = listOf(song("a", 2_000), song("b", 3_000), song("c", 1_000))

        assertEquals(
            listOf("c", "b", "a"),
            playlist.sortedForDetail(SongSort.DATE_ADDED_DESC, AddedOrder.APPENDED).ids(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            playlist.sortedForDetail(SongSort.DATE_ADDED_ASC, AddedOrder.APPENDED).ids(),
        )
    }

    @Test
    fun `a streamed song added after downloaded ones is not sunk below them`() {
        val playlist = listOf(song("downloaded-1", 1_000), song("downloaded-2", 2_000), song("streamed"))

        assertEquals(
            listOf("streamed", "downloaded-2", "downloaded-1"),
            playlist.sortedForDetail(SongSort.DATE_ADDED_DESC, AddedOrder.APPENDED).ids(),
        )
    }

    @Test
    fun `a liked list is already newest first`() {
        val liked = listOf(song("newest", 1_000), song("middle", 3_000), song("oldest", 2_000))

        assertEquals(
            listOf("newest", "middle", "oldest"),
            liked.sortedForDetail(SongSort.DATE_ADDED_DESC, AddedOrder.NEWEST_FIRST).ids(),
        )
        assertEquals(
            listOf("oldest", "middle", "newest"),
            liked.sortedForDetail(SongSort.DATE_ADDED_ASC, AddedOrder.NEWEST_FIRST).ids(),
        )
    }

    @Test
    fun `device files are still sorted by their own dates`() {
        val album = listOf(song("a", 2_000), song("b", 3_000), song("c", 1_000), song("untagged"))

        assertEquals(
            listOf("b", "a", "c", "untagged"),
            album.sortedForDetail(SongSort.DATE_ADDED_DESC).ids(),
        )
        assertEquals(
            listOf("c", "a", "b", "untagged"),
            album.sortedForDetail(SongSort.DATE_ADDED_ASC).ids(),
        )
    }

    @Test
    fun `each page is read the way it is kept`() {
        assertEquals(AddedOrder.APPENDED, addedOrderOf("VLPLexample", BrowseType.PLAYLIST))
        assertEquals(AddedOrder.APPENDED, addedOrderOf(OfflinePlaylists.PREFIX + "p1", BrowseType.PLAYLIST))
        assertEquals(AddedOrder.APPENDED, addedOrderOf(Downloads.pageIdFor("VLPLexample"), BrowseType.PLAYLIST))

        assertEquals(AddedOrder.NEWEST_FIRST, addedOrderOf(YtMusicRepository.LIKED_MUSIC, BrowseType.PLAYLIST))
        assertEquals(AddedOrder.NEWEST_FIRST, addedOrderOf("LM", BrowseType.PLAYLIST))
        assertEquals(
            AddedOrder.NEWEST_FIRST,
            addedOrderOf(Downloads.pageIdFor(YtMusicRepository.LIKED_MUSIC), BrowseType.PLAYLIST),
        )
        assertEquals(
            AddedOrder.NEWEST_FIRST,
            addedOrderOf(OfflinePlaylists.PREFIX + OfflinePlaylists.LIKED_ID, BrowseType.PLAYLIST),
        )

        assertEquals(AddedOrder.FILE_DATES, addedOrderOf("MPREexample", BrowseType.ALBUM))
        assertEquals(AddedOrder.FILE_DATES, addedOrderOf(null, null))
    }
}
