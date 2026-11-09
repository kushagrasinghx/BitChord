package com.music.bitchord.playback.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tracks a receiver could never be given.
 *
 * Written after asking "is this http(s)?" refused every track in the app:
 * `bitchord://` is how a normal track is spelled, and the resolver expands it
 * before a receiver sees it. So the question has to be which schemes stay on the
 * phone, not which ones already look like a stream.
 */
class LocalSchemesTest {

    @Test
    fun `a file on the phone cannot reach a receiver`() {
        assertFalse(receiverCanOpen("file"))
    }

    @Test
    fun `a MediaStore track cannot reach a receiver`() {
        assertFalse(receiverCanOpen("content"))
    }

    @Test
    fun `a network share cannot reach a receiver`() {
        assertFalse(receiverCanOpen("smb"))
        assertFalse(receiverCanOpen("webdav"))
    }

    /**
     * The one that broke it. Nearly every track the app owns is spelled this
     * way, and the resolver expands it into a stream the receiver can fetch.
     */
    @Test
    fun `our own address is not one of them`() {
        assertTrue(receiverCanOpen("bitchord"))
    }

    @Test
    fun `a plain stream is not one of them`() {
        assertTrue(receiverCanOpen("https"))
        assertTrue(receiverCanOpen("http"))
    }

    @Test
    fun `a track with no scheme cannot be sent anywhere`() {
        assertFalse(receiverCanOpen(null))
    }

    /**
     * A download is a copy of a stream. The file cannot be opened by a receiver,
     * but the track it was downloaded from can still be fetched from wherever it
     * came from, and the item carries that track's id beside the file's URI.
     */
    @Test
    fun `a downloaded track is cast through the track it came from`() {
        assertTrue(castableTrack("content", "f15_P8wuaQk"))
        assertTrue(castableTrack("file", "i2vxJbyBFtY"))
    }

    /** A module track downloads the same way, and keeps its module key. */
    @Test
    fun `a downloaded module track keeps its module address`() {
        assertTrue(castableTrack("content", "src:jiosaavn::TjR3dNl"))
    }

    /**
     * The case that looks identical from the outside and is not: an imported
     * file has nothing behind it to ask again, and its id is the path itself.
     * This is the one a fallback built on `mediaId.isNotBlank()` gets wrong.
     */
    @Test
    fun `an imported file is refused however its id is spelled`() {
        assertFalse(castableTrack("content", "content://media/external/audio/media/42"))
        assertFalse(castableTrack("file", "file:///storage/emulated/0/Music/a.flac"))
        assertFalse(castableTrack("file", "/storage/emulated/0/Music/a.flac"))
        assertFalse(castableTrack("content", null))
    }

    /** A download stands in for a track, not for a share the track lives on. */
    @Test
    fun `a local share is not reached by a downloaded copy`() {
        assertFalse(castableTrack("smb", "DRLa1NNPlVc"))
        assertFalse(castableTrack("webdav", "DRLa1NNPlVc"))
        assertFalse(castableTrack("android.resource", "DRLa1NNPlVc"))
    }

    @Test
    fun `the ordinary tracks are unchanged`() {
        assertTrue(castableTrack("bitchord", "DRLa1NNPlVc"))
        assertTrue(castableTrack("https", null))
        assertFalse(castableTrack(null, "DRLa1NNPlVc"))
        assertFalse(castableTrack(null, null))
    }
}
