package com.music.bitchord

import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.ModuleSource
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the source layer that can be wrong quietly.
 *
 * The cross-source matcher gets most of the attention because it is the one
 * piece here whose failure isn't visible: a bad match doesn't crash or show an
 * error, it plays a different recording under the right title.
 */
class SourcesTest {

    // ---- Track identity -----------------------------------------------------

    @Test
    fun `track key round-trips`() {
        val key = SourceRegistry.trackKey("cfg-1", "track-42")
        assertEquals("cfg-1" to "track-42", SourceRegistry.parseTrackKey(key))
    }

    /** A module's own track ids are opaque and some issue ones containing colons. */
    @Test
    fun `track key survives separators inside the track id`() {
        val key = SourceRegistry.trackKey("cfg-1", "al::bum::7")
        assertEquals("cfg-1" to "al::bum::7", SourceRegistry.parseTrackKey(key))
    }

    /** A bare YouTube video id must not be mistaken for a source-backed one. */
    @Test
    fun `plain video ids are not source keys`() {
        assertNull(SourceRegistry.parseTrackKey("dQw4w9WgXcQ"))
        assertNull(SourceRegistry.parseTrackKey(""))
    }

    // ---- Format reporting ---------------------------------------------------

    @Test
    fun `lossless is decided by codec, not bitrate`() {
        assertEquals(true, StreamFormat(codec = "flac", kbps = 900).isLossless)
        assertEquals(true, StreamFormat(codec = "alac").isLossless)
        // A high sample rate does not rescue a lossy codec.
        assertEquals(false, StreamFormat(codec = "opus", sampleRateHz = 192_000).isLossless)
        // Unknown stays unknown rather than defaulting to "no".
        assertNull(StreamFormat().isLossless)
    }

    @Test
    fun `summary states depth and rate and drops bitrate when lossless`() {
        val hiRes = StreamFormat(codec = "flac", kbps = 4608, sampleRateHz = 192_000, bitDepth = 24)
        assertEquals("FLAC · 24-bit · 192 kHz", hiRes.summary)

        val lossy = StreamFormat(codec = "mp3", kbps = 320, sampleRateHz = 44_100)
        assertEquals("MP3 · 44.1 kHz · 320 kbps", lossy.summary)

        assertEquals("Unknown format", StreamFormat().summary)
    }

    /**
     * The badge tiers. "Hi-Quality" exists to separate a module's 320kbps
     * stream from YouTube's 160kbps Opus, which the screen otherwise renders
     * identically — as nothing at all.
     */
    @Test
    fun `names a high-bitrate lossy stream without calling it lossless`() {
        val aac320 = NerdStats.Snapshot(mimeType = "audio/mp4a-latm", bitrateKbps = 320, sampleRateHz = 44_100, channels = 2)
        assertFalse(aac320.isLossless)
        assertTrue(aac320.isHiQuality)

        val opus160 = NerdStats.Snapshot(mimeType = "audio/opus", bitrateKbps = 160, sampleRateHz = 48_000, channels = 2)
        assertFalse(opus160.isHiQuality)

        // Lossless is its own badge and never doubles as this one, however
        // large the bitrate a FLAC reports.
        val flac = NerdStats.Snapshot(mimeType = "audio/flac", bitrateKbps = 1411, sampleRateHz = 44_100, channels = 2)
        assertTrue(flac.isLossless)
        assertFalse(flac.isHiQuality)
    }

    /** With no measured bitrate, what the source said it was sending will do. */
    @Test
    fun `falls back to the declared bitrate for the quality tier`() {
        val claimed = NerdStats.Snapshot(
            mimeType = "audio/mp4a-latm",
            bitrateKbps = null,
            sampleRateHz = 44_100,
            channels = 2,
            claimed = StreamFormat(codec = "aac", kbps = 320),
        )
        assertTrue(claimed.isHiQuality)

        val silent = NerdStats.Snapshot(mimeType = "audio/mp4a-latm", bitrateKbps = null, sampleRateHz = null, channels = null)
        assertFalse(silent.isHiQuality)
    }

    // ---- Cross-source matching ---------------------------------------------

    private fun song(title: String, artist: String, duration: String? = null) =
        Song(videoId = "x", title = title, artist = artist, thumbnailUrl = null, durationText = duration)

    private fun matches(candidate: Song, title: String, artist: String, durationSec: Int? = null) =
        TrackMatcher.matches(candidate, title, artist, durationSec)

    @Test
    fun `matches the same recording across differing catalogue titles`() {
        assertTrue(
            matches(
                song("Bohemian Rhapsody (Remastered 2011)", "Queen"),
                title = "Bohemian Rhapsody",
                artist = "Queen",
            ),
        )
        assertTrue(
            matches(
                song("Sunflower", "Post Malone, Swae Lee"),
                title = "Sunflower (feat. Swae Lee)",
                artist = "Post Malone",
            ),
        )
        // Punctuation and case are not identity.
        assertTrue(
            matches(
                song("Don't Stop Me Now", "QUEEN"),
                title = "Dont Stop Me Now",
                artist = "Queen",
            ),
        )
    }

    /**
     * The one that sent this back for a rewrite. YouTube files the track under
     * the film it is from and credits the lead singer; the module holds the
     * same audio under the bare title and credits the duet. Every part of that
     * disagreement is packaging.
     */
    @Test
    fun `matches a film credit against a bare catalogue listing`() {
        assertTrue(
            matches(
                song("Paniyon Sa", "Atif Aslam, Tulsi Kumar", duration = "4:07"),
                title = "Paniyon Sa (From \"Satyameva Jayate\")",
                artist = "Atif Aslam",
                durationSec = 247,
            ),
        )
        // And the other way round, which is how a module-queued track finds
        // its YouTube seed for radio.
        assertTrue(
            matches(
                song("Paniyon Sa (From \"Satyameva Jayate\")", "Atif Aslam"),
                title = "Paniyon Sa",
                artist = "Atif Aslam, Tulsi Kumar",
            ),
        )
    }

    /** The trailing labels an upload hangs on a title with no brackets to hold them. */
    @Test
    fun `strips upload labelling from either side`() {
        assertTrue(matches(song("Tum Hi Ho", "Arijit Singh"), "Tum Hi Ho Full Song", "Arijit Singh"))
        assertTrue(
            matches(
                song("Kesariya", "Arijit Singh"),
                title = "Kesariya - Brahmastra | Official Video",
                artist = "Arijit Singh",
            ),
        )
        // "Artist - Title" uploads: the head is the credit, not the song.
        assertTrue(
            matches(
                song("Believer", "Imagine Dragons"),
                title = "Imagine Dragons - Believer",
                artist = "Imagine Dragons",
            ),
        )
    }

    @Test
    fun `refuses a different song by the same artist`() {
        assertFalse(
            matches(
                song("The Show Must Go On", "Queen"),
                title = "Bohemian Rhapsody",
                artist = "Queen",
            ),
        )
    }

    /**
     * The dangerous case: same title, different artist. A cover, a tribute
     * album, or a completely unrelated song that happens to share a name — all
     * of which a loose matcher would happily play instead.
     */
    @Test
    fun `refuses a cover by a different artist`() {
        assertFalse(
            matches(
                song("Hurt", "Johnny Cash"),
                title = "Hurt",
                artist = "Nine Inch Nails",
            ),
        )
    }

    @Test
    fun `accepts a shared artist when catalogues credit differently`() {
        assertTrue(
            matches(
                song("Numb / Encore", "Jay-Z & Linkin Park"),
                title = "Numb / Encore",
                artist = "Linkin Park",
            ),
        )
    }

    /**
     * Film catalogues credit from opposite ends: YouTube Music files this
     * under the composer, every store under the singer, and the two credits
     * share nothing at all. An exact runtime is what says they are the same
     * master anyway.
     */
    @Test
    fun `accepts a composer credit against a singer credit on an exact runtime`() {
        assertTrue(
            matches(
                song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = 233,
            ),
        )
        // The remix and the acoustic cover from the same result set are the
        // reason this is safe: neither agrees on length.
        assertFalse(
            matches(
                song("Jhak Maar Ke", "Leo Lz Mix", duration = "4:01"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = 233,
            ),
        )
    }

    /**
     * The runtime may only stand in for a credit when there *is* a runtime.
     * Without one there is nothing corroborating anything, and an unrelated
     * song sharing a title must still lose.
     */
    @Test
    fun `will not waive the credit check without a runtime to back it`() {
        assertFalse(matches(song("Jhak Maar Ke", "Neeraj Shridhar"), "Jhak Maar Ke", "Pritam"))
        assertFalse(
            matches(
                song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = null,
            ),
        )
    }

    /** A properly credited copy always outranks one the runtime merely vouched for. */
    @Test
    fun `prefers a credited match over a runtime-vouched one`() {
        val target = TrackMatcher.Target("Jhak Maar Ke", "Pritam, Neeraj Shridhar", durationSec = 233)
        val vouched = song("Jhak Maar Ke", "Some Uploader", duration = "3:53")
        val credited = song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53")
        assertEquals(credited, TrackMatcher.best(listOf(vouched, credited), target))
    }

    /** A name inside another name is not a shared credit. */
    @Test
    fun `refuses an artist whose name merely contains the one asked for`() {
        assertFalse(matches(song("No One Knows", "Queens of the Stone Age"), "No One Knows", "Queen"))
    }

    /**
     * A different take is a different recording, and the direction it is asked
     * for in doesn't change that.
     */
    @Test
    fun `refuses a different take of the same song`() {
        assertFalse(matches(song("Shape of You (Acoustic)", "Ed Sheeran"), "Shape of You", "Ed Sheeran"))
        assertFalse(matches(song("Shape of You", "Ed Sheeran"), "Shape of You (Acoustic)", "Ed Sheeran"))
        assertFalse(matches(song("Creep (Live)", "Radiohead"), "Creep", "Radiohead"))
        assertFalse(matches(song("Faded", "Alan Walker"), "Faded (Slowed + Reverb)", "Alan Walker"))
        // A stem carries the right title and the right artist and is not the
        // song — this one was one candidate away from playing.
        assertFalse(
            matches(
                song("Apna Bana Le - Arijit Singh Vocals Only", "Arijit Singh, Sachin-Jigar"),
                title = "Apna Bana Le (From \"Bhediya\")",
                artist = "Arijit Singh",
            ),
        )
        assertFalse(matches(song("Kesariya (Instrumental)", "Arijit Singh"), "Kesariya", "Arijit Singh"))
        // Both sides saying the same thing is still a match.
        assertTrue(matches(song("Creep (Live)", "Radiohead"), "Creep [Live]", "Radiohead"))
    }

    /** Version-shaped words that describe the ordinary release, not a new take. */
    @Test
    fun `treats an album or radio version as the plain track`() {
        assertTrue(matches(song("Africa", "Toto"), "Africa (Album Version)", "Toto"))
        assertTrue(matches(song("Clocks", "Coldplay"), "Clocks (Radio Edit)", "Coldplay"))
    }

    /** The signal a title can't give: a loop, a snippet, or a whole album side. */
    @Test
    fun `refuses a candidate whose runtime is nowhere near`() {
        assertFalse(
            matches(
                song("Levitating", "Dua Lipa", duration = "1:00:12"),
                title = "Levitating",
                artist = "Dua Lipa",
                durationSec = 203,
            ),
        )
        // A few seconds of trimmed silence is not a different recording.
        assertTrue(
            matches(
                song("Levitating", "Dua Lipa", duration = "3:25"),
                title = "Levitating",
                artist = "Dua Lipa",
                durationSec = 203,
            ),
        )
    }

    /** With no artist to check against, the title alone has to carry it. */
    @Test
    fun `falls back to title alone when no artist is known`() {
        assertTrue(matches(song("Clair de Lune", "Debussy"), "Clair de Lune", ""))
        assertFalse(matches(song("Reverie", "Debussy"), "Clair de Lune", ""))
    }

    // ---- Choosing between candidates ---------------------------------------

    /**
     * Search backends rank however they like. The right copy is the one whose
     * runtime and credit agree, not the one that came back first.
     */
    @Test
    fun `picks the closest candidate rather than the first acceptable one`() {
        val target = TrackMatcher.Target("Paniyon Sa", "Atif Aslam", durationSec = 247)
        val wrongLength = song("Paniyon Sa", "Atif Aslam", duration = "4:32")
        val right = song("Paniyon Sa", "Atif Aslam, Tulsi Kumar", duration = "4:06")
        assertEquals(right, TrackMatcher.best(listOf(wrongLength, right), target))
    }

    /**
     * A declared tier is a reason to prefer one copy of a recording over
     * another. It is not a reason to play a different recording — the DJ edit
     * on a compilation carries the right title and the right artist, and only
     * its runtime gives it away.
     */
    @Test
    fun `refuses to let a lossless label outrank the right runtime`() {
        val target = TrackMatcher.Target("Sakhiyaan", "Maninder Buttar", durationSec = 180)
        val djEdit = song("Sakhiyaan", "Maninder Buttar", duration = "3:05")
            .copy(albumName = "Punjabi Dj Holi songs", sourceQuality = "LOSSLESS")
        val albumCut = song("Sakhiyaan", "Maninder Buttar", duration = "3:00")
            .copy(albumName = "Sakhiyaan")
        // Both are acceptable matches on title and artist alone...
        assertTrue(TrackMatcher.matches(djEdit, target.title, target.artist))
        // ...and the runtime is the only thing that separates them.
        assertEquals(albumCut, TrackMatcher.best(listOf(djEdit, albumCut), target))
        // Including when lossless is being asked for and only the wrong cut
        // claims to have it, which is the case that actually shipped broken.
        assertEquals(
            listOf(albumCut),
            SourceResolver.preferred(listOf(djEdit, albumCut), target, wantsLossless = true),
        )
    }

    /** With no runtime to separate them, the declared tier is the tiebreak again. */
    @Test
    fun `prefers the lossless copy when nothing separates the recordings`() {
        val target = TrackMatcher.Target("Sakhiyaan", "Maninder Buttar", durationSec = 180)
        val plain = song("Sakhiyaan", "Maninder Buttar", duration = "3:00")
        val lossless = song("Sakhiyaan", "Maninder Buttar", duration = "3:00")
            .copy(sourceQuality = "LOSSLESS")
        assertEquals(
            listOf(lossless, plain),
            SourceResolver.preferred(listOf(plain, lossless), target, wantsLossless = true),
        )
    }

    // ---- Deciding whether an upgrade is worth the seam -----------------------

    /**
     * Lossless is always worth it — it is what was asked for, and the reason
     * the second look happens at all.
     */
    @Test
    fun `always swaps to lossless`() {
        val youtube = StreamFormat(codec = "opus", kbps = 160)
        assertTrue(SourceResolver.worthSwapping(StreamFormat(codec = "flac"), youtube))
        // Even against a lossy stream that is nominally the higher bitrate.
        assertTrue(
            SourceResolver.worthSwapping(StreamFormat(codec = "flac"), StreamFormat(codec = "aac", kbps = 320)),
        )
    }

    /**
     * The Shaayraana case: no catalogue had a lossless copy, one had a 320kbps
     * AAC, and the track played on YouTube's 160kbps Opus because the only
     * question being asked was "is this lossless".
     */
    @Test
    fun `swaps to a lossy stream that is clearly better than what is playing`() {
        val youtube = StreamFormat(codec = "opus", kbps = 160)
        assertTrue(SourceResolver.worthSwapping(StreamFormat(codec = "aac", kbps = 320), youtube))
    }

    /** A margin too narrow to hear does not earn a break in the audio. */
    @Test
    fun `refuses a lossy swap that gains little`() {
        assertFalse(
            SourceResolver.worthSwapping(
                StreamFormat(codec = "mp3", kbps = 192),
                StreamFormat(codec = "aac", kbps = 128),
            ),
        )
        // And never a downgrade, however the codecs compare.
        assertFalse(
            SourceResolver.worthSwapping(
                StreamFormat(codec = "mp3", kbps = 128),
                StreamFormat(codec = "opus", kbps = 160),
            ),
        )
    }

    /**
     * An unstated bitrate on either side is not evidence of an improvement.
     * Swapping on one would be gambling the listener's audio on a guess.
     */
    @Test
    fun `refuses a lossy swap it cannot measure`() {
        val playing = StreamFormat(codec = "opus", kbps = 160)
        assertFalse(SourceResolver.worthSwapping(StreamFormat(codec = "aac"), playing))
        assertFalse(SourceResolver.worthSwapping(StreamFormat(codec = "aac", kbps = 320), null))
        assertFalse(
            SourceResolver.worthSwapping(StreamFormat(codec = "aac", kbps = 320), StreamFormat(codec = "opus")),
        )
    }

    @Test
    fun `has nothing to offer when no candidate is the recording`() {
        val target = TrackMatcher.Target("Paniyon Sa", "Atif Aslam")
        assertNull(TrackMatcher.best(listOf(song("Paniyon Sa", "Some Cover Band")), target))
        assertNull(TrackMatcher.best(emptyList(), target))
    }

    // ---- Asking ------------------------------------------------------------

    /**
     * What a source is asked for. The raw title is never one of the queries:
     * a catalogue that lists "Paniyon Sa" has never stored the film name
     * YouTube prints alongside it, and scoring against words it doesn't hold
     * is how a source that had the track answered as if it didn't.
     */
    @Test
    fun `asks for the title a catalogue would file the track under`() {
        val queries = TrackMatcher.queries(
            TrackMatcher.Target("Paniyon Sa (From \"Satyameva Jayate\") | Official Video", "Atif Aslam, Tulsi Kumar"),
        )
        assertEquals(listOf("paniyon sa atif aslam", "paniyon sa"), queries)
    }

    /** A version marker is part of what to search for, not packaging to drop. */
    @Test
    fun `keeps the version marker in the query`() {
        assertEquals(
            "shape of you acoustic",
            TrackMatcher.queries(TrackMatcher.Target("Shape of You (Acoustic)", "")).single(),
        )
    }

    @Test
    fun `has nothing to ask for without a title`() {
        assertTrue(TrackMatcher.queries(TrackMatcher.Target("", "Atif Aslam")).isEmpty())
    }

    // ---- The mid-track swap guard ------------------------------------------

    /**
     * The check standing between a listener and having their audio cut for a
     * different recording. Stricter than ordinary matching on purpose, and it
     * refuses anything it cannot actually check.
     */
    @Test
    fun `only swaps in a copy of demonstrably the same length`() {
        val playing = TrackMatcher.Target("Jo Tere Sang", "Jeet Gannguli", durationSec = 306)
        assertTrue(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x", "5:06"), playing, 2))
        assertTrue(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x", "5:04"), playing, 2))
        assertFalse(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x", "5:12"), playing, 2))
        // A candidate that never said how long it is cannot be checked, and an
        // unverifiable swap is not worth making.
        assertFalse(TrackMatcher.withinSeconds(song("Jo Tere Sang", "x"), playing, 2))
        // Neither is one where nothing is playing to compare against.
        assertFalse(
            TrackMatcher.withinSeconds(
                song("Jo Tere Sang", "x", "5:06"),
                TrackMatcher.Target("Jo Tere Sang", "Jeet Gannguli"),
                2,
            ),
        )
    }

    // ---- Quality tiers -----------------------------------------------------

    /**
     * Every module spells its quality differently, and the spelling is all
     * there is to go on when choosing which catalogue to open a track from.
     */
    @Test
    fun `reads a tier out of whatever a module calls it`() {
        assertEquals("LOSSLESS", ModuleSource.qualityTier("LOSSLESS"))
        assertEquals("LOSSLESS", ModuleSource.qualityTier("FLAC 16-bit / 44.1kHz"))
        assertEquals("LOSSLESS", ModuleSource.qualityTier("hires-96"))
        assertEquals("HIGH", ModuleSource.qualityTier("HIGH"))
        assertEquals("HIGH", ModuleSource.qualityTier("320kbps"))
        assertEquals("LOW", ModuleSource.qualityTier("128kbps"))
        assertEquals("LOW", ModuleSource.qualityTier("LOW"))
        assertNull(ModuleSource.qualityTier(""))
        assertNull(ModuleSource.qualityTier("Deadbeat"))
    }

    /** The codec wins the tie: a bit depth alongside FLAC is still FLAC. */
    @Test
    fun `does not mistake a bit depth for a bitrate tier`() {
        assertEquals("LOSSLESS", ModuleSource.qualityTier("24-bit / 192 kHz"))
        assertEquals("LOSSLESS", ModuleSource.qualityTier("FLAC 128"))
    }

    @Test
    fun `orders tiers worst to best`() {
        assertEquals(listOf("LOW", "HIGH", "LOSSLESS"), ModuleSource.TIERS)
    }

    // ---- Runtime parsing ---------------------------------------------------

    @Test
    fun `reads a runtime off a queue row`() {
        assertEquals(225, TrackMatcher.secondsOf("3:45"))
        assertEquals(3723, TrackMatcher.secondsOf("1:02:03"))
        assertNull(TrackMatcher.secondsOf(null))
        assertNull(TrackMatcher.secondsOf("live"))
        assertNull(TrackMatcher.secondsOf("0:00"))
    }
}
