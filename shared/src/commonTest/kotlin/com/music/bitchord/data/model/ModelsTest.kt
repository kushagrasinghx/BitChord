package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {
    @Test
    fun durationIsSharedAcrossTargets() {
        assertEquals(225_000L, "3:45".durationMillis())
        assertEquals(3_723_000L, "1:02:03".durationMillis())
        assertEquals(0L, "not-a-duration".durationMillis())
        assertEquals(0L, null.durationMillis())
    }

    @Test
    fun artworkSizeHintIsSharedAcrossTargets() {
        assertEquals(
            "https://example.test/w720-h720.jpg",
            "https://example.test/w120-h120.jpg".artworkAt(720),
        )
        assertEquals("https://example.test/cover.jpg", "https://example.test/cover.jpg".artworkAt(720))
        assertEquals(
            "https://yt3.ggpht.com/ytc/AIdro_x123=s512-c-k-c0x00ffffff-no-rj",
            "https://yt3.ggpht.com/ytc/AIdro_x123=s88-c-k-c0x00ffffff-no-rj".artworkAt(512),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8oc=s512-c",
            "https://lh3.googleusercontent.com/a/ACg8oc=s96-c".artworkAt(512),
        )
        assertEquals(
            "https://example.test/cover=s88.jpg",
            "https://example.test/cover=s88.jpg".artworkAt(512),
        )
        assertEquals(
            "https://notggpht.com/avatar=s88.jpg",
            "https://notggpht.com/avatar=s88.jpg".artworkAt(512),
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg".artworkAt(1200),
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg".artworkAt(320),
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg".artworkAt(160),
        )
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.webp",
            "https://img.youtube.com/vi/dQw4w9WgXcQ/default.webp".artworkAt(720),
        )
    }

    @Test
    fun anUpgradedYouTubeCoverComesBackDownForARow() {
        // Read back off the lock screen's artwork, the URL already names the
        // large copy; a row must not load it.
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg".artworkAt(160),
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg".artworkAt(160),
        )
    }

    @Test
    fun aRenamedYouTubeCoverDropsTheSignatureOfTheOldOne() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=-oaymwEj&rs=AOn4CL".artworkAt(720),
        )
        // Nothing renamed, nothing dropped.
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=-oaymwEj&rs=AOn4CL",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg?sqp=-oaymwEj&rs=AOn4CL".artworkAt(160),
        )
    }

    @Test
    fun aLargeYouTubeCoverFallsBackToTheOneEveryVideoHas() {
        assertEquals(
            "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg",
            youTubeThumbnailFallback("https://i.ytimg.com/vi/jNQXAC9IVRw/maxresdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg",
            youTubeThumbnailFallback("https://i.ytimg.com/vi/jNQXAC9IVRw/sddefault.jpg"),
        )
        assertEquals(null, youTubeThumbnailFallback("https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg"))
        assertEquals(null, youTubeThumbnailFallback("https://lh3.googleusercontent.com/x=w544-h544"))
    }

    private fun album(id: String) = ShelfItem(id, "", null, null, id)
    private fun song(id: String) = ShelfItem(id, "", null, id, null)

    @Test
    fun aReorderedReleaseShelfUnderAnotherTitleIsARepeat() {
        val albums = HomeShelf("Albums & singles", (1..10).map { album("a$it") })
        val reordered = HomeShelf("New albums & singles", (10 downTo 2).map { album("a$it") } + album("b"))
        val other = HomeShelf("Charts", (1..10).map { album("c$it") } + album("a1"))
        assertEquals(listOf(other), listOf(reordered, other).withoutRepeatsOf(listOf(albums)))
        assertEquals(listOf(albums), listOf(albums, reordered).withoutRepeatsOf(emptyList()))
    }

    @Test
    fun songShelvesAndAFewSharedAlbumsAreKept() {
        val recents = HomeShelf("Recents", (1..10).map { song("s$it") })
        val listenAgain = HomeShelf("Listen again", (1..10).map { song("s$it") } + album("a1") + album("a2"))
        val albums = HomeShelf("Albums & singles", (1..10).map { album("a$it") })
        assertEquals(listOf(listenAgain), listOf(listenAgain).withoutRepeatsOf(listOf(recents, albums)))
    }
}
