package com.music.bitchord.playback

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailLoadId
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedContextRequestsTest {

    @After
    fun tearDown() {
        BoundedContextRequests.clear()
    }

    @Test
    fun `take consumes only the matching newest token and keeps snapshot`() {
        val first = request("first")
        val staleToken = BoundedContextRequests.put(first)
        val newestToken = BoundedContextRequests.put(request("newest"))

        assertNull(BoundedContextRequests.take(staleToken))
        val taken = BoundedContextRequests.take(newestToken)

        assertEquals("newest", taken?.songs?.single()?.videoId)
        assertEquals(newestToken, taken?.handoffToken)
        assertEquals("newest", BoundedContextRequests.snapshot.value?.songs?.single()?.videoId)
        assertNull(BoundedContextRequests.take(newestToken))
    }

    @Test
    fun `publish grows the process snapshot and rejects a stale handoff`() {
        val token = BoundedContextRequests.put(request("one", continuation = "next"))
        val taken = requireNotNull(BoundedContextRequests.take(token))
        val second = song("two")

        assertTrue(BoundedContextRequests.publish(taken, listOf(song("one"), second), "last"))
        val snapshot = requireNotNull(BoundedContextRequests.snapshot.value)
        assertEquals(listOf("one", "two"), snapshot.songs.map(Song::videoId))
        assertEquals("last", snapshot.continuationToken)
        assertEquals(token, snapshot.request.handoffToken)

        assertFalse(BoundedContextRequests.publish("stale", listOf(second), null))
        assertEquals("last", BoundedContextRequests.snapshot.value?.continuationToken)
    }

    @Test
    fun `playback from a downloaded preview cannot merge into the remote listing`() {
        val page = detailPage(DetailLoadId())
        val preview = request("one")
        val taken = requireNotNull(BoundedContextRequests.take(BoundedContextRequests.put(preview)))
        assertTrue(BoundedContextRequests.publish(taken, preview.songs, null))

        // Same video, but the download has no playlist occurrence ID.
        val snapshot = requireNotNull(BoundedContextRequests.snapshot.value)
        assertFalse(snapshot.songs.single().contextEntryKey() == remoteSong().contextEntryKey())
        assertFalse(snapshot.canUpdateDetail(page))
    }

    @Test
    fun `continuation mirrors only to the detail load that handed off playback`() {
        val loadId = DetailLoadId()
        val page = detailPage(loadId)
        val seed = request("one", continuation = "next").copy(songs = listOf(remoteSong()), detailLoadId = loadId)
        val taken = requireNotNull(BoundedContextRequests.take(BoundedContextRequests.put(seed)))
        assertTrue(BoundedContextRequests.publish(taken, seed.songs + song("two"), null))

        val snapshot = requireNotNull(BoundedContextRequests.snapshot.value)
        assertTrue(snapshot.canUpdateDetail(page))
        assertFalse(snapshot.canUpdateDetail(page.copy(detailLoadId = DetailLoadId())))
        assertFalse(snapshot.canUpdateDetail(page.copy(continuation = null)))
    }

    @Test
    fun `released continuation can be resumed by its owner only`() {
        val loadId = DetailLoadId()
        val seed = request("one", continuation = "next").copy(songs = listOf(remoteSong()), detailLoadId = loadId)
        val token = BoundedContextRequests.put(seed)
        val page = detailPage(loadId)
        assertFalse(requireNotNull(BoundedContextRequests.snapshot.value).canUpdateDetail(page))

        requireNotNull(BoundedContextRequests.take(token))
        BoundedContextRequests.release(token)
        val released = requireNotNull(BoundedContextRequests.snapshot.value)
        assertTrue(released.canUpdateDetail(page))
        assertFalse(released.canUpdateDetail(detailPage(DetailLoadId())))
    }

    private fun detailPage(loadId: DetailLoadId) = DetailPage(
        browseId = "VLrelease",
        title = "Release",
        subtitle = "",
        thumbnailUrl = null,
        songs = UiState.Success(listOf(remoteSong())),
        type = BrowseType.PLAYLIST,
        continuation = "next",
        detailLoadId = loadId,
    )

    private fun remoteSong() = song("one").copy(setVideoId = "slot-one")

    private fun request(id: String, continuation: String? = null) =
        BoundedContextRequests.Request(
            songs = listOf(song(id)),
            startIndex = 0,
            source = QueueSource("Release", PlaybackSourceType.BROWSE, "VLrelease"),
            continuationToken = continuation,
            shuffle = false,
        )

    private fun song(id: String) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )
}
